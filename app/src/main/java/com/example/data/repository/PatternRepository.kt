package com.example.data.repository

import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    val meaningAudioUrl: String?
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
    val examples: List<PatternExample>
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
                    meaningAudioUrl = o.optStringOrNull("meaningAudioUrl")
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
                examples = examples
            )
            Result.success(detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeGet(path: String): String {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            throw IOException("Backend URL is not configured.")
        }
        val token = runCatching {
            Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
        }.getOrNull()

        val reqBuilder = Request.Builder()
            .url("$baseUrl$path")
            .get()
            .header("Accept", "application/json")
        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $token")
        }

        return client.newCall(reqBuilder.build()).execute().use { res ->
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
        val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
