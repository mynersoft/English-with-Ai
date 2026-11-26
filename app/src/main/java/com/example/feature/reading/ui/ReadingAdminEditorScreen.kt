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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun editorChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
)

@Composable
private fun AdminPassageCefrBadge(level: String) {
    val (bg, fg) = when (level.uppercase()) {
        "A1", "A2" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "B1", "B2" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = level.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

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
    val colors = MaterialTheme.colorScheme
    val fieldColors = editorFieldColors()
    val chipColors = editorChipColors()
    var showAddVocabDialog by remember { mutableStateOf(false) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
        contentColor = colors.onBackground
    ) {
        if (editorState.loadingDetail) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            return@Surface
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .imePadding(),
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
                            color = colors.onBackground
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onTogglePreview,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                            ) {
                                Icon(
                                    imageVector = if (editorState.previewMode) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (editorState.previewMode) "Edit" else "Preview")
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close editor",
                                    tint = colors.onBackground
                                )
                            }
                        }
                    }
                }

                if (!editorState.validationError.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = editorState.validationError,
                                color = colors.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                if (editorState.previewMode) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surface,
                            contentColor = colors.onSurface,
                            border = BorderStroke(1.dp, colors.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AdminPassageCefrBadge(editorState.level)
                                    Text(
                                        text = "${editorState.estimatedMinutes} min",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = editorState.title.ifBlank { "(Untitled)" },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                                if (editorState.summary.isNotBlank()) {
                                    Text(
                                        text = editorState.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Split Sentences (${editorState.previewSentences.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                editorState.previewSentences.forEachIndexed { idx, s ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.surfaceVariant,
                                        contentColor = colors.onSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${idx + 1}. ${s.en}",
                                            color = colors.onSurface,
                                            modifier = Modifier.padding(10.dp),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                                if (editorState.bengaliTranslation.isNotBlank()) {
                                    Text(
                                        text = "Bengali Translation",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = editorState.bengaliTranslation,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.onSurface
                                    )
                                }
                                Text(
                                    text = "Vocabulary: ${editorState.vocabulary.size} · Questions: ${editorState.questions.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
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
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_passage_title_input")
                        )
                    }

                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "CEFR Level",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReadingCefrLevel.entries.filter { it != ReadingCefrLevel.ALL }.forEach { lvl ->
                                    FilterChip(
                                        selected = editorState.level == lvl.code,
                                        onClick = { onUpdate { it.copy(level = lvl.code) } },
                                        label = { Text(lvl.code) },
                                        colors = chipColors
                                    )
                                }
                            }

                            if (categories.isNotEmpty()) {
                                Text(
                                    text = "Category",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    categories.forEach { cat ->
                                        FilterChip(
                                            selected = editorState.categoryId == cat.id,
                                            onClick = { onUpdate { it.copy(categoryId = cat.id) } },
                                            label = { Text(cat.name) },
                                            colors = chipColors
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
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = editorState.englishText,
                            onValueChange = { v -> onUpdate { it.copy(englishText = v) } },
                            label = { Text("English Passage Text * (${editorState.previewSentences.size} sentences)") },
                            minLines = 5,
                            colors = fieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_passage_english_input")
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = editorState.bengaliTranslation,
                            onValueChange = { v -> onUpdate { it.copy(bengaliTranslation = v) } },
                            label = { Text("Bengali Translation") },
                            minLines = 3,
                            colors = fieldColors,
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
                                colors = fieldColors,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = editorState.estimatedMinutes.toString(),
                                onValueChange = { v ->
                                    onUpdate { it.copy(estimatedMinutes = v.filter(Char::isDigit).toIntOrNull() ?: 3) }
                                },
                                label = { Text("Minutes") },
                                singleLine = true,
                                colors = fieldColors,
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
                            Text(
                                text = "Published (visible to learners)",
                                fontWeight = FontWeight.SemiBold,
                                color = colors.onBackground
                            )
                            Switch(
                                checked = editorState.isPublished,
                                onCheckedChange = { pub -> onUpdate { it.copy(isPublished = pub) } }
                            )
                        }
                    }

                    // Vocabulary items section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Passage Vocabulary (${editorState.vocabulary.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onBackground
                            )
                            OutlinedButton(
                                onClick = { showAddVocabDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                            ) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Word")
                            }
                        }
                    }

                    items(editorState.vocabulary.size) { idx ->
                        val v = editorState.vocabulary[idx]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface,
                            contentColor = colors.onSurface,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${v.word} — ${v.meaningBn}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface
                                    )
                                    if (v.partOfSpeech.isNotBlank()) {
                                        Text(
                                            text = v.partOfSpeech,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveVocab(idx) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Remove word",
                                        tint = colors.error
                                    )
                                }
                            }
                        }
                    }

                    // Questions section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Comprehension Questions (${editorState.questions.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.onBackground
                            )
                            OutlinedButton(
                                onClick = { showAddQuestionDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                            ) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Question")
                            }
                        }
                    }

                    items(editorState.questions.size) { idx ->
                        val q = editorState.questions[idx]
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surface,
                            contentColor = colors.onSurface,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${idx + 1}. [${q.type.displayLabel}] ${q.prompt}",
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = "Answer: ${q.correctAnswer}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveQuestion(idx) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Remove question",
                                        tint = colors.error
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onSave,
                        enabled = !editorState.saving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_save_reading_passage_button")
                    ) {
                        if (editorState.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Save Passage")
                    }
                }
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
    val colors = MaterialTheme.colorScheme
    val fieldColors = editorFieldColors()
    var word by remember { mutableStateOf("") }
    var meaningBn by remember { mutableStateOf("") }
    var pronunciation by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf("") }
    var example by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = { Text("Add Passage Vocabulary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = word, onValueChange = { word = it }, label = { Text("English Word *") }, singleLine = true, colors = fieldColors)
                OutlinedTextField(value = meaningBn, onValueChange = { meaningBn = it }, label = { Text("Bengali Meaning") }, singleLine = true, colors = fieldColors)
                OutlinedTextField(value = pronunciation, onValueChange = { pronunciation = it }, label = { Text("Pronunciation (IPA)") }, singleLine = true, colors = fieldColors)
                OutlinedTextField(value = pos, onValueChange = { pos = it }, label = { Text("Part of Speech") }, singleLine = true, colors = fieldColors)
                OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("Example Sentence") }, colors = fieldColors)
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
    val colors = MaterialTheme.colorScheme
    val fieldColors = editorFieldColors()
    val chipColors = editorChipColors()
    var type by remember { mutableStateOf(ReadingQuestionType.MCQ) }
    var prompt by remember { mutableStateOf("") }
    var optionsCsv by remember { mutableStateOf("") }
    var correct by remember { mutableStateOf("") }
    var explanationBn by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurface,
        title = { Text("Add Comprehension Question") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ReadingQuestionType.entries.forEach { qt ->
                        FilterChip(
                            selected = type == qt,
                            onClick = { type = qt },
                            label = { Text(qt.displayLabel) },
                            colors = chipColors
                        )
                    }
                }
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Question Prompt *") }, colors = fieldColors)
                if (type == ReadingQuestionType.MCQ) {
                    OutlinedTextField(value = optionsCsv, onValueChange = { optionsCsv = it }, label = { Text("Options (comma-separated)") }, colors = fieldColors)
                }
                if (type == ReadingQuestionType.TRUE_FALSE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("True", "False").forEach { tf ->
                            FilterChip(
                                selected = correct == tf,
                                onClick = { correct = tf },
                                label = { Text(tf) },
                                colors = chipColors
                            )
                        }
                    }
                } else {
                    OutlinedTextField(value = correct, onValueChange = { correct = it }, label = { Text("Correct Answer *") }, singleLine = true, colors = fieldColors)
                }
                OutlinedTextField(value = explanationBn, onValueChange = { explanationBn = it }, label = { Text("Bengali Explanation") }, colors = fieldColors)
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
