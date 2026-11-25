package com.example.feature.reading.audio

import android.content.Context

class ProviderRegistry(
    context: Context,
    private val config: AudioProviderConfig = AudioProviderConfig()
) {
    private val appContext = context.applicationContext

    fun createTtsProvider(): TtsProvider = when (config.ttsProviderId) {
        AudioProviderConfig.PROVIDER_ANDROID_TTS -> AndroidTtsProvider(appContext)
        else -> AndroidTtsProvider(appContext)
    }

    fun createSttProvider(): SttProvider = when (config.sttProviderId) {
        AudioProviderConfig.PROVIDER_ANDROID_STT -> AndroidSttProvider(appContext)
        else -> AndroidSttProvider(appContext)
    }

    fun createAudioPlayerProvider(): AudioPlayerProvider = when (config.playerProviderId) {
        AudioProviderConfig.PROVIDER_ANDROID_MEDIA_PLAYER -> AndroidMediaPlayerProvider()
        else -> AndroidMediaPlayerProvider()
    }

    fun createPronunciationAssessmentProvider(): PronunciationAssessmentProvider =
        when (config.assessmentProviderId) {
            AudioProviderConfig.PROVIDER_LOCAL_TEXT_MATCHING -> TextMatchingAssessmentProvider()
            else -> TextMatchingAssessmentProvider()
        }
}
