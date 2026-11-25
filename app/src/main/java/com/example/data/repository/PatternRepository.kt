package com.example.data.repository

import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class PatternSummary(
    val id: String,
    val title: String,
    val meaning: String,
    val isPremium: Boolean,
    val order: Int,
    val sentenceCount: Int,
    val audioTotal: Int,
    val audioReady: Int,
    val titleAudioUrl: String?,
    val meaningAudioUrl: String?,
    val isCompleted: Boolean = false
)

data class PatternExample(
    val id: String,
    val en: String,
    val bn: String,
    val enAudioUrl: String?,
    val bnAudioUrl: String?
)

data class PatternDetail(
    val id: String,
    val title: String,
    val meaning: String,
    val explanation: String,
    val isPremium: Boolean,
    val order: Int,
    val titleAudioUrl: String?,
    val meaningAudioUrl: String?,
    val examples: List<PatternExample>,
    val isCompleted: Boolean = false
)

class PatternRepository {

    suspend fun fetchPatterns(): Result<List<PatternSummary>> = withContext(Dispatchers.IO) {
        try {
            val raw = executeGet("/api/v1/patterns")
            val arr = JSONArray(raw)
            val list = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                PatternSummary(
                    id = o.optString("id").ifBlank { o.optString("_id") },
                    title = o.optString("title"),
                    meaning = o.optString("meaning"),
                    isPremium = o.optBoolean("isPremium", false),
                    order = o.optInt("order", i),
                    sentenceCount = o.optInt("sentenceCount", 0),
                    audioTotal = o.optInt("audioTotal", 0),
                    audioReady = o.optInt("audioReady", 0),
                    titleAudioUrl = o.optStringOrNull("titleAudioUrl"),
                    meaningAudioUrl = o.optStringOrNull("meaningAudioUrl"),
                    isCompleted = o.optBoolean("isCompleted", false)
                )
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPatternDetail(patternId: String): Result<PatternDetail> = withContext(Dispatchers.IO) {
        try {
            val raw = executeGet("/api/v1/patterns/$patternId")
            val o = JSONObject(raw)
            val exArray = o.optJSONArray("examples") ?: JSONArray()
            val examples = List(exArray.length()) { i ->
                val ex = exArray.getJSONObject(i)
                PatternExample(
                    id = ex.optString("id").ifBlank { ex.optString("_id", "$i") },
                    en = ex.optString("en"),
                    bn = ex.optString("bn"),
                    enAudioUrl = ex.optJSONObject("enAudio")?.optStringOrNull("url"),
                    bnAudioUrl = ex.optJSONObject("bnAudio")?.optStringOrNull("url")
                )
            }
            val detail = PatternDetail(
                id = o.optString("id").ifBlank { o.optString("_id", patternId) },
                title = o.optString("title"),
                meaning = o.optString("meaning"),
                explanation = o.optStringOrNull("explanation").orEmpty(),
                isPremium = o.optBoolean("isPremium", false),
                order = o.optInt("order", 0),
                titleAudioUrl = o.optJSONObject("titleAudio")?.optStringOrNull("url"),
                meaningAudioUrl = o.optJSONObject("meaningAudio")?.optStringOrNull("url"),
                examples = examples,
                isCompleted = o.optBoolean("isCompleted", false)
            )
            Result.success(detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPatternCompleted(
        patternId: String,
        completed: Boolean
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
            if (baseUrl.isBlank()) {
                throw IOException("Backend URL is not configured.")
            }
            val user = Firebase.auth.currentUser
                ?: throw IOException("Please sign in to save learned patterns.")
            val token = user.getIdToken(false).await().token
                ?: throw IOException("Could not verify authentication token.")

            val payload = JSONObject().put("completed", completed).toString()
            val req = Request.Builder()
                .url("$baseUrl/api/v1/patterns/$patternId/progress")
                .post(payload.toRequestBody(JSON_TYPE))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()

            NetworkClient.standard.newCall(req).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (!res.isSuccessful || json?.optBoolean("success", true) == false) {
                    val errObj = json?.optJSONObject("error")
                    val msg = errObj?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: when {
                            res.code == 404 && body.trimStart().startsWith("<") ->
                                "Pattern progress API not deployed yet (404)."
                            body.trimStart().startsWith("<") -> "Server error (${res.code})"
                            else -> body.take(160)
                        }
                    throw IOException(msg.ifBlank { "Could not save pattern progress (${res.code})." })
                }
                val savedCompleted = json?.optBoolean("isCompleted", completed) ?: completed
                Result.success(savedCompleted)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun optionalAuthToken(): String? {
        val user = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return null
        return runCatching { user.getIdToken(false).await().token }.getOrNull()
    }

    private suspend fun executeGet(path: String): String {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            throw IOException("Backend URL is not configured.")
        }
        val token = optionalAuthToken()
        val reqBuilder = Request.Builder()
            .url("$baseUrl$path")
            .get()
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $token")
        }

        return NetworkClient.standard.newCall(reqBuilder.build()).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching {
                    val j = JSONObject(body)
                    j.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: j.optString("error").takeIf { it.isNotBlank() }
                }.getOrNull()
                    ?: if (body.trimStart().startsWith("<")) "Server error (${res.code})" else body.take(160)
                throw IOException(msg.ifBlank { "Error ${res.code} while loading patterns." })
            }
            body
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
