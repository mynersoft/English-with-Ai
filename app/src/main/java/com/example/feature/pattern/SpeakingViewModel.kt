package com.example.feature.speaking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ChatTurn
import com.example.data.repository.Correction
import com.example.data.repository.SpeakingProgressTracker
import com.example.data.repository.SpeakingRepository
import com.example.data.repository.StudentDashboardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SpeakingPhase { IDLE, LISTENING, THINKING, SPEAKING }

data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val correction: Correction? = null,
    val includeInHistory: Boolean = true
)

data class SpeakingUiState(
    val messages: List<ChatMessage> = emptyList(),
    val phase: SpeakingPhase = SpeakingPhase.IDLE,
    val draftText: String = "",
    val partialText: String = "",
    val topic: String = "Daily Life",
    val error: String? = null
)

val SpeakingTopics = listOf(
    "Daily Life", "Self Introduction", "Family", "Education", "Shopping", "Travel",
    "Food", "Job Interviews", "Workplace", "Social Communication", "Business Communication"
)

internal fun combineDraftAndSpeech(base: String, speech: String): String {
    val cleanBase = base.trim()
    val cleanSpeech = speech.trim()
    val merged = when {
        cleanBase.isEmpty() -> cleanSpeech
        cleanSpeech.isEmpty() -> cleanBase
        else -> "$cleanBase $cleanSpeech"
    }
    return merged.take(500)
}

class SpeakingViewModel(
    application: Application,
    private val level: String,
    private val repository: SpeakingRepository = SpeakingRepository()
) : AndroidViewModel(application) {

    private val speech = SpeechHelper(application)
    private val progressTracker = SpeakingProgressTracker(application)
    private val dashboardRepository = StudentDashboardRepository(application)

    private var nextId = 1L
    private var baseDraftBeforeListening = ""
    private var sessionStartedAtMs: Long? = null
    private var lastFlushedAtMs: Long? = null
    private var hasRecordedSession = false

    private val _state = MutableStateFlow(
        SpeakingUiState(
            messages = listOf(
                ChatMessage(
                    id = 0,
                    isUser = false,
                    text = "Hi! I am your English speaking partner. Tap the mic and say hello. " +
                        "If you make a mistake, I will show you the correct sentence.",
                    includeInHistory = false
                )
            )
        )
    )
    val state: StateFlow<SpeakingUiState> = _state.asStateFlow()

    fun setTopic(topic: String) = _state.update { it.copy(topic = topic) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun onDraftChange(rawText: String) {
        if (sessionStartedAtMs == null && rawText.isNotBlank()) {
            sessionStartedAtMs = System.currentTimeMillis()
        }
        _state.update { it.copy(draftText = rawText.take(500)) }
    }

    fun onMicTap() {
        when (_state.value.phase) {
            SpeakingPhase.THINKING -> Unit
            SpeakingPhase.LISTENING -> speech.stopListening()
            SpeakingPhase.SPEAKING -> {
                speech.stopSpeaking()
                _state.update { it.copy(phase = SpeakingPhase.IDLE) }
                startListening()
            }
            SpeakingPhase.IDLE -> startListening()
        }
    }

    private fun startListening() {
        if (sessionStartedAtMs == null) {
            sessionStartedAtMs = System.currentTimeMillis()
        }
        baseDraftBeforeListening = _state.value.draftText
        _state.update { it.copy(phase = SpeakingPhase.LISTENING, partialText = "", error = null) }
        speech.startListening(
            onPartial = { text ->
                val combined = combineDraftAndSpeech(baseDraftBeforeListening, text)
                _state.update { it.copy(partialText = text, draftText = combined) }
            },
            onFinal = { text ->
                val combined = combineDraftAndSpeech(baseDraftBeforeListening, text)
                baseDraftBeforeListening = ""
                _state.update {
                    it.copy(
                        phase = SpeakingPhase.IDLE,
                        partialText = "",
                        draftText = combined,
                        error = null
                    )
                }
            },
            onErrorMessage = { msg ->
                val capturedPartial = _state.value.partialText
                if (capturedPartial.isNotBlank()) {
                    val combined = combineDraftAndSpeech(baseDraftBeforeListening, capturedPartial)
                    baseDraftBeforeListening = ""
                    _state.update {
                        it.copy(
                            phase = SpeakingPhase.IDLE,
                            partialText = "",
                            draftText = combined,
                            error = null
                        )
                    }
                } else {
                    val restored = baseDraftBeforeListening
                    baseDraftBeforeListening = ""
                    _state.update {
                        it.copy(
                            phase = SpeakingPhase.IDLE,
                            partialText = "",
                            draftText = restored,
                            error = msg
                        )
                    }
                }
            }
        )
    }

    fun sendCurrentDraft() {
        sendMessage(_state.value.draftText)
    }

    /** Sends text entered via keyboard or captured via speech recognition once user clicks Send. */
    fun sendMessage(rawText: String) {
        if (_state.value.phase == SpeakingPhase.LISTENING) {
            speech.cancelListening()
            baseDraftBeforeListening = ""
        }
        val text = rawText.trim().ifEmpty { _state.value.draftText.trim() }
        if (text.isEmpty() || _state.value.phase == SpeakingPhase.THINKING) return
        speech.stopSpeaking()

        val now = System.currentTimeMillis()
        if (sessionStartedAtMs == null) {
            sessionStartedAtMs = now
        }

        val userMessage = ChatMessage(id = nextId++, isUser = true, text = text)
        val history = _state.value.messages
            .filter { it.includeInHistory }
            .map { ChatTurn(if (it.isUser) "user" else "assistant", it.text) }
        val topic = _state.value.topic

        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                phase = SpeakingPhase.THINKING,
                draftText = "",
                partialText = "",
                error = null
            )
        }

        viewModelScope.launch {
            repository.sendTurn(text, level, topic, history)
                .onSuccess { reply ->
                    val completedAt = System.currentTimeMillis()
                    val anchor = lastFlushedAtMs ?: sessionStartedAtMs ?: completedAt
                    val turnDurationSec = ((completedAt - anchor) / 1000L).coerceIn(5L, 180L).toInt()
                    lastFlushedAtMs = completedAt
                    val isNewSession = !hasRecordedSession
                    hasRecordedSession = true
                    progressTracker.recordTurn(isNewSession = isNewSession, durationSeconds = turnDurationSec)
                    launch { dashboardRepository.recordSpeakingActivity(turnDurationSec, topic) }

                    val aiMessage = ChatMessage(id = nextId++, isUser = false, text = reply.reply)
                    _state.update { s ->
                        s.copy(
                            messages = s.messages.map { m ->
                                if (m.id == userMessage.id) m.copy(correction = reply.correction) else m
                            } + aiMessage,
                            phase = SpeakingPhase.SPEAKING
                        )
                    }
                    speech.speak(reply.reply) {
                        val ttsDoneAt = System.currentTimeMillis()
                        val prev = lastFlushedAtMs
                        if (prev != null) {
                            val ttsSec = ((ttsDoneAt - prev) / 1000L).coerceIn(0L, 120L).toInt()
                            if (ttsSec > 0) {
                                progressTracker.addSeconds(ttsSec)
                                lastFlushedAtMs = ttsDoneAt
                            }
                        }
                        _state.update { s ->
                            if (s.phase == SpeakingPhase.SPEAKING) s.copy(phase = SpeakingPhase.IDLE) else s
                        }
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(phase = SpeakingPhase.IDLE, error = e.message ?: "Something went wrong.") }
                }
        }
    }

    override fun onCleared() {
        val anchor = lastFlushedAtMs
        if (hasRecordedSession && anchor != null) {
            val extraSec = ((System.currentTimeMillis() - anchor) / 1000L).coerceIn(0L, 120L).toInt()
            if (extraSec > 0) progressTracker.addSeconds(extraSec)
        }
        speech.release()
        super.onCleared()
    }
}
