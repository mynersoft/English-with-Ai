package com.example.feature.speaking

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.ui.PermissionPromptDialog
import com.example.core.ui.openAppPermissionSettings
import com.example.core.ui.shouldOpenAppSettings
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
    var showMicPermissionDialog by remember { mutableStateOf(false) }
    var micDeniedOnce by remember { mutableStateOf(false) }

    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val isImeVisible = imeBottom > navBottom

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micDeniedOnce = false
            vm.onMicTap()
        } else {
            micDeniedOnce = true
            showMicPermissionDialog = true
        }
    }
    val onMic = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.onMicTap() else showMicPermissionDialog = true
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
                    onMic = onMic,
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
                    if (message.isUser) UserBubble(message.text, message.correction) else AiBubble(message.text)
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

    if (showMicPermissionDialog) {
        PermissionPromptDialog(
            title = "Microphone Permission Required",
            message = "Allow microphone permission to talk with the AI Speaking Partner.",
            icon = Icons.Filled.Mic,
            onAllow = {
                showMicPermissionDialog = false
                if (shouldOpenAppSettings(context, Manifest.permission.RECORD_AUDIO, micDeniedOnce)) {
                    openAppPermissionSettings(context)
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onCancel = { showMicPermissionDialog = false }
        )
    }
}

@Composable
private fun SpeakingComposerBar(
    state: SpeakingUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
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
            Text(
                text = when (state.phase) {
                    SpeakingPhase.IDLE -> if (state.draftText.isBlank()) {
                        "Tap the mic to speak or write below"
                    } else {
                        "Tap Send when ready to send"
                    }
                    SpeakingPhase.LISTENING -> "Listening... tap Stop when done, then tap Send"
                    SpeakingPhase.THINKING -> "AI is thinking..."
                    SpeakingPhase.SPEAKING -> "AI is speaking... tap the mic to interrupt"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (state.phase == SpeakingPhase.LISTENING) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp, start = 16.dp, end = 16.dp)
            )

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
                            if (state.phase == SpeakingPhase.LISTENING) "Listening..." else "Write or speak here..."
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
                MicButton(phase = state.phase, onClick = onMic)
            }
        }
    }
}

@Composable
private fun MicButton(phase: SpeakingPhase, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "micScale"
    )
    val listening = phase == SpeakingPhase.LISTENING
    FilledIconButton(
        onClick = onClick,
        enabled = phase != SpeakingPhase.THINKING,
        modifier = Modifier
            .size(52.dp)
            .scale(if (listening) scale else 1f)
            .testTag("speaking_mic_button"),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (listening || phase == SpeakingPhase.SPEAKING) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (listening) "Stop listening" else "Start speaking",
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun AiBubble(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Column {
            Text("AI Teacher", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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
private fun UserBubble(text: String, correction: Correction?, live: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (live) "You (listening...)" else "You", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (live) 0.6f else 1f),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        if (correction != null) CorrectionCard(correction)
    }
}

@Composable
private fun CorrectionCard(correction: Correction) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Better way to say it", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = SuccessGreen)
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
}
