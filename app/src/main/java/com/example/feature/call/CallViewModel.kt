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
    val isRandomSession: Boolean = false,
    val isSearchingNextRandom: Boolean = false,
    val randomAttemptCount: Int = 0,
    val randomStatusText: String? = null,
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
    private var randomSearchJob: Job? = null
    private var lastSignalCursorIso: String? = null

    private var isRandomSessionActive: Boolean = false
    private var randomAppContext: Context? = null
    private var randomAudioMuted: Boolean = false
    private var randomVideoEnabled: Boolean = true
    private val randomExcludeUserIds = linkedSetOf<String>()
    private var randomAttemptCounter: Int = 0
    private var peerEverJoinedCurrentRoom: Boolean = false
    private var roomRingStartedAtMs: Long = 0L

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
        if (_activeCall.value.room != null || _activeCall.value.isSearchingNextRandom) return
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
        resetRandomSessionState()
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
                    beginEngineSession(
                        appContext = appContext,
                        room = joined.room,
                        transport = joined.transport,
                        audioMuted = audioMuted,
                        videoEnabled = videoEnabled,
                        isRandomSession = false
                    )
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(busy = false, error = err.message ?: "Unable to start 1-on-1 call.")
                    }
                }
        }
    }

    fun startRandomCall(
        context: Context,
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ) {
        if (_lobby.value.busy || _activeCall.value.isSearchingNextRandom) return
        resetRandomSessionState()
        isRandomSessionActive = true
        randomAppContext = context.applicationContext
        randomAudioMuted = audioMuted
        randomVideoEnabled = videoEnabled
        connectNextRandomPartner(
            previousRoom = null,
            statusMessage = "Finding a free student not in another call..."
        )
    }

    fun skipToNextRandomPartner() {
        val currentRoom = _activeCall.value.room
        if (!isRandomSessionActive && currentRoom?.effectiveRandom != true) return
        val appContext = randomAppContext ?: return
        isRandomSessionActive = true
        randomAppContext = appContext

        val peerId = currentRoom?.let { resolvePartnerUserId(it) }
        if (!peerId.isNullOrBlank()) {
            randomExcludeUserIds.add(peerId)
        }
        connectNextRandomPartner(
            previousRoom = currentRoom,
            statusMessage = "Connecting you to the next free student..."
        )
    }

    private fun connectNextRandomPartner(
        previousRoom: CallRoomSummary?,
        statusMessage: String
    ) {
        val appContext = randomAppContext ?: return
        randomSearchJob?.cancel()
        tearDownEngineOnly()
        randomAttemptCounter += 1
        val attemptNumber = randomAttemptCounter

        _lobby.update { it.copy(busy = true, error = null) }
        _activeCall.value = ActiveCallUiState(
            isRandomSession = true,
            isSearchingNextRandom = true,
            randomAttemptCount = attemptNumber,
            randomStatusText = statusMessage
        )

        randomSearchJob = viewModelScope.launch {
            if (previousRoom != null) {
                if (previousRoom.status == "ringing" && previousRoom.isHost) {
                    runCatching { callRepo.respondToCall(previousRoom.id, "cancel") }
                } else {
                    runCatching { callRepo.leaveRoom(previousRoom.id, endRoom = false) }
                }
            }
            delay(280L)
            if (!isRandomSessionActive) return@launch

            callRepo.startRandomCall(
                excludeUserIds = randomExcludeUserIds.toList(),
                previousRoomId = previousRoom?.id,
                audioMuted = randomAudioMuted,
                videoEnabled = randomVideoEnabled
            )
                .onSuccess { joined ->
                    if (!isRandomSessionActive) {
                        runCatching { callRepo.respondToCall(joined.room.id, "cancel") }
                        _lobby.update { it.copy(busy = false) }
                        return@launch
                    }
                    val randomRoom = joined.room.copy(isRandom = true)
                    val matchedPeerId = resolvePartnerUserId(randomRoom)
                    if (!matchedPeerId.isNullOrBlank()) {
                        randomExcludeUserIds.add(matchedPeerId)
                    }
                    _lobby.update { it.copy(busy = false) }
                    beginEngineSession(
                        appContext = appContext,
                        room = randomRoom,
                        transport = joined.transport,
                        audioMuted = randomAudioMuted,
                        videoEnabled = randomVideoEnabled,
                        isRandomSession = true
                    )
                }
                .onFailure { err ->
                    resetRandomSessionState()
                    _lobby.update { it.copy(busy = false) }
                    _activeCall.value = ActiveCallUiState(
                        endedNotice = err.message ?: "No free students available right now. Please try again."
                    )
                    loadLobby(silent = true)
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
        resetRandomSessionState()
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
                    beginEngineSession(
                        appContext = appContext,
                        room = joined.room,
                        transport = joined.transport,
                        audioMuted = audioMuted,
                        videoEnabled = videoEnabled,
                        isRandomSession = false
                    )
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
        resetRandomSessionState()
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
                    beginEngineSession(
                        appContext = appContext,
                        room = joined.room,
                        transport = joined.transport,
                        audioMuted = audioMuted,
                        videoEnabled = videoEnabled,
                        isRandomSession = joined.room.effectiveRandom
                    )
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
        resetRandomSessionState()
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
                    beginEngineSession(
                        appContext = appContext,
                        room = joined.room,
                        transport = joined.transport,
                        audioMuted = audioMuted,
                        videoEnabled = videoEnabled,
                        isRandomSession = false
                    )
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
        resetRandomSessionState()
        val appContext = context.applicationContext
        if (room.effectiveRandom) {
            randomAppContext = appContext
            randomAudioMuted = audioMuted
            randomVideoEnabled = videoEnabled
        }
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
                        beginEngineSession(
                            appContext = appContext,
                            room = joined.room,
                            transport = joined.transport,
                            audioMuted = audioMuted,
                            videoEnabled = videoEnabled,
                            isRandomSession = joined.room.effectiveRandom
                        )
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
        videoEnabled: Boolean,
        isRandomSession: Boolean
    ) {
        tearDownEngineOnly()
        ActiveCallTracker.setActiveCall(room.id)
        lastSignalCursorIso = null
        peerEverJoinedCurrentRoom = room.participantCount > 1
        roomRingStartedAtMs = System.currentTimeMillis()
        randomAppContext = appContext

        val engine = CallEngineFactory.create(appContext, transport.provider)
        _activeEngine.value = engine
        _activeCall.value = ActiveCallUiState(
            room = room,
            transport = transport,
            engineState = engine.state.value,
            elapsedSeconds = 0,
            joining = true,
            isRandomSession = isRandomSession,
            isSearchingNextRandom = false,
            randomAttemptCount = randomAttemptCounter,
            randomStatusText = if (isRandomSession && room.participantCount <= 1) {
                "Ringing ${room.targetName.ifBlank { "free student" }}..."
            } else null
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
        if (!room.mode.isOneOnOne || room.participantCount > 1) {
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
                    if (updatedRoom.participantCount > 1) {
                        peerEverJoinedCurrentRoom = true
                    }
                    _activeCall.update {
                        it.copy(
                            room = updatedRoom.copy(isRandom = it.room?.isRandom == true || updatedRoom.isRandom)
                        )
                    }

                    // Auto-skip to next free student if random partner doesn't answer within timeout
                    if (isRandomSessionActive &&
                        updatedRoom.isHost &&
                        updatedRoom.status == "ringing" &&
                        !peerEverJoinedCurrentRoom &&
                        System.currentTimeMillis() - roomRingStartedAtMs >= RANDOM_RING_TIMEOUT_MS
                    ) {
                        val partnerId = resolvePartnerUserId(updatedRoom)
                        if (!partnerId.isNullOrBlank()) {
                            randomExcludeUserIds.add(partnerId)
                        }
                        val partnerName = updatedRoom.targetName.ifBlank { "Partner" }
                        connectNextRandomPartner(
                            previousRoom = updatedRoom,
                            statusMessage = "$partnerName didn't answer. Connecting to next free student..."
                        )
                        return@launch
                    }

                    if (updatedRoom.status == "ended" ||
                        updatedRoom.status == "declined" ||
                        updatedRoom.status == "missed"
                    ) {
                        val shouldAutoReconnect = isRandomSessionActive && (
                            updatedRoom.status == "declined" ||
                                updatedRoom.status == "missed" ||
                                updatedRoom.endedReason in setOf("declined", "cancelled", "no_answer") ||
                                !peerEverJoinedCurrentRoom
                            )
                        if (shouldAutoReconnect) {
                            val partnerId = resolvePartnerUserId(updatedRoom)
                            if (!partnerId.isNullOrBlank()) {
                                randomExcludeUserIds.add(partnerId)
                            }
                            val partnerName = updatedRoom.targetName
                                .ifBlank { updatedRoom.hostName }
                                .ifBlank { "Partner" }
                            val reasonText = when {
                                updatedRoom.status == "declined" || updatedRoom.endedReason == "declined" ->
                                    "$partnerName declined. Connecting to next free student..."
                                updatedRoom.status == "missed" || updatedRoom.endedReason == "no_answer" ->
                                    "$partnerName didn't answer. Connecting to next free student..."
                                else ->
                                    "$partnerName cancelled. Connecting to next free student..."
                            }
                            connectNextRandomPartner(
                                previousRoom = null,
                                statusMessage = reasonText
                            )
                            return@launch
                        }

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
        val nextMuted = !engine.state.value.localAudioMuted
        randomAudioMuted = nextMuted
        engine.setMicMuted(nextMuted)
    }

    fun toggleVideo() {
        val engine = _activeEngine.value ?: return
        val nextVideo = !engine.state.value.localVideoEnabled
        randomVideoEnabled = nextVideo
        engine.setVideoEnabled(nextVideo)
    }

    fun switchCamera() {
        _activeEngine.value?.switchCamera()
    }

    fun toggleSpeakerphone() {
        val engine = _activeEngine.value ?: return
        engine.setSpeakerphone(!engine.state.value.isSpeakerphone)
    }

    fun leaveActiveCall(endRoomForAll: Boolean = false) {
        val currentRoom = _activeCall.value.room
        resetRandomSessionState()
        _lobby.update { it.copy(busy = false) }
        finishLocalCall(notice = null)
        if (currentRoom != null) {
            viewModelScope.launch {
                if (currentRoom.status == "ringing" && currentRoom.isHost && currentRoom.mode.isOneOnOne) {
                    callRepo.respondToCall(roomId = currentRoom.id, action = "cancel")
                } else {
                    callRepo.leaveRoom(roomId = currentRoom.id, endRoom = endRoomForAll)
                }
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

    private fun resolvePartnerUserId(room: CallRoomSummary): String? {
        val selfId = room.selfUserId
        val remoteParticipant = room.participants.firstOrNull { it.userId.isNotBlank() && it.userId != selfId }?.userId
        if (!remoteParticipant.isNullOrBlank()) return remoteParticipant
        return if (room.isHost) {
            room.targetUserId?.takeIf { it.isNotBlank() && it != selfId }
        } else {
            room.hostUserId.takeIf { it.isNotBlank() && it != selfId }
        }
    }

    private fun resetRandomSessionState() {
        isRandomSessionActive = false
        randomSearchJob?.cancel()
        randomSearchJob = null
        randomExcludeUserIds.clear()
        randomAttemptCounter = 0
        peerEverJoinedCurrentRoom = false
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
        resetRandomSessionState()
        tearDownEngineOnly()
        super.onCleared()
    }

    companion object {
        private const val RANDOM_RING_TIMEOUT_MS = 22_000L

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
