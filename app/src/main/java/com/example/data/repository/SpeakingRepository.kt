package com.example.data.repository

import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.example.core.network.toUserFriendlyBackendMessage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ChatTurn(val role: String, val content: String) // role: "user" | "assistant"

data class Correction(
    val original: String,
    val corrected: String,
    val explanationBn: String
)

data class SpeakingReply(val reply: String, val correction: Correction?)

class SpeakingException(message: String, val code: String? = null) : Exception(message)

/** Talks to POST /api/v1/ai/speaking/turn on your backend (never to the AI provider directly). */
class SpeakingRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    suspend fun sendTurn(
        message: String,
        level: String,
        topic: String,
        history: List<ChatTurn>
    ): Result<SpeakingReply> = withContext(Dispatchers.IO) {
        try {
            var token = idToken()
            val body = JSONObject()
                .put("message", message)
                .put("level", level.lowercase())
                .put("topic", topic)
                .put("history", JSONArray().also { arr ->
                    history.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
                })

            var response = post("/api/v1/ai/speaking/turn", token, body)

            if (response.status == 401) {
                val freshToken = runCatching { idToken(forceRefresh = true) }.getOrNull()
                if (!freshToken.isNullOrBlank()) {
                    token = freshToken
                    response = post("/api/v1/ai/speaking/turn", token, body)
                }
            }

            if (response.status == 404 && response.errorCode == "USER_NOT_FOUND") {
                post("/api/v1/auth/sync", token, null)
                response = post("/api/v1/ai/speaking/turn", token, body)
            }

            if (!response.ok) throw SpeakingException(response.errorMessage, response.errorCode)

            val data = response.json.getJSONObject("data")
            val c = data.optJSONObject("correction")
            val serverCorrection = if (c != null && c.optBoolean("hasMistake") && c.optString("corrected").isNotBlank()) {
                Correction(
                    original = c.optString("original", message),
                    corrected = c.optString("corrected"),
                    explanationBn = c.optString("explanationBn")
                )
            } else null
            Result.success(
                SpeakingReplyParser.parseReplyAndCorrection(
                    rawReply = data.optString("reply"),
                    serverCorrection = serverCorrection,
                    originalMessage = message
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SpeakingException) {
            Result.failure(SpeakingException(e.toUserFriendlyBackendMessage("Unable to reach the AI tutor right now. Please try again."), e.code))
        } catch (e: IOException) {
            Result.failure(SpeakingException(e.toUserFriendlyBackendMessage("Unable to connect to the server. Please check your internet connection and try again.")))
        } catch (e: Exception) {
            Result.failure(SpeakingException(e.toUserFriendlyBackendMessage("Something went wrong. Please try again.")))
        }
    }

    private class HttpResult(val status: Int, val json: JSONObject) {
        val ok: Boolean get() = status in 200..299 && json.optBoolean("success", false)
        val errorCode: String? get() = json.optJSONObject("error")?.optString("code")
        val errorMessage: String
            get() = json.optJSONObject("error")?.optString("message").takeUnless { it.isNullOrBlank() }
                ?: "Server error ($status). Please try again."
    }

    private fun post(path: String, token: String, body: JSONObject?): HttpResult {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            throw SpeakingException("Backend URL is not configured yet.")
        }
        val payload = (body ?: JSONObject()).toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(payload)
            .build()

        return NetworkClient.speaking.newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
            HttpResult(res.code, json)
        }
    }

    private suspend fun idToken(forceRefresh: Boolean = false): String = suspendCancellableCoroutine { cont ->
        val user = auth.currentUser
        if (user == null) {
            cont.resumeWithException(SpeakingException("Please log in again."))
            return@suspendCancellableCoroutine
        }
        user.getIdToken(forceRefresh).addOnCompleteListener { task ->
            val result = task.result
            if (task.isSuccessful && result?.token != null) {
                cont.resume(result.token!!)
            } else {
                cont.resumeWithException(SpeakingException("Could not verify your login. Please log in again."))
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal object SpeakingReplyParser {
    private val UNICODE_ESCAPE_REGEX = Regex("""\\u([0-9a-fA-F]{4})""")
    private val TRAILING_BROKEN_ESCAPE_REGEX = Regex("""(\\u[0-9a-fA-F]{0,3}|\\)$""")
    private val FENCE_REGEX = Regex("""```(?:json)?""", RegexOption.IGNORE_CASE)

    fun decodeEscapes(raw: String): String {
        var out = raw.replace(TRAILING_BROKEN_ESCAPE_REGEX, "")
        out = UNICODE_ESCAPE_REGEX.replace(out) { match ->
            val code = match.groupValues[1].toInt(16)
            code.toChar().toString()
        }
        out = out
            .replace("\\n", "\n")
            .replace("\\r", "")
            .replace("\\t", " ")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        return out.trim()
    }

    private fun extractStringField(jsonText: String, key: String): String {
        val keyRegex = Regex("""\"${Regex.escape(key)}\"\s*:\s*\"""")
        val match = keyRegex.find(jsonText) ?: return ""
        val sb = StringBuilder()
        var i = match.range.last + 1
        var escaped = false
        while (i < jsonText.length) {
            val ch = jsonText[i]
            if (escaped) {
                sb.append('\\').append(ch)
                escaped = false
                i++
                continue
            }
            if (ch == '\\') {
                escaped = true
                i++
                continue
            }
            if (ch == '"') {
                break
            }
            sb.append(ch)
            i++
        }
        return decodeEscapes(sb.toString())
    }

    private fun extractBooleanField(jsonText: String, key: String): Boolean? {
        val boolRegex = Regex("""\"${Regex.escape(key)}\"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
        val match = boolRegex.find(jsonText) ?: return null
        return match.groupValues[1].equals("true", ignoreCase = true)
    }

    fun parseReplyAndCorrection(
        rawReply: String,
        serverCorrection: Correction?,
        originalMessage: String
    ): SpeakingReply {
        val cleaned = rawReply.replace(FENCE_REGEX, "").trim()
        val looksLikeRawJson =
            cleaned.startsWith("{") && (cleaned.contains("\"reply\"") || cleaned.contains("\"correction\""))

        val cleanServerCorrection = serverCorrection?.let { sc ->
            val cleanCorrected = decodeEscapes(sc.corrected)
            if (cleanCorrected.isBlank() || cleanCorrected.equals(originalMessage.trim(), ignoreCase = true)) {
                null
            } else {
                Correction(
                    original = sc.original.ifBlank { originalMessage },
                    corrected = cleanCorrected,
                    explanationBn = decodeEscapes(sc.explanationBn)
                )
            }
        }

        if (!looksLikeRawJson) {
            val safeReply = decodeEscapes(cleaned)
                .ifBlank { "Could you say that again? Let's keep practicing!" }
            return SpeakingReply(reply = safeReply, correction = cleanServerCorrection)
        }

        val extractedReply = extractStringField(cleaned, "reply")
            .ifBlank { "Could you say that again? Let's keep practicing!" }

        val recoveredCorrection = if (cleanServerCorrection != null) {
            cleanServerCorrection
        } else {
            val hasMistake = extractBooleanField(cleaned, "hasMistake") == true
            val corrected = extractStringField(cleaned, "corrected")
            val original = extractStringField(cleaned, "original").ifBlank { originalMessage }
            val explanationBn = extractStringField(cleaned, "explanationBn")
            if (hasMistake && corrected.isNotBlank() && !corrected.equals(originalMessage.trim(), ignoreCase = true)) {
                Correction(
                    original = original,
                    corrected = corrected,
                    explanationBn = explanationBn
                )
            } else {
                null
            }
        }

        return SpeakingReply(reply = extractedReply, correction = recoveredCorrection)
    }
}
