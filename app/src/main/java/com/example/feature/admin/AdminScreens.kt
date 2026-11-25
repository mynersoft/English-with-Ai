package com.example.feature.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.vocabulary.VocabAdminPanel
import com.example.feature.vocabulary.VocabAdminUiState
import com.example.feature.vocabulary.VocabAdminViewModel

@Composable
fun AdminRoute(
    onBack: () -> Unit,
    vm: AdminViewModel = viewModel(),
    vocabAdminVm: VocabAdminViewModel = viewModel()
) {
    val access by vm.access.collectAsStateWithLifecycle()
    val patternListState by vm.list.collectAsStateWithLifecycle()
    val vocabAdminState by vocabAdminVm.state.collectAsStateWithLifecycle()

    var showPatternEditor by rememberSaveable { mutableStateOf(false) }
    var activeSectionTab by rememberSaveable { mutableIntStateOf(0) } // 0=Overview, 1=Patterns, 2=Vocabulary
    var vocabSubTab by rememberSaveable { mutableIntStateOf(0) }
    var openAddWordTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.verifyAccessAndLoad()
    }

    LaunchedEffect(access) {
        if (access is AdminAccessState.Allowed) {
            vocabAdminVm.loadInitial()
        }
    }

    BackHandler {
        if (showPatternEditor) {
            showPatternEditor = false
            vm.loadList()
        } else {
            onBack()
        }
    }

    when (val gate = access) {
        is AdminAccessState.Checking -> {
            AdminAccessGateScreen(
                checking = true,
                reason = "Verifying administrator permissions...",
                onBack = onBack,
                onRetry = { vm.verifyAccessAndLoad() }
            )
        }
        is AdminAccessState.Denied -> {
            AdminAccessGateScreen(
                checking = false,
                reason = gate.reason,
                onBack = onBack,
                onRetry = { vm.verifyAccessAndLoad() }
            )
        }
        is AdminAccessState.Allowed -> {
            if (showPatternEditor) {
                AdminEditScreen(
                    vm = vm,
                    onClose = {
                        showPatternEditor = false
                        vm.loadList()
                    }
                )
            } else {
                AdminDashboardHub(
                    activeTab = activeSectionTab,
                    onSelectTab = { activeSectionTab = it },
                    patternState = patternListState,
                    vocabState = vocabAdminState,
                    vocabSubTab = vocabSubTab,
                    openAddWordTrigger = openAddWordTrigger,
                    onBack = onBack,
                    onRefreshAll = {
                        vm.loadList()
                        vocabAdminVm.loadInitial()
                    },
                    onNewPattern = {
                        vm.openNew()
                        showPatternEditor = true
                    },
                    onEditPattern = { id ->
                        vm.openEdit(id)
                        showPatternEditor = true
                    },
                    onDeletePattern = { id -> vm.deletePattern(id) },
                    onClearPatternMessage = { vm.clearListMessage() },
                    onQuickAddVocabulary = {
                        vocabSubTab = 0
                        activeSectionTab = 2
                        openAddWordTrigger++
                    },
                    onQuickManageCategories = {
                        vocabSubTab = 1
                        activeSectionTab = 2
                    },
                    onQuickCsvImport = {
                        vocabSubTab = 2
                        activeSectionTab = 2
                    },
                    vocabAdminVm = vocabAdminVm
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminAccessGateScreen(
    checking: Boolean,
    reason: String,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (checking) {
                    CircularProgressIndicator()
                    Text(reason, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Admin Access Only",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Text("Go Back")
                        }
                        Button(onClick = onRetry, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry Check")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminDashboardHub(
    activeTab: Int,
    onSelectTab: (Int) -> Unit,
    patternState: AdminListState,
    vocabState: VocabAdminUiState,
    vocabSubTab: Int,
    openAddWordTrigger: Int,
    onBack: () -> Unit,
    onRefreshAll: () -> Unit,
    onNewPattern: () -> Unit,
    onEditPattern: (String) -> Unit,
    onDeletePattern: (String) -> Unit,
    onClearPatternMessage: () -> Unit,
    onQuickAddVocabulary: () -> Unit,
    onQuickManageCategories: () -> Unit,
    onQuickCsvImport: () -> Unit,
    vocabAdminVm: VocabAdminViewModel
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmDeletePattern by remember { mutableStateOf<AdminPatternSummary?>(null) }

    LaunchedEffect(patternState.message) {
        patternState.message?.let {
            snackbar.showSnackbar(it)
            onClearPatternMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_dashboard_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefreshAll,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_dashboard_refresh_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh admin data")
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeTab == 1) {
                FloatingActionButton(
                    onClick = onNewPattern,
                    modifier = Modifier.testTag("admin_fab_new_pattern")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Post new pattern")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabRow(
                selectedTabIndex = activeTab,
                modifier = Modifier.widthIn(max = 600.dp)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { onSelectTab(0) },
                    text = { Text("Overview") },
                    modifier = Modifier.testTag("admin_tab_overview")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { onSelectTab(1) },
                    text = { Text("Patterns (${patternState.patterns.size})") },
                    modifier = Modifier.testTag("admin_tab_patterns")
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { onSelectTab(2) },
                    text = { Text("Vocabulary (${vocabState.total})") },
                    modifier = Modifier.testTag("admin_tab_vocabulary")
                )
            }

            when (activeTab) {
                0 -> AdminOverviewTab(
                    patternCount = patternState.patterns.size,
                    vocabCount = vocabState.total,
                    categoryCount = vocabState.categories.size,
                    onPostNewPattern = onNewPattern,
                    onOpenPatterns = { onSelectTab(1) },
                    onPostNewVocabulary = onQuickAddVocabulary,
                    onManageCategories = onQuickManageCategories,
                    onCsvImport = onQuickCsvImport
                )
                1 -> AdminPatternsListTab(
                    state = patternState,
                    onNewPattern = onNewPattern,
                    onEditPattern = onEditPattern,
                    onRequestDelete = { confirmDeletePattern = it }
                )
                2 -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    VocabAdminPanel(
                        state = vocabState,
                        requestedSubTab = vocabSubTab,
                        openAddWordTrigger = openAddWordTrigger,
                        onClearMessage = { vocabAdminVm.clearMessage() },
                        onFilterChange = { s, l, c -> vocabAdminVm.updateFilters(s, l, c) },
                        onPageChange = { p -> vocabAdminVm.loadWords(p) },
                        onSaveWord = { w, done -> vocabAdminVm.saveWord(w, done) },
                        onTogglePublish = { id, pub -> vocabAdminVm.togglePublish(id, pub) },
                        onDeleteWord = { id -> vocabAdminVm.deleteWord(id) },
                        onSaveCategory = { id, name, desc, parent, active ->
                            vocabAdminVm.saveCategory(id, name, desc, parent, active)
                        },
                        onDeleteCategory = { id -> vocabAdminVm.deleteCategory(id) },
                        onImportCsv = { csv -> vocabAdminVm.importCsv(csv) }
                    )
                }
            }
        }
    }

    confirmDeletePattern?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDeletePattern = null },
            title = { Text("Delete pattern?") },
            text = { Text("\"${p.title}\" and all its audio will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePattern(p.id)
                    confirmDeletePattern = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePattern = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminOverviewTab(
    patternCount: Int,
    vocabCount: Int,
    categoryCount: Int,
    onPostNewPattern: () -> Unit,
    onOpenPatterns: () -> Unit,
    onPostNewVocabulary: () -> Unit,
    onManageCategories: () -> Unit,
    onCsvImport: () -> Unit
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AdminStatTile("Patterns", patternCount.toString(), Modifier.weight(1f))
                AdminStatTile("Words", vocabCount.toString(), Modifier.weight(1f))
                AdminStatTile("Categories", categoryCount.toString(), Modifier.weight(1f))
            }
        }

        item {
            Text(
                text = "Quick Content Actions (Admin Only)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            AdminActionCard(
                icon = Icons.Filled.Add,
                title = "Post New Sentence Pattern",
                subtitle = "Create a new pattern, add Bangla examples & generate TTS audio",
                testTag = "admin_quick_post_pattern",
                onClick = onPostNewPattern
            )
        }

        item {
            AdminActionCard(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Manage & Edit Patterns",
                subtitle = "Edit existing patterns, upload custom voice, or regenerate audio",
                testTag = "admin_quick_manage_patterns",
                onClick = onOpenPatterns
            )
        }

        item {
            AdminActionCard(
                icon = Icons.Filled.Translate,
                title = "Post New Vocabulary Word",
                subtitle = "Add word, Bangla meaning, IPA, examples, synonyms & CEFR level",
                testTag = "admin_quick_post_vocab",
                onClick = onPostNewVocabulary
            )
        }

        item {
            AdminActionCard(
                icon = Icons.Filled.Category,
                title = "Manage Vocabulary Categories",
                subtitle = "Add, edit, activate, or remove parent & sub categories",
                testTag = "admin_quick_categories",
                onClick = onManageCategories
            )
        }

        item {
            AdminActionCard(
                icon = Icons.Filled.UploadFile,
                title = "Bulk CSV Vocabulary Import",
                subtitle = "Paste CSV rows with duplicate detection & validation",
                testTag = "admin_quick_csv_import",
                onClick = onCsvImport
            )
        }
    }
}

@Composable
private fun AdminStatTile(label: String, value: String, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminPatternsListTab(
    state: AdminListState,
    onNewPattern: () -> Unit,
    onEditPattern: (String) -> Unit,
    onRequestDelete: (AdminPatternSummary) -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
    ) {
        when {
            state.loading && state.patterns.isEmpty() ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            state.patterns.isEmpty() ->
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("No patterns yet. Post your first sentence pattern.")
                    Button(onClick = onNewPattern) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Post New Pattern")
                    }
                }

            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.patterns, key = { it.id }) { p ->
                    PatternCard(
                        p = p,
                        onEdit = { onEditPattern(p.id) },
                        onDelete = { onRequestDelete(p) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternCard(p: AdminPatternSummary, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (p.isPremium) {
                    Tag("Premium", Color(0xFFFFE8CC), Color(0xFFB45309), Modifier.padding(start = 8.dp))
                }
            }
            Text(p.meaning, style = MaterialTheme.typography.bodyMedium)
            val outdated = if (p.audioStale > 0) " · ${p.audioStale} outdated" else ""
            Text(
                "#${p.order} · ${p.categoryName} · ${p.sentenceCount} sentences · audio ${p.audioReady}/${p.audioTotal}$outdated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}