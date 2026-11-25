package com.example.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.domain.model.ReadingReportSummary

@Composable
fun ReadingReportSection(
    report: ReadingReportSummary,
    onOpenReading: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emerald = Color(0xFF0E6B3B)
    val emeraldSoft = Color(0xFFE6F4EC)
    val textDark = Color(0xFF111827)
    val textMuted = Color(0xFF6B7280)
    val border = Color(0xFFE5E7EB)

    val goalRatio = if (report.dailyGoalMinutes > 0) {
        (report.todayMinutes.toFloat() / report.dailyGoalMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .testTag("reading_report_section"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Reading Report",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = textDark
        )
        Text(
            text = "${report.completedPassages} of ${report.totalPassages} passages completed (${report.completionPercent}%)",
            style = MaterialTheme.typography.bodySmall,
            color = textMuted
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF9FBF9),
            border = BorderStroke(1.dp, border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(emeraldSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoStories,
                                contentDescription = null,
                                tint = emerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Today's Reading Goal",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = textDark
                            )
                            Text(
                                text = "${report.todayMinutes} / ${report.dailyGoalMinutes} min today",
                                style = MaterialTheme.typography.bodySmall,
                                color = textMuted
                            )
                        }
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = emeraldSoft) {
                        Text(
                            text = "${(goalRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = emerald,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { goalRatio },
                    color = emerald,
                    trackColor = border,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportMetricBox(
                        icon = Icons.Filled.CheckCircle,
                        tint = emerald,
                        value = "${report.completedPassages}/${report.totalPassages}",
                        label = "Completed",
                        modifier = Modifier.weight(1f)
                    )
                    ReportMetricBox(
                        icon = Icons.Filled.MenuBook,
                        tint = Color(0xFFD97706),
                        value = "${report.inProgressPassages}",
                        label = "In Progress",
                        modifier = Modifier.weight(1f)
                    )
                    ReportMetricBox(
                        icon = Icons.Filled.Schedule,
                        tint = Color(0xFF2563EB),
                        value = "${report.totalMinutes}m",
                        label = "Total Time",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReportMetricBox(
                        icon = Icons.Filled.Quiz,
                        tint = Color(0xFF7C3AED),
                        value = if (report.quizAttemptsCount > 0) "${report.averageQuizScore}%" else "--",
                        label = "Quiz Avg (${report.quizAttemptsCount})",
                        modifier = Modifier.weight(1f)
                    )
                    ReportMetricBox(
                        icon = Icons.Filled.RecordVoiceOver,
                        tint = Color(0xFF0F766E),
                        value = if (report.readAloudCount > 0) "${report.averageReadAloudScore}%" else "--",
                        label = "Text Match (${report.readAloudCount})",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (report.recentAttempts.isNotEmpty()) {
                    HorizontalDivider(color = border)
                    Text(
                        text = "Recent Reading Results",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = textDark
                    )
                    report.recentAttempts.forEach { att ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = att.passageTitle.ifBlank { "Reading Passage" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${if (att.mode == "read_aloud") "Text Matching" else "Comprehension"} · ${att.level}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textMuted
                                )
                            }
                            Text(
                                text = "${att.scorePercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = emerald
                            )
                        }
                    }
                }

                Button(
                    onClick = onOpenReading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = emerald,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("reading_report_open_button")
                ) {
                    Text("Open Reading Practice")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportMetricBox(
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEAECEF)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
