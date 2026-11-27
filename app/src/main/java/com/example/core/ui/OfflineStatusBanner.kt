package com.example.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val OfflineSurfaceBg = Color(0xFFF4F7F6)
private val OfflineSurfaceBorder = Color(0xFFD5E0DC)
private val OfflineAccent = Color(0xFF0E6B3B)
private val OfflineBadgeBg = Color(0xFFE0EFE8)
private val OfflineTitleDark = Color(0xFF111827)
private val OfflineBodyMuted = Color(0xFF4B5563)

@Composable
fun OfflineStatusCard(
    isReconnecting: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "You're Currently Offline",
    message: String = "Please check your Wi-Fi or mobile data. Your lessons and progress will sync automatically once connected."
) {
    val infiniteTransition = rememberInfiniteTransition(label = "offline_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offline_icon_alpha"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = OfflineSurfaceBg,
        border = BorderStroke(1.dp, OfflineSurfaceBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_status_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(OfflineBadgeBg)
                        .alpha(pulseAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    if (isReconnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.2.dp,
                            color = OfflineAccent
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.WifiOff,
                            contentDescription = "Offline",
                            tint = OfflineAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isReconnecting) "Reconnecting..." else title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OfflineTitleDark
                    )
                    Text(
                        text = "Waiting for internet connection...",
                        style = MaterialTheme.typography.labelSmall,
                        color = OfflineAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = OfflineBodyMuted
            )

            LinearProgressIndicator(
                color = OfflineAccent,
                trackColor = OfflineBadgeBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            OutlinedButton(
                onClick = onRetry,
                enabled = !isReconnecting,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, OfflineAccent.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OfflineAccent),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("offline_retry_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isReconnecting) "Checking..." else "Try Again",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun OfflineFloatingBanner(
    visible: Boolean,
    isReconnecting: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "offline_strip_pulse")
        val dotAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "offline_strip_dot"
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1F2937),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("offline_floating_banner")
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WifiOff,
                        contentDescription = null,
                        tint = Color(0xFF6EE7B7),
                        modifier = Modifier
                            .size(18.dp)
                            .alpha(dotAlpha)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isReconnecting) "Reconnecting to server..." else "You are offline",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Waiting for internet connection...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD1D5DB),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = onRetry,
                        enabled = !isReconnecting,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("offline_banner_retry_button")
                    ) {
                        Text(
                            text = if (isReconnecting) "..." else "Retry",
                            color = Color(0xFF6EE7B7),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                LinearProgressIndicator(
                    color = Color(0xFF34D399),
                    trackColor = Color(0xFF374151),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }
        }
    }
}
