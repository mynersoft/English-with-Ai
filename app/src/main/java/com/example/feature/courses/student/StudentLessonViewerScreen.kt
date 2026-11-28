package com.example.feature.courses.student

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.feature.courses.ui.LessonContentRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentLessonViewerScreen(
    state: StudentLessonViewerUiState,
    isStartingCheckout: Boolean = false,
    onBack: () -> Unit,
    onToggleComplete: (Boolean) -> Unit,
    onVideoProgress: (blockId: String, positionSeconds: Int, durationSeconds: Int) -> Unit,
    onNavigateLesson: (String) -> Unit,
    onPurchaseCourse: (() -> Unit)? = null
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.lesson?.title ?: "Lesson",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        state.course?.title?.let { courseTitle ->
                            Text(
                                text = courseTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("student_lesson_viewer_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to course")
                    }
                }
            )
        }
    ) { padding ->
        val lesson = state.lesson
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.isLockedError -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 420.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Premium Lesson",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.error ?: "Upgrade to Premium to unlock this lesson.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        if (onPurchaseCourse != null) {
                            Button(
                                onClick = onPurchaseCourse,
                                enabled = !isStartingCheckout,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("lesson_locked_buy_course_button")
                            ) {
                                if (isStartingCheckout) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Opening SSLCOMMERZ...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.ShoppingCart,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Buy Course with SSLCOMMERZ")
                                }
                            }
                        }
                        OutlinedButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Text("Back to Course")
                        }
                    }
                }
            }

            lesson == null -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(state.error ?: "Lesson unavailable.", textAlign = TextAlign.Center)
                        Button(onClick = onBack) { Text("Back to Course") }
                    }
                }
            }

            else -> {
                LessonContentRenderer(
                    lesson = lesson,
                    moduleTitle = state.module?.title,
                    progress = state.progress,
                    previousLesson = state.previousLesson,
                    nextLesson = state.nextLesson,
                    isPreview = false,
                    isSavingProgress = state.isSavingProgress,
                    onToggleComplete = onToggleComplete,
                    onVideoProgress = onVideoProgress,
                    onNavigateLesson = onNavigateLesson,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}
