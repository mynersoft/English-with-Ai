package com.example.feature.vocabulary

import android.Manifest
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.audio.VocabAudioPlayer
import com.example.core.common.UiState
import com.example.core.ui.PermissionPromptDialog
import com.example.core.ui.openAppPermissionSettings
import com.example.core.ui.shouldOpenAppSettings
import com.example.domain.model.ReviewTabData
import com.example.feature.speaking.SpeechHelper

private data class ReviewTabOption(val key: String, val label: String)

private val reviewTabOptions = listOf(
    ReviewTabOption("due", "Due Today"),
    ReviewTabOption("difficult", "Difficult"),
    ReviewTabOption("learning", "Learning"),
    ReviewTabOption("mastered", "Mastered")
)

@Composable
fun VocabReviewScreen(
    selectedTab: String,
    reviewState: UiState<ReviewTabData>,
    feedback: VocabActionFeedback,
    onBack: () -> Unit,
    onSelectTab: (String) -> Unit,
    onSubmitReview: (vocabularyId: String, quality: Int) -> Unit
) {
    val pal = vocabPalette()
    val context = LocalContext.current
    val player = remember { VocabAudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Smart Review",
                subtitle = "Spaced Repetition (SM-2)",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 600.dp)) {
                val counts = (reviewState as? UiState.Success)?.data?.counts
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(reviewTabOptions, key = { it.key }) { opt ->
                        val count = when (opt.key) {
                            "due" -> counts?.due
                            "difficult" -> counts?.difficult
                            "learning" -> counts?.learning
                            "mastered" -> counts?.mastered
                            else -> null
                        }
                        FilterChip(
                            selected = selectedTab == opt.key,
                            onClick = { onSelectTab(opt.key) },
                            label = { Text(if (count != null) "${opt.label} ($count)" else opt.label) },
                            modifier = Modifier.testTag("vocab_review_tab_${opt.key}")
                        )
                    }
                }

                if (!feedback.message.isNullOrBlank()) {
                    VocabFeedbackBanner(
                        message = feedback.message,
                        isError = feedback.isError,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                when (reviewState) {
                    is UiState.Loading -> VocabLoadingSkeleton()
                    is UiState.Error -> VocabErrorState(
                        message = reviewState.message,
                        onRetry = { onSelectTab(selectedTab) }
                    )
                    is UiState.Success -> {
                        val itemsList = reviewState.data.items
                        if (itemsList.isEmpty()) {
                            VocabEmptyState(
                                title = "All caught up!",
                                subtitle = when (selectedTab) {
                                    "due" -> "No vocabulary words are due for review right now."
                                    "difficult" -> "No difficult words flagged yet."
                                    "mastered" -> "Keep reviewing words to reach Mastered status (5+ reps & 30d interval)."
                                    else -> "No words in this bucket yet."
                                },
                                icon = Icons.Filled.Replay
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 20.dp)
                            ) {
                                items(itemsList, key = { it.id }) { word ->
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = pal.card,
                                        border = BorderStroke(1.dp, pal.border),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = word.word,
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = pal.textPrimary
                                                    )
                                                    Text(
                                                        text = "${word.meaning} · Interval ${word.progress.intervalDays}d · Rep ${word.progress.repetition}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = pal.textSecondary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { player.play(word.word, word.audioUrl) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = "Listen",
                                                        tint = pal.primary
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                ReviewRateButton("Again", 1, false, !feedback.busy, Modifier.weight(1f)) {
                                                    onSubmitReview(word.id, 1)
                                                }
                                                ReviewRateButton("Hard", 3, false, !feedback.busy, Modifier.weight(1f)) {
                                                    onSubmitReview(word.id, 3)
                                                }
                                                ReviewRateButton("Good", 4, true, !feedback.busy, Modifier.weight(1f)) {
                                                    onSubmitReview(word.id, 4)
                                                }
                                                ReviewRateButton("Easy", 5, true, !feedback.busy, Modifier.weight(1f)) {
                                                    onSubmitReview(word.id, 5)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRateButton(
    label: String,
    quality: Int,
    positive: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val pal = vocabPalette()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (positive) pal.primary else pal.gold
        ),
        border = BorderStroke(1.dp, if (positive) pal.primary.copy(alpha = 0.45f) else pal.gold.copy(alpha = 0.45f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("vocab_review_q_$quality")
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabSpeakingPracticeScreen(
    state: SpeakingPracticeUiState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onSubmitTranscript: (transcript: String, durationSeconds: Int) -> Unit
) {
    val pal = vocabPalette()
    val context = LocalContext.current
    val speechHelper = remember { SpeechHelper(context) }
    DisposableEffect(Unit) { onDispose { speechHelper.release() } }

    var isListening by remember { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var localNotice by remember { mutableStateOf<String?>(null) }
    var recordStartMs by remember { mutableLongStateOf(0L) }
    var permissionDenied by remember { mutableStateOf(false) }

    val speechSupported = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    fun beginRecording() {
        localNotice = null
        permissionDenied = false
        recordStartMs = System.currentTimeMillis()
        isListening = true
        speechHelper.startListening(
            onPartial = { partial -> transcript = partial },
            onFinal = { finalText ->
                isListening = false
                transcript = finalText
            },
            onErrorMessage = { msg ->
                isListening = false
                localNotice = msg
            }
        )
    }

    var showMicPermissionDialog by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            beginRecording()
        } else {
            permissionDenied = true
            showMicPermissionDialog = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Speaking Practice",
                subtitle = "Use today's words in speech",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                state.loading -> VocabLoadingSkeleton(modifier = Modifier.widthIn(max = 600.dp))
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!state.overview.aiConfigured) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = pal.goldContainer,
                                    border = BorderStroke(1.dp, pal.gold.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Info, contentDescription = null, tint = pal.gold)
                                        Text(
                                            text = "AI feedback is currently not configured on the server. Record or transcribe your practice below; scoring requires server AI configuration.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = pal.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        if (!state.error.isNullOrBlank()) {
                            item { VocabFeedbackBanner(message = state.error, isError = true) }
                        }
                        if (!localNotice.isNullOrBlank()) {
                            item { VocabFeedbackBanner(message = localNotice, isError = permissionDenied) }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = pal.card,
                                border = BorderStroke(1.dp, pal.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Challenge Prompt",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = pal.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Speak 2–3 English sentences about your day using at least two of today's words:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = pal.textPrimary
                                    )
                                    if (state.todayWords.isEmpty()) {
                                        OutlinedButton(onClick = onRetryLoad) { Text("Load Today's Words") }
                                    } else {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            state.todayWords.forEach { w ->
                                                AssistChip(
                                                    onClick = { speechHelper.speak(w.word, null) },
                                                    label = { Text("${w.word} (${w.meaning})") }
                                                )
                                            }
                                        }
                                    }

                                    if (!speechSupported) {
                                        Text(
                                            text = "On-device speech recognition is unavailable on this device. Please type your spoken sentences below.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = pal.gold
                                        )
                                    } else {
                                        Button(
                                            onClick = {
                                                if (isListening) {
                                                    speechHelper.stopListening()
                                                    isListening = false
                                                } else {
                                                    val granted = ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.RECORD_AUDIO
                                                    ) == PackageManager.PERMISSION_GRANTED
                                                    if (granted) {
                                                        beginRecording()
                                                    } else {
                                                        showMicPermissionDialog = true
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isListening) pal.danger else pal.primary,
                                                contentColor = pal.onPrimary
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .minimumInteractiveComponentSize()
                                                .testTag("vocab_speaking_mic_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                                                contentDescription = null
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = if (isListening) "Stop Recording" else "Start Speaking",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = transcript,
                                onValueChange = { transcript = it },
                                label = { Text("Your Transcript") },
                                minLines = 3,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vocab_speaking_transcript_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val sec = if (recordStartMs > 0L) {
                                        ((System.currentTimeMillis() - recordStartMs) / 1000L).toInt().coerceIn(5, 600)
                                    } else 30
                                    onSubmitTranscript(transcript, sec)
                                },
                                enabled = !state.evaluating && transcript.trim().length >= 3 && state.overview.aiConfigured,
                                colors = ButtonDefaults.buttonColors(containerColor = pal.gold, contentColor = pal.onPrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("vocab_speaking_submit_button")
                            ) {
                                if (state.evaluating) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = pal.onPrimary)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (state.overview.aiConfigured) "Evaluate Speaking" else "AI Feedback Not Configured",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (state.overview.sessions.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Recent Speaking Sessions",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = pal.textPrimary
                                )
                            }
                            items(state.overview.sessions, key = { it.id }) { s ->
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = pal.card,
                                    border = BorderStroke(1.dp, pal.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("\"${s.transcript}\"", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = pal.textPrimary)
                                        ScoreBarRow("Grammar", s.grammarScore)
                                        ScoreBarRow("Vocabulary", s.vocabularyScore)
                                        ScoreBarRow("Fluency", s.fluencyScore)
                                        if (s.feedback.isNotBlank()) {
                                            Text(s.feedback, style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMicPermissionDialog) {
        PermissionPromptDialog(
            title = "Microphone Permission Required",
            message = "Allow microphone permission to record your speaking practice.",
            icon = Icons.Filled.Mic,
            onAllow = {
                showMicPermissionDialog = false
                if (shouldOpenAppSettings(context, Manifest.permission.RECORD_AUDIO, permissionDenied)) {
                    openAppPermissionSettings(context)
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onCancel = { showMicPermissionDialog = false }
        )
    }
}

@Composable
private fun ScoreBarRow(label: String, score: Int) {
    val pal = vocabPalette()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
            Text("$score/100", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = pal.primary)
        }
        LinearProgressIndicator(
            progress = { (score / 100f).coerceIn(0f, 1f) },
            color = pal.primary,
            trackColor = pal.border,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}
