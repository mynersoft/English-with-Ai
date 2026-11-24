package com.example.feature.dashboard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import com.example.core.ui.BannerTone
import com.example.core.ui.StatusBanner
import com.example.domain.model.UserProfile
import com.example.domain.model.UserSession
import com.example.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    profileState: UiState<UserProfile?>,
    sessionsState: UiState<List<UserSession>>,
    fallbackEmail: String,
    isEmailVerified: Boolean,
    actionState: DashboardActionState,
    onOpenProfile: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenSpeaking: () -> Unit = {},
    onOpenSentenceLearning: () -> Unit = {},
    onOpenVocabulary: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
    onRetryBackendSync: () -> Unit = {},
    onSignOut: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "English AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSignOut,
                        enabled = !actionState.isBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                        item {
                            Text(
                                text = "Quick Screen Navigation",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        item {
                            AllScreensButtonGrid(
                                onOpenSpeaking = onOpenSpeaking,
                                onOpenSentenceLearning = onOpenSentenceLearning,
                                onOpenVocabulary = onOpenVocabulary,
                                onOpenProfile = onOpenProfile,
                                onOpenSessions = onOpenSessions,
                                onOpenSecurity = onOpenSecurity,
                                onOpenAdmin = onOpenAdmin
                            )
                        }
                        item {
                            SignOutButtonCard(
                                isBusy = actionState.isBusy,
                                testTag = "dashboard_error_sign_out_button",
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    val profile = profileState.data
                    val activeSessionsCount = (sessionsState as? UiState.Success)?.data
                        ?.count { it.activeSession } ?: 1

                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            StatusBanner(
                                message = actionState.bannerMessage,
                                tone = actionState.bannerTone
                            )
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

                        item {
                            WelcomeHeroCard(
                                displayName = profile?.displayName ?: "Learner",
                                email = profile?.email ?: fallbackEmail,
                                verified = isEmailVerified || (profile?.emailVerified == true),
                                provider = profile?.authProvider ?: "password",
                                currentDay = (profile?.currentDay ?: 1L).toInt(),
                                level = profile?.safeLevel?.displayName ?: "Beginner"
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MetricCard(
                                    icon = Icons.Filled.LocalFireDepartment,
                                    label = "Streak",
                                    value = "${profile?.streakDays ?: 1L} day",
                                    accent = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    icon = Icons.Filled.CalendarMonth,
                                    label = "Daily Goal",
                                    value = "${profile?.dailyGoalMinutes ?: 20L} min",
                                    accent = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricCard(
                                    icon = Icons.Filled.Translate,
                                    label = "Bangla Help",
                                    value = if (profile?.banglaExplanations != false) "On" else "Off",
                                    accent = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        item {
                            Text(
                                text = "Quick Screen Navigation",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        item {
                            AllScreensButtonGrid(
                                onOpenSpeaking = onOpenSpeaking,
                                onOpenSentenceLearning = onOpenSentenceLearning,
                                onOpenVocabulary = onOpenVocabulary,
                                onOpenProfile = onOpenProfile,
                                onOpenSessions = onOpenSessions,
                                onOpenSecurity = onOpenSecurity,
                                onOpenAdmin = onOpenAdmin
                            )
                        }

                        item {
                            Text(
                                text = "Learning & Practice",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        item {
                            AccountNavCard(
                                icon = Icons.Filled.Translate,
                                title = "Vocabulary Mastery",
                                subtitle = "Daily 5-word learning, flashcards, Bangla quizzes, review & speaking",
                                testTag = "nav_vocabulary_card",
                                onClick = onOpenVocabulary
                            )
                        }

                        item {
                            AccountNavCard(
                                icon = Icons.Filled.Mic,
                                title = "AI Speaking Partner",
                                subtitle = "Talk in English with AI and see corrections for your mistakes",
                                testTag = "nav_speaking_card",
                                onClick = onOpenSpeaking
                            )
                        }

                        item {
                            AccountNavCard(
                                icon = Icons.Filled.MenuBook,
                                title = "Sentence Patterns",
                                subtitle = "Practice spoken English structures with Bangla meaning & speaking drills",
                                testTag = "nav_patterns_card",
                                onClick = onOpenSentenceLearning
                            )
                        }

                        item {
                            Text(
                                text = "Account & Session Center",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        item {
                            AccountNavCard(
                                icon = Icons.Filled.ManageAccounts,
                                title = "Profile & Learning Preferences",
                                subtitle = "Edit display name, level (${profile?.safeLevel?.displayName ?: "Beginner"}), and daily target",
                                testTag = "nav_profile_card",
                                onClick = onOpenProfile
                            )
                        }

                        item {
                            AccountNavCard(
                                icon = Icons.Filled.Devices,
                                title = "Active Sessions & Devices",
                                subtitle = "$activeSessionsCount active session(s) synced across your devices",
                                testTag = "nav_sessions_card",
                                onClick = onOpenSessions
                            )
                        }

                        item {
                            AccountNavCard(
                                icon = Icons.Filled.Security,
                                title = "Security, Password & Account",
                                subtitle = "Change password, send recovery link, deactivate or delete account",
                                testTag = "nav_security_card",
                                onClick = onOpenSecurity
                            )
                        }

                        item {
                            AccountNavCard(
                                icon = Icons.Filled.AdminPanelSettings,
                                title = "Admin · Manage Patterns & Audio",
                                subtitle = "Create, edit, and generate TTS or upload audio for patterns",
                                testTag = "nav_admin_card",
                                onClick = onOpenAdmin
                            )
                        }

                        item {
                            SignOutButtonCard(
                                isBusy = actionState.isBusy,
                                testTag = "dashboard_bottom_sign_out_button",
                                onSignOut = onSignOut
                            )
                        }

                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllScreensButtonGrid(
    onOpenSpeaking: () -> Unit,
    onOpenSentenceLearning: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenAdmin: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenQuickButton(
            icon = Icons.Filled.Translate,
            label = "Vocabulary Mastery (Daily 5 Words)",
            testTag = "quick_nav_vocabulary_button",
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            onClick = onOpenVocabulary,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScreenQuickButton(
                icon = Icons.Filled.Mic,
                label = "AI Speaking",
                testTag = "quick_nav_speaking_button",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onOpenSpeaking,
                modifier = Modifier.weight(1f)
            )
            ScreenQuickButton(
                icon = Icons.Filled.MenuBook,
                label = "Patterns",
                testTag = "quick_nav_patterns_button",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onOpenSentenceLearning,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScreenQuickButton(
                icon = Icons.Filled.ManageAccounts,
                label = "Profile",
                testTag = "quick_nav_profile_button",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onOpenProfile,
                modifier = Modifier.weight(1f)
            )
            ScreenQuickButton(
                icon = Icons.Filled.Devices,
                label = "Sessions",
                testTag = "quick_nav_sessions_button",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onOpenSessions,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScreenQuickButton(
                icon = Icons.Filled.Security,
                label = "Security",
                testTag = "quick_nav_security_button",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onOpenSecurity,
                modifier = Modifier.weight(1f)
            )
            ScreenQuickButton(
                icon = Icons.Filled.AdminPanelSettings,
                label = "Admin",
                testTag = "quick_nav_admin_button",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onOpenAdmin,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ScreenQuickButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = modifier
            .height(52.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WelcomeHeroCard(
    displayName: String,
    email: String,
    verified: Boolean,
    provider: String,
    currentDay: Int,
    level: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "$level · Day $currentDay of 60",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (verified) {
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Verified account",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Verified",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Welcome, $displayName",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$email · Signed in via ${provider.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "60-Day Curriculum Progress",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Text(
                            text = "${((currentDay / 60f) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (currentDay / 60f).coerceIn(0.02f, 1f) },
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AccountNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Backend Account Sync Failed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(
                onClick = onRetry,
                enabled = !isSyncing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("retry_backend_sync_button")
            ) {
                Text(if (isSyncing) "Syncing..." else "Retry MongoDB Sync")
            }
        }
    }
}

@Composable
private fun SignOutButtonCard(
    isBusy: Boolean,
    testTag: String,
    onSignOut: () -> Unit
) {
    OutlinedButton(
        onClick = onSignOut,
        enabled = !isBusy,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .height(50.dp)
            .testTag(testTag)
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
