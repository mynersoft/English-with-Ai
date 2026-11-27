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
            val correction = if (c != null && c.optBoolean("hasMistake") && c.optString("corrected").isNotBlank()) {
                Correction(
                    original = c.optString("original", message),
                    corrected = c.optString("corrected"),
                    explanationBn = c.optString("explanationBn")
                )
            } else null
            Result.success(SpeakingReply(data.getString("reply"), correction))
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
