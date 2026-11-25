package com.example.feature.vocabulary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.common.UiState
import com.example.domain.model.PaginatedWordsData
import com.example.domain.model.VocabCategorySummary
import com.example.domain.model.VocabDashboardData
import com.example.domain.model.VocabWord

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyDashboardScreen(
    dashboardState: UiState<VocabDashboardData>,
    isAdmin: Boolean,
    feedback: VocabActionFeedback,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onContinueLearning: () -> Unit,
    onStartReview: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onOpenQuiz: () -> Unit,
    onOpenSentenceBuilding: () -> Unit,
    onOpenSpeaking: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenCategoryWords: (String) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    val pal = vocabPalette()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Vocabulary Mastery",
                subtitle = "5 Words Daily · Smart Review",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = onOpenFavorites,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vocab_favorites_icon")
                    ) {
                        Icon(Icons.Filled.Bookmark, contentDescription = "Bookmarked words", tint = pal.gold)
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vocab_settings_icon")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Vocabulary settings", tint = pal.textPrimary)
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
            when (dashboardState) {
                is UiState.Loading -> VocabLoadingSkeleton(lines = 5, modifier = Modifier.widthIn(max = 600.dp))
                is UiState.Error -> VocabErrorState(
                    message = dashboardState.message,
                    onRetry = onRetry,
                    modifier = Modifier.widthIn(max = 600.dp)
                )
                is UiState.Success -> {
                    val data = dashboardState.data
                    val progressFraction by animateFloatAsState(
                        targetValue = (data.newWordsDone.toFloat() / data.dailyGoal.coerceAtLeast(1)).coerceIn(0f, 1f),
                        animationSpec = spring(),
                        label = "daily_progress"
                    )

                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!feedback.message.isNullOrBlank()) {
                            item { VocabFeedbackBanner(message = feedback.message, isError = feedback.isError) }
                        }

                        if (data.isFromOfflineCache) {
                            item {
                                VocabFeedbackBanner(
                                    message = "Showing cached progress. Pull or tap retry when online.",
                                    isError = false
                                )
                            }
                        }

                        // Hero Daily Progress Card
                        item {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Brush.linearGradient(
                                                listOf(BrandGreen, Color(0xFF2D7A4F))
                                            )
                                        )
                                        .padding(20.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Hello, ${data.greetingName}",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Today's Goal: ${data.newWordsDone} / ${data.dailyGoal} words",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White.copy(alpha = 0.9f)
                                                )
                                            }
                                            Surface(
                                                color = BrandGold,
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.LocalFireDepartment,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "${data.streak}d Streak",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        LinearProgressIndicator(
                                            progress = { progressFraction.coerceAtLeast(0.03f) },
                                            color = Color(0xFFFDE68A),
                                            trackColor = Color.White.copy(alpha = 0.25f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(10.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = onContinueLearning,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = BrandGreen
                                                ),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("vocab_continue_learning_button")
                                            ) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (data.isGoalCompleted) "Today's Words" else "Continue Learning",
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = onStartReview,
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("vocab_start_review_button")
                                            ) {
                                                Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Review (${data.dueReviews})", maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Stats Summary Row
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                DashStatCard("Due Today", "${data.dueReviews}", pal.gold, Modifier.weight(1f))
                                DashStatCard("Learned", "${data.totalLearned}", pal.primary, Modifier.weight(1f))
                                DashStatCard("Mastered", "${data.masteredCount}", Color(0xFF0E7490), Modifier.weight(1f))
                            }
                        }

                        // Practice Modes Grid
                        item {
                            Text(
                                text = "Practice & Skills",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = pal.textPrimary
                            )
                            Spacer(Modifier.height(10.dp))
                            FlowRow(
                                maxItemsInEachRow = 2,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                PracticeModeTile(
                                    icon = Icons.Filled.Style,
                                    title = "Flashcards",
                                    subtitle = "Flip & memorize",
                                    accent = pal.primary,
                                    tag = "vocab_flashcards_btn",
                                    modifier = Modifier.fillMaxWidth(0.485f),
                                    onClick = onOpenFlashcards
                                )
                                PracticeModeTile(
                                    icon = Icons.Filled.Quiz,
                                    title = "Quiz Arena",
                                    subtitle = "MCQ, match & listen",
                                    accent = pal.gold,
                                    tag = "vocab_quiz_btn",
                                    modifier = Modifier.fillMaxWidth(0.485f),
                                    onClick = onOpenQuiz
                                )
                                PracticeModeTile(
                                    icon = Icons.Filled.EditNote,
                                    title = "Sentence Build",
                                    subtitle = "Write with words",
                                    accent = pal.primary,
                                    tag = "vocab_sentence_btn",
                                    modifier = Modifier.fillMaxWidth(0.485f),
                                    onClick = onOpenSentenceBuilding
                                )
                                PracticeModeTile(
                                    icon = Icons.Filled.Mic,
                                    title = "Speaking Drill",
                                    subtitle = "Speak & get scored",
                                    accent = pal.gold,
                                    tag = "vocab_speaking_btn",
                                    modifier = Modifier.fillMaxWidth(0.485f),
                                    onClick = onOpenSpeaking
                                )
                                PracticeModeTile(
                                    icon = Icons.Filled.BarChart,
                                    title = "Progress Charts",
                                    subtitle = "Weekly stats & accuracy",
                                    accent = pal.primary,
                                    tag = "vocab_progress_btn",
                                    modifier = Modifier.fillMaxWidth(0.485f),
                                    onClick = onOpenProgress
                                )
                                PracticeModeTile(
                                    icon = Icons.Filled.Category,
                                    title = "All Categories",
                                    subtitle = "Browse topics & words",
                                    accent = pal.gold,
                                    tag = "vocab_categories_btn",
                                    modifier = Modifier.fillMaxWidth(0.485f),
                                    onClick = onOpenCategories
                                )
                            }
                        }

                        // Categories Carousel
                        if (data.categories.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Topics",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = pal.textPrimary
                                    )
                                    Text(
                                        text = "See All",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = pal.primary,
                                        modifier = Modifier
                                            .clickable(onClick = onOpenCategories)
                                            .padding(6.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(data.categories, key = { it.id }) { cat ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = pal.card,
                                            border = BorderStroke(1.dp, pal.border),
                                            modifier = Modifier
                                                .width(156.dp)
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onOpenCategoryWords(cat.id) }
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(pal.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.MenuBook,
                                                        contentDescription = null,
                                                        tint = pal.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Text(
                                                    text = cat.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = pal.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${cat.wordCount} words",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = pal.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Admin Panel (Strictly Admin Only)
                        if (isAdmin) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = pal.goldContainer,
                                    border = BorderStroke(1.dp, pal.gold.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable(onClick = onOpenAdmin)
                                        .testTag("vocab_admin_entry_card")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = pal.gold)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Admin · Vocabulary Manager",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = pal.textPrimary
                                            )
                                            Text(
                                                text = "Add, edit, publish words, categories & CSV bulk import",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = pal.textSecondary
                                            )
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = pal.gold)
                                    }
                                }
                            }
                        }

                        // Recent Activity
                        item {
                            Text(
                                text = "Recent Activity",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = pal.textPrimary
                            )
                        }

                        if (data.recentActivity.isEmpty()) {
                            item {
                                VocabEmptyState(
                                    title = "Start your first word session",
                                    subtitle = "Complete today's 5 words or take a quick quiz to see your activity here.",
                                    icon = Icons.Filled.AutoAwesome
                                )
                            }
                        } else {
                            items(data.recentActivity) { act ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = pal.card,
                                    border = BorderStroke(1.dp, pal.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (act.type == "quiz") pal.goldContainer else pal.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (act.type == "quiz") Icons.Filled.WorkspacePremium else Icons.Filled.Mic,
                                                contentDescription = null,
                                                tint = if (act.type == "quiz") pal.gold else pal.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = act.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = pal.textPrimary
                                            )
                                            Text(
                                                text = act.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = pal.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashStatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val pal = vocabPalette()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = pal.textSecondary
            )
        }
    }
}

@Composable
private fun PracticeModeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val pal = vocabPalette()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = pal.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = pal.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CategoriesScreen(
    categoriesState: UiState<List<VocabCategorySummary>>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectCategory: (String) -> Unit
) {
    val pal = vocabPalette()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Categories",
                subtitle = "Topics & Subtopics",
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
            when (categoriesState) {
                is UiState.Loading -> VocabLoadingSkeleton(modifier = Modifier.widthIn(max = 600.dp))
                is UiState.Error -> VocabErrorState(
                    message = categoriesState.message,
                    onRetry = onRetry,
                    modifier = Modifier.widthIn(max = 600.dp)
                )
                is UiState.Success -> {
                    val cats = categoriesState.data
                    if (cats.isEmpty()) {
                        VocabEmptyState(
                            title = "No categories yet",
                            subtitle = "Ask an admin to seed or add vocabulary categories.",
                            modifier = Modifier.widthIn(max = 600.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = 600.dp)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(cats, key = { it.id }) { parent ->
                                CategoryHierarchyCard(parent = parent, onSelectCategory = onSelectCategory)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHierarchyCard(
    parent: VocabCategorySummary,
    onSelectCategory: (String) -> Unit
) {
    val pal = vocabPalette()
    var expanded by remember { mutableStateOf(parent.subcategories.isNotEmpty()) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable { onSelectCategory(parent.id) }
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pal.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = pal.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = parent.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = pal.textPrimary
                    )
                    Text(
                        text = parent.description.ifBlank { "${parent.totalWordCount} words available" },
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.textSecondary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = pal.primaryContainer
                ) {
                    Text(
                        text = "${parent.totalWordCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = pal.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (parent.subcategories.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = "Toggle subcategories",
                            tint = pal.textSecondary
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded && parent.subcategories.isNotEmpty()) {
                Column(
                    modifier = Modifier.padding(top = 10.dp, start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    parent.subcategories.forEach { sub ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = pal.background,
                            border = BorderStroke(1.dp, pal.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectCategory(sub.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sub.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = pal.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${sub.wordCount} words",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pal.textSecondary
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = pal.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WordBrowserScreen(
    title: String,
    wordsState: UiState<PaginatedWordsData>,
    initialCategoryId: String,
    onBack: () -> Unit,
    onLoadWords: (page: Int, level: String, categoryId: String, search: String) -> Unit,
    onOpenDeck: (List<VocabWord>, Int) -> Unit
) {
    val pal = vocabPalette()
    var search by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("ALL") }
    var page by remember { mutableIntStateOf(1) }

    LaunchedEffect(initialCategoryId) {
        onLoadWords(1, "ALL", initialCategoryId, "")
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = { VocabTopBar(title = title, onBack = onBack) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 600.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it
                        page = 1
                        onLoadWords(1, level, initialCategoryId, it)
                    },
                    placeholder = { Text("Search English word or Bangla meaning...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("ALL", "A1", "A2", "B1", "B2", "C1", "C2")) { lvl ->
                        FilterChip(
                            selected = level == lvl,
                            onClick = {
                                level = lvl
                                page = 1
                                onLoadWords(1, lvl, initialCategoryId, search)
                            },
                            label = { Text(lvl) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                when (wordsState) {
                    is UiState.Loading -> VocabLoadingSkeleton()
                    is UiState.Error -> VocabErrorState(
                        message = wordsState.message,
                        onRetry = { onLoadWords(page, level, initialCategoryId, search) }
                    )
                    is UiState.Success -> {
                        val data = wordsState.data
                        if (data.items.isEmpty()) {
                            VocabEmptyState(
                                title = "No matching words",
                                subtitle = "Try clearing your search filter or picking another CEFR level."
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 20.dp)
                            ) {
                                items(data.items.size) { index ->
                                    val word = data.items[index]
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = pal.card,
                                        border = BorderStroke(1.dp, pal.border),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onOpenDeck(data.items, index) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = word.word,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = pal.textPrimary
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = pal.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = word.level,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = pal.primary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = word.meaning,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = pal.textSecondary
                                                )
                                            }
                                            Icon(
                                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = pal.textSecondary
                                            )
                                        }
                                    }
                                }
                                if (data.totalPages > 1) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val prev = (page - 1).coerceAtLeast(1)
                                                    page = prev
                                                    onLoadWords(prev, level, initialCategoryId, search)
                                                },
                                                enabled = page > 1
                                            ) { Text("Previous") }
                                            Text("Page ${data.page} / ${data.totalPages}", color = pal.textSecondary)
                                            OutlinedButton(
                                                onClick = {
                                                    val next = (page + 1).coerceAtMost(data.totalPages)
                                                    page = next
                                                    onLoadWords(next, level, initialCategoryId, search)
                                                },
                                                enabled = page < data.totalPages
                                            ) { Text("Next") }
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
