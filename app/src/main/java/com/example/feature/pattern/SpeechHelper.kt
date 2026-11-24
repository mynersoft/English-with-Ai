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
    private var pendingSpeak: Pair<String, (() -> Unit)?>? = null
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
                pendingSpeak?.let { (text, done) -> pendingSpeak = null; speak(text, done) }
            } else {
                ttsFailed = true
                pendingSpeak?.second?.invoke()
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
        if (ttsFailed) { onDone?.invoke(); return }
        if (!ttsReady) { pendingSpeak = text to onDone; return }
        onSpeakDone = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utt-${System.nanoTime()}")
    }

    fun stopSpeaking() {
        onSpeakDone = null
        pendingSpeak = null
        tts?.stop()
    }

    /** Must be called on the main thread. */
    fun startListening(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onErrorMessage: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onErrorMessage("Speech recognition is not available on this phone. You can type instead.")
            return
        }
        destroyRecognizer()
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                destroyRecognizer()
                if (text.isNullOrBlank()) onErrorMessage("I did not hear you. Tap the mic and try again.") else onFinal(text)
            }

            override fun onError(error: Int) {
                destroyRecognizer()
                onErrorMessage(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "I did not hear you. Tap the mic and try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission is needed to talk."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech recognition needs internet on this phone."
                        else -> "Could not listen. Please try again."
                    }
                )
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    /** Stops recording; the recognizer then delivers the final text through onFinal. */
    fun stopListening() {
        recognizer?.stopListening()
    }

    fun cancelListening() {
        recognizer?.cancel()
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    fun release() {
        cancelListening()
        stopSpeaking()
        tts?.shutdown()
        tts = null
    }
}
