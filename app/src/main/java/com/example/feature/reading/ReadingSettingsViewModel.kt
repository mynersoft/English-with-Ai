package com.example.feature.reading

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ReadingRepository
import com.example.domain.model.ReadingSettings
import com.example.feature.reading.audio.ProviderRegistry
import com.example.feature.reading.audio.TtsVoiceOption
import com.example.feature.reading.reminder.ReadingReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReadingSettingsUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val settings: ReadingSettings = ReadingSettings(),
    val availableVoices: List<TtsVoiceOption> = emptyList(),
    val accentStatusNote: String? = null,
    val error: String? = null,
    val savedMessage: String? = null
)

class ReadingSettingsViewModel(
    application: Application,
    private val repository: ReadingRepository = ReadingRepository()
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, ReadingRepository())

    private val ttsProvider = ProviderRegistry(application).createTtsProvider()

    private val _uiState = MutableStateFlow(ReadingSettingsUiState())
    val uiState: StateFlow<ReadingSettingsUiState> = _uiState.asStateFlow()

    init {
        ttsProvider.initialize("en-US", "", 1.0f)
        viewModelScope.launch {
            ttsProvider.availableVoices.collect { voices ->
                _uiState.update { it.copy(availableVoices = voices) }
            }
        }
        viewModelScope.launch {
            ttsProvider.accentStatusNote.collect { note ->
                _uiState.update { it.copy(accentStatusNote = note) }
            }
        }
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val result = repository.fetchSettings()
            result.fold(
                onSuccess = { s ->
                    ttsProvider.setAccent(s.accent)
                    _uiState.update { it.copy(loading = false, settings = s) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(loading = false, error = err.message ?: "Failed to load settings.")
                    }
                }
            )
        }
    }

    fun updateDraft(transform: (ReadingSettings) -> ReadingSettings) {
        _uiState.update { state ->
            val updated = transform(state.settings)
            if (updated.accent != state.settings.accent) {
                ttsProvider.setAccent(updated.accent)
            }
            state.copy(settings = updated, savedMessage = null, error = null)
        }
    }

    fun previewVoice(voiceId: String) {
        ttsProvider.setVoice(voiceId)
        ttsProvider.setSpeed(_uiState.value.settings.defaultSpeed)
        ttsProvider.speak(
            text = "Hello! This voice will read your English passages.",
            utteranceId = "preview-${System.nanoTime()}",
            onComplete = {},
            onError = {}
        )
    }

    fun saveSettings() {
        val current = _uiState.value.settings
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, savedMessage = null) }
            val result = repository.updateSettings(current)
            result.fold(
                onSuccess = { saved ->
                    ReadingReminderScheduler.syncReminder(
                        context = getApplication(),
                        enabled = saved.reminderEnabled,
                        timeHHmm = saved.reminderTime
                    )
                    _uiState.update {
                        it.copy(
                            saving = false,
                            settings = saved,
                            savedMessage = "Reading settings saved"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            saving = false,
                            error = err.message ?: "Failed to save settings."
                        )
                    }
                }
            )
        }
    }

    override fun onCleared() {
        ttsProvider.release()
        super.onCleared()
    }
}
