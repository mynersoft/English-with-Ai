package com.example.feature.call

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.call.ActiveCallTracker
import com.example.core.call.CallInviteNotifier
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.example.domain.model.CallMode
import com.example.domain.model.CallProviderConfig
import com.example.domain.model.CallProviderType
import com.example.domain.model.CallRoomSummary
import com.example.domain.model.CallTransportSession
import com.example.domain.model.ChatStudentSummary
import com.example.feature.call.engine.CallConnectionPhase
import com.example.feature.call.engine.CallEngine
import com.example.feature.call.engine.CallEngineFactory
import com.example.feature.call.engine.CallEngineState
import com.example.feature.call.engine.CallSignalingBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CallLobbyUiState(
    val loading: Boolean = false,
    val config: CallProviderConfig = CallProviderConfig(),
    val publicRooms: List<CallRoomSummary> = emptyList(),
    val privateRooms: List<CallRoomSummary> = emptyList(),
    val directCalls: List<CallRoomSummary> = emptyList(),
    val incomingInvites: List<CallRoomSummary> = emptyList(),
    val students: List<ChatStudentSummary> = emptyList(),
    val studentSearch: String = "",
    val loadingStudents: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null
)

data class ActiveCallUiState(
    val room: CallRoomSummary? = null,
    val transport: CallTransportSession? = null,
    val engineState: CallEngineState = CallEngineState(),
    val elapsedSeconds: Int = 0,
    val joining: Boolean = false,
    val endedNotice: String? = null,
    val error: String? = null
)

class CallViewModel(
    private val callRepo: CallRepository = CallRepository(),
    private val chatRepo: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _lobby = MutableStateFlow(CallLobbyUiState())
    val lobby: StateFlow<CallLobbyUiState> = _lobby.asStateFlow()

    private val _activeCall = MutableStateFlow(ActiveCallUiState())
    val activeCall: StateFlow<ActiveCallUiState> = _activeCall.asStateFlow()

    private val _activeEngine = MutableStateFlow<CallEngine?>(null)
    val activeEngine: StateFlow<CallEngine?> = _activeEngine.asStateFlow()

    private var pollJob: Job? = null
    private var timerJob: Job? = null
    private var engineStateJob: Job? = null
    private var lastSignalCursorIso: String? = null

    fun loadLobby(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _lobby.update { it.copy(loading = true, error = null) }
            }
            callRepo.fetchLobby()
                .onSuccess { snap ->
                    _lobby.update {
                        it.copy(
                            loading = false,
                            config = snap.config,
                            publicRooms = snap.publicRooms,
                            privateRooms = snap.privateRooms,
                            directCalls = snap.directCalls,
                            incomingInvites = snap.incomingInvites,
                            error = null
                        )
                    }
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(
                            loading = false,
                            error = if (silent) it.error else (err.message ?: "Failed to load video rooms.")
                        )
                    }
                }
        }
    }

    fun refreshIncomingInvites() {
        if (_activeCall.value.room != null) return
        viewModelScope.launch {
            callRepo.fetchIncomingInvites()
                .onSuccess { invites ->
                    _lobby.update { it.copy(incomingInvites = invites) }
                }
        }
    }

    fun searchStudents(query: String) {
        _lobby.update { it.copy(studentSearch = query, loadingStudents = true) }
        viewModelScope.launch {
            chatRepo.searchStudents(query)
                .onSuccess { list ->
                    _lobby.update { it.copy(loadingStudents = false, students = list) }
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(loadingStudents = false, error = err.message ?: "Failed to load students.")
                    }
                }
        }
    }

    fun startDirectCall(
        context: Context,
        targetUserId: String,
        topic: String = "1-on-1 English Video Call",
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ) {
        if (_lobby.value.busy) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _lobby.update { it.copy(busy = true, error = null) }
            callRepo.createDirectCall(
                targetUserId = targetUserId,
                topic = topic,
                audioMuted = audioMuted,
                videoEnabled = videoEnabled
            )
                .onSuccess { joined ->
                    _lobby.update { it.copy(busy = false) }
                    beginEngineSession(appContext, joined.room, joined.transport, audioMuted, videoEnabled)
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(busy = false, error = err.message ?: "Unable to start 1-on-1 call.")
                    }
                }
        }
    }

    fun createGroupRoom(
        context: Context,
        mode: CallMode,
        title: String,
        topic: String,
        learningLevel: String,
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true,
        onCreated: () -> Unit = {}
    ) {
        if (_lobby.value.busy || title.trim().length < 2) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _lobby.update { it.copy(busy = true, error = null) }
            callRepo.createGroupRoom(
                mode = mode,
                title = title,
                topic = topic,
                learningLevel = learningLevel,
                audioMuted = audioMuted,
                videoEnabled = videoEnabled
            )
                .onSuccess { joined ->
                    _lobby.update { it.copy(busy = false) }
                    onCreated()
                    beginEngineSession(appContext, joined.room, joined.transport, audioMuted, videoEnabled)
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(busy = false, error = err.message ?: "Unable to create video room.")
                    }
                }
        }
    }

    fun joinRoom(
        context: Context,
        roomId: String,
        roomCode: String? = null,
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ) {
        if (_lobby.value.busy || roomId.isBlank()) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _lobby.update { it.copy(busy = true, error = null) }
            CallInviteNotifier.clearCallNotification(appContext, roomId)
            callRepo.joinRoom(
                roomId = roomId,
                roomCode = roomCode,
                audioMuted = audioMuted,
                videoEnabled = videoEnabled
            )
                .onSuccess { joined ->
                    _lobby.update { it.copy(busy = false) }
                    beginEngineSession(appContext, joined.room, joined.transport, audioMuted, videoEnabled)
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(busy = false, error = err.message ?: "Unable to join video room.")
                    }
                }
        }
    }

    fun joinPrivateByCode(
        context: Context,
        roomCode: String,
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true,
        onSuccessCloseDialog: () -> Unit = {}
    ) {
        if (_lobby.value.busy || roomCode.trim().length < 4) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _lobby.update { it.copy(busy = true, error = null) }
            callRepo.joinPrivateByCode(
                roomCode = roomCode,
                audioMuted = audioMuted,
                videoEnabled = videoEnabled
            )
                .onSuccess { joined ->
                    _lobby.update { it.copy(busy = false) }
                    onSuccessCloseDialog()
                    beginEngineSession(appContext, joined.room, joined.transport, audioMuted, videoEnabled)
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(busy = false, error = err.message ?: "Could not join private room.")
                    }
                }
        }
    }

    fun acceptIncomingInvite(
        context: Context,
        room: CallRoomSummary,
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _lobby.update {
                it.copy(
                    busy = true,
                    incomingInvites = it.incomingInvites.filterNot { inv -> inv.id == room.id }
                )
            }
            CallInviteNotifier.clearCallNotification(appContext, room.id)
            callRepo.respondToCall(
                roomId = room.id,
                action = "accept",
                audioMuted = audioMuted,
                videoEnabled = videoEnabled
            )
                .onSuccess { joined ->
                    _lobby.update { it.copy(busy = false) }
                    if (joined != null) {
                        beginEngineSession(appContext, joined.room, joined.transport, audioMuted, videoEnabled)
                    } else {
                        joinRoom(appContext, room.id, audioMuted = audioMuted, videoEnabled = videoEnabled)
                    }
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(busy = false, error = err.message ?: "Call is no longer available.")
                    }
                }
        }
    }

    fun declineIncomingInvite(context: Context, roomId: String) {
        val appContext = context.applicationContext
        CallInviteNotifier.clearCallNotification(appContext, roomId)
        _lobby.update {
            it.copy(incomingInvites = it.incomingInvites.filterNot { inv -> inv.id == roomId })
        }
        viewModelScope.launch {
            callRepo.respondToCall(roomId = roomId, action = "decline")
        }
    }

    private suspend fun beginEngineSession(
        appContext: Context,
        room: CallRoomSummary,
        transport: CallTransportSession,
        audioMuted: Boolean,
        videoEnabled: Boolean
    ) {
        tearDownEngineOnly()
        ActiveCallTracker.setActiveCall(room.id)
        lastSignalCursorIso = null

        val engine = CallEngineFactory.create(appContext, transport.provider)
        _activeEngine.value = engine
        _activeCall.value = ActiveCallUiState(
            room = room,
            transport = transport,
            engineState = engine.state.value,
            elapsedSeconds = 0,
            joining = true
        )

        engineStateJob?.cancel()
        engineStateJob = viewModelScope.launch {
            engine.state.collectLatest { engState ->
                _activeCall.update { it.copy(engineState = engState) }
                if (engState.phase == CallConnectionPhase.CONNECTED && timerJob == null) {
                    startElapsedTimer()
                }
            }
        }

        val bridge = object : CallSignalingBridge {
            override suspend fun sendSignal(toUserId: String, type: String, payload: String) {
                callRepo.sendSignal(
                    roomId = room.id,
                    toUserId = toUserId,
                    type = type,
                    payload = payload
                )
            }
        }

        runCatching {
            engine.startSession(
                room = room,
                transport = transport,
                signaling = bridge,
                audioMuted = audioMuted,
                videoEnabled = videoEnabled
            )
        }.onFailure { err ->
            _activeCall.update {
                it.copy(joining = false, error = err.message ?: "Failed to initialize camera or audio.")
            }
        }

        _activeCall.update { it.copy(joining = false) }
        if (room.mode != CallMode.DIRECT || room.participantCount > 1) {
            startElapsedTimer()
        }
        startRoomPollLoop(room.id)
    }

    private fun startElapsedTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _activeCall.value.room != null) {
                delay(1000L)
                _activeCall.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun startRoomPollLoop(roomId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive && _activeCall.value.room?.id == roomId) {
                val engState = _activeCall.value.engineState
                callRepo.pollRoom(
                    roomId = roomId,
                    afterIso = lastSignalCursorIso,
                    audioMuted = engState.localAudioMuted,
                    videoEnabled = engState.localVideoEnabled
                ).onSuccess { poll ->
                    val updatedRoom = poll.room
                    _activeCall.update { it.copy(room = updatedRoom) }

                    if (updatedRoom.status == "ended" ||
                        updatedRoom.status == "declined" ||
                        updatedRoom.status == "missed"
                    ) {
                        val notice = when (updatedRoom.status) {
                            "declined" -> "Call was declined."
                            "missed" -> "No answer."
                            else -> "Call ended."
                        }
                        finishLocalCall(notice = notice)
                        return@launch
                    }

                    val engine = _activeEngine.value
                    if (engine != null) {
                        engine.syncParticipants(updatedRoom.participants, updatedRoom.selfUserId)
                        for (sig in poll.signals) {
                            engine.handleSignal(sig)
                            if (sig.createdAt.isNotBlank()) {
                                lastSignalCursorIso = sig.createdAt
                            }
                        }
                        if (poll.signals.isEmpty() && poll.serverTime.isNotBlank() && lastSignalCursorIso == null) {
                            lastSignalCursorIso = poll.serverTime
                        }
                    }
                }
                delay(1200L)
            }
        }
    }

    fun toggleMic() {
        val engine = _activeEngine.value ?: return
        engine.setMicMuted(!engine.state.value.localAudioMuted)
    }

    fun toggleVideo() {
        val engine = _activeEngine.value ?: return
        engine.setVideoEnabled(!engine.state.value.localVideoEnabled)
    }

    fun switchCamera() {
        _activeEngine.value?.switchCamera()
    }

    fun toggleSpeakerphone() {
        val engine = _activeEngine.value ?: return
        engine.setSpeakerphone(!engine.state.value.isSpeakerphone)
    }

    fun leaveActiveCall(endRoomForAll: Boolean = false) {
        val roomId = _activeCall.value.room?.id
        finishLocalCall(notice = null)
        if (!roomId.isNullOrBlank()) {
            viewModelScope.launch {
                callRepo.leaveRoom(roomId = roomId, endRoom = endRoomForAll)
                loadLobby(silent = true)
            }
        } else {
            loadLobby(silent = true)
        }
    }

    fun clearLobbyError() {
        _lobby.update { it.copy(error = null) }
    }

    fun clearEndedNotice() {
        _activeCall.update { it.copy(endedNotice = null, error = null) }
    }

    private fun finishLocalCall(notice: String?) {
        tearDownEngineOnly()
        _activeCall.value = ActiveCallUiState(endedNotice = notice)
        loadLobby(silent = true)
    }

    private fun tearDownEngineOnly() {
        pollJob?.cancel()
        pollJob = null
        timerJob?.cancel()
        timerJob = null
        engineStateJob?.cancel()
        engineStateJob = null
        ActiveCallTracker.setActiveCall(null)
        _activeEngine.value?.release()
        _activeEngine.value = null
    }

    override fun onCleared() {
        tearDownEngineOnly()
        super.onCleared()
    }

    companion object {
        fun selectEngineProviderLabel(provider: CallProviderType): String {
            return when (provider) {
                CallProviderType.FREE_P2P -> "Free P2P ($0)"
                CallProviderType.LIVEKIT -> "LiveKit SFU"
                CallProviderType.AGORA -> "Agora RTC"
                CallProviderType.DAILY -> "Daily SFU"
            }
        }
    }
}
