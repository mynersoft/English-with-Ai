package com.example.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.launch

private val DrawerFlameOrange = Color(0xFFF97316)

private data class StudentSidebarEntry(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val badge: String? = null,
    val badgeHighlight: Boolean = false,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
fun StudentSidebarContainer(
    selectedTab: AppMainTab,
    displayName: String,
    email: String,
    level: String,
    planLabel: String,
    streakDays: Int,
    currentDay: Int,
    dailyGoalMin: Int,
    activeSessionsCount: Int,
    activeChatRoomsCount: Int,
    isAdmin: Boolean,
    gesturesEnabled: Boolean = true,
    onSelectTab: (AppMainTab) -> Unit,
    onOpenReading: () -> Unit,
    onOpenCourses: () -> Unit = {},
    onOpenVideoCalls: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit,
    content: @Composable (openDrawer: () -> Unit, isWideScreen: Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }

    val isDark = LocalIsDarkTheme.current
    val drawerBg = if (isDark) Color(0xFF111827) else Color.White

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        BackHandler(enabled = !isWideScreen && drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }

        val closeIfModal: (() -> Unit) -> Unit = { action ->
            if (isWideScreen) {
                action()
            } else {
                scope.launch {
                    drawerState.close()
                    action()
                }
            }
        }

        val drawerContent: @Composable () -> Unit = {
            StudentSidebarDrawerContent(
                selectedTab = selectedTab,
                displayName = displayName,
                email = email,
                level = level,
                planLabel = planLabel,
                streakDays = streakDays,
                currentDay = currentDay,
                dailyGoalMin = dailyGoalMin,
                activeSessionsCount = activeSessionsCount,
                activeChatRoomsCount = activeChatRoomsCount,
                isAdmin = isAdmin,
                onSelectTab = { tab -> closeIfModal { onSelectTab(tab) } },
                onOpenReading = { closeIfModal(onOpenReading) },
                onOpenCourses = { closeIfModal(onOpenCourses) },
                onOpenVideoCalls = { closeIfModal(onOpenVideoCalls) },
                onOpenProfile = { closeIfModal(onOpenProfile) },
                onOpenSessions = { closeIfModal(onOpenSessions) },
                onOpenSecurity = { closeIfModal(onOpenSecurity) },
                onOpenAdmin = { closeIfModal(onOpenAdmin) },
                onRequestLogout = { confirmLogout = true }
            )
        }

        val openDrawer: () -> Unit = {
            if (!isWideScreen) {
                scope.launch { drawerState.open() }
            }
        }

        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(292.dp),
                        drawerContainerColor = drawerBg
                    ) {
                        drawerContent()
                    }
                }
            ) {
                content(openDrawer, true)
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = gesturesEnabled || drawerState.isOpen,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(304.dp),
                        drawerContainerColor = drawerBg
                    ) {
                        drawerContent()
                    }
                }
            ) {
                content(openDrawer, false)
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            containerColor = if (isDark) Color(0xFF131C31) else Color.White,
            title = { Text("Log out?", fontWeight = FontWeight.Bold) },
            text = { Text("You will be signed out of your student account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onSignOut()
                    },
                    modifier = Modifier.testTag("student_confirm_logout_button")
                ) {
                    Text("Log Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StudentSidebarDrawerContent(
    selectedTab: AppMainTab,
    displayName: String,
    email: String,
    level: String,
    planLabel: String,
    streakDays: Int,
    currentDay: Int,
    dailyGoalMin: Int,
    activeSessionsCount: Int,
    activeChatRoomsCount: Int,
    isAdmin: Boolean,
    onSelectTab: (AppMainTab) -> Unit,
    onOpenReading: () -> Unit,
    onOpenCourses: () -> Unit = {},
    onOpenVideoCalls: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenAdmin: () -> Unit,
    onRequestLogout: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val context = LocalContext.current
    val themeMode by AppThemeManager.observeThemeMode(context).collectAsStateWithLifecycle()

    val drawerBg = if (isDark) Color(0xFF111827) else Color.White
    val emerald = if (isDark) Color(0xFF34D399) else Color(0xFF0E6B3B)
    val activePill = if (isDark) Color(0xFF133A29) else Color(0xFFD1FAE5)
    val mintTop = if (isDark) Color(0xFF123526) else Color(0xFFD3F4E4)
    val mintBottom = if (isDark) Color(0xFF0F241C) else Color(0xFFEAFAF2)
    val textDark = if (isDark) Color(0xFFF8FAFC) else Color(0xFF111827)
    val textMuted = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)
    val dividerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE5E7EB)
    val miniBadgeBg = if (isDark) Color(0xFF1E293B) else Color.White

    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = activePill,
        selectedIconColor = emerald,
        selectedTextColor = emerald,
        unselectedIconColor = textMuted,
        unselectedTextColor = textDark
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(drawerBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Student Profile Summary Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable { onSelectTab(AppMainTab.PROGRESS) }
                .testTag("student_drawer_header")
        ) {
            Column(
                modifier = Modifier
                    .background(Brush.verticalGradient(listOf(mintTop, mintBottom)))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.size(48.dp)) {
                        Image(
                            painter = painterResource(id = R.drawable.img_student_avatar),
                            contentDescription = "$displayName avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .border(2.dp, miniBadgeBg, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(1.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF16A34A))
                                .border(1.5.dp, miniBadgeBg, CircleShape)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName.ifBlank { "Learner" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (email.isNotBlank()) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.labelSmall,
                                color = textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DrawerMiniBadge(
                        text = "$streakDays d",
                        icon = Icons.Filled.LocalFireDepartment,
                        iconTint = DrawerFlameOrange,
                        bg = miniBadgeBg,
                        fg = textDark
                    )
                    DrawerMiniBadge(
                        text = "$level · D$currentDay",
                        bg = miniBadgeBg.copy(alpha = 0.9f),
                        fg = emerald
                    )
                    DrawerMiniBadge(
                        text = planLabel,
                        icon = Icons.Filled.WorkspacePremium,
                        iconTint = emerald,
                        bg = activePill,
                        fg = emerald
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "LEARNING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            val learningEntries = listOf(
                StudentSidebarEntry(
                    label = "Home Dashboard",
                    icon = Icons.Filled.Home,
                    selected = selectedTab == AppMainTab.HOME,
                    tag = "student_drawer_home",
                    onClick = { onSelectTab(AppMainTab.HOME) }
                ),
                StudentSidebarEntry(
                    label = "Vocabulary",
                    icon = Icons.Filled.Style,
                    selected = selectedTab == AppMainTab.VOCABULARY,
                    tag = "student_drawer_vocabulary",
                    onClick = { onSelectTab(AppMainTab.VOCABULARY) }
                ),
                StudentSidebarEntry(
                    label = "Sentence Patterns",
                    icon = Icons.Filled.GraphicEq,
                    selected = selectedTab == AppMainTab.PATTERNS,
                    tag = "student_drawer_patterns",
                    onClick = { onSelectTab(AppMainTab.PATTERNS) }
                ),
                StudentSidebarEntry(
                    label = "Reading Practice",
                    icon = Icons.Filled.AutoStories,
                    selected = false,
                    badge = "A1–C2",
                    tag = "student_drawer_reading",
                    onClick = onOpenReading
                ),
                StudentSidebarEntry(
                    label = "Courses",
                    icon = Icons.Filled.School,
                    selected = false,
                    badge = "Video · PDF",
                    tag = "student_drawer_courses",
                    onClick = onOpenCourses
                ),
                StudentSidebarEntry(
                    label = "AI Speaking",
                    icon = Icons.Filled.Mic,
                    selected = selectedTab == AppMainTab.SPEAKING,
                    tag = "student_drawer_speaking",
                    onClick = { onSelectTab(AppMainTab.SPEAKING) }
                ),
                StudentSidebarEntry(
                    label = "Student Chat",
                    icon = Icons.Filled.Forum,
                    selected = selectedTab == AppMainTab.CHAT,
                    badge = if (activeChatRoomsCount > 0) "$activeChatRoomsCount" else null,
                    badgeHighlight = activeChatRoomsCount > 0,
                    tag = "student_drawer_chat",
                    onClick = { onSelectTab(AppMainTab.CHAT) }
                ),
                StudentSidebarEntry(
                    label = "Video Calls",
                    icon = Icons.Filled.Videocam,
                    selected = false,
                    badge = "1-on-1 · Group",
                    tag = "student_drawer_video_calls",
                    onClick = onOpenVideoCalls
                ),
                StudentSidebarEntry(
                    label = "Progress & Reports",
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    selected = selectedTab == AppMainTab.PROGRESS,
                    badge = "${streakDays}d",
                    tag = "student_drawer_progress",
                    onClick = { onSelectTab(AppMainTab.PROGRESS) }
                )
            )

            learningEntries.forEach { entry ->
                StudentDrawerNavRow(entry = entry, colors = itemColors)
            }

            HorizontalDivider(
                color = dividerColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Text(
                text = "APPEARANCE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("drawer_theme_mode_selector"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        color = if (selected) activePill else if (isDark) Color(0xFF1E293B) else Color(0xFFF3F4F6),
                        border = BorderStroke(
                            1.dp,
                            if (selected) emerald else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { AppThemeManager.setThemeMode(context, mode) }
                            .testTag("drawer_theme_chip_${mode.storageKey}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = "${mode.label} theme",
                                tint = if (selected) emerald else textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) emerald else textDark,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = dividerColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Text(
                text = "ACCOUNT & SETTINGS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            val accountEntries = buildList {
                add(
                    StudentSidebarEntry(
                        label = "Profile & Goal",
                        icon = Icons.Filled.ManageAccounts,
                        selected = false,
                        badge = "${dailyGoalMin}m",
                        tag = "student_drawer_profile",
                        onClick = onOpenProfile
                    )
                )
                add(
                    StudentSidebarEntry(
                        label = "Devices & Sessions",
                        icon = Icons.Filled.Devices,
                        selected = false,
                        badge = "$activeSessionsCount",
                        tag = "student_drawer_sessions",
                        onClick = onOpenSessions
                    )
                )
                add(
                    StudentSidebarEntry(
                        label = "Security & Password",
                        icon = Icons.Filled.Security,
                        selected = false,
                        tag = "student_drawer_security",
                        onClick = onOpenSecurity
                    )
                )
                if (isAdmin) {
                    add(
                        StudentSidebarEntry(
                            label = "Admin Console",
                            icon = Icons.Filled.AdminPanelSettings,
                            selected = false,
                            badge = "Admin",
                            tag = "student_drawer_admin",
                            onClick = onOpenAdmin
                        )
                    )
                }
            }

            accountEntries.forEach { entry ->
                StudentDrawerNavRow(entry = entry, colors = itemColors)
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        HorizontalDivider(
            color = dividerColor,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            NavigationDrawerItem(
                label = {
                    Text(
                        text = "Log Out",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                selected = false,
                onClick = onRequestLogout,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("student_drawer_logout_button")
            )
        }
    }
}

@Composable
private fun StudentDrawerNavRow(
    entry: StudentSidebarEntry,
    colors: androidx.compose.material3.NavigationDrawerItemColors
) {
    val isDark = LocalIsDarkTheme.current
    val emerald = if (isDark) Color(0xFF10B981) else Color(0xFF0E6B3B)
    val badgeBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF3F4F6)
    val badgeText = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)

    NavigationDrawerItem(
        label = {
            Text(
                text = entry.label,
                fontWeight = if (entry.selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        selected = entry.selected,
        onClick = entry.onClick,
        icon = { Icon(entry.icon, contentDescription = null) },
        colors = colors,
        badge = entry.badge?.let { text ->
            {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        entry.badgeHighlight -> Color(0xFFDC2626)
                        entry.selected -> emerald
                        else -> badgeBg
                    }
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            entry.badgeHighlight || entry.selected -> Color.White
                            else -> badgeText
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(entry.tag)
    )
}

@Composable
private fun DrawerMiniBadge(
    text: String,
    icon: ImageVector? = null,
    iconTint: Color = if (LocalIsDarkTheme.current) Color(0xFF34D399) else Color(0xFF0E6B3B),
    bg: Color,
    fg: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1
            )
        }
    }
}
