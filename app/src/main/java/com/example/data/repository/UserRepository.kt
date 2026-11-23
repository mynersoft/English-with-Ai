package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.domain.model.AccountStatus
import com.example.domain.model.LearningLevel
import com.example.domain.model.UserProfile
import com.example.domain.model.UserSession
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthenticatedUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val uid = requireAuthenticatedUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        emitAll(
            db.collection("users")
                .document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        )
    }

    fun observeSessions(userId: String): Flow<List<UserSession>> = flow {
        val uid = requireAuthenticatedUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("sessions")
                .snapshots()
                .map { querySnapshot ->
                    querySnapshot.toObjects(
                        UserSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.lastActiveAt?.seconds ?: 0L }
                }
        )
    }

    suspend fun ensureUserProfile(
        user: FirebaseUser,
        authProvider: String,
        learningLevel: LearningLevel = LearningLevel.Beginner,
        dailyGoalMinutes: Int = 20,
        preferredName: String? = null
    ): Result<Unit> {
        val docPath = "users/${user.uid}"
        return try {
            val uid = requireAuthenticatedUserId()
            check(uid == user.uid) { "Signed-in user changed." }
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()

            val resolvedEmail = (user.email ?: "learner@englishai.app").take(254)
            val resolvedName = (preferredName?.trim()?.takeIf { it.isNotBlank() }
                ?: user.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: resolvedEmail.substringBefore("@").ifBlank { "Learner" })
                .take(60)

            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "email" to resolvedEmail,
                    "displayName" to resolvedName,
                    "authProvider" to authProvider,
                    "emailVerified" to user.isEmailVerified,
                    "learningLevel" to learningLevel.name,
                    "dailyGoalMinutes" to dailyGoalMinutes.coerceIn(5, 240),
                    "banglaExplanations" to true,
                    "accountStatus" to AccountStatus.ACTIVE.name,
                    "currentDay" to 1,
                    "streakDays" to 1,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val existingVerified = snapshot.getBoolean("emailVerified") ?: false
                val existingProvider = snapshot.getString("authProvider") ?: "password"
                if (existingVerified != user.isEmailVerified || existingProvider != authProvider) {
                    docRef.update(
                        mapOf(
                            "emailVerified" to user.isEmailVerified,
                            "authProvider" to authProvider,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncEmailVerified(userId: String, verified: Boolean, authProvider: String): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireAuthenticatedUserId()
            check(uid == userId) { "Refusing cross-user write." }
            db.collection("users").document(userId).update(
                mapOf(
                    "emailVerified" to verified,
                    "authProvider" to authProvider,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfilePreferences(
        userId: String,
        displayName: String,
        learningLevel: LearningLevel,
        dailyGoalMinutes: Int,
        banglaExplanations: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireAuthenticatedUserId()
            check(uid == userId) { "Refusing cross-user write." }
            val trimmedName = displayName.trim()
            require(trimmedName.length in 1..60) { "Name must be between 1 and 60 characters." }
            val safeMinutes = dailyGoalMinutes.coerceIn(5, 240)

            auth.currentUser?.let { current ->
                if (current.displayName != trimmedName) {
                    current.updateProfile(
                        UserProfileChangeRequest.Builder().setDisplayName(trimmedName).build()
                    ).await()
                }
            }

            db.collection("users").document(userId).update(
                mapOf(
                    "displayName" to trimmedName,
                    "learningLevel" to learningLevel.name,
                    "dailyGoalMinutes" to safeMinutes,
                    "banglaExplanations" to banglaExplanations,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAccountStatus(userId: String, status: AccountStatus): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireAuthenticatedUserId()
            check(uid == userId) { "Refusing cross-user write." }
            db.collection("users").document(userId).update(
                mapOf(
                    "accountStatus" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertCurrentSession(
        userId: String,
        sessionId: String,
        deviceName: String,
        authProvider: String
    ): Result<Unit> {
        val docPath = "users/$userId/sessions/$sessionId"
        return try {
            val uid = requireAuthenticatedUserId()
            check(uid == userId) { "Refusing cross-user write." }
            require(sessionId.isNotBlank() && sessionId.length <= 128) { "Invalid session ID." }
            val docRef = db.collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)

            val existing = docRef.get().await()
            val safeDevice = deviceName.trim().ifBlank { "Android Device" }.take(100)
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "deviceName" to safeDevice,
                        "platform" to "Android",
                        "authProvider" to authProvider,
                        "activeSession" to true,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "deviceName" to safeDevice,
                        "authProvider" to authProvider,
                        "activeSession" to true,
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markSessionInactive(userId: String, sessionId: String): Result<Unit> {
        val docPath = "users/$userId/sessions/$sessionId"
        return try {
            val uid = auth.currentUser?.uid ?: return Result.success(Unit)
            if (uid != userId) return Result.success(Unit)
            val docRef = db.collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "activeSession" to false,
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun revokeSession(userId: String, sessionId: String): Result<Unit> {
        val docPath = "users/$userId/sessions/$sessionId"
        return try {
            val uid = requireAuthenticatedUserId()
            check(uid == userId) { "Refusing cross-user delete." }
            db.collection("users")
                .document(userId)
                .collection("sessions")
                .document(sessionId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAllUserData(userId: String): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireAuthenticatedUserId()
            check(uid == userId) { "Refusing cross-user delete." }
            val userRef = db.collection("users").document(userId)
            val sessionsSnapshot = userRef.collection("sessions").get().await()
            val batch = db.batch()
            for (doc in sessionsSnapshot.documents) {
                batch.delete(doc.reference)
            }
            batch.delete(userRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
