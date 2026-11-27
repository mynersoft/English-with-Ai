package com.example.data.repository

import com.example.core.network.NetworkClient
import com.example.core.network.sanitizeForBackendFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PatternSummary(
    val id: String,
    val title: String,
    val meaning: String,
    val categoryName: String = "All",
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
    val categoryName: String = "All",
    val explanation: String,
    val isPremium: Boolean,
    val order: Int,
    val titleAudioUrl: String?,
    val meaningAudioUrl: String?,
    val examples: List<PatternExample>
)

class PatternRepository {

    suspend fun fetchPatterns(forceRefresh: Boolean = false): Result<List<PatternSummary>> = withContext(Dispatchers.IO) {
        try {
            if (!forceRefresh) {
                getCachedPatterns()?.let { return@withContext Result.success(it) }
            }
            val raw = NetworkClient.executePublicGetText("/api/v1/patterns")
            val arr = JSONArray(raw)
            val list = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                PatternSummary(
                    id = o.optString("id").ifBlank { o.optString("_id") },
                    title = o.optString("title"),
                    meaning = o.optString("meaning"),
                    categoryName = o.optString("categoryName").trim().ifBlank { "All" },
                    isPremium = o.optBoolean("isPremium", false),
                    order = o.optInt("order", i),
                    sentenceCount = o.optInt("sentenceCount", 0),
                    audioTotal = o.optInt("audioTotal", 0),
                    audioReady = o.optInt("audioReady", 0),
                    titleAudioUrl = o.optStringOrNull("titleAudioUrl"),
                    meaningAudioUrl = o.optStringOrNull("meaningAudioUrl")
                )
            }
            putCachedPatterns(list)
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.sanitizeForBackendFailure())
        }
    }

    suspend fun fetchPatternDetail(
        patternId: String,
        forceRefresh: Boolean = false
    ): Result<PatternDetail> = withContext(Dispatchers.IO) {
        try {
            if (!forceRefresh) {
                getCachedDetail(patternId)?.let { return@withContext Result.success(it) }
            }
            val raw = NetworkClient.executePublicGetText("/api/v1/patterns/$patternId")
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
                categoryName = o.optString("categoryName").trim().ifBlank { "All" },
                explanation = o.optStringOrNull("explanation").orEmpty(),
                isPremium = o.optBoolean("isPremium", false),
                order = o.optInt("order", 0),
                titleAudioUrl = o.optJSONObject("titleAudio")?.optStringOrNull("url"),
                meaningAudioUrl = o.optJSONObject("meaningAudio")?.optStringOrNull("url"),
                examples = examples
            )
            putCachedDetail(patternId, detail)
            Result.success(detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.sanitizeForBackendFailure())
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    companion object {
        private const val CACHE_TTL_MS = 120_000L
        private val cacheLock = Any()

        private var cachedPatterns: List<PatternSummary>? = null
        private var cachedPatternsAtMs: Long = 0L

        private data class CachedDetailEntry(val detail: PatternDetail, val savedAtMs: Long)

        private val detailCache = object : LinkedHashMap<String, CachedDetailEntry>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDetailEntry>?): Boolean =
                size > 40
        }

        private fun getCachedPatterns(): List<PatternSummary>? = synchronized(cacheLock) {
            val data = cachedPatterns ?: return null
            if (System.currentTimeMillis() - cachedPatternsAtMs > CACHE_TTL_MS) {
                cachedPatterns = null
                return null
            }
            return data
        }

        private fun putCachedPatterns(list: List<PatternSummary>) = synchronized(cacheLock) {
            cachedPatterns = list
            cachedPatternsAtMs = System.currentTimeMillis()
        }

        private fun getCachedDetail(id: String): PatternDetail? = synchronized(cacheLock) {
            val entry = detailCache[id] ?: return null
            if (System.currentTimeMillis() - entry.savedAtMs > CACHE_TTL_MS) {
                detailCache.remove(id)
                return null
            }
            return entry.detail
        }

        private fun putCachedDetail(id: String, detail: PatternDetail) = synchronized(cacheLock) {
            detailCache[id] = CachedDetailEntry(detail, System.currentTimeMillis())
        }
    }
}
