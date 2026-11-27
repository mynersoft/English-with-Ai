package com.example.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.common.UiState
import com.example.core.ui.BannerTone
import com.example.core.ui.OfflineStatusCard
import com.example.core.ui.StatusBanner
import com.example.domain.model.UserProfile
import com.example.domain.model.UserSession
import java.util.Calendar

private val HomeEmerald = Color(0xFF0E6B3B)
private val HomeEmeraldBright = Color(0xFF15803D)
private val HomeSurfaceBg = Color(0xFFF8FAF9)
private val HomeTextDark = Color(0xFF111827)
private val HomeTextMuted = Color(0xFF6B7280)
private val HomeFlameOrange = Color(0xFFF97316)

@Composable
fun DashboardScreen(
    profileState: UiState<UserProfile?>,
    sessionsState: UiState<List<UserSession>>,
    fallbackEmail: String,
    isEmailVerified: Boolean,
    actionState: DashboardActionState,
    bottomBar: @Composable () -> Unit = {},
    isOnline: Boolean = true,
    showMenuButton: Boolean = true,
    onOpenMenu: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSpeaking: () -> Unit = {},
    onOpenSentenceLearning: () -> Unit = {},
    onOpenVocabulary: () -> Unit = {},
    onOpenReading: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
    onRetryBackendSync: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    val streakDays = (profile?.streakDays ?: 1L).toInt().coerceAtLeast(1)
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Learner"
    val firstName = displayName.trim().substringBefore(" ").ifBlank { displayName }
    val level = profile?.safeLevel?.displayName ?: "Beginner"
    val currentDay = (profile?.currentDay ?: 1L).toInt().coerceIn(1, 60)
    val dailyGoalMin = (profile?.dailyGoalMinutes ?: 20L).toInt()
    val activeSessionsCount = (sessionsState as? UiState.Success)?.data
        ?.count { it.activeSession } ?: 1

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = HomeSurfaceBg,
        bottomBar = bottomBar,
        topBar = {
            HomeTopHeader(
                streakDays = streakDays,
                displayName = displayName,
                showMenuButton = showMenuButton,
                onOpenMenu = onOpenMenu,
                onStreakClick = onOpenProgress,
                onAvatarClick = onOpenProgress
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (profileState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = HomeEmerald)
                    }
                }

                is UiState.Error -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!isOnline) {
                            item {
                                OfflineStatusCard(
                                    isReconnecting = actionState.isSyncingBackend,
                                    onRetry = onRetryBackendSync
                                )
                            }
                        } else {
                            item {
                                StatusBanner(message = profileState.message, tone = BannerTone.ERROR)
                            }
                            if (!actionState.backendSyncError.isNullOrBlank()) {
                                item {
                                    BackendSyncWarningCard(
                                        error = actionState.backendSyncError,
                                        isSyncing = actionState.isSyncingBackend,
                                        onRetry = onRetryBackendSync
                                    )
                                }
                            }
                        }
                        item {
                            AllHomeFeaturesSection(
                                level = level,
                                currentDay = currentDay,
                                onOpenSentenceLearning = onOpenSentenceLearning,
                                onOpenVocabulary = onOpenVocabulary,
                                onOpenReading = onOpenReading,
                                onOpenSpeaking = onOpenSpeaking,
                                onOpenChat = onOpenChat,
                                onOpenProgress = onOpenProgress
                            )
                        }
                        item {
                            AccountControlsSection(
                                activeSessionsCount = activeSessionsCount,
                                isAdmin = actionState.isAdmin,
                                isBusy = actionState.isBusy,
                                onOpenProfile = onOpenProfile,
                                onOpenSessions = onOpenSessions,
                                onOpenSecurity = onOpenSecurity,
                                onOpenAdmin = onOpenAdmin,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!actionState.bannerMessage.isNullOrBlank()) {
                            item {
                                StatusBanner(
                                    message = actionState.bannerMessage,
                                    tone = actionState.bannerTone
                                )
                            }
                        }

                        if (!isOnline) {
                            item {
                                OfflineStatusCard(
                                    isReconnecting = actionState.isSyncingBackend,
                                    onRetry = onRetryBackendSync
                                )
                            }
                        } else if (!actionState.backendSyncError.isNullOrBlank()) {
                            item {
                                BackendSyncWarningCard(
                                    error = actionState.backendSyncError,
                                    isSyncing = actionState.isSyncingBackend,
                                    onRetry = onRetryBackendSync
                                )
                            }
                        }

                        // Greeting Headline matching reference image 3
                        item {
                            HomeGreetingSection(
                                firstName = firstName,
                                currentDay = currentDay,
                                level = level,
                                dailyGoalMin = dailyGoalMin,
                                onOpenProgress = onOpenProgress
                            )
                        }

                        // All Learning Features displayed prominently on Home Page
                        item {
                            AllHomeFeaturesSection(
                                level = level,
                                currentDay = currentDay,
                                onOpenSentenceLearning = onOpenSentenceLearning,
                                onOpenVocabulary = onOpenVocabulary,
                                onOpenReading = onOpenReading,
                                onOpenSpeaking = onOpenSpeaking,
                                onOpenChat = onOpenChat,
                                onOpenProgress = onOpenProgress
                            )
                        }

                        // Account, Security & Admin Dashboard (Admin shown strictly to admins)
                        item {
                            AccountControlsSection(
                                activeSessionsCount = activeSessionsCount,
                                isAdmin = actionState.isAdmin,
                                isBusy = actionState.isBusy,
                                onOpenProfile = onOpenProfile,
                                onOpenSessions = onOpenSessions,
                                onOpenSecurity = onOpenSecurity,
                                onOpenAdmin = onOpenAdmin,
                                onSignOut = onSignOut
                            )
                        }

                        item { Spacer(modifier = Modifier.height(10.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopHeader(
    streakDays: Int,
    displayName: String,
    showMenuButton: Boolean,
    onOpenMenu: () -> Unit,
    onStreakClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showMenuButton) {
                    IconButton(
                        onClick = onOpenMenu,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("student_sidebar_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open student menu",
                            tint = HomeTextDark
                        )
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = HomeEmerald, fontWeight = FontWeight.ExtraBold)) {
                            append("English")
                        }
                        withStyle(SpanStyle(color = HomeTextDark, fontWeight = FontWeight.ExtraBold)) {
                            append(" AI")
                        }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = (-0.3).sp,
                    modifier = if (!showMenuButton) Modifier.padding(start = 4.dp) else Modifier
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Streak pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onStreakClick)
                        .minimumInteractiveComponentSize()
                        .testTag("home_streak_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = "Streak days",
                            tint = HomeFlameOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$streakDays",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = HomeTextDark
                        )
                    }
                }

                // Right: Student avatar -> Student Profile / Progress Dashboard
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick)
                        .testTag("home_student_avatar_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_student_avatar),
                        contentDescription = "$displayName student dashboard",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFFD1FAE5), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A))
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeGreetingSection(
    firstName: String,
    currentDay: Int,
    level: String,
    dailyGoalMin: Int,
    onOpenProgress: () -> Unit
) {
    val greetingPrefix = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$greetingPrefix, $firstName 👋",
                style = MaterialTheme.typography.bodyMedium,
                color = HomeTextMuted,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = HomeTextDark, fontWeight = FontWeight.Bold)) {
                        append("What will you ")
                    }
                    withStyle(SpanStyle(color = HomeEmerald, fontWeight = FontWeight.ExtraBold)) {
                        append("learn today?")
                    }
                },
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFE6F4EC),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenProgress)
        ) {
            Text(
                text = "$level · Day $currentDay/60\n${dailyGoalMin}m goal",
                style = MaterialTheme.typography.labelSmall,
                color = HomeEmerald,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun AllHomeFeaturesSection(
    level: String,
    currentDay: Int,
    onOpenSentenceLearning: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenReading: () -> Unit,
    onOpenSpeaking: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenProgress: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Spoken English & Sentence Patterns (Purple-Indigo Hero Card matching Image 3)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onOpenSentenceLearning)
                .testTag("nav_patterns_card")
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF312E81), Color(0xFF5B21B6))
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Spoken English",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HeroPillBadge(text = level, bg = Color.White, fg = Color(0xFF4338CA))
                            HeroPillBadge(
                                text = "Day $currentDay · Patterns",
                                bg = Color.White.copy(alpha = 0.2f),
                                fg = Color.White
                            )
                        }
                        Text(
                            text = "Real-life sentence patterns with Bangla meaning & speaking drills",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Button(
                            onClick = onOpenSentenceLearning,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF312E81)
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("quick_nav_patterns_button")
                        ) {
                            Text("Start Learning", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Circular visual accent on right (matching Image 3)
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(3.dp, Color.White.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }

        // 2. Vocabulary Mastery (Emerald Green Hero Card matching Image 3)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Transparent,
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onOpenVocabulary)
                .testTag("nav_vocabulary_card")
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF064E3B), Color(0xFF15803D))
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Vocabulary",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HeroPillBadge(text = level, bg = Color.White, fg = HomeEmerald)
                            HeroPillBadge(
                                text = "5 Words Daily",
                                bg = Color.White.copy(alpha = 0.2f),
                                fg = Color.White
                            )
                        }
                        Text(
                            text = "Flashcards, Bangla quizzes, sentence building & SRS review",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Button(
                            onClick = onOpenVocabulary,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = HomeEmerald
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("quick_nav_vocabulary_button")
                        ) {
                            Text("Start Learning", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Stacked mini flashcard graphic matching Image 3
                    Box(
                        modifier = Modifier.size(width = 92.dp, height = 104.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.28f),
                            modifier = Modifier
                                .size(width = 76.dp, height = 92.dp)
                                .offset(x = (-8).dp, y = 4.dp)
                                .rotate(-7f)
                        ) {}
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(width = 82.dp, height = 96.dp)
                                .offset(x = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFD1FAE5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Style,
                                        contentDescription = null,
                                        tint = HomeEmerald,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "fluent",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = HomeTextDark,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "সাবলীল",
                                            fontSize = 8.sp,
                                            color = HomeTextMuted,
                                            maxLines = 1
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(HomeEmeraldBright),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Reading Practice Card (Teal Gradient Card)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onOpenReading)
                .testTag("nav_reading_card")
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0F766E), Color(0xFF0D9488))
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HeroPillBadge(text = "CEFR A1–C2", bg = Color.White, fg = Color(0xFF0F766E))
                            HeroPillBadge(
                                text = "Read Aloud & Quiz",
                                bg = Color.White.copy(alpha = 0.2f),
                                fg = Color.White
                            )
                        }
                        Text(
                            text = "Reading Practice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Listen with audio sync, tap any word for Bangla meaning & check text matching",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Button(
                            onClick = onOpenReading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF0F766E)
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("quick_nav_reading_button")
                        ) {
                            Text("Start Reading", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoStories,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // 4 & 5. AI Speaking Partner + Student English Chat (2-Column Grid)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeFeatureGridCard(
                icon = Icons.Filled.Mic,
                title = "AI Speaking",
                subtitle = "Real-time voice chat & instant corrections",
                accent = Color(0xFF2563EB),
                accentBg = Color(0xFFDBEAFE),
                testTag = "quick_nav_speaking_button",
                onClick = onOpenSpeaking,
                modifier = Modifier.weight(1f)
            )
            HomeFeatureGridCard(
                icon = Icons.Filled.Forum,
                title = "Student Chat",
                subtitle = "Study groups, 1-on-1 & handwriting OCR",
                accent = HomeEmerald,
                accentBg = Color(0xFFD1FAE5),
                testTag = "quick_nav_chat_button",
                onClick = onOpenChat,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeroPillBadge(
    text: String,
    bg: Color,
    fg: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun HomeFeatureGridCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    accentBg: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = HomeTextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = HomeTextMuted,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun AccountControlsSection(
    activeSessionsCount: Int,
    isAdmin: Boolean,
    isBusy: Boolean,
    onOpenProfile: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Account & Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = HomeTextDark
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactAccountTile(
                icon = Icons.Filled.ManageAccounts,
                label = "Profile",
                testTag = "quick_nav_profile_button",
                onClick = onOpenProfile,
                modifier = Modifier.weight(1f)
            )
            CompactAccountTile(
                icon = Icons.Filled.Devices,
                label = "Sessions ($activeSessionsCount)",
                testTag = "quick_nav_sessions_button",
                onClick = onOpenSessions,
                modifier = Modifier.weight(1f)
            )
            CompactAccountTile(
                icon = Icons.Filled.Security,
                label = "Security",
                testTag = "quick_nav_security_button",
                onClick = onOpenSecurity,
                modifier = Modifier.weight(1f)
            )
        }

        if (isAdmin) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFFEF5E6),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenAdmin)
                    .testTag("nav_admin_card")
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .testTag("quick_nav_admin_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFDE68A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFFB45309)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Admin Dashboard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = HomeTextDark
                        )
                        Text(
                            text = "Manage Patterns, Vocabulary, Categories, CSV & Reading",
                            style = MaterialTheme.typography.bodySmall,
                            color = HomeTextMuted
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFFB45309)
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            enabled = !isBusy,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .height(48.dp)
                .testTag("dashboard_bottom_sign_out_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sign Out",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CompactAccountTile(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HomeEmerald,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = HomeTextDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BackendSyncWarningCard(
    error: String,
    isSyncing: Boolean,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFFFFBEB),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isSyncing) "Syncing Your Account..." else "Connection Taking Longer",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF92400E)
            )
            Text(
                text = if (isSyncing) "Connecting to the server. Please hold on a moment." else error,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF78350F)
            )
            if (isSyncing) {
                LinearProgressIndicator(
                    color = HomeEmerald,
                    trackColor = Color(0xFFFEF3C7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
            OutlinedButton(
                onClick = onRetry,
                enabled = !isSyncing,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF92400E)),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("retry_backend_sync_button")
            ) {
                Text(if (isSyncing) "Syncing..." else "Retry Sync", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
