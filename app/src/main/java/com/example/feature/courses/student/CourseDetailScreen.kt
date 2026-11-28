package com.example.feature.courses.student

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.LocalIsDarkTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.EnrollmentStatus
import com.example.domain.model.LessonSummary
import com.example.domain.model.ModuleWithLessons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    state: StudentCourseDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDownloadOffline: () -> Unit,
    onOpenLesson: (String) -> Unit
) {
    var lockedLessonDialog by remember { mutableStateOf<LessonSummary?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.course?.title ?: "Course Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("student_course_detail_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh course")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            val course = state.course
            when {
                state.isLoading && course == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                course == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.error ?: "Course could not be loaded.")
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (state.isLoading || state.isDownloadingOffline) {
                            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                        }

                        if (!state.message.isNullOrBlank()) {
                            item {
                                val isDark = LocalIsDarkTheme.current
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) Color(0xFF133A29) else Color(0xFFD1FAE5),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.message,
                                        color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        if (!state.error.isNullOrBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.error,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        // Course Hero Card
                        item(key = "course_hero") {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    if (course.thumbnailUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = course.thumbnailUrl,
                                            contentDescription = course.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(190.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            DetailChip(
                                                text = course.level.label,
                                                bg = MaterialTheme.colorScheme.primaryContainer,
                                                fg = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            DetailChip(
                                                text = "${course.durationDays} days",
                                                bg = MaterialTheme.colorScheme.secondaryContainer,
                                                fg = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            val isDark = LocalIsDarkTheme.current
                                            DetailChip(
                                                text = course.enrollmentStatus.label,
                                                bg = if (course.enrollmentStatus == EnrollmentStatus.COMPLETED) {
                                                    if (isDark) Color(0xFF133A29) else Color(0xFFD1FAE5)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                fg = if (course.enrollmentStatus == EnrollmentStatus.COMPLETED) {
                                                    if (isDark) Color(0xFF34D399) else Color(0xFF065F46)
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }

                                        Text(
                                            text = course.title,
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.ExtraBold
                                        )

                                        if (course.subtitle.isNotBlank()) {
                                            Text(
                                                text = course.subtitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        if (course.description.isNotBlank()) {
                                            Text(
                                                text = course.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Text(
                                            text = "${course.totalModules} modules · ${course.totalLessons} lessons · ${course.completedLessons}/${course.totalLessons} completed (${course.completionPercentage}%)",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        LinearProgressIndicator(
                                            progress = { (course.completionPercentage / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )

                                        val startLessonId = course.continueLessonId
                                            ?: state.modules.flatMap { it.lessons }.firstOrNull { !it.isLocked }?.id

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { startLessonId?.let(onOpenLesson) },
                                                enabled = startLessonId != null,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("course_start_continue_button")
                                            ) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = when (course.enrollmentStatus) {
                                                        EnrollmentStatus.NOT_STARTED -> "Start Learning"
                                                        EnrollmentStatus.IN_PROGRESS -> "Continue Learning"
                                                        EnrollmentStatus.COMPLETED -> "Review Course"
                                                    },
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = onDownloadOffline,
                                                enabled = !state.isDownloadingOffline,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("course_save_offline_button")
                                            ) {
                                                Icon(
                                                    imageVector = if (course.isOfflineAvailable) {
                                                        Icons.Filled.OfflinePin
                                                    } else {
                                                        Icons.Filled.DownloadForOffline
                                                    },
                                                    contentDescription = "Save course offline",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Modules & Lessons Hierarchy
                        items(state.modules, key = { it.id }) { mod ->
                            StudentModuleCard(
                                module = mod,
                                onLessonClick = { lesson ->
                                    if (lesson.isLocked) {
                                        lockedLessonDialog = lesson
                                    } else {
                                        onOpenLesson(lesson.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    lockedLessonDialog?.let { locked ->
        AlertDialog(
            onDismissRequest = { lockedLessonDialog = null },
            title = { Text("Premium Lesson Locked", fontWeight = FontWeight.Bold) },
            text = {
                Text("\"${locked.title}\" requires an active Premium plan. Free preview lessons in this course remain open.")
            },
            confirmButton = {
                TextButton(onClick = { lockedLessonDialog = null }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun StudentModuleCard(
    module: ModuleWithLessons,
    onLessonClick: (LessonSummary) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_module_card_${module.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Module ${module.order}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = module.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (module.lessons.isEmpty()) {
                Text(
                    text = "No lessons published in this module yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                module.lessons.forEach { lesson ->
                    StudentLessonListItem(
                        lesson = lesson,
                        onClick = { onLessonClick(lesson) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentLessonListItem(
    lesson: LessonSummary,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val isCompleted = lesson.progress?.isCompleted == true
    val progressPct = lesson.progress?.progressPercentage ?: 0

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (lesson.isLocked) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("student_lesson_row_${lesson.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            lesson.isLocked -> if (isDark) Color(0xFF3E2B0D) else Color(0xFFFEF3C7)
                            isCompleted -> if (isDark) Color(0xFF133A29) else Color(0xFFD1FAE5)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        lesson.isLocked -> Icons.Filled.Lock
                        isCompleted -> Icons.Filled.CheckCircle
                        else -> Icons.Filled.PlayCircleFilled
                    },
                    contentDescription = when {
                        lesson.isLocked -> "Locked premium lesson"
                        isCompleted -> "Completed lesson"
                        else -> "Open lesson"
                    },
                    tint = when {
                        lesson.isLocked -> if (isDark) Color(0xFFFBBF24) else Color(0xFF92400E)
                        isCompleted -> if (isDark) Color(0xFF34D399) else Color(0xFF065F46)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${lesson.order}. ${lesson.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${lesson.estimatedDurationMinutes}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (lesson.isPremium) "Premium" else "Free",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lesson.isPremium) {
                            if (isDark) Color(0xFFFBBF24) else Color(0xFF92400E)
                        } else {
                            if (isDark) Color(0xFF34D399) else Color(0xFF065F46)
                        }
                    )
                    if (!lesson.isLocked && progressPct in 1..99) {
                        Text(
                            text = "$progressPct% watched",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
private fun DetailChip(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
