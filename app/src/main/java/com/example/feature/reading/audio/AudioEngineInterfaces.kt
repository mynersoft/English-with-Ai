package com.example.feature.reading.audio

import com.example.domain.model.TextMatchComparisonResult
import kotlinx.coroutines.flow.StateFlow

enum class AudioStatus(val label: String) {
    READY("Ready"),
    LOADING("Loading"),
    PLAYING("Playing"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    ERROR("Error"),
    UNSUPPORTED("Unsupported")
}

data class TtsVoiceOption(
    val id: String,
    val name: String,
    val localeTag: String,
    val isNetworkRequired: Boolean = false
)

data class AudioProviderConfig(
    val ttsProviderId: String = PROVIDER_ANDROID_TTS,
    val sttProviderId: String = PROVIDER_ANDROID_STT,
    val playerProviderId: String = PROVIDER_ANDROID_MEDIA_PLAYER,
    val assessmentProviderId: String = PROVIDER_LOCAL_TEXT_MATCHING
) {
    companion object {
        const val PROVIDER_ANDROID_TTS = "android_tts"
        const val PROVIDER_ANDROID_STT = "android_stt"
        const val PROVIDER_ANDROID_MEDIA_PLAYER = "android_mediaplayer"
        const val PROVIDER_LOCAL_TEXT_MATCHING = "local_text_matching"
    }
}

interface TtsProvider {
    val status: StateFlow<AudioStatus>
    val availableVoices: StateFlow<List<TtsVoiceOption>>
    fun initialize(accent: String, voiceId: String, speed: Float)
    fun setAccent(accent: String)
    fun setVoice(voiceId: String)
    fun setSpeed(speed: Float)
    fun speak(text: String, utteranceId: String, onComplete: () -> Unit, onError: (String) -> Unit)
    fun stop()
    fun release()
}

interface SttProvider {
    fun isSupported(): Boolean
    fun startListening(
        localeTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    )
    fun stopListening()
    fun cancel()
    fun release()
}

interface AudioPlayerProvider {
    val status: StateFlow<AudioStatus>
    fun playUrl(
        url: String,
        speed: Float,
        onPrepared: () -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    )
    fun pause()
    fun resume()
    fun stop()
    fun setSpeed(speed: Float)
    fun release()
}

interface PronunciationAssessmentProvider {
    val label: String
    fun assessTextMatch(expectedText: String, spokenText: String): TextMatchComparisonResult
}
