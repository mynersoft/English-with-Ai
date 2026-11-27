package com.example.core.network

import com.example.BuildConfig
import com.example.data.repository.BackendApiException
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP client, tuned dispatcher/connection pool, and resilient request executors.
 * Reuses warm TCP/TLS/HTTP2 sockets across all repositories and recovers automatically from
 * expired Firebase tokens (single 401 force-refresh retry) and transient GET hiccups.
 */
object NetworkClient {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val TRANSIENT_STATUS_CODES = setOf(408, 502, 503, 504)

    private val sharedDispatcher: Dispatcher by lazy {
        Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
    }

    private val sharedPool: ConnectionPool by lazy {
        ConnectionPool(10, 5, TimeUnit.MINUTES)
    }

    val standard: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(sharedDispatcher)
            .connectionPool(sharedPool)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val speaking: OkHttpClient by lazy {
        standard.newBuilder()
            .readTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    val admin: OkHttpClient by lazy {
        standard.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS) // batch TTS generation
            .build()
    }

    suspend fun executeAuthorizedJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        client: OkHttpClient = standard,
        notFoundHint: String? = null
    ): JSONObject {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            throw BackendApiException("Backend URL is not configured.", "CONFIG_MISSING", 500)
        }
        val user = Firebase.auth.currentUser
            ?: throw BackendApiException("Please sign in to continue.", "UNAUTHORIZED", 401)

        var token = user.getIdToken(false).await().token
            ?: throw BackendApiException("Could not verify authentication token.", "UNAUTHORIZED", 401)

        val upperMethod = method.uppercase()
        val reqBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
            ?: if (upperMethod == "POST" || upperMethod == "PUT" || upperMethod == "PATCH") {
                "{}".toRequestBody(JSON_MEDIA_TYPE)
            } else null

        var refreshedToken = false
        var attempt = 0
        val maxAttempts = if (upperMethod == "GET") 2 else 1

        while (true) {
            attempt += 1
            val req = Request.Builder()
                .url("$baseUrl$path")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .method(upperMethod, reqBody)
                .build()

            try {
                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()

                    if (res.code == 401 && !refreshedToken) {
                        refreshedToken = true
                        val fresh = runCatching { user.getIdToken(true).await().token }.getOrNull()
                        if (!fresh.isNullOrBlank()) {
                            token = fresh
                            attempt -= 1 // do not consume GET transient retry budget for token refresh
                            return@use
                        }
                    }

                    if (upperMethod == "GET" && res.code in TRANSIENT_STATUS_CODES && attempt < maxAttempts) {
                        delay(180L)
                        return@use
                    }

                    val trimmed = raw.trim()
                    val json = if (trimmed.isEmpty()) JSONObject() else runCatching { JSONObject(trimmed) }.getOrNull()

                    if (!res.isSuccessful || json?.optBoolean("success", true) == false) {
                        val errObj = json?.optJSONObject("error")
                        val code = errObj?.optString("code")?.takeIf { it.isNotBlank() }
                        val message = errObj?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                            ?: when {
                                res.code == 404 && trimmed.startsWith("<") && !notFoundHint.isNullOrBlank() ->
                                    notFoundHint
                                trimmed.startsWith("<") -> "Server error (${res.code})"
                                else -> "Request failed (${res.code})"
                            }
                        throw BackendApiException(message, code, res.code)
                    }

                    if (json == null) {
                        throw BackendApiException(
                            "Received an unexpected response from the server.",
                            "INVALID_RESPONSE",
                            res.code
                        )
                    }

                    return json.optJSONObject("data") ?: json
                }
            } catch (e: IOException) {
                if (e is BackendApiException) throw e
                val isTransientIo = e is EOFException || e is SocketTimeoutException ||
                    e.message.orEmpty().contains("reset", ignoreCase = true)
                if (upperMethod == "GET" && isTransientIo && attempt < maxAttempts) {
                    delay(180L)
                    continue
                }
                throw e
            }
        }
    }

    suspend fun executePublicGetText(
        path: String,
        client: OkHttpClient = standard
    ): String {
        val baseUrl = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
        if (baseUrl.isBlank()) {
            throw BackendApiException("Backend URL is not configured.", "CONFIG_MISSING", 500)
        }
        val req = Request.Builder()
            .url("$baseUrl$path")
            .get()
            .header("Accept", "application/json")
            .build()

        var attempt = 0
        while (true) {
            attempt += 1
            try {
                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (res.code in TRANSIENT_STATUS_CODES && attempt < 2) {
                        delay(180L)
                        return@use
                    }
                    if (!res.isSuccessful) {
                        val parsed = runCatching { JSONObject(raw) }.getOrNull()
                        val code = parsed?.optJSONObject("error")?.optString("code")?.takeIf { it.isNotBlank() }
                            ?: parsed?.optString("code")?.takeIf { it.isNotBlank() }
                        val msg = parsed?.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                            ?: parsed?.optString("error")?.takeIf { it.isNotBlank() }
                            ?: if (raw.trimStart().startsWith("<")) "Server error (${res.code})" else raw.take(160)
                        throw BackendApiException(
                            msg.ifBlank { "Request failed (${res.code})" },
                            code,
                            res.code
                        )
                    }
                    return raw
                }
            } catch (e: IOException) {
                if (e is BackendApiException) throw e
                val isTransientIo = e is EOFException || e is SocketTimeoutException ||
                    e.message.orEmpty().contains("reset", ignoreCase = true)
                if (isTransientIo && attempt < 2) {
                    delay(180L)
                    continue
                }
                throw e
            }
        }
    }
}
