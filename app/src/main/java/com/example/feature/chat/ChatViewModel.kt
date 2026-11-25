package com.example.feature.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ChatRepository
import com.example.domain.model.ChatMemberSummary
import com.example.domain.model.ChatMessageItem
import com.example.domain.model.ChatRoomSummary
import com.example.domain.model.ChatStudentSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class ChatLobbyUiState(
    val loading: Boolean = false,
    val aiConfigured: Boolean? = null,
    val groupRooms: List<ChatRoomSummary> = emptyList(),
    val directRooms: List<ChatRoomSummary> = emptyList(),
    val starterChips: List<String> = listOf(
        "I feel like...",
        "I want to...",
        "I'd like to...",
        "I'm planning to..."
    ),
    val students: List<ChatStudentSummary> = emptyList(),
    val studentSearch: String = "",
    val loadingStudents: Boolean = false,
    val creatingRoom: Boolean = false,
    val error: String? = null
)

data class ChatThreadUiState(
    val room: ChatRoomSummary? = null,
    val members: List<ChatMemberSummary> = emptyList(),
    val messages: List<ChatMessageItem> = emptyList(),
    val loading: Boolean = false,
    val sending: Boolean = false,
    val aiConfigured: Boolean = false,
    val autoCorrectEnabled: Boolean = true,
    val replyingTo: ChatMessageItem? = null,
    val correctingIds: Set<String> = emptySet(),
    val extractingHandwriting: Boolean = false,
    val extractedHandwritingText: String? = null,
    val ocrError: String? = null,
    val error: String? = null
)

class ChatViewModel : ViewModel() {
    private val repo = ChatRepository()

    private val _lobby = MutableStateFlow(ChatLobbyUiState())
    val lobby: StateFlow<ChatLobbyUiState> = _lobby.asStateFlow()

    private val _thread = MutableStateFlow(ChatThreadUiState())
    val thread: StateFlow<ChatThreadUiState> = _thread.asStateFlow()

    private var pollingJob: Job? = null

    fun loadLobby() {
        viewModelScope.launch {
            _lobby.update { it.copy(loading = true, error = null) }
            repo.fetchRooms()
                .onSuccess { snap ->
                    _lobby.update {
                        it.copy(
                            loading = false,
                            aiConfigured = snap.aiConfigured,
                            groupRooms = snap.groupRooms,
                            directRooms = snap.directRooms,
                            starterChips = snap.starterChips.ifEmpty { it.starterChips },
                            error = null
                        )
                    }
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(loading = false, error = err.message ?: "Failed to load chat rooms.")
                    }
                }
        }
    }

    fun searchStudents(query: String) {
        _lobby.update { it.copy(studentSearch = query, loadingStudents = true) }
        viewModelScope.launch {
            repo.searchStudents(query)
                .onSuccess { list ->
                    _lobby.update { it.copy(loadingStudents = false, students = list) }
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(loadingStudents = false, error = err.message ?: "Could not load students.")
                    }
                }
        }
    }

    fun createGroupRoom(name: String, description: String, avatarEmoji: String, onCreated: (ChatRoomSummary) -> Unit) {
        if (name.trim().length < 2) return
        viewModelScope.launch {
            _lobby.update { it.copy(creatingRoom = true, error = null) }
            repo.createGroupRoom(name, description, avatarEmoji)
                .onSuccess { room ->
                    _lobby.update { it.copy(creatingRoom = false) }
                    loadLobby()
                    onCreated(room)
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(creatingRoom = false, error = err.message ?: "Could not create room.")
                    }
                }
        }
    }

    fun startDirectChat(peerUserId: String, onReady: (ChatRoomSummary) -> Unit) {
        viewModelScope.launch {
            _lobby.update { it.copy(creatingRoom = true, error = null) }
            repo.startDirectChat(peerUserId)
                .onSuccess { room ->
                    _lobby.update { it.copy(creatingRoom = false) }
                    loadLobby()
                    onReady(room)
                }
                .onFailure { err ->
                    _lobby.update {
                        it.copy(creatingRoom = false, error = err.message ?: "Could not open chat.")
                    }
                }
        }
    }

    fun openRoom(room: ChatRoomSummary) {
        pollingJob?.cancel()
        _thread.value = ChatThreadUiState(
            room = room,
            loading = true,
            aiConfigured = _lobby.value.aiConfigured == true
        )
        viewModelScope.launch {
            repo.fetchMessages(room.id)
                .onSuccess { snap ->
                    _thread.update {
                        it.copy(
                            room = snap.room,
                            members = snap.members,
                            messages = snap.messages,
                            loading = false,
                            aiConfigured = snap.aiConfigured,
                            error = null
                        )
                    }
                    startPolling(room.id)
                }
                .onFailure { err ->
                    _thread.update {
                        it.copy(loading = false, error = err.message ?: "Could not load chat messages.")
                    }
                }
        }
    }

    fun retryThread() {
        _thread.value.room?.let { openRoom(it) }
    }

    fun closeRoom() {
        pollingJob?.cancel()
        pollingJob = null
        _thread.value = ChatThreadUiState()
        loadLobby()
    }

    private fun startPolling(roomId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000L)
                val lastIso = _thread.value.messages.lastOrNull()?.createdAt
                repo.fetchMessages(roomId, afterIso = lastIso)
                    .onSuccess { snap ->
                        _thread.update { cur ->
                            val existingById = cur.messages.associateBy { it.id }
                            val merged = (cur.messages + snap.messages)
                                .associateBy { it.id }
                                .values
                                .sortedBy { it.createdAt }
                            // Also keep updated room metadata (onlineCount, memberCount)
                            val updatedMessages = if (existingById.isEmpty()) snap.messages else merged
                            cur.copy(
                                room = snap.room,
                                members = snap.members,
                                messages = updatedMessages,
                                aiConfigured = snap.aiConfigured,
                                error = null
                            )
                        }
                    }
            }
        }
    }

    fun toggleMembership() {
        val room = _thread.value.room ?: return
        viewModelScope.launch {
            repo.toggleMembership(room.id, join = !room.isMember)
                .onSuccess { joined ->
                    _thread.update { cur ->
                        val curRoom = cur.room ?: return@update cur
                        val delta = if (joined == curRoom.isMember) 0 else if (joined) 1 else -1
                        cur.copy(
                            room = curRoom.copy(
                                isMember = joined,
                                memberCount = (curRoom.memberCount + delta).coerceAtLeast(0)
                            )
                        )
                    }
                }
                .onFailure { err ->
                    _thread.update { it.copy(error = err.message ?: "Failed to update membership.") }
                }
        }
    }

    fun toggleAutoCorrect() {
        _thread.update { it.copy(autoCorrectEnabled = !it.autoCorrectEnabled) }
    }

    fun setReplyTarget(message: ChatMessageItem?) {
        _thread.update { it.copy(replyingTo = message) }
    }

    fun clearThreadError() {
        _thread.update { it.copy(error = null) }
    }

    fun sendMessage(text: String, onSent: () -> Unit = {}) {
        val trimmed = text.trim()
        val room = _thread.value.room ?: return
        if (trimmed.isEmpty() || _thread.value.sending) return

        val replyId = _thread.value.replyingTo?.id
        val autoCorrect = _thread.value.autoCorrectEnabled

        viewModelScope.launch {
            _thread.update { it.copy(sending = true, error = null) }
            repo.sendMessage(
                roomId = room.id,
                text = trimmed,
                replyToMessageId = replyId,
                autoCorrect = autoCorrect
            )
                .onSuccess { savedMsg ->
                    _thread.update { cur ->
                        val updatedList = (cur.messages + savedMsg)
                            .associateBy { it.id }
                            .values
                            .sortedBy { it.createdAt }
                        cur.copy(
                            sending = false,
                            replyingTo = null,
                            messages = updatedList,
                            room = cur.room?.copy(isMember = true)
                        )
                    }
                    onSent()
                }
                .onFailure { err ->
                    _thread.update {
                        it.copy(sending = false, error = err.message ?: "Could not send message.")
                    }
                }
        }
    }

    fun requestAiCorrection(messageId: String) {
        if (_thread.value.correctingIds.contains(messageId)) return
        viewModelScope.launch {
            _thread.update { it.copy(correctingIds = it.correctingIds + messageId, error = null) }
            repo.correctMessage(messageId)
                .onSuccess { updatedMsg ->
                    _thread.update { cur ->
                        cur.copy(
                            correctingIds = cur.correctingIds - messageId,
                            messages = cur.messages.map { if (it.id == messageId) updatedMsg else it }
                        )
                    }
                }
                .onFailure { err ->
                    _thread.update {
                        it.copy(
                            correctingIds = it.correctingIds - messageId,
                            error = err.message ?: "AI correction failed."
                        )
                    }
                }
        }
    }

    fun extractHandwritingFromUri(
        context: Context,
        uri: Uri,
        tempFileToDelete: File? = null
    ) {
        if (_thread.value.extractingHandwriting) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _thread.update {
                it.copy(
                    extractingHandwriting = true,
                    extractedHandwritingText = null,
                    ocrError = null
                )
            }
            try {
                val encodedRes = HandwritingImageHelper.encodeUriToCompressedJpegBase64(appContext, uri)
                if (encodedRes.isFailure) {
                    _thread.update {
                        it.copy(
                            extractingHandwriting = false,
                            ocrError = encodedRes.exceptionOrNull()?.message
                                ?: "Could not read photo."
                        )
                    }
                    return@launch
                }
                repo.extractHandwritingText(encodedRes.getOrThrow())
                    .onSuccess { extracted ->
                        _thread.update {
                            it.copy(
                                extractingHandwriting = false,
                                extractedHandwritingText = extracted,
                                ocrError = null
                            )
                        }
                    }
                    .onFailure { err ->
                        _thread.update {
                            it.copy(
                                extractingHandwriting = false,
                                ocrError = err.message ?: "Could not extract handwriting text."
                            )
                        }
                    }
            } finally {
                tempFileToDelete?.let { runCatching { it.delete() } }
            }
        }
    }

    fun dismissExtractedHandwriting() {
        _thread.update { it.copy(extractedHandwritingText = null) }
    }

    fun reportOcrError(message: String) {
        _thread.update { it.copy(extractingHandwriting = false, ocrError = message) }
    }

    fun clearOcrError() {
        _thread.update { it.copy(ocrError = null) }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        super.onCleared()
    }
}
