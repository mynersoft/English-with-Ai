package com.example.feature.call.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Manages VoIP call audio focus and speakerphone/earpiece routing across API 24-36.
 * Uses AudioManager.setCommunicationDevice() on Android 12+ (API 31+) per Android 14+ VoIP guidelines.
 */
class CallAudioRouter(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var previousMode: Int = AudioManager.MODE_NORMAL
    private var focusRequest: AudioFocusRequest? = null
    private var active: Boolean = false

    fun start(speakerOn: Boolean = true) {
        val am = audioManager ?: return
        if (!active) {
            previousMode = am.mode
            active = true
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerphone(speakerOn)
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        val am = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val targetType = if (enabled) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                val match = devices.firstOrNull { it.type == targetType }
                if (match != null) {
                    am.setCommunicationDevice(match)
                } else if (!enabled) {
                    am.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = enabled
            }
        }
    }

    fun release() {
        val am = audioManager ?: return
        if (!active) return
        active = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
            }
            am.mode = previousMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }
}
