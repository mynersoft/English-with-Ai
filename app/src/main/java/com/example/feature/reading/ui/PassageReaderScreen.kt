package com.example.feature.reading.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ReadingPassageDetail
import com.example.domain.model.ReadingPracticeMode
import com.example.domain.model.ReadingSentence
import com.example.feature.reading.PassageReaderViewModel
import com.example.feature.reading.audio.TextMatchComparator

@Composable
fun PassageReaderScreen(
    passageId: String,
    viewModel: PassageReaderViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioState by viewModel.audioState.collectAsStateWithLifecycle()
    val pal = readingPalette()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(passageId) {
        viewModel.loadPassage(passageId)
    }

    LaunchedEffect(uiState.progressMessage) {
        uiState.progressMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearProgressMessage()
        }
    }

    // Save progress when leaving or pausing the reader screen
    DisposableEffect(lifecycleOwner, passageId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushProgress(markCompleted = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushProgress(markCompleted = false)
            viewModel.stopAudio()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ReadingTopBar(
                title = uiState.passage?.summary?.title ?: "Reading Passage",
                subtitle = uiState.passage?.let { "${it.summary.level} · ${it.summary.categoryName.ifBlank { "Reading" }}" },
                onBack = {
                    viewModel.flushProgress(markCompleted = false)
                    onBack()
                },
                actions = {
                    IconButton(
                        onClick = viewModel::toggleBengali,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_bengali_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = if (uiState.showBengali) "Hide Bengali translation" else "Show Bengali translation",
                            tint = if (uiState.showBengali) pal.primary else pal.textSecondary
                        )
                    }
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
            when {
                uiState.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = pal.primary)
                    }
                }

                uiState.error != null || uiState.passage == null -> {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(uiState.error ?: "Passage unavailable.", color = pal.missing)
                        Button(
                            onClick = { viewModel.loadPassage(passageId) },
                            colors = ButtonDefaults.buttonColors(containerColor = pal.primary)
                        ) { Text("Retry") }
                    }
                }

                else -> {
                    val passage = uiState.passage!!
                    val totalSentences = passage.sentences.size
                    val readPct = if (totalSentences > 0) {
                        (uiState.readSentenceIds.size.toFloat() / totalSentences.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Progress Bar
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Reading Progress · ${uiState.readSentenceIds.size}/$totalSentences sentences",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pal.textSecondary
                                )
                                Text(
                                    text = "${(readPct * 100).toInt()}% · ${uiState.totalSessionSeconds / 60}m ${uiState.totalSessionSeconds % 60}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = pal.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { readPct },
                                color = pal.primary,
                                trackColor = pal.border,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Provider-Independent Audio Player
                        AudioPlayerBar(
                            audioState = audioState,
                            sentenceCount = totalSentences,
                            hasAudioUrl = !passage.audioUrl.isNullOrBlank(),
                            onPlayPassage = viewModel::playFullPassage,
                            onPlayMp3 = viewModel::playOptionalMp3,
                            onPause = viewModel::pauseAudio,
                            onResume = viewModel::resumeAudio,
                            onStop = viewModel::stopAudio,
                            onReplay = viewModel::replayAudio,
                            onPrevious = viewModel::previousSentence,
                            onNext = viewModel::nextSentence,
                            onSelectSpeed = viewModel::setSpeed,
                            onSelectVoice = viewModel::selectVoice
                        )

                        // Mode Switcher
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReadingPracticeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.mode == mode,
                                    onClick = { viewModel.selectMode(mode) },
                                    label = { Text(mode.label, maxLines = 1) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reading_mode_${mode.name.lowercase()}")
                                )
                            }
                        }

                        // Mode Content
                        Box(modifier = Modifier.weight(1f)) {
                            when (uiState.mode) {
                                ReadingPracticeMode.LISTEN_AND_READ -> {
                                    ListenAndReadContent(
                                        passage = passage,
                                        activeSentenceIndex = audioState.activeSentenceIndex,
                                        readSentenceIds = uiState.readSentenceIds,
                                        showBengali = uiState.showBengali,
                                        isCompleted = uiState.progress?.status == "completed",
                                        onPlaySentence = viewModel::playSentence,
                                        onWordTap = viewModel::onWordTapped,
                                        onMarkCompleted = viewModel::markPassageCompleted
                                    )
                                }

                                ReadingPracticeMode.READ_ALOUD -> {
                                    ReadAloudModeContent(
                                        passage = passage,
                                        audioState = audioState,
                                        readAloudState = uiState.readAloud,
                                        onStartMic = viewModel::startReadAloudMic,
                                        onStopMic = viewModel::stopReadAloudMic,
                                        onPlayActiveSentence = { viewModel.playSentence(audioState.activeSentenceIndex) },
                                        onPreviousSentence = viewModel::previousSentence,
                                        onNextSentence = viewModel::nextSentence,
                                        onEnableManualMode = { viewModel.enableManualReadingMode() },
                                        onDisableManualMode = viewModel::disableManualReadingModeIfSupported,
                                        onManualTextChange = viewModel::updateManualTypedTranscript,
                                        onEvaluateManualText = viewModel::evaluateManualTypedMatch,
                                        onToggleManualSentenceRead = viewModel::toggleManualSentenceRead
                                    )
                                }

                                ReadingPracticeMode.READ_AND_UNDERSTAND -> {
                                    ReadAndUnderstandModeContent(
                                        questions = passage.questions,
                                        quizState = uiState.quiz,
                                        attempts = uiState.attempts,
                                        showBengali = uiState.showBengali,
                                        onSelectAnswer = viewModel::selectQuizAnswer,
                                        onSubmitQuiz = viewModel::submitQuiz,
                                        onResetQuiz = viewModel::clearQuizFeedback
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    WordPopupSheet(
        state = uiState.wordPopup,
        onListenWord = viewModel::listenPopupWord,
        onSaveToVocabulary = viewModel::savePopupWordToVocabulary,
        onDismiss = viewModel::dismissWordPopup
    )
}

@Composable
private fun ListenAndReadContent(
    passage: ReadingPassageDetail,
    activeSentenceIndex: Int,
    readSentenceIds: Set<String>,
    showBengali: Boolean,
    isCompleted: Boolean,
    onPlaySentence: (Int) -> Unit,
    onWordTap: (String) -> Unit,
    onMarkCompleted: () -> Unit
) {
    val pal = readingPalette()
    val listState = rememberLazyListState()
    var vocabExpanded by remember { mutableStateOf(false) }

    val highlightedWords = remember(passage.vocabulary) {
        passage.vocabulary.map { TextMatchComparator.normalizeWord(it.word) }.filter { it.isNotBlank() }.toSet()
    }

    // Auto-scroll active sentence smoothly into view during full-passage or sentence playback
    LaunchedEffect(activeSentenceIndex) {
        if (activeSentenceIndex in passage.sentences.indices) {
            runCatching { listState.animateScrollToItem(activeSentenceIndex + 1) }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Tap any word for Bengali meaning & save · Tap Play on a sentence to listen",
                style = MaterialTheme.typography.labelSmall,
                color = pal.textSecondary
            )
        }

        itemsIndexed(passage.sentences, key = { _, s -> s.sentenceId }) { index, sentence ->
            SentenceReaderCard(
                index = index,
                sentence = sentence,
                isActive = index == activeSentenceIndex,
                isRead = readSentenceIds.contains(sentence.sentenceId),
                showBengali = showBengali,
                highlightedWords = highlightedWords,
                onPlay = { onPlaySentence(index) },
                onWordTap = onWordTap
            )
        }

        if (showBengali && passage.bengaliTranslation.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pal.primaryContainer.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, pal.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Full Bengali Translation",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = pal.primary
                        )
                        Text(
                            text = passage.bengaliTranslation,
                            style = MaterialTheme.typography.bodyMedium,
                            color = pal.textPrimary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }

        if (passage.vocabulary.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pal.card,
                    border = BorderStroke(1.dp, pal.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vocabExpanded = !vocabExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Passage Vocabulary (${passage.vocabulary.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = pal.textPrimary
                            )
                            Icon(
                                imageVector = if (vocabExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (vocabExpanded) "Collapse vocabulary" else "Expand vocabulary"
                            )
                        }
                        if (vocabExpanded) {
                            HorizontalDivider(color = pal.border)
                            passage.vocabulary.forEach { v ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onWordTap(v.word) }
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(v.word, fontWeight = FontWeight.SemiBold, color = pal.textPrimary)
                                        Text(
                                            text = v.meaningBn.ifBlank { "Meaning not available" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = pal.textSecondary
                                        )
                                    }
                                    if (v.partOfSpeech.isNotBlank()) {
                                        Text(v.partOfSpeech, style = MaterialTheme.typography.labelSmall, color = pal.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            if (isCompleted) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = pal.matchedBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = pal.matched)
                        Spacer(Modifier.width(8.dp))
                        Text("Passage Completed", fontWeight = FontWeight.Bold, color = pal.matched)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onMarkCompleted,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("mark_passage_completed_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mark Passage as Completed")
                }
            }
        }
    }
}

@Composable
private fun SentenceReaderCard(
    index: Int,
    sentence: ReadingSentence,
    isActive: Boolean,
    isRead: Boolean,
    showBengali: Boolean,
    highlightedWords: Set<String>,
    onPlay: () -> Unit,
    onWordTap: (String) -> Unit
) {
    val pal = readingPalette()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) pal.paperHighlight else pal.card,
        border = BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) pal.primary else pal.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sentence_card_$index")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("play_sentence_$index")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Play sentence ${index + 1}",
                    tint = if (isActive) pal.primary else pal.textSecondary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ClickableWordSentenceText(
                    text = sentence.en,
                    highlightedVocabWords = highlightedWords,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = pal.textPrimary,
                        lineHeight = 25.sp,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                    ),
                    onWordTap = onWordTap
                )

                if (showBengali && sentence.bn.isNotBlank()) {
                    Text(
                        text = sentence.bn,
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.textSecondary,
                        lineHeight = 20.sp
                    )
                }

                if (isRead) {
                    Text(
                        text = "Read ✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = pal.matched,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
