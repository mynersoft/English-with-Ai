package com.example.feature.courses.student

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.CourseDomainUtils
import com.example.domain.model.CourseSummary
import com.example.domain.model.EnrollmentStatus
import com.example.ui.theme.LocalIsDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseCatalogScreen(
    state: StudentCourseCatalogUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onLevelChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onOpenCourse: (String) -> Unit,
    onPageChange: (Int) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Courses", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Structured video, PDF & guided lessons",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("courses_catalog_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("courses_catalog_refresh")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh courses")
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
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search courses...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_courses_search")
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ALL" to "All Levels", "Beginner" to "Beginner", "Intermediate" to "Intermediate", "Advanced" to "Advanced")
                            .forEach { (key, label) ->
                                FilterChip(
                                    selected = state.levelFilter == key,
                                    onClick = { onLevelChange(key) },
                                    label = { Text(label) }
                                )
                            }
                    }
                }

                if (state.categories.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.categoryFilter.isBlank(),
                                onClick = { onCategoryChange("") },
                                label = { Text("All Categories") }
                            )
                            state.categories.forEach { cat ->
                                FilterChip(
                                    selected = state.categoryFilter.equals(cat, ignoreCase = true),
                                    onClick = { onCategoryChange(cat) },
                                    label = { Text(cat) }
                                )
                            }
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

                if (state.isLoading && state.courses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.courses.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No published courses available",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Try clearing filters or check back soon for new lessons.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(state.courses, key = { it.id }) { course ->
                        StudentCourseCatalogCard(
                            course = course,
                            onClick = { onOpenCourse(course.id) }
                        )
                    }

                    if (state.pagination.totalPages > 1) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onPageChange(state.pagination.page - 1) },
                                    enabled = state.pagination.page > 1
                                ) { Text("Previous") }
                                Text("Page ${state.pagination.page} of ${state.pagination.totalPages}")
                                OutlinedButton(
                                    onClick = { onPageChange(state.pagination.page + 1) },
                                    enabled = state.pagination.page < state.pagination.totalPages
                                ) { Text("Next") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentCourseCatalogCard(
    course: CourseSummary,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("student_course_card_${course.id}")
    ) {
        Column {
            if (course.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = course.thumbnailUrl,
                    contentDescription = course.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BadgePill(
                        text = course.level.label,
                        bg = MaterialTheme.colorScheme.primaryContainer,
                        fg = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    BadgePill(
                        text = course.category,
                        bg = MaterialTheme.colorScheme.secondaryContainer,
                        fg = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    val isDark = LocalIsDarkTheme.current
                    if (course.isPremium) {
                        val badgeLabel = when {
                            course.isPurchased -> "Purchased"
                            course.hasAccess -> "Unlocked"
                            else -> "Buy · ${CourseDomainUtils.formatCoursePrice(course.price)}"
                        }
                        BadgePill(
                            text = badgeLabel,
                            bg = if (course.hasAccess) {
                                if (isDark) Color(0xFF133A29) else Color(0xFFD1FAE5)
                            } else {
                                if (isDark) Color(0xFF3E2B0D) else Color(0xFFFEF3C7)
                            },
                            fg = if (course.hasAccess) {
                                if (isDark) Color(0xFF34D399) else Color(0xFF065F46)
                            } else {
                                if (isDark) Color(0xFFFBBF24) else Color(0xFF92400E)
                            },
                            icon = Icons.Filled.WorkspacePremium
                        )
                    } else {
                        BadgePill(
                            text = "Free",
                            bg = if (isDark) Color(0xFF133A29) else Color(0xFFD1FAE5),
                            fg = if (isDark) Color(0xFF34D399) else Color(0xFF065F46)
                        )
                    }
                    if (course.isOfflineAvailable) {
                        Icon(
                            Icons.Filled.OfflinePin,
                            contentDescription = "Available offline",
                            tint = if (isDark) Color(0xFF34D399) else Color(0xFF0E6B3B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (course.subtitle.isNotBlank()) {
                    Text(
                        text = course.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${course.durationDays} days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = "${course.totalModules} modules · ${course.totalLessons} lessons",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = course.enrollmentStatus.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (course.enrollmentStatus) {
                            EnrollmentStatus.COMPLETED -> if (LocalIsDarkTheme.current) Color(0xFF34D399) else Color(0xFF065F46)
                            EnrollmentStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                            EnrollmentStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                if (course.totalLessons > 0 && course.enrollmentStatus != EnrollmentStatus.NOT_STARTED) {
                    LinearProgressIndicator(
                        progress = { (course.completionPercentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun BadgePill(
    text: String,
    bg: Color,
    fg: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
        }
    }
}
