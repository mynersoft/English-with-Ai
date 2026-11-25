package com.example.feature.reading.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.UiState
import com.example.domain.model.ReadingAttemptRecord
import com.example.domain.model.ReadingCategory
import com.example.domain.model.ReadingCefrLevel
import com.example.domain.model.ReadingDashboardData
import com.example.domain.model.ReadingPassageSummary
import com.example.domain.model.ReadingWeeklyDay
import com.example.feature.reading.ReadingDashboardFilterState
import com.example.feature.reading.ReadingDashboardSection
import com.example.feature.reading.ReadingDashboardViewModel

@Composable
fun ReadingDashboardScreen(
    viewModel: ReadingDashboardViewModel,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onOpenPassage: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pal = readingPalette()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            ReadingTopBar(
                title = "Reading Practice",
                subtitle = "Listen, read aloud & understand",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.loadAll(silent = false) },
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reading_refresh_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh reading dashboard")
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reading_settings_button")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Reading settings")
                    }
                    if (isAdmin) {
                        IconButton(
                            onClick = onOpenAdmin,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("reading_admin_button")
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = "Reading admin")
                        }
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
            when (val state = dashboardState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = pal.primary)
                    }
                }

                is UiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = pal.card,
                        border = BorderStroke(1.dp, pal.border),
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Could not load Reading Practice", fontWeight = FontWeight.Bold, color = pal.textPrimary)
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                            Button(
                                onClick = { viewModel.loadAll(silent = false) },
                                colors = ButtonDefaults.buttonColors(containerColor = pal.primary)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    ReadingDashboardBody(
                        data = state.data,
                        filterState = filterState,
                        onSelectSection = viewModel::selectSection,
                        onSelectLevel = viewModel::selectLevel,
                        onSelectCategory = viewModel::selectCategory,
                        onSearchChange = viewModel::updateSearchQuery,
                        onClearFilters = viewModel::clearFilters,
                        onLoadMore = viewModel::loadMorePassages,
                        onOpenPassage = onOpenPassage
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingDashboardBody(
    data: ReadingDashboardData,
    filterState: ReadingDashboardFilterState,
    onSelectSection: (ReadingDashboardSection) -> Unit,
    onSelectLevel: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPassage: (String) -> Unit
) {
    val pal = readingPalette()
    val stats = data.stats

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Daily goal + key stats hero
        item {
            val goalRatio = if (stats.dailyGoalMinutes > 0) {
                (stats.todayMinutes.toFloat() / stats.dailyGoalMinutes.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .background(Brush.linearGradient(listOf(Color(0xFF14643E), Color(0xFF1E8352))))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Today's Reading Goal",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "${stats.todayMinutes} / ${stats.dailyGoalMinutes} min",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.16f)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.LocalFireDepartment, null, tint = Color(0xFFFDE68A), modifier = Modifier.size(16.dp))
                                Text("${stats.streakDays}d streak", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { goalRatio },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MiniStatChip("Completed", "${stats.completedPassagesCount}/${stats.totalPublishedPassages}", Modifier.weight(1f))
                        MiniStatChip("Total Time", "${stats.totalReadingMinutes} min", Modifier.weight(1f))
                        MiniStatChip("Best Streak", "${stats.longestStreak} d", Modifier.weight(1f))
                    }
                }
            }
        }

        // Continue Reading card
        data.continueReading?.let { resume ->
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = pal.primaryContainer,
                    border = BorderStroke(1.dp, pal.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onOpenPassage(resume.id) }
                        .testTag("continue_reading_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(pal.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = pal.onPrimary)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Continue Reading", style = MaterialTheme.typography.labelSmall, color = pal.primary, fontWeight = FontWeight.Bold)
                            Text(resume.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${resume.level} · ${resume.progressPercent}% complete · sentence ${resume.lastSentenceIndex + 1}", style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = pal.primary)
                    }
                }
            }
        }

        // Weekly Activity Strip
        if (data.weeklyActivity.isNotEmpty()) {
            item {
                WeeklyReadingActivityCard(
                    weekLabel = data.weekRangeLabel,
                    days = data.weeklyActivity
                )
            }
        }

        // Section selector chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingDashboardSection.entries.forEach { sec ->
                    FilterChip(
                        selected = filterState.selectedSection == sec,
                        onClick = { onSelectSection(sec) },
                        label = { Text(sec.label) },
                        modifier = Modifier.testTag("reading_section_${sec.name.lowercase()}")
                    )
                }
            }
        }

        when (filterState.selectedSection) {
            ReadingDashboardSection.DISCOVER -> {
                item {
                    PassageFiltersPanel(
                        filterState = filterState,
                        categories = data.categories,
                        onSearchChange = onSearchChange,
                        onSelectLevel = onSelectLevel,
                        onSelectCategory = onSelectCategory,
                        onClearFilters = onClearFilters
                    )
                }

                if (filterState.isLoadingPassages && filterState.passages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = pal.primary)
                        }
                    }
                } else if (!filterState.passagesError.isNullOrBlank() && filterState.passages.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = pal.missingBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(filterState.passagesError, color = pal.missing, modifier = Modifier.padding(14.dp))
                        }
                    }
                } else if (filterState.passages.isEmpty()) {
                    item {
                        EmptyReadingCard(
                            title = "No matching passages",
                            subtitle = "Try another CEFR level, category, or ask an admin to publish reading passages."
                        )
                    }
                } else {
                    items(filterState.passages, key = { it.id }) { passage ->
                        PassageSummaryCard(passage = passage, onClick = { onOpenPassage(passage.id) })
                    }
                    if (filterState.page < filterState.totalPages) {
                        item {
                            OutlinedButton(
                                onClick = onLoadMore,
                                enabled = !filterState.isLoadingMore,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("reading_load_more_button")
                            ) {
                                if (filterState.isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("Load More (${filterState.passages.size} of ${filterState.totalPassages})")
                            }
                        }
                    }
                }
            }

            ReadingDashboardSection.RECOMMENDED -> {
                if (data.recommended.isEmpty()) {
                    item {
                        EmptyReadingCard("No recommendations yet", "All available passages have been read or none match your level yet.")
                    }
                } else {
                    items(data.recommended, key = { it.id }) { passage ->
                        PassageSummaryCard(passage = passage, badge = "Recommended", onClick = { onOpenPassage(passage.id) })
                    }
                }
            }

            ReadingDashboardSection.RECENT -> {
                if (data.recent.isEmpty()) {
                    item {
                        EmptyReadingCard("No recent passages", "Open a passage in All Passages to start reading.")
                    }
                } else {
                    items(data.recent, key = { it.id }) { passage ->
                        PassageSummaryCard(passage = passage, onClick = { onOpenPassage(passage.id) })
                    }
                }
            }

            ReadingDashboardSection.HISTORY -> {
                if (data.history.isEmpty()) {
                    item {
                        EmptyReadingCard("No attempts recorded yet", "Complete Read Aloud or Read & Understand quizzes inside any passage.")
                    }
                } else {
                    items(data.history, key = { it.id }) { attempt ->
                        AttemptHistoryRow(attempt = attempt, onOpenPassage = onOpenPassage)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.14f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun WeeklyReadingActivityCard(weekLabel: String, days: List<ReadingWeeklyDay>) {
    val pal = readingPalette()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Weekly Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                Text(weekLabel, style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { d ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("${d.minutes}m", style = MaterialTheme.typography.labelSmall, color = if (d.isActive) pal.primary else pal.textSecondary)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        d.isActive -> pal.primary
                                        d.isToday -> pal.primaryContainer
                                        else -> pal.border.copy(alpha = 0.55f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = d.dayNumber,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (d.isActive) pal.onPrimary else pal.textPrimary
                            )
                        }
                        Text(d.dayShort, style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PassageFiltersPanel(
    filterState: ReadingDashboardFilterState,
    categories: List<ReadingCategory>,
    onSearchChange: (String) -> Unit,
    onSelectLevel: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onClearFilters: () -> Unit
) {
    val pal = readingPalette()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = filterState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search reading passages...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filterState.searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reading_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ReadingCefrLevel.entries.forEach { lvl ->
                FilterChip(
                    selected = filterState.selectedLevel == lvl.code,
                    onClick = { onSelectLevel(lvl.code) },
                    label = { Text(lvl.code) },
                    modifier = Modifier.testTag("reading_level_${lvl.code}")
                )
            }
        }

        if (categories.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filterState.selectedCategoryId.isBlank(),
                    onClick = { onSelectCategory("") },
                    label = { Text("All Categories") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = filterState.selectedCategoryId == cat.id,
                        onClick = { onSelectCategory(cat.id) },
                        label = { Text("${cat.name} (${cat.passageCount})") }
                    )
                }
            }
        }

        if (filterState.selectedLevel != "ALL" || filterState.selectedCategoryId.isNotBlank() || filterState.searchQuery.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClearFilters) {
                    Text("Reset Filters", color = pal.primary)
                }
            }
        }
    }
}

@Composable
private fun PassageSummaryCard(
    passage: ReadingPassageSummary,
    badge: String? = null,
    onClick: () -> Unit
) {
    val pal = readingPalette()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("reading_passage_card_${passage.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CefrBadge(level = passage.level)
                    if (passage.categoryName.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(8.dp), color = pal.background) {
                            Text(
                                text = passage.categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = pal.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (!badge.isNullOrBlank()) {
                        Surface(shape = RoundedCornerShape(8.dp), color = pal.amberContainer) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, null, tint = pal.amber, modifier = Modifier.size(11.dp))
                                Text(badge, style = MaterialTheme.typography.labelSmall, color = pal.amber)
                            }
                        }
                    }
                }

                when (passage.status) {
                    "completed" -> Icon(Icons.Filled.CheckCircle, contentDescription = "Completed", tint = pal.matched, modifier = Modifier.size(20.dp))
                    "in_progress" -> Text("${passage.progressPercent}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = pal.primary)
                }
            }

            Text(passage.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.textPrimary)
            if (passage.summary.isNotBlank()) {
                Text(passage.summary, style = MaterialTheme.typography.bodySmall, color = pal.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = pal.textSecondary, modifier = Modifier.size(14.dp))
                    Text("${passage.estimatedMinutes} min", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MenuBook, null, tint = pal.textSecondary, modifier = Modifier.size(14.dp))
                    Text("${passage.sentenceCount} sentences · ${passage.questionCount} quiz", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun AttemptHistoryRow(
    attempt: ReadingAttemptRecord,
    onOpenPassage: (String) -> Unit
) {
    val pal = readingPalette()
    val modeLabel = if (attempt.mode == "read_aloud") "Read Aloud · Text Matching" else "Read & Understand"
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = attempt.passageId.isNotBlank()) { onOpenPassage(attempt.passageId) }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(attempt.passageTitle.ifBlank { "Reading Passage" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = pal.textPrimary)
                Text("$modeLabel · ${attempt.level}", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
            }
            Surface(shape = RoundedCornerShape(10.dp), color = pal.primaryContainer) {
                Text(
                    text = "${attempt.scorePercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = pal.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyReadingCard(title: String, subtitle: String) {
    val pal = readingPalette()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.MenuBook, contentDescription = null, tint = pal.primary, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
        }
    }
}
