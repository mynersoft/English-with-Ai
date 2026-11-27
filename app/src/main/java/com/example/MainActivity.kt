package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.englishai.feature.speaking.SentenceLearningScreen
import com.example.core.chat.ChatMessageNotifier
import com.example.core.chat.ChatNotificationConstants
import com.example.core.chat.ChatNotificationMonitor
import com.example.core.common.UiState
import com.example.core.network.NetworkMonitor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.example.core.reminder.DailySessionReminderPrefs
import com.example.core.reminder.DailySessionReminderScheduler
import com.example.core.wordofday.WordOfTheDayDialog
import com.example.core.wordofday.WordOfTheDayEntry
import com.example.core.wordofday.WordOfTheDayNotificationConstants
import com.example.core.wordofday.WordOfTheDayScheduler
import com.example.core.wordofday.WordOfTheDayStorage
import com.example.core.wordofday.WordOfTheDaySync
import com.example.core.ui.OfflineFloatingBanner
import com.example.core.ui.SystemBarsAppearance
import com.example.core.ui.attemptSilentGoogleAutoSignIn
import org.json.JSONObject
import com.example.data.repository.AuthRepository
import com.example.data.repository.UserRepository
import com.example.domain.model.AccountStatus
import com.example.domain.model.StudentDashboardData
import com.example.feature.admin.AdminRoute
import com.example.feature.auth.AuthViewModel
import com.example.feature.auth.EmailVerificationScreen
import com.example.feature.auth.ForgotPasswordScreen
import com.example.feature.auth.LoginScreen
import com.example.feature.auth.RegisterScreen
import com.example.feature.chat.ChatRoute
import com.example.feature.chat.ChatViewModel
import com.example.feature.dashboard.AppMainTab
import com.example.feature.dashboard.DashboardScreen
import com.example.feature.dashboard.DashboardViewModel
import com.example.feature.dashboard.DeactivatedAccountScreen
import com.example.feature.dashboard.MainBottomNavBar
import com.example.feature.dashboard.ProfileScreen
import com.example.feature.dashboard.SecuritySettingsScreen
import com.example.feature.dashboard.SessionsScreen
import com.example.feature.dashboard.StudentProfileDashboardScreen
import com.example.feature.dashboard.StudentProfileDashboardViewModel
import com.example.feature.dashboard.StudentSidebarContainer
import com.example.feature.pattern.PatternViewModel
import com.example.feature.reading.ui.ReadingRoute
import com.example.feature.speaking.SpeakingRoute
import com.example.feature.vocabulary.VocabularyRoute
import com.example.feature.vocabulary.VocabularyViewModel
import com.example.ui.theme.EnglishAITheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private object AuthRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT_PASSWORD = "forgot_password"
}

private object AppRoutes {
    const val MAIN_TABS = "main_tabs"
    const val PROFILE = "profile"
    const val SESSIONS = "sessions"
    const val SECURITY = "security"
    const val READING = "reading"
    const val ADMIN = "admin"
}

data class ChatDeepLinkTarget(
    val roomId: String,
    val messageId: String? = null
)

class MainActivity : ComponentActivity() {
    private var launchedWordOfTheDay by mutableStateOf<WordOfTheDayEntry?>(null)
    private var launchedChatTarget by mutableStateOf<ChatDeepLinkTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DailySessionReminderScheduler.scheduleDailyReminder(this)
        WordOfTheDayScheduler.scheduleMorningNotification(this)
        ChatMessageNotifier.ensureChannel(this)
        handleDeepLinkIntents(intent)
        setContent {
            EnglishAITheme {
                EnglishAIApp(
                    wordOfTheDayPopup = launchedWordOfTheDay,
                    onDismissWordOfTheDay = { launchedWordOfTheDay = null },
                    chatDeepLinkTarget = launchedChatTarget,
                    onConsumeChatDeepLink = { launchedChatTarget = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntents(intent)
    }

    private fun handleDeepLinkIntents(intent: Intent?) {
        handleWordOfTheDayIntent(intent)
        handleChatNotificationIntent(intent)
    }

    private fun handleWordOfTheDayIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(WordOfTheDayNotificationConstants.EXTRA_OPEN_WORD_OF_THE_DAY, false) != true) {
            return
        }
        val rawJson = intent.getStringExtra(WordOfTheDayNotificationConstants.EXTRA_WORD_JSON)
        val parsed = rawJson
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let { WordOfTheDayEntry.fromJson(it) }
            ?: WordOfTheDayStorage.getCurrentEntry(this)
        if (parsed != null) {
            launchedWordOfTheDay = parsed
        }
        intent.removeExtra(WordOfTheDayNotificationConstants.EXTRA_OPEN_WORD_OF_THE_DAY)
    }

    private fun handleChatNotificationIntent(intent: Intent?) {
        val roomId = intent?.getStringExtra(ChatNotificationConstants.EXTRA_OPEN_CHAT_ROOM_ID)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val messageId = intent.getStringExtra(ChatNotificationConstants.EXTRA_OPEN_CHAT_MESSAGE_ID)
            ?.takeIf { it.isNotBlank() }
        launchedChatTarget = ChatDeepLinkTarget(roomId = roomId, messageId = messageId)
        intent.removeExtra(ChatNotificationConstants.EXTRA_OPEN_CHAT_ROOM_ID)
        intent.removeExtra(ChatNotificationConstants.EXTRA_OPEN_CHAT_MESSAGE_ID)
    }
}

@Composable
fun EnglishAIApp(
    wordOfTheDayPopup: WordOfTheDayEntry? = null,
    onDismissWordOfTheDay: () -> Unit = {},
    chatDeepLinkTarget: ChatDeepLinkTarget? = null,
    onConsumeChatDeepLink: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository(Firebase.auth) }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AuthViewModel(
                    authRepository = AuthRepository(Firebase.auth),
                    userRepository = UserRepository(db, Firebase.auth)
                )
            }
        }
    )

    val currentUser by authRepository.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    // Counter incremented when user.reload() confirms email verification
    var verificationRefreshTick by remember { mutableIntStateOf(0) }

    // Attempt silent Google auto-sign-in once when unauthenticated
    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptSilentGoogleAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onTokenReceived = { idToken ->
                    authViewModel.signInWithGoogleToken(idToken)
                },
                onUnauthenticated = { /* Interactive AuthFlow handles sign-in */ }
            )
        }
    }

    val user = currentUser
    val isVerifiedCheck = remember(user, verificationRefreshTick) {
        user != null && !authRepository.requiresEmailVerification(user)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || DailySessionReminderPrefs.hasNotificationPermission(context)) {
            DailySessionReminderScheduler.scheduleDailyReminder(context)
            DailySessionReminderScheduler.postNotificationIfEligible(context)
            WordOfTheDayScheduler.scheduleMorningNotification(context)
        }
    }

    LaunchedEffect(user?.uid) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !DailySessionReminderPrefs.hasNotificationPermission(context) &&
            !DailySessionReminderPrefs.hasPromptedNotificationPermission(context)
        ) {
            DailySessionReminderPrefs.markNotificationPermissionPrompted(context)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (user != null) {
            DailySessionReminderPrefs.markLoggedInToday(context)
            WordOfTheDaySync.syncPool(context)
        } else if (DailySessionReminderPrefs.hasNotificationPermission(context)) {
            DailySessionReminderScheduler.postNotificationIfEligible(context)
        }
    }

    if (wordOfTheDayPopup != null) {
        WordOfTheDayDialog(
            entry = wordOfTheDayPopup,
            onDismiss = onDismissWordOfTheDay
        )
    }

    val systemDark = isSystemInDarkTheme()

    if (user == null) {
        SystemBarsAppearance(lightStatusBars = !systemDark)
        UnauthenticatedNavGraph(authViewModel = authViewModel)
    } else if (!isVerifiedCheck) {
        SystemBarsAppearance(lightStatusBars = !systemDark)
        val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
        EmailVerificationScreen(
            userEmail = user.email.orEmpty(),
            uiState = authUiState,
            onCheckVerification = {
                authViewModel.checkVerificationStatus(
                    onVerified = { verificationRefreshTick++ }
                )
            },
            onResendVerification = { authViewModel.resendVerificationEmail() },
            onSignOut = { authViewModel.signOut(credentialManager) }
        )
    } else {
        key(user.uid) {
            AuthenticatedNavGraph(
                currentUserId = user.uid,
                credentialManager = credentialManager,
                chatDeepLinkTarget = chatDeepLinkTarget,
                onConsumeChatDeepLink = onConsumeChatDeepLink
            )
        }
    }
}

@Composable
private fun UnauthenticatedNavGraph(
    authViewModel: AuthViewModel
) {
    val navController = rememberNavController()
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        authViewModel.resetTransientLoading()
    }

    NavHost(
        navController = navController,
        startDestination = AuthRoutes.LOGIN
    ) {
        composable(AuthRoutes.LOGIN) {
            LoginScreen(
                uiState = uiState,
                onSignInEmail = { email, password ->
                    authViewModel.signInWithEmail(email, password)
                },
                onGoogleTokenReceived = { idToken ->
                    authViewModel.signInWithGoogleToken(idToken)
                },
                onGoogleLoadingChanged = { loading ->
                    authViewModel.setGoogleLoading(loading)
                },
                onGoogleError = { msg ->
                    authViewModel.showError(msg)
                },
                onNavigateToRegister = {
                    authViewModel.clearBanner()
                    navController.navigate(AuthRoutes.REGISTER)
                },
                onNavigateToForgotPassword = { prefilledEmail ->
                    authViewModel.clearResetConfirmation()
                    val encoded = Uri.encode(prefilledEmail)
                    navController.navigate("${AuthRoutes.FORGOT_PASSWORD}?email=$encoded")
                },
                onClearBanner = { authViewModel.clearBanner() }
            )
        }

        composable(AuthRoutes.REGISTER) {
            RegisterScreen(
                uiState = uiState,
                onRegister = { name, email, password, confirmPassword, level, goal ->
                    authViewModel.registerWithEmail(
                        displayName = name,
                        email = email,
                        password = password,
                        confirmPassword = confirmPassword,
                        learningLevel = level,
                        dailyGoalMinutes = goal
                    )
                },
                onGoogleTokenReceived = { idToken ->
                    authViewModel.signInWithGoogleToken(idToken)
                },
                onGoogleLoadingChanged = { loading ->
                    authViewModel.setGoogleLoading(loading)
                },
                onGoogleError = { msg ->
                    authViewModel.showError(msg)
                },
                onNavigateBack = {
                    authViewModel.clearBanner()
                    navController.popBackStack()
                },
                onClearBanner = { authViewModel.clearBanner() }
            )
        }

        composable(
            route = "${AuthRoutes.FORGOT_PASSWORD}?email={email}",
            arguments = listOf(
                navArgument("email") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val initialEmail = backStackEntry.arguments?.getString("email").orEmpty()
            ForgotPasswordScreen(
                initialEmail = initialEmail,
                uiState = uiState,
                onSendResetLink = { email -> authViewModel.sendPasswordReset(email) },
                onNavigateBack = {
                    authViewModel.clearResetConfirmation()
                    navController.popBackStack()
                },
                onClearBanner = { authViewModel.clearBanner() }
            )
        }
    }
}

@Composable
private fun AuthenticatedNavGraph(
    currentUserId: String,
    credentialManager: CredentialManager,
    chatDeepLinkTarget: ChatDeepLinkTarget? = null,
    onConsumeChatDeepLink: () -> Unit = {}
) {
    val context = LocalContext.current
    val navScope = rememberCoroutineScope()
    val dashboardViewModel: DashboardViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                DashboardViewModel(
                    userRepository = UserRepository(db, Firebase.auth),
                    authRepository = AuthRepository(Firebase.auth),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val studentDashboardViewModel: StudentProfileDashboardViewModel = viewModel(key = "student_$currentUserId")
    val vocabularyViewModel: VocabularyViewModel = viewModel(key = "vocab_$currentUserId")
    val patternViewModel: PatternViewModel = viewModel(key = "pattern_$currentUserId")
    val chatViewModel: ChatViewModel = viewModel(key = "chat_$currentUserId")

    val profileState by dashboardViewModel.profileState.collectAsStateWithLifecycle()
    val sessionsState by dashboardViewModel.sessionsState.collectAsStateWithLifecycle()
    val actionState by dashboardViewModel.actionState.collectAsStateWithLifecycle()
    val studentDashboardState by studentDashboardViewModel.state.collectAsStateWithLifecycle()
    val studentDashboardRefreshing by studentDashboardViewModel.isRefreshing.collectAsStateWithLifecycle()
    val chatLobbyState by chatViewModel.lobby.collectAsStateWithLifecycle()

    val isOnline by remember(context) {
        NetworkMonitor.observeIsOnline(context)
    }.collectAsStateWithLifecycle(initialValue = NetworkMonitor.isDeviceOnline(context))
    var wasOffline by remember { mutableStateOf(!isOnline) }

    val loadedProfile = (profileState as? UiState.Success)?.data
    LaunchedEffect(currentUserId) {
        dashboardViewModel.syncBackendUser(preferredName = loadedProfile?.displayName)
        chatViewModel.loadLobby()
    }

    LaunchedEffect(isOnline) {
        if (isOnline && wasOffline) {
            dashboardViewModel.syncBackendUser(preferredName = loadedProfile?.displayName)
            chatViewModel.loadLobby()
            studentDashboardViewModel.loadDashboard(silent = true)
        }
        wasOffline = !isOnline
    }

    val performSignOut: () -> Unit = {
        navScope.launch {
            if (NetworkMonitor.isDeviceOnline(context)) {
                withTimeoutOrNull(1200L) {
                    ChatNotificationMonitor.unregisterCurrentFcmToken(context)
                }
            } else {
                ChatMessageNotifier.clearAll(context)
            }
            dashboardViewModel.signOut(credentialManager)
        }
    }

    LaunchedEffect(currentUserId, isOnline) {
        if (isOnline) {
            ChatNotificationMonitor.runMonitorLoop(
                context = context,
                userId = currentUserId,
                isOnline = true
            )
        }
    }

    val systemDark = isSystemInDarkTheme()

    if (profileState is UiState.Loading) {
        SystemBarsAppearance(lightStatusBars = !systemDark)
        ProfileLoadingFallbackScreen(
            isBusy = actionState.isBusy,
            onSignOut = performSignOut
        )
        return
    }

    if (loadedProfile?.safeAccountStatus == AccountStatus.DEACTIVATED) {
        SystemBarsAppearance(lightStatusBars = !systemDark)
        DeactivatedAccountScreen(
            displayName = loadedProfile.displayName.ifBlank { "Learner" },
            actionState = actionState,
            onReactivate = { dashboardViewModel.reactivateAccount() },
            onSignOut = performSignOut
        )
        return
    }

    val navController = rememberNavController()
    var selectedTab by rememberSaveable { mutableStateOf(AppMainTab.HOME) }
    var patternFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(chatDeepLinkTarget) {
        val target = chatDeepLinkTarget ?: return@LaunchedEffect
        if (navController.currentDestination?.route != AppRoutes.MAIN_TABS) {
            navController.popBackStack(AppRoutes.MAIN_TABS, inclusive = false)
        }
        dashboardViewModel.clearBanner()
        selectedTab = AppMainTab.CHAT
        chatViewModel.openRoomById(
            roomId = target.roomId,
            highlightMessageId = target.messageId,
            context = context
        )
        onConsumeChatDeepLink()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppRoutes.MAIN_TABS
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > WindowInsets.navigationBars.getBottom(density)

    val (lightStatusBars, lightNavigationBars) = when {
        currentRoute == AppRoutes.MAIN_TABS && selectedTab == AppMainTab.SPEAKING ->
            (!systemDark) to (if (isImeVisible) !systemDark else true)
        currentRoute == AppRoutes.MAIN_TABS ->
            true to true
        else ->
            (!systemDark) to (!systemDark)
    }
    SystemBarsAppearance(
        lightStatusBars = lightStatusBars,
        lightNavigationBars = lightNavigationBars
    )

    val activeChatRoomsCount = remember(chatLobbyState.groupRooms, chatLobbyState.directRooms) {
        (chatLobbyState.groupRooms + chatLobbyState.directRooms).count { it.onlineCount > 0 }
    }

    val sharedBottomBar: @Composable () -> Unit = {
        MainBottomNavBar(
            selectedTab = selectedTab,
            chatBadgeCount = activeChatRoomsCount,
            onSelectTab = { tab ->
                dashboardViewModel.clearBanner()
                selectedTab = tab
                if (tab == AppMainTab.PROGRESS) {
                    studentDashboardViewModel.loadDashboard(silent = true)
                }
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.MAIN_TABS
    ) {
        composable(AppRoutes.MAIN_TABS) {
            BackHandler(enabled = selectedTab != AppMainTab.HOME && !patternFullscreen) {
                selectedTab = AppMainTab.HOME
            }

            val studentSuccessData = (studentDashboardState as? UiState.Success<StudentDashboardData>)?.data
            val sidebarPlanLabel = studentSuccessData?.user?.planLabel?.takeIf { it.isNotBlank() }
                ?: if (actionState.isAdmin) "Admin" else "Free"
            val sidebarStreakDays = (loadedProfile?.streakDays ?: 1L).toInt().coerceAtLeast(1)
            val sidebarCurrentDay = (loadedProfile?.currentDay ?: 1L).toInt().coerceIn(1, 60)
            val sidebarDailyGoalMin = (loadedProfile?.dailyGoalMinutes ?: 20L).toInt()
            val sidebarActiveSessions = (sessionsState as? UiState.Success)?.data
                ?.count { it.activeSession } ?: 1

            StudentSidebarContainer(
                selectedTab = selectedTab,
                displayName = loadedProfile?.displayName?.takeIf { it.isNotBlank() } ?: "Learner",
                email = loadedProfile?.email?.ifBlank { dashboardViewModel.currentEmail() }
                    ?: dashboardViewModel.currentEmail(),
                level = loadedProfile?.safeLevel?.displayName ?: "Beginner",
                planLabel = sidebarPlanLabel,
                streakDays = sidebarStreakDays,
                currentDay = sidebarCurrentDay,
                dailyGoalMin = sidebarDailyGoalMin,
                activeSessionsCount = sidebarActiveSessions,
                activeChatRoomsCount = activeChatRoomsCount,
                isAdmin = actionState.isAdmin,
                gesturesEnabled = !patternFullscreen &&
                    (selectedTab == AppMainTab.HOME || selectedTab == AppMainTab.PROGRESS),
                onSelectTab = { tab ->
                    dashboardViewModel.clearBanner()
                    selectedTab = tab
                    if (tab == AppMainTab.PROGRESS) {
                        studentDashboardViewModel.loadDashboard(silent = true)
                    }
                },
                onOpenReading = {
                    dashboardViewModel.clearBanner()
                    navController.navigate(AppRoutes.READING)
                },
                onOpenProfile = {
                    dashboardViewModel.clearBanner()
                    navController.navigate(AppRoutes.PROFILE)
                },
                onOpenSessions = {
                    dashboardViewModel.clearBanner()
                    navController.navigate(AppRoutes.SESSIONS)
                },
                onOpenSecurity = {
                    dashboardViewModel.clearBanner()
                    navController.navigate(AppRoutes.SECURITY)
                },
                onOpenAdmin = {
                    dashboardViewModel.clearBanner()
                    navController.navigate(AppRoutes.ADMIN)
                },
                onSignOut = performSignOut
            ) { openDrawer, isWideScreen ->
                val effectiveBottomBar: @Composable () -> Unit =
                    if (isWideScreen) ({}) else sharedBottomBar

                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        AppMainTab.HOME -> {
                            EnglishAITheme(darkTheme = false) {
                                DashboardScreen(
                                    profileState = profileState,
                                    sessionsState = sessionsState,
                                    fallbackEmail = dashboardViewModel.currentEmail(),
                                    isEmailVerified = dashboardViewModel.isEmailVerified(),
                                    actionState = actionState,
                                    bottomBar = effectiveBottomBar,
                                    isOnline = isOnline,
                                    showMenuButton = !isWideScreen,
                                    onOpenMenu = openDrawer,
                                onOpenProfile = {
                                    dashboardViewModel.clearBanner()
                                    navController.navigate(AppRoutes.PROFILE)
                                },
                                onOpenSessions = {
                                    dashboardViewModel.clearBanner()
                                    navController.navigate(AppRoutes.SESSIONS)
                                },
                                onOpenSecurity = {
                                    dashboardViewModel.clearBanner()
                                    navController.navigate(AppRoutes.SECURITY)
                                },
                                onOpenSpeaking = {
                                    dashboardViewModel.clearBanner()
                                    selectedTab = AppMainTab.SPEAKING
                                },
                                onOpenSentenceLearning = {
                                    dashboardViewModel.clearBanner()
                                    selectedTab = AppMainTab.PATTERNS
                                },
                                onOpenVocabulary = {
                                    dashboardViewModel.clearBanner()
                                    selectedTab = AppMainTab.VOCABULARY
                                },
                                onOpenReading = {
                                    dashboardViewModel.clearBanner()
                                    navController.navigate(AppRoutes.READING)
                                },
                                onOpenChat = {
                                    dashboardViewModel.clearBanner()
                                    selectedTab = AppMainTab.CHAT
                                },
                                onOpenProgress = {
                                    dashboardViewModel.clearBanner()
                                    studentDashboardViewModel.loadDashboard(silent = true)
                                    selectedTab = AppMainTab.PROGRESS
                                },
                                onOpenAdmin = {
                                    dashboardViewModel.clearBanner()
                                    navController.navigate(AppRoutes.ADMIN)
                                },
                                onRetryBackendSync = {
                                    dashboardViewModel.syncBackendUser(preferredName = loadedProfile?.displayName)
                                },
                                onSignOut = performSignOut
                            )
                        }
                    }

                    AppMainTab.VOCABULARY -> {
                        VocabularyRoute(
                            vm = vocabularyViewModel,
                            bottomBar = effectiveBottomBar,
                            onBack = { selectedTab = AppMainTab.HOME }
                        )
                    }

                    AppMainTab.PATTERNS -> {
                        EnglishAITheme(darkTheme = false) {
                            SentenceLearningScreen(
                                viewModel = patternViewModel,
                                bottomBar = if (patternFullscreen) ({}) else effectiveBottomBar,
                                onFullscreenModeChanged = { patternFullscreen = it },
                                onBack = { selectedTab = AppMainTab.HOME }
                            )
                        }
                    }

                    AppMainTab.SPEAKING -> {
                        SpeakingRoute(
                            level = loadedProfile?.safeLevel?.name ?: "Beginner",
                            bottomBar = effectiveBottomBar,
                            onBack = { selectedTab = AppMainTab.HOME }
                        )
                    }

                    AppMainTab.CHAT -> {
                        EnglishAITheme(darkTheme = false) {
                            ChatRoute(
                                vm = chatViewModel,
                                bottomBar = effectiveBottomBar,
                                onBack = { selectedTab = AppMainTab.HOME }
                            )
                        }
                    }

                    AppMainTab.PROGRESS -> {
                        EnglishAITheme(darkTheme = false) {
                            StudentProfileDashboardScreen(
                                state = studentDashboardState,
                                isRefreshing = studentDashboardRefreshing,
                                fallbackName = loadedProfile?.displayName ?: "Learner",
                                fallbackEmail = loadedProfile?.email ?: dashboardViewModel.currentEmail(),
                                isAdmin = actionState.isAdmin,
                                bottomBar = effectiveBottomBar,
                                isOnline = isOnline,
                                showMenuButton = !isWideScreen,
                                onOpenMenu = openDrawer,
                                onRefresh = { studentDashboardViewModel.loadDashboard(silent = false) },
                                onBackToHome = { selectedTab = AppMainTab.HOME },
                                onNavigateRoute = { route ->
                                    when (route.lowercase()) {
                                        "vocabulary", "vocab" -> selectedTab = AppMainTab.VOCABULARY
                                        "patterns", "spoken" -> selectedTab = AppMainTab.PATTERNS
                                        "speaking", "speak" -> selectedTab = AppMainTab.SPEAKING
                                        "chat", "group" -> selectedTab = AppMainTab.CHAT
                                        "reading" -> navController.navigate(AppRoutes.READING)
                                        else -> selectedTab = AppMainTab.HOME
                                    }
                                },
                                onOpenProfile = {
                                    dashboardViewModel.clearBanner()
                                    navController.navigate(AppRoutes.PROFILE)
                                },
                                onOpenSessions = {
                                    dashboardViewModel.clearBanner()
                                    navController.navigate(AppRoutes.SESSIONS)
                                },
                                onOpenSecurity = {
                                    dashboardViewModel.clearBanner()
                                    navController.navigate(AppRoutes.SECURITY)
                                },
                                onOpenAdmin = {
                                    dashboardViewModel.clearBanner()
                                    navController.navigate(AppRoutes.ADMIN)
                                },
                                onSignOut = performSignOut
                            )
                        }
                    }
                }

                    OfflineFloatingBanner(
                        visible = !isOnline && selectedTab != AppMainTab.HOME,
                        isReconnecting = actionState.isSyncingBackend,
                        onRetry = {
                            if (NetworkMonitor.isDeviceOnline(context)) {
                                dashboardViewModel.syncBackendUser(preferredName = loadedProfile?.displayName)
                                chatViewModel.loadLobby()
                                studentDashboardViewModel.loadDashboard(silent = true)
                            }
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }

        composable(AppRoutes.READING) {
            Box(modifier = Modifier.fillMaxSize()) {
                ReadingRoute(
                    isAdmin = actionState.isAdmin,
                    onBack = { navController.popBackStack() }
                )
                OfflineFloatingBanner(
                    visible = !isOnline,
                    isReconnecting = actionState.isSyncingBackend,
                    onRetry = {
                        if (NetworkMonitor.isDeviceOnline(context)) {
                            dashboardViewModel.syncBackendUser(preferredName = loadedProfile?.displayName)
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        composable(AppRoutes.ADMIN) {
            AdminRoute(
                onBack = { navController.popBackStack() },
                onLogout = performSignOut,
                adminEmail = loadedProfile?.email ?: dashboardViewModel.currentEmail(),
                adminName = loadedProfile?.displayName ?: "Administrator"
            )
        }

        composable(AppRoutes.PROFILE) {
            ProfileScreen(
                profile = loadedProfile,
                actionState = actionState,
                onSave = { name, level, goal, bangla ->
                    dashboardViewModel.updateProfile(name, level, goal, bangla)
                },
                onNavigateBack = {
                    dashboardViewModel.clearBanner()
                    navController.popBackStack()
                },
                onClearBanner = { dashboardViewModel.clearBanner() }
            )
        }

        composable(AppRoutes.SESSIONS) {
            SessionsScreen(
                sessionsState = sessionsState,
                currentSessionId = dashboardViewModel.currentSessionId,
                actionState = actionState,
                onRevokeSession = { sessionId ->
                    dashboardViewModel.revokeSession(sessionId)
                },
                onNavigateBack = {
                    dashboardViewModel.clearBanner()
                    navController.popBackStack()
                }
            )
        }

        composable(AppRoutes.SECURITY) {
            SecuritySettingsScreen(
                email = dashboardViewModel.currentEmail(),
                isEmailVerified = dashboardViewModel.isEmailVerified(),
                hasPasswordProvider = dashboardViewModel.hasPasswordProvider(),
                actionState = actionState,
                onChangePassword = { curr, newPw, confirm ->
                    dashboardViewModel.changePassword(curr, newPw, confirm)
                },
                onSendResetEmail = { dashboardViewModel.sendPasswordResetEmailToSelf() },
                onDeactivateAccount = { dashboardViewModel.deactivateAccount(credentialManager) },
                onDeleteWithPassword = { pw ->
                    dashboardViewModel.deleteAccountWithPassword(pw, credentialManager)
                },
                onDeleteWithGoogleToken = { token ->
                    dashboardViewModel.deleteAccountWithGoogleToken(token, credentialManager)
                },
                onAuthError = { msg ->
                    dashboardViewModel.showError(msg)
                },
                onSignOut = performSignOut,
                onNavigateBack = {
                    dashboardViewModel.clearBanner()
                    navController.popBackStack()
                },
                onClearBanner = { dashboardViewModel.clearBanner() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileLoadingFallbackScreen(
    isBusy: Boolean,
    onSignOut: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("English AI") },
                actions = {
                    IconButton(
                        onClick = onSignOut,
                        enabled = !isBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("loading_top_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign Out"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator()
                Text("Loading your account...")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    enabled = !isBusy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("loading_sign_out_button")
                ) {
                    Text("Sign Out")
                }
            }
        }
    }
}
