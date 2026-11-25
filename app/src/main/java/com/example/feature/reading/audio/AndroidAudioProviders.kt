package com.example.feature.reading.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.domain.model.TextMatchComparisonResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class AndroidTtsProvider(context: Context) : TtsProvider {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(AudioStatus.LOADING)
    override val status: StateFlow<AudioStatus> = _status.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoiceOption>>(emptyList())
    override val availableVoices: StateFlow<List<TtsVoiceOption>> = _availableVoices.asStateFlow()

    private var tts: TextToSpeech? = null
    private var currentAccent: String = "en-US"
    private var requestedVoiceId: String = ""
    private var currentSpeed: Float = 1.0f
    private var isInitialized = false
    private var isUnsupported = false

    private var activeUtteranceId: String? = null
    private var onCompleteCb: (() -> Unit)? = null
    private var onErrorCb: ((String) -> Unit)? = null

    override fun initialize(accent: String, voiceId: String, speed: Float) {
        currentAccent = accent
        requestedVoiceId = voiceId
        currentSpeed = speed
        if (tts != null) {
            applyConfig()
            return
        }
        _status.value = AudioStatus.LOADING
        try {
            tts = TextToSpeech(appContext) { result ->
                mainHandler.post {
                    if (result == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        isUnsupported = false
                        attachUtteranceListener()
                        loadVoices()
                        applyConfig()
                        _status.value = AudioStatus.READY
                    } else {
                        isUnsupported = true
                        _status.value = AudioStatus.UNSUPPORTED
                    }
                }
            }
        } catch (_: Exception) {
            isUnsupported = true
            _status.value = AudioStatus.UNSUPPORTED
        }
    }

    private fun attachUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId == activeUtteranceId) {
                        _status.value = AudioStatus.PLAYING
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId == activeUtteranceId) {
                        val cb = onCompleteCb
                        clearCallbacks()
                        _status.value = AudioStatus.COMPLETED
                        cb?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handleTtsError(utteranceId, "Speech synthesis error.")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleTtsError(utteranceId, "Speech synthesis error ($errorCode).")
            }
        })
    }

    private fun handleTtsError(utteranceId: String?, msg: String) {
        mainHandler.post {
            if (utteranceId == null || utteranceId == activeUtteranceId) {
                val cb = onErrorCb
                clearCallbacks()
                _status.value = AudioStatus.ERROR
                cb?.invoke(msg)
            }
        }
    }

    private fun loadVoices() {
        val engine = tts ?: return
        val voiceSet: Set<Voice> = runCatching { engine.voices }.getOrNull() ?: emptySet()
        val options = voiceSet
            .filter { it.locale?.language?.equals("en", ignoreCase = true) == true }
            .sortedWith(compareBy<Voice> { it.locale.toLanguageTag() }.thenBy { it.name })
            .map { v ->
                val country = v.locale?.country?.ifBlank { "" }.orEmpty()
                val label = buildString {
                    append(if (country.equals("GB", true)) "UK" else if (country.equals("US", true)) "US" else country.ifBlank { "EN" })
                    append(" · ")
                    append(v.name.substringAfterLast('-').ifBlank { v.name }.take(22))
                    if (v.isNetworkConnectionRequired) append(" (Net)")
                }
                TtsVoiceOption(
                    id = v.name,
                    name = label,
                    localeTag = v.locale?.toLanguageTag() ?: "en-US",
                    isNetworkRequired = v.isNetworkConnectionRequired
                )
            }
        _availableVoices.value = options
    }

    private fun applyConfig() {
        val engine = tts ?: return
        if (!isInitialized) return

        val locale = if (currentAccent == "en-GB") Locale.UK else Locale.US
        engine.language = locale
        engine.setSpeechRate(currentSpeed.coerceIn(0.5f, 2.0f))

        if (requestedVoiceId.isNotBlank()) {
            val match = runCatching {
                engine.voices?.firstOrNull { it.name == requestedVoiceId }
            }.getOrNull()
            if (match != null) {
                engine.voice = match
            }
        }
    }

    override fun setAccent(accent: String) {
        currentAccent = accent
        applyConfig()
    }

    override fun setVoice(voiceId: String) {
        requestedVoiceId = voiceId
        applyConfig()
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
        tts?.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
    }

    override fun speak(
        text: String,
        utteranceId: String,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isUnsupported) {
            _status.value = AudioStatus.UNSUPPORTED
            onError("Text-to-Speech is not supported on this device.")
            return
        }
        val engine = tts
        if (engine == null || !isInitialized) {
            initialize(currentAccent, requestedVoiceId, currentSpeed)
            onError("Audio engine is still loading. Please try again.")
            return
        }
        clearCallbacks()
        activeUtteranceId = utteranceId
        onCompleteCb = onComplete
        onErrorCb = onError
        _status.value = AudioStatus.LOADING

        val res = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (res != TextToSpeech.SUCCESS) {
            clearCallbacks()
            _status.value = AudioStatus.ERROR
            onError("Unable to start speech playback.")
        }
    }

    override fun stop() {
        clearCallbacks()
        tts?.stop()
        if (isInitialized) {
            _status.value = AudioStatus.READY
        }
    }

    override fun release() {
        clearCallbacks()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun clearCallbacks() {
        activeUtteranceId = null
        onCompleteCb = null
        onErrorCb = null
    }
}

class AndroidSttProvider(context: Context) : SttProvider {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    override fun isSupported(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(appContext) }.getOrDefault(false)

    override fun startListening(
        localeTag: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSupported()) {
            onError("Speech recognition is not supported on this device.")
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
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onPartial(text)
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                destroyRecognizer()
                if (text.isNullOrBlank()) {
                    onError("Could not hear speech clearly. Please try again.")
                } else {
                    onFinal(text)
                }
            }

            override fun onError(error: Int) {
                destroyRecognizer()
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        "No speech detected. Tap the mic and read aloud."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is required for Read Aloud mode."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Network error during speech recognition."
                    else -> "Speech recognition failed (code $error). Try Manual Reading Mode."
                }
                onError(msg)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag.ifBlank { "en-US" })
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        r.startListening(intent)
    }

    override fun stopListening() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
        destroyRecognizer()
    }

    override fun release() {
        cancel()
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }
}

class AndroidMediaPlayerProvider : AudioPlayerProvider {
    private val _status = MutableStateFlow(AudioStatus.READY)
    override val status: StateFlow<AudioStatus> = _status.asStateFlow()

    private var player: MediaPlayer? = null
    private var currentSpeed: Float = 1.0f

    override fun playUrl(
        url: String,
        speed: Float,
        onPrepared: () -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        stop()
        currentSpeed = speed
        _status.value = AudioStatus.LOADING
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                applyPlaybackSpeed(it, currentSpeed)
                it.start()
                _status.value = AudioStatus.PLAYING
                onPrepared()
            }
            mp.setOnCompletionListener {
                _status.value = AudioStatus.COMPLETED
                onComplete()
            }
            mp.setOnErrorListener { _, what, _ ->
                stop()
                _status.value = AudioStatus.ERROR
                onError("MP3 audio playback error ($what).")
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stop()
            _status.value = AudioStatus.ERROR
            onError(e.message ?: "Unable to play audio URL.")
        }
    }

    override fun pause() {
        val mp = player ?: return
        if (mp.isPlaying) {
            mp.pause()
            _status.value = AudioStatus.PAUSED
        }
    }

    override fun resume() {
        val mp = player ?: return
        if (!mp.isPlaying) {
            applyPlaybackSpeed(mp, currentSpeed)
            mp.start()
            _status.value = AudioStatus.PLAYING
        }
    }

    override fun stop() {
        runCatching {
            player?.stop()
        }
        runCatching {
            player?.release()
        }
        player = null
        _status.value = AudioStatus.READY
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
        player?.let { applyPlaybackSpeed(it, speed) }
    }

    override fun release() {
        stop()
    }

    private fun applyPlaybackSpeed(mp: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val params: PlaybackParams = mp.playbackParams ?: PlaybackParams()
                params.speed = speed.coerceIn(0.5f, 2.0f)
                mp.playbackParams = params
            }
        }
    }
}

class TextMatchingAssessmentProvider : PronunciationAssessmentProvider {
    override val label: String = "Text Matching"

    override fun assessTextMatch(
        expectedText: String,
        spokenText: String
    ): TextMatchComparisonResult = TextMatchComparator.compare(expectedText, spokenText)
}
