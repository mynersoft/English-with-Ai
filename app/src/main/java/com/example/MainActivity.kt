package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.englishai.feature.speaking.SentenceLearningScreen
import com.example.core.common.UiState
import com.example.core.ui.attemptSilentGoogleAutoSignIn
import com.example.data.repository.AuthRepository
import com.example.data.repository.UserRepository
import com.example.domain.model.AccountStatus
import com.example.feature.admin.AdminRoute
import com.example.feature.auth.AuthViewModel
import com.example.feature.auth.EmailVerificationScreen
import com.example.feature.auth.ForgotPasswordScreen
import com.example.feature.auth.LoginScreen
import com.example.feature.auth.RegisterScreen
import com.example.feature.dashboard.DashboardScreen
import com.example.feature.dashboard.DashboardViewModel
import com.example.feature.dashboard.DeactivatedAccountScreen
import com.example.feature.dashboard.ProfileScreen
import com.example.feature.dashboard.SecuritySettingsScreen
import com.example.feature.dashboard.SessionsScreen
import com.example.feature.speaking.SpeakingRoute
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
    const val DASHBOARD = "dashboard"
    const val PROFILE = "profile"
    const val SESSIONS = "sessions"
    const val SECURITY = "security"
    const val SPEAKING = "speaking"
    const val SENTENCE_LEARNING = "sentence-learning"
    const val ADMIN = "admin"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EnglishAITheme {
                EnglishAIApp()
            }
        }
    }
}

@Composable
fun EnglishAIApp() {
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

    if (user == null) {
        UnauthenticatedNavGraph(authViewModel = authViewModel)
    } else if (!isVerifiedCheck) {
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
                credentialManager = credentialManager
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
    credentialManager: CredentialManager
) {
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

    val profileState by dashboardViewModel.profileState.collectAsStateWithLifecycle()
    val sessionsState by dashboardViewModel.sessionsState.collectAsStateWithLifecycle()
    val actionState by dashboardViewModel.actionState.collectAsStateWithLifecycle()

    val loadedProfile = (profileState as? UiState.Success)?.data
    if (profileState is UiState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (loadedProfile?.safeAccountStatus == AccountStatus.DEACTIVATED) {
        DeactivatedAccountScreen(
            displayName = loadedProfile.displayName.ifBlank { "Learner" },
            actionState = actionState,
            onReactivate = { dashboardViewModel.reactivateAccount() },
            onSignOut = { dashboardViewModel.signOut(credentialManager) }
        )
        return
    }

    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppRoutes.DASHBOARD
    ) {
        composable(AppRoutes.DASHBOARD) {
            DashboardScreen(
                profileState = profileState,
                sessionsState = sessionsState,
                fallbackEmail = dashboardViewModel.currentEmail(),
                isEmailVerified = dashboardViewModel.isEmailVerified(),
                actionState = actionState,
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
                    navController.navigate(AppRoutes.SPEAKING)
                },
                onOpenSentenceLearning = {
                    dashboardViewModel.clearBanner()
                    navController.navigate(AppRoutes.SENTENCE_LEARNING)
                },
                onOpenAdmin = {
                    dashboardViewModel.clearBanner()
                    navController.navigate(AppRoutes.ADMIN)
                },
                onSignOut = { dashboardViewModel.signOut(credentialManager) }
            )
        }

        composable(AppRoutes.SPEAKING) {
            SpeakingRoute(
                level = loadedProfile?.safeLevel?.name ?: "Beginner",
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.SENTENCE_LEARNING) {
            SentenceLearningScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoutes.ADMIN) {
            AdminRoute(
                onBack = { navController.popBackStack() }
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
                onSignOut = { dashboardViewModel.signOut(credentialManager) },
                onNavigateBack = {
                    dashboardViewModel.clearBanner()
                    navController.popBackStack()
                },
                onClearBanner = { dashboardViewModel.clearBanner() }
            )
        }
    }
}
