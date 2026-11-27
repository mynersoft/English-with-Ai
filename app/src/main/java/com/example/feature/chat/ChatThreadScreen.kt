package com.example.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.ui.PermissionPromptDialog
import com.example.core.ui.isPermissionPermanentlyDenied
import com.example.core.ui.openAppPermissionSettings
import com.example.core.ui.requestSystemPermission
import com.example.domain.model.ChatMessageItem
import com.example.domain.model.ChatRoomSummary
import java.io.File

private val ChatCanvasBg = Color(0xFFF6F7F6)
private val ChatPrimaryGreen = Color(0xFF0E7A3E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    state: ChatThreadUiState,
    starterChips: List<String>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleMembership: () -> Unit,
    onToggleAutoCorrect: () -> Unit,
    onReplySelect: (ChatMessageItem?) -> Unit,
    onSendMessage: (String, onSent: () -> Unit) -> Unit,
    onRequestAiCorrection: (String) -> Unit,
    onProcessHandwritingPhoto: (Uri, File?) -> Unit = { _, _ -> },
    onDismissExtractedText: () -> Unit = {},
    onOcrError: (String) -> Unit = {},
    onClearOcrError: () -> Unit = {},
    onClearHighlight: () -> Unit = {},
    onClearError: () -> Unit
) {
    val room = state.room ?: return
    val context = LocalContext.current
    var draft by rememberSaveable(room.id) { mutableStateOf("") }
    var showRoomInfo by remember { mutableStateOf(false) }
    var showCaptureOptions by remember { mutableStateOf(false) }
    var showCameraPermissionDialog by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        val uri = pendingCameraUri
        pendingCameraFile = null
        pendingCameraUri = null
        if (success && uri != null) {
            onProcessHandwritingPhoto(uri, file)
        } else {
            file?.let { runCatching { it.delete() } }
        }
    }

    val launchCamera = {
        runCatching {
            HandwritingImageHelper.createTempCameraImageUri(context)
        }.onSuccess { (file, uri) ->
            pendingCameraFile = file
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }.onFailure { err ->
            onOcrError(err.message ?: "Unable to launch camera.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else if (isPermissionPermanentlyDenied(context, Manifest.permission.CAMERA)) {
            showCameraPermissionDialog = true
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onProcessHandwritingPhoto(uri, null)
        }
    }

    LaunchedEffect(state.messages.size, state.highlightedMessageId) {
        val highlightId = state.highlightedMessageId
        if (!highlightId.isNullOrBlank()) {
            val targetIndex = state.messages.indexOfFirst { it.id == highlightId }
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
                return@LaunchedEffect
            }
        }
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.highlightedMessageId) {
        if (!state.highlightedMessageId.isNullOrBlank()) {
            kotlinx.coroutines.delay(2600L)
            onClearHighlight()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = ChatCanvasBg,
        topBar = {
            ChatRoomTopBar(
                room = room,
                onBack = onBack,
                onOpenRoomInfo = { showRoomInfo = true }
            )
        },
        bottomBar = {
            ChatComposerBar(
                draft = draft,
                onDraftChange = { draft = it },
                sending = state.sending,
                extractingHandwriting = state.extractingHandwriting,
                autoCorrectEnabled = state.autoCorrectEnabled,
                aiConfigured = state.aiConfigured,
                replyingTo = state.replyingTo,
                starterChips = starterChips,
                onOpenHandwritingCapture = { showCaptureOptions = true },
                onToggleAutoCorrect = onToggleAutoCorrect,
                onCancelReply = { onReplySelect(null) },
                onChipClick = { chip ->
                    val prefix = chip.removeSuffix("...").trim()
                    draft = if (draft.isBlank()) "$prefix " else "${draft.trimEnd()} $prefix "
                },
                onSend = {
                    val toSend = draft
                    onSendMessage(toSend) { draft = "" }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
            ) {
                if (!state.error.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
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
                            TextButton(onClick = {
                                onClearError()
                                onRetry()
                            }) { Text("Retry") }
                        }
                    }
                }

                if (!state.ocrError.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.ocrError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onClearOcrError) { Text("Dismiss") }
                        }
                    }
                }

                if (state.extractingHandwriting) {
                    Surface(
                        color = Color(0xFFFFF8EC),
                        border = BorderStroke(1.dp, Color(0xFFE5A446)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFD97706)
                            )
                            Text(
                                text = "Reading handwritten text from your photo...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                when {
                    state.loading && state.messages.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ChatPrimaryGreen)
                        }
                    }

                    state.messages.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE5ECE5))
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(room.avatarEmoji, fontSize = 30.sp)
                                    Text(
                                        text = "Start the conversation!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Type in English, tap a starter chip, or snap a photo of your handwriting to send text only.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(
                                count = state.messages.size,
                                key = { idx -> state.messages[idx].id }
                            ) { idx ->
                                val msg = state.messages[idx]
                                val dateLabel = formatDateDivider(msg.createdAt)
                                val prevDateLabel =
                                    if (idx > 0) formatDateDivider(state.messages[idx - 1].createdAt) else null

                                if (dateLabel != prevDateLabel) {
                                    ChatDateDivider(label = dateLabel)
                                }

                                ChatMessageBubbleRow(
                                    msg = msg,
                                    isCorrecting = state.correctingIds.contains(msg.id),
                                    isHighlighted = state.highlightedMessageId == msg.id,
                                    onReply = { onReplySelect(msg) },
                                    onRequestAiCorrection = { onRequestAiCorrection(msg.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCaptureOptions) {
        AlertDialog(
            onDismissRequest = { showCaptureOptions = false },
            title = { Text("Scan Handwriting to Text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Take a photo of your handwritten English notebook. Only the extracted text will be sent—never the photo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showCaptureOptions = false
                            requestSystemPermission(
                                context = context,
                                permission = Manifest.permission.CAMERA,
                                launcher = cameraPermissionLauncher,
                                onAlreadyGranted = { launchCamera() },
                                onPermanentlyDenied = { showCameraPermissionDialog = true }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ChatPrimaryGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("chat_take_handwriting_photo_button")
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Take Photo with Camera")
                    }
                    OutlinedButton(
                        onClick = {
                            showCaptureOptions = false
                            galleryPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("chat_pick_handwriting_photo_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose Photo from Gallery")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCaptureOptions = false }) { Text("Cancel") }
            }
        )
    }

    if (showCameraPermissionDialog) {
        PermissionPromptDialog(
            title = "Camera Permission Required",
            message = "Camera permission was denied. Enable it in App Settings to take a photo of your handwriting.",
            icon = Icons.Filled.PhotoCamera,
            onAllow = {
                showCameraPermissionDialog = false
                openAppPermissionSettings(context)
            },
            onCancel = { showCameraPermissionDialog = false }
        )
    }

    state.extractedHandwritingText?.let { extracted ->
        var editableText by remember(extracted) { mutableStateOf(extracted) }
        AlertDialog(
            onDismissRequest = onDismissExtractedText,
            title = { Text("Extracted Handwriting Text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Only this text will be sent in chat (the photo is discarded):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editableText,
                        onValueChange = { editableText = it },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_extracted_text_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toSend = editableText.trim()
                        if (toSend.isNotEmpty()) {
                            onDismissExtractedText()
                            onSendMessage(toSend) {}
                        }
                    },
                    enabled = editableText.isNotBlank() && !state.sending,
                    colors = ButtonDefaults.buttonColors(containerColor = ChatPrimaryGreen),
                    modifier = Modifier.testTag("chat_send_extracted_text_button")
                ) {
                    Text("Send Text Only")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onDismissExtractedText) { Text("Discard") }
                    TextButton(
                        onClick = {
                            val clean = editableText.trim()
                            if (clean.isNotEmpty()) {
                                draft = if (draft.isBlank()) clean else "${draft.trimEnd()} $clean"
                            }
                            onDismissExtractedText()
                        }
                    ) {
                        Text("Insert in Box")
                    }
                }
            }
        )
    }

    if (showRoomInfo) {
        AlertDialog(
            onDismissRequest = { showRoomInfo = false },
            title = { Text("${room.avatarEmoji} ${room.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (room.description.isNotBlank()) {
                        Text(room.description, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-correct my English (AI)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = state.autoCorrectEnabled,
                            onCheckedChange = { onToggleAutoCorrect() }
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = "Members (${state.members.size}/${room.maxMembers} · ${room.onlineCount} online)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    LazyColumn(
                        modifier = Modifier.height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.members, key = { it.id }) { m ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("• ${m.name}", style = MaterialTheme.typography.bodyMedium)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE8F5EC)
                                ) {
                                    Text(
                                        text = m.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChatPrimaryGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (room.roomType == "group") {
                    OutlinedButton(onClick = {
                        onToggleMembership()
                        showRoomInfo = false
                    }) {
                        Text(if (room.isMember) "Leave Room" else "Join Room")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoomInfo = false }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomTopBar(
    room: ChatRoomSummary,
    onBack: () -> Unit,
    onOpenRoomInfo: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chat_thread_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clickable(onClick = onOpenRoomInfo)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEAF2EA)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(room.avatarEmoji, fontSize = 20.sp)
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = room.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${room.memberCount}/${room.maxMembers}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF6B7280)
                            )
                            Text("·", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16A34A))
                            )
                            Text(
                                text = "${room.onlineCount} online",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4B5563)
                            )
                            if (room.isMember) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFEDEDED)
                                ) {
                                    Text(
                                        text = "Member",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF374151),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            actions = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    color = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    IconButton(
                        onClick = onOpenRoomInfo,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chat_room_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = "Room members & AI settings",
                            tint = Color(0xFF111827)
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun ChatComposerBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    extractingHandwriting: Boolean,
    autoCorrectEnabled: Boolean,
    aiConfigured: Boolean,
    replyingTo: ChatMessageItem?,
    starterChips: List<String>,
    onOpenHandwritingCapture: () -> Unit,
    onToggleAutoCorrect: () -> Unit,
    onCancelReply: () -> Unit,
    onChipClick: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            replyingTo?.let { target ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF3F6F3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${target.senderName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ChatPrimaryGreen
                            )
                            Text(
                                text = target.displayTextForViewer(),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4B5563),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onCancelReply,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel reply")
                        }
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(starterChips) { chip ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFD6DDD6)),
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onChipClick(chip) }
                            .testTag("chat_starter_chip")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelLarge,
                                color = ChatPrimaryGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "Write in English...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                            BasicTextField(
                                value = draft,
                                onValueChange = onDraftChange,
                                singleLine = false,
                                maxLines = 3,
                                textStyle = TextStyle(
                                    color = Color(0xFF111827),
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(ChatPrimaryGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_message_input")
                            )
                        }
                        IconButton(
                            onClick = onOpenHandwritingCapture,
                            enabled = !extractingHandwriting && !sending,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(38.dp)
                                .testTag("chat_camera_handwriting_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = "Scan handwritten text from photo",
                                tint = ChatPrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleAutoCorrect,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(36.dp)
                                .testTag("chat_toggle_ai_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = if (autoCorrectEnabled) "AI auto-correct on" else "AI auto-correct off",
                                tint = when {
                                    !aiConfigured -> Color(0xFFCBD5E1)
                                    autoCorrectEnabled -> Color(0xFFEA8C1E)
                                    else -> Color(0xFF9CA3AF)
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                val canSend = draft.isNotBlank() && !sending
                Surface(
                    shape = CircleShape,
                    color = if (canSend) ChatPrimaryGreen else Color(0xFFF3F4F6),
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(enabled = canSend, onClick = onSend)
                        .testTag("chat_send_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = if (canSend) Color.White else Color(0xFF9CA3AF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
