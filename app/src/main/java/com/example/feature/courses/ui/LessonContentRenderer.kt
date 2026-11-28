package com.example.feature.courses.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.CourseMediaFileCache
import com.example.domain.model.ContentBlockType
import com.example.domain.model.CourseDomainUtils
import com.example.domain.model.LessonContentBlock
import com.example.domain.model.LessonNavLink
import com.example.domain.model.LessonProgressModel
import com.example.domain.model.LessonSummary
import com.example.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shared reusable lesson content renderer used identically by BOTH
 * Admin Lesson Preview (drafts & published) and Student Lesson Viewer.
 */
@Composable
fun LessonContentRenderer(
    lesson: LessonSummary,
    moduleTitle: String? = null,
    progress: LessonProgressModel? = null,
    previousLesson: LessonNavLink? = null,
    nextLesson: LessonNavLink? = null,
    isPreview: Boolean = false,
    isSavingProgress: Boolean = false,
    onToggleComplete: ((Boolean) -> Unit)? = null,
    onVideoProgress: ((blockId: String, positionSeconds: Int, durationSeconds: Int) -> Unit)? = null,
    onNavigateLesson: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val orderedBlocks = remember(lesson.content) {
        lesson.content.sortedBy { it.order }
    }
    val firstVideoBlockId = remember(orderedBlocks) {
        orderedBlocks.firstOrNull { it.type == ContentBlockType.VIDEO }?.id
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .testTag("lesson_content_renderer_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isPreview) {
                item(key = "preview_banner") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (lesson.isPublished) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lesson_preview_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = if (lesson.isPublished) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (lesson.isPublished) {
                                        "Admin Preview (Published)"
                                    } else {
                                        "Admin Draft Preview"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Rendered with the exact student lesson viewer.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item(key = "lesson_header") {
                LessonHeaderCard(
                    lesson = lesson,
                    moduleTitle = moduleTitle,
                    progress = progress
                )
            }

            if (orderedBlocks.isEmpty()) {
                item(key = "empty_blocks") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No content blocks added yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isPreview) {
                                    "Add Video, PDF, Text, or Image blocks in the editor to preview them here."
                                } else {
                                    "This lesson has no published learning blocks yet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(orderedBlocks, key = { "${it.id}_${it.order}" }) { block ->
                    val resumeSeconds = when {
                        progress == null || progress.lastPositionSeconds <= 0 -> 0
                        progress.lastContentId == block.id -> progress.lastPositionSeconds
                        progress.lastContentId.isBlank() && block.id == firstVideoBlockId ->
                            progress.lastPositionSeconds
                        else -> 0
                    }

                    LessonBlockCard(
                        block = block,
                        initialVideoPositionSeconds = resumeSeconds,
                        onVideoProgress = if (isPreview) null else onVideoProgress
                    )
                }
            }

            item(key = "lesson_footer") {
                LessonFooterSection(
                    lesson = lesson,
                    progress = progress,
                    previousLesson = previousLesson,
                    nextLesson = nextLesson,
                    isPreview = isPreview,
                    isSavingProgress = isSavingProgress,
                    onToggleComplete = onToggleComplete,
                    onNavigateLesson = onNavigateLesson
                )
            }
        }
    }
}

@Composable
private fun LessonHeaderCard(
    lesson: LessonSummary,
    moduleTitle: String?,
    progress: LessonProgressModel?
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!moduleTitle.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = moduleTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (lesson.isPremium) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Text(
                        text = if (lesson.isPremium) "Premium" else "Free",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lesson.isPremium) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${lesson.estimatedDurationMinutes} min",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = lesson.title.ifBlank { "Untitled Lesson" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (lesson.subtitle.isNotBlank()) {
                Text(
                    text = lesson.subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            if (lesson.description.isNotBlank()) {
                Text(
                    text = lesson.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (progress?.isCompleted == true) {
                val isDark = LocalIsDarkTheme.current
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF133A29) else Color(0xFFD1FAE5)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Lesson completed",
                            tint = if (isDark) Color(0xFF34D399) else Color(0xFF065F46),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Completed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF34D399) else Color(0xFF065F46)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reusable single block renderer (also invoked directly for Admin Individual Block Preview).
 */
@Composable
fun LessonBlockCard(
    block: LessonContentBlock,
    initialVideoPositionSeconds: Int = 0,
    onVideoProgress: ((blockId: String, positionSeconds: Int, durationSeconds: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("lesson_block_card_${block.type.apiValue}_${block.order}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BlockHeaderRow(block = block)

            when (block.type) {
                ContentBlockType.VIDEO -> VideoBlockContent(
                    block = block,
                    initialPositionSeconds = initialVideoPositionSeconds,
                    onVideoProgress = onVideoProgress
                )

                ContentBlockType.PDF -> PdfBlockContent(block = block)

                ContentBlockType.TEXT -> {
                    if (block.description.isNotBlank()) {
                        Text(
                            text = block.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    RichTextContentView(rawText = block.text)
                }

                ContentBlockType.IMAGE -> ImageBlockContent(block = block)
            }
        }
    }
}

@Composable
private fun BlockHeaderRow(block: LessonContentBlock) {
    val isDark = LocalIsDarkTheme.current
    val (icon, tint, label) = when (block.type) {
        ContentBlockType.VIDEO -> Triple(
            Icons.Filled.Videocam,
            if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA),
            "Video"
        )
        ContentBlockType.PDF -> Triple(
            Icons.Filled.PictureAsPdf,
            if (isDark) Color(0xFFF87171) else Color(0xFFB91C1C),
            "PDF Document"
        )
        ContentBlockType.TEXT -> Triple(
            Icons.Filled.TextSnippet,
            if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E),
            "Notes"
        )
        ContentBlockType.IMAGE -> Triple(
            Icons.Filled.Image,
            if (isDark) Color(0xFF34D399) else Color(0xFF0E6B3B),
            "Visual"
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = if (isDark) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.title.ifBlank { label },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (block.description.isNotBlank() && block.type != ContentBlockType.TEXT) {
                Text(
                    text = block.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VideoBlockContent(
    block: LessonContentBlock,
    initialPositionSeconds: Int,
    onVideoProgress: ((blockId: String, positionSeconds: Int, durationSeconds: Int) -> Unit)?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localCachedFile = remember(block.url) {
        CourseMediaFileCache.getCachedFileIfExists(context, block.url, ".mp4")
    }
    val playUri = remember(block.url, localCachedFile) {
        if (localCachedFile != null) Uri.fromFile(localCachedFile) else Uri.parse(block.url)
    }

    var isPlaying by remember(block.id) { mutableStateOf(false) }
    var isPrepared by remember(block.id) { mutableStateOf(false) }
    var isMuted by remember(block.id) { mutableStateOf(false) }
    var isFullscreen by remember(block.id) { mutableStateOf(false) }
    var videoError by remember(block.id) { mutableStateOf<String?>(null) }
    var durationMs by remember(block.id) {
        mutableIntStateOf((block.durationSeconds * 1000).coerceAtLeast(0))
    }
    var currentPosMs by remember(block.id) {
        mutableIntStateOf((initialPositionSeconds * 1000).coerceAtLeast(0))
    }
    var appliedInitialSeek by remember(block.id) { mutableStateOf(false) }
    var videoViewRef by remember(block.id) { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember(block.id) { mutableStateOf<MediaPlayer?>(null) }
    var isDownloading by remember(block.id) { mutableStateOf(false) }

    // Poll playback position & report resume telemetry periodically
    LaunchedEffect(isPlaying, isPrepared) {
        var tick = 0
        while (isActive && isPlaying && isPrepared) {
            val vv = videoViewRef
            if (vv != null) {
                val pos = runCatching { vv.currentPosition }.getOrDefault(currentPosMs)
                val dur = runCatching { vv.duration }.getOrDefault(durationMs)
                if (dur > 0) durationMs = dur
                currentPosMs = pos.coerceAtLeast(0)
                tick += 1
                if (tick % 10 == 0) {
                    onVideoProgress?.invoke(
                        block.id,
                        currentPosMs / 1000,
                        (durationMs / 1000).coerceAtLeast(block.durationSeconds)
                    )
                }
            }
            delay(500L)
        }
    }

    DisposableEffect(block.id) {
        onDispose {
            if (currentPosMs > 0) {
                onVideoProgress?.invoke(
                    block.id,
                    currentPosMs / 1000,
                    (durationMs / 1000).coerceAtLeast(block.durationSeconds)
                )
            }
            runCatching { videoViewRef?.stopPlayback() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (!isFullscreen) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            videoViewRef = this
                            setVideoURI(playUri)
                            setOnPreparedListener { mp ->
                                mediaPlayerRef = mp
                                isPrepared = true
                                videoError = null
                                val dur = mp.duration.takeIf { it > 0 } ?: (block.durationSeconds * 1000)
                                if (dur > 0) durationMs = dur
                                val vol = if (isMuted) 0f else 1f
                                mp.setVolume(vol, vol)
                                if (!appliedInitialSeek && initialPositionSeconds > 0) {
                                    appliedInitialSeek = true
                                    val targetMs = (initialPositionSeconds * 1000).coerceIn(0, dur.coerceAtLeast(0))
                                    seekTo(targetMs)
                                    currentPosMs = targetMs
                                }
                            }
                            setOnCompletionListener {
                                isPlaying = false
                                currentPosMs = durationMs
                                onVideoProgress?.invoke(
                                    block.id,
                                    (durationMs / 1000).coerceAtLeast(block.durationSeconds),
                                    (durationMs / 1000).coerceAtLeast(block.durationSeconds)
                                )
                            }
                            setOnErrorListener { _, _, _ ->
                                isPlaying = false
                                isPrepared = false
                                videoError = "Unable to play this video source."
                                true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (!isPrepared && videoError == null) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
            }

            if (videoError != null) {
                Text(
                    text = videoError!!,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (isPrepared && !isPlaying) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable {
                            videoViewRef?.start()
                            isPlaying = true
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play video",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // Video controls bar
        val totalSec = (durationMs / 1000).coerceAtLeast(block.durationSeconds)
        val posSec = (currentPosMs / 1000).coerceIn(0, totalSec.coerceAtLeast(1))
        val sliderMax = durationMs.coerceAtLeast(1000).toFloat()

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    val vv = videoViewRef ?: return@IconButton
                    if (isPlaying) {
                        vv.pause()
                        isPlaying = false
                        currentPosMs = runCatching { vv.currentPosition }.getOrDefault(currentPosMs)
                        onVideoProgress?.invoke(block.id, currentPosMs / 1000, totalSec)
                    } else {
                        vv.start()
                        isPlaying = true
                    }
                },
                enabled = isPrepared,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("video_play_pause_${block.id}")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause video" else "Play video"
                )
            }

            Text(
                text = "${CourseDomainUtils.formatDurationSeconds(posSec)} / ${CourseDomainUtils.formatDurationSeconds(totalSec)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            var sliderValue by remember(currentPosMs) { mutableFloatStateOf(currentPosMs.toFloat()) }
            Slider(
                value = sliderValue.coerceIn(0f, sliderMax),
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    val target = sliderValue.toInt().coerceIn(0, durationMs.coerceAtLeast(0))
                    videoViewRef?.seekTo(target)
                    currentPosMs = target
                    onVideoProgress?.invoke(block.id, target / 1000, totalSec)
                },
                valueRange = 0f..sliderMax,
                enabled = isPrepared,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    isMuted = !isMuted
                    val vol = if (isMuted) 0f else 1f
                    runCatching { mediaPlayerRef?.setVolume(vol, vol) }
                },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute"
                )
            }

            IconButton(
                onClick = {
                    runCatching { videoViewRef?.pause() }
                    isPlaying = false
                    isFullscreen = true
                },
                enabled = isPrepared,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Fullscreen video")
            }
        }

        if (initialPositionSeconds > 0 && currentPosMs == initialPositionSeconds * 1000) {
            Text(
                text = "Resumes from ${CourseDomainUtils.formatDurationSeconds(initialPositionSeconds)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (block.isDownloadable && block.url.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    if (!isDownloading) {
                        isDownloading = true
                        scope.launch {
                            val result = runCatching {
                                downloadFileForUser(context, block.url, block.title.ifBlank { "lesson_video" }, "video/mp4", ".mp4")
                            }
                            isDownloading = false
                            val msg = result.fold(
                                onSuccess = { "Video saved offline ($it)" },
                                onFailure = { "Download failed: ${it.message ?: "error"}" }
                            )
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isDownloading,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        isDownloading -> "Downloading..."
                        localCachedFile != null -> "Saved Offline (Download Copy)"
                        else -> "Download Video"
                    }
                )
            }
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(playUri)
                            setOnPreparedListener { mp ->
                                val vol = if (isMuted) 0f else 1f
                                mp.setVolume(vol, vol)
                                seekTo(currentPosMs)
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close fullscreen", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PdfBlockContent(block: LessonContentBlock) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pdfFile by remember(block.url) {
        mutableStateOf<File?>(CourseMediaFileCache.getCachedFileIfExists(context, block.url, ".pdf"))
    }
    var isLoading by remember(block.url) { mutableStateOf(false) }
    var loadError by remember(block.url) { mutableStateOf<String?>(null) }
    var pageCount by remember(block.url) { mutableIntStateOf(0) }
    var currentPageIndex by remember(block.url) { mutableIntStateOf(0) }
    var pageBitmap by remember(block.url, currentPageIndex) { mutableStateOf<Bitmap?>(null) }
    var isFullscreenPdf by remember(block.id) { mutableStateOf(false) }
    var isDownloading by remember(block.id) { mutableStateOf(false) }

    LaunchedEffect(block.url) {
        if (block.url.isBlank()) {
            loadError = "No PDF URL provided."
            return@LaunchedEffect
        }
        if (pdfFile == null) {
            isLoading = true
            loadError = null
            val res = runCatching { CourseMediaFileCache.downloadAndCache(context, block.url, ".pdf") }
            isLoading = false
            res.onSuccess { pdfFile = it }
                .onFailure { loadError = it.message ?: "Failed to load PDF" }
        }
    }

    LaunchedEffect(pdfFile, currentPageIndex) {
        val file = pdfFile ?: return@LaunchedEffect
        isLoading = true
        val renderResult = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val count = renderer.pageCount.coerceAtLeast(0)
                        if (count == 0) {
                            Pair(0, null as Bitmap?)
                        } else {
                            val safeIndex = currentPageIndex.coerceIn(0, count - 1)
                            renderer.openPage(safeIndex).use { page ->
                                val width = 1080
                                val height = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * width)
                                    .toInt()
                                    .coerceIn(320, 2200)
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(AndroidColor.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                Pair(count, bmp)
                            }
                        }
                    }
                }
            }
        }
        isLoading = false
        renderResult.onSuccess { (count, bmp) ->
            pageCount = count
            pageBitmap = bmp
            loadError = if (count == 0) "This PDF has no pages." else null
        }.onFailure { err ->
            loadError = err.message ?: "Unable to render PDF."
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 460.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (LocalIsDarkTheme.current) Color(0xFF1E293B) else Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center
        ) {
            when {
                loadError != null -> {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(loadError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        FilledTonalButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(block.url)))
                            }
                        }) {
                            Text("Open in PDF App")
                        }
                    }
                }

                pageBitmap != null -> {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "${block.title.ifBlank { "PDF" }} page ${currentPageIndex + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFullscreenPdf = true }
                    )
                }

                isLoading -> CircularProgressIndicator()
            }
        }

        if (pageCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { if (currentPageIndex > 0) currentPageIndex -= 1 },
                    enabled = currentPageIndex > 0,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Prev")
                }

                Text(
                    text = "Page ${currentPageIndex + 1} of $pageCount",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedButton(
                    onClick = { if (currentPageIndex < pageCount - 1) currentPageIndex += 1 },
                    enabled = currentPageIndex < pageCount - 1,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Next")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pageBitmap != null) {
                FilledTonalButton(
                    onClick = { isFullscreenPdf = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Expand Reader")
                }
            }

            if (block.isDownloadable && block.url.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        if (!isDownloading) {
                            isDownloading = true
                            scope.launch {
                                val res = runCatching {
                                    downloadFileForUser(context, block.url, block.title.ifBlank { "lesson_document" }, "application/pdf", ".pdf")
                                }
                                isDownloading = false
                                val msg = res.fold(
                                    onSuccess = { "PDF saved ($it)" },
                                    onFailure = { "Download failed: ${it.message ?: "error"}" }
                                )
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isDownloading,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isDownloading) "Saving..." else "Download PDF")
                }
            }
        }
    }

    if (isFullscreenPdf && pageBitmap != null) {
        Dialog(
            onDismissRequest = { isFullscreenPdf = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF111827)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${block.title.ifBlank { "PDF" }} (${currentPageIndex + 1}/$pageCount)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { isFullscreenPdf = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close PDF", tint = Color.White)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "Fullscreen PDF page",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { if (currentPageIndex > 0) currentPageIndex -= 1 },
                            enabled = currentPageIndex > 0
                        ) { Text("Previous Page") }
                        Button(
                            onClick = { if (currentPageIndex < pageCount - 1) currentPageIndex += 1 },
                            enabled = currentPageIndex < pageCount - 1
                        ) { Text("Next Page") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBlockContent(block: LessonContentBlock) {
    val context = LocalContext.current
    val localImage = remember(block.url) {
        CourseMediaFileCache.getCachedFileIfExists(context, block.url, ".jpg")
    }
    var showZoom by remember(block.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable { showZoom = true }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(localImage ?: block.url)
                    .crossfade(true)
                    .build(),
                contentDescription = block.alt.ifBlank { block.title.ifBlank { "Lesson image" } },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 380.dp)
            )
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ZoomIn,
                    contentDescription = "Zoom image",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(18.dp)
                )
            }
        }
        if (block.alt.isNotBlank()) {
            Text(
                text = block.alt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showZoom) {
        Dialog(
            onDismissRequest = { showZoom = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showZoom = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = localImage ?: block.url,
                    contentDescription = block.alt.ifBlank { block.title },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
                IconButton(
                    onClick = { showZoom = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close zoom", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LessonFooterSection(
    lesson: LessonSummary,
    progress: LessonProgressModel?,
    previousLesson: LessonNavLink?,
    nextLesson: LessonNavLink?,
    isPreview: Boolean,
    isSavingProgress: Boolean,
    onToggleComplete: ((Boolean) -> Unit)?,
    onNavigateLesson: ((String) -> Unit)?
) {
    val isCompleted = progress?.isCompleted == true

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (!isPreview && onToggleComplete != null) {
            Button(
                onClick = { onToggleComplete(!isCompleted) },
                enabled = !isSavingProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCompleted) Color(0xFF0E6B3B) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isCompleted) Color.White else MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("lesson_mark_complete_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        isSavingProgress -> "Saving Progress..."
                        isCompleted -> "Completed (Tap to Undo)"
                        else -> "Mark Lesson Complete"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (previousLesson != null || nextLesson != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (previousLesson != null) {
                    OutlinedButton(
                        onClick = { onNavigateLesson?.invoke(previousLesson.id) },
                        enabled = onNavigateLesson != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("lesson_previous_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Previous", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (nextLesson != null) {
                    Button(
                        onClick = { onNavigateLesson?.invoke(nextLesson.id) },
                        enabled = onNavigateLesson != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("lesson_next_button")
                    ) {
                        Text("Next Lesson", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private suspend fun downloadFileForUser(
    context: Context,
    url: String,
    baseTitle: String,
    mimeType: String,
    defaultExt: String
): String {
    val cached = CourseMediaFileCache.downloadAndCache(context, url, defaultExt)
    val safeName = baseTitle.lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "_")
        .trim('_')
        .ifBlank { "lesson_resource" } + defaultExt

    return withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    cached.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return@withContext "Downloads/$safeName"
            }
        }
        "App Offline Cache"
    }
}
