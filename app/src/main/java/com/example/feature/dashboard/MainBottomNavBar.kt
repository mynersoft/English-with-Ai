package com.example.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.ui.theme.LocalIsDarkTheme

enum class AppMainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    VOCABULARY("Vocab", Icons.Filled.Style, Icons.Outlined.Style),
    PATTERNS("Patterns", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    SPEAKING("Speak", Icons.Filled.Mic, Icons.Outlined.Mic),
    CHAT("Chat", Icons.Filled.Forum, Icons.Outlined.Forum),
    PROGRESS("Progress", Icons.AutoMirrored.Filled.TrendingUp, Icons.AutoMirrored.Outlined.TrendingUp)
}

private val NavBadgeRed = Color(0xFFDC2626)

@Composable
fun MainBottomNavBar(
    selectedTab: AppMainTab,
    chatBadgeCount: Int = 0,
    onSelectTab: (AppMainTab) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val barBg = if (isDark) Color(0xFF111827) else Color.White
    val dividerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE5E7EB)
    val activeGreen = if (isDark) Color(0xFF34D399) else Color(0xFF0E6B3B)
    val activePill = if (isDark) Color(0xFF133A29) else Color(0xFFD1FAE5)
    val inactiveColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF525E66)

    Surface(
        color = barBg,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 1.dp, color = dividerColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppMainTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = ripple(bounded = true),
                                onClick = { onSelectTab(tab) }
                            )
                            .padding(vertical = 4.dp)
                            .testTag("bottom_tab_${tab.name.lowercase()}")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(46.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (isSelected) activePill else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                tint = if (isSelected) activeGreen else inactiveColor,
                                modifier = Modifier.size(21.dp)
                            )
                            if (tab == AppMainTab.CHAT && chatBadgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-4).dp, y = 1.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(NavBadgeRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (chatBadgeCount > 9) "9+" else "$chatBadgeCount",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 10.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                lineHeight = 12.sp
                            ),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) activeGreen else inactiveColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
