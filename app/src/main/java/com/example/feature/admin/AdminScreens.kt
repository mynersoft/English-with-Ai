package com.example.feature.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feature.courses.admin.AdminCourseDetailScreen
import com.example.feature.courses.admin.AdminCourseListUiState
import com.example.feature.courses.admin.AdminCourseViewModel
import com.example.feature.courses.admin.AdminCoursesPanel
import com.example.feature.courses.admin.AdminLessonEditorScreen
import com.example.feature.reading.ReadingAdminListState
import com.example.feature.reading.ReadingAdminViewModel
import com.example.feature.reading.ui.ReadingAdminPanel
import androidx.compose.runtime.CompositionLocalProvider
import com.example.core.ui.SystemBarsAppearance
import com.example.feature.vocabulary.LocalVocabDarkTheme
import com.example.feature.vocabulary.VocabAdminPanel
import com.example.feature.vocabulary.VocabAdminUiState
import com.example.feature.vocabulary.VocabAdminViewModel
import com.example.ui.theme.EnglishAITheme

@Composable
fun AdminRoute(
    onBack: () -> Unit,
    onLogout: () -> Unit = { Firebase.auth.signOut() },
    adminEmail: String = Firebase.auth.currentUser?.email.orEmpty(),
    adminName: String = Firebase.auth.currentUser?.displayName.orEmpty(),
    vm: AdminViewModel = viewModel(),
    dashboardVm: AdminDashboardViewModel = viewModel(),
    vocabAdminVm: VocabAdminViewModel = viewModel(),
    readingAdminVm: ReadingAdminViewModel = viewModel(),
    studentsAdminVm: AdminStudentsViewModel = viewModel(),
    courseAdminVm: AdminCourseViewModel = viewModel(),
    aiAdminVm: AdminAiManagementViewModel = viewModel()
) {
    val access by vm.access.collectAsStateWithLifecycle()
    val dashboardState by dashboardVm.state.collectAsStateWithLifecycle()
    val patternListState by vm.list.collectAsStateWithLifecycle()
    val vocabAdminState by vocabAdminVm.state.collectAsStateWithLifecycle()
    val readingListState by readingAdminVm.listState.collectAsStateWithLifecycle()
    val readingEditorState by readingAdminVm.editorState.collectAsStateWithLifecycle()
    val studentsState by studentsAdminVm.state.collectAsStateWithLifecycle()
    val courseListState by courseAdminVm.listState.collectAsStateWithLifecycle()
    val courseDetailState by courseAdminVm.detailState.collectAsStateWithLifecycle()
    val lessonEditorState by courseAdminVm.editorState.collectAsStateWithLifecycle()
    val aiState by aiAdminVm.state.collectAsStateWithLifecycle()

    var showPatternEditor by rememberSaveable { mutableStateOf(false) }
    var activeSectionTab by rememberSaveable { mutableIntStateOf(0) } // 0=Overview, 1=Patterns, 2=Vocabulary, 3=Reading, 4=Students, 5=Courses, 6=AI
    var vocabSubTab by rememberSaveable { mutableIntStateOf(0) }
    var openAddWordTrigger by remember { mutableIntStateOf(0) }
    var openCreateCourseTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.verifyAccessAndLoad()
    }

    LaunchedEffect(access) {
        if (access is AdminAccessState.Allowed) {
            dashboardVm.loadDashboard()
            vocabAdminVm.loadInitial()
            readingAdminVm.loadInitial()
            studentsAdminVm.loadInitial()
            courseAdminVm.loadInitial()
            aiAdminVm.loadInitial()
        }
    }

    BackHandler {
        when {
            showPatternEditor -> {
                showPatternEditor = false
                vm.loadList()
            }
            readingEditorState.isEditing -> {
                readingAdminVm.closeEditor()
                readingAdminVm.loadInitial()
            }
            lessonEditorState.isOpen -> {
                courseAdminVm.closeLessonEditor()
            }
            courseDetailState.isOpen -> {
                courseAdminVm.closeCourseDetail()
            }
            activeSectionTab == 4 && studentsState.isViewingDetail -> {
                studentsAdminVm.closeStudentDetail()
            }
            else -> onBack()
        }
    }

    EnglishAITheme(darkTheme = true) {
        CompositionLocalProvider(LocalVocabDarkTheme provides true) {
            SystemBarsAppearance(lightStatusBars = false, lightNavigationBars = false)
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
            when {
                showPatternEditor -> {
                    AdminEditScreen(
                        vm = vm,
                        onClose = {
                            showPatternEditor = false
                            vm.loadList()
                        }
                    )
                }
                readingEditorState.isEditing -> {
                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing,
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ReadingAdminPanel(vm = readingAdminVm)
                        }
                    }
                }
                lessonEditorState.isOpen -> {
                    AdminLessonEditorScreen(
                        state = lessonEditorState,
                        inMemoryPreviewLesson = { courseAdminVm.buildInMemoryPreviewLesson() },
                        onBack = { courseAdminVm.closeLessonEditor() },
                        onUpdateFields = { title, subtitle, desc, dur, prem, pub ->
                            courseAdminVm.updateLessonFields(title, subtitle, desc, dur, prem, pub)
                        },
                        onCreateEmptyBlock = { type -> courseAdminVm.createEmptyBlock(type) },
                        onUpsertBlock = { blk -> courseAdminVm.upsertBlock(blk) },
                        onRemoveBlock = { id -> courseAdminVm.removeBlock(id) },
                        onReorderBlocks = { from, to -> courseAdminVm.reorderContentBlocks(from, to) },
                        onUploadBlockMedia = { uri, mediaType, cb ->
                            courseAdminVm.uploadBlockMedia(uri, mediaType, cb)
                        },
                        onOpenFullPreview = { open -> courseAdminVm.setFullPreviewOpen(open) },
                        onOpenBlockPreview = { blk -> courseAdminVm.setBlockPreview(blk) },
                        onSaveDraft = { courseAdminVm.saveLesson(publish = false) },
                        onPublish = { courseAdminVm.saveLesson(publish = true) }
                    )
                }
                courseDetailState.isOpen -> {
                    AdminCourseDetailScreen(
                        state = courseDetailState,
                        onBack = { courseAdminVm.closeCourseDetail() },
                        onPreviewCourse = { courseAdminVm.setCoursePreviewOpen(true) },
                        onDismissPreviewCourse = { courseAdminVm.setCoursePreviewOpen(false) },
                        onSaveModule = { cid, mid, title, desc, pub, onDone ->
                            courseAdminVm.saveModule(cid, mid, title, desc, pub, onDone)
                        },
                        onToggleModulePublish = { mid, pub ->
                            courseAdminVm.toggleModulePublish(mid, pub)
                        },
                        onDeleteModule = { mid -> courseAdminVm.deleteModule(mid) },
                        onMoveModule = { from, to -> courseAdminVm.moveModule(from, to) },
                        onAddLesson = { cid, mid, mTitle ->
                            courseAdminVm.openNewLesson(cid, mid, mTitle)
                        },
                        onEditLesson = { lid, mTitle ->
                            courseAdminVm.openEditLesson(lid, mTitle, openInPreviewDirectly = false)
                        },
                        onPreviewLesson = { lid, mTitle ->
                            courseAdminVm.openEditLesson(lid, mTitle, openInPreviewDirectly = true)
                        },
                        onDuplicateLesson = { lid -> courseAdminVm.duplicateLesson(lid) },
                        onToggleLessonPublish = { lid, pub ->
                            courseAdminVm.toggleLessonPublish(lid, pub)
                        },
                        onDeleteLesson = { lid -> courseAdminVm.deleteLesson(lid) },
                        onMoveLesson = { mid, from, to -> courseAdminVm.moveLesson(mid, from, to) }
                    )
                }
                else -> {
                    AdminDashboardHub(
                        activeTab = activeSectionTab,
                        onSelectTab = { activeSectionTab = it },
                        adminName = adminName.ifBlank { "Administrator" },
                        adminEmail = adminEmail,
                        dashboardState = dashboardState,
                        patternState = patternListState,
                        vocabState = vocabAdminState,
                        readingState = readingListState,
                        studentsState = studentsState,
                        courseListState = courseListState,
                        aiState = aiState,
                        vocabSubTab = vocabSubTab,
                        openAddWordTrigger = openAddWordTrigger,
                        openCreateCourseTrigger = openCreateCourseTrigger,
                        onBack = onBack,
                        onLogout = onLogout,
                        onRefreshDashboard = { dashboardVm.loadDashboard(forceRefresh = true) },
                        onRefreshAll = {
                            dashboardVm.loadDashboard(forceRefresh = true)
                            vm.loadList()
                            vocabAdminVm.loadInitial()
                            readingAdminVm.loadInitial()
                            studentsAdminVm.refresh()
                            courseAdminVm.loadInitial()
                            aiAdminVm.loadInitial(forceRefresh = true)
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
                        onQuickCreateReading = {
                            activeSectionTab = 3
                            readingAdminVm.openNewPassage()
                        },
                        onQuickManageReading = {
                            activeSectionTab = 3
                        },
                        onQuickManageStudents = {
                            activeSectionTab = 4
                        },
                        onQuickManageCourses = {
                            activeSectionTab = 5
                        },
                        onQuickCreateCourse = {
                            activeSectionTab = 5
                            openCreateCourseTrigger++
                        },
                        onQuickManageAi = {
                            activeSectionTab = 6
                        },
                        onClearReadingMessage = { readingAdminVm.clearBanner() },
                        vocabAdminVm = vocabAdminVm,
                        readingAdminVm = readingAdminVm,
                        studentsAdminVm = studentsAdminVm,
                        courseAdminVm = courseAdminVm,
                        aiAdminVm = aiAdminVm
                    )
                }
            }
        }
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
    adminName: String,
    adminEmail: String,
    dashboardState: AdminDashboardUiState,
    patternState: AdminListState,
    vocabState: VocabAdminUiState,
    readingState: ReadingAdminListState,
    studentsState: AdminStudentsUiState,
    courseListState: AdminCourseListUiState,
    aiState: AdminAiManagementUiState,
    vocabSubTab: Int,
    openAddWordTrigger: Int,
    openCreateCourseTrigger: Int,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onRefreshDashboard: () -> Unit,
    onRefreshAll: () -> Unit,
    onNewPattern: () -> Unit,
    onEditPattern: (String) -> Unit,
    onDeletePattern: (String) -> Unit,
    onClearPatternMessage: () -> Unit,
    onQuickAddVocabulary: () -> Unit,
    onQuickManageCategories: () -> Unit,
    onQuickCsvImport: () -> Unit,
    onQuickCreateReading: () -> Unit,
    onQuickManageReading: () -> Unit,
    onQuickManageStudents: () -> Unit,
    onQuickManageCourses: () -> Unit,
    onQuickCreateCourse: () -> Unit,
    onQuickManageAi: () -> Unit,
    onClearReadingMessage: () -> Unit,
    vocabAdminVm: VocabAdminViewModel,
    readingAdminVm: ReadingAdminViewModel,
    studentsAdminVm: AdminStudentsViewModel,
    courseAdminVm: AdminCourseViewModel,
    aiAdminVm: AdminAiManagementViewModel
) {
    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var confirmDeletePattern by remember { mutableStateOf<AdminPatternSummary?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(patternState.message) {
        patternState.message?.let {
            snackbar.showSnackbar(it)
            onClearPatternMessage()
        }
    }

    LaunchedEffect(readingState.message, readingState.error) {
        val msg = readingState.message ?: readingState.error
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            onClearReadingMessage()
        }
    }

    LaunchedEffect(studentsState.message, studentsState.error) {
        val msg = studentsState.message ?: studentsState.error
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            studentsAdminVm.clearBanner()
        }
    }

    LaunchedEffect(courseListState.message, courseListState.error) {
        val msg = courseListState.message ?: courseListState.error
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            courseAdminVm.clearBanner()
        }
    }

    LaunchedEffect(aiState.message) {
        aiState.message?.let { msg ->
            snackbar.showSnackbar(msg)
            aiAdminVm.clearBanner()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp

        val drawerContent: @Composable () -> Unit = {
            AdminSidebarDrawerContent(
                activeTab = activeTab,
                adminName = adminName,
                adminEmail = adminEmail,
                patternCount = patternState.patterns.size,
                vocabCount = vocabState.total,
                readingCount = readingState.total,
                studentCount = studentsState.counts.totalStudents,
                courseCount = courseListState.pagination.total,
                aiBadge = if (aiState.draftConfig.isEnabled) "On" else "Off",
                onSelectTab = { idx ->
                    onSelectTab(idx)
                    if (!isWideScreen) {
                        scope.launch { drawerState.close() }
                    }
                },
                onRefreshAll = {
                    onRefreshAll()
                    if (!isWideScreen) {
                        scope.launch { drawerState.close() }
                    }
                },
                onBackToApp = {
                    if (!isWideScreen) {
                        scope.launch {
                            drawerState.close()
                            onBack()
                        }
                    } else {
                        onBack()
                    }
                },
                onRequestLogout = {
                    confirmLogout = true
                }
            )
        }

        val hubScaffold: @Composable () -> Unit = {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("Admin Dashboard", fontWeight = FontWeight.Bold)
                                Text(
                                    text = when (activeTab) {
                                        1 -> "Sentence Patterns"
                                        2 -> "Vocabulary Catalog"
                                        3 -> "Reading Passages"
                                        4 -> "Student Management"
                                        5 -> "Courses & Lessons"
                                        6 -> "AI Management & Costs"
                                        else -> "Overview & Quick Actions"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            if (!isWideScreen) {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("admin_sidebar_menu_button")
                                ) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Open admin menu")
                                }
                            } else {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("admin_dashboard_back_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to app")
                                }
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
                            if (!isWideScreen) {
                                IconButton(
                                    onClick = { confirmLogout = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("admin_topbar_logout_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Log out",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
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
                    if (!isWideScreen) {
                        ScrollableTabRow(
                            selectedTabIndex = activeTab,
                            edgePadding = 12.dp,
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
                            Tab(
                                selected = activeTab == 3,
                                onClick = { onSelectTab(3) },
                                text = { Text("Reading (${readingState.total})") },
                                modifier = Modifier.testTag("admin_tab_reading")
                            )
                            Tab(
                                selected = activeTab == 4,
                                onClick = { onSelectTab(4) },
                                text = { Text("Students (${studentsState.counts.totalStudents})") },
                                modifier = Modifier.testTag("admin_tab_students")
                            )
                            Tab(
                                selected = activeTab == 5,
                                onClick = { onSelectTab(5) },
                                text = { Text("Courses (${courseListState.pagination.total})") },
                                modifier = Modifier.testTag("admin_tab_courses")
                            )
                            Tab(
                                selected = activeTab == 6,
                                onClick = { onSelectTab(6) },
                                text = { Text("AI") },
                                modifier = Modifier.testTag("admin_tab_ai")
                            )
                        }
                    }

                    when (activeTab) {
                        0 -> AdminOverviewTab(
                            dashboardState = dashboardState,
                            patternCount = patternState.patterns.size,
                            vocabCount = vocabState.total,
                            categoryCount = vocabState.categories.size,
                            readingCount = readingState.total,
                            studentCount = studentsState.counts.totalStudents,
                            courseCount = courseListState.pagination.total,
                            onRefreshDashboard = onRefreshDashboard,
                            onPostNewPattern = onNewPattern,
                            onOpenPatterns = { onSelectTab(1) },
                            onPostNewVocabulary = onQuickAddVocabulary,
                            onManageCategories = onQuickManageCategories,
                            onCsvImport = onQuickCsvImport,
                            onCreateReading = onQuickCreateReading,
                            onManageReading = onQuickManageReading,
                            onManageStudents = onQuickManageStudents,
                            onCreateCourse = onQuickCreateCourse,
                            onManageCourses = onQuickManageCourses,
                            onManageAi = onQuickManageAi
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
                        3 -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ReadingAdminPanel(vm = readingAdminVm)
                        }
                        4 -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AdminStudentsPanel(
                                state = studentsState,
                                onSearchQueryChange = { studentsAdminVm.onSearchQueryChange(it) },
                                onAccountFilterChange = { studentsAdminVm.setAccountStatusFilter(it) },
                                onSubscriptionFilterChange = { studentsAdminVm.setSubscriptionFilter(it) },
                                onPageChange = { studentsAdminVm.loadStudents(it) },
                                onSelectStudent = { studentsAdminVm.openStudentDetail(it) },
                                onCloseDetail = { studentsAdminVm.closeStudentDetail() },
                                onUpdateAccountStatus = { id, st -> studentsAdminVm.updateAccountStatus(id, st) },
                                onUpdateSubscription = { id, st, pl, exp ->
                                    studentsAdminVm.updateSubscription(id, st, pl, exp)
                                }
                            )
                        }
                        5 -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AdminCoursesPanel(
                                state = courseListState,
                                openCreateTrigger = openCreateCourseTrigger,
                                onSearchChange = { courseAdminVm.updateFilters(search = it) },
                                onLevelFilterChange = { courseAdminVm.updateFilters(level = it) },
                                onPublishedFilterChange = { courseAdminVm.updateFilters(published = it) },
                                onPageChange = { p -> courseAdminVm.loadCourses(p) },
                                onOpenCourseDetail = { cid -> courseAdminVm.openCourseDetail(cid) },
                                onTogglePublish = { cid, pub -> courseAdminVm.toggleCoursePublish(cid, pub) },
                                onDeleteCourse = { cid -> courseAdminVm.deleteCourse(cid) },
                                onUploadThumbnail = { uri, cb -> courseAdminVm.uploadThumbnailImage(uri, cb) },
                                onUploadMedia = { uri, mediaType, cb -> courseAdminVm.uploadDialogMedia(uri, mediaType, cb) },
                                onSaveCourse = { cid, t, st, d, url, pid, lvl, cat, days, prem, price, pub, initMod, initLesson, initVidUrl, initVidPid, initVidDur, initPdfUrl, initPdfPid, initText, openModules, done ->
                                    courseAdminVm.saveCourse(
                                        courseId = cid,
                                        title = t,
                                        subtitle = st,
                                        description = d,
                                        thumbnailUrl = url,
                                        thumbnailPublicId = pid,
                                        level = lvl,
                                        category = cat,
                                        durationDays = days,
                                        isPremium = prem,
                                        price = price,
                                        isPublished = pub,
                                        initialModuleTitle = initMod,
                                        initialLessonTitle = initLesson,
                                        initialVideoUrl = initVidUrl,
                                        initialVideoPublicId = initVidPid,
                                        initialVideoDuration = initVidDur,
                                        initialPdfUrl = initPdfUrl,
                                        initialPdfPublicId = initPdfPid,
                                        initialTextContent = initText,
                                        openModulesAfterSave = openModules,
                                        onDone = done
                                    )
                                },
                                onClearDialogError = { courseAdminVm.clearDialogError() }
                            )
                        }
                        6 -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AdminAiManagementPanel(
                                state = aiState,
                                onRefresh = { aiAdminVm.loadInitial(forceRefresh = true) },
                                onSelectWindowDays = { days -> aiAdminVm.selectWindowDays(days) },
                                onToggleGlobalEnabled = { enabled -> aiAdminVm.toggleGlobalEnabled(enabled) },
                                onUpdateDraft = { transform -> aiAdminVm.updateDraft(transform) },
                                onSelectProvider = { providerId -> aiAdminVm.selectProvider(providerId) },
                                onSelectPresetModel = { model -> aiAdminVm.selectPresetModel(model) },
                                onSaveInstructions = { aiAdminVm.saveInstructions() },
                                onSaveLimits = { aiAdminVm.saveLimits() },
                                onSaveModelAndConversation = { aiAdminVm.saveModelAndConversation() },
                                onResetSection = { section -> aiAdminVm.resetSection(section) }
                            )
                        }
                    }
                }
            }
        }

        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(280.dp)
                    ) {
                        drawerContent()
                    }
                }
            ) {
                hubScaffold()
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(300.dp)
                    ) {
                        drawerContent()
                    }
                }
            ) {
                hubScaffold()
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

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("You will be signed out of your administrator account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        onLogout()
                    },
                    modifier = Modifier.testTag("admin_confirm_logout_button")
                ) {
                    Text("Log Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminSidebarDrawerContent(
    activeTab: Int,
    adminName: String,
    adminEmail: String,
    patternCount: Int,
    vocabCount: Int,
    readingCount: Int,
    studentCount: Int,
    courseCount: Int,
    aiBadge: String,
    onSelectTab: (Int) -> Unit,
    onRefreshAll: () -> Unit,
    onBackToApp: () -> Unit,
    onRequestLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Branded Admin Header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AdminPanelSettings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Admin Console",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = adminName.ifBlank { "Administrator" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (adminEmail.isNotBlank()) {
                        Text(
                            text = adminEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "MANAGEMENT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            data class NavEntry(
                val tabIndex: Int,
                val label: String,
                val icon: ImageVector,
                val badge: String? = null,
                val tag: String
            )

            val entries = listOf(
                NavEntry(0, "Overview", Icons.Filled.Dashboard, null, "admin_drawer_overview"),
                NavEntry(5, "Courses & Lessons", Icons.Filled.School, "$courseCount", "admin_drawer_courses"),
                NavEntry(1, "Sentence Patterns", Icons.AutoMirrored.Filled.MenuBook, "$patternCount", "admin_drawer_patterns"),
                NavEntry(2, "Vocabulary", Icons.Filled.Translate, "$vocabCount", "admin_drawer_vocabulary"),
                NavEntry(3, "Reading Practice", Icons.Filled.AutoStories, "$readingCount", "admin_drawer_reading"),
                NavEntry(4, "Students", Icons.Filled.People, "$studentCount", "admin_drawer_students"),
                NavEntry(6, "AI Management", Icons.Filled.AutoAwesome, aiBadge, "admin_drawer_ai")
            )

            entries.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.label, fontWeight = if (activeTab == item.tabIndex) FontWeight.SemiBold else FontWeight.Normal) },
                    selected = activeTab == item.tabIndex,
                    onClick = { onSelectTab(item.tabIndex) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    badge = item.badge?.let { count ->
                        {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (activeTab == item.tabIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = count,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (activeTab == item.tabIndex) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(item.tag)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "SYSTEM",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            NavigationDrawerItem(
                label = { Text("Refresh All Data") },
                selected = false,
                onClick = onRefreshAll,
                icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_drawer_refresh")
            )

            NavigationDrawerItem(
                label = { Text("Return to Learner App") },
                selected = false,
                onClick = onBackToApp,
                icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_drawer_back_to_app")
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))

        // Pinned Logout Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            NavigationDrawerItem(
                label = {
                    Text(
                        text = "Log Out",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                selected = false,
                onClick = onRequestLogout,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("admin_drawer_logout_button")
            )
        }
    }
}

@Composable
private fun AdminOverviewTab(
    dashboardState: AdminDashboardUiState,
    patternCount: Int,
    vocabCount: Int,
    categoryCount: Int,
    readingCount: Int,
    studentCount: Int,
    courseCount: Int,
    onRefreshDashboard: () -> Unit,
    onPostNewPattern: () -> Unit,
    onOpenPatterns: () -> Unit,
    onPostNewVocabulary: () -> Unit,
    onManageCategories: () -> Unit,
    onCsvImport: () -> Unit,
    onCreateReading: () -> Unit,
    onManageReading: () -> Unit,
    onManageStudents: () -> Unit,
    onCreateCourse: () -> Unit,
    onManageCourses: () -> Unit,
    onManageAi: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AdminDashboardAnalyticsSection(
                state = dashboardState,
                fallbackStudentCount = studentCount,
                fallbackPatternCount = patternCount,
                fallbackVocabCount = vocabCount,
                fallbackReadingCount = readingCount,
                onRetry = onRefreshDashboard,
                onOpenStudents = onManageStudents,
                onOpenAiManagement = onManageAi
            )
        }

        item {
            Text(
                text = "Quick Management Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        item {
            AdminActionCard(
                icon = Icons.Filled.School,
                title = "Manage Courses & Lessons ($courseCount)",
                subtitle = "Create courses, organize modules, build Video/PDF/Rich Text/Image lessons & preview",
                testTag = "admin_quick_manage_courses",
                onClick = onManageCourses
            )
        }

        item {
            AdminActionCard(
                icon = Icons.Filled.Add,
                title = "Create New Course",
                subtitle = "Launch a structured course with level, cover image & pricing",
                testTag = "admin_quick_create_course",
                onClick = onCreateCourse
            )
        }

        item {
            AdminActionCard(
                icon = Icons.Filled.AutoAwesome,
                title = "AI Management & Costs",
                subtitle = "Edit AI prompts, usage limits, token costs, conversation rules & model config",
                testTag = "admin_quick_manage_ai",
                onClick = onManageAi
            )
        }

        item {
            AdminActionCard(
                icon = Icons.Filled.People,
                title = "Manage Students",
                subtitle = "Search students, view learning progress, and update account or subscription status ($categoryCount vocab categories)",
                testTag = "admin_quick_manage_students",
                onClick = onManageStudents
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

        item {
            AdminActionCard(
                icon = Icons.Filled.AutoStories,
                title = "Create Reading Passage",
                subtitle = "Publish new CEFR reading passages, word meanings & quizzes",
                testTag = "admin_quick_create_reading",
                onClick = onCreateReading
            )
        }

        item {
            AdminActionCard(
                icon = Icons.Filled.AutoStories,
                title = "Manage Reading Passages & Categories",
                subtitle = "Edit, publish/unpublish passages or organize reading categories",
                testTag = "admin_quick_manage_reading",
                onClick = onManageReading
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
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
                    Tag("Premium", Color(0xFF3E2B0D), Color(0xFFFBBF24), Modifier.padding(start = 8.dp))
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