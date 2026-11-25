package com.example.feature.admin

import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

// ---------- models ----------

data class AdminAudio(
    val url: String? = null,
    val source: String? = null, // "tts" | "manual"
    val stale: Boolean = false
)

data class AdminPatternSummary(
    val id: String,
    val title: String,
    val meaning: String,
    val categoryName: String = "All",
    val isPremium: Boolean,
    val order: Int,
    val sentenceCount: Int,
    val audioTotal: Int,
    val audioReady: Int,
    val audioStale: Int
)

data class ExampleForm(
    val key: String = UUID.randomUUID().toString(), // only for LazyColumn keys
    val id: String? = null,                          // null = not saved yet
    val en: String = "",
    val bn: String = "",
    val enAudio: AdminAudio = AdminAudio(),
    val bnAudio: AdminAudio = AdminAudio()
)

data class PatternForm(
    val id: String? = null,
    val title: String = "",
    val meaning: String = "",
    val categoryName: String = "All",
    val explanation: String = "",
    val isPremium: Boolean = false,
    val order: String = "0",
    val titleAudio: AdminAudio = AdminAudio(),
    val meaningAudio: AdminAudio = AdminAudio(),
    val examples: List<ExampleForm> = listOf(ExampleForm())
)

/** target: "title" | "meaning" | "example"  (index + lang "en"/"bn" for example) */
data class AudioTarget(val target: String, val index: Int? = null, val lang: String? = null) {
    fun busyId() = when (target) {
        "example" -> "ex${index}_$lang"
        else -> target
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("target", target)
        index?.let { put("index", it) }
        lang?.let { put("lang", it) }
    }
}

// ---------- API ----------

object AdminApi {
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private suspend fun idToken(): String {
        val user = Firebase.auth.currentUser ?: throw IOException("Not signed in")
        return user.getIdToken(false).await().token ?: throw IOException("No auth token")
    }

    private suspend fun call(path: String, method: String, body: RequestBody? = null): String {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            throw IOException("Backend URL is not configured yet.")
        }
        val token = idToken()
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api/v1/$path")
                .header("Authorization", "Bearer $token")
                .method(method, body)
                .build()
            NetworkClient.admin.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = runCatching {
                        val jsonErr = JSONObject(text)
                        jsonErr.optJSONObject("error")?.optString("message")
                            ?.takeIf { it.isNotBlank() }
                            ?: jsonErr.optString("error")
                    }.getOrNull().orEmpty()
                    val fallback = when (res.code) {
                        401, 403 -> "Unauthorized (${res.code}): Ensure your backend is updated and your account has admin access."
                        else -> "Error ${res.code}"
                    }
                    throw IOException(
                        if (msg.isNotBlank() && res.code != 401 && res.code != 403) msg else fallback
                    )
                }
                text
            }
        }
    }

    private fun json(o: JSONObject) = o.toString().toRequestBody(JSON_TYPE)

    private fun JSONObject?.toAudio(): AdminAudio {
        if (this == null) return AdminAudio()
        return AdminAudio(
            url = if (isNull("url")) null else optString("url"),
            source = if (isNull("source")) null else optString("source"),
            stale = optBoolean("stale", false)
        )
    }

    // ----- admin check -----
    suspend fun isAdmin(): Boolean = runCatching {
        JSONObject(call("admin/me", "GET")).optBoolean("admin", false)
    }.getOrDefault(false)

    // ----- patterns -----
    suspend fun listPatterns(): List<AdminPatternSummary> {
        val arr = JSONArray(call("patterns", "GET"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            AdminPatternSummary(
                id = o.getString("id"),
                title = o.optString("title"),
                meaning = o.optString("meaning"),
                categoryName = o.optString("categoryName").trim().ifBlank { "All" },
                isPremium = o.optBoolean("isPremium"),
                order = o.optInt("order"),
                sentenceCount = o.optInt("sentenceCount"),
                audioTotal = o.optInt("audioTotal"),
                audioReady = o.optInt("audioReady"),
                audioStale = o.optInt("audioStale")
            )
        }
    }

    suspend fun getPattern(id: String): PatternForm {
        val o = JSONObject(call("patterns/$id", "GET"))
        val ex = o.optJSONArray("examples") ?: JSONArray()
        return PatternForm(
            id = o.optString("id").ifBlank { o.getString("_id") },
            title = o.optString("title"),
            meaning = o.optString("meaning"),
            categoryName = o.optString("categoryName").trim().ifBlank { "All" },
            explanation = if (o.isNull("explanation")) "" else o.optString("explanation"),
            isPremium = o.optBoolean("isPremium"),
            order = o.optInt("order").toString(),
            titleAudio = o.optJSONObject("titleAudio").toAudio(),
            meaningAudio = o.optJSONObject("meaningAudio").toAudio(),
            examples = List(ex.length()) { i ->
                val e = ex.getJSONObject(i)
                ExampleForm(
                    id = e.optString("id").ifBlank { e.optString("_id").ifBlank { null } },
                    en = e.optString("en"),
                    bn = e.optString("bn"),
                    enAudio = e.optJSONObject("enAudio").toAudio(),
                    bnAudio = e.optJSONObject("bnAudio").toAudio()
                )
            }
        )
    }

    /** Creates or updates. Returns the pattern id. */
    suspend fun savePattern(form: PatternForm): String {
        val examples = JSONArray()
        form.examples
            .filter { it.en.isNotBlank() && it.bn.isNotBlank() }
            .forEach {
                examples.put(JSONObject().apply {
                    it.id?.let { id -> put("id", id) }
                    put("en", it.en.trim())
                    put("bn", it.bn.trim())
                })
            }
        val body = JSONObject().apply {
            put("title", form.title.trim())
            put("meaning", form.meaning.trim())
            put("categoryName", form.categoryName.trim().ifBlank { "All" })
            put("explanation", form.explanation)
            put("isPremium", form.isPremium)
            put("order", form.order.toIntOrNull() ?: 0)
            put("examples", examples)
        }
        return if (form.id == null) {
            val created = JSONObject(call("patterns", "POST", json(body)))
            created.optString("_id").ifBlank { created.getString("id") }
        } else {
            call("patterns/${form.id}", "PUT", json(body))
            form.id
        }
    }

    suspend fun deletePattern(id: String) {
        call("patterns/$id", "DELETE")
    }

    // ----- audio -----
    /** Returns "Generated X, skipped Y" counts. */
    suspend fun generateAudio(id: String, target: JSONObject, force: Boolean): Pair<Int, Int> {
        target.put("force", force)
        val o = JSONObject(call("patterns/$id/audio", "POST", json(target)))
        return (o.optJSONArray("generated")?.length() ?: 0) to (o.optJSONArray("skipped")?.length() ?: 0)
    }

    suspend fun uploadAudio(id: String, t: AudioTarget, bytes: ByteArray, mime: String) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("target", t.target)
            .apply {
                t.index?.let { addFormDataPart("index", it.toString()) }
                t.lang?.let { addFormDataPart("lang", it) }
            }
            .addFormDataPart("file", "audio", bytes.toRequestBody(mime.toMediaType()))
            .build()
        call("patterns/$id/audio", "PUT", form)
    }

    suspend fun deleteAudio(id: String, t: AudioTarget) {
        call("patterns/$id/audio", "DELETE", json(t.toJson()))
    }
}