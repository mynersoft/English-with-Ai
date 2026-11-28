package com.example.feature.speaking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Wraps Android SpeechRecognizer (speech-to-text) and TextToSpeech. Free, runs on the phone. */
class SpeechHelper(context: Context) {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var pendingSpeak: Triple<String, Float, (() -> Unit)?>? = null
    private var onSpeakDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.9f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { finishSpeaking() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { finishSpeaking() }
                    override fun onError(utteranceId: String?, errorCode: Int) { finishSpeaking() }
                })
                ttsReady = true
                pendingSpeak?.let { (text, rate, done) ->
                    pendingSpeak = null
                    speak(text, rate, done)
                }
            } else {
                ttsFailed = true
                pendingSpeak?.third?.invoke()
                pendingSpeak = null
            }
        }
    }

    private fun finishSpeaking() {
        val cb = onSpeakDone
        onSpeakDone = null
        cb?.invoke()
    }

    fun speak(text: String, onDone: (() -> Unit)?) {
        speak(text = text, speechRate = 0.9f, onDone = onDone)
    }

    fun speak(text: String, speechRate: Float = 0.9f, onDone: (() -> Unit)? = null) {
        if (ttsFailed || text.isBlank()) {
            onDone?.invoke()
            return
        }
        if (!ttsReady) {
            pendingSpeak = Triple(text, speechRate, onDone)
            return
        }
        onSpeakDone = onDone
        tts?.setSpeechRate(speechRate.coerceIn(0.6f, 1.25f))
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt-${System.nanoTime()}")
    }

    fun stopSpeaking() {
        onSpeakDone = null
        pendingSpeak = null
        tts?.stop()
    }

    fun isInlineRecognitionAvailable(): Boolean {
        return runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)
    }

    fun createRecognizerIntent(prompt: String = "Speak in English to practice pronunciation"): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
    }

    fun canLaunchVoiceActivity(): Boolean {
        return runCatching {
            createRecognizerIntent().resolveActivity(appContext.packageManager) != null
        }.getOrDefault(false)
    }

    /** Must be called on the main thread. */
    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onErrorMessage: (String) -> Unit,
        onReady: () -> Unit = {},
        onRmsChanged: (Float) -> Unit = {}
    ) {
        if (!isInlineRecognitionAvailable()) {
            onErrorMessage("Speech recognition is not available on this phone. You can type instead.")
            return
        }
        destroyRecognizer()
        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
        if (r == null) {
            onErrorMessage("Could not start microphone speech recognition. Please try again.")
            return
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onReady()
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                onRmsChanged(normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                onRmsChanged(0f)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                destroyRecognizer()
                if (text.isNullOrBlank()) {
                    onErrorMessage("I did not hear you clearly. Tap the mic and try again.")
                } else {
                    onFinal(text)
                }
            }

            override fun onError(error: Int) {
                destroyRecognizer()
                onErrorMessage(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "I did not hear you clearly. Tap the mic and try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission is needed to practice speaking."
                        SpeechRecognizer.ERROR_AUDIO ->
                            "Could not access the microphone. Close other audio apps and try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Microphone recognizer is busy. Please wait a moment and try again."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech recognition needs internet on this phone."
                        SpeechRecognizer.ERROR_CLIENT ->
                            "Voice input stopped. Tap the mic to try again."
                        else -> "Could not listen. Please try again."
                    }
                )
            }
        })
        val intent = createRecognizerIntent()
        try {
            r.startListening(intent)
        } catch (_: SecurityException) {
            destroyRecognizer()
            onErrorMessage("Microphone permission is needed to practice speaking.")
        } catch (_: Exception) {
            destroyRecognizer()
            onErrorMessage("Could not start voice recognition. Please try again.")
        }
    }

    /** Stops recording; the recognizer then delivers the final text through onFinal. Returns false if no active recognizer. */
    fun stopListening(): Boolean {
        val active = recognizer ?: return false
        return try {
            active.stopListening()
            true
        } catch (_: Exception) {
            destroyRecognizer()
            false
        }
    }

    fun cancelListening() {
        runCatching { recognizer?.cancel() }
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    fun release() {
        cancelListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
    }
}
