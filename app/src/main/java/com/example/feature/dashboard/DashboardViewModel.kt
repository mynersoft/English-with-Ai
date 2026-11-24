package com.example.feature.dashboard

import android.util.Log
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.OperationType
import com.example.core.common.UiState
import com.example.core.common.ValidationUtils
import com.example.core.common.handleFirestoreError
import com.example.core.ui.BannerTone
import com.example.data.repository.AuthRepository
import com.example.data.repository.UserRepository
import com.example.domain.model.AccountStatus
import com.example.domain.model.LearningLevel
import com.example.domain.model.UserProfile
import com.example.domain.model.UserSession
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class DashboardActionState(
    val isBusy: Boolean = false,
    val bannerMessage: String? = null,
    val bannerTone: BannerTone = BannerTone.INFO,
    val backendSyncError: String? = null,
    val isSyncingBackend: Boolean = false
)

class DashboardViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    val currentUserId: String
) : ViewModel() {

    private val _actionState = MutableStateFlow(DashboardActionState())
    val actionState: StateFlow<DashboardActionState> = _actionState.asStateFlow()

    val currentSessionId: String = ValidationUtils.currentDeviceSessionId()

    val profileState: StateFlow<UiState<UserProfile?>> = userRepository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            if (Firebase.auth.currentUser?.uid == currentUserId) {
                handleFirestoreError(rootError, OperationType.GET, "users/$currentUserId")
            }
            Log.w(TAG, "Error observing user profile", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val sessionsState: StateFlow<UiState<List<UserSession>>> = userRepository.observeSessions(currentUserId)
        .map<List<UserSession>, UiState<List<UserSession>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            if (Firebase.auth.currentUser?.uid == currentUserId) {
                handleFirestoreError(rootError, OperationType.LIST, "users/$currentUserId/sessions")
            }
            Log.w(TAG, "Error observing sessions", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load active sessions"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    init {
        syncInitialProfileAndSession()
    }

    fun syncBackendUser(preferredName: String? = null) {
        viewModelScope.launch {
            _actionState.update { it.copy(isSyncingBackend = true) }
            val result = authRepository.syncCurrentUserToBackend(preferredName = preferredName)
            result.onSuccess {
                _actionState.update {
                    it.copy(isSyncingBackend = false, backendSyncError = null)
                }
            }.onFailure { err ->
                _actionState.update {
                    it.copy(
                        isSyncingBackend = false,
                        backendSyncError = err.message ?: "Could not sync account to MongoDB backend."
                    )
                }
            }
        }
    }

    private fun syncInitialProfileAndSession() {
        viewModelScope.launch {
            val user = authRepository.currentUser() ?: return@launch
            if (user.uid != currentUserId) return@launch
            val provider = authRepository.resolveAuthProviderLabel(user)
            userRepository.ensureUserProfile(user = user, authProvider = provider)
            userRepository.upsertCurrentSession(
                userId = currentUserId,
                sessionId = currentSessionId,
                deviceName = ValidationUtils.currentDeviceName(),
                authProvider = provider
            )
        }
    }

    fun hasPasswordProvider(): Boolean {
        val user = authRepository.currentUser() ?: return false
        return user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
    }

    fun currentEmail(): String = authRepository.currentUser()?.email.orEmpty()

    fun isEmailVerified(): Boolean = authRepository.currentUser()?.isEmailVerified ?: false

    fun clearBanner() {
        _actionState.update { it.copy(bannerMessage = null) }
    }

    fun showError(message: String) {
        _actionState.update {
            it.copy(
                isBusy = false,
                bannerMessage = message,
                bannerTone = BannerTone.ERROR
            )
        }
    }

    fun updateProfile(
        displayName: String,
        learningLevel: LearningLevel,
        dailyGoalMinutes: Int,
        banglaExplanations: Boolean
    ) {
        if (!ValidationUtils.isValidDisplayName(displayName)) {
            _actionState.update {
                it.copy(
                    bannerMessage = "Name must be between 2 and 60 characters.",
                    bannerTone = BannerTone.ERROR
                )
            }
            return
        }

        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, bannerMessage = null) }
            val result = userRepository.updateProfilePreferences(
                userId = currentUserId,
                displayName = displayName,
                learningLevel = learningLevel,
                dailyGoalMinutes = dailyGoalMinutes,
                banglaExplanations = banglaExplanations
            )
            result.onSuccess {
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = "Profile preferences saved.",
                        bannerTone = BannerTone.SUCCESS
                    )
                }
            }.onFailure { error ->
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = error.message ?: "Failed to update profile.",
                        bannerTone = BannerTone.ERROR
                    )
                }
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        if (currentPassword.isBlank()) {
            _actionState.update {
                it.copy(bannerMessage = "Enter your current password.", bannerTone = BannerTone.ERROR)
            }
            return
        }
        if (!ValidationUtils.evaluatePassword(newPassword).isStrongEnough) {
            _actionState.update {
                it.copy(
                    bannerMessage = "New password must be at least 8 characters with uppercase, lowercase, and a number.",
                    bannerTone = BannerTone.ERROR
                )
            }
            return
        }
        if (newPassword != confirmPassword) {
            _actionState.update {
                it.copy(bannerMessage = "New passwords do not match.", bannerTone = BannerTone.ERROR)
            }
            return
        }

        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, bannerMessage = null) }
            val result = authRepository.updatePasswordWithReauth(currentPassword, newPassword)
            result.onSuccess {
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = "Password updated securely.",
                        bannerTone = BannerTone.SUCCESS
                    )
                }
            }.onFailure { error ->
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = error.message ?: "Could not update password.",
                        bannerTone = BannerTone.ERROR
                    )
                }
            }
        }
    }

    fun sendPasswordResetEmailToSelf() {
        val email = currentEmail()
        if (email.isBlank()) return
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, bannerMessage = null) }
            val result = authRepository.sendPasswordResetEmail(email)
            result.onSuccess {
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = "Password recovery email sent to $email.",
                        bannerTone = BannerTone.SUCCESS
                    )
                }
            }.onFailure { error ->
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = error.message ?: "Failed to send password reset email.",
                        bannerTone = BannerTone.ERROR
                    )
                }
            }
        }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, bannerMessage = null) }
            val result = userRepository.revokeSession(currentUserId, sessionId)
            result.onSuccess {
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = "Device session removed.",
                        bannerTone = BannerTone.INFO
                    )
                }
            }.onFailure { error ->
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = error.message ?: "Failed to remove session.",
                        bannerTone = BannerTone.ERROR
                    )
                }
            }
        }
    }

    fun reactivateAccount() {
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, bannerMessage = null) }
            val statusResult = userRepository.updateAccountStatus(currentUserId, AccountStatus.ACTIVE)
            statusResult.onSuccess {
                val user = authRepository.currentUser()
                val provider = user?.let { authRepository.resolveAuthProviderLabel(it) } ?: "password"
                userRepository.upsertCurrentSession(
                    userId = currentUserId,
                    sessionId = currentSessionId,
                    deviceName = ValidationUtils.currentDeviceName(),
                    authProvider = provider
                )
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = "Welcome back! Your account is active again.",
                        bannerTone = BannerTone.SUCCESS
                    )
                }
            }.onFailure { error ->
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = error.message ?: "Could not reactivate account.",
                        bannerTone = BannerTone.ERROR
                    )
                }
            }
        }
    }

    fun deactivateAccount(credentialManager: CredentialManager) {
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, bannerMessage = null) }
            try {
                val result = userRepository.updateAccountStatus(currentUserId, AccountStatus.DEACTIVATED)
                result.onSuccess {
                    withTimeoutOrNull(800L) {
                        runCatching { userRepository.markSessionInactive(currentUserId, currentSessionId) }
                    }
                    authRepository.signOut(credentialManager)
                }.onFailure { error ->
                    _actionState.update {
                        it.copy(
                            isBusy = false,
                            bannerMessage = error.message ?: "Failed to deactivate account.",
                            bannerTone = BannerTone.ERROR
                        )
                    }
                }
            } finally {
                _actionState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun deleteAccountWithPassword(password: String, credentialManager: CredentialManager) {
        if (password.isBlank()) {
            _actionState.update {
                it.copy(bannerMessage = "Enter your password to confirm deletion.", bannerTone = BannerTone.ERROR)
            }
            return
        }
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, bannerMessage = null) }
            val reauth = authRepository.reauthenticateWithPassword(password)
            reauth.onFailure { error ->
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = error.message ?: "Password verification failed.",
                        bannerTone = BannerTone.ERROR
                    )
                }
                return@launch
            }
            performAccountDeletion(credentialManager)
        }
    }

    fun deleteAccountWithGoogleToken(idToken: String, credentialManager: CredentialManager) {
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true, bannerMessage = null) }
            val reauth = authRepository.reauthenticateWithGoogleIdToken(idToken)
            reauth.onFailure { error ->
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = error.message ?: "Google re-authentication failed.",
                        bannerTone = BannerTone.ERROR
                    )
                }
                return@launch
            }
            performAccountDeletion(credentialManager)
        }
    }

    private suspend fun performAccountDeletion(credentialManager: CredentialManager) {
        try {
            val dataResult = userRepository.deleteAllUserData(currentUserId)
            dataResult.onFailure { error ->
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = error.message ?: "Could not delete cloud data.",
                        bannerTone = BannerTone.ERROR
                    )
                }
                return
            }
            val authResult = authRepository.deleteCurrentAuthUser()
            authResult.onSuccess {
                authRepository.signOut(credentialManager)
            }.onFailure { error ->
                _actionState.update {
                    it.copy(
                        isBusy = false,
                        bannerMessage = error.message ?: "Could not delete authentication account.",
                        bannerTone = BannerTone.ERROR
                    )
                }
            }
        } finally {
            _actionState.update { it.copy(isBusy = false) }
        }
    }

    fun signOut(credentialManager: CredentialManager) {
        viewModelScope.launch {
            _actionState.update { it.copy(isBusy = true) }
            try {
                withTimeoutOrNull(800L) {
                    try {
                        userRepository.markSessionInactive(currentUserId, currentSessionId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
                authRepository.signOut(credentialManager)
            } finally {
                _actionState.update { DashboardActionState() }
            }
        }
    }

    private companion object {
        const val TAG = "DashboardVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
