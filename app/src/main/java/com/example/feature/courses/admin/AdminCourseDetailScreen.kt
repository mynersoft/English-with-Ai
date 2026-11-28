package com.example.feature.courses.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Unpublished
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.domain.model.CourseSummary
import com.example.domain.model.LessonSummary
import com.example.domain.model.ModuleWithLessons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCourseDetailScreen(
    state: AdminCourseDetailUiState,
    onBack: () -> Unit,
    onPreviewCourse: () -> Unit,
    onDismissPreviewCourse: () -> Unit,
    onSaveModule: (courseId: String, moduleId: String?, title: String, desc: String, isPublished: Boolean, onDone: () -> Unit) -> Unit,
    onToggleModulePublish: (moduleId: String, isPublished: Boolean) -> Unit,
    onDeleteModule: (moduleId: String) -> Unit,
    onMoveModule: (fromIndex: Int, toIndex: Int) -> Unit,
    onAddLesson: (courseId: String, moduleId: String, moduleTitle: String) -> Unit,
    onEditLesson: (lessonId: String, moduleTitle: String) -> Unit,
    onPreviewLesson: (lessonId: String, moduleTitle: String) -> Unit,
    onDuplicateLesson: (lessonId: String) -> Unit,
    onToggleLessonPublish: (lessonId: String, isPublished: Boolean) -> Unit,
    onDeleteLesson: (lessonId: String) -> Unit,
    onMoveLesson: (moduleId: String, fromIndex: Int, toIndex: Int) -> Unit
) {
    var showModuleDialog by remember { mutableStateOf(false) }
    var editingModule by remember { mutableStateOf<ModuleWithLessons?>(null) }
    var confirmDeleteModule by remember { mutableStateOf<ModuleWithLessons?>(null) }
    var confirmDeleteLesson by remember { mutableStateOf<LessonSummary?>(null) }

    val course = state.course

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = course?.title ?: "Course Details",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                        if (course != null) {
                            Text(
                                text = "${course.totalModules} modules · ${course.totalLessons} lessons",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_course_detail_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to courses")
                    }
                },
                actions = {
                    if (course != null) {
                        FilledTonalButton(
                            onClick = onPreviewCourse,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("admin_preview_course_button")
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Preview")
                        }
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
            if (state.isLoading && course == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (course == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Course not found.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (state.isBusy || state.isLoading) {
                        item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
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

                    item {
                        CourseOverviewHeaderCard(
                            course = course,
                            onAddModule = {
                                editingModule = null
                                showModuleDialog = true
                            }
                        )
                    }

                    if (state.modules.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
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
                                    Text("No modules yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Add modules to organize lessons cleanly inside this course.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = {
                                        editingModule = null
                                        showModuleDialog = true
                                    }) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add First Module")
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(state.modules, key = { _, m -> m.id }) { modIdx, module ->
                            AdminModuleCard(
                                module = module,
                                isFirst = modIdx == 0,
                                isLast = modIdx == state.modules.lastIndex,
                                onMoveUp = { onMoveModule(modIdx, modIdx - 1) },
                                onMoveDown = { onMoveModule(modIdx, modIdx + 1) },
                                onEditModule = {
                                    editingModule = module
                                    showModuleDialog = true
                                },
                                onTogglePublish = { onToggleModulePublish(module.id, !module.isPublished) },
                                onDeleteModule = { confirmDeleteModule = module },
                                onAddLesson = { onAddLesson(course.id, module.id, module.title) },
                                onEditLesson = { lesson -> onEditLesson(lesson.id, module.title) },
                                onPreviewLesson = { lesson -> onPreviewLesson(lesson.id, module.title) },
                                onDuplicateLesson = { lesson -> onDuplicateLesson(lesson.id) },
                                onToggleLessonPublish = { lesson -> onToggleLessonPublish(lesson.id, !lesson.isPublished) },
                                onDeleteLesson = { lesson -> confirmDeleteLesson = lesson },
                                onMoveLesson = { from, to -> onMoveLesson(module.id, from, to) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showModuleDialog && course != null) {
        ModuleEditorDialog(
            initial = editingModule,
            isBusy = state.isBusy,
            onDismiss = { showModuleDialog = false },
            onSave = { title, desc, published ->
                onSaveModule(course.id, editingModule?.id, title, desc, published) {
                    showModuleDialog = false
                }
            }
        )
    }

    confirmDeleteModule?.let { mod ->
        AlertDialog(
            onDismissRequest = { confirmDeleteModule = null },
            title = { Text("Delete module?") },
            text = { Text("Deleting \"${mod.title}\" will also remove all ${mod.lessons.size} lessons inside it.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteModule(mod.id)
                    confirmDeleteModule = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteModule = null }) { Text("Cancel") }
            }
        )
    }

    confirmDeleteLesson?.let { lesson ->
        AlertDialog(
            onDismissRequest = { confirmDeleteLesson = null },
            title = { Text("Delete lesson?") },
            text = { Text("\"${lesson.title}\" and its student progress records will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteLesson(lesson.id)
                    confirmDeleteLesson = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteLesson = null }) { Text("Cancel") }
            }
        )
    }

    if (state.isPreviewCourseOpen && course != null) {
        AdminCoursePreviewDialog(
            course = course,
            modules = state.modules,
            onDismiss = onDismissPreviewCourse,
            onPreviewLesson = { lesson, modTitle ->
                onDismissPreviewCourse()
                onPreviewLesson(lesson.id, modTitle)
            }
        )
    }
}

@Composable
private fun CourseOverviewHeaderCard(
    course: CourseSummary,
    onAddModule: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (course.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = course.thumbnailUrl,
                        contentDescription = course.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${course.level.label} · ${course.category} · ${course.durationDays} days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (course.subtitle.isNotBlank()) {
                        Text(course.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = if (course.isPublished) "Published to Students" else "Draft (Admin Only)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (course.isPublished) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = onAddModule,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("admin_add_module_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Module")
            }
        }
    }
}

@Composable
private fun AdminModuleCard(
    module: ModuleWithLessons,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEditModule: () -> Unit,
    onTogglePublish: () -> Unit,
    onDeleteModule: () -> Unit,
    onAddLesson: () -> Unit,
    onEditLesson: (LessonSummary) -> Unit,
    onPreviewLesson: (LessonSummary) -> Unit,
    onDuplicateLesson: (LessonSummary) -> Unit,
    onToggleLessonPublish: (LessonSummary) -> Unit,
    onDeleteLesson: (LessonSummary) -> Unit,
    onMoveLesson: (fromIndex: Int, toIndex: Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_module_card_${module.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "M${module.order}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = (if (module.isPublished) "Published" else "Draft") +
                            " · ${module.lessons.size} lessons",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (module.isPublished) Color(0xFF34D399) else Color(0xFFFBBF24)
                    )
                }
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move module up")
                }
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move module down")
                }
            }

            if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onAddLesson,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("admin_add_lesson_${module.id}")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Lesson")
                }
                IconButton(onClick = onEditModule, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit module")
                }
                IconButton(onClick = onTogglePublish, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(
                        if (module.isPublished) Icons.Filled.Unpublished else Icons.Filled.Publish,
                        contentDescription = if (module.isPublished) "Unpublish module" else "Publish module",
                        tint = if (module.isPublished) Color(0xFFFBBF24) else Color(0xFF34D399)
                    )
                }
                IconButton(onClick = onDeleteModule, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete module", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (module.lessons.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                module.lessons.forEachIndexed { lessonIdx, lesson ->
                    AdminLessonRow(
                        lesson = lesson,
                        isFirst = lessonIdx == 0,
                        isLast = lessonIdx == module.lessons.lastIndex,
                        onMoveUp = { onMoveLesson(lessonIdx, lessonIdx - 1) },
                        onMoveDown = { onMoveLesson(lessonIdx, lessonIdx + 1) },
                        onEdit = { onEditLesson(lesson) },
                        onPreview = { onPreviewLesson(lesson) },
                        onDuplicate = { onDuplicateLesson(lesson) },
                        onTogglePublish = { onToggleLessonPublish(lesson) },
                        onDelete = { onDeleteLesson(lesson) }
                    )
                }
            } else {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No lessons yet — tap \"Add Lesson\" above to add Video, PDF, Text, and Image blocks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminLessonRow(
    lesson: LessonSummary,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    onDuplicate: () -> Unit,
    onTogglePublish: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${lesson.order}.",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = (if (lesson.isPublished) "Published" else "Draft") +
                            " · " + (if (lesson.isPremium) "Premium" else "Free") +
                            " · ${lesson.contentCount} blocks · ${lesson.estimatedDurationMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move lesson up", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move lesson down", modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_edit_lesson_${lesson.id}")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                    FilledTonalButton(
                        onClick = onPreview,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_preview_lesson_${lesson.id}")
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Preview")
                    }
                }
                Row {
                    IconButton(onClick = onDuplicate, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate lesson", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onTogglePublish, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            if (lesson.isPublished) Icons.Filled.Unpublished else Icons.Filled.Publish,
                            contentDescription = if (lesson.isPublished) "Unpublish lesson" else "Publish lesson",
                            tint = if (lesson.isPublished) Color(0xFFFBBF24) else Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete lesson",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleEditorDialog(
    initial: ModuleWithLessons?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, isPublished: Boolean) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var desc by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var published by remember(initial) { mutableStateOf(initial?.isPublished ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Module" else "Edit Module", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Module Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("module_title_input")
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Published")
                    Switch(checked = published, onCheckedChange = { published = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.trim().length < 2) {
                        error = "Module title must be at least 2 characters."
                        return@Button
                    }
                    onSave(title, desc, published)
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_module_dialog_button")
            ) {
                Text(if (isBusy) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminCoursePreviewDialog(
    course: CourseSummary,
    modules: List<ModuleWithLessons>,
    onDismiss: () -> Unit,
    onPreviewLesson: (LessonSummary, String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text("Course Preview (Student View)", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close preview")
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (course.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = course.thumbnailUrl,
                                        contentDescription = course.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                Text(course.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                if (course.subtitle.isNotBlank()) {
                                    Text(course.subtitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (course.description.isNotBlank()) {
                                    Text(course.description, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    text = "${course.level.label} · ${course.durationDays} days · ${modules.size} modules · ${modules.sumOf { it.lessons.size }} lessons",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    modules.forEach { mod ->
                        item(key = "preview_mod_${mod.id}") {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "${mod.order}. ${mod.title}" + if (!mod.isPublished) " (Draft)" else "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    mod.lessons.forEach { lesson ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${lesson.order}. ${lesson.title}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "${lesson.estimatedDurationMinutes}m · ${if (lesson.isPremium) "Premium" else "Free"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                TextButton(onClick = { onPreviewLesson(lesson, mod.title) }) {
                                                    Text("Open Preview")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
