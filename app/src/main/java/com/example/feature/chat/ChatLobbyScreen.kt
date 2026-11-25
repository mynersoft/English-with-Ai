package com.example.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.ChatRoomSummary
import com.example.domain.model.ChatStudentSummary

@Composable
fun ChatRoute(
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lobbyState by vm.lobby.collectAsStateWithLifecycle()
    val threadState by vm.thread.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.loadLobby()
    }

    val activeRoom = threadState.room
    if (activeRoom != null) {
        BackHandler { vm.closeRoom() }
        ChatThreadScreen(
            state = threadState,
            starterChips = lobbyState.starterChips,
            onBack = { vm.closeRoom() },
            onRetry = { vm.retryThread() },
            onToggleMembership = { vm.toggleMembership() },
            onToggleAutoCorrect = { vm.toggleAutoCorrect() },
            onReplySelect = { vm.setReplyTarget(it) },
            onSendMessage = { text, onSent -> vm.sendMessage(text, onSent) },
            onRequestAiCorrection = { msgId -> vm.requestAiCorrection(msgId) },
            onProcessHandwritingPhoto = { uri, tempFile ->
                vm.extractHandwritingFromUri(context, uri, tempFile)
            },
            onDismissExtractedText = { vm.dismissExtractedHandwriting() },
            onOcrError = { msg -> vm.reportOcrError(msg) },
            onClearOcrError = { vm.clearOcrError() },
            onClearError = { vm.clearThreadError() }
        )
    } else {
        ChatLobbyScreen(
            state = lobbyState,
            onBack = onBack,
            onRefresh = { vm.loadLobby() },
            onOpenRoom = { vm.openRoom(it) },
            onCreateGroupRoom = { name, desc, emoji, done ->
                vm.createGroupRoom(name, desc, emoji) { room ->
                    done()
                    vm.openRoom(room)
                }
            },
            onSearchStudents = { vm.searchStudents(it) },
            onStartDirectChat = { peerId, done ->
                vm.startDirectChat(peerId) { room ->
                    done()
                    vm.openRoom(room)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatLobbyScreen(
    state: ChatLobbyUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenRoom: (ChatRoomSummary) -> Unit,
    onCreateGroupRoom: (name: String, description: String, emoji: String, onDone: () -> Unit) -> Unit,
    onSearchStudents: (String) -> Unit,
    onStartDirectChat: (peerUserId: String, onDone: () -> Unit) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0=Study Rooms, 1=Direct Chats
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var showStudentDialog by rememberSaveable { mutableStateOf(false) }

    val primaryGreen = Color(0xFF1F5D3B)
    val bgLight = Color(0xFFF7FBF7)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = bgLight,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Student English Chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (state.aiConfigured) {
                                true -> "Practice with learners · AI grammar fix active"
                                false -> "Practice with learners · Set AI_API_KEY for AI fixes"
                                null -> "Practice with learners"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_lobby_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onSearchStudents("")
                            showStudentDialog = true
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_find_student_button")
                    ) {
                        Icon(Icons.Filled.PersonSearch, contentDescription = "Message a student")
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_lobby_refresh_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh rooms")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                modifier = Modifier.widthIn(max = 600.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Study Rooms (${state.groupRooms.size})") },
                    modifier = Modifier.testTag("chat_tab_rooms")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Direct (${state.directRooms.size})") },
                    modifier = Modifier.testTag("chat_tab_direct")
                )
            }

            if (!state.error.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                }
            }

            if (state.loading && state.groupRooms.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryGreen)
                }
            } else {
                val visibleRooms = if (selectedTab == 0) state.groupRooms else state.directRooms
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showCreateDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_create_room_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Study Room", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = {
                                    onSearchStudents("")
                                    showStudentDialog = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("chat_message_classmate_button")
                            ) {
                                Icon(Icons.Filled.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Find Student", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    if (visibleRooms.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Forum,
                                        contentDescription = null,
                                        tint = primaryGreen,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = if (selectedTab == 0) "No study rooms yet" else "No direct chats yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (selectedTab == 0) {
                                            "Create a study room to chat in English with fellow learners."
                                        } else {
                                            "Tap 'Find Student' to start a 1-on-1 practice chat."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(visibleRooms, key = { it.id }) { room ->
                            ChatRoomCard(
                                room = room,
                                primaryGreen = primaryGreen,
                                onClick = { onOpenRoom(room) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            creating = state.creatingRoom,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, emoji ->
                onCreateGroupRoom(name, desc, emoji) { showCreateDialog = false }
            }
        )
    }

    if (showStudentDialog) {
        FindStudentDialog(
            students = state.students,
            search = state.studentSearch,
            loading = state.loadingStudents,
            creating = state.creatingRoom,
            onSearchChange = onSearchStudents,
            onDismiss = { showStudentDialog = false },
            onSelectStudent = { student ->
                onStartDirectChat(student.id) { showStudentDialog = false }
            }
        )
    }
}

@Composable
private fun ChatRoomCard(
    room: ChatRoomSummary,
    primaryGreen: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE5ECE5)),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("chat_room_card_${room.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFEDF6EF)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = room.avatarEmoji, fontSize = 22.sp)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (room.isMember) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFE8F5EC)
                        ) {
                            Text(
                                text = "Member",
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryGreen,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = room.lastMessageText.ifBlank { room.description.ifBlank { "Tap to start chatting in English" } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${room.memberCount}/${room.maxMembers}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A))
                    )
                    Text(
                        text = "${room.onlineCount} online",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF15803D)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateRoomDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🦁") }
    val emojis = listOf("🦁", "📚", "🗣️", "🌱", "🎯", "☕")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Study Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Room Name*") },
                    placeholder = { Text("Evening Speaking Circle") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Topic / Description") },
                    placeholder = { Text("Practice daily English & help each other") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Room Icon", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(emojis) { icon ->
                        FilterChip(
                            selected = emoji == icon,
                            onClick = { emoji = icon },
                            label = { Text(icon, fontSize = 18.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, desc, emoji) },
                enabled = !creating && name.trim().length >= 2,
                modifier = Modifier.testTag("chat_confirm_create_room_button")
            ) {
                Text(if (creating) "Creating..." else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FindStudentDialog(
    students: List<ChatStudentSummary>,
    search: String,
    loading: Boolean,
    creating: Boolean,
    onSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelectStudent: (ChatStudentSummary) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message a Student") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    label = { Text("Search by student name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (students.isEmpty()) {
                    Text(
                        text = "No other active students matched your search yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(students, key = { it.id }) { student ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF7FBF7),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !creating) { onSelectStudent(student) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1F5D3B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = student.name.take(2).uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = student.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = student.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Chat",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF1F5D3B),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
