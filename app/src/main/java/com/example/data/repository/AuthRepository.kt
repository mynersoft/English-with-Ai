package com.example.data.repository

import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthEmailException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(
    private val auth: FirebaseAuth = Firebase.auth
) {
    fun currentUser(): FirebaseUser? = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Sign-in succeeded without user record."))
            user.reload().await()
            Result.success(auth.currentUser ?: user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Email sign-in failed: ${e.message}")
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun registerWithEmail(
        displayName: String,
        email: String,
        password: String
    ): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Registration failed to create user."))
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(displayName.trim())
                .build()
            user.updateProfile(profileUpdates).await()
            user.sendEmailVerification().await()
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Email registration failed: ${e.message}")
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("No signed-in user."))
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Send email verification failed: ${e.message}")
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun reloadCurrentUser(): Result<FirebaseUser> {
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("No signed-in user."))
            user.reload().await()
            val refreshed = auth.currentUser ?: user
            Result.success(refreshed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reload user failed: ${e.message}")
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Password reset failed: ${e.message}")
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun updatePasswordWithReauth(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("No signed-in user."))
            val email = user.email ?: return Result.failure(IllegalStateException("No email linked to this account."))
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Password update failed: ${e.message}")
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: return Result.failure(IllegalStateException("Google Sign-In returned no user."))
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Google Firebase credential sign-in failed: ${e.message}")
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun reauthenticateWithPassword(password: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("No signed-in user."))
            val email = user.email ?: return Result.failure(IllegalStateException("Account has no email."))
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun reauthenticateWithGoogleIdToken(idToken: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("No signed-in user."))
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun deleteCurrentAuthUser(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("No signed-in user."))
            user.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(toAuthFailure(e))
        }
    }

    suspend fun signOut(credentialManager: CredentialManager) {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    fun resolveAuthProviderLabel(user: FirebaseUser): String {
        val providers = user.providerData.map { it.providerId }.filter { it != "firebase" }
        val hasGoogle = providers.contains(GoogleAuthProvider.PROVIDER_ID)
        val hasPassword = providers.contains(EmailAuthProvider.PROVIDER_ID)
        return when {
            hasGoogle && hasPassword -> "multi"
            hasGoogle -> "google"
            else -> "password"
        }
    }

    fun requiresEmailVerification(user: FirebaseUser): Boolean {
        val providers = user.providerData.map { it.providerId }.filter { it != "firebase" }
        val usesPassword = providers.contains(EmailAuthProvider.PROVIDER_ID)
        val usesGoogle = providers.contains(GoogleAuthProvider.PROVIDER_ID)
        return usesPassword && !usesGoogle && !user.isEmailVerified
    }

    private fun mapAuthError(e: Exception): String {
        val rawMsg = e.message.orEmpty()
        if (rawMsg.contains("operation is not allowed", ignoreCase = true) ||
            rawMsg.contains("sign-in provider is disabled", ignoreCase = true)
        ) {
            return EMAIL_PROVIDER_DISABLED_MSG
        }
        return when (e) {
            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Use at least 8 characters with uppercase, lowercase, and a number."
            is FirebaseAuthInvalidCredentialsException ->
                "Invalid email or password. Please check your details and try again."
            is FirebaseAuthInvalidUserException ->
                "No active account matches this email. Please check the address or create a new account."
            is FirebaseAuthUserCollisionException ->
                "An account already exists with this email. Try signing in or using Continue with Google."
            is FirebaseAuthRecentLoginRequiredException ->
                "For your security, please re-authenticate before completing this sensitive change."
            is FirebaseAuthEmailException ->
                "We could not deliver the verification or recovery email right now. Please try again shortly."
            is FirebaseNetworkException ->
                "No internet connection. Please check your network and try again."
            is FirebaseAuthException -> {
                when (e.errorCode) {
                    "ERROR_OPERATION_NOT_ALLOWED" -> EMAIL_PROVIDER_DISABLED_MSG
                    "ERROR_TOO_MANY_REQUESTS" ->
                        "Too many requests in a short time. Please wait a minute before trying again."
                    "ERROR_USER_DISABLED" ->
                        "This account has been disabled. Contact support for assistance."
                    else -> e.localizedMessage ?: "Authentication failed. Please try again."
                }
            }
            else -> e.localizedMessage ?: "Authentication failed. Please try again."
        }
    }

    private fun isProviderDisabledException(e: Exception): Boolean {
        if (e is FirebaseAuthException && e.errorCode == "ERROR_OPERATION_NOT_ALLOWED") return true
        val msg = e.message.orEmpty()
        return msg.contains("operation is not allowed", ignoreCase = true) ||
            msg.contains("sign-in provider is disabled", ignoreCase = true)
    }

    private fun toAuthFailure(e: Exception): AuthFailureException {
        return AuthFailureException(
            message = mapAuthError(e),
            cause = e,
            isProviderDisabled = isProviderDisabledException(e)
        )
    }

    private companion object {
        const val TAG = "AuthRepository"
        const val EMAIL_PROVIDER_DISABLED_MSG =
            "Email/Password authentication is currently disabled in Firebase Console for this project. Enable it under Authentication > Sign-in method, or sign in with Google below."
    }
}

class AuthFailureException(
    override val message: String,
    cause: Throwable? = null,
    val isProviderDisabled: Boolean = false
) : Exception(message, cause)
