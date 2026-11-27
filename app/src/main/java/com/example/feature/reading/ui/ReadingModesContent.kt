package com.example.feature.reading.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.ui.PermissionPromptDialog
import com.example.core.ui.isPermissionPermanentlyDenied
import com.example.core.ui.openAppPermissionSettings
import com.example.core.ui.requestSystemPermission
import com.example.domain.model.ReadingAttemptRecord
import com.example.domain.model.ReadingPassageDetail
import com.example.domain.model.ReadingQuestion
import com.example.domain.model.ReadingQuestionType
import com.example.domain.model.TextMatchComparisonResult
import com.example.domain.model.WordMatchStatus
import com.example.feature.reading.QuizUiState
import com.example.feature.reading.ReadAloudUiState
import com.example.feature.reading.audio.AudioManagerState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadAloudModeContent(
    passage: ReadingPassageDetail,
    audioState: AudioManagerState,
    readAloudState: ReadAloudUiState,
    onStartMic: () -> Unit,
    onStopMic: () -> Unit,
    onMicPermissionDenied: () -> Unit,
    onPlayActiveSentence: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit
) {
    val pal = readingPalette()
    val context = LocalContext.current
    val activeIdx = audioState.activeSentenceIndex.coerceIn(0, (passage.sentences.size - 1).coerceAtLeast(0))
    val activeSentence = passage.sentences.getOrNull(activeIdx)

    var showMicPermissionDialog by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartMic()
        } else {
            onMicPermissionDenied()
            if (isPermissionPermanentlyDenied(context, Manifest.permission.RECORD_AUDIO)) {
                showMicPermissionDialog = true
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = pal.card,
                border = BorderStroke(1.dp, pal.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Sentence (${activeIdx + 1}/${passage.sentences.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = pal.primary
                        )
                        TextButton(onClick = onPlayActiveSentence) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Hear Model")
                        }
                    }

                    Text(
                        text = activeSentence?.en ?: "No sentence available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = pal.textPrimary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPreviousSentence,
                            enabled = activeIdx > 0,
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) { Text("Prev") }
                        OutlinedButton(
                            onClick = onNextSentence,
                            enabled = activeIdx < passage.sentences.lastIndex,
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) { Text("Next") }
                    }
                }
            }
        }

        // Speech-to-Text Voice Read Aloud
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = pal.card,
                border = BorderStroke(1.dp, pal.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Read Aloud · Text Matching",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = pal.textPrimary
                    )
                    Text(
                        text = "Speak the sentence clearly. Transcripts are compared locally against the original text — raw audio is never stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.textSecondary
                    )

                    if (!audioState.sttSupported) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = pal.amberContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, pal.amber.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Speech recognition is unavailable on this device. Tap \"Hear Model\" above to listen and practice.",
                                style = MaterialTheme.typography.bodySmall,
                                color = pal.textPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        if (audioState.isListening) {
                            Button(
                                onClick = onStopMic,
                                colors = ButtonDefaults.buttonColors(containerColor = pal.missing, contentColor = pal.onPrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("read_aloud_stop_mic")
                            ) {
                                Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Stop Listening", maxLines = 1)
                            }
                        } else {
                            Button(
                                onClick = {
                                    requestSystemPermission(
                                        context = context,
                                        permission = Manifest.permission.RECORD_AUDIO,
                                        launcher = micPermissionLauncher,
                                        onAlreadyGranted = onStartMic,
                                        onPermanentlyDenied = { showMicPermissionDialog = true }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("read_aloud_start_mic")
                            ) {
                                Icon(Icons.Filled.Mic, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Speak Sentence", maxLines = 1)
                            }
                        }
                    }

                    if (!readAloudState.micStatusMessage.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = pal.amberContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = readAloudState.micStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = pal.textPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (readAloudState.partialTranscript.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = pal.background,
                            border = BorderStroke(1.dp, pal.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Recognized Transcript", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                                Text(readAloudState.partialTranscript, style = MaterialTheme.typography.bodyMedium, color = pal.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Text Matching Comparison Result Card
        readAloudState.comparison?.let { comp ->
            item {
                TextMatchingResultCard(
                    comparison = comp,
                    savedMessage = readAloudState.savedAttemptMessage
                )
            }
        }
    }

    if (showMicPermissionDialog) {
        PermissionPromptDialog(
            title = "Microphone Permission Required",
            message = "Microphone permission was denied. Enable it in App Settings to speak sentences aloud in Read Aloud mode.",
            icon = Icons.Filled.Mic,
            onAllow = {
                showMicPermissionDialog = false
                openAppPermissionSettings(context)
            },
            onCancel = { showMicPermissionDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextMatchingResultCard(
    comparison: TextMatchComparisonResult,
    savedMessage: String?
) {
    val pal = readingPalette()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = Modifier.fillMaxWidth().testTag("text_matching_result_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Text Matching",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = pal.textPrimary
                )
                Surface(shape = RoundedCornerShape(10.dp), color = pal.primaryContainer) {
                    Text(
                        text = "${comparison.matchPercent}% match",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = pal.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MatchCountPill("Matched: ${comparison.matchedCount}", pal.matchedBg, pal.matched)
                MatchCountPill("Missing: ${comparison.missingWords.size}", pal.missingBg, pal.missing)
                MatchCountPill("Mismatch: ${comparison.mismatchedPairs.size}", pal.mismatchedBg, pal.mismatched)
                MatchCountPill("Extra: ${comparison.extraWords.size}", pal.extraBg, pal.extra)
            }

            Text("Expected Sentence Tokens", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                comparison.expectedTokens.forEach { tok ->
                    val (bg, fg) = when (tok.status) {
                        WordMatchStatus.MATCHED -> pal.matchedBg to pal.matched
                        WordMatchStatus.MISSING -> pal.missingBg to pal.missing
                        WordMatchStatus.MISMATCHED -> pal.mismatchedBg to pal.mismatched
                        WordMatchStatus.EXTRA -> pal.extraBg to pal.extra
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
                        Text(
                            text = tok.text,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = fg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (comparison.spokenTokens.any { it.status == WordMatchStatus.EXTRA || it.status == WordMatchStatus.MISMATCHED }) {
                Text("Spoken Tokens", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    comparison.spokenTokens.forEach { tok ->
                        val (bg, fg) = when (tok.status) {
                            WordMatchStatus.MATCHED -> pal.matchedBg to pal.matched
                            WordMatchStatus.EXTRA -> pal.extraBg to pal.extra
                            WordMatchStatus.MISMATCHED -> pal.mismatchedBg to pal.mismatched
                            WordMatchStatus.MISSING -> pal.missingBg to pal.missing
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = bg) {
                            Text(
                                text = tok.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = fg,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (!savedMessage.isNullOrBlank()) {
                Text(savedMessage, style = MaterialTheme.typography.labelSmall, color = pal.matched, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MatchCountPill(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ReadAndUnderstandModeContent(
    questions: List<ReadingQuestion>,
    quizState: QuizUiState,
    attempts: List<ReadingAttemptRecord>,
    showBengali: Boolean,
    onSelectAnswer: (String, String) -> Unit,
    onSubmitQuiz: () -> Unit,
    onResetQuiz: () -> Unit
) {
    val pal = readingPalette()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (questions.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pal.card,
                    border = BorderStroke(1.dp, pal.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No comprehension questions have been added to this passage yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = pal.textSecondary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            val gradedMap = quizState.latestAttempt?.answers?.associateBy { it.questionId }.orEmpty()

            quizState.latestAttempt?.let { graded ->
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = pal.primaryContainer,
                        border = BorderStroke(1.dp, pal.primary),
                        modifier = Modifier.fillMaxWidth().testTag("quiz_result_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Quiz Score: ${graded.scorePercent}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.primary)
                                Text("${graded.correctCount} of ${graded.totalCount} correct", style = MaterialTheme.typography.bodySmall, color = pal.textPrimary)
                            }
                            OutlinedButton(onClick = onResetQuiz) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retake")
                            }
                        }
                    }
                }
            }

            itemsIndexed(questions, key = { _, q -> q.questionId }) { idx, q ->
                val selected = quizState.selectedAnswers[q.questionId].orEmpty()
                val graded = gradedMap[q.questionId]

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pal.card,
                    border = BorderStroke(1.dp, pal.border),
                    modifier = Modifier.fillMaxWidth().testTag("quiz_question_$idx")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Q${idx + 1}. ${q.prompt}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = pal.textPrimary
                        )

                        when (q.type) {
                            ReadingQuestionType.MCQ, ReadingQuestionType.TRUE_FALSE -> {
                                val opts = if (q.type == ReadingQuestionType.TRUE_FALSE && q.options.isEmpty()) {
                                    listOf("True", "False")
                                } else q.options

                                opts.forEach { option ->
                                    val isChosen = selected.equals(option, ignoreCase = true)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isChosen) pal.primaryContainer.copy(alpha = 0.55f) else pal.background)
                                            .clickable(enabled = graded == null) { onSelectAnswer(q.questionId, option) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isChosen,
                                            onClick = if (graded == null) ({ onSelectAnswer(q.questionId, option) }) else null
                                        )
                                        Text(option, style = MaterialTheme.typography.bodyMedium, color = pal.textPrimary)
                                    }
                                }
                            }

                            ReadingQuestionType.SHORT_ANSWER -> {
                                OutlinedTextField(
                                    value = selected,
                                    onValueChange = { if (graded == null) onSelectAnswer(q.questionId, it) },
                                    label = { Text("Your short answer") },
                                    enabled = graded == null,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        if (graded != null) {
                            HorizontalDivider(color = pal.border)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (graded.isCorrect) pal.matchedBg else pal.missingBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (graded.isCorrect) "Correct ✓" else "Incorrect · Correct: ${graded.correctAnswer}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (graded.isCorrect) pal.matched else pal.missing
                                    )
                                    if (showBengali && graded.explanationBn.isNotBlank()) {
                                        Text(
                                            text = "ব্যাখ্যা: ${graded.explanationBn}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = pal.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!quizState.submitError.isNullOrBlank()) {
                item {
                    Text(quizState.submitError, color = pal.missing, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (quizState.latestAttempt == null) {
                item {
                    Button(
                        onClick = onSubmitQuiz,
                        enabled = !quizState.isSubmitting && quizState.selectedAnswers.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("quiz_submit_button")
                    ) {
                        if (quizState.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = pal.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Submit Answers")
                    }
                }
            }
        }

        if (attempts.isNotEmpty()) {
            item {
                Text(
                    text = "Saved Attempts (${attempts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = pal.textPrimary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(attempts, key = { it.id }) { att ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = pal.card,
                    border = BorderStroke(1.dp, pal.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (att.mode == "read_aloud") "Read Aloud · Text Matching" else "Comprehension Quiz",
                                fontWeight = FontWeight.SemiBold,
                                color = pal.textPrimary
                            )
                            Text("${att.correctCount}/${att.totalCount}", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = pal.primary, modifier = Modifier.size(16.dp))
                            Text("${att.scorePercent}%", fontWeight = FontWeight.Bold, color = pal.primary)
                        }
                    }
                }
            }
        }
    }
}
