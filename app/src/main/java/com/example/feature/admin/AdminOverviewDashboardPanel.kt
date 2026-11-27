package com.example.feature.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val EmeraldSuccess = Color(0xFF15803D)
private val EmeraldContainer = Color(0xFFDCFCE7)
private val AmberWarning = Color(0xFFB45309)
private val AmberContainer = Color(0xFFFEF3C7)
private val IndigoAccent = Color(0xFF4338CA)
private val IndigoContainer = Color(0xFFE0E7FF)
private val CyanAccent = Color(0xFF0E7490)
private val CyanContainer = Color(0xFFCFFAFE)

@Composable
fun AdminDashboardAnalyticsSection(
    state: AdminDashboardUiState,
    fallbackStudentCount: Int,
    fallbackPatternCount: Int,
    fallbackVocabCount: Int,
    fallbackReadingCount: Int,
    onRetry: () -> Unit,
    onOpenStudents: () -> Unit
) {
    val overview = state.data

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.loading && overview == null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_dashboard_loading_card")
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    Column {
                        Text(
                            text = "Loading live platform analytics...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Gathering student activity, completion, AI usage, subscriptions & health.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (!state.error.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_dashboard_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_dashboard_retry_button")
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        if (overview != null) {
            SystemHealthCard(
                health = overview.systemHealth,
                refreshing = state.refreshing,
                onRefresh = onRetry
            )

            StudentMetricsCard(
                students = overview.students,
                onOpenStudents = onOpenStudents
            )

            CourseCompletionCard(
                course = overview.courseCompletion
            )

            AiUsageStatsCard(
                ai = overview.aiUsage
            )

            SubscriptionStatsCard(
                subs = overview.subscriptions,
                onOpenStudents = onOpenStudents
            )
        } else if (!state.loading) {
            // Minimal safe summary row while retrying on failure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactFallbackMetric("Students", fallbackStudentCount.toString(), Modifier.weight(1f))
                CompactFallbackMetric("Patterns", fallbackPatternCount.toString(), Modifier.weight(1f))
                CompactFallbackMetric("Words", fallbackVocabCount.toString(), Modifier.weight(1f))
                CompactFallbackMetric("Reading", fallbackReadingCount.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompactFallbackMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SystemHealthCard(
    health: AdminDashboardSystemHealth,
    refreshing: Boolean,
    onRefresh: () -> Unit
) {
    val (badgeColor, badgeBg, statusIcon) = when (health.status.lowercase()) {
        "healthy" -> Triple(EmeraldSuccess, EmeraldContainer, Icons.Filled.CheckCircle)
        "degraded" -> Triple(AmberWarning, AmberContainer, Icons.Filled.WarningAmber)
        else -> Triple(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer, Icons.Filled.ErrorOutline)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_system_health_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.HealthAndSafety,
                        contentDescription = "System health",
                        tint = badgeColor
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "System Health",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = badgeBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(statusIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(13.dp))
                                Text(
                                    text = health.status.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor
                                )
                            }
                        }
                    }
                    Text(
                        text = buildString {
                            append(health.summaryLabel)
                            if (health.dbLatencyMs != null) append(" · ${health.dbLatencyMs} ms DB")
                            append(" · Up ${health.uptimeLabel}")
                            if (health.memoryUsedMb > 0) append(" · ${health.memoryUsedMb} MB heap")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !refreshing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("admin_health_refresh_button")
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh dashboard metrics")
                    }
                }
            }

            if (health.checks.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    health.checks.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { check ->
                                HealthServiceMiniTile(check = check, modifier = Modifier.weight(1f))
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthServiceMiniTile(check: AdminHealthCheckItem, modifier: Modifier) {
    val dotColor = when (check.status.lowercase()) {
        "healthy" -> EmeraldSuccess
        "degraded" -> AmberWarning
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = check.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = check.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StudentMetricsCard(
    students: AdminDashboardStudentsMetrics,
    onOpenStudents: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_student_metrics_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCardHeader(
                icon = Icons.Filled.People,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "Students & Daily Activity",
                subtitle = "+${students.newToday} joined today · +${students.newLast7Days} in 7 days",
                actionLabel = "View All",
                onAction = onOpenStudents
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigKpiBox(
                    value = students.totalRegistered.toString(),
                    label = "Total Registered",
                    sublabel = "${students.activeAccounts} active accts",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                BigKpiBox(
                    value = students.activeLast7Days.toString(),
                    label = "Active (7d)",
                    sublabel = "${students.suspended + students.deactivated} restricted",
                    accentColor = EmeraldSuccess,
                    modifier = Modifier.weight(1f)
                )
                BigKpiBox(
                    value = students.dailyActiveUsers.toString(),
                    label = "Daily Active (DAU)",
                    sublabel = "${students.dauPercentOfActive}% of active",
                    accentColor = IndigoAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            if (students.dailyActiveTrend.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "7-Day Daily Active Users Trend",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Today: ${students.dailyActiveUsers} DAU",
                                style = MaterialTheme.typography.labelSmall,
                                color = IndigoAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        val maxVal = (students.dailyActiveTrend.maxOfOrNull { it.activeUsers } ?: 1).coerceAtLeast(1)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            students.dailyActiveTrend.forEach { pt ->
                                val barFraction = if (pt.activeUsers == 0) 0.08f else (pt.activeUsers.toFloat() / maxVal).coerceIn(0.14f, 1f)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Text(
                                        text = pt.activeUsers.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pt.isToday) IndigoAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (pt.isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.55f)
                                            .height((36.dp * barFraction).coerceAtLeast(4.dp))
                                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                            .background(
                                                if (pt.isToday) IndigoAccent
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                            )
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = pt.dayShort,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pt.isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (pt.isToday) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
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
private fun CourseCompletionCard(
    course: AdminDashboardCourseCompletion
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_course_completion_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.School, contentDescription = null, tint = EmeraldSuccess)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Course Completion Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${course.publishedWords} words · ${course.publishedPatterns} patterns · ${course.publishedPassages} passages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = EmeraldContainer
                ) {
                    Text(
                        text = "${course.overallPercent}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (course.overallPercent / 100f).coerceIn(0f, 1f) },
                color = EmeraldSuccess,
                trackColor = EmeraldContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
            )

            course.modules.forEach { module ->
                val accent = when (module.key) {
                    "vocabulary" -> EmeraldSuccess
                    "patterns" -> IndigoAccent
                    else -> CyanAccent
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = module.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${module.percent}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (module.percent / 100f).coerceIn(0f, 1f) },
                        color = accent,
                        trackColor = accent.copy(alpha = 0.14f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                    )
                    Text(
                        text = module.summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quizzes completed: ${course.vocabQuizAttempts + course.readingQuizAttempts}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Reading quiz avg: ${course.avgReadingQuizScore}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = CyanAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun AiUsageStatsCard(
    ai: AdminDashboardAiUsage
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_ai_usage_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IndigoContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = IndigoAccent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Usage Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${ai.provider.replaceFirstChar { it.uppercase() }} · ${ai.model.ifBlank { "default" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (ai.configured) EmeraldContainer else AmberContainer
                ) {
                    Text(
                        text = if (ai.configured) "AI Active" else "Not Set",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (ai.configured) EmeraldSuccess else AmberWarning,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigKpiBox(
                    value = ai.todayInteractions.toString(),
                    label = "AI Calls Today",
                    sublabel = "All AI features",
                    accentColor = IndigoAccent,
                    modifier = Modifier.weight(1f)
                )
                BigKpiBox(
                    value = ai.totalInteractions.toString(),
                    label = "Total AI Calls",
                    sublabel = "${ai.chatMistakesFixedTotal} grammar fixes",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiFeatureUsageRow(
                    title = "AI Speaking Partner Turns",
                    todayCount = ai.speakingTurnsToday,
                    totalCount = ai.speakingTurnsTotal,
                    extra = "Conversational turns"
                )
                AiFeatureUsageRow(
                    title = "Speaking Pronunciation & Fluency Checks",
                    todayCount = ai.speakingEvaluationsToday,
                    totalCount = ai.speakingEvaluationsTotal,
                    extra = "Avg score ${ai.avgSpeakingScore}%"
                )
                AiFeatureUsageRow(
                    title = "Chat AI Grammar Corrections",
                    todayCount = ai.chatGrammarChecksToday,
                    totalCount = ai.chatGrammarChecksTotal,
                    extra = "${ai.chatMistakesFixedTotal} mistakes fixed"
                )
            }
        }
    }
}

@Composable
private fun AiFeatureUsageRow(
    title: String,
    todayCount: Int,
    totalCount: Int,
    extra: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$totalCount total",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "+$todayCount today",
                    style = MaterialTheme.typography.labelSmall,
                    color = IndigoAccent,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionStatsCard(
    subs: AdminDashboardSubscriptions,
    onOpenStudents: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_subscription_stats_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCardHeader(
                icon = Icons.Filled.WorkspacePremium,
                iconBg = AmberContainer,
                iconTint = AmberWarning,
                title = "Subscription Statistics",
                subtitle = "${subs.paidConversionPercent}% paid conversion · ${subs.premiumOrTrialCount} paid or trial",
                actionLabel = "Manage",
                onAction = onOpenStudents
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigKpiBox(
                    value = subs.paidActiveCount.toString(),
                    label = "Paid Active",
                    sublabel = "${subs.paidConversionPercent}% conv.",
                    accentColor = AmberWarning,
                    modifier = Modifier.weight(1f)
                )
                BigKpiBox(
                    value = subs.trialCount.toString(),
                    label = "Trial",
                    sublabel = "${subs.expiringSoonCount} exp. 7d",
                    accentColor = CyanAccent,
                    modifier = Modifier.weight(1f)
                )
                BigKpiBox(
                    value = subs.freeCount.toString(),
                    label = "Free",
                    sublabel = "${subs.expiredCount + subs.cancelledCount} lapsed",
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            // Segmented horizontal distribution bar
            if (subs.totalStudents > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val segments = listOf(
                        subs.paidActiveCount to EmeraldSuccess,
                        subs.trialCount to CyanAccent,
                        subs.freeCount to MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        subs.expiredCount to AmberWarning,
                        subs.cancelledCount to MaterialTheme.colorScheme.error
                    )
                    segments.forEach { (count, color) ->
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(count.toFloat())
                                    .fillMaxHeight()
                                    .background(color)
                            )
                        }
                    }
                }
            }

            if (subs.byPlan.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    subs.byPlan.forEach { plan ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ) {
                            Text(
                                text = "${plan.label}: ${plan.count} (${plan.percent}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCardHeader(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(
            onClick = onAction,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Text(actionLabel, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BigKpiBox(
    value: String,
    label: String,
    sublabel: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
