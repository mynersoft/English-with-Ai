package com.example.feature.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.common.UiState
import com.example.core.ui.OfflineStatusCard
import com.example.domain.model.CourseReportModule
import com.example.domain.model.StreakCalendarData
import com.example.domain.model.StudentDashboardData
import com.example.domain.model.WeeklyActivityDay
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalIsDarkTheme

private val FlameOrange = Color(0xFFF97316)

@Composable
private fun dashBg(): Color = if (LocalIsDarkTheme.current) Color(0xFF0B1120) else Color.White

@Composable
private fun dashCard(): Color = if (LocalIsDarkTheme.current) Color(0xFF131C31) else Color.White

@Composable
private fun dashBandBg(): Color = if (LocalIsDarkTheme.current) Color(0xFF111827) else Color(0xFFF2F4F4)

@Composable
private fun dashHeroTop(): Color = if (LocalIsDarkTheme.current) Color(0xFF123526) else Color(0xFFD3F4E4)

@Composable
private fun dashHeroMid(): Color = if (LocalIsDarkTheme.current) Color(0xFF0E211C) else Color(0xFFEAFAF2)

@Composable
private fun dashEmerald(): Color = if (LocalIsDarkTheme.current) Color(0xFF34D399) else Color(0xFF0E6B3B)

@Composable
private fun dashVocabGreen(): Color = if (LocalIsDarkTheme.current) Color(0xFF4ADE80) else Color(0xFF15803D)

@Composable
private fun dashPatternsPurple(): Color = if (LocalIsDarkTheme.current) Color(0xFFC084FC) else Color(0xFF7C3AED)

@Composable
private fun dashSpeakingIndigo(): Color = if (LocalIsDarkTheme.current) Color(0xFF818CF8) else Color(0xFF4F46E5)

@Composable
private fun dashAmber(): Color = if (LocalIsDarkTheme.current) Color(0xFFFBBF24) else Color(0xFFF59E0B)

@Composable
private fun dashDotInactive(): Color = if (LocalIsDarkTheme.current) Color(0xFF1E293B) else Color(0xFFEAEEF0)

@Composable
private fun dashTextPrimary(): Color = if (LocalIsDarkTheme.current) Color(0xFFF8FAFC) else Color(0xFF111827)

@Composable
private fun dashTextMuted(): Color = if (LocalIsDarkTheme.current) Color(0xFF94A3B8) else Color(0xFF6B7280)

@Composable
private fun dashBorder(): Color = if (LocalIsDarkTheme.current) Color(0xFF23304A) else Color(0xFFE5E7EB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileDashboardScreen(
    state: UiState<StudentDashboardData>,
    isRefreshing: Boolean,
    fallbackName: String,
    fallbackEmail: String,
    isAdmin: Boolean,
    bottomBar: @Composable () -> Unit,
    isOnline: Boolean = true,
    showMenuButton: Boolean = true,
    onOpenMenu: () -> Unit = {},
    onRefresh: () -> Unit,
    onBackToHome: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit
) {
    var showStreakCalendar by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val expandedModules = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = dashBg(),
        bottomBar = bottomBar
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when (state) {
                is UiState.Loading -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ProfileHeaderSection(
                            name = fallbackName.ifBlank { "Learner" },
                            subtitle = fallbackEmail,
                            planLabel = if (isAdmin) "Admin" else "Free",
                            streakDays = 0,
                            vocabCount = 0,
                            patternsCount = 0,
                            speakingCount = 0,
                            isRefreshing = true,
                            showMenuButton = showMenuButton,
                            onOpenMenu = onOpenMenu,
                            onBack = onBackToHome,
                            onOpenSettings = { showSettingsSheet = true },
                            onRefresh = onRefresh,
                            onOpenStreakCalendar = {}
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = dashEmerald())
                        }
                    }
                }

                is UiState.Error -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            ProfileHeaderSection(
                                name = fallbackName.ifBlank { "Learner" },
                                subtitle = fallbackEmail,
                                planLabel = if (isAdmin) "Admin" else "Free",
                                streakDays = 0,
                                vocabCount = 0,
                                patternsCount = 0,
                                speakingCount = 0,
                                isRefreshing = isRefreshing,
                                showMenuButton = showMenuButton,
                                onOpenMenu = onOpenMenu,
                                onBack = onBackToHome,
                                onOpenSettings = { showSettingsSheet = true },
                                onRefresh = onRefresh,
                                onOpenStreakCalendar = {}
                            )
                        }
                        item {
                            if (!isOnline) {
                                OfflineStatusCard(
                                    isReconnecting = isRefreshing,
                                    onRetry = onRefresh,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                                )
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Could not load student progress",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        OutlinedButton(
                                            onClick = onRefresh,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("student_dashboard_retry_button")
                                        ) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is UiState.Success -> {
                    val data = state.data
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            ProfileHeaderSection(
                                name = data.user.name.ifBlank { fallbackName.ifBlank { "Learner" } },
                                subtitle = data.user.email.ifBlank { fallbackEmail },
                                planLabel = data.user.planLabel,
                                streakDays = data.stats.streakDays,
                                vocabCount = data.stats.vocabularyLearned,
                                patternsCount = data.stats.patternsPracticed,
                                speakingCount = data.stats.speakingSessions,
                                speakingMinutes = data.stats.speakingMinutes,
                                readingCount = data.stats.readingPassagesCompleted,
                                readingMinutes = data.stats.readingMinutes,
                                isRefreshing = isRefreshing,
                                showMenuButton = showMenuButton,
                                onOpenMenu = onOpenMenu,
                                onBack = onBackToHome,
                                onOpenSettings = { showSettingsSheet = true },
                                onRefresh = onRefresh,
                                onOpenStreakCalendar = { showStreakCalendar = true }
                            )
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(dashBandBg())
                            )
                        }

                        item {
                            LearningActivitySection(
                                weekRangeLabel = data.weekRangeLabel,
                                weeklyActivity = data.weeklyActivity
                            )
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(dashBandBg())
                            )
                        }

                        item {
                            ReadingReportSection(
                                report = data.readingReport,
                                onOpenReading = { onNavigateRoute("reading") }
                            )
                        }

                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(dashBandBg())
                            )
                        }

                        item {
                            CourseReportSection(
                                overallPercent = data.courseReport.overallPercent,
                                modules = data.courseReport.modules,
                                speakingMinutes = data.stats.speakingMinutes,
                                expandedMap = expandedModules,
                                onToggleModule = { key ->
                                    expandedModules[key] = !(expandedModules[key] ?: false)
                                },
                                onOpenModule = onNavigateRoute
                            )
                        }
                    }

                    if (showStreakCalendar) {
                        StreakCalendarDialog(
                            streakDays = data.stats.streakDays,
                            calendar = data.streakCalendar,
                            onDismiss = { showStreakCalendar = false }
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val context = LocalContext.current
        val themeMode by AppThemeManager.observeThemeMode(context).collectAsStateWithLifecycle()
        val isDark = LocalIsDarkTheme.current
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = dashCard()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Account & Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = dashTextPrimary(),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = dashTextMuted()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .testTag("progress_theme_mode_selector"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val selected = themeMode == mode
                        val modeIcon = when (mode) {
                            AppThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                            AppThemeMode.LIGHT -> Icons.Filled.LightMode
                            AppThemeMode.DARK -> Icons.Filled.DarkMode
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) (if (isDark) Color(0xFF133A29) else Color(0xFFD1FAE5))
                            else (if (isDark) Color(0xFF1E293B) else Color(0xFFF3F4F6)),
                            border = BorderStroke(
                                1.dp,
                                if (selected) dashEmerald() else Color.Transparent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { AppThemeManager.setThemeMode(context, mode) }
                                .testTag("progress_theme_chip_${mode.storageKey}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = "${mode.label} theme",
                                    tint = if (selected) dashEmerald() else dashTextMuted(),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) dashEmerald() else dashTextPrimary()
                                )
                            }
                        }
                    }
                }
                SettingsSheetItem(
                    icon = Icons.Filled.ManageAccounts,
                    title = "Edit Profile & Learning Level",
                    onClick = {
                        showSettingsSheet = false
                        onOpenProfile()
                    }
                )
                SettingsSheetItem(
                    icon = Icons.Filled.Devices,
                    title = "Active Devices & Sessions",
                    onClick = {
                        showSettingsSheet = false
                        onOpenSessions()
                    }
                )
                SettingsSheetItem(
                    icon = Icons.Filled.Security,
                    title = "Security & Password",
                    onClick = {
                        showSettingsSheet = false
                        onOpenSecurity()
                    }
                )
                if (isAdmin) {
                    SettingsSheetItem(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = "Admin Dashboard",
                        onClick = {
                            showSettingsSheet = false
                            onOpenAdmin()
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = dashBorder())
                SettingsSheetItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "Sign Out",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        showSettingsSheet = false
                        onSignOut()
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileHeaderSection(
    name: String,
    subtitle: String,
    planLabel: String,
    streakDays: Int,
    vocabCount: Int,
    patternsCount: Int,
    speakingCount: Int,
    speakingMinutes: Int = 0,
    readingCount: Int = 0,
    readingMinutes: Int = 0,
    isRefreshing: Boolean,
    showMenuButton: Boolean = true,
    onOpenMenu: () -> Unit = {},
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onOpenStreakCalendar: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val circleBtnBg = if (isDark) Color(0xFF131C31) else Color.White
    val planBadgeBg = if (isDark) Color(0xFF1A2D24) else Color(0xFFD9E6DF)
    val planBadgeFg = if (isDark) Color(0xFFCBD5E1) else Color(0xFF374151)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(dashHeroTop(), dashHeroMid(), dashBg())
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (showMenuButton) {
                    Surface(
                        shape = CircleShape,
                        color = circleBtnBg,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(44.dp)
                    ) {
                        IconButton(
                            onClick = onOpenMenu,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("student_progress_sidebar_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Open student menu",
                                tint = dashTextPrimary()
                            )
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = circleBtnBg,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("student_dashboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = dashTextPrimary()
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = CircleShape,
                    color = circleBtnBg,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("student_dashboard_refresh_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = dashEmerald()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh progress",
                                tint = dashTextPrimary()
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = circleBtnBg,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("student_dashboard_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Account and settings",
                            tint = dashTextPrimary()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Plan badge ("Free" or "Admin") matching screenshot
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = planBadgeBg
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = planBadgeFg,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = planLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = planBadgeFg
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(88.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.img_student_avatar),
                    contentDescription = "$name profile avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .border(2.dp, circleBtnBg, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 3.dp, bottom = 3.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16A34A))
                        .border(2.dp, circleBtnBg, CircleShape)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = dashTextPrimary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = dashTextMuted(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2x2 Summary Metrics Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InlineStatMetric(
                icon = Icons.Filled.LocalFireDepartment,
                iconTint = FlameOrange,
                valueText = "$streakDays-day",
                labelText = "streak",
                modifier = Modifier.weight(1f)
            )
            InlineStatMetric(
                icon = Icons.Outlined.MenuBook,
                iconTint = dashVocabGreen(),
                valueText = "$vocabCount",
                labelText = "vocabulary",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InlineStatMetric(
                icon = Icons.Filled.GraphicEq,
                iconTint = dashPatternsPurple(),
                valueText = "$patternsCount",
                labelText = "patterns",
                modifier = Modifier.weight(1f)
            )
            InlineStatMetric(
                icon = Icons.Filled.RecordVoiceOver,
                iconTint = dashSpeakingIndigo(),
                valueText = "${speakingMinutes}m",
                labelText = if (speakingCount > 0) "AI talk ($speakingCount)" else "AI talk",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InlineStatMetric(
                icon = Icons.Filled.AutoStories,
                iconTint = dashVocabGreen(),
                valueText = "$readingCount",
                labelText = "passages",
                modifier = Modifier.weight(1f)
            )
            InlineStatMetric(
                icon = Icons.Outlined.MenuBook,
                iconTint = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E),
                valueText = "${readingMinutes}m",
                labelText = "reading",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedButton(
            onClick = onOpenStreakCalendar,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, dashBorder()),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = dashCard(),
                contentColor = dashEmerald()
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .minimumInteractiveComponentSize()
                .testTag("view_streak_calendar_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.EventAvailable,
                contentDescription = null,
                tint = dashEmerald(),
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "View Streak Calendar",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = dashEmerald()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun InlineStatMetric(
    icon: ImageVector,
    iconTint: Color,
    valueText: String,
    labelText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = dashTextPrimary())) {
                    append(valueText)
                }
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = dashTextMuted())) {
                    append(labelText)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LearningActivitySection(
    weekRangeLabel: String,
    weeklyActivity: List<WeeklyActivityDay>
) {
    val isDark = LocalIsDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(dashBg())
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Learning Activity",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = dashTextPrimary()
        )
        if (weekRangeLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = weekRangeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = dashTextMuted()
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        ActivityMatrixRow(
            icon = Icons.Filled.GraphicEq,
            iconTint = dashPatternsPurple(),
            label = "Patterns",
            activeList = weeklyActivity.map { it.patternsActive }
        )
        Spacer(modifier = Modifier.height(14.dp))
        ActivityMatrixRow(
            icon = Icons.Filled.RecordVoiceOver,
            iconTint = if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6),
            label = "AI Speaking",
            activeList = weeklyActivity.map { it.speakingActive }
        )
        Spacer(modifier = Modifier.height(14.dp))
        ActivityMatrixRow(
            icon = Icons.Filled.Style,
            iconTint = dashEmerald(),
            label = "Vocabulary",
            activeList = weeklyActivity.map { it.vocabularyActive }
        )
        Spacer(modifier = Modifier.height(14.dp))
        ActivityMatrixRow(
            icon = Icons.Filled.AutoStories,
            iconTint = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E),
            label = "Reading",
            activeList = weeklyActivity.map { it.readingActive }
        )
        Spacer(modifier = Modifier.height(14.dp))
        ActivityMatrixRow(
            icon = Icons.Filled.CheckCircle,
            iconTint = dashAmber(),
            label = "Practice",
            activeList = weeklyActivity.map { it.practiceActive }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 7-day footer aligned identically to dots above
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1.35f))
            weeklyActivity.forEach { day ->
                Box(
                    modifier = Modifier.weight(0.5f),
                    contentAlignment = Alignment.Center
                ) {
                    if (day.isToday) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) Color(0xFF10B981) else Color(0xFF0E6B3B))
                                .padding(horizontal = 6.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = day.dayNumber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = day.dayShort,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.92f),
                                lineHeight = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 7.dp)
                        ) {
                            Text(
                                text = day.dayNumber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = dashTextPrimary(),
                                lineHeight = 16.sp
                            )
                            Text(
                                text = day.dayShort,
                                fontSize = 10.sp,
                                color = dashTextMuted(),
                                lineHeight = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityMatrixRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    activeList: List<Boolean>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1.35f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = dashTextPrimary(),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val normalized = if (activeList.size == 7) activeList else List(7) { false }
        normalized.forEach { active ->
            Box(
                modifier = Modifier.weight(0.5f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(if (active) iconTint else dashDotInactive())
                )
            }
        }
    }
}

@Composable
private fun CourseReportSection(
    overallPercent: Int,
    modules: List<CourseReportModule>,
    speakingMinutes: Int,
    expandedMap: MutableMap<String, Boolean>,
    onToggleModule: (String) -> Unit,
    onOpenModule: (String) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(dashBg())
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Course Report",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = dashTextPrimary()
        )
        Text(
            text = "Overall $overallPercent% completed",
            style = MaterialTheme.typography.bodySmall,
            color = dashTextMuted()
        )

        Spacer(modifier = Modifier.height(4.dp))

        modules.forEach { mod ->
            val isExpanded = expandedMap[mod.key] == true
            val (icon, iconTint, iconBg) = when (mod.key) {
                "vocabulary" -> Triple(Icons.Filled.Style, dashEmerald(), if (isDark) Color(0xFF133A29) else Color(0xFFE6F4EC))
                "patterns" -> Triple(Icons.Filled.GraphicEq, dashPatternsPurple(), if (isDark) Color(0xFF321B4D) else Color(0xFFF3E8FF))
                "speaking" -> Triple(Icons.Filled.RecordVoiceOver, dashSpeakingIndigo(), if (isDark) Color(0xFF1F2752) else Color(0xFFEEF2FF))
                "reading" -> Triple(Icons.Filled.AutoStories, dashEmerald(), if (isDark) Color(0xFF133A29) else Color(0xFFE6F4EC))
                else -> Triple(Icons.Filled.Forum, if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E), if (isDark) Color(0xFF133A36) else Color(0xFFCCFBF1))
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = dashCard(),
                border = BorderStroke(1.dp, dashBorder()),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onToggleModule(mod.key) }
                    .testTag("course_report_card_${mod.key}")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(iconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mod.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = dashTextPrimary(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (mod.key == "speaking" || mod.route == "speaking") {
                                Text(
                                    text = "$speakingMinutes min talked with AI",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = dashSpeakingIndigo(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "${mod.percent}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = dashTextMuted()
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = dashTextMuted()
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = dashBorder())
                            Text(
                                text = mod.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = dashTextMuted()
                            )
                            Button(
                                onClick = { onOpenModule(mod.route) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color(0xFF10B981) else Color(0xFF0E6B3B),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Continue ${mod.title.substringBefore(" ·")}")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
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
private fun StreakCalendarDialog(
    streakDays: Int,
    calendar: StreakCalendarData,
    onDismiss: () -> Unit
) {
    val activeSet = remember(calendar.activeDaysOfMonth) { calendar.activeDaysOfMonth.toSet() }
    val dayHeaders = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val leadingBlanks = calendar.firstDayOfWeek.coerceIn(0, 6)
    val totalCells = leadingBlanks + calendar.daysInMonth
    val weeks = (totalCells + 6) / 7
    val isDark = LocalIsDarkTheme.current
    val activeCircle = if (isDark) Color(0xFF10B981) else Color(0xFF0E6B3B)
    val todaySoft = if (isDark) Color(0xFF133A29) else Color(0xFFE6F4EC)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dashCard(),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = FlameOrange
                )
                Text(
                    text = calendar.monthLabel.ifBlank { "Streak Calendar" },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalendarSummaryPill("Current", "$streakDays d", Modifier.weight(1f))
                    CalendarSummaryPill("Longest", "${calendar.longestStreak} d", Modifier.weight(1f))
                    CalendarSummaryPill("Active", "${calendar.totalActiveDays} d", Modifier.weight(1f))
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    dayHeaders.forEach { d ->
                        Text(
                            text = d,
                            style = MaterialTheme.typography.labelSmall,
                            color = dashTextMuted(),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (w in 0 until weeks) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val cellIndex = w * 7 + c
                                val dayNum = cellIndex - leadingBlanks + 1
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (dayNum in 1..calendar.daysInMonth) {
                                        val isActive = activeSet.contains(dayNum)
                                        val isToday = dayNum == calendar.todayDay
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isActive -> activeCircle
                                                        isToday -> todaySoft
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .then(
                                                    if (isToday && !isActive) {
                                                        Modifier.border(1.5.dp, dashEmerald(), CircleShape)
                                                    } else Modifier
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isActive || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isActive -> Color.White
                                                    isToday -> dashEmerald()
                                                    else -> dashTextPrimary()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = dashEmerald(), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CalendarSummaryPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Color(0xFF1A2A24) else Color(0xFFF3F8F5),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = dashEmerald()
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = dashTextMuted()
            )
        }
    }
}

@Composable
private fun SettingsSheetItem(
    icon: ImageVector,
    title: String,
    tint: Color = dashTextPrimary(),
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = tint,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = dashTextMuted()
            )
        }
    }
}
