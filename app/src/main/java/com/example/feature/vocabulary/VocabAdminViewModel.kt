package com.example.feature.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.VocabularyRepository
import com.example.domain.model.CsvImportSummary
import com.example.domain.model.VocabCategorySummary
import com.example.domain.model.VocabWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VocabAdminUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val words: List<VocabWord> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val total: Int = 0,
    val search: String = "",
    val levelFilter: String = "ALL",
    val categoryFilter: String = "",
    val categories: List<VocabCategorySummary> = emptyList(),
    val importSummary: CsvImportSummary? = null,
    val message: String? = null,
    val isError: Boolean = false
)

class VocabAdminViewModel : ViewModel() {
    private val repo = VocabularyRepository()

    private val _state = MutableStateFlow(VocabAdminUiState())
    val state: StateFlow<VocabAdminUiState> = _state.asStateFlow()

    fun loadInitial() {
        loadCategories()
        loadWords(page = 1)
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, isError = false, importSummary = null) }
    }

    fun updateFilters(search: String? = null, level: String? = null, categoryId: String? = null) {
        _state.update {
            it.copy(
                search = search ?: it.search,
                levelFilter = level ?: it.levelFilter,
                categoryFilter = categoryId ?: it.categoryFilter
            )
        }
        loadWords(page = 1)
    }

    fun loadWords(page: Int = _state.value.page) {
        val cur = _state.value
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            repo.adminListWords(
                page = page,
                search = cur.search,
                level = cur.levelFilter,
                categoryId = cur.categoryFilter
            )
                .onSuccess { res ->
                    _state.update {
                        it.copy(
                            loading = false,
                            words = res.items,
                            page = res.page,
                            totalPages = res.totalPages,
                            total = res.total
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(loading = false, message = err.message ?: "Failed to load words", isError = true)
                    }
                }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            repo.adminListCategories()
                .onSuccess { cats -> _state.update { it.copy(categories = cats) } }
                .onFailure { err ->
                    _state.update { it.copy(message = err.message ?: "Failed to load categories", isError = true) }
                }
        }
    }

    fun saveWord(word: VocabWord, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            repo.adminSaveWord(word)
                .onSuccess {
                    _state.update { it.copy(saving = false, message = "Word saved!", isError = false) }
                    loadCategories()
                    loadWords()
                    onDone()
                }
                .onFailure { err ->
                    _state.update { it.copy(saving = false, message = err.message ?: "Save failed", isError = true) }
                }
        }
    }

    fun togglePublish(wordId: String, nextPublished: Boolean) {
        viewModelScope.launch {
            repo.adminTogglePublish(wordId, nextPublished)
                .onSuccess { pub ->
                    _state.update { s ->
                        s.copy(
                            words = s.words.map { if (it.id == wordId) it.copy(isPublished = pub) else it },
                            message = if (pub) "Word published" else "Word unpublished",
                            isError = false
                        )
                    }
                }
                .onFailure { err ->
                    _state.update { it.copy(message = err.message ?: "Update failed", isError = true) }
                }
        }
    }

    fun deleteWord(wordId: String) {
        viewModelScope.launch {
            repo.adminDeleteWord(wordId)
                .onSuccess {
                    _state.update { it.copy(message = "Word deleted", isError = false) }
                    loadWords()
                }
                .onFailure { err ->
                    _state.update { it.copy(message = err.message ?: "Delete failed", isError = true) }
                }
        }
    }

    fun saveCategory(id: String?, name: String, description: String, parentId: String?, isActive: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            repo.adminSaveCategory(id, name, description, parentId, isActive)
                .onSuccess {
                    _state.update { it.copy(saving = false, message = "Category saved!", isError = false) }
                    loadCategories()
                }
                .onFailure { err ->
                    _state.update { it.copy(saving = false, message = err.message ?: "Save failed", isError = true) }
                }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            repo.adminDeleteCategory(categoryId)
                .onSuccess {
                    _state.update { it.copy(message = "Category deleted", isError = false) }
                    loadCategories()
                }
                .onFailure { err ->
                    _state.update { it.copy(message = err.message ?: "Delete failed", isError = true) }
                }
        }
    }

    fun reportMessage(message: String, isError: Boolean = true) {
        _state.update { it.copy(message = message, isError = isError) }
    }

    fun importCsv(csvText: String) {
        val chunks = CsvImportHelper.splitIntoChunks(csvText)
        if (chunks.isEmpty()) {
            _state.update {
                it.copy(
                    message = "CSV must contain a header row and at least 1 word row.",
                    isError = true
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null, importSummary = null) }
            var totalRows = 0
            var totalInserted = 0
            var totalDuplicates = 0
            val allErrors = mutableListOf<String>()

            for ((index, chunk) in chunks.withIndex()) {
                val res = repo.adminImportCsv(chunk)
                if (res.isFailure) {
                    val err = res.exceptionOrNull()
                    val partial = if (totalRows > 0) {
                        CsvImportSummary(totalRows, totalInserted, totalDuplicates, allErrors)
                    } else null
                    _state.update {
                        it.copy(
                            saving = false,
                            importSummary = partial,
                            message = err?.message ?: "Import failed on batch ${index + 1}",
                            isError = true
                        )
                    }
                    if (totalInserted > 0) {
                        loadCategories()
                        loadWords(1)
                    }
                    return@launch
                }
                val s = res.getOrThrow()
                totalRows += s.totalRows
                totalInserted += s.inserted
                totalDuplicates += s.duplicates
                allErrors.addAll(s.errors)
            }

            val summary = CsvImportSummary(
                totalRows = totalRows,
                inserted = totalInserted,
                duplicates = totalDuplicates,
                errors = allErrors
            )
            _state.update {
                it.copy(
                    saving = false,
                    importSummary = summary,
                    message = "Imported $totalInserted words ($totalDuplicates duplicates skipped).",
                    isError = false
                )
            }
            loadCategories()
            loadWords(1)
        }
    }
}
