package com.example.feature.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ReadingRepository
import com.example.domain.model.ReadingCategory
import com.example.domain.model.ReadingPassageDetail
import com.example.domain.model.ReadingPassageSummary
import com.example.domain.model.ReadingQuestion
import com.example.domain.model.ReadingQuestionType
import com.example.domain.model.ReadingSentence
import com.example.domain.model.ReadingVocabItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadingAdminListState(
    val loading: Boolean = false,
    val passages: List<ReadingPassageSummary> = emptyList(),
    val categories: List<ReadingCategory> = emptyList(),
    val filterLevel: String = "ALL",
    val filterCategoryId: String = "",
    val filterPublished: String = "all",
    val searchQuery: String = "",
    val page: Int = 1,
    val totalPages: Int = 1,
    val total: Int = 0,
    val message: String? = null,
    val error: String? = null
)

data class ReadingPassageEditorState(
    val isEditing: Boolean = false,
    val editingId: String? = null,
    val loadingDetail: Boolean = false,
    val saving: Boolean = false,
    val previewMode: Boolean = false,
    val title: String = "",
    val level: String = "A1",
    val categoryId: String = "",
    val summary: String = "",
    val englishText: String = "",
    val bengaliTranslation: String = "",
    val audioUrl: String = "",
    val estimatedMinutes: Int = 3,
    val isPublished: Boolean = true,
    val previewSentences: List<ReadingSentence> = emptyList(),
    val vocabulary: List<ReadingVocabItem> = emptyList(),
    val questions: List<ReadingQuestion> = emptyList(),
    val validationError: String? = null
)

class ReadingAdminViewModel(
    private val repository: ReadingRepository = ReadingRepository()
) : ViewModel() {

    private val _listState = MutableStateFlow(ReadingAdminListState())
    val listState: StateFlow<ReadingAdminListState> = _listState.asStateFlow()

    private val _editorState = MutableStateFlow(ReadingPassageEditorState())
    val editorState: StateFlow<ReadingPassageEditorState> = _editorState.asStateFlow()

    private var searchJob: Job? = null

    fun loadInitial() {
        loadCategories()
        loadPassages(page = 1)
    }

    fun loadCategories() {
        viewModelScope.launch {
            repository.adminFetchCategories().onSuccess { cats ->
                _listState.update { it.copy(categories = cats) }
            }
        }
    }

    fun loadPassages(page: Int = 1) {
        val cur = _listState.value
        viewModelScope.launch {
            _listState.update { it.copy(loading = true, error = null) }
            repository.adminFetchPassages(
                page = page,
                level = cur.filterLevel,
                categoryId = cur.filterCategoryId,
                published = cur.filterPublished,
                search = cur.searchQuery.trim()
            ).fold(
                onSuccess = { paged ->
                    _listState.update {
                        it.copy(
                            loading = false,
                            passages = paged.items,
                            page = paged.page,
                            totalPages = paged.totalPages,
                            total = paged.total
                        )
                    }
                },
                onFailure = { err ->
                    _listState.update {
                        it.copy(loading = false, error = err.message ?: "Failed to load passages.")
                    }
                }
            )
        }
    }

    fun updateFilters(
        level: String = _listState.value.filterLevel,
        categoryId: String = _listState.value.filterCategoryId,
        published: String = _listState.value.filterPublished
    ) {
        _listState.update {
            it.copy(filterLevel = level, filterCategoryId = categoryId, filterPublished = published)
        }
        loadPassages(1)
    }

    fun updateSearch(query: String) {
        _listState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            loadPassages(1)
        }
    }

    fun openNewPassage() {
        val defaultCat = _listState.value.categories.firstOrNull { it.isActive }?.id.orEmpty()
        _editorState.value = ReadingPassageEditorState(
            isEditing = true,
            editingId = null,
            categoryId = defaultCat
        )
    }

    fun openEditPassage(id: String) {
        _editorState.value = ReadingPassageEditorState(
            isEditing = true,
            editingId = id,
            loadingDetail = true
        )
        viewModelScope.launch {
            repository.adminFetchPassageDetail(id).fold(
                onSuccess = { bundle ->
                    val d = bundle.passage
                    _editorState.value = ReadingPassageEditorState(
                        isEditing = true,
                        editingId = d.summary.id,
                        loadingDetail = false,
                        title = d.summary.title,
                        level = d.summary.level,
                        categoryId = d.summary.categoryId,
                        summary = d.summary.summary,
                        englishText = d.englishText,
                        bengaliTranslation = d.bengaliTranslation,
                        audioUrl = d.audioUrl.orEmpty(),
                        estimatedMinutes = d.summary.estimatedMinutes,
                        isPublished = d.summary.isPublished,
                        previewSentences = d.sentences,
                        vocabulary = d.vocabulary,
                        questions = d.questions
                    )
                },
                onFailure = { err ->
                    _editorState.update {
                        it.copy(loadingDetail = false, validationError = err.message ?: "Could not load passage.")
                    }
                }
            )
        }
    }

    fun closeEditor() {
        _editorState.value = ReadingPassageEditorState(isEditing = false)
    }

    fun togglePreviewMode() {
        _editorState.update { state ->
            val sentences = ReadingSentenceSplitter.splitEnglishText(state.englishText)
            state.copy(previewMode = !state.previewMode, previewSentences = sentences)
        }
    }

    fun updateEditor(transform: (ReadingPassageEditorState) -> ReadingPassageEditorState) {
        _editorState.update { state ->
            val next = transform(state).copy(validationError = null)
            if (next.englishText != state.englishText) {
                next.copy(previewSentences = ReadingSentenceSplitter.splitEnglishText(next.englishText))
            } else next
        }
    }

    fun addVocabItem(item: ReadingVocabItem) {
        if (item.word.isBlank()) return
        _editorState.update { state ->
            val dedup = state.vocabulary.filterNot { it.word.equals(item.word.trim(), ignoreCase = true) }
            state.copy(vocabulary = dedup + item.copy(word = item.word.trim()))
        }
    }

    fun removeVocabAt(index: Int) {
        _editorState.update { state ->
            state.copy(vocabulary = state.vocabulary.filterIndexed { i, _ -> i != index })
        }
    }

    fun addQuestion(question: ReadingQuestion) {
        if (question.prompt.isBlank()) return
        val qid = question.questionId.ifBlank { "q_${_editorState.value.questions.size + 1}_${System.currentTimeMillis() % 1000}" }
        _editorState.update { state ->
            state.copy(questions = state.questions + question.copy(questionId = qid))
        }
    }

    fun removeQuestionAt(index: Int) {
        _editorState.update { state ->
            state.copy(questions = state.questions.filterIndexed { i, _ -> i != index })
        }
    }

    fun savePassage(onSaved: () -> Unit = {}) {
        val ed = _editorState.value
        if (ed.title.trim().isEmpty()) {
            _editorState.update { it.copy(validationError = "Title is required.") }
            return
        }
        if (ed.englishText.trim().isEmpty()) {
            _editorState.update { it.copy(validationError = "English passage text is required.") }
            return
        }
        val audioUrlTrimmed = ed.audioUrl.trim()
        if (audioUrlTrimmed.isNotEmpty() &&
            !audioUrlTrimmed.startsWith("http://") &&
            !audioUrlTrimmed.startsWith("https://")
        ) {
            _editorState.update { it.copy(validationError = "Audio URL must start with http:// or https://") }
            return
        }

        val detail = ReadingPassageDetail(
            summary = ReadingPassageSummary(
                id = ed.editingId.orEmpty(),
                title = ed.title.trim(),
                level = ed.level,
                categoryId = ed.categoryId,
                summary = ed.summary.trim(),
                estimatedMinutes = ed.estimatedMinutes.coerceIn(1, 120),
                isPublished = ed.isPublished
            ),
            englishText = ed.englishText.trim(),
            bengaliTranslation = ed.bengaliTranslation.trim(),
            audioUrl = audioUrlTrimmed.ifBlank { null },
            sentences = ReadingSentenceSplitter.splitEnglishText(ed.englishText),
            vocabulary = ed.vocabulary,
            questions = ed.questions
        )

        viewModelScope.launch {
            _editorState.update { it.copy(saving = true, validationError = null) }
            repository.adminSavePassage(ed.editingId, detail).fold(
                onSuccess = {
                    _editorState.value = ReadingPassageEditorState(isEditing = false)
                    _listState.update {
                        it.copy(message = if (ed.editingId == null) "Passage created" else "Passage updated")
                    }
                    loadPassages(1)
                    loadCategories()
                    onSaved()
                },
                onFailure = { err ->
                    _editorState.update {
                        it.copy(saving = false, validationError = err.message ?: "Failed to save passage.")
                    }
                }
            )
        }
    }

    fun togglePublish(passage: ReadingPassageSummary) {
        viewModelScope.launch {
            repository.adminFetchPassageDetail(passage.id).fold(
                onSuccess = { bundle ->
                    val d = bundle.passage
                    val updated = d.copy(
                        summary = d.summary.copy(isPublished = !d.summary.isPublished)
                    )
                    repository.adminSavePassage(passage.id, updated).fold(
                        onSuccess = {
                            _listState.update {
                                it.copy(message = if (updated.summary.isPublished) "Passage published" else "Moved to Draft")
                            }
                            loadPassages(_listState.value.page)
                        },
                        onFailure = { err ->
                            _listState.update { it.copy(error = err.message ?: "Could not update publish state.") }
                        }
                    )
                },
                onFailure = { err ->
                    _listState.update { it.copy(error = err.message ?: "Failed to update passage.") }
                }
            )
        }
    }

    fun deletePassage(id: String) {
        viewModelScope.launch {
            repository.adminDeletePassage(id).fold(
                onSuccess = {
                    _listState.update { it.copy(message = "Passage deleted") }
                    loadPassages(1)
                    loadCategories()
                },
                onFailure = { err ->
                    _listState.update { it.copy(error = err.message ?: "Failed to delete passage.") }
                }
            )
        }
    }

    fun saveCategory(id: String?, name: String, description: String, isActive: Boolean) {
        if (name.trim().isEmpty()) {
            _listState.update { it.copy(error = "Category name is required.") }
            return
        }
        viewModelScope.launch {
            repository.adminSaveCategory(id, name.trim(), description.trim(), isActive).fold(
                onSuccess = {
                    _listState.update { it.copy(message = if (id == null) "Category added" else "Category updated") }
                    loadCategories()
                },
                onFailure = { err ->
                    _listState.update { it.copy(error = err.message ?: "Failed to save category.") }
                }
            )
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            repository.adminDeleteCategory(id).fold(
                onSuccess = {
                    _listState.update { it.copy(message = "Category removed") }
                    loadCategories()
                },
                onFailure = { err ->
                    _listState.update { it.copy(error = err.message ?: "Failed to delete category.") }
                }
            )
        }
    }

    fun clearBanner() {
        _listState.update { it.copy(message = null, error = null) }
    }
}
