package com.example.feature.reading.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unpublished
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.ReadingCategory
import com.example.domain.model.ReadingCefrLevel
import com.example.domain.model.ReadingPassageSummary
import com.example.feature.reading.ReadingAdminViewModel

@Composable
fun ReadingAdminScreen(
    onBack: () -> Unit,
    vm: ReadingAdminViewModel = viewModel()
) {
    val pal = readingPalette()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState by vm.listState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadInitial() }
    LaunchedEffect(listState.message, listState.error) {
        val msg = listState.message ?: listState.error
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearBanner()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ReadingTopBar(
                title = "Reading Admin",
                subtitle = "Passages, questions & categories",
                onBack = onBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            ReadingAdminPanel(vm = vm)
        }
    }
}

@Composable
fun ReadingAdminPanel(
    vm: ReadingAdminViewModel = viewModel()
) {
    val listState by vm.listState.collectAsStateWithLifecycle()
    val editorState by vm.editorState.collectAsStateWithLifecycle()
    val pal = readingPalette()
    var subTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Passages, 1 = Categories
    var deletePassageTarget by remember { mutableStateOf<ReadingPassageSummary?>(null) }
    var deleteCategoryTarget by remember { mutableStateOf<ReadingCategory?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<ReadingCategory?>(null) }

    LaunchedEffect(Unit) {
        if (listState.passages.isEmpty() && !listState.loading) {
            vm.loadInitial()
        }
    }

    if (editorState.isEditing) {
        ReadingAdminEditorScreen(
            editorState = editorState,
            categories = listState.categories,
            onUpdate = vm::updateEditor,
            onTogglePreview = vm::togglePreviewMode,
            onAddVocab = vm::addVocabItem,
            onRemoveVocab = vm::removeVocabAt,
            onAddQuestion = vm::addQuestion,
            onRemoveQuestion = vm::removeQuestionAt,
            onSave = { vm.savePassage() },
            onClose = vm::closeEditor
        )
        return
    }

    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
    ) {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Passages (${listState.total})") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Categories (${listState.categories.size})") })
        }

        if (subTab == 0) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Button(
                        onClick = vm::openNewPassage,
                        colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("admin_new_reading_passage_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Create Reading Passage")
                    }
                }

                item {
                    OutlinedTextField(
                        value = listState.searchQuery,
                        onValueChange = vm::updateSearch,
                        placeholder = { Text("Search admin passages...") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("all" to "All Status", "true" to "Published", "false" to "Draft").forEach { (key, label) ->
                            FilterChip(
                                selected = listState.filterPublished == key,
                                onClick = { vm.updateFilters(published = key) },
                                label = { Text(label) }
                            )
                        }
                        ReadingCefrLevel.entries.forEach { lvl ->
                            FilterChip(
                                selected = listState.filterLevel == lvl.code,
                                onClick = { vm.updateFilters(level = lvl.code) },
                                label = { Text(lvl.code) }
                            )
                        }
                    }
                }

                if (!listState.error.isNullOrBlank()) {
                    item { Text(listState.error.orEmpty(), color = pal.missing) }
                }

                if (listState.loading && listState.passages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = pal.primary)
                        }
                    }
                } else {
                    items(listState.passages, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = pal.card,
                            border = BorderStroke(1.dp, pal.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CefrBadge(item.level)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (item.isPublished) pal.matchedBg else pal.amberContainer
                                        ) {
                                            Text(
                                                text = if (item.isPublished) "Published" else "Draft",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (item.isPublished) pal.matched else pal.amber,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Row {
                                        IconButton(onClick = { vm.togglePublish(item) }) {
                                            Icon(
                                                imageVector = if (item.isPublished) Icons.Filled.Unpublished else Icons.Filled.Publish,
                                                contentDescription = if (item.isPublished) "Unpublish" else "Publish"
                                            )
                                        }
                                        IconButton(onClick = { vm.openEditPassage(item.id) }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit passage")
                                        }
                                        IconButton(onClick = { deletePassageTarget = item }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete passage", tint = pal.missing)
                                        }
                                    }
                                }
                                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                                Text(
                                    text = "${item.categoryName.ifBlank { "General" }} · ${item.sentenceCount} sentences · ${item.vocabCount} vocab · ${item.questionCount} quiz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pal.textSecondary
                                )
                            }
                        }
                    }

                    if (listState.totalPages > 1) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                OutlinedButton(onClick = { vm.loadPassages(listState.page - 1) }, enabled = listState.page > 1) { Text("Prev") }
                                Text("Page ${listState.page} / ${listState.totalPages}", modifier = Modifier.align(Alignment.CenterVertically))
                                OutlinedButton(onClick = { vm.loadPassages(listState.page + 1) }, enabled = listState.page < listState.totalPages) { Text("Next") }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Button(
                        onClick = {
                            editingCategory = null
                            showCategoryDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Category, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Reading Category")
                    }
                }
                items(listState.categories, key = { it.id }) { cat ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = pal.card,
                        border = BorderStroke(1.dp, pal.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cat.name, fontWeight = FontWeight.Bold, color = pal.textPrimary)
                                Text(
                                    text = "${cat.passageCount} passages · ${if (cat.isActive) "Active" else "Inactive"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pal.textSecondary
                                )
                            }
                            Row {
                                IconButton(onClick = {
                                    editingCategory = cat
                                    showCategoryDialog = true
                                }) { Icon(Icons.Filled.Edit, contentDescription = "Edit category") }
                                IconButton(onClick = { deleteCategoryTarget = cat }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete category", tint = pal.missing)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deletePassageTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { deletePassageTarget = null },
            title = { Text("Delete reading passage?") },
            text = { Text("\"${p.title}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePassage(p.id)
                    deletePassageTarget = null
                }) { Text("Delete", color = pal.missing) }
            },
            dismissButton = { TextButton(onClick = { deletePassageTarget = null }) { Text("Cancel") } }
        )
    }

    deleteCategoryTarget?.let { cat ->
        AlertDialog(
            onDismissRequest = { deleteCategoryTarget = null },
            title = { Text("Delete category?") },
            text = { Text("Delete \"${cat.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(cat.id)
                    deleteCategoryTarget = null
                }) { Text("Delete", color = pal.missing) }
            },
            dismissButton = { TextButton(onClick = { deleteCategoryTarget = null }) { Text("Cancel") } }
        )
    }

    if (showCategoryDialog) {
        CategoryEditDialog(
            existing = editingCategory,
            onDismiss = { showCategoryDialog = false },
            onSave = { name, desc, active ->
                vm.saveCategory(editingCategory?.id, name, desc, active)
                showCategoryDialog = false
            }
        )
    }
}

@Composable
private fun CategoryEditDialog(
    existing: ReadingCategory?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var desc by remember(existing) { mutableStateOf(existing?.description.orEmpty()) }
    var active by remember(existing) { mutableStateOf(existing?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Category" else "Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category Name") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Active")
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, desc, active) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
