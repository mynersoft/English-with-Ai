package com.example.feature.speaking

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.ui.PermissionPromptDialog
import com.example.core.ui.findActivity
import com.example.core.ui.isPermissionPermanentlyDenied
import com.example.core.ui.markPermissionRequested
import com.example.core.ui.openAppPermissionSettings
import com.example.core.ui.requestSystemPermission
import com.example.data.repository.Correction
import com.example.ui.theme.SuccessGreen

/** Entry point used by navigation. [level] is the learner's level: Beginner / Intermediate / Advanced. */
@Composable
fun SpeakingRoute(
    level: String,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val vm: SpeakingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SpeakingViewModel(checkNotNull(this[APPLICATION_KEY]), level) }
        }
    )
    SpeakingScreen(vm = vm, onBack = onBack, bottomBar = bottomBar)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakingScreen(
    vm: SpeakingViewModel,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    var showMicSettingsDialog by remember { mutableStateOf(false) }
    var showMicRationaleDialog by remember { mutableStateOf(false) }
    var pendingClearDraft by remember { mutableStateOf(false) }

    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val isImeVisible = imeBottom > navBottom

    val voiceActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = matches?.firstOrNull()
            if (recognized.isNullOrBlank()) {
                vm.showError("I did not hear anything clearly. Tap the mic and try again.")
            } else {
                vm.onExternalVoiceResult(recognized)
            }
        }
    }

    fun startVoiceInput(clearDraftFirst: Boolean) {
        if (state.phase == SpeakingPhase.LISTENING) {
            vm.onMicTap()
            return
        }
        if (vm.isInlineSpeechAvailable()) {
            vm.onMicTap(clearDraftFirst = clearDraftFirst)
        } else if (vm.canUseVoiceActivityFallback()) {
            if (clearDraftFirst) vm.onDraftChange("")
            runCatching {
                voiceActivityLauncher.launch(vm.buildVoiceIntent())
            }.onFailure {
                vm.showError("Could not launch voice input on this phone. You can type instead.")
            }
        } else {
            vm.showError("Speech recognition is not available on this phone. You can type instead.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val clearDraft = pendingClearDraft
        pendingClearDraft = false
        if (granted) {
            startVoiceInput(clearDraftFirst = clearDraft)
        } else if (isPermissionPermanentlyDenied(context, Manifest.permission.RECORD_AUDIO)) {
            showMicSettingsDialog = true
        } else {
            vm.showError("Microphone permission is needed to practice pronunciation with voice-to-text. Tap the mic to allow.")
        }
    }

    val triggerMic: (Boolean) -> Unit = { clearDraftFirst ->
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasMicPermission) {
            startVoiceInput(clearDraftFirst = clearDraftFirst)
        } else {
            pendingClearDraft = clearDraftFirst
            val activity = context.findActivity()
            val shouldShowRationale = activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)

            if (shouldShowRationale) {
                showMicRationaleDialog = true
            } else {
                requestSystemPermission(
                    context = context,
                    permission = Manifest.permission.RECORD_AUDIO,
                    launcher = permissionLauncher,
                    onAlreadyGranted = { startVoiceInput(clearDraftFirst = clearDraftFirst) },
                    onPermanentlyDenied = {
                        pendingClearDraft = false
                        showMicSettingsDialog = true
                    }
                )
            }
        }
    }

    val extraItems = if (state.phase == SpeakingPhase.THINKING) 1 else 0
    LaunchedEffect(state.messages.size, state.phase, isImeVisible) {
        val total = state.messages.size + extraItems
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SpeakingComposerBar(
                    state = state,
                    onDraftChange = vm::onDraftChange,
                    onSend = vm::sendCurrentDraft,
                    onMic = { triggerMic(false) },
                    onCancelListening = vm::cancelListening,
                    modifier = if (isImeVisible) Modifier.imePadding() else Modifier
                )
                if (!isImeVisible) {
                    bottomBar()
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("AI Speaking Partner", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SpeakingTopics) { topic ->
                    FilterChip(
                        selected = topic == state.topic,
                        onClick = { vm.setTopic(topic) },
                        label = { Text(topic) }
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    if (message.isUser) {
                        UserBubble(
                            text = message.text,
                            correction = message.correction,
                            onListenCorrection = { phrase -> vm.speakText(phrase, slow = true) },
                            onPracticeCorrection = { triggerMic(true) }
                        )
                    } else {
                        AiBubble(
                            text = message.text,
                            onListen = { vm.speakText(message.text) }
                        )
                    }
                }
                if (state.phase == SpeakingPhase.THINKING) {
                    item(key = "thinking") { ThinkingBubble() }
                }
            }

            state.error?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = vm::clearError) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
        }
    }

    if (showMicRationaleDialog) {
        PermissionPromptDialog(
            title = "Allow Microphone Access",
            message = "English AI uses your microphone for voice-to-text so you can speak sentences aloud and practice English pronunciation with the AI Speaking Partner.",
            icon = Icons.Filled.Mic,
            confirmText = "Allow Mic",
            onAllow = {
                showMicRationaleDialog = false
                markPermissionRequested(context, Manifest.permission.RECORD_AUDIO)
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onCancel = {
                pendingClearDraft = false
                showMicRationaleDialog = false
            }
        )
    }

    if (showMicSettingsDialog) {
        PermissionPromptDialog(
            title = "Microphone Permission Required",
            message = "Microphone permission was denied. Enable it in App Settings to speak and practice pronunciation with the AI Speaking Partner.",
            icon = Icons.Filled.Mic,
            onAllow = {
                showMicSettingsDialog = false
                openAppPermissionSettings(context)
            },
            onCancel = { showMicSettingsDialog = false }
        )
    }
}

@Composable
private fun SpeakingComposerBar(
    state: SpeakingUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onCancelListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSend = (state.draftText.isNotBlank() || state.partialText.isNotBlank()) &&
        state.phase != SpeakingPhase.THINKING

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            AnimatedVisibility(visible = state.phase == SpeakingPhase.LISTENING) {
                ListeningVoiceBanner(
                    voiceLevel = state.voiceLevel,
                    isReady = state.isRecognizerReady,
                    partialText = state.partialText,
                    onCancel = onCancelListening
                )
            }

            if (state.phase != SpeakingPhase.LISTENING) {
                Text(
                    text = when (state.phase) {
                        SpeakingPhase.IDLE -> if (state.draftText.isBlank()) {
                            "Tap the mic to speak or write below"
                        } else {
                            "Review your pronunciation text and tap Send"
                        }
                        SpeakingPhase.LISTENING -> ""
                        SpeakingPhase.THINKING -> "AI is thinking..."
                        SpeakingPhase.SPEAKING -> "AI is speaking... tap the mic to interrupt"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp, start = 16.dp, end = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.draftText,
                    onValueChange = onDraftChange,
                    placeholder = {
                        Text(
                            if (state.phase == SpeakingPhase.LISTENING) "Speak in English now..." else "Write or speak here..."
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("speaking_message_input"),
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) onSend()
                        }
                    )
                )
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("speaking_send_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
                MicButton(phase = state.phase, voiceLevel = state.voiceLevel, onClick = onMic)
            }
        }
    }
}

@Composable
private fun ListeningVoiceBanner(
    voiceLevel: Float,
    isReady: Boolean,
    partialText: String,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("speaking_listening_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            VoiceLevelBars(voiceLevel = voiceLevel)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isReady) "Listening (English US) — speak clearly" else "Starting microphone...",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (partialText.isNotBlank()) {
                    Text(
                        text = partialText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                        maxLines = 2
                    )
                }
            }
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("speaking_cancel_listen_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Cancel voice input",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun VoiceLevelBars(voiceLevel: Float) {
    val smoothed by animateFloatAsState(
        targetValue = voiceLevel.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "voiceLevel"
    )
    val multipliers = listOf(0.45f, 0.75f, 1.0f, 0.75f, 0.45f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(24.dp)
    ) {
        multipliers.forEach { weight ->
            val barHeight = (6f + 18f * smoothed * weight).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }
}

@Composable
private fun MicButton(phase: SpeakingPhase, voiceLevel: Float, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val idlePulse by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "micScale"
    )
    val listening = phase == SpeakingPhase.LISTENING
    val dynamicScale = if (listening) {
        (idlePulse + voiceLevel * 0.08f).coerceAtMost(1.22f)
    } else {
        1f
    }
    FilledIconButton(
        onClick = onClick,
        enabled = phase != SpeakingPhase.THINKING,
        modifier = Modifier
            .size(52.dp)
            .minimumInteractiveComponentSize()
            .scale(dynamicScale)
            .testTag("speaking_mic_button"),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (listening || phase == SpeakingPhase.SPEAKING) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (listening) "Stop listening" else "Start voice input",
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun AiBubble(text: String, onListen: () -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("AI Teacher", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(
                    onClick = onListen,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("speaking_ai_speak_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Listen to AI pronunciation",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { copySpeakingText(context, text) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("speaking_ai_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy AI reply",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .testTag("speaking_ai_bubble")
            ) {
                SelectionContainer {
                    Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.size(32.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun UserBubble(
    text: String,
    correction: Correction?,
    live: Boolean = false,
    onListenCorrection: (String) -> Unit = {},
    onPracticeCorrection: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (!live && text.isNotBlank()) {
                IconButton(
                    onClick = { copySpeakingText(context, text) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("speaking_user_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy your message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Text(if (live) "You (listening...)" else "You", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (live) 0.6f else 1f),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            SelectionContainer {
                Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        if (correction != null) {
            CorrectionCard(
                correction = correction,
                onListen = { onListenCorrection(correction.corrected) },
                onPractice = onPracticeCorrection
            )
        }
    }
}

@Composable
private fun CorrectionCard(
    correction: Correction,
    onListen: () -> Unit,
    onPractice: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
        modifier = Modifier
            .widthIn(max = 320.dp)
            .testTag("speaking_correction_card")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Better way to say it", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = SuccessGreen)
                IconButton(
                    onClick = { copySpeakingText(context, correction.corrected) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("speaking_correction_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy corrected sentence",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Close, contentDescription = "Mistake", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(
                            correction.original,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Check, contentDescription = "Correct", tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        Text(correction.corrected, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (correction.explanationBn.isNotBlank()) {
                        Text(correction.explanationBn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onListen,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("speaking_correction_listen_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Listen", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = onPractice,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("speaking_correction_practice_button")
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Practice", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun copySpeakingText(context: Context, text: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Speaking Message", text))
    Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
}
