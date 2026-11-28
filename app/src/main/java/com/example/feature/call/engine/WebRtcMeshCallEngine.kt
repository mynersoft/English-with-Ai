package com.example.feature.call.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.domain.model.CallMode
import com.example.domain.model.CallParticipantSummary
import com.example.domain.model.CallProviderType
import com.example.domain.model.CallRoomSummary
import com.example.domain.model.CallSignalMessage
import com.example.domain.model.CallTransportSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Zero-cost ($0) Peer-to-Peer WebRTC Mesh Engine using free public STUN servers and
 * backend HTTP/MongoDB signaling. Supports 1-to-1 calls and small group calls seamlessly.
 */
class WebRtcMeshCallEngine(
    private val appContext: Context
) : CallEngine {

    override val providerType: CallProviderType = CallProviderType.FREE_P2P

    private val _state = MutableStateFlow(CallEngineState())
    override val state: StateFlow<CallEngineState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioRouter = CallAudioRouter(appContext)
    private val released = AtomicBoolean(false)

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var signalingBridge: CallSignalingBridge? = null
    private var rtcIceServers: List<PeerConnection.IceServer> = emptyList()
    private var selfUserId: String = ""
    private var currentMode: CallMode = CallMode.GROUP_PUBLIC

    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val remoteVideoTracks = ConcurrentHashMap<String, VideoTrack>()
    private val remoteDescReady = ConcurrentHashMap<String, Boolean>()
    private val pendingRemoteIce = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val localOfferCreated = ConcurrentHashMap<String, Boolean>()

    override suspend fun startSession(
        room: CallRoomSummary,
        transport: CallTransportSession,
        signaling: CallSignalingBridge,
        audioMuted: Boolean,
        videoEnabled: Boolean
    ) {
        if (released.get()) return
        signalingBridge = signaling
        selfUserId = room.selfUserId
        currentMode = room.mode

        _state.update {
            it.copy(
                phase = if (room.mode == CallMode.DIRECT && room.status == "ringing" && room.participantCount <= 1) {
                    CallConnectionPhase.RINGING
                } else {
                    CallConnectionPhase.CONNECTING
                },
                providerType = transport.provider,
                providerLabel = transport.providerLabel,
                localAudioMuted = audioMuted,
                localVideoEnabled = videoEnabled,
                isSpeakerphone = true,
                errorMessage = null
            )
        }

        rtcIceServers = transport.iceServers.mapNotNull { server ->
            if (server.urls.isEmpty()) return@mapNotNull null
            val builder = PeerConnection.IceServer.builder(server.urls)
            if (!server.username.isNullOrBlank()) builder.setUsername(server.username)
            if (!server.credential.isNullOrBlank()) builder.setPassword(server.credential)
            builder.createIceServer()
        }.ifEmpty {
            listOf(
                PeerConnection.IceServer.builder(
                    listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302")
                ).createIceServer(),
                PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
            )
        }

        initializeMediaPipeline(audioMuted = audioMuted, videoEnabled = videoEnabled)
        audioRouter.start(speakerOn = true)
        syncParticipants(room.participants, selfUserId)
    }

    private fun initializeMediaPipeline(audioMuted: Boolean, videoEnabled: Boolean) {
        if (factory != null) return
        ensureFactoryInitialized(appContext)
        val egl = EglBase.create()
        eglBase = egl

        val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
        val pcFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        factory = pcFactory

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasAudioPerm) {
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            audioSource = pcFactory.createAudioSource(audioConstraints)
            localAudioTrack = pcFactory.createAudioTrack("ENGAI_AUDIO_${System.currentTimeMillis()}", audioSource)?.apply {
                setEnabled(!audioMuted)
            }
        }

        val hasCameraPerm = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCameraPerm) {
            val capturer = createCameraCapturer(preferFront = true)
            if (capturer != null) {
                videoCapturer = capturer
                val helper = SurfaceTextureHelper.create("EngAiCapture", egl.eglBaseContext)
                surfaceTextureHelper = helper
                val vSource = pcFactory.createVideoSource(capturer.isScreencast)
                videoSource = vSource
                capturer.initialize(helper, appContext, vSource.capturerObserver)
                runCatching { capturer.startCapture(640, 480, 24) }
                localVideoTrack = pcFactory.createVideoTrack("ENGAI_VIDEO_${System.currentTimeMillis()}", vSource)?.apply {
                    setEnabled(videoEnabled)
                }
                _state.update { it.copy(hasCameraCapture = true, remoteStreamsVersion = it.remoteStreamsVersion + 1) }
            }
        }
    }

    private fun createCameraCapturer(preferFront: Boolean): CameraVideoCapturer? {
        return runCatching {
            val enumerator = Camera2Enumerator(appContext)
            val names = enumerator.deviceNames
            val preferred = names.firstOrNull {
                if (preferFront) enumerator.isFrontFacing(it) else enumerator.isBackFacing(it)
            } ?: names.firstOrNull()
            preferred?.let { enumerator.createCapturer(it, null) }
        }.getOrNull()
    }

    override fun syncParticipants(participants: List<CallParticipantSummary>, selfUserId: String) {
        if (released.get() || factory == null) return
        if (selfUserId.isNotBlank()) {
            this.selfUserId = selfUserId
        }
        val effectiveSelf = this.selfUserId
        val remoteIds = participants.map { it.userId }.filter { it.isNotBlank() && it != effectiveSelf }.toSet()

        // Close stale peer connections for users who left
        val currentPeers = peerConnections.keys.toList()
        for (peerId in currentPeers) {
            if (!remoteIds.contains(peerId)) {
                closePeer(peerId)
            }
        }

        // Open peer connections for newly joined participants
        for (peerId in remoteIds) {
            val pc = getOrCreatePeerConnection(peerId) ?: continue
            // Deterministic polite-offer rule prevents glare in both 1-to-1 and N-way mesh:
            // lexicographically smaller userId initiates the SDP offer.
            val shouldInitiateOffer = effectiveSelf.isNotBlank() && effectiveSelf < peerId
            if (shouldInitiateOffer && localOfferCreated.putIfAbsent(peerId, true) == null) {
                createAndSendOffer(peerId, pc)
            }
        }

        _state.update { cur ->
            val phase = when {
                remoteIds.isEmpty() && currentMode == CallMode.DIRECT && cur.phase == CallConnectionPhase.RINGING ->
                    CallConnectionPhase.RINGING
                cur.peerConnectionStates.values.any { st -> st == "CONNECTED" || st == "COMPLETED" } ->
                    CallConnectionPhase.CONNECTED
                remoteIds.isNotEmpty() ->
                    CallConnectionPhase.CONNECTING
                else -> cur.phase
            }
            cur.copy(phase = phase)
        }
    }

    private fun getOrCreatePeerConnection(peerId: String): PeerConnection? {
        peerConnections[peerId]?.let { return it }
        val pcFactory = factory ?: return null

        val rtcConfig = PeerConnection.RTCConfiguration(rtcIceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                val label = newState?.name ?: return
                _state.update { cur ->
                    val nextMap = cur.peerConnectionStates + (peerId to label)
                    val anyConnected = nextMap.values.any { it == "CONNECTED" || it == "COMPLETED" }
                    cur.copy(
                        peerConnectionStates = nextMap,
                        phase = if (anyConnected) CallConnectionPhase.CONNECTED else cur.phase
                    )
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                val ice = candidate ?: return
                val payload = JSONObject()
                    .put("sdpMid", ice.sdpMid ?: "")
                    .put("sdpMLineIndex", ice.sdpMLineIndex)
                    .put("candidate", ice.sdp)
                    .toString()
                scope.launch {
                    runCatching {
                        signalingBridge?.sendSignal(peerId, "ice", payload)
                    }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                val track = stream?.videoTracks?.firstOrNull() ?: return
                registerRemoteVideoTrack(peerId, track)
            }

            override fun onRemoveStream(stream: MediaStream?) {
                remoteVideoTracks.remove(peerId)
                _state.update {
                    it.copy(
                        remoteVideoAvailable = remoteVideoTracks.keys.toSet(),
                        remoteStreamsVersion = it.remoteStreamsVersion + 1
                    )
                }
            }

            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                val track = receiver?.track() as? VideoTrack ?: return
                registerRemoteVideoTrack(peerId, track)
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track() as? VideoTrack ?: return
                registerRemoteVideoTrack(peerId, track)
            }
        }

        val pc = pcFactory.createPeerConnection(rtcConfig, observer) ?: return null
        val streamIds = listOf("engai_stream")
        localAudioTrack?.let { pc.addTrack(it, streamIds) }
        localVideoTrack?.let { pc.addTrack(it, streamIds) }
        peerConnections[peerId] = pc
        return pc
    }

    private fun registerRemoteVideoTrack(peerId: String, track: VideoTrack) {
        remoteVideoTracks[peerId] = track
        _state.update {
            it.copy(
                phase = CallConnectionPhase.CONNECTED,
                remoteVideoAvailable = remoteVideoTracks.keys.toSet(),
                remoteStreamsVersion = it.remoteStreamsVersion + 1
            )
        }
    }

    private fun createAndSendOffer(peerId: String, pc: PeerConnection) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc.createOffer(object : SimpleSdpObserver("createOffer") {
            override fun onCreateSuccess(desc: SessionDescription?) {
                val sdp = desc ?: return
                pc.setLocalDescription(SimpleSdpObserver("setLocalOffer") {
                    val payload = JSONObject()
                        .put("type", sdp.type.canonicalForm())
                        .put("sdp", sdp.description)
                        .toString()
                    scope.launch {
                        runCatching { signalingBridge?.sendSignal(peerId, "offer", payload) }
                    }
                }, sdp)
            }
        }, constraints)
    }

    override suspend fun handleSignal(signal: CallSignalMessage) {
        if (released.get() || factory == null) return
        val peerId = signal.fromUserId
        if (peerId.isBlank() || peerId == selfUserId) return

        when (signal.type.lowercase()) {
            "bye" -> {
                closePeer(peerId)
            }

            "offer", "renegotiate" -> {
                val json = runCatching { JSONObject(signal.payload) }.getOrNull() ?: return
                val sdpText = json.optString("sdp").takeIf { it.isNotBlank() } ?: return
                val pc = getOrCreatePeerConnection(peerId) ?: return
                val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, sdpText)
                pc.setRemoteDescription(SimpleSdpObserver("setRemoteOffer") {
                    remoteDescReady[peerId] = true
                    drainPendingIce(peerId, pc)
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    }
                    pc.createAnswer(object : SimpleSdpObserver("createAnswer") {
                        override fun onCreateSuccess(answerDesc: SessionDescription?) {
                            val ans = answerDesc ?: return
                            pc.setLocalDescription(SimpleSdpObserver("setLocalAnswer") {
                                val payload = JSONObject()
                                    .put("type", ans.type.canonicalForm())
                                    .put("sdp", ans.description)
                                    .toString()
                                scope.launch {
                                    runCatching { signalingBridge?.sendSignal(peerId, "answer", payload) }
                                }
                            }, ans)
                        }
                    }, constraints)
                }, remoteDesc)
            }

            "answer" -> {
                val json = runCatching { JSONObject(signal.payload) }.getOrNull() ?: return
                val sdpText = json.optString("sdp").takeIf { it.isNotBlank() } ?: return
                val pc = peerConnections[peerId] ?: return
                val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdpText)
                pc.setRemoteDescription(SimpleSdpObserver("setRemoteAnswer") {
                    remoteDescReady[peerId] = true
                    drainPendingIce(peerId, pc)
                }, remoteDesc)
            }

            "ice" -> {
                val json = runCatching { JSONObject(signal.payload) }.getOrNull() ?: return
                val candidate = json.optString("candidate").takeIf { it.isNotBlank() } ?: return
                val sdpMid = json.optString("sdpMid")
                val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)
                val ice = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                val pc = getOrCreatePeerConnection(peerId) ?: return
                if (remoteDescReady[peerId] == true) {
                    runCatching { pc.addIceCandidate(ice) }
                } else {
                    val queue = pendingRemoteIce.getOrPut(peerId) { mutableListOf() }
                    synchronized(queue) { queue.add(ice) }
                }
            }
        }
    }

    private fun drainPendingIce(peerId: String, pc: PeerConnection) {
        val queue = pendingRemoteIce.remove(peerId) ?: return
        synchronized(queue) {
            queue.forEach { candidate ->
                runCatching { pc.addIceCandidate(candidate) }
            }
            queue.clear()
        }
    }

    private fun closePeer(peerId: String) {
        remoteVideoTracks.remove(peerId)
        remoteDescReady.remove(peerId)
        pendingRemoteIce.remove(peerId)
        localOfferCreated.remove(peerId)
        peerConnections.remove(peerId)?.let { pc ->
            runCatching { pc.close() }
            runCatching { pc.dispose() }
        }
        _state.update {
            it.copy(
                remoteVideoAvailable = remoteVideoTracks.keys.toSet(),
                peerConnectionStates = it.peerConnectionStates - peerId,
                remoteStreamsVersion = it.remoteStreamsVersion + 1
            )
        }
    }

    override fun setMicMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        _state.update { it.copy(localAudioMuted = muted) }
    }

    override fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        _state.update { it.copy(localVideoEnabled = enabled) }
    }

    override fun switchCamera() {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                _state.update { it.copy(isFrontCamera = isFront) }
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                Log.w(TAG, "Camera switch error: $errorDescription")
            }
        })
    }

    override fun setSpeakerphone(enabled: Boolean) {
        audioRouter.setSpeakerphone(enabled)
        _state.update { it.copy(isSpeakerphone = enabled) }
    }

    @Composable
    override fun LocalVideoSurface(modifier: Modifier) {
        val egl = eglBase ?: return
        val track = localVideoTrack ?: return
        val isFront = _state.value.isFrontCamera
        WebRtcVideoView(
            eglContext = egl.eglBaseContext,
            videoTrack = track,
            mirror = isFront,
            isOverlay = true,
            modifier = modifier
        )
    }

    @Composable
    override fun RemoteVideoSurface(userId: String, modifier: Modifier) {
        val egl = eglBase ?: return
        val track = remoteVideoTracks[userId] ?: return
        WebRtcVideoView(
            eglContext = egl.eglBaseContext,
            videoTrack = track,
            mirror = false,
            isOverlay = false,
            modifier = modifier
        )
    }

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        _state.update { it.copy(phase = CallConnectionPhase.ENDED) }
        peerConnections.keys.toList().forEach { closePeer(it) }
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoCapturer = null
        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        runCatching { factory?.dispose() }
        factory = null
        runCatching { eglBase?.release() }
        eglBase = null
        audioRouter.release()
        scope.cancel()
    }

    private companion object {
        const val TAG = "WebRtcMeshCallEngine"
        val factoryInitOnce = AtomicBoolean(false)

        fun ensureFactoryInitialized(context: Context) {
            if (factoryInitOnce.compareAndSet(false, true)) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
            }
        }
    }
}

@Composable
private fun WebRtcVideoView(
    eglContext: EglBase.Context,
    videoTrack: VideoTrack,
    mirror: Boolean,
    isOverlay: Boolean,
    modifier: Modifier = Modifier
) {
    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(videoTrack, rendererRef) {
        val view = rendererRef
        if (view != null) {
            runCatching { videoTrack.addSink(view) }
        }
        onDispose {
            if (view != null) {
                runCatching { videoTrack.removeSink(view) }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            rendererRef?.let { view ->
                runCatching { view.release() }
            }
            rendererRef = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                setZOrderMediaOverlay(isOverlay)
                rendererRef = this
            }
        },
        update = { view ->
            view.setMirror(mirror)
        }
    )
}

private open class SimpleSdpObserver(
    private val label: String,
    private val onSetSuccessAction: (() -> Unit)? = null
) : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {
        onSetSuccessAction?.invoke()
    }

    override fun onCreateFailure(error: String?) {
        Log.w("WebRtcSdp", "$label create failure: $error")
    }

    override fun onSetFailure(error: String?) {
        Log.w("WebRtcSdp", "$label set failure: $error")
    }
}
