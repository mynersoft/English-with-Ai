package com.example.feature.call.engine

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.domain.model.CallParticipantSummary
import com.example.domain.model.CallProviderType
import com.example.domain.model.CallRoomSummary
import com.example.domain.model.CallSignalMessage
import com.example.domain.model.CallTransportSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallConnectionPhase {
    IDLE,
    RINGING,
    CONNECTING,
    CONNECTED,
    ENDED,
    ERROR
}

data class CallEngineState(
    val phase: CallConnectionPhase = CallConnectionPhase.IDLE,
    val providerType: CallProviderType = CallProviderType.FREE_P2P,
    val providerLabel: String = "Free WebRTC P2P ($0)",
    val localAudioMuted: Boolean = false,
    val localVideoEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val isSpeakerphone: Boolean = true,
    val hasCameraCapture: Boolean = false,
    val remoteVideoAvailable: Set<String> = emptySet(),
    val peerConnectionStates: Map<String, String> = emptyMap(),
    val remoteStreamsVersion: Int = 0,
    val errorMessage: String? = null
)

interface CallSignalingBridge {
    suspend fun sendSignal(toUserId: String, type: String, payload: String)
}

/**
 * Provider-agnostic media transport engine interface.
 * UI screens, CallViewModel, and CallRepository depend ONLY on this interface—never on WebRTC or
 * any paid CPaaS/SFU SDK directly—allowing zero-rewrite migration from Free P2P to LiveKit/Agora/Daily.
 */
interface CallEngine {
    val providerType: CallProviderType
    val state: StateFlow<CallEngineState>

    suspend fun startSession(
        room: CallRoomSummary,
        transport: CallTransportSession,
        signaling: CallSignalingBridge,
        audioMuted: Boolean,
        videoEnabled: Boolean
    )

    fun syncParticipants(participants: List<CallParticipantSummary>, selfUserId: String)

    suspend fun handleSignal(signal: CallSignalMessage)

    fun setMicMuted(muted: Boolean)

    fun setVideoEnabled(enabled: Boolean)

    fun switchCamera()

    fun setSpeakerphone(enabled: Boolean)

    @Composable
    fun LocalVideoSurface(modifier: Modifier)

    @Composable
    fun RemoteVideoSurface(userId: String, modifier: Modifier)

    fun release()
}

/**
 * Resolves the concrete CallEngine from the server-supplied CallProviderType.
 * To enable a paid SFU later (e.g., LiveKit Cloud), register a LiveKitCallEngine here—no UI or ViewModel changes needed.
 */
object CallEngineFactory {
    fun create(
        context: Context,
        provider: CallProviderType,
        paidProviderAdapterFactory: ((Context, CallProviderType) -> CallEngine)? = null
    ): CallEngine {
        return when (provider) {
            CallProviderType.FREE_P2P -> WebRtcMeshCallEngine(context.applicationContext)
            CallProviderType.LIVEKIT,
            CallProviderType.AGORA,
            CallProviderType.DAILY -> {
                paidProviderAdapterFactory?.invoke(context.applicationContext, provider)
                    ?: PaidSfuFallbackAdapter(provider, WebRtcMeshCallEngine(context.applicationContext))
            }
        }
    }
}

/**
 * Placeholder adapter that receives paid SFU credentials cleanly from the backend and delegates to
 * a drop-in SDK implementation when installed (or surfaces a clean diagnostic fallback).
 */
private class PaidSfuFallbackAdapter(
    override val providerType: CallProviderType,
    private val fallbackP2p: WebRtcMeshCallEngine
) : CallEngine {
    private val _state = MutableStateFlow(
        CallEngineState(
            providerType = providerType,
            providerLabel = "${providerType.name} Adapter"
        )
    )
    override val state: StateFlow<CallEngineState> = fallbackP2p.state

    override suspend fun startSession(
        room: CallRoomSummary,
        transport: CallTransportSession,
        signaling: CallSignalingBridge,
        audioMuted: Boolean,
        videoEnabled: Boolean
    ) {
        // When a paid SFU SDK (e.g., livekit-android) is linked, initialize it using transport.sfu?.token & serverUrl here.
        // Falling back cleanly to ICE/STUN mesh if paid SDK adapter is not yet linked preserves uninterrupted calls.
        fallbackP2p.startSession(room, transport, signaling, audioMuted, videoEnabled)
    }

    override fun syncParticipants(participants: List<CallParticipantSummary>, selfUserId: String) {
        fallbackP2p.syncParticipants(participants, selfUserId)
    }

    override suspend fun handleSignal(signal: CallSignalMessage) {
        fallbackP2p.handleSignal(signal)
    }

    override fun setMicMuted(muted: Boolean) = fallbackP2p.setMicMuted(muted)

    override fun setVideoEnabled(enabled: Boolean) = fallbackP2p.setVideoEnabled(enabled)

    override fun switchCamera() = fallbackP2p.switchCamera()

    override fun setSpeakerphone(enabled: Boolean) = fallbackP2p.setSpeakerphone(enabled)

    @Composable
    override fun LocalVideoSurface(modifier: Modifier) {
        fallbackP2p.LocalVideoSurface(modifier)
    }

    @Composable
    override fun RemoteVideoSurface(userId: String, modifier: Modifier) {
        if (state.value.remoteVideoAvailable.isEmpty() && _state.value.phase == CallConnectionPhase.ERROR) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text("SFU video", style = MaterialTheme.typography.labelSmall)
            }
        } else {
            fallbackP2p.RemoteVideoSurface(userId = userId, modifier = modifier)
        }
    }

    override fun release() = fallbackP2p.release()
}
