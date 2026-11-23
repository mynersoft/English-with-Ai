package com.example.feature.auth

import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.ValidationUtils
import com.example.core.ui.BannerTone
import com.example.data.repository.AuthRepository
import com.example.data.repository.UserRepository
import com.example.domain.model.LearningLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isGoogleLoading: Boolean = false,
    val bannerMessage: String? = null,
    val bannerTone: BannerTone = BannerTone.INFO,
    val isEmailProviderDisabled: Boolean = false,
    val resetSentToEmail: String? = null,
    val verificationCooldownSec: Int = 0,
    val resetCooldownSec: Int = 0
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var verificationTimerJob: Job? = null
    private var resetTimerJob: Job? = null

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null, isEmailProviderDisabled = false) }
    }

    fun setGoogleLoading(loading: Boolean) {
        _uiState.update { it.copy(isGoogleLoading = loading) }
    }

    fun showError(message: String, providerDisabled: Boolean = false) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isGoogleLoading = false,
                bannerMessage = message,
                bannerTone = if (providerDisabled) BannerTone.WARNING else BannerTone.ERROR,
                isEmailProviderDisabled = providerDisabled
            )
        }
    }

    private fun handleAuthThrowable(error: Throwable, fallback: String) {
        val providerDisabled = (error as? com.example.data.repository.AuthFailureException)?.isProviderDisabled == true
        showError(error.message ?: fallback, providerDisabled = providerDisabled)
    }

    fun signInWithEmail(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (!ValidationUtils.isValidEmail(trimmedEmail)) {
            showError("Please enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            showError("Please enter your password.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, bannerMessage = null)
            }
            val result = authRepository.signInWithEmail(trimmedEmail, password)
            result.onSuccess { user ->
                val provider = authRepository.resolveAuthProviderLabel(user)
                userRepository.ensureUserProfile(user = user, authProvider = provider)
                userRepository.upsertCurrentSession(
                    userId = user.uid,
                    sessionId = ValidationUtils.currentDeviceSessionId(),
                    deviceName = ValidationUtils.currentDeviceName(),
                    authProvider = provider
                )
                _uiState.update { it.copy(isLoading = false) }
            }.onFailure { error ->
                handleAuthThrowable(error, "Sign-in failed.")
            }
        }
    }

    fun registerWithEmail(
        displayName: String,
        email: String,
        password: String,
        confirmPassword: String,
        learningLevel: LearningLevel,
        dailyGoalMinutes: Int
    ) {
        val trimmedName = displayName.trim()
        val trimmedEmail = email.trim()
        if (!ValidationUtils.isValidDisplayName(trimmedName)) {
            showError("Please enter your full name (2 to 60 characters).")
            return
        }
        if (!ValidationUtils.isValidEmail(trimmedEmail)) {
            showError("Please enter a valid email address.")
            return
        }
        val strength = ValidationUtils.evaluatePassword(password)
        if (!strength.isStrongEnough) {
            showError("Password must be at least 8 characters and include uppercase, lowercase, and a number.")
            return
        }
        if (password != confirmPassword) {
            showError("Passwords do not match.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, bannerMessage = null)
            }
            val result = authRepository.registerWithEmail(trimmedName, trimmedEmail, password)
            result.onSuccess { user ->
                userRepository.ensureUserProfile(
                    user = user,
                    authProvider = "password",
                    learningLevel = learningLevel,
                    dailyGoalMinutes = dailyGoalMinutes,
                    preferredName = trimmedName
                )
                userRepository.upsertCurrentSession(
                    userId = user.uid,
                    sessionId = ValidationUtils.currentDeviceSessionId(),
                    deviceName = ValidationUtils.currentDeviceName(),
                    authProvider = "password"
                )
                startVerificationCooldown()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bannerMessage = "Verification email sent to $trimmedEmail.",
                        bannerTone = BannerTone.SUCCESS
                    )
                }
            }.onFailure { error ->
                handleAuthThrowable(error, "Registration failed.")
            }
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGoogleLoading = true, bannerMessage = null) }
            val result = authRepository.signInWithGoogleIdToken(idToken)
            result.onSuccess { user ->
                val provider = authRepository.resolveAuthProviderLabel(user)
                userRepository.ensureUserProfile(user = user, authProvider = provider)
                userRepository.upsertCurrentSession(
                    userId = user.uid,
                    sessionId = ValidationUtils.currentDeviceSessionId(),
                    deviceName = ValidationUtils.currentDeviceName(),
                    authProvider = provider
                )
                _uiState.update { it.copy(isGoogleLoading = false) }
            }.onFailure { error ->
                handleAuthThrowable(error, "Google Sign-In failed.")
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val trimmedEmail = email.trim()
        if (!ValidationUtils.isValidEmail(trimmedEmail)) {
            showError("Enter a valid email address to receive a recovery link.")
            return
        }
        if (_uiState.value.resetCooldownSec > 0) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, bannerMessage = null) }
            val result = authRepository.sendPasswordResetEmail(trimmedEmail)
            result.onSuccess {
                startResetCooldown()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        resetSentToEmail = trimmedEmail,
                        bannerMessage = "Recovery link sent! Please check your inbox and spam folder for $trimmedEmail.",
                        bannerTone = BannerTone.SUCCESS
                    )
                }
            }.onFailure { error ->
                handleAuthThrowable(error, "Could not send password reset email.")
            }
        }
    }

    fun clearResetConfirmation() {
        _uiState.update { it.copy(resetSentToEmail = null, bannerMessage = null) }
    }

    fun resendVerificationEmail() {
        if (_uiState.value.verificationCooldownSec > 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, bannerMessage = null) }
            val result = authRepository.sendEmailVerification()
            result.onSuccess {
                startVerificationCooldown()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bannerMessage = "A fresh verification link has been sent to your email.",
                        bannerTone = BannerTone.SUCCESS
                    )
                }
            }.onFailure { error ->
                handleAuthThrowable(error, "Failed to resend verification email.")
            }
        }
    }

    fun checkVerificationStatus(onVerified: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, bannerMessage = null) }
            val result = authRepository.reloadCurrentUser()
            result.onSuccess { user ->
                if (user.isEmailVerified) {
                    val provider = authRepository.resolveAuthProviderLabel(user)
                    userRepository.syncEmailVerified(user.uid, true, provider)
                    _uiState.update { it.copy(isLoading = false) }
                    onVerified()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            bannerMessage = "Email not verified yet. Open the link in your inbox, then tap Check Status again.",
                            bannerTone = BannerTone.WARNING
                        )
                    }
                }
            }.onFailure { error ->
                handleAuthThrowable(error, "Unable to refresh verification status.")
            }
        }
    }

    fun signOut(credentialManager: CredentialManager) {
        viewModelScope.launch {
            val uid = authRepository.currentUser()?.uid
            if (!uid.isNullOrBlank()) {
                userRepository.markSessionInactive(uid, ValidationUtils.currentDeviceSessionId())
            }
            authRepository.signOut(credentialManager)
            _uiState.update { AuthUiState() }
        }
    }

    private fun startVerificationCooldown() {
        verificationTimerJob?.cancel()
        verificationTimerJob = viewModelScope.launch {
            for (sec in 60 downTo 0) {
                _uiState.update { it.copy(verificationCooldownSec = sec) }
                if (sec > 0) delay(1000L)
            }
        }
    }

    private fun startResetCooldown() {
        resetTimerJob?.cancel()
        resetTimerJob = viewModelScope.launch {
            for (sec in 60 downTo 0) {
                _uiState.update { it.copy(resetCooldownSec = sec) }
                if (sec > 0) delay(1000L)
            }
        }
    }
}
