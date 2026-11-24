package com.example.feature.vocabulary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.common.UiState
import com.example.domain.model.VocabCategorySummary
import com.example.domain.model.VocabProgressAnalytics
import com.example.domain.model.VocabSettings
import com.example.domain.model.VocabWord
import com.example.domain.model.WeeklyActivityPoint

@Composable
fun VocabProgressAnalyticsScreen(
    progressState: UiState<VocabProgressAnalytics>,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    val pal = vocabPalette()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Progress & Analytics",
                subtitle = "Real activity from your learning log",
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
            when (progressState) {
                is UiState.Loading -> VocabLoadingSkeleton(modifier = Modifier.widthIn(max = 600.dp))
                is UiState.Error -> VocabErrorState(
                    message = progressState.message,
                    onRetry = onRetry,
                    modifier = Modifier.widthIn(max = 600.dp)
                )
                is UiState.Success -> {
                    val data = progressState.data
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AnalyticsStatCard("Streak", "${data.streak} days", Modifier.weight(1f))
                                AnalyticsStatCard("Quiz Accuracy", "${data.quizAccuracyPercent}%", Modifier.weight(1f))
                                AnalyticsStatCard("Quizzes", "${data.totalQuizzes}", Modifier.weight(1f))
                            }
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
                                        text = "Last 7 Days Activity",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = pal.textPrimary
                                    )
                                    Text(
                                        text = "Green = New words · Gold = Reviewed words",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = pal.textSecondary
                                    )
                                    WeeklyActivityCanvasChart(points = data.weeklyActivity)
                                }
                            }
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
                                        text = "Mastery Distribution",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = pal.textPrimary
                                    )
                                    WordStatusDistributionCanvas(
                                        learning = data.learningCount,
                                        review = data.reviewCount,
                                        mastered = data.masteredCount
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Learning: ${data.learningCount}", style = MaterialTheme.typography.bodySmall, color = pal.gold)
                                        Text("In Review: ${data.reviewCount}", style = MaterialTheme.typography.bodySmall, color = pal.textPrimary)
                                        Text("Mastered: ${data.masteredCount}", style = MaterialTheme.typography.bodySmall, color = pal.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
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
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Speaking Practice Summary (${data.totalSpeakingSessions} sessions)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = pal.textPrimary
                                    )
                                    Text(
                                        text = "Average Grammar: ${data.avgGrammarScore}% · Vocabulary: ${data.avgVocabularyScore}% · Fluency: ${data.avgFluencyScore}%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = pal.textSecondary
                                    )
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
private fun AnalyticsStatCard(label: String, value: String, modifier: Modifier) {
    val pal = vocabPalette()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = pal.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
        }
    }
}

@Composable
private fun WeeklyActivityCanvasChart(points: List<WeeklyActivityPoint>) {
    val pal = vocabPalette()
    val maxVal = (points.maxOfOrNull { it.newWords + it.reviewedWords } ?: 5).coerceAtLeast(5)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            if (points.isEmpty()) return@Canvas
            val groupWidth = size.width / points.size
            val barWidth = (groupWidth * 0.28f).coerceAtLeast(8f)
            val gap = 4f
            points.forEachIndexed { idx, p ->
                val centerX = idx * groupWidth + groupWidth / 2f
                val newH = (p.newWords.toFloat() / maxVal) * (size.height - 16f)
                val revH = (p.reviewedWords.toFloat() / maxVal) * (size.height - 16f)

                drawRoundRect(
                    color = pal.primary,
                    topLeft = Offset(centerX - barWidth - gap / 2f, size.height - newH),
                    size = Size(barWidth, newH.coerceAtLeast(4f)),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = pal.gold,
                    topLeft = Offset(centerX + gap / 2f, size.height - revH),
                    size = Size(barWidth, revH.coerceAtLeast(4f)),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            points.forEach { p ->
                Text(
                    text = p.date.takeLast(5),
                    style = MaterialTheme.typography.labelSmall,
                    color = pal.textSecondary
                )
            }
        }
    }
}

@Composable
private fun WordStatusDistributionCanvas(learning: Int, review: Int, mastered: Int) {
    val pal = vocabPalette()
    val total = (learning + review + mastered).coerceAtLeast(1).toFloat()
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        var cursor = 0f
        val segments = listOf(
            learning / total to pal.gold,
            review / total to pal.textSecondary.copy(alpha = 0.45f),
            mastered / total to pal.primary
        )
        segments.forEach { (ratio, color) ->
            val segW = ratio * size.width
            if (segW > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cursor, 0f),
                    size = Size(segW, size.height),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                cursor += segW
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabSettingsScreen(
    settingsState: UiState<VocabSettings>,
    categoriesState: UiState<List<VocabCategorySummary>>,
    feedback: VocabActionFeedback,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (VocabSettings) -> Unit
) {
    val pal = vocabPalette()
    val loaded = (settingsState as? UiState.Success)?.data
    var dailyGoal by remember(loaded) { mutableIntStateOf(loaded?.dailyGoal ?: 5) }
    var preferredLevel by remember(loaded) { mutableStateOf(loaded?.preferredLevel ?: "A1") }
    var selectedCats by remember(loaded) { mutableStateOf(loaded?.preferredCategoryIds?.toSet() ?: emptySet()) }
    var autoPronounce by remember(loaded) { mutableStateOf(loaded?.autoPronounce ?: true) }
    var reminderEnabled by remember(loaded) { mutableStateOf(loaded?.reminderEnabled ?: false) }
    var reminderTime by remember(loaded) { mutableStateOf(loaded?.reminderTime ?: "20:00") }
    var timezone by remember(loaded) { mutableStateOf(loaded?.timezone ?: "Asia/Dhaka") }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Vocabulary Settings",
                subtitle = "Customize daily pace & pronunciation",
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
            when (settingsState) {
                is UiState.Loading -> VocabLoadingSkeleton(modifier = Modifier.widthIn(max = 600.dp))
                is UiState.Error -> VocabErrorState(
                    message = settingsState.message,
                    onRetry = onRetry,
                    modifier = Modifier.widthIn(max = 600.dp)
                )
                is UiState.Success -> {
                    val categories = (categoriesState as? UiState.Success)?.data.orEmpty()
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

                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = pal.card,
                                border = BorderStroke(1.dp, pal.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Daily New Words Goal (Max 5)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        (1..5).forEach { goal ->
                                            FilterChip(
                                                selected = dailyGoal == goal,
                                                onClick = { dailyGoal = goal },
                                                label = { Text("$goal / day") }
                                            )
                                        }
                                    }
                                    Text("Preferred CEFR Level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("ALL", "A1", "A2", "B1", "B2", "C1", "C2").forEach { lvl ->
                                            FilterChip(
                                                selected = preferredLevel == lvl,
                                                onClick = { preferredLevel = lvl },
                                                label = { Text(lvl) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (categories.isNotEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = pal.card,
                                    border = BorderStroke(1.dp, pal.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Preferred Categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            categories.forEach { cat ->
                                                val isSel = selectedCats.contains(cat.id)
                                                FilterChip(
                                                    selected = isSel,
                                                    onClick = {
                                                        selectedCats = if (isSel) selectedCats - cat.id else selectedCats + cat.id
                                                    },
                                                    label = { Text(cat.name) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = pal.card,
                                border = BorderStroke(1.dp, pal.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Auto Pronunciation", style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary)
                                        Switch(checked = autoPronounce, onCheckedChange = { autoPronounce = it })
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Daily Reminder", style = MaterialTheme.typography.bodyLarge, color = pal.textPrimary)
                                        Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                                    }
                                    if (reminderEnabled) {
                                        OutlinedTextField(
                                            value = reminderTime,
                                            onValueChange = { reminderTime = it },
                                            label = { Text("Reminder Time (HH:mm)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    OutlinedTextField(
                                        value = timezone,
                                        onValueChange = { timezone = it },
                                        label = { Text("Timezone (e.g. Asia/Dhaka)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    onSave(
                                        VocabSettings(
                                            dailyGoal = dailyGoal,
                                            preferredLevel = preferredLevel,
                                            preferredCategoryIds = selectedCats.toList(),
                                            autoPronounce = autoPronounce,
                                            reminderEnabled = reminderEnabled,
                                            reminderTime = reminderTime.trim().ifBlank { "20:00" },
                                            timezone = timezone.trim().ifBlank { "Asia/Dhaka" }
                                        )
                                    )
                                },
                                enabled = !feedback.busy,
                                colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("vocab_settings_save_button")
                            ) {
                                Text(if (feedback.busy) "Saving..." else "Save Settings", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VocabFavoritesScreen(
    favoritesState: UiState<List<VocabWord>>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenDeck: (List<VocabWord>, Int) -> Unit,
    onToggleBookmark: (String) -> Unit
) {
    val pal = vocabPalette()
    LaunchedEffect(Unit) { onRetry() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Bookmarked Words",
                subtitle = "Saved for quick revision",
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
            when (favoritesState) {
                is UiState.Loading -> VocabLoadingSkeleton(modifier = Modifier.widthIn(max = 600.dp))
                is UiState.Error -> VocabErrorState(
                    message = favoritesState.message,
                    onRetry = onRetry,
                    modifier = Modifier.widthIn(max = 600.dp)
                )
                is UiState.Success -> {
                    val words = favoritesState.data
                    if (words.isEmpty()) {
                        VocabEmptyState(
                            title = "No bookmarks yet",
                            subtitle = "Tap the bookmark icon on any word card to save it here.",
                            icon = Icons.Filled.Bookmark,
                            modifier = Modifier.widthIn(max = 600.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(words.size) { index ->
                                val w = words[index]
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = pal.card,
                                    border = BorderStroke(1.dp, pal.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { onOpenDeck(words, index) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${w.word} (${w.level})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                                            Text(w.meaning, style = MaterialTheme.typography.bodyMedium, color = pal.textSecondary)
                                        }
                                        IconButton(
                                            onClick = {
                                                onToggleBookmark(w.id)
                                                onRetry()
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(Icons.Filled.Bookmark, contentDescription = "Remove bookmark", tint = pal.gold)
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
