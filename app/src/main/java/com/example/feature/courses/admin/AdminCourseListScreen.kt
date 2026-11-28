package com.example.feature.courses.admin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Unpublished
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.domain.model.CourseLevel
import com.example.domain.model.CourseSummary

@Composable
fun AdminCoursesPanel(
    state: AdminCourseListUiState,
    openCreateTrigger: Int = 0,
    onSearchChange: (String) -> Unit,
    onLevelFilterChange: (String) -> Unit,
    onPublishedFilterChange: (String) -> Unit,
    onPageChange: (Int) -> Unit,
    onOpenCourseDetail: (String) -> Unit,
    onTogglePublish: (String, Boolean) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onUploadThumbnail: (android.net.Uri, (String, String) -> Unit) -> Unit,
    onUploadMedia: (android.net.Uri, String, (String, String, Int) -> Unit) -> Unit = { _, _, _ -> },
    onSaveCourse: (
        courseId: String?,
        title: String,
        subtitle: String,
        description: String,
        thumbnailUrl: String,
        thumbnailPublicId: String,
        level: CourseLevel,
        category: String,
        durationDays: Int,
        isPremium: Boolean,
        price: Double,
        isPublished: Boolean,
        initialModuleTitle: String,
        initialLessonTitle: String,
        initialVideoUrl: String,
        initialVideoPublicId: String,
        initialVideoDuration: Int,
        initialPdfUrl: String,
        initialPdfPublicId: String,
        initialTextContent: String,
        openModulesAfterSave: Boolean,
        onDone: () -> Unit
    ) -> Unit,
    onClearDialogError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var editingCourse by remember { mutableStateOf<CourseSummary?>(null) }
    var showCourseDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<CourseSummary?>(null) }

    LaunchedEffect(openCreateTrigger) {
        if (openCreateTrigger > 0) {
            onClearDialogError()
            editingCourse = null
            showCourseDialog = true
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Course Management",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.pagination.total} courses · Modules & flexible lessons",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            onClearDialogError()
                            editingCourse = null
                            showCourseDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_create_course_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Course")
                    }
                }
            }

            if (state.isBusy) {
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
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search courses by title or category...") },
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
                        .testTag("admin_courses_search_input")
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
                                onClick = { onLevelFilterChange(key) },
                                label = { Text(label) }
                            )
                        }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("all" to "All Status", "published" to "Published", "draft" to "Drafts")
                        .forEach { (key, label) ->
                            FilterChip(
                                selected = state.publishedFilter == key,
                                onClick = { onPublishedFilterChange(key) },
                                label = { Text(label) }
                            )
                        }
                }
            }

            if (state.isLoading && state.courses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.courses.isEmpty()) {
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
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text("No courses found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Create your first structured course, then add Modules and Lessons with Video, PDF, Text & Image content.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    onClearDialogError()
                                    editingCourse = null
                                    showCourseDialog = true
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Create First Course")
                            }
                        }
                    }
                }
            } else {
                items(state.courses, key = { it.id }) { course ->
                    AdminCourseCard(
                        course = course,
                        onOpenDetail = { onOpenCourseDetail(course.id) },
                        onEdit = {
                            onClearDialogError()
                            editingCourse = course
                            showCourseDialog = true
                        },
                        onTogglePublish = { onTogglePublish(course.id, !course.isPublished) },
                        onDelete = { confirmDelete = course }
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

    if (showCourseDialog) {
        AdminCourseEditorDialog(
            initialCourse = editingCourse,
            isBusy = state.isBusy,
            dialogError = state.dialogError,
            onUploadThumbnail = onUploadThumbnail,
            onUploadMedia = onUploadMedia,
            onDismiss = {
                onClearDialogError()
                showCourseDialog = false
            },
            onOpenExistingModules = { existingId ->
                onClearDialogError()
                showCourseDialog = false
                onOpenCourseDetail(existingId)
            },
            onSave = { title, subtitle, desc, thumbUrl, thumbPubId, level, cat, days, premium, price, published, initMod, initLesson, initVideoUrl, initVideoPubId, initVideoDur, initPdfUrl, initPdfPubId, initText, openModules ->
                onSaveCourse(
                    editingCourse?.id,
                    title,
                    subtitle,
                    desc,
                    thumbUrl,
                    thumbPubId,
                    level,
                    cat,
                    days,
                    premium,
                    price,
                    published,
                    initMod,
                    initLesson,
                    initVideoUrl,
                    initVideoPubId,
                    initVideoDur,
                    initPdfUrl,
                    initPdfPubId,
                    initText,
                    openModules
                ) {
                    showCourseDialog = false
                }
            }
        )
    }

    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete course?") },
            text = { Text("\"${c.title}\" and all its modules, lessons, and student progress will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCourse(c.id)
                        confirmDelete = null
                    },
                    modifier = Modifier.testTag("admin_confirm_delete_course")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminCourseCard(
    course: CourseSummary,
    onOpenDetail: () -> Unit,
    onEdit: () -> Unit,
    onTogglePublish: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .testTag("admin_course_card_${course.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (course.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = course.thumbnailUrl,
                            contentDescription = course.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusChip(
                            text = if (course.isPublished) "Published" else "Draft",
                            bg = if (course.isPublished) Color(0xFF133A29) else Color(0xFF3E2B0D),
                            fg = if (course.isPublished) Color(0xFF34D399) else Color(0xFFFBBF24)
                        )
                        StatusChip(
                            text = course.level.label,
                            bg = MaterialTheme.colorScheme.secondaryContainer,
                            fg = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        StatusChip(
                            text = if (course.isPremium) {
                                if (course.price > 0) "৳${course.price.toInt()}" else "Premium"
                            } else {
                                "Free"
                            },
                            bg = MaterialTheme.colorScheme.tertiaryContainer,
                            fg = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${course.category} · ${course.totalModules} modules · ${course.totalLessons} lessons · ${course.durationDays}d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onOpenDetail,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("admin_manage_course_${course.id}")
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Modules & Lessons", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("admin_edit_course_${course.id}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit course")
                }

                IconButton(
                    onClick = onTogglePublish,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("admin_publish_course_${course.id}")
                ) {
                    Icon(
                        imageVector = if (course.isPublished) Icons.Filled.Unpublished else Icons.Filled.Publish,
                        contentDescription = if (course.isPublished) "Unpublish course" else "Publish course",
                        tint = if (course.isPublished) Color(0xFFFBBF24) else Color(0xFF34D399)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("admin_delete_course_${course.id}")
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete course",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminCourseEditorDialog(
    initialCourse: CourseSummary?,
    isBusy: Boolean,
    dialogError: String? = null,
    onUploadThumbnail: (android.net.Uri, (String, String) -> Unit) -> Unit,
    onUploadMedia: (android.net.Uri, String, (String, String, Int) -> Unit) -> Unit = { _, _, _ -> },
    onDismiss: () -> Unit,
    onOpenExistingModules: (String) -> Unit = {},
    onSave: (
        title: String,
        subtitle: String,
        description: String,
        thumbnailUrl: String,
        thumbnailPublicId: String,
        level: CourseLevel,
        category: String,
        durationDays: Int,
        isPremium: Boolean,
        price: Double,
        isPublished: Boolean,
        initialModuleTitle: String,
        initialLessonTitle: String,
        initialVideoUrl: String,
        initialVideoPublicId: String,
        initialVideoDuration: Int,
        initialPdfUrl: String,
        initialPdfPublicId: String,
        initialTextContent: String,
        openModulesAfterSave: Boolean
    ) -> Unit
) {
    var title by remember(initialCourse) { mutableStateOf(initialCourse?.title.orEmpty()) }
    var subtitle by remember(initialCourse) { mutableStateOf(initialCourse?.subtitle.orEmpty()) }
    var description by remember(initialCourse) { mutableStateOf(initialCourse?.description.orEmpty()) }
    var thumbnailUrl by remember(initialCourse) { mutableStateOf(initialCourse?.thumbnailUrl.orEmpty()) }
    var thumbnailPublicId by remember(initialCourse) { mutableStateOf(initialCourse?.thumbnailPublicId.orEmpty()) }
    var level by remember(initialCourse) { mutableStateOf(initialCourse?.level ?: CourseLevel.BEGINNER) }
    var category by remember(initialCourse) { mutableStateOf(initialCourse?.category ?: "Spoken English") }
    var durationDays by remember(initialCourse) { mutableStateOf((initialCourse?.durationDays ?: 30).toString()) }
    var isPremium by remember(initialCourse) { mutableStateOf(initialCourse?.isPremium ?: false) }
    var price by remember(initialCourse) { mutableStateOf((initialCourse?.price ?: 0.0).toString()) }
    var isPublished by remember(initialCourse) { mutableStateOf(initialCourse?.isPublished ?: false) }
    var firstModuleTitle by remember(initialCourse) { mutableStateOf("") }
    var firstLessonTitle by remember(initialCourse) { mutableStateOf("") }
    var videoUrl by remember(initialCourse) { mutableStateOf("") }
    var videoPublicId by remember(initialCourse) { mutableStateOf("") }
    var videoDuration by remember(initialCourse) { mutableStateOf(0) }
    var pdfUrl by remember(initialCourse) { mutableStateOf("") }
    var pdfPublicId by remember(initialCourse) { mutableStateOf("") }
    var textContent by remember(initialCourse) { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadThumbnail(uri) { url, publicId ->
                thumbnailUrl = url
                thumbnailPublicId = publicId
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadMedia(uri, "video") { uploadedUrl, uploadedPublicId, uploadedDur ->
                videoUrl = uploadedUrl
                videoPublicId = uploadedPublicId
                videoDuration = uploadedDur
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onUploadMedia(uri, "pdf") { uploadedUrl, uploadedPublicId, _ ->
                pdfUrl = uploadedUrl
                pdfPublicId = uploadedPublicId
            }
        }
    }

    fun submit(openModules: Boolean) {
        if (title.trim().length < 2) {
            validationError = "Course title must be at least 2 characters."
            return
        }
        validationError = null
        onSave(
            title,
            subtitle,
            description,
            thumbnailUrl,
            thumbnailPublicId,
            level,
            category,
            durationDays.toIntOrNull()?.coerceIn(1, 365) ?: 30,
            isPremium,
            if (isPremium) price.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0 else 0.0,
            isPublished,
            firstModuleTitle,
            firstLessonTitle,
            videoUrl,
            videoPublicId,
            videoDuration,
            pdfUrl,
            pdfPublicId,
            textContent,
            openModules
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 540.dp)
                .heightIn(max = 760.dp)
                .imePadding()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (initialCourse == null) "Create Course" else "Edit Course",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (initialCourse == null) {
                                "Set course info, then add modules & Video/PDF/Text lessons"
                            } else {
                                "Update course settings or jump to modules & lessons"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close dialog")
                    }
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    val activeError = validationError ?: dialogError
                    if (!activeError.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeError,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Course Title *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("course_title_input")
                    )
                    OutlinedTextField(
                        value = subtitle,
                        onValueChange = { subtitle = it },
                        label = { Text("Subtitle") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Cover image preview & picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = thumbnailUrl,
                                    contentDescription = "Cover preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Filled.Image, contentDescription = null)
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBusy) "Uploading..." else "Upload Cover")
                        }
                    }

                    OutlinedTextField(
                        value = thumbnailUrl,
                        onValueChange = { thumbnailUrl = it },
                        label = { Text("Cover Image URL (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Learning Level", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CourseLevel.entries.forEach { lvl ->
                            FilterChip(
                                selected = level == lvl,
                                onClick = { level = lvl },
                                label = { Text(lvl.label, maxLines = 1) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = durationDays,
                            onValueChange = { durationDays = it.filter { c -> c.isDigit() } },
                            label = { Text("Days") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(88.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Premium Course")
                        Switch(checked = isPremium, onCheckedChange = { isPremium = it })
                    }

                    if (isPremium) {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Price (BDT ৳)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Published")
                        Switch(checked = isPublished, onCheckedChange = { isPublished = it })
                    }

                    if (initialCourse == null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "First Module & Lesson (Optional)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Enter a module or lesson title to jump straight into adding Video, PDF, Text & Image content.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ContentTypeHintChip(Icons.Filled.Videocam, "Video")
                                    ContentTypeHintChip(Icons.Filled.PictureAsPdf, "PDF")
                                    ContentTypeHintChip(Icons.Filled.TextSnippet, "Text")
                                    ContentTypeHintChip(Icons.Filled.Image, "Image")
                                }
                                OutlinedTextField(
                                    value = firstModuleTitle,
                                    onValueChange = { firstModuleTitle = it },
                                    label = { Text("First Module Title (e.g. Module 1: Basics)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("course_first_module_input")
                                )
                                OutlinedTextField(
                                    value = firstLessonTitle,
                                    onValueChange = { firstLessonTitle = it },
                                    label = { Text("First Lesson Title (e.g. Lesson 1: Intro)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("course_first_lesson_input")
                                )

                                // Video Upload + URL Input
                                OutlinedButton(
                                    onClick = {
                                        videoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("course_upload_video_button")
                                ) {
                                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isBusy) "Uploading..." else "Upload Video")
                                }
                                OutlinedTextField(
                                    value = videoUrl,
                                    onValueChange = { videoUrl = it },
                                    label = { Text("Video URL (optional)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("course_video_url_input")
                                )

                                // PDF Upload + URL Input
                                OutlinedButton(
                                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("course_upload_pdf_button")
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isBusy) "Uploading..." else "Upload PDF")
                                }
                                OutlinedTextField(
                                    value = pdfUrl,
                                    onValueChange = { pdfUrl = it },
                                    label = { Text("PDF URL (optional)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("course_pdf_url_input")
                                )

                                // Text Content Input
                                OutlinedTextField(
                                    value = textContent,
                                    onValueChange = { textContent = it },
                                    label = { Text("Lesson Text Content (optional)") },
                                    minLines = 3,
                                    maxLines = 6,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("course_text_content_input")
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onOpenExistingModules(initialCourse.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Manage Modules & Video/PDF/Text Lessons")
                        }
                    }
                }

                // Footer Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (initialCourse == null) {
                        OutlinedButton(
                            onClick = { submit(openModules = false) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("save_course_only_button")
                        ) {
                            Text("Save")
                        }
                        val hasLessonOrMedia = firstLessonTitle.isNotBlank() ||
                            videoUrl.isNotBlank() ||
                            pdfUrl.isNotBlank() ||
                            textContent.isNotBlank()
                        Button(
                            onClick = { submit(openModules = true) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("save_course_dialog_button")
                        ) {
                            Text(
                                text = when {
                                    isBusy -> "Saving..."
                                    hasLessonOrMedia -> "Save & Edit Lesson"
                                    else -> "Add Modules"
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = { submit(openModules = false) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("save_course_dialog_button")
                        ) {
                            Text(if (isBusy) "Saving..." else "Save Changes")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentTypeHintChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
