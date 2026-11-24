package com.example.feature.vocabulary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.audio.VocabAudioPlayer
import com.example.domain.model.QuizQuestionItem
import com.example.domain.model.QuizResultData

private data class QuizModeChoice(val id: String, val label: String)

private val quizModes = listOf(
    QuizModeChoice("mixed", "Mixed Quiz"),
    QuizModeChoice("en_to_bn", "EN → BN"),
    QuizModeChoice("bn_to_en", "BN → EN"),
    QuizModeChoice("fill_blank", "Fill Blank"),
    QuizModeChoice("matching", "Matching"),
    QuizModeChoice("listening", "Listening")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabQuizScreen(
    quizState: QuizUiState,
    onBack: () -> Unit,
    onStartQuiz: (type: String, count: Int) -> Unit,
    onSelectAnswer: (vocabularyId: String, option: String) -> Unit,
    onPairMatching: (vocabularyId: String, meaning: String) -> Unit,
    onMoveQuestion: (Int) -> Unit,
    onSubmitQuiz: () -> Unit
) {
    val pal = vocabPalette()
    val context = LocalContext.current
    val player = remember { VocabAudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var selectedMode by rememberSaveable { mutableStateOf("mixed") }
    var activeMatchingWordId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (quizState.session == null && !quizState.loading && quizState.result == null && quizState.error == null) {
            onStartQuiz(selectedMode, 5)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Vocabulary Quiz",
                subtitle = quizModes.find { it.id == selectedMode }?.label,
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
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(quizModes, key = { it.id }) { mode ->
                        FilterChip(
                            selected = selectedMode == mode.id,
                            onClick = {
                                selectedMode = mode.id
                                activeMatchingWordId = null
                                onStartQuiz(mode.id, 5)
                            },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("vocab_quiz_mode_${mode.id}")
                        )
                    }
                }

                when {
                    quizState.loading -> VocabLoadingSkeleton()
                    quizState.error != null && quizState.session == null -> VocabErrorState(
                        message = quizState.error,
                        onRetry = { onStartQuiz(selectedMode, 5) }
                    )
                    quizState.result != null -> QuizResultView(
                        result = quizState.result,
                        onRetry = {
                            activeMatchingWordId = null
                            onStartQuiz(selectedMode, 5)
                        }
                    )
                    quizState.session == null || quizState.session.questions.isEmpty() -> VocabEmptyState(
                        title = "Quiz unavailable",
                        subtitle = "Make sure at least 4 vocabulary words are published.",
                        icon = Icons.Filled.Quiz,
                        actionLabel = "Reload Quiz",
                        onAction = { onStartQuiz(selectedMode, 5) }
                    )
                    quizState.session.quizType == "matching" -> {
                        val q = quizState.session.questions.first()
                        MatchingQuizBody(
                            question = q,
                            pairs = quizState.matchingPairs,
                            activeWordId = activeMatchingWordId,
                            submitting = quizState.submitting,
                            error = quizState.error,
                            onSelectWord = { activeMatchingWordId = it },
                            onSelectMeaning = { meaning ->
                                val target = activeMatchingWordId
                                if (target != null) {
                                    onPairMatching(target, meaning)
                                    val nextUnpaired = q.leftItems.firstOrNull {
                                        it.vocabularyId != target && !quizState.matchingPairs.containsKey(it.vocabularyId)
                                    }?.vocabularyId
                                    activeMatchingWordId = nextUnpaired
                                }
                            },
                            onSubmit = onSubmitQuiz
                        )
                    }
                    else -> {
                        val questions = quizState.session.questions
                        val index = quizState.currentIndex.coerceIn(0, questions.lastIndex)
                        val q = questions[index]
                        val selected = quizState.selectedAnswers[q.vocabularyId]

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Question ${index + 1} of ${questions.size}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = pal.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${quizState.selectedAnswers.size}/${questions.size} answered",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = pal.textSecondary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (index + 1f) / questions.size.toFloat() },
                                        color = pal.primary,
                                        trackColor = pal.border,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }

                            if (!quizState.error.isNullOrBlank()) {
                                item { VocabFeedbackBanner(message = quizState.error, isError = true) }
                            }

                            item {
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = pal.card,
                                    border = BorderStroke(1.dp, pal.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(shape = RoundedCornerShape(8.dp), color = pal.goldContainer) {
                                            Text(
                                                text = questionBadgeLabel(q.questionType),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = pal.gold,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }

                                        if (q.questionType == "listening") {
                                            Text(
                                                text = "Listen carefully and choose the matching English word.",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = pal.textPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Button(
                                                onClick = { player.play(q.prompt, q.audioUrl) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = pal.primary,
                                                    contentColor = pal.onPrimary
                                                ),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(52.dp)
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("vocab_quiz_listen_button")
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Play Word Audio", fontWeight = FontWeight.SemiBold)
                                            }
                                            if (q.hint.isNotBlank()) {
                                                Text(
                                                    text = "Meaning clue: ${q.hint}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = pal.textSecondary
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = q.prompt,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = pal.textPrimary
                                            )
                                            if (q.hint.isNotBlank()) {
                                                Text(
                                                    text = q.hint,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = pal.textSecondary
                                                )
                                            }
                                            if (q.questionType == "en_to_bn") {
                                                OutlinedButton(
                                                    onClick = { player.play(q.prompt, q.audioUrl) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Pronounce")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            items(q.options) { option ->
                                val isChosen = selected == option
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isChosen) pal.primaryContainer else pal.card,
                                    border = BorderStroke(
                                        width = if (isChosen) 2.dp else 1.dp,
                                        color = if (isChosen) pal.primary else pal.border
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onSelectAnswer(q.vocabularyId, option) }
                                        .testTag("vocab_quiz_option")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Medium,
                                            color = pal.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isChosen) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = pal.primary)
                                        }
                                    }
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, bottom = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onMoveQuestion(-1) },
                                        enabled = index > 0 && !quizState.submitting,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Text("Previous")
                                    }
                                    if (index < questions.lastIndex) {
                                        Button(
                                            onClick = { onMoveQuestion(1) },
                                            enabled = !quizState.submitting,
                                            colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Text("Next")
                                        }
                                    } else {
                                        Button(
                                            onClick = onSubmitQuiz,
                                            enabled = !quizState.submitting && quizState.selectedAnswers.size == questions.size,
                                            colors = ButtonDefaults.buttonColors(containerColor = pal.gold, contentColor = pal.onPrimary),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .minimumInteractiveComponentSize()
                                                .testTag("vocab_quiz_submit_button")
                                        ) {
                                            if (quizState.submitting) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = pal.onPrimary)
                                            } else {
                                                Text("Submit Quiz", fontWeight = FontWeight.Bold)
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
private fun MatchingQuizBody(
    question: QuizQuestionItem,
    pairs: Map<String, String>,
    activeWordId: String?,
    submitting: Boolean,
    error: String?,
    onSelectWord: (String) -> Unit,
    onSelectMeaning: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val pal = vocabPalette()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Tap an English word, then tap its matching Bengali meaning (${pairs.size}/${question.leftItems.size} paired).",
                style = MaterialTheme.typography.bodyMedium,
                color = pal.textSecondary
            )
        }
        if (!error.isNullOrBlank()) {
            item { VocabFeedbackBanner(message = error, isError = true) }
        }
        item {
            Text("1. English Words", style = MaterialTheme.typography.labelLarge, color = pal.primary, fontWeight = FontWeight.Bold)
        }
        items(question.leftItems, key = { it.vocabularyId }) { left ->
            val selected = activeWordId == left.vocabularyId
            val assigned = pairs[left.vocabularyId]
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (selected) pal.primaryContainer else pal.card,
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) pal.primary else pal.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectWord(left.vocabularyId) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(left.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                        if (left.pronunciation.isNotBlank()) {
                            Text(left.pronunciation, style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                        }
                    }
                    if (!assigned.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(10.dp), color = pal.goldContainer) {
                            Text(
                                text = assigned,
                                style = MaterialTheme.typography.labelMedium,
                                color = pal.gold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        item {
            Text("2. Bengali Meanings", style = MaterialTheme.typography.labelLarge, color = pal.gold, fontWeight = FontWeight.Bold)
        }
        items(question.rightItems, key = { it.optionId }) { right ->
            val used = pairs.values.contains(right.meaning)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (used) pal.primaryContainer.copy(alpha = 0.5f) else pal.card,
                border = BorderStroke(1.dp, if (used) pal.primary else pal.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = activeWordId != null) { onSelectMeaning(right.meaning) }
            ) {
                Text(
                    text = right.meaning,
                    style = MaterialTheme.typography.bodyLarge,
                    color = pal.textPrimary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        item {
            Button(
                onClick = onSubmit,
                enabled = !submitting && pairs.size == question.leftItems.size && question.leftItems.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("vocab_matching_submit_button")
            ) {
                Text(if (submitting) "Grading..." else "Submit Matches", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuizResultView(
    result: QuizResultData,
    onRetry: () -> Unit
) {
    val pal = vocabPalette()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = pal.card,
                border = BorderStroke(1.5.dp, if (result.score >= 70) pal.primary else pal.gold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = if (result.score >= 70) pal.primary else pal.gold,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "${result.score}%",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = pal.textPrimary
                    )
                    Text(
                        text = "${result.correctAnswers} of ${result.totalQuestions} correct · ${result.timeTaken}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = pal.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("vocab_quiz_retry_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Play Another Quiz", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        items(result.results) { row ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = pal.card,
                border = BorderStroke(1.dp, if (row.isCorrect) pal.primary.copy(alpha = 0.4f) else pal.danger.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (row.isCorrect) Icons.Filled.CheckCircle else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (row.isCorrect) pal.primary else pal.danger
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${row.word} — ${row.meaning}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                        Text("Your answer: ${row.selectedAnswer.ifBlank { "(blank)" }}", style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                        if (!row.isCorrect) {
                            Text("Correct answer: ${row.correctAnswer}", style = MaterialTheme.typography.bodySmall, color = pal.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun questionBadgeLabel(type: String): String = when (type) {
    "en_to_bn" -> "English → Bengali"
    "bn_to_en" -> "Bengali → English"
    "fill_blank" -> "Fill in the Blank"
    "listening" -> "Listening Test"
    "matching" -> "Word Matching"
    else -> "Vocabulary Check"
}

@Composable
fun VocabSentenceBuildingScreen(
    state: SentenceUiState,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onMoveWord: (Int) -> Unit,
    onCheckSentence: (vocabularyId: String, sentence: String) -> Unit
) {
    val pal = vocabPalette()
    val currentWord = state.words.getOrNull(state.currentIndex)
    var sentenceInput by rememberSaveable(currentWord?.id) { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Sentence Building",
                subtitle = if (state.words.isNotEmpty()) "Word ${state.currentIndex + 1} of ${state.words.size}" else null,
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
                state.error != null && state.words.isEmpty() -> VocabErrorState(
                    message = state.error,
                    onRetry = onRetryLoad,
                    modifier = Modifier.widthIn(max = 600.dp)
                )
                currentWord == null -> VocabEmptyState(
                    title = "No words to practice",
                    subtitle = "Load today's vocabulary words first.",
                    actionLabel = "Load Words",
                    onAction = onRetryLoad,
                    modifier = Modifier.widthIn(max = 600.dp)
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = pal.card,
                                border = BorderStroke(1.dp, pal.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "TARGET WORD (${currentWord.level} · ${currentWord.partOfSpeech})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = pal.gold,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = currentWord.word,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = pal.textPrimary
                                    )
                                    Text(
                                        text = "বাংলা অর্থ: ${currentWord.meaning}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = pal.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "এই শব্দটি ব্যবহার করে একটি ইংরেজি বাক্য লিখুন।",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = pal.textSecondary
                                    )
                                }
                            }
                        }

                        item {
                            OutlinedTextField(
                                value = sentenceInput,
                                onValueChange = { sentenceInput = it },
                                label = { Text("Write an English sentence using \"${currentWord.word}\"") },
                                minLines = 3,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vocab_sentence_input")
                            )
                        }

                        if (!state.error.isNullOrBlank()) {
                            item { VocabFeedbackBanner(message = state.error, isError = true) }
                        }

                        item {
                            Button(
                                onClick = { onCheckSentence(currentWord.id, sentenceInput) },
                                enabled = !state.checking && sentenceInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("vocab_sentence_check_button")
                            ) {
                                if (state.checking) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = pal.onPrimary)
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Check Sentence", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        state.result?.let { res ->
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (!res.aiConfigured) pal.goldContainer else if (res.isCorrect) pal.primaryContainer else pal.dangerContainer,
                                    border = BorderStroke(1.dp, pal.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (!res.aiConfigured) Icons.Filled.Info else if (res.isCorrect) Icons.Filled.CheckCircle else Icons.Filled.Close,
                                                contentDescription = null,
                                                tint = if (!res.aiConfigured) pal.gold else if (res.isCorrect) pal.primary else pal.danger
                                            )
                                            Text(
                                                text = when {
                                                    !res.aiConfigured -> "Basic Check (AI evaluation not configured)"
                                                    res.isCorrect -> "দারুণ হয়েছে! বাক্যটি সঠিক"
                                                    else -> "সংশোধন প্রয়োজন"
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = pal.textPrimary
                                            )
                                        }
                                        Text(
                                            text = res.feedbackBn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = pal.textPrimary
                                        )
                                        if (res.correctedSentence.isNotBlank()) {
                                            Text(
                                                text = "Suggested: ${res.correctedSentence}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = pal.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = { onMoveWord(-1) },
                                    enabled = state.currentIndex > 0,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) { Text("Previous Word") }
                                OutlinedButton(
                                    onClick = { onMoveWord(1) },
                                    enabled = state.currentIndex < state.words.lastIndex,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) { Text("Next Word") }
                            }
                        }
                    }
                }
            }
        }
    }
}
