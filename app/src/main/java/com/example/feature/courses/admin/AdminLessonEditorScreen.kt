package com.example.feature.courses.admin

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.domain.model.ContentBlockType
import com.example.domain.model.LessonContentBlock
import com.example.domain.model.LessonSummary
import com.example.feature.courses.ui.LessonBlockCard
import com.example.feature.courses.ui.LessonContentRenderer
import com.example.feature.courses.ui.RichTextToolbarEditor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLessonEditorScreen(
    state: AdminLessonEditorUiState,
    inMemoryPreviewLesson: () -> LessonSummary,
    onBack: () -> Unit,
    onUpdateFields: (
        title: String?,
        subtitle: String?,
        description: String?,
        duration: String?,
        isPremium: Boolean?,
        isPublished: Boolean?
    ) -> Unit,
    onCreateEmptyBlock: (ContentBlockType) -> LessonContentBlock,
    onUpsertBlock: (LessonContentBlock) -> Unit,
    onRemoveBlock: (String) -> Unit,
    onReorderBlocks: (fromIndex: Int, toIndex: Int) -> Unit,
    onUploadBlockMedia: (android.net.Uri, String, (String, String, Int) -> Unit) -> Unit,
    onOpenFullPreview: (Boolean) -> Unit,
    onOpenBlockPreview: (LessonContentBlock?) -> Unit,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit
) {
    var editingBlock by remember { mutableStateOf<LessonContentBlock?>(null) }
    var draggingBlockId by remember { mutableStateOf<String?>(null) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableIntStateOf(220) }

    BackHandler(enabled = state.previewOpen || state.previewBlock != null) {
        if (state.previewBlock != null) onOpenBlockPreview(null)
        else if (state.previewOpen) onOpenFullPreview(false)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.lessonId == null) "New Lesson" else "Edit Lesson",
                            fontWeight = FontWeight.Bold
                        )
                        if (state.moduleTitle.isNotBlank()) {
                            Text(
                                text = state.moduleTitle,
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
                            .testTag("admin_lesson_editor_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { onOpenFullPreview(true) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_lesson_preview_button")
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Preview")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onSaveDraft,
                        enabled = !state.isSaving && !state.isUploadingMedia,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_lesson_save_draft_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save Draft", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Button(
                        onClick = onPublish,
                        enabled = !state.isSaving && !state.isUploadingMedia,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_lesson_publish_button")
                    ) {
                        Icon(Icons.Filled.Publish, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (state.isSaving) "Saving..." else "Publish",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (state.isSaving || state.isUploadingMedia) {
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

                    // 1. Lesson Information Section
                    item(key = "lesson_info_section") {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Lesson Information",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (state.isPublished) Color(0xFF133A29) else Color(0xFF3E2B0D)
                                    ) {
                                        Text(
                                            text = if (state.isPublished) "Published" else "Draft",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (state.isPublished) Color(0xFF34D399) else Color(0xFFFBBF24),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = state.title,
                                    onValueChange = { onUpdateFields(it, null, null, null, null, null) },
                                    label = { Text("Lesson Title *") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("lesson_title_input")
                                )

                                OutlinedTextField(
                                    value = state.subtitle,
                                    onValueChange = { onUpdateFields(null, it, null, null, null, null) },
                                    label = { Text("Subtitle") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = state.description,
                                    onValueChange = { onUpdateFields(null, null, it, null, null, null) },
                                    label = { Text("Lesson Description / Summary") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = state.estimatedDurationMinutes,
                                        onValueChange = { raw ->
                                            onUpdateFields(null, null, null, raw.filter { it.isDigit() }, null, null)
                                        },
                                        label = { Text("Est. Minutes") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(120.dp)
                                    )

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (state.isPremium) "Premium" else "Free",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Switch(
                                            checked = state.isPremium,
                                            onCheckedChange = { onUpdateFields(null, null, null, null, it, null) },
                                            modifier = Modifier.testTag("lesson_premium_switch")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Content Builder Add Buttons Section
                    item(key = "content_builder_toolbar") {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Content Builder (${state.contentBlocks.size} blocks)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Add multiple Video, PDF, Text, and Image blocks in any order. Drag handles to reorder.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AddBlockButton(
                                        label = "Add Video",
                                        icon = Icons.Filled.Videocam,
                                        tag = "admin_add_video_block_button",
                                        modifier = Modifier.weight(1f)
                                    ) { editingBlock = onCreateEmptyBlock(ContentBlockType.VIDEO) }

                                    AddBlockButton(
                                        label = "Add PDF",
                                        icon = Icons.Filled.PictureAsPdf,
                                        tag = "admin_add_pdf_block_button",
                                        modifier = Modifier.weight(1f)
                                    ) { editingBlock = onCreateEmptyBlock(ContentBlockType.PDF) }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AddBlockButton(
                                        label = "Add Text",
                                        icon = Icons.Filled.TextSnippet,
                                        tag = "admin_add_text_block_button",
                                        modifier = Modifier.weight(1f)
                                    ) { editingBlock = onCreateEmptyBlock(ContentBlockType.TEXT) }

                                    AddBlockButton(
                                        label = "Add Image",
                                        icon = Icons.Filled.Image,
                                        tag = "admin_add_image_block_button",
                                        modifier = Modifier.weight(1f)
                                    ) { editingBlock = onCreateEmptyBlock(ContentBlockType.IMAGE) }
                                }
                            }
                        }
                    }

                    // 3. Content Block List with Drag-and-Drop Reordering
                    if (state.contentBlocks.isEmpty()) {
                        item(key = "no_blocks_hint") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No content blocks yet. Tap any Add button above to build your lesson.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(state.contentBlocks, key = { _, b -> b.id }) { index, block ->
                            val isDragging = draggingBlockId == block.id
                            ContentBlockEditorItemCard(
                                block = block,
                                index = index,
                                totalCount = state.contentBlocks.size,
                                isDragging = isDragging,
                                dragOffsetY = if (isDragging) dragDeltaY else 0f,
                                onMeasuredHeight = { h -> if (h > 0) itemHeightPx = h },
                                onDragStart = {
                                    draggingBlockId = block.id
                                    dragDeltaY = 0f
                                },
                                onDragDelta = { dy ->
                                    dragDeltaY += dy
                                    val threshold = (itemHeightPx * 0.65f).coerceAtLeast(110f)
                                    val currentIdx = state.contentBlocks.indexOfFirst { it.id == block.id }
                                    if (currentIdx != -1) {
                                        if (dragDeltaY > threshold && currentIdx < state.contentBlocks.lastIndex) {
                                            onReorderBlocks(currentIdx, currentIdx + 1)
                                            dragDeltaY -= threshold
                                        } else if (dragDeltaY < -threshold && currentIdx > 0) {
                                            onReorderBlocks(currentIdx, currentIdx - 1)
                                            dragDeltaY += threshold
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggingBlockId = null
                                    dragDeltaY = 0f
                                },
                                onMoveUp = { onReorderBlocks(index, index - 1) },
                                onMoveDown = { onReorderBlocks(index, index + 1) },
                                onEdit = { editingBlock = block },
                                onPreviewBlock = { onOpenBlockPreview(block) },
                                onDelete = { onRemoveBlock(block.id) }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Block Add/Edit Modal
    editingBlock?.let { targetBlock ->
        ContentBlockEditorDialog(
            initial = targetBlock,
            isUploading = state.isUploadingMedia,
            uploadError = state.error,
            onUploadMedia = onUploadBlockMedia,
            onDismiss = { editingBlock = null },
            onSave = { updated ->
                onUpsertBlock(updated)
                editingBlock = null
            }
        )
    }

    // Individual Block Preview Modal (uses shared LessonBlockCard)
    state.previewBlock?.let { previewBlk ->
        Dialog(
            onDismissRequest = { onOpenBlockPreview(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = { Text("Block Preview", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { onOpenBlockPreview(null) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close block preview")
                            }
                        }
                    )
                }
            ) { pad ->
                Box(
                    modifier = Modifier
                        .padding(pad)
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(modifier = Modifier.widthIn(max = 600.dp)) {
                        LessonBlockCard(block = previewBlk)
                    }
                }
            }
        }
    }

    // Full Lesson Preview Modal (uses exact shared LessonContentRenderer, including unpublished drafts!)
    if (state.previewOpen) {
        val previewLesson = remember(state) { inMemoryPreviewLesson() }
        Dialog(
            onDismissRequest = { onOpenFullPreview(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = { Text("Student Lesson Preview", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(
                                onClick = { onOpenFullPreview(false) },
                                modifier = Modifier.testTag("close_lesson_preview_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close preview")
                            }
                        }
                    )
                }
            ) { pad ->
                LessonContentRenderer(
                    lesson = previewLesson,
                    moduleTitle = state.moduleTitle,
                    previousLesson = state.previewPrevious,
                    nextLesson = state.previewNext,
                    isPreview = true,
                    modifier = Modifier.padding(pad)
                )
            }
        }
    }
}

@Composable
private fun AddBlockButton(
    label: String,
    icon: ImageVector,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(tag)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ContentBlockEditorItemCard(
    block: LessonContentBlock,
    index: Int,
    totalCount: Int,
    isDragging: Boolean,
    dragOffsetY: Float,
    onMeasuredHeight: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onPreviewBlock: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, tint) = when (block.type) {
        ContentBlockType.VIDEO -> Icons.Filled.Videocam to Color(0xFFA5B4FC)
        ContentBlockType.PDF -> Icons.Filled.PictureAsPdf to Color(0xFFF87171)
        ContentBlockType.TEXT -> Icons.Filled.TextSnippet to Color(0xFF2DD4BF)
        ContentBlockType.IMAGE -> Icons.Filled.Image to Color(0xFF34D399)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isDragging) 2.dp else 1.dp,
            color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragOffsetY }
            .onSizeChanged { onMeasuredHeight(it.height) }
            .testTag("editor_block_card_${block.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Touch drag handle for drag-and-drop ordering
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .pointerInput(block.id) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() }
                            )
                        }
                        .testTag("drag_handle_${block.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder block",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = block.type.label, tint = tint, modifier = Modifier.size(18.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${index + 1} · ${block.title.ifBlank { block.type.label }}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val summary = when (block.type) {
                        ContentBlockType.TEXT -> block.text.take(80).replace("\n", " ")
                        ContentBlockType.VIDEO -> "${block.durationSeconds}s · ${if (block.isDownloadable) "Downloadable" else "Stream"} · ${block.url}"
                        ContentBlockType.PDF -> "${if (block.isDownloadable) "Downloadable" else "View-only"} · ${block.url}"
                        ContentBlockType.IMAGE -> "${block.alt.ifBlank { "No alt text" }} · ${block.url}"
                    }
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move block up", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, enabled = index < totalCount - 1, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move block down", modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }

                FilledTonalButton(
                    onClick = onPreviewBlock,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Preview Block")
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = onDelete, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete block",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentBlockEditorDialog(
    initial: LessonContentBlock,
    isUploading: Boolean,
    uploadError: String? = null,
    onUploadMedia: (android.net.Uri, String, (String, String, Int) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (LessonContentBlock) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial.title) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var url by remember(initial) { mutableStateOf(initial.url) }
    var publicId by remember(initial) { mutableStateOf(initial.publicId) }
    var text by remember(initial) { mutableStateOf(initial.text) }
    var alt by remember(initial) { mutableStateOf(initial.alt) }
    var durationSeconds by remember(initial) { mutableStateOf(initial.durationSeconds.toString()) }
    var isDownloadable by remember(initial) { mutableStateOf(initial.isDownloadable) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mediaType = if (initial.type == ContentBlockType.VIDEO) "video" else "image"
            onUploadMedia(uri, mediaType) { uploadedUrl, uploadedPublicId, uploadedDur ->
                url = uploadedUrl
                publicId = uploadedPublicId
                if (uploadedDur > 0) {
                    durationSeconds = uploadedDur.toString()
                }
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onUploadMedia(uri, "pdf") { uploadedUrl, uploadedPublicId, _ ->
                url = uploadedUrl
                publicId = uploadedPublicId
            }
        }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Configure ${initial.type.label} Block",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Close, contentDescription = "Close block dialog")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isUploading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    val activeError = validationError ?: uploadError
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
                        label = { Text("Block Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("block_title_input")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Notes (optional)") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    when (initial.type) {
                        ContentBlockType.VIDEO -> {
                            OutlinedButton(
                                onClick = {
                                    mediaPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                enabled = !isUploading,
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isUploading) "Uploading Video..." else "Upload Video File")
                            }
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("Video URL *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("block_url_input")
                            )
                            OutlinedTextField(
                                value = durationSeconds,
                                onValueChange = { durationSeconds = it.filter { c -> c.isDigit() } },
                                label = { Text("Duration (seconds)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Allow Offline Download")
                                Switch(checked = isDownloadable, onCheckedChange = { isDownloadable = it })
                            }
                        }

                        ContentBlockType.PDF -> {
                            OutlinedButton(
                                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                enabled = !isUploading,
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isUploading) "Uploading PDF..." else "Upload PDF Document")
                            }
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("PDF URL *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("block_url_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Allow Download Option")
                                Switch(checked = isDownloadable, onCheckedChange = { isDownloadable = it })
                            }
                        }

                        ContentBlockType.IMAGE -> {
                            if (url.isNotBlank()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = alt.ifBlank { title },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    mediaPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isUploading,
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isUploading) "Uploading Image..." else "Upload Image")
                            }
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("Image URL *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("block_url_input")
                            )
                            OutlinedTextField(
                                value = alt,
                                onValueChange = { alt = it },
                                label = { Text("Alt Text (Accessibility)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ContentBlockType.TEXT -> {
                            RichTextToolbarEditor(
                                value = text,
                                onValueChange = { text = it }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (initial.type == ContentBlockType.TEXT) {
                                if (text.trim().isEmpty() && title.trim().isEmpty()) {
                                    validationError = "Please enter text content."
                                    return@Button
                                }
                            } else {
                                val trimmedUrl = url.trim()
                                if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                                    validationError = "Please upload a file or enter a valid http/https URL."
                                    return@Button
                                }
                            }
                            validationError = null
                            onSave(
                                initial.copy(
                                    title = title.trim(),
                                    description = description.trim(),
                                    url = url.trim(),
                                    publicId = publicId.trim(),
                                    text = text,
                                    alt = alt.trim(),
                                    durationSeconds = durationSeconds.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                                    isDownloadable = isDownloadable
                                )
                            )
                        },
                        enabled = !isUploading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_block_dialog_button")
                    ) {
                        Text("Save Block")
                    }
                }
            }
        }
    }
}
