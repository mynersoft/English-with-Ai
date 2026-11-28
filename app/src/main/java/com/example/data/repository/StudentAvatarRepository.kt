package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.example.core.ui.AvatarImageHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

data class AvatarUploadOutcome(
    val avatarUrl: String,
    val syncedToCloud: Boolean,
    val message: String
)

object StudentAvatarRepository {
    private const val TAG = "StudentAvatarRepo"
    private const val PREFS_NAME = "english_ai_avatar_prefs"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCachedAvatarUrl(context: Context, userId: String): String? {
        if (userId.isBlank()) return null
        val stored = prefs(context).getString("avatar_url_$userId", null)?.trim()
        if (stored.isNullOrBlank()) return null
        if (stored.startsWith("file://")) {
            val file = File(stored.removePrefix("file://"))
            if (!file.exists() || file.length() == 0L) return null
        }
        return stored
    }

    fun cacheRemoteAvatarUrlIfPresent(context: Context, userId: String, remoteUrl: String?) {
        if (userId.isBlank()) return
        val clean = remoteUrl?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return
        prefs(context).edit().putString("avatar_url_$userId", clean).apply()
    }

    fun resolveBestAvatarUrl(context: Context, userId: String, remoteUrl: String?): String? {
        val cleanRemote = remoteUrl?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val cached = getCachedAvatarUrl(context, userId)
        return cleanRemote ?: cached ?: Firebase.auth.currentUser?.photoUrl?.toString()?.takeIf { it.isNotBlank() }
    }

    suspend fun uploadAvatar(
        context: Context,
        userId: String,
        sourceUri: Uri
    ): Result<AvatarUploadOutcome> {
        val appContext = context.applicationContext
        val bytesResult = AvatarImageHelper.prepareAvatarJpeg(appContext, sourceUri)
        val jpegBytes = bytesResult.getOrElse { return Result.failure(it) }

        val localFile = withContext(Dispatchers.IO) {
            AvatarImageHelper.saveLocalAvatarFile(appContext, userId, jpegBytes)
        }
        val localFileUri = Uri.fromFile(localFile).toString()
        prefs(appContext).edit().putString("avatar_url_$userId", localFileUri).apply()

        val cloudUploadResult = uploadBytesToBackend(jpegBytes)
        return cloudUploadResult.fold(
            onSuccess = { remoteUrl ->
                prefs(appContext).edit().putString("avatar_url_$userId", remoteUrl).apply()
                if (remoteUrl.startsWith("https://", ignoreCase = true) ||
                    remoteUrl.startsWith("http://", ignoreCase = true)
                ) {
                    runCatching {
                        Firebase.auth.currentUser?.updateProfile(
                            UserProfileChangeRequest.Builder()
                                .setPhotoUri(Uri.parse(remoteUrl))
                                .build()
                        )?.await()
                    }
                }
                Result.success(
                    AvatarUploadOutcome(
                        avatarUrl = remoteUrl,
                        syncedToCloud = true,
                        message = "Profile photo updated."
                    )
                )
            },
            onFailure = { err ->
                Log.w(TAG, "Cloud avatar upload fell back to device storage: ${err.message}")
                Result.success(
                    AvatarUploadOutcome(
                        avatarUrl = localFileUri,
                        syncedToCloud = false,
                        message = "Profile photo saved on this device. Cloud sync will update when the server is reachable."
                    )
                )
            }
        )
    }

    suspend fun removeAvatar(context: Context, userId: String): Result<Unit> {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            AvatarImageHelper.clearLocalAvatarFiles(appContext, userId)
        }
        prefs(appContext).edit().remove("avatar_url_$userId").apply()

        runCatching {
            Firebase.auth.currentUser?.updateProfile(
                UserProfileChangeRequest.Builder().setPhotoUri(null).build()
            )?.await()
        }

        val backendResult = runCatching {
            NetworkClient.executeAuthorizedJson("DELETE", "/api/v1/student/avatar")
        }
        backendResult.onFailure { err ->
            if (err is CancellationException) throw err
            Log.w(TAG, "Cloud avatar removal skipped: ${err.message}")
        }
        return Result.success(Unit)
    }

    private suspend fun uploadBytesToBackend(jpegBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(IllegalStateException("BACKEND_BASE_URL not configured"))
            }
            val user = Firebase.auth.currentUser
                ?: return@withContext Result.failure(IllegalStateException("Not signed in"))

            var token = user.getIdToken(false).await().token
                ?: return@withContext Result.failure(IllegalStateException("Missing auth token"))

            var refreshed = false
            for (attempt in 0..1) {
                val fileBody = jpegBytes.toRequestBody("image/jpeg".toMediaType())
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "avatar_${System.currentTimeMillis()}.jpg", fileBody)
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/api/v1/student/avatar")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .post(multipart)
                    .build()

                var retryAuth = false
                val parsedUrl: String? = NetworkClient.admin.newCall(request).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (res.code == 401 && !refreshed) {
                        refreshed = true
                        val fresh = runCatching { user.getIdToken(true).await().token }.getOrNull()
                        if (!fresh.isNullOrBlank()) {
                            token = fresh
                            retryAuth = true
                            return@use null
                        }
                    }
                    val json = runCatching { JSONObject(raw) }.getOrNull()
                    if (!res.isSuccessful || json?.optBoolean("success", true) == false) {
                        val errMsg = json?.optJSONObject("error")?.optString("message")
                            ?.takeIf { it.isNotBlank() }
                            ?: "Avatar upload failed (${res.code})"
                        throw BackendApiException(errMsg, null, res.code)
                    }
                    val data = json?.optJSONObject("data") ?: json
                    val photoUrl = data?.optString("photoUrl")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: throw IllegalStateException("Server did not return photoUrl")
                    photoUrl
                }

                if (!retryAuth && parsedUrl != null) {
                    return@withContext Result.success(parsedUrl)
                }
            }
            Result.failure(IllegalStateException("Avatar upload failed after token refresh."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
