package com.example.feature.vocabulary

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.audio.VocabAudioPlayer
import com.example.core.common.UiState
import com.example.core.network.NetworkClient
import com.example.domain.model.DailyWordsData
import com.example.domain.model.VocabWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordDetailDeckScreen(
    dailyState: UiState<DailyWordsData>,
    deck: List<VocabWord>,
    currentIndex: Int,
    autoPronounce: Boolean,
    feedback: VocabActionFeedback,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMove: (Int) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onKnowWord: (String, Boolean) -> Unit
) {
    val pal = vocabPalette()
    val context = LocalContext.current
    val player = remember { VocabAudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val currentWord = deck.getOrNull(currentIndex)
    var meaningRevealed by remember(currentWord?.id) { mutableStateOf(false) }

    LaunchedEffect(currentWord?.id, autoPronounce) {
        if (currentWord != null && autoPronounce) {
            player.play(currentWord.word, currentWord.audioUrl)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Learn Word",
                subtitle = if (deck.isNotEmpty()) "Word ${currentIndex + 1} of ${deck.size}" else null,
                onBack = onBack,
                actions = {
                    if (currentWord != null) {
                        IconButton(
                            onClick = { onToggleBookmark(currentWord.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("vocab_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (currentWord.progress.isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = "Bookmark word",
                                tint = pal.gold
                            )
                        }
                    }
                }
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
                deck.isEmpty() && dailyState is UiState.Loading ->
                    VocabLoadingSkeleton(modifier = Modifier.widthIn(max = 600.dp))
                deck.isEmpty() && dailyState is UiState.Error ->
                    VocabErrorState(
                        message = dailyState.message,
                        onRetry = onRetry,
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                currentWord == null ->
                    VocabEmptyState(
                        title = "No words available",
                        subtitle = "All words are learned or none are published yet.",
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
                        if (!feedback.message.isNullOrBlank()) {
                            item { VocabFeedbackBanner(message = feedback.message, isError = feedback.isError) }
                        }

                        // Optional word image
                        if (!currentWord.imageUrl.isNullOrBlank()) {
                            item {
                                RemoteWordImage(url = currentWord.imageUrl, contentDescription = currentWord.word)
                            }
                        }

                        // Main Word Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = pal.card,
                                border = BorderStroke(1.dp, pal.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(shape = RoundedCornerShape(8.dp), color = pal.primaryContainer) {
                                                Text(
                                                    text = currentWord.level,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = pal.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            Surface(shape = RoundedCornerShape(8.dp), color = pal.goldContainer) {
                                                Text(
                                                    text = currentWord.partOfSpeech,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = pal.gold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            if (currentWord.progress.isKnown || currentWord.progress.status.equals("mastered", ignoreCase = true)) {
                                                Surface(shape = RoundedCornerShape(8.dp), color = pal.primary) {
                                                    Text(
                                                        text = "Known ✓",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = pal.onPrimary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (currentWord.categoryName.isNotBlank()) {
                                            Text(
                                                text = currentWord.categoryName,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = pal.textSecondary
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = currentWord.word,
                                                style = MaterialTheme.typography.displaySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = pal.textPrimary
                                            )
                                            if (currentWord.pronunciation.isNotBlank()) {
                                                Text(
                                                    text = currentWord.pronunciation,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = pal.textSecondary
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = pal.primaryContainer,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { player.play(currentWord.word, currentWord.audioUrl) }
                                                .testTag("vocab_play_audio_button")
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = "Listen pronunciation",
                                                    tint = pal.primary
                                                )
                                            }
                                        }
                                    }

                                    if (!meaningRevealed) {
                                        OutlinedButton(
                                            onClick = { meaningRevealed = true },
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, pal.primary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumInteractiveComponentSize()
                                                .testTag("vocab_reveal_meaning_button")
                                        ) {
                                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Reveal Meaning (বাংলা অর্থ দেখুন)", fontWeight = FontWeight.SemiBold)
                                        }
                                    }

                                    AnimatedVisibility(visible = meaningRevealed) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = pal.primaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = currentWord.meaning,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = pal.primary,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Examples Section
                        if (currentWord.examples.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = pal.card,
                                    border = BorderStroke(1.dp, pal.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Examples",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = pal.textPrimary
                                        )
                                        currentWord.examples.forEach { ex ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = ex.en,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Medium,
                                                        color = pal.textPrimary
                                                    )
                                                    Text(
                                                        text = ex.bn,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = pal.textSecondary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { player.play(ex.en, null) },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.VolumeUp,
                                                        contentDescription = "Listen example",
                                                        tint = pal.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Synonyms & Antonyms
                        if (currentWord.synonyms.isNotEmpty() || currentWord.antonyms.isNotEmpty()) {
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
                                        if (currentWord.synonyms.isNotEmpty()) {
                                            Text(
                                                text = "Synonyms",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = pal.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                currentWord.synonyms.forEach { s ->
                                                    Surface(shape = RoundedCornerShape(10.dp), color = pal.primaryContainer) {
                                                        Text(s, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = pal.textPrimary)
                                                    }
                                                }
                                            }
                                        }
                                        if (currentWord.antonyms.isNotEmpty()) {
                                            Text(
                                                text = "Antonyms",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = pal.gold,
                                                fontWeight = FontWeight.Bold
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                currentWord.antonyms.forEach { a ->
                                                    Surface(shape = RoundedCornerShape(10.dp), color = pal.goldContainer) {
                                                        Text(a, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = pal.textPrimary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Action Buttons: Don't Know / I Know + Prev / Next
                        item {
                            val isSavedKnown = currentWord.progress.isKnown ||
                                currentWord.progress.status.equals("mastered", ignoreCase = true)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onKnowWord(currentWord.id, false) },
                                    enabled = !feedback.busy,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = pal.danger),
                                    border = BorderStroke(1.dp, pal.danger.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("vocab_dont_know_button")
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Don't Know", fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = { onKnowWord(currentWord.id, true) },
                                    enabled = !feedback.busy,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSavedKnown) pal.primaryContainer else pal.primary,
                                        contentColor = if (isSavedKnown) pal.primary else pal.onPrimary
                                    ),
                                    border = if (isSavedKnown) BorderStroke(1.dp, pal.primary) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("vocab_know_button")
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isSavedKnown) "Saved as Known" else "I Know",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = { onMove(-1) },
                                    enabled = currentIndex > 0,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Previous")
                                }
                                OutlinedButton(
                                    onClick = { onMove(1) },
                                    enabled = currentIndex < deck.lastIndex,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Next")
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
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
fun FlashcardsScreen(
    dailyState: UiState<DailyWordsData>,
    deck: List<VocabWord>,
    currentIndex: Int,
    feedback: VocabActionFeedback,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMove: (Int) -> Unit,
    onKnowWord: (String, Boolean) -> Unit
) {
    val pal = vocabPalette()
    val context = LocalContext.current
    val player = remember { VocabAudioPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    val word = deck.getOrNull(currentIndex)
    var flipped by remember(word?.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "flashcard_flip"
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Flashcards",
                subtitle = if (deck.isNotEmpty()) "${currentIndex + 1} / ${deck.size} · Tap card to flip" else null,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                deck.isEmpty() && dailyState is UiState.Loading ->
                    VocabLoadingSkeleton(modifier = Modifier.widthIn(max = 600.dp))
                deck.isEmpty() && dailyState is UiState.Error ->
                    VocabErrorState(
                        message = dailyState.message,
                        onRetry = onRetry,
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                word == null ->
                    VocabEmptyState(
                        title = "No flashcards ready",
                        subtitle = "Load today's daily words first to practice flashcards.",
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                else -> {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!feedback.message.isNullOrBlank()) {
                            VocabFeedbackBanner(message = feedback.message, isError = feedback.isError)
                        }

                        // Flippable 3D Card
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = pal.card,
                            border = BorderStroke(1.5.dp, if (rotation > 90f) pal.primary else pal.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .graphicsLayer {
                                    rotationY = rotation
                                    cameraDistance = 12f * density
                                }
                                .clip(RoundedCornerShape(26.dp))
                                .clickable { flipped = !flipped }
                                .testTag("vocab_flashcard_surface")
                        ) {
                            if (rotation <= 90f) {
                                // Front Side
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = pal.primaryContainer) {
                                        Text(
                                            text = "${word.level} · ${word.partOfSpeech}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = pal.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(18.dp))
                                    Text(
                                        text = word.word,
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = pal.textPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    if (word.pronunciation.isNotBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = word.pronunciation,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = pal.textSecondary
                                        )
                                    }
                                    Spacer(Modifier.height(22.dp))
                                    OutlinedButton(
                                        onClick = { player.play(word.word, word.audioUrl) },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Listen Pronunciation")
                                    }
                                    Spacer(Modifier.height(20.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.Flip, contentDescription = null, tint = pal.textSecondary, modifier = Modifier.size(16.dp))
                                        Text("Tap card to flip for Bengali meaning", style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                                    }
                                }
                            } else {
                                // Back Side (counter-rotated 180deg so text is upright)
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { rotationY = 180f }
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Text(
                                            text = word.word,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = pal.textPrimary
                                        )
                                        Text(
                                            text = word.meaning,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = pal.primary
                                        )
                                    }
                                    if (word.examples.isNotEmpty()) {
                                        item {
                                            Text("Examples", style = MaterialTheme.typography.labelLarge, color = pal.gold, fontWeight = FontWeight.Bold)
                                        }
                                        word.examples.forEach { ex ->
                                            item {
                                                Surface(shape = RoundedCornerShape(12.dp), color = pal.background, modifier = Modifier.fillMaxWidth()) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(ex.en, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = pal.textPrimary)
                                                        Text(ex.bn, style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (word.synonyms.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "Synonyms: ${word.synonyms.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = pal.textSecondary
                                            )
                                        }
                                    }
                                    if (word.antonyms.isNotEmpty()) {
                                        item {
                                            Text(
                                                text = "Antonyms: ${word.antonyms.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = pal.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val isFlashcardKnown = word.progress.isKnown ||
                            word.progress.status.equals("mastered", ignoreCase = true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    flipped = false
                                    onKnowWord(word.id, false)
                                },
                                enabled = !feedback.busy,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = pal.danger),
                                border = BorderStroke(1.dp, pal.danger.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Text("Don't Know", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    flipped = false
                                    onKnowWord(word.id, true)
                                },
                                enabled = !feedback.busy,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFlashcardKnown) pal.primaryContainer else pal.primary,
                                    contentColor = if (isFlashcardKnown) pal.primary else pal.onPrimary
                                ),
                                border = if (isFlashcardKnown) BorderStroke(1.dp, pal.primary) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    text = if (isFlashcardKnown) "Saved as Known" else "I Know",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(onClick = { onMove(-1) }, enabled = currentIndex > 0) { Text("Previous") }
                            OutlinedButton(onClick = { onMove(1) }, enabled = currentIndex < deck.lastIndex) { Text("Next") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteWordImage(url: String, contentDescription: String) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url).get().build()
                NetworkClient.standard.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val bytes = res.body?.bytes() ?: return@use null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    bitmap?.let { img ->
        Image(
            bitmap = img,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(18.dp))
        )
    }
}
