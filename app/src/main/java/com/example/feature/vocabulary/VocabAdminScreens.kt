package com.example.feature.vocabulary

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.VocabCategorySummary
import com.example.domain.model.VocabExample
import com.example.domain.model.VocabWord
import kotlinx.coroutines.launch

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

            VocabAdminPanel(
                state = state,
                onFilterChange = onFilterChange,
                onPageChange = onPageChange,
                onSaveWord = onSaveWord,
                onTogglePublish = onTogglePublish,
                onDeleteWord = onDeleteWord,
                onSaveCategory = onSaveCategory,
                onDeleteCategory = onDeleteCategory,
                onImportCsv = onImportCsv
            )
        }
    }
}

@Composable
fun VocabAdminPanel(
    state: VocabAdminUiState,
    requestedSubTab: Int? = null,
    openAddWordTrigger: Int = 0,
    onClearMessage: () -> Unit = {},
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
    var tabIndex by remember { mutableIntStateOf(requestedSubTab ?: 0) }
    var editingWord by remember { mutableStateOf<VocabWord?>(null) }
    var showWordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(requestedSubTab) {
        if (requestedSubTab != null && requestedSubTab in 0..2) {
            tabIndex = requestedSubTab
        }
    }
    LaunchedEffect(openAddWordTrigger) {
        if (openAddWordTrigger > 0) {
            tabIndex = 0
            editingWord = null
            onClearMessage()
            showWordDialog = true
        }
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
                    onClearMessage()
                    showWordDialog = true
                },
                onEditWord = {
                    editingWord = it
                    onClearMessage()
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

    if (showWordDialog) {
        AdminWordFormDialog(
            initial = editingWord,
            categories = state.categories,
            saving = state.saving,
            errorMessage = if (state.isError) state.message else null,
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
    errorMessage: String? = null,
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
    var catId by remember(initial) {
        mutableStateOf(initial?.categoryId?.takeIf { it.isNotBlank() } ?: flatCategories.firstOrNull()?.id.orEmpty())
    }
    var customCategoryName by remember(initial) {
        mutableStateOf(initial?.categoryName?.takeIf { it.isNotBlank() } ?: "Daily Life")
    }
    LaunchedEffect(flatCategories) {
        if (catId.isBlank() && flatCategories.isNotEmpty()) {
            catId = flatCategories.first().id
        }
    }
    var ex1En by remember(initial) { mutableStateOf(initial?.examples?.getOrNull(0)?.en ?: "") }
    var ex1Bn by remember(initial) { mutableStateOf(initial?.examples?.getOrNull(0)?.bn ?: "") }
    var ex2En by remember(initial) { mutableStateOf(initial?.examples?.getOrNull(1)?.en ?: "") }
    var ex2Bn by remember(initial) { mutableStateOf(initial?.examples?.getOrNull(1)?.bn ?: "") }
    var synText by remember(initial) { mutableStateOf(initial?.synonyms?.joinToString(", ") ?: "") }
    var antText by remember(initial) { mutableStateOf(initial?.antonyms?.joinToString(", ") ?: "") }

    val hasValidCategory = catId.isNotBlank() || customCategoryName.isNotBlank()
    val canSave = !saving && wordText.isNotBlank() && meaningText.isNotBlank() && hasValidCategory

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
                    } else {
                        OutlinedTextField(
                            value = customCategoryName,
                            onValueChange = { customCategoryName = it },
                            label = { Text("Category Name*") },
                            supportingText = { Text("No categories yet — will be created automatically") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                if (!errorMessage.isNullOrBlank()) {
                    item {
                        VocabFeedbackBanner(message = errorMessage, isError = true)
                    }
                }
                if (wordText.isBlank() || meaningText.isBlank()) {
                    item {
                        Text(
                            text = "Enter both English Word and Bengali Meaning to enable Save.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                            categoryName = customCategoryName.trim(),
                            examples = examples,
                            synonyms = synText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            antonyms = antText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            isPublished = initial?.isPublished ?: true
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier.testTag("vocab_admin_save_word_button")
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
    var editingCategory by remember { mutableStateOf<VocabCategorySummary?>(null) }

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
                        Text(
                            text = if (cat.isActive) cat.name else "${cat.name} (Inactive)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text("${cat.totalWordCount} words · ${cat.subcategories.size} subtopics", style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                    }
                    IconButton(onClick = { editingCategory = cat }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit category", tint = pal.primary)
                    }
                    IconButton(onClick = { onDeleteCategory(cat.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete category", tint = pal.danger)
                    }
                }
            }
        }
    }

    editingCategory?.let { cat ->
        var editName by remember(cat) { mutableStateOf(cat.name) }
        var editDesc by remember(cat) { mutableStateOf(cat.description) }
        var editActive by remember(cat) { mutableStateOf(cat.isActive) }
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("Edit Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(editName, { editName = it }, label = { Text("Category Name*") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editDesc, { editDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Active")
                        Switch(checked = editActive, onCheckedChange = { editActive = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCategory(cat.id, editName, editDesc, cat.parentId, editActive)
                        editingCategory = null
                    },
                    enabled = !saving && editName.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminCsvImportTab(
    saving: Boolean,
    summary: com.example.domain.model.CsvImportSummary?,
    onImportCsv: (String) -> Unit
) {
    val pal = vocabPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var csvText by remember { mutableStateOf("${CsvImportHelper.DEFAULT_HEADER}\n") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var largeFileCsv by remember { mutableStateOf<String?>(null) }
    var readingFile by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }

    val activeCsv = largeFileCsv ?: csvText
    val detectedRows = remember(activeCsv) { CsvImportHelper.countDataRows(activeCsv) }

    val csvFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        readingFile = true
        fileError = null
        scope.launch {
            CsvImportHelper.readCsvFromUri(context, uri)
                .onSuccess { (name, content) ->
                    selectedFileName = name
                    if (content.length > 60_000) {
                        largeFileCsv = content
                        csvText = content.lineSequence().take(15).joinToString("\n")
                    } else {
                        largeFileCsv = null
                        csvText = content
                    }
                    readingFile = false
                }
                .onFailure { err ->
                    readingFile = false
                    fileError = err.message ?: "Could not read CSV file."
                }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = pal.card,
                border = BorderStroke(1.dp, pal.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Import from .CSV File or Paste Rows",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = pal.textPrimary
                    )
                    Text(
                        text = "Supports UTF-8 CSV files with header row (word, meaning, pronunciation, partOfSpeech, level, category, example1En, example1Bn, synonyms, antonyms). Duplicates are skipped automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = pal.textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                csvFilePicker.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/csv",
                                        "application/vnd.ms-excel",
                                        "text/plain"
                                    )
                                )
                            },
                            enabled = !saving && !readingFile,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("vocab_admin_pick_csv_button")
                        ) {
                            if (readingFile) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (readingFile) "Reading..." else "Choose .csv File")
                        }
                        OutlinedButton(
                            onClick = {
                                selectedFileName = null
                                largeFileCsv = null
                                fileError = null
                                csvText = CsvImportHelper.SAMPLE_CSV
                            },
                            enabled = !saving && !readingFile,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("vocab_admin_sample_csv_button")
                        ) {
                            Text("Sample")
                        }
                    }

                    selectedFileName?.let { fileName ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = pal.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = pal.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$detectedRows word row(s) ready" +
                                            if (largeFileCsv != null) " (showing first 15 lines below)" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = pal.textSecondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedFileName = null
                                        largeFileCsv = null
                                        fileError = null
                                        csvText = "${CsvImportHelper.DEFAULT_HEADER}\n"
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear selected file")
                                }
                            }
                        }
                    }

                    fileError?.let { err ->
                        VocabFeedbackBanner(message = err, isError = true)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = csvText,
                onValueChange = {
                    largeFileCsv = null
                    fileError = null
                    csvText = it
                },
                label = {
                    Text(
                        if (detectedRows > 0) "CSV Content ($detectedRows row${if (detectedRows == 1) "" else "s"})"
                        else "CSV Content"
                    )
                },
                minLines = 7,
                maxLines = 12,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("vocab_admin_csv_input")
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onImportCsv(activeCsv) },
                enabled = !saving && !readingFile && detectedRows >= 1,
                colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("vocab_admin_import_csv_button")
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        saving -> "Importing..."
                        detectedRows > 0 -> "Import $detectedRows Word${if (detectedRows == 1) "" else "s"}"
                        else -> "Import CSV Words"
                    },
                    fontWeight = FontWeight.Bold
                )
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
                        s.errors.take(10).forEach { err ->
                            Text("Error: $err", style = MaterialTheme.typography.bodySmall, color = pal.danger)
                        }
                    }
                }
            }
        }
    }
}
