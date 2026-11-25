package com.example.feature.reading.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ReadingCategory
import com.example.domain.model.ReadingCefrLevel
import com.example.domain.model.ReadingQuestion
import com.example.domain.model.ReadingQuestionType
import com.example.domain.model.ReadingVocabItem
import com.example.feature.reading.ReadingPassageEditorState

@Composable
fun ReadingAdminEditorScreen(
    editorState: ReadingPassageEditorState,
    categories: List<ReadingCategory>,
    onUpdate: ((ReadingPassageEditorState) -> ReadingPassageEditorState) -> Unit,
    onTogglePreview: () -> Unit,
    onAddVocab: (ReadingVocabItem) -> Unit,
    onRemoveVocab: (Int) -> Unit,
    onAddQuestion: (ReadingQuestion) -> Unit,
    onRemoveQuestion: (Int) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val pal = readingPalette()
    var showAddVocabDialog by remember { mutableStateOf(false) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }

    if (editorState.loadingDetail) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = pal.primary)
        }
        return
    }

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editorState.editingId == null) "New Passage" else "Edit Passage",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = pal.textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onTogglePreview) {
                        Icon(if (editorState.previewMode) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (editorState.previewMode) "Edit" else "Preview")
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close editor")
                    }
                }
            }
        }

        if (!editorState.validationError.isNullOrBlank()) {
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = pal.missingBg, modifier = Modifier.fillMaxWidth()) {
                    Text(editorState.validationError, color = pal.missing, modifier = Modifier.padding(12.dp))
                }
            }
        }

        if (editorState.previewMode) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pal.card,
                    border = BorderStroke(1.dp, pal.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CefrBadge(editorState.level)
                            Text("${editorState.estimatedMinutes} min", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                        }
                        Text(editorState.title.ifBlank { "(Untitled)" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (editorState.summary.isNotBlank()) {
                            Text(editorState.summary, style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                        }
                        Text("Split Sentences (${editorState.previewSentences.size})", style = MaterialTheme.typography.labelMedium, color = pal.primary, fontWeight = FontWeight.Bold)
                        editorState.previewSentences.forEachIndexed { idx, s ->
                            Surface(shape = RoundedCornerShape(10.dp), color = pal.background, modifier = Modifier.fillMaxWidth()) {
                                Text("${idx + 1}. ${s.en}", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (editorState.bengaliTranslation.isNotBlank()) {
                            Text("Bengali Translation", style = MaterialTheme.typography.labelMedium, color = pal.primary, fontWeight = FontWeight.Bold)
                            Text(editorState.bengaliTranslation, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Vocabulary: ${editorState.vocabulary.size} · Questions: ${editorState.questions.size}", style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = editorState.title,
                    onValueChange = { v -> onUpdate { it.copy(title = v) } },
                    label = { Text("Passage Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("admin_passage_title_input")
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CEFR Level", style = MaterialTheme.typography.labelMedium, color = pal.textSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReadingCefrLevel.entries.filter { it != ReadingCefrLevel.ALL }.forEach { lvl ->
                            FilterChip(
                                selected = editorState.level == lvl.code,
                                onClick = { onUpdate { it.copy(level = lvl.code) } },
                                label = { Text(lvl.code) }
                            )
                        }
                    }

                    if (categories.isNotEmpty()) {
                        Text("Category", style = MaterialTheme.typography.labelMedium, color = pal.textSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                FilterChip(
                                    selected = editorState.categoryId == cat.id,
                                    onClick = { onUpdate { it.copy(categoryId = cat.id) } },
                                    label = { Text(cat.name) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = editorState.summary,
                    onValueChange = { v -> onUpdate { it.copy(summary = v) } },
                    label = { Text("Short Summary") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = editorState.englishText,
                    onValueChange = { v -> onUpdate { it.copy(englishText = v) } },
                    label = { Text("English Passage Text * (${editorState.previewSentences.size} sentences)") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth().testTag("admin_passage_english_input")
                )
            }

            item {
                OutlinedTextField(
                    value = editorState.bengaliTranslation,
                    onValueChange = { v -> onUpdate { it.copy(bengaliTranslation = v) } },
                    label = { Text("Bengali Translation") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editorState.audioUrl,
                        onValueChange = { v -> onUpdate { it.copy(audioUrl = v) } },
                        label = { Text("Optional MP3 URL") },
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = editorState.estimatedMinutes.toString(),
                        onValueChange = { v ->
                            onUpdate { it.copy(estimatedMinutes = v.filter(Char::isDigit).toIntOrNull() ?: 3) }
                        },
                        label = { Text("Minutes") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Published (visible to learners)", fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = editorState.isPublished,
                        onCheckedChange = { pub -> onUpdate { it.copy(isPublished = pub) } }
                    )
                }
            }

            // Vocabulary items section
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Passage Vocabulary (${editorState.vocabulary.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { showAddVocabDialog = true }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Word")
                    }
                }
            }

            items(editorState.vocabulary.size) { idx ->
                val v = editorState.vocabulary[idx]
                Surface(shape = RoundedCornerShape(12.dp), color = pal.card, border = BorderStroke(1.dp, pal.border), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${v.word} — ${v.meaningBn}", fontWeight = FontWeight.SemiBold)
                            if (v.partOfSpeech.isNotBlank()) Text(v.partOfSpeech, style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                        }
                        IconButton(onClick = { onRemoveVocab(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove word", tint = pal.missing)
                        }
                    }
                }
            }

            // Questions section
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Comprehension Questions (${editorState.questions.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { showAddQuestionDialog = true }) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Question")
                    }
                }
            }

            items(editorState.questions.size) { idx ->
                val q = editorState.questions[idx]
                Surface(shape = RoundedCornerShape(12.dp), color = pal.card, border = BorderStroke(1.dp, pal.border), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${idx + 1}. [${q.type.displayLabel}] ${q.prompt}", fontWeight = FontWeight.SemiBold)
                            Text("Answer: ${q.correctAnswer}", style = MaterialTheme.typography.labelSmall, color = pal.primary)
                        }
                        IconButton(onClick = { onRemoveQuestion(idx) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove question", tint = pal.missing)
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onSave,
                enabled = !editorState.saving,
                colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("admin_save_reading_passage_button")
            ) {
                if (editorState.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = pal.onPrimary)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text("Save Passage")
            }
        }
    }

    if (showAddVocabDialog) {
        AddVocabItemDialog(
            onDismiss = { showAddVocabDialog = false },
            onAdd = {
                onAddVocab(it)
                showAddVocabDialog = false
            }
        )
    }

    if (showAddQuestionDialog) {
        AddReadingQuestionDialog(
            onDismiss = { showAddQuestionDialog = false },
            onAdd = {
                onAddQuestion(it)
                showAddQuestionDialog = false
            }
        )
    }
}

@Composable
private fun AddVocabItemDialog(
    onDismiss: () -> Unit,
    onAdd: (ReadingVocabItem) -> Unit
) {
    var word by remember { mutableStateOf("") }
    var meaningBn by remember { mutableStateOf("") }
    var pronunciation by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Passage Vocabulary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("English Word *") }, singleLine = true)
                OutlinedTextField(value = meaningBn, onValueChange = { meaningBn = it }, label = { Text("Bengali Meaning") }, singleLine = true)
                OutlinedTextField(value = pronunciation, onValueChange = { pronunciation = it }, label = { Text("Pronunciation (IPA)") }, singleLine = true)
                OutlinedTextField(value = pos, onValueChange = { pos = it }, label = { Text("Part of Speech") }, singleLine = true)
                OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("Example Sentence") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(ReadingVocabItem(word.trim(), meaningBn.trim(), pronunciation.trim(), pos.trim(), example.trim())) },
                enabled = word.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddReadingQuestionDialog(
    onDismiss: () -> Unit,
    onAdd: (ReadingQuestion) -> Unit
) {
    var type by remember { mutableStateOf(ReadingQuestionType.MCQ) }
    var prompt by remember { mutableStateOf("") }
    var optionsCsv by remember { mutableStateOf("") }
    var correct by remember { mutableStateOf("") }
    var explanationBn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Comprehension Question") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ReadingQuestionType.entries.forEach { qt ->
                        FilterChip(selected = type == qt, onClick = { type = qt }, label = { Text(qt.displayLabel) })
                    }
                }
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Question Prompt *") })
                if (type == ReadingQuestionType.MCQ) {
                    OutlinedTextField(value = optionsCsv, onValueChange = { optionsCsv = it }, label = { Text("Options (comma-separated)") })
                }
                if (type == ReadingQuestionType.TRUE_FALSE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("True", "False").forEach { tf ->
                            FilterChip(selected = correct == tf, onClick = { correct = tf }, label = { Text(tf) })
                        }
                    }
                } else {
                    OutlinedTextField(value = correct, onValueChange = { correct = it }, label = { Text("Correct Answer *") }, singleLine = true)
                }
                OutlinedTextField(value = explanationBn, onValueChange = { explanationBn = it }, label = { Text("Bengali Explanation") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val opts = when (type) {
                        ReadingQuestionType.MCQ -> optionsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        ReadingQuestionType.TRUE_FALSE -> listOf("True", "False")
                        ReadingQuestionType.SHORT_ANSWER -> emptyList()
                    }
                    onAdd(
                        ReadingQuestion(
                            questionId = "",
                            type = type,
                            prompt = prompt.trim(),
                            options = opts,
                            correctAnswer = correct.trim(),
                            explanationBn = explanationBn.trim()
                        )
                    )
                },
                enabled = prompt.isNotBlank() && correct.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
