package com.example.core.wordofday

import android.content.Context
import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONObject

object WordOfTheDaySync {
    suspend fun syncPool(context: Context): List<WordOfTheDayEntry> = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        val user = runCatching { Firebase.auth.currentUser }.getOrNull()
        val token = if (baseUrl.isNotBlank() && user != null) {
            runCatching { user.getIdToken(false).await().token }.getOrNull()
        } else {
            null
        }
        if (baseUrl.isBlank() || token.isNullOrBlank()) {
            return@withContext WordOfTheDayStorage.getCachedPool(context)
        }

        val fromDedicatedEndpoint = fetchFromDedicatedEndpoint(baseUrl, token)
        if (fromDedicatedEndpoint.isNotEmpty()) {
            WordOfTheDayStorage.saveCachedPool(context, fromDedicatedEndpoint)
            return@withContext fromDedicatedEndpoint
        }

        // Fallback to existing paginated vocabulary catalog if backend has not yet redeployed /word-of-the-day
        val fromCatalogEndpoint = fetchFromWordsCatalog(baseUrl, token)
        if (fromCatalogEndpoint.isNotEmpty()) {
            WordOfTheDayStorage.saveCachedPool(context, fromCatalogEndpoint)
            return@withContext fromCatalogEndpoint
        }

        WordOfTheDayStorage.getCachedPool(context)
    }

    suspend fun resolveEntryForNotification(
        context: Context,
        todayKey: String = WordOfTheDayStorage.todayDateKey()
    ): WordOfTheDayEntry? {
        if (WordOfTheDayStorage.getLastSyncDate(context) != todayKey) {
            withTimeoutOrNull(4500L) {
                runCatching { syncPool(context) }
            }
        }
        val pool = WordOfTheDayStorage.getCachedPool(context)
        val shown = WordOfTheDayStorage.getShownWordKeys(context)
        return WordOfTheDayRules.pickNextUnshownWord(
            candidates = pool,
            shownKeys = shown,
            seedDateKey = todayKey
        )
    }

    private fun fetchFromDedicatedEndpoint(baseUrl: String, token: String): List<WordOfTheDayEntry> {
        val data = getJson("$baseUrl/api/vocabulary/word-of-the-day", token) ?: return emptyList()
        val out = mutableListOf<WordOfTheDayEntry>()
        WordOfTheDayEntry.fromJson(data.optJSONObject("wordOfTheDay"))?.let(out::add)
        val upcoming = data.optJSONArray("upcoming")
        if (upcoming != null) {
            for (i in 0 until upcoming.length()) {
                WordOfTheDayEntry.fromJson(upcoming.optJSONObject(i))?.let(out::add)
            }
        }
        return out.distinctBy { it.normalizedKey() }
    }

    private fun fetchFromWordsCatalog(baseUrl: String, token: String): List<WordOfTheDayEntry> {
        val data = getJson("$baseUrl/api/vocabulary/words?limit=35", token) ?: return emptyList()
        val items = data.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<WordOfTheDayEntry>()
        for (i in 0 until items.length()) {
            WordOfTheDayEntry.fromJson(items.optJSONObject(i))?.let(out::add)
        }
        return out.distinctBy { it.normalizedKey() }
    }

    private fun getJson(url: String, token: String): JSONObject? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()
        NetworkClient.standard.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use null
            val raw = res.body?.string().orEmpty()
            val root = JSONObject(raw)
            if (!root.optBoolean("success", true)) return@use null
            root.optJSONObject("data") ?: root
        }
    }.getOrNull()
}
