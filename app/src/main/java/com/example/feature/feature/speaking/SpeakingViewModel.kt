package com.example.feature.speaking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ChatTurn
import com.example.data.repository.Correction
import com.example.data.repository.SpeakingRepository
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
    val partialText: String = "",
    val topic: String = "Daily Life",
    val error: String? = null
)

val SpeakingTopics = listOf(
    "Daily Life", "Self Introduction", "Family", "Education", "Shopping", "Travel",
    "Food", "Job Interviews", "Workplace", "Social Communication", "Business Communication"
)

class SpeakingViewModel(
    application: Application,
    private val level: String,
    private val repository: SpeakingRepository = SpeakingRepository()
) : AndroidViewModel(application) {

    private val speech = SpeechHelper(application)
    private var nextId = 1L

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
        _state.update { it.copy(phase = SpeakingPhase.LISTENING, partialText = "", error = null) }
        speech.startListening(
            onPartial = { text -> _state.update { it.copy(partialText = text) } },
            onFinal = { text -> sendMessage(text) },
            onErrorMessage = { msg -> _state.update { it.copy(phase = SpeakingPhase.IDLE, partialText = "", error = msg) } }
        )
    }

    /** Used by both the mic (recognized text) and the typing box. */
    fun sendMessage(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || _state.value.phase == SpeakingPhase.THINKING) return
        speech.stopSpeaking()

        val userMessage = ChatMessage(id = nextId++, isUser = true, text = text)
        val history = _state.value.messages
            .filter { it.includeInHistory }
            .map { ChatTurn(if (it.isUser) "user" else "assistant", it.text) }
        val topic = _state.value.topic

        _state.update {
            it.copy(messages = it.messages + userMessage, phase = SpeakingPhase.THINKING, partialText = "", error = null)
        }

        viewModelScope.launch {
            repository.sendTurn(text, level, topic, history)
                .onSuccess { reply ->
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
        speech.release()
        super.onCleared()
    }
}
