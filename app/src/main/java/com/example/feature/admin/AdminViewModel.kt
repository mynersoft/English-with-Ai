package com.example.feature.admin

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface AdminAccessState {
    data object Checking : AdminAccessState
    data object Allowed : AdminAccessState
    data class Denied(val reason: String) : AdminAccessState
}

data class AdminListState(
    val loading: Boolean = false,
    val patterns: List<AdminPatternSummary> = emptyList(),
    val message: String? = null
)

data class AdminEditState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val busyId: String? = null,   // which audio control is working ("title", "ex0_en", "all"...)
    val form: PatternForm = PatternForm(),
    val dirty: Boolean = false,
    val message: String? = null
) {
    val isNew get() = form.id == null
    /** Audio is made from the SAVED text, so lock audio buttons until saved. */
    val audioLocked get() = isNew || dirty
}

class AdminViewModel : ViewModel() {

    private val _access = MutableStateFlow<AdminAccessState>(AdminAccessState.Checking)
    val access: StateFlow<AdminAccessState> = _access.asStateFlow()

    private val _list = MutableStateFlow(AdminListState())
    val list: StateFlow<AdminListState> = _list.asStateFlow()

    private val _edit = MutableStateFlow(AdminEditState())
    val edit: StateFlow<AdminEditState> = _edit.asStateFlow()

    fun verifyAccessAndLoad() {
        viewModelScope.launch {
            _access.value = AdminAccessState.Checking
            val ok = AdminApi.isAdmin()
            if (ok) {
                _access.value = AdminAccessState.Allowed
                loadList()
            } else {
                _access.value = AdminAccessState.Denied(
                    "Only verified administrators can access the Admin Dashboard."
                )
            }
        }
    }

    // ---------- list ----------
    fun loadList() {
        viewModelScope.launch {
            _list.update { it.copy(loading = true) }
            try {
                val items = AdminApi.listPatterns()
                _list.update { it.copy(loading = false, patterns = items) }
            } catch (e: Exception) {
                _list.update { it.copy(loading = false, message = e.message ?: "Failed to load") }
            }
        }
    }

    fun deletePattern(id: String) {
        viewModelScope.launch {
            try {
                AdminApi.deletePattern(id)
                _list.update { it.copy(message = "Deleted") }
                loadList()
            } catch (e: Exception) {
                _list.update { it.copy(message = e.message ?: "Delete failed") }
            }
        }
    }

    fun clearListMessage() = _list.update { it.copy(message = null) }

    // ---------- open editor ----------
    fun openNew() {
        _edit.value = AdminEditState()
    }

    fun openEdit(id: String) {
        viewModelScope.launch {
            _edit.value = AdminEditState(loading = true)
            try {
                _edit.value = AdminEditState(form = AdminApi.getPattern(id))
            } catch (e: Exception) {
                _edit.value = AdminEditState(message = e.message ?: "Failed to load")
            }
        }
    }

    fun clearEditMessage() = _edit.update { it.copy(message = null) }

    // ---------- form editing ----------
    private fun editForm(block: (PatternForm) -> PatternForm) =
        _edit.update { it.copy(form = block(it.form), dirty = true) }

    fun setTitle(v: String) = editForm { it.copy(title = v) }
    fun setMeaning(v: String) = editForm { it.copy(meaning = v) }
    fun setCategoryName(v: String) = editForm { it.copy(categoryName = v) }
    fun setExplanation(v: String) = editForm { it.copy(explanation = v) }
    fun setPremium(v: Boolean) = editForm { it.copy(isPremium = v) }
    fun setOrder(v: String) = editForm { it.copy(order = v.filter(Char::isDigit)) }

    fun setExampleEn(i: Int, v: String) = editForm { f ->
        f.copy(examples = f.examples.mapIndexed { j, e -> if (j == i) e.copy(en = v) else e })
    }

    fun setExampleBn(i: Int, v: String) = editForm { f ->
        f.copy(examples = f.examples.mapIndexed { j, e -> if (j == i) e.copy(bn = v) else e })
    }

    fun addExample() = editForm { it.copy(examples = it.examples + ExampleForm()) }

    fun removeExample(i: Int) = editForm { f ->
        f.copy(examples = f.examples.filterIndexed { j, _ -> j != i })
    }

    // ---------- save ----------
    fun save() {
        val form = _edit.value.form
        if (form.title.isBlank() || form.meaning.isBlank()) {
            _edit.update { it.copy(message = "Title and meaning are required") }
            return
        }
        viewModelScope.launch {
            _edit.update { it.copy(saving = true) }
            try {
                val id = AdminApi.savePattern(form)
                val fresh = AdminApi.getPattern(id) // new example ids + audio flags
                _edit.value = AdminEditState(form = fresh, message = "Saved")
            } catch (e: Exception) {
                _edit.update { it.copy(saving = false, message = e.message ?: "Save failed") }
            }
        }
    }

    // ---------- audio ----------
    /** Update only audio fields so text typed in the form is not lost. */
    private suspend fun refreshAudio() {
        val id = _edit.value.form.id ?: return
        val fresh = AdminApi.getPattern(id)
        _edit.update { s ->
            s.copy(
                form = s.form.copy(
                    titleAudio = fresh.titleAudio,
                    meaningAudio = fresh.meaningAudio,
                    examples = s.form.examples.map { ex ->
                        val f = fresh.examples.find { it.id == ex.id }
                        if (f != null) ex.copy(enAudio = f.enAudio, bnAudio = f.bnAudio) else ex
                    }
                )
            )
        }
    }

    private fun audioAction(busyId: String, okMessage: String? = null, block: suspend () -> String?) {
        val s = _edit.value
        if (s.audioLocked || s.busyId != null) return
        viewModelScope.launch {
            _edit.update { it.copy(busyId = busyId) }
            try {
                val msg = block() ?: okMessage
                refreshAudio()
                _edit.update { it.copy(busyId = null, message = msg) }
            } catch (e: Exception) {
                _edit.update { it.copy(busyId = null, message = e.message ?: "Audio action failed") }
            }
        }
    }

    fun generate(t: AudioTarget, force: Boolean) {
        val id = _edit.value.form.id ?: return
        audioAction(t.busyId()) {
            AdminApi.generateAudio(id, t.toJson(), force)
            null
        }
    }

    fun generateAll() {
        val id = _edit.value.form.id ?: return
        audioAction("all") {
            val (done, skipped) = AdminApi.generateAudio(id, JSONObject().put("target", "all"), false)
            "Generated $done, skipped $skipped"
        }
    }

    fun deleteAudio(t: AudioTarget) {
        val id = _edit.value.form.id ?: return
        audioAction(t.busyId()) {
            AdminApi.deleteAudio(id, t)
            null
        }
    }

    fun upload(resolver: ContentResolver, uri: Uri, t: AudioTarget) {
        val id = _edit.value.form.id ?: return
        audioAction(t.busyId()) {
            val mime = resolver.getType(uri) ?: "audio/mpeg"
            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: throw IllegalStateException("Cannot read file")
            AdminApi.uploadAudio(id, t, bytes, mime)
            null
        }
    }
}