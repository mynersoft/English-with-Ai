package com.example.feature.admin

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private class AudioActions(
    val locked: Boolean,
    val busyId: String?,
    val player: PreviewPlayer,
    val onGenerate: (AudioTarget, Boolean) -> Unit,
    val onUpload: (AudioTarget) -> Unit,
    val onDelete: (AudioTarget) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminEditScreen(vm: AdminViewModel, onClose: () -> Unit) {
    val state by vm.edit.collectAsStateWithLifecycle()
    val form = state.form
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val player = rememberPreviewPlayer()

    var pendingTarget by remember { mutableStateOf<AudioTarget?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val t = pendingTarget
        if (uri != null && t != null) vm.upload(context.contentResolver, uri, t)
        pendingTarget = null
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearEditMessage()
        }
    }

    val actions = AudioActions(
        locked = state.audioLocked,
        busyId = state.busyId,
        player = player,
        onGenerate = { t, force -> vm.generate(t, force) },
        onUpload = { t ->
            pendingTarget = t
            picker.launch("audio/*")
        },
        onDelete = { t -> vm.deleteAudio(t) }
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Post New Pattern" else "Edit Pattern") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { vm.save() }, enabled = !state.saving && !state.loading) {
                        Text(if (state.saving) "Saving..." else "Save")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.audioLocked) {
                item {
                    Surface(
                        color = Color(0xFFFFF3CD),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (state.isNew) "Save the pattern first, then you can generate or upload audio."
                            else "Unsaved changes. Save before generating or uploading audio.",
                            color = Color(0xFF8A6D00),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = vm::setTitle,
                    label = { Text("Title (English)") },
                    placeholder = { Text("I'd like to + Verb") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!state.isNew) {
                item { AudioRow("Title audio", form.titleAudio, AudioTarget("title"), actions) }
            }

            item {
                OutlinedTextField(
                    value = form.meaning,
                    onValueChange = vm::setMeaning,
                    label = { Text("Meaning (Bangla)") },
                    placeholder = { Text("আমি ... করতে চাই (ভদ্রভাবে)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!state.isNew) {
                item { AudioRow("Meaning audio", form.meaningAudio, AudioTarget("meaning"), actions) }
            }

            item {
                OutlinedTextField(
                    value = form.categoryName,
                    onValueChange = vm::setCategoryName,
                    label = { Text("Category Name") },
                    placeholder = { Text("All") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = form.explanation,
                    onValueChange = vm::setExplanation,
                    label = { Text("Explanation (Bangla)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = form.isPremium, onCheckedChange = vm::setPremium)
                    Text("Premium", Modifier.padding(start = 8.dp, end = 24.dp))
                    OutlinedTextField(
                        value = form.order,
                        onValueChange = vm::setOrder,
                        label = { Text("Order") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Sentences (${form.examples.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (!state.isNew) {
                        OutlinedButton(
                            onClick = { vm.generateAll() },
                            enabled = !state.audioLocked && state.busyId == null
                        ) { Text(if (state.busyId == "all") "Generating..." else "Generate missing audio") }
                    }
                }
            }

            itemsIndexed(form.examples, key = { _, ex -> ex.key }) { i, ex ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Sentence ${i + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.removeExample(i) }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        OutlinedTextField(
                            value = ex.en,
                            onValueChange = { vm.setExampleEn(i, it) },
                            label = { Text("English sentence") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (ex.id != null) {
                            AudioRow("English audio", ex.enAudio, AudioTarget("example", i, "en"), actions)
                        }
                        OutlinedTextField(
                            value = ex.bn,
                            onValueChange = { vm.setExampleBn(i, it) },
                            label = { Text("বাংলা অনুবাদ") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (ex.id != null) {
                            AudioRow("Bangla audio", ex.bnAudio, AudioTarget("example", i, "bn"), actions)
                        }
                    }
                }
            }

            item {
                OutlinedButton(onClick = { vm.addExample() }, modifier = Modifier.fillMaxWidth()) {
                    Text("+ Add sentence")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioRow(label: String, audio: AdminAudio, target: AudioTarget, a: AudioActions) {
    val busy = a.busyId == target.busyId()
    val disabled = a.locked || a.busyId != null
    var confirmReplace by remember { mutableStateOf(false) }
    val url = audio.url
    val playing = url != null && a.player.playingUrl == url

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    StatusTag(audio)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (url != null) {
                    TextButton(onClick = { a.player.toggle(url) }) {
                        Text(if (playing) "■ Stop" else "▶ Play")
                    }
                }
                TextButton(
                    enabled = !disabled,
                    onClick = {
                        if (audio.source == "manual") confirmReplace = true
                        else a.onGenerate(target, false)
                    }
                ) { Text(if (url != null) "Regenerate" else "Generate") }
                TextButton(enabled = !disabled, onClick = { a.onUpload(target) }) { Text("Upload") }
                if (url != null) {
                    TextButton(enabled = !disabled, onClick = { a.onDelete(target) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace your recording?") },
            text = { Text("This audio was uploaded by you. Regenerating will replace it with TTS audio.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReplace = false
                    a.onGenerate(target, true)
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StatusTag(audio: AdminAudio) {
    when {
        audio.url == null -> Tag("No audio", Color(0xFFE5E7EB), Color(0xFF6B7280))
        audio.stale -> Tag("Outdated", Color(0xFFFFF3CD), Color(0xFF8A6D00))
        audio.source == "manual" -> Tag("Manual", Color(0xFFDCFCE7), Color(0xFF166534))
        else -> Tag("TTS", Color(0xFFDCFCE7), Color(0xFF166534))
    }
}

@Composable
fun Tag(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Surface(color = bg, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

private class PreviewPlayer {
    private var mp: MediaPlayer? = null
    var playingUrl by mutableStateOf<String?>(null)
        private set

    fun toggle(url: String) {
        if (playingUrl == url) {
            stop()
            return
        }
        stop()
        val player = MediaPlayer()
        mp = player
        playingUrl = url
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(url)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { stop() }
            player.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        mp?.release()
        mp = null
        playingUrl = null
    }
}

@Composable
private fun rememberPreviewPlayer(): PreviewPlayer {
    val player = remember { PreviewPlayer() }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    return player
}
