package com.example.feature.call

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.PermissionPromptDialog
import com.example.core.ui.isPermissionPermanentlyDenied
import com.example.core.ui.markPermissionRequested
import com.example.core.ui.openAppPermissionSettings
import com.example.domain.model.CallMode
import com.example.domain.model.CallRoomSummary
import com.example.domain.model.ChatStudentSummary
import com.example.ui.theme.LocalIsDarkTheme

@Composable
fun VideoCallRoute(
    callViewModel: CallViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lobbyState by callViewModel.lobby.collectAsStateWithLifecycle()
    val activeState by callViewModel.activeCall.collectAsStateWithLifecycle()
    val activeEngine by callViewModel.activeEngine.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        callViewModel.loadLobby()
        callViewModel.searchStudents("")
    }

    if (activeState.room != null) {
        ActiveCallScreen(
            state = activeState,
            engine = activeEngine,
            onToggleMic = { callViewModel.toggleMic() },
            onToggleVideo = { callViewModel.toggleVideo() },
            onSwitchCamera = { callViewModel.switchCamera() },
            onToggleSpeaker = { callViewModel.toggleSpeakerphone() },
            onLeaveCall = { endForAll -> callViewModel.leaveActiveCall(endRoomForAll = endForAll) }
        )
    } else {
        CallLobbyScreen(
            state = lobbyState,
            endedNotice = activeState.endedNotice,
            onDismissNotice = { callViewModel.clearEndedNotice() },
            onBack = onBack,
            onRefresh = {
                callViewModel.loadLobby()
                callViewModel.searchStudents(lobbyState.studentSearch)
            },
            onSearchStudents = { callViewModel.searchStudents(it) },
            onStartDirectCall = { studentId, videoEnabled ->
                callViewModel.startDirectCall(
                    context = context,
                    targetUserId = studentId,
                    videoEnabled = videoEnabled
                )
            },
            onCreateGroupRoom = { mode, title, topic, level, videoEnabled, done ->
                callViewModel.createGroupRoom(
                    context = context,
                    mode = mode,
                    title = title,
                    topic = topic,
                    learningLevel = level,
                    videoEnabled = videoEnabled,
                    onCreated = done
                )
            },
            onJoinRoom = { roomId, videoEnabled ->
                callViewModel.joinRoom(
                    context = context,
                    roomId = roomId,
                    videoEnabled = videoEnabled
                )
            },
            onJoinByCode = { code, videoEnabled, done ->
                callViewModel.joinPrivateByCode(
                    context = context,
                    roomCode = code,
                    videoEnabled = videoEnabled,
                    onSuccessCloseDialog = done
                )
            },
            onClearError = { callViewModel.clearLobbyError() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallLobbyScreen(
    state: CallLobbyUiState,
    endedNotice: String?,
    onDismissNotice: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchStudents: (String) -> Unit,
    onStartDirectCall: (studentId: String, videoEnabled: Boolean) -> Unit,
    onCreateGroupRoom: (CallMode, String, String, String, Boolean, () -> Unit) -> Unit,
    onJoinRoom: (roomId: String, videoEnabled: Boolean) -> Unit,
    onJoinByCode: (code: String, videoEnabled: Boolean, () -> Unit) -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0=Public, 1=Private, 2=1-on-1
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var createDefaultPrivate by rememberSaveable { mutableStateOf(false) }
    var showJoinCodeDialog by rememberSaveable { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var pendingCallAction by remember { mutableStateOf<((videoEnabled: Boolean) -> Unit)?>(null) }

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val micGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camGranted = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val action = pendingCallAction
        pendingCallAction = null
        if (micGranted && action != null) {
            action(camGranted)
        } else if (!micGranted) {
            showPermissionDialog = true
        }
    }

    val runWithCallPermissions: ((videoEnabled: Boolean) -> Unit) -> Unit = { action ->
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasMic && hasCam) {
            action(true)
        } else {
            val micPermanent = !hasMic && isPermissionPermanentlyDenied(context, Manifest.permission.RECORD_AUDIO)
            if (micPermanent) {
                showPermissionDialog = true
            } else {
                pendingCallAction = action
                markPermissionRequested(context, Manifest.permission.RECORD_AUDIO)
                markPermissionRequested(context, Manifest.permission.CAMERA)
                callPermissionsLauncher.launch(
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                )
            }
        }
    }

    val isDark = LocalIsDarkTheme.current
    val bg = if (isDark) Color(0xFF0B120F) else Color(0xFFF6F9F7)
    val topBarBg = if (isDark) Color(0xFF101915) else Color.White
    val cardBg = if (isDark) Color(0xFF141E1A) else Color.White
    val borderColor = if (isDark) Color(0xFF24352E) else Color(0xFFE5ECE5)
    val accentGreen = if (isDark) Color(0xFF34D399) else Color(0xFF0E6B3B)
    val buttonGreen = if (isDark) Color(0xFF0E7A4D) else Color(0xFF0E6B3B)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarBg),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("call_lobby_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Video Practice Calls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.config.providerLabel} · Max ${state.config.maxGroupParticipants} in group",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentGreen
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("call_lobby_refresh_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh video rooms")
                    }
                }
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
                containerColor = topBarBg,
                modifier = Modifier.widthIn(max = 600.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Public (${state.publicRooms.size})") },
                    modifier = Modifier.testTag("call_tab_public")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Private (${state.privateRooms.size})") },
                    modifier = Modifier.testTag("call_tab_private")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("1-on-1") },
                    modifier = Modifier.testTag("call_tab_direct")
                )
            }

            if (!endedNotice.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF17252B) else Color(0xFFE0F2FE),
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = endedNotice,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissNotice) { Text("OK") }
                    }
                }
            }

            if (!state.error.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }

            if (state.loading && state.publicRooms.isEmpty() && state.privateRooms.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentGreen)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Action Strip
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    createDefaultPrivate = selectedTab == 1
                                    showCreateDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = buttonGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("call_create_group_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Group Call", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = { showJoinCodeDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("call_join_by_code_button")
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Join by Code", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            if (state.publicRooms.isEmpty()) {
                                item {
                                    EmptyCallCard(
                                        title = "No live public video rooms",
                                        subtitle = "Start a public English speaking room so any learner can join and practice live.",
                                        cardBg = cardBg,
                                        borderColor = borderColor,
                                        accentGreen = accentGreen
                                    )
                                }
                            } else {
                                items(state.publicRooms, key = { it.id }) { room ->
                                    CallRoomLobbyCard(
                                        room = room,
                                        cardBg = cardBg,
                                        borderColor = borderColor,
                                        accentGreen = accentGreen,
                                        busy = state.busy,
                                        onJoin = {
                                            runWithCallPermissions { vid -> onJoinRoom(room.id, vid) }
                                        },
                                        onCopyCode = { copyCode(context, it) }
                                    )
                                }
                            }
                        }

                        1 -> {
                            if (state.privateRooms.isEmpty()) {
                                item {
                                    EmptyCallCard(
                                        title = "No active private rooms",
                                        subtitle = "Create a private room to get an invite code, or tap 'Join by Code' to enter a friend's room.",
                                        cardBg = cardBg,
                                        borderColor = borderColor,
                                        accentGreen = accentGreen
                                    )
                                }
                            } else {
                                items(state.privateRooms, key = { it.id }) { room ->
                                    CallRoomLobbyCard(
                                        room = room,
                                        cardBg = cardBg,
                                        borderColor = borderColor,
                                        accentGreen = accentGreen,
                                        busy = state.busy,
                                        onJoin = {
                                            runWithCallPermissions { vid -> onJoinRoom(room.id, vid) }
                                        },
                                        onCopyCode = { copyCode(context, it) }
                                    )
                                }
                            }
                        }

                        else -> {
                            item {
                                OutlinedTextField(
                                    value = state.studentSearch,
                                    onValueChange = onSearchStudents,
                                    leadingIcon = { Icon(Icons.Filled.PersonSearch, contentDescription = null) },
                                    label = { Text("Search classmates for 1-on-1 call") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("call_student_search_input")
                                )
                            }
                            if (state.loadingStudents && state.students.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = accentGreen)
                                    }
                                }
                            } else if (state.students.isEmpty()) {
                                item {
                                    EmptyCallCard(
                                        title = "No active classmates found",
                                        subtitle = "Try clearing the search box or invite a study buddy to join the app.",
                                        cardBg = cardBg,
                                        borderColor = borderColor,
                                        accentGreen = accentGreen
                                    )
                                }
                            } else {
                                items(state.students, key = { it.id }) { student ->
                                    DirectCallStudentCard(
                                        student = student,
                                        cardBg = cardBg,
                                        borderColor = borderColor,
                                        accentGreen = accentGreen,
                                        busy = state.busy,
                                        onCall = {
                                            runWithCallPermissions { vid -> onStartDirectCall(student.id, vid) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateVideoRoomDialog(
            initialPrivate = createDefaultPrivate,
            busy = state.busy,
            onDismiss = { showCreateDialog = false },
            onCreate = { mode, title, topic, level ->
                runWithCallPermissions { videoEnabled ->
                    onCreateGroupRoom(mode, title, topic, level, videoEnabled) {
                        showCreateDialog = false
                    }
                }
            }
        )
    }

    if (showJoinCodeDialog) {
        JoinByCodeDialog(
            busy = state.busy,
            onDismiss = { showJoinCodeDialog = false },
            onJoin = { code ->
                runWithCallPermissions { videoEnabled ->
                    onJoinByCode(code, videoEnabled) {
                        showJoinCodeDialog = false
                    }
                }
            }
        )
    }

    if (showPermissionDialog) {
        PermissionPromptDialog(
            title = "Microphone & Camera Required",
            message = "Video calls need microphone (and camera) permission. Please allow them in App Settings to join or start a call.",
            icon = Icons.Filled.Videocam,
            onAllow = {
                showPermissionDialog = false
                openAppPermissionSettings(context)
            },
            onCancel = { showPermissionDialog = false }
        )
    }
}

@Composable
private fun CallRoomLobbyCard(
    room: CallRoomSummary,
    cardBg: Color,
    borderColor: Color,
    accentGreen: Color,
    busy: Boolean,
    onJoin: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    val isPrivate = room.mode == CallMode.GROUP_PRIVATE
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_room_card_${room.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentGreen.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                        contentDescription = null,
                        tint = accentGreen
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Host: ${room.hostName} · ${room.participantCount}/${room.maxParticipants} in call",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onJoin,
                    enabled = !busy && room.participantCount < room.maxParticipants,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = if (room.participantCount >= room.maxParticipants) "Full" else "Join",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (room.topic.isNotBlank()) {
                Text(
                    text = room.topic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isPrivate && room.roomCode.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentGreen.copy(alpha = 0.12f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onCopyCode(room.roomCode) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Invite Code: ${room.roomCode}",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy invite code",
                            tint = accentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectCallStudentCard(
    student: ChatStudentSummary,
    cardBg: Color,
    borderColor: Color,
    accentGreen: Color,
    busy: Boolean,
    onCall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("call_student_card_${student.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentGreen.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = student.name.take(2).uppercase(),
                    color = accentGreen,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = student.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onCall,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("call_student_button_${student.id}")
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Call", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyCallCard(
    title: String,
    subtitle: String,
    cardBg: Color,
    borderColor: Color,
    accentGreen: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = null,
                tint = accentGreen,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateVideoRoomDialog(
    initialPrivate: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (mode: CallMode, title: String, topic: String, level: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(initialPrivate) }
    var level by remember { mutableStateOf("all") }
    val levels = listOf("all" to "All Levels", "beginner" to "Beginner", "intermediate" to "Intermediate", "advanced" to "Advanced")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Group Video Call") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isPrivate,
                        onClick = { isPrivate = false },
                        leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Public Room") }
                    )
                    FilterChip(
                        selected = isPrivate,
                        onClick = { isPrivate = true },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Private (Code)") }
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Room Title*") },
                    placeholder = { Text("Daily Spoken English Club") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_call_room_title_input")
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Practice Topic") },
                    placeholder = { Text("Introducing yourself & daily routine") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Level", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    levels.take(2).forEach { (key, label) ->
                        FilterChip(
                            selected = level == key,
                            onClick = { level = key },
                            label = { Text(label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    levels.drop(2).forEach { (key, label) ->
                        FilterChip(
                            selected = level == key,
                            onClick = { level = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mode = if (isPrivate) CallMode.GROUP_PRIVATE else CallMode.GROUP_PUBLIC
                    onCreate(mode, title, topic, level)
                },
                enabled = !busy && title.trim().length >= 2,
                modifier = Modifier.testTag("confirm_create_call_room_button")
            ) {
                Text(if (busy) "Starting..." else "Start Call")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun JoinByCodeDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Private Video Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter the 6-character invite code shared by your study partner.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(12) },
                    label = { Text("Private Room Code") },
                    placeholder = { Text("ENG4K9") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_private_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = !busy && code.trim().length >= 4,
                modifier = Modifier.testTag("confirm_join_private_code_button")
            ) {
                Text(if (busy) "Joining..." else "Join Room")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun copyCode(context: Context, code: String) {
    if (code.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("Private Video Room Code", code))
    Toast.makeText(context, "Copied invite code: $code", Toast.LENGTH_SHORT).show()
}
