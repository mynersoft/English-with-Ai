package com.example.feature.reading.audio

import com.example.domain.model.ReadingSentence
import com.example.domain.model.TextMatchComparisonResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioManagerState(
    val status: AudioStatus = AudioStatus.LOADING,
    val activeSentenceIndex: Int = 0,
    val isPlayingFullPassage: Boolean = false,
    val isMp3Active: Boolean = false,
    val speed: Float = 1.0f,
    val accent: String = "en-US",
    val selectedVoiceId: String = "",
    val availableVoices: List<TtsVoiceOption> = emptyList(),
    val accentStatusNote: String? = null,
    val sttSupported: Boolean = false,
    val isListening: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Provider-independent coordinator for Reading audio, speech recognition, and text matching.
 * UI layers observe [state] and invoke AudioManager methods without referencing concrete SDKs.
 */
class AudioManager(registry: ProviderRegistry) {
    private val tts: TtsProvider = registry.createTtsProvider()
    private val stt: SttProvider = registry.createSttProvider()
    private val player: AudioPlayerProvider = registry.createAudioPlayerProvider()
    private val assessment: PronunciationAssessmentProvider =
        registry.createPronunciationAssessmentProvider()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(
        AudioManagerState(
            status = AudioStatus.LOADING,
            sttSupported = stt.isSupported()
        )
    )
    val state: StateFlow<AudioManagerState> = _state.asStateFlow()

    private var currentSentences: List<ReadingSentence> = emptyList()
    private var currentAudioUrl: String? = null
    private var pausedOnIndex: Int? = null
    private var onSentenceCompletedCallback: ((Int, ReadingSentence) -> Unit)? = null

    init {
        tts.initialize(_state.value.accent, _state.value.selectedVoiceId, _state.value.speed)

        scope.launch {
            tts.status.collect { ttsStatus ->
                if (!_state.value.isMp3Active && _state.value.status != AudioStatus.PAUSED) {
                    _state.update { it.copy(status = ttsStatus) }
                }
            }
        }
        scope.launch {
            tts.availableVoices.collect { voices ->
                _state.update { it.copy(availableVoices = voices) }
            }
        }
        scope.launch {
            tts.accentStatusNote.collect { note ->
                _state.update { it.copy(accentStatusNote = note) }
            }
        }
        scope.launch {
            player.status.collect { mpStatus ->
                if (_state.value.isMp3Active) {
                    _state.update { it.copy(status = mpStatus) }
                }
            }
        }
    }

    fun applySettings(accent: String, voiceId: String, speed: Float) {
        _state.update {
            it.copy(accent = accent, selectedVoiceId = voiceId, speed = speed)
        }
        tts.setAccent(accent)
        tts.setVoice(voiceId)
        tts.setSpeed(speed)
        player.setSpeed(speed)
    }

    fun setSentences(
        sentences: List<ReadingSentence>,
        mp3Url: String? = null,
        initialIndex: Int = 0,
        onSentenceCompleted: ((Int, ReadingSentence) -> Unit)? = null
    ) {
        currentSentences = sentences
        currentAudioUrl = mp3Url
        onSentenceCompletedCallback = onSentenceCompleted
        val safeIdx = initialIndex.coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
        _state.update { it.copy(activeSentenceIndex = safeIdx) }
    }

    fun playSentence(index: Int) {
        if (currentSentences.isEmpty()) return
        val safeIdx = index.coerceIn(0, currentSentences.lastIndex)
        stopMp3Internal()
        pausedOnIndex = null
        _state.update {
            it.copy(
                activeSentenceIndex = safeIdx,
                isPlayingFullPassage = false,
                isMp3Active = false,
                errorMessage = null
            )
        }
        speakSentenceAt(safeIdx, autoAdvance = false)
    }

    fun playFullPassage(fromIndex: Int = _state.value.activeSentenceIndex) {
        if (currentSentences.isEmpty()) return
        val safeIdx = fromIndex.coerceIn(0, currentSentences.lastIndex)
        stopMp3Internal()
        pausedOnIndex = null
        _state.update {
            it.copy(
                activeSentenceIndex = safeIdx,
                isPlayingFullPassage = true,
                isMp3Active = false,
                errorMessage = null
            )
        }
        speakSentenceAt(safeIdx, autoAdvance = true)
    }

    fun playOptionalMp3() {
        val url = currentAudioUrl?.takeIf { it.isNotBlank() } ?: return
        tts.stop()
        pausedOnIndex = null
        _state.update {
            it.copy(
                isMp3Active = true,
                isPlayingFullPassage = true,
                errorMessage = null
            )
        }
        player.playUrl(
            url = url,
            speed = _state.value.speed,
            onPrepared = {
                _state.update { it.copy(status = AudioStatus.PLAYING) }
            },
            onComplete = {
                _state.update {
                    it.copy(status = AudioStatus.COMPLETED, isPlayingFullPassage = false)
                }
            },
            onError = { err ->
                _state.update {
                    it.copy(status = AudioStatus.ERROR, isMp3Active = false, errorMessage = err)
                }
            }
        )
    }

    fun pause() {
        if (_state.value.isMp3Active) {
            player.pause()
            _state.update { it.copy(status = AudioStatus.PAUSED) }
            return
        }
        if (_state.value.status == AudioStatus.PLAYING || _state.value.status == AudioStatus.LOADING) {
            pausedOnIndex = _state.value.activeSentenceIndex
            tts.stop()
            _state.update { it.copy(status = AudioStatus.PAUSED) }
        }
    }

    fun resume() {
        if (_state.value.isMp3Active) {
            player.resume()
            _state.update { it.copy(status = AudioStatus.PLAYING) }
            return
        }
        val idx = pausedOnIndex ?: _state.value.activeSentenceIndex
        pausedOnIndex = null
        if (_state.value.isPlayingFullPassage) {
            playFullPassage(idx)
        } else {
            playSentence(idx)
        }
    }

    fun stop() {
        pausedOnIndex = null
        stopMp3Internal()
        tts.stop()
        _state.update {
            it.copy(
                isPlayingFullPassage = false,
                isMp3Active = false,
                status = if (it.status == AudioStatus.UNSUPPORTED) AudioStatus.UNSUPPORTED else AudioStatus.READY
            )
        }
    }

    fun replay() {
        if (_state.value.isMp3Active && !currentAudioUrl.isNullOrBlank()) {
            playOptionalMp3()
        } else if (_state.value.isPlayingFullPassage) {
            playFullPassage(0)
        } else {
            playSentence(_state.value.activeSentenceIndex)
        }
    }

    fun previousSentence() {
        if (currentSentences.isEmpty()) return
        val prev = (_state.value.activeSentenceIndex - 1).coerceAtLeast(0)
        if (_state.value.isPlayingFullPassage) {
            playFullPassage(prev)
        } else {
            playSentence(prev)
        }
    }

    fun nextSentence() {
        if (currentSentences.isEmpty()) return
        val next = (_state.value.activeSentenceIndex + 1).coerceAtMost(currentSentences.lastIndex)
        if (_state.value.isPlayingFullPassage) {
            playFullPassage(next)
        } else {
            playSentence(next)
        }
    }

    fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        tts.setSpeed(speed)
        player.setSpeed(speed)
        // If actively speaking TTS, restart active sentence at new speed smoothly
        if (!_state.value.isMp3Active && _state.value.status == AudioStatus.PLAYING) {
            val idx = _state.value.activeSentenceIndex
            val full = _state.value.isPlayingFullPassage
            if (full) playFullPassage(idx) else playSentence(idx)
        }
    }

    fun setAccent(accent: String) {
        _state.update { it.copy(accent = accent, selectedVoiceId = "") }
        tts.setVoice("")
        tts.setAccent(accent)
    }

    fun selectVoice(voiceId: String) {
        _state.update { it.copy(selectedVoiceId = voiceId) }
        tts.setVoice(voiceId)
    }

    fun previewVoice(voiceId: String) {
        selectVoice(voiceId)
        speakWord("Hello! This voice will read your English passage.")
    }

    fun speakWord(word: String) {
        if (word.isBlank()) return
        stopMp3Internal()
        pausedOnIndex = null
        _state.update { it.copy(isPlayingFullPassage = false, isMp3Active = false, errorMessage = null) }
        tts.speak(
            text = word,
            utteranceId = "word-${System.nanoTime()}",
            onComplete = { _state.update { it.copy(status = AudioStatus.READY) } },
            onError = { err -> _state.update { it.copy(status = AudioStatus.ERROR, errorMessage = err) } }
        )
    }

    fun startReadAloudListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stop()
        if (!stt.isSupported()) {
            _state.update { it.copy(sttSupported = false, isListening = false) }
            onError("Speech recognition is not supported on this device.")
            return
        }
        _state.update { it.copy(isListening = true, errorMessage = null) }
        stt.startListening(
            localeTag = _state.value.accent,
            onPartial = onPartial,
            onFinal = { text ->
                _state.update { it.copy(isListening = false) }
                onFinal(text)
            },
            onError = { err ->
                _state.update { it.copy(isListening = false, errorMessage = err) }
                onError(err)
            }
        )
    }

    fun stopReadAloudListening() {
        stt.stopListening()
        _state.update { it.copy(isListening = false) }
    }

    fun cancelReadAloudListening() {
        stt.cancel()
        _state.update { it.copy(isListening = false) }
    }

    fun assessmentLabel(): String = assessment.label

    fun assessTextMatch(expectedText: String, spokenText: String): TextMatchComparisonResult =
        assessment.assessTextMatch(expectedText, spokenText)

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun release() {
        pausedOnIndex = null
        stt.release()
        player.release()
        tts.release()
        scope.cancel()
    }

    private fun stopMp3Internal() {
        if (_state.value.isMp3Active) {
            player.stop()
        }
    }

    private fun speakSentenceAt(index: Int, autoAdvance: Boolean) {
        val sentence = currentSentences.getOrNull(index) ?: return
        _state.update { it.copy(activeSentenceIndex = index, status = AudioStatus.LOADING) }
        val uttId = "sent-${sentence.sentenceId}-${System.nanoTime()}"
        tts.speak(
            text = sentence.en,
            utteranceId = uttId,
            onComplete = {
                onSentenceCompletedCallback?.invoke(index, sentence)
                if (autoAdvance && _state.value.isPlayingFullPassage && _state.value.status != AudioStatus.PAUSED) {
                    val next = index + 1
                    if (next < currentSentences.size) {
                        speakSentenceAt(next, autoAdvance = true)
                    } else {
                        _state.update {
                            it.copy(
                                isPlayingFullPassage = false,
                                status = AudioStatus.COMPLETED
                            )
                        }
                    }
                } else if (_state.value.status != AudioStatus.PAUSED) {
                    _state.update { it.copy(status = AudioStatus.COMPLETED) }
                }
            },
            onError = { err ->
                _state.update {
                    it.copy(
                        isPlayingFullPassage = false,
                        status = if (it.status == AudioStatus.UNSUPPORTED) AudioStatus.UNSUPPORTED else AudioStatus.ERROR,
                        errorMessage = err
                    )
                }
            }
        )
    }
}
