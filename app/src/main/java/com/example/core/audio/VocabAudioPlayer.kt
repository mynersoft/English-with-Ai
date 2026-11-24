package com.example.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Plays word pronunciation from audioUrl if present, falling back seamlessly
 * to Android's built-in English TextToSpeech.
 */
class VocabAudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var pendingSpeak: Pair<String, (() -> Unit)?>? = null
    private var onDoneCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.9f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        notifyDone()
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        notifyDone()
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        notifyDone()
                    }
                })
                ttsReady = true
                pendingSpeak?.let { (text, cb) ->
                    pendingSpeak = null
                    speakWithTts(text, cb)
                }
            } else {
                ttsFailed = true
                pendingSpeak?.second?.let { cb -> mainHandler.post { cb() } }
                pendingSpeak = null
            }
        }
    }

    fun play(wordOrSentence: String, audioUrl: String? = null, onDone: (() -> Unit)? = null) {
        stop()
        if (!audioUrl.isNullOrBlank()) {
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(audioUrl)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener {
                        releaseMediaPlayer()
                        mainHandler.post { onDone?.invoke() }
                    }
                    setOnErrorListener { _, _, _ ->
                        releaseMediaPlayer()
                        speakWithTts(wordOrSentence, onDone)
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = player
                return
            } catch (_: Exception) {
                releaseMediaPlayer()
            }
        }
        speakWithTts(wordOrSentence, onDone)
    }

    private fun speakWithTts(text: String, onDone: (() -> Unit)?) {
        if (ttsFailed || text.isBlank()) {
            mainHandler.post { onDone?.invoke() }
            return
        }
        if (!ttsReady) {
            pendingSpeak = text to onDone
            return
        }
        onDoneCallback = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vocab-${System.nanoTime()}")
    }

    fun stop() {
        pendingSpeak = null
        onDoneCallback = null
        releaseMediaPlayer()
        runCatching { tts?.stop() }
    }

    fun release() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }

    private fun notifyDone() {
        val cb = onDoneCallback
        onDoneCallback = null
        if (cb != null) {
            mainHandler.post { cb() }
        }
    }

    private fun releaseMediaPlayer() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }
}
