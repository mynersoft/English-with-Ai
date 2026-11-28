package com.englishai.feature.speaking

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.ui.CelebrationOverlay
import com.example.data.repository.PatternDetail
import com.example.data.repository.PatternExample
import com.example.data.repository.PatternSummary
import com.example.feature.pattern.PatternDetailUiState
import com.example.feature.pattern.PatternListUiState
import com.example.feature.pattern.PatternViewModel
import com.example.feature.speaking.SpeechHelper
import com.example.ui.theme.LocalIsDarkTheme

@Composable
private fun patternBrandBg(): Color =
    if (LocalIsDarkTheme.current) Color(0xFF0E7A4D) else Color(0xFF087F50)

@Composable
private fun patternAccent(): Color =
    if (LocalIsDarkTheme.current) Color(0xFF34D399) else Color(0xFF087F50)

@Composable
private fun patternAppBg(): Color =
    if (LocalIsDarkTheme.current) Color(0xFF0B1310) else Color(0xFFF7F9F7)

@Composable
private fun patternCardBg(): Color =
    if (LocalIsDarkTheme.current) Color(0xFF14201B) else Color.White

@Composable
private fun patternDarkText(): Color =
    if (LocalIsDarkTheme.current) Color(0xFFECFDF5) else Color(0xFF202820)

@Composable
private fun patternGrayText(): Color =
    if (LocalIsDarkTheme.current) Color(0xFFA7B8B0) else Color(0xFF777777)

private enum class LearningPage {
    LIST, EXPLANATION, PRACTICE
}

@Composable
fun SentenceLearningScreen(
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    onFullscreenModeChanged: (Boolean) -> Unit = {},
    viewModel: PatternViewModel = viewModel()
) {
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val celebration by viewModel.celebrationEvent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by remember { mutableStateOf(LearningPage.LIST) }

    LaunchedEffect(page) {
        onFullscreenModeChanged(page != LearningPage.LIST)
    }

    DisposableEffect(Unit) {
        onDispose { onFullscreenModeChanged(false) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (page) {
            LearningPage.LIST -> {
                PatternListScreen(
                    state = listState,
                    onBack = onBack,
                    bottomBar = bottomBar,
                    onRefresh = { viewModel.loadPatterns() },
                    onSelectCategory = { viewModel.selectCategory(it) },
                    onPatternClick = { patternId ->
                        viewModel.selectPattern(patternId)
                        page = LearningPage.EXPLANATION
                    }
                )
            }

            LearningPage.EXPLANATION -> {
                BackHandler {
                    viewModel.clearSelectedPattern()
                    page = LearningPage.LIST
                }
                PatternExplanationScreen(
                    state = detailState,
                    onBack = {
                        viewModel.clearSelectedPattern()
                        page = LearningPage.LIST
                    },
                    onRetry = {
                        detailState.selectedPatternId?.let { viewModel.selectPattern(it) }
                    },
                    onPractice = { page = LearningPage.PRACTICE }
                )
            }

            LearningPage.PRACTICE -> {
                BackHandler { page = LearningPage.EXPLANATION }
                val loadedPattern = detailState.pattern
                if (loadedPattern != null && loadedPattern.examples.isNotEmpty()) {
                    SpeakingPracticeScreen(
                        pattern = loadedPattern,
                        onBack = { page = LearningPage.EXPLANATION },
                        onPatternCompleted = {
                            viewModel.onPatternPracticeCompleted(context, loadedPattern)
                        }
                    )
                } else {
                    page = LearningPage.EXPLANATION
                }
            }
        }

        CelebrationOverlay(
            event = celebration,
            onDismiss = { viewModel.dismissCelebration() }
        )
    }
}

// ------------------------------------
// SCREEN 1: PATTERN LIST
// ------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatternListScreen(
    state: PatternListUiState,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    onRefresh: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onPatternClick: (String) -> Unit
) {
    val categories = remember(state.patterns) {
        listOf("All") + state.patterns
            .map { it.categoryName.trim() }
            .filter { it.isNotBlank() && !it.equals("All", ignoreCase = true) }
            .distinct()
    }
    val filteredPatterns = remember(state.patterns, state.selectedCategory) {
        if (state.selectedCategory.equals("All", ignoreCase = true)) {
            state.patterns
        } else {
            state.patterns.filter {
                it.categoryName.trim().equals(state.selectedCategory, ignoreCase = true)
            }
        }
    }
    val appBackground = patternAppBg()
    val brandBg = patternBrandBg()
    val accentGreen = patternAccent()
    val darkText = patternDarkText()
    val grayText = patternGrayText()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = appBackground,
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Spoken English",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sentence_learning_back_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("refresh_patterns_button")
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh patterns"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brandBg)
                    .padding(16.dp)
            ) {
                Text(
                    "Spoken English Practice",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "প্রতিদিন অনুশীলন করুন",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }

            Text(
                "Sentence Patterns",
                modifier = Modifier.padding(
                    start = 20.dp,
                    top = 20.dp,
                    bottom = 4.dp
                ),
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = darkText
            )

            Text(
                "বাক্য গঠনের প্যাটার্ন (${filteredPatterns.size})",
                modifier = Modifier.padding(horizontal = 20.dp),
                color = grayText,
                fontSize = 14.sp
            )

            when {
                state.isLoading && state.patterns.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = accentGreen)
                            Text("Loading patterns from database...", color = grayText)
                        }
                    }
                }

                state.errorMessage != null && state.patterns.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = state.errorMessage,
                                color = darkText,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = brandBg,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("retry_patterns_button")
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                state.patterns.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = grayText,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "No patterns found in the database yet.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = darkText,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Add patterns from the Admin screen, then tap Refresh.",
                                color = grayText,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("empty_refresh_patterns_button")
                            ) {
                                Text("Refresh")
                            }
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.isLoading) {
                            LinearProgressIndicator(
                                color = accentGreen,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                        if (state.errorMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = state.errorMessage,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(onClick = onRefresh) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories, key = { it }) { category ->
                                val isSelected = category.equals(state.selectedCategory, ignoreCase = true)
                                val count = if (category.equals("All", ignoreCase = true)) {
                                    state.patterns.size
                                } else {
                                    state.patterns.count {
                                        it.categoryName.trim().equals(category, ignoreCase = true)
                                    }
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectCategory(category) },
                                    label = { Text("$category ($count)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentGreen.copy(alpha = 0.16f),
                                        selectedLabelColor = accentGreen
                                    ),
                                    modifier = Modifier.testTag("pattern_category_chip_$category")
                                )
                            }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 6.dp,
                                bottom = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(filteredPatterns, key = { it.id }) { pattern ->
                                PatternCard(
                                    pattern = pattern,
                                    onClick = { onPatternClick(pattern.id) }
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
private fun PatternCard(
    pattern: PatternSummary,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val accentGreen = patternAccent()
    val cardBg = patternCardBg()
    val darkText = patternDarkText()
    val grayText = patternGrayText()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag("pattern_card_${pattern.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDark) 0.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pattern.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = darkText
                )

                if (!pattern.categoryName.equals("All", ignoreCase = true)) {
                    Surface(
                        color = accentGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            pattern.categoryName,
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 5.dp
                            ),
                            color = accentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (pattern.isPremium) {
                    Surface(
                        color = if (isDark) Color(0xFF3D2A0C) else Color(0xFFFFF2DE),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            "Premium",
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 5.dp
                            ),
                            color = if (isDark) Color(0xFFFBBF24) else Color(0xFFC17B20),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                pattern.meaning,
                color = grayText,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = grayText,
                    modifier = Modifier.size(17.dp)
                )

                Text(
                    "  ${pattern.sentenceCount}টি Example",
                    color = grayText,
                    fontSize = 12.sp
                )

                if (pattern.audioTotal > 0) {
                    Spacer(Modifier.width(18.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = accentGreen,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        "  Audio ${pattern.audioReady}/${pattern.audioTotal}",
                        color = grayText,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ------------------------------------
// SCREEN 2: PATTERN EXPLANATION
// ------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatternExplanationScreen(
    state: PatternDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPractice: () -> Unit
) {
    val context = LocalContext.current
    val audioPlayer = rememberPatternAudioPlayer(context)
    val pattern = state.pattern
    val isDark = LocalIsDarkTheme.current
    val appBackground = patternAppBg()
    val brandBg = patternBrandBg()
    val accentGreen = patternAccent()
    val cardBg = patternCardBg()
    val darkText = patternDarkText()
    val grayText = patternGrayText()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = appBackground,
        topBar = {
            TopAppBar(
                title = { Text("Pattern Explanation") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pattern_explanation_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBackground
                )
            )
        },
        bottomBar = {
            if (pattern != null) {
                Surface(
                    color = if (isDark) Color(0xFF111B17) else Color.White,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = {
                            audioPlayer.stopAll()
                            onPractice()
                        },
                        enabled = pattern.examples.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .height(54.dp)
                            .testTag("start_speaking_practice_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandBg,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            if (pattern.examples.isNotEmpty()) {
                                "Speaking Practice শুরু করুন"
                            } else {
                                "No Example Sentences Available"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (pattern.examples.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentGreen)
                }
            }

            state.errorMessage != null || pattern == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = state.errorMessage ?: "Pattern not found.",
                            textAlign = TextAlign.Center,
                            color = darkText
                        )
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandBg,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = accentGreen,
                                    modifier = Modifier.size(48.dp)
                                )

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        pattern.title,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = darkText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { audioPlayer.play(pattern.titleAudioUrl, pattern.title) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Listen to pattern title",
                                            tint = accentGreen
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        pattern.meaning,
                                        fontSize = 19.sp,
                                        color = grayText,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (!pattern.meaningAudioUrl.isNullOrBlank()) {
                                        IconButton(
                                            onClick = { audioPlayer.play(pattern.meaningAudioUrl, null) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = "Listen to Bangla meaning",
                                                tint = accentGreen
                                            )
                                        }
                                    }
                                }

                                if (pattern.explanation.isNotBlank()) {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        pattern.explanation,
                                        fontSize = 16.sp,
                                        lineHeight = 27.sp,
                                        color = darkText,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Example Sentences (${pattern.examples.size})",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = darkText
                        )
                    }

                    if (pattern.examples.isEmpty()) {
                        item {
                            Text(
                                "No example sentences have been added for this pattern yet.",
                                color = grayText
                            )
                        }
                    } else {
                        itemsIndexed(pattern.examples, key = { idx, ex -> "${ex.id}_$idx" }) { index, example ->
                            ExampleSentenceCard(
                                index = index,
                                example = example,
                                onPlayEn = { audioPlayer.play(example.enAudioUrl, example.en) },
                                onPlayBn = example.bnAudioUrl?.let { url ->
                                    { audioPlayer.play(url, null) }
                                }
                            )
                        }
                    }

                    item {
                        Text(
                            "মনে রাখুন: এই Pattern দিয়ে নিজের জীবনের বাক্য তৈরি করে অনুশীলন করুন।",
                            color = accentGreen,
                            fontSize = 14.sp,
                            lineHeight = 23.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExampleSentenceCard(
    index: Int,
    example: PatternExample,
    onPlayEn: () -> Unit,
    onPlayBn: (() -> Unit)?
) {
    val accentGreen = patternAccent()
    val cardBg = patternCardBg()
    val darkText = patternDarkText()
    val grayText = patternGrayText()

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Example ${index + 1}",
                color = accentGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    example.en,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = darkText,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onPlayEn,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Listen to English sentence",
                        tint = accentGreen
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    example.bn,
                    fontSize = 15.sp,
                    color = grayText,
                    modifier = Modifier.weight(1f)
                )
                if (onPlayBn != null) {
                    IconButton(
                        onClick = onPlayBn,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Listen to Bangla translation",
                            tint = grayText
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------
// SCREEN 3: SPEAKING PRACTICE
// ------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeakingPracticeScreen(
    pattern: PatternDetail,
    onBack: () -> Unit,
    onPatternCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val audioPlayer = rememberPatternAudioPlayer(context)

    var currentIndex by remember { mutableIntStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    val examples = pattern.examples
    val safeIndex = currentIndex.coerceIn(0, (examples.size - 1).coerceAtLeast(0))
    val currentExample = examples[safeIndex]

    val isDark = LocalIsDarkTheme.current
    val appBackground = patternAppBg()
    val brandBg = patternBrandBg()
    val accentGreen = patternAccent()
    val darkText = patternDarkText()
    val grayText = patternGrayText()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = appBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        pattern.title,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            audioPlayer.stopAll()
                            onBack()
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            audioPlayer.stopAll()
                            showAnswer = false
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            LinearProgressIndicator(
                progress = { (safeIndex + 1).toFloat() / examples.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = accentGreen,
                trackColor = if (isDark) Color(0xFF273931) else Color.LightGray
            )

            Text(
                "${safeIndex + 1} of ${examples.size}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
                textAlign = TextAlign.Center,
                color = grayText
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF192620) else Color(0xFFE8E8E8)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = brandBg,
                        shape = RoundedCornerShape(30.dp)
                    ) {
                        Text(
                            "এখন ইংরেজিতে বলুন...",
                            modifier = Modifier.padding(
                                horizontal = 20.dp,
                                vertical = 10.dp
                            ),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            currentExample.bn,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 36.sp,
                            color = darkText,
                            textAlign = TextAlign.Center
                        )

                        if (showAnswer) {
                            Text(
                                currentExample.en,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentGreen,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedIconButton(
                                onClick = {
                                    audioPlayer.play(currentExample.enAudioUrl, currentExample.en)
                                },
                                modifier = Modifier
                                    .size(58.dp)
                                    .testTag("practice_listen_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Listen",
                                    tint = accentGreen
                                )
                            }

                            OutlinedIconButton(
                                onClick = { showAnswer = !showAnswer },
                                modifier = Modifier
                                    .size(58.dp)
                                    .testTag("practice_toggle_answer_button")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Show answer",
                                    tint = accentGreen
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "শুনুন  •  উত্তর দেখুন",
                            color = grayText,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = {
                    audioPlayer.stopAll()
                    if (safeIndex == examples.lastIndex) {
                        onPatternCompleted()
                        currentIndex = 0
                    } else {
                        currentIndex = safeIndex + 1
                    }
                    showAnswer = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("practice_next_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandBg,
                    contentColor = Color.White
                )
            ) {
                Text(
                    if (safeIndex == examples.lastIndex) "আবার অনুশীলন করুন" else "পরবর্তী বাক্য",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private class PatternAudioController(val speech: SpeechHelper) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(audioUrl: String?, fallbackEnglishText: String?) {
        stopAudioOnly()
        if (!audioUrl.isNullOrBlank()) {
            val mp = MediaPlayer()
            mediaPlayer = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                mp.setDataSource(audioUrl)
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener { stopAudioOnly() }
                mp.setOnErrorListener { _, _, _ ->
                    stopAudioOnly()
                    if (!fallbackEnglishText.isNullOrBlank()) {
                        speech.speak(fallbackEnglishText, null)
                    }
                    true
                }
                mp.prepareAsync()
                return
            } catch (_: Exception) {
                stopAudioOnly()
            }
        }
        if (!fallbackEnglishText.isNullOrBlank()) {
            speech.speak(fallbackEnglishText, null)
        }
    }

    fun stopAudioOnly() {
        mediaPlayer?.release()
        mediaPlayer = null
        speech.stopSpeaking()
    }

    fun stopAll() {
        stopAudioOnly()
        speech.cancelListening()
    }

    fun release() {
        stopAll()
        speech.release()
    }
}

@Composable
private fun rememberPatternAudioPlayer(context: android.content.Context): PatternAudioController {
    val controller = remember(context) { PatternAudioController(SpeechHelper(context)) }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}
