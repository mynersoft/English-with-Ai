package com.example.feature.vocabulary

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.VocabCategorySummary
import com.example.domain.model.VocabExample
import com.example.domain.model.VocabWord

@Composable
fun VocabAdminScreen(
    isAdmin: Boolean,
    state: VocabAdminUiState,
    onBack: () -> Unit,
    onInit: () -> Unit,
    onFilterChange: (search: String?, level: String?, categoryId: String?) -> Unit,
    onPageChange: (Int) -> Unit,
    onSaveWord: (VocabWord, onDone: () -> Unit) -> Unit,
    onTogglePublish: (wordId: String, nextPublished: Boolean) -> Unit,
    onDeleteWord: (wordId: String) -> Unit,
    onSaveCategory: (id: String?, name: String, desc: String, parentId: String?, isActive: Boolean) -> Unit,
    onDeleteCategory: (categoryId: String) -> Unit,
    onImportCsv: (String) -> Unit
) {
    val pal = vocabPalette()
    LaunchedEffect(isAdmin) {
        if (isAdmin) onInit()
    }

    var tabIndex by remember { mutableIntStateOf(0) }
    var editingWord by remember { mutableStateOf<VocabWord?>(null) }
    var showWordDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        topBar = {
            VocabTopBar(
                title = "Vocabulary Admin",
                subtitle = "Words, Categories & CSV Bulk Import",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (!isAdmin) {
                VocabEmptyState(
                    title = "Admin access required",
                    subtitle = "Only verified administrators can manage vocabulary content.",
                    modifier = Modifier.widthIn(max = 600.dp)
                )
                return@Box
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
            ) {
                TabRow(selectedTabIndex = tabIndex, containerColor = pal.background) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Words (${state.total})") })
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Categories") })
                    Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("CSV Import") })
                }

                if (!state.message.isNullOrBlank()) {
                    VocabFeedbackBanner(
                        message = state.message,
                        isError = state.isError,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                when (tabIndex) {
                    0 -> AdminWordsTab(
                        state = state,
                        onFilterChange = onFilterChange,
                        onPageChange = onPageChange,
                        onAddWord = {
                            editingWord = null
                            showWordDialog = true
                        },
                        onEditWord = {
                            editingWord = it
                            showWordDialog = true
                        },
                        onTogglePublish = onTogglePublish,
                        onDeleteWord = onDeleteWord
                    )
                    1 -> AdminCategoriesTab(
                        categories = state.categories,
                        saving = state.saving,
                        onSaveCategory = onSaveCategory,
                        onDeleteCategory = onDeleteCategory
                    )
                    2 -> AdminCsvImportTab(
                        saving = state.saving,
                        summary = state.importSummary,
                        onImportCsv = onImportCsv
                    )
                }
            }
        }
    }

    if (showWordDialog) {
        AdminWordFormDialog(
            initial = editingWord,
            categories = state.categories,
            saving = state.saving,
            onDismiss = { showWordDialog = false },
            onSave = { word ->
                onSaveWord(word) { showWordDialog = false }
            }
        )
    }
}

@Composable
private fun AdminWordsTab(
    state: VocabAdminUiState,
    onFilterChange: (search: String?, level: String?, categoryId: String?) -> Unit,
    onPageChange: (Int) -> Unit,
    onAddWord: () -> Unit,
    onEditWord: (VocabWord) -> Unit,
    onTogglePublish: (String, Boolean) -> Unit,
    onDeleteWord: (String) -> Unit
) {
    val pal = vocabPalette()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = { onFilterChange(it, null, null) },
                placeholder = { Text("Search word or meaning...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onAddWord,
                colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(52.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("vocab_admin_add_word_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add word")
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("ALL", "A1", "A2", "B1", "B2", "C1", "C2")) { lvl ->
                FilterChip(
                    selected = state.levelFilter == lvl,
                    onClick = { onFilterChange(null, lvl, null) },
                    label = { Text(lvl) }
                )
            }
        }

        if (state.loading) {
            VocabLoadingSkeleton(lines = 3)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(state.words, key = { it.id }) { word ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = pal.card,
                        border = BorderStroke(1.dp, pal.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${word.word} (${word.level})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                                Text(word.meaning, style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                            }
                            Switch(
                                checked = word.isPublished,
                                onCheckedChange = { onTogglePublish(word.id, it) }
                            )
                            IconButton(
                                onClick = { onEditWord(word) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = pal.primary)
                            }
                            IconButton(
                                onClick = { onDeleteWord(word.id) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = pal.danger)
                            }
                        }
                    }
                }
                if (state.totalPages > 1) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { onPageChange(state.page - 1) }, enabled = state.page > 1) { Text("Prev") }
                            Text("Page ${state.page}/${state.totalPages}", color = pal.textSecondary)
                            OutlinedButton(onClick = { onPageChange(state.page + 1) }, enabled = state.page < state.totalPages) { Text("Next") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminWordFormDialog(
    initial: VocabWord?,
    categories: List<VocabCategorySummary>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (VocabWord) -> Unit
) {
    val flatCategories = remember(categories) {
        categories.flatMap { parent -> listOf(parent) + parent.subcategories }
    }
    var wordText by remember(initial) { mutableStateOf(initial?.word ?: "") }
    var meaningText by remember(initial) { mutableStateOf(initial?.meaning ?: "") }
    var ipaText by remember(initial) { mutableStateOf(initial?.pronunciation ?: "") }
    var posText by remember(initial) { mutableStateOf(initial?.partOfSpeech ?: "noun") }
    var levelText by remember(initial) { mutableStateOf(initial?.level ?: "A1") }
    var catId by remember(initial, flatCategories) {
        mutableStateOf(initial?.categoryId?.ifBlank { flatCategories.firstOrNull()?.id.orEmpty() } ?: flatCategories.firstOrNull()?.id.orEmpty())
    }
    var ex1En by remember(initial) { mutableStateOf(initial?.examples?.getOrNull(0)?.en ?: "") }
    var ex1Bn by remember(initial) { mutableStateOf(initial?.examples?.getOrNull(0)?.bn ?: "") }
    var ex2En by remember(initial) { mutableStateOf(initial?.examples?.getOrNull(1)?.en ?: "") }
    var ex2Bn by remember(initial) { mutableStateOf(initial?.examples?.getOrNull(1)?.bn ?: "") }
    var synText by remember(initial) { mutableStateOf(initial?.synonyms?.joinToString(", ") ?: "") }
    var antText by remember(initial) { mutableStateOf(initial?.antonyms?.joinToString(", ") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Vocabulary Word" else "Edit Word") },
        text = {
            LazyColumn(
                modifier = Modifier.height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(wordText, { wordText = it }, label = { Text("English Word*") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(meaningText, { meaningText = it }, label = { Text("Bengali Meaning*") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ipaText, { ipaText = it }, label = { Text("IPA Pronunciation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(posText, { posText = it }, label = { Text("Part of Speech") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Text("CEFR Level", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("A1", "A2", "B1", "B2", "C1", "C2")) { lvl ->
                            FilterChip(selected = levelText == lvl, onClick = { levelText = lvl }, label = { Text(lvl) })
                        }
                    }
                    if (flatCategories.isNotEmpty()) {
                        Text("Category", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(flatCategories, key = { it.id }) { c ->
                                FilterChip(selected = catId == c.id, onClick = { catId = c.id }, label = { Text(c.name) })
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(ex1En, { ex1En = it }, label = { Text("Example 1 (English)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ex1Bn, { ex1Bn = it }, label = { Text("Example 1 (Bengali)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ex2En, { ex2En = it }, label = { Text("Example 2 (English)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ex2Bn, { ex2Bn = it }, label = { Text("Example 2 (Bengali)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(synText, { synText = it }, label = { Text("Synonyms (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(antText, { antText = it }, label = { Text("Antonyms (comma-separated)") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val examples = listOfNotNull(
                        if (ex1En.isNotBlank() && ex1Bn.isNotBlank()) VocabExample(ex1En.trim(), ex1Bn.trim()) else null,
                        if (ex2En.isNotBlank() && ex2Bn.isNotBlank()) VocabExample(ex2En.trim(), ex2Bn.trim()) else null
                    )
                    onSave(
                        VocabWord(
                            id = initial?.id.orEmpty(),
                            word = wordText.trim(),
                            meaning = meaningText.trim(),
                            pronunciation = ipaText.trim(),
                            partOfSpeech = posText.trim().ifBlank { "noun" },
                            level = levelText,
                            categoryId = catId,
                            examples = examples,
                            synonyms = synText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            antonyms = antText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            isPublished = initial?.isPublished ?: true
                        )
                    )
                },
                enabled = !saving && wordText.isNotBlank() && meaningText.isNotBlank() && catId.isNotBlank()
            ) { Text(if (saving) "Saving..." else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AdminCategoriesTab(
    categories: List<VocabCategorySummary>,
    saving: Boolean,
    onSaveCategory: (id: String?, name: String, desc: String, parentId: String?, isActive: Boolean) -> Unit,
    onDeleteCategory: (String) -> Unit
) {
    val pal = vocabPalette()
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = pal.card,
                border = BorderStroke(1.dp, pal.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create Category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, label = { Text("Category Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onSaveCategory(null, name, desc, null, true)
                            name = ""
                            desc = ""
                        },
                        enabled = !saving && name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add Category") }
                }
            }
        }
        items(categories, key = { it.id }) { cat ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = pal.card,
                border = BorderStroke(1.dp, pal.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(cat.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("${cat.totalWordCount} words · ${cat.subcategories.size} subtopics", style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                    }
                    IconButton(onClick = { onDeleteCategory(cat.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete category", tint = pal.danger)
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCsvImportTab(
    saving: Boolean,
    summary: com.example.domain.model.CsvImportSummary?,
    onImportCsv: (String) -> Unit
) {
    val pal = vocabPalette()
    var csvText by remember {
        mutableStateOf(
            "word,meaning,pronunciation,partOfSpeech,level,category,example1En,example1Bn,example2En,example2Bn,synonyms,antonyms\n"
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Paste CSV rows below. Duplicate words are detected and skipped automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = pal.textSecondary
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = csvText,
                onValueChange = { csvText = it },
                label = { Text("CSV Content") },
                minLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vocab_admin_csv_input")
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onImportCsv(csvText) },
                enabled = !saving && csvText.lines().size >= 2,
                colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (saving) "Importing..." else "Import CSV Words", fontWeight = FontWeight.Bold)
            }
        }
        summary?.let { s ->
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = pal.primaryContainer,
                    border = BorderStroke(1.dp, pal.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Import Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Total rows: ${s.totalRows} · Inserted: ${s.inserted} · Duplicates skipped: ${s.duplicates}")
                        s.errors.take(5).forEach { err ->
                            Text("Error: $err", style = MaterialTheme.typography.bodySmall, color = pal.danger)
                        }
                    }
                }
            }
        }
    }
}
