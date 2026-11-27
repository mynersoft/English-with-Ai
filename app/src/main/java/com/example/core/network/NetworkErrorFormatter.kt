package com.example.core.network

import com.example.data.repository.BackendApiException
import org.json.JSONException
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

private const val MSG_CONNECTION_ERROR =
    "Unable to connect to the server. Please check your internet connection and try again."
private const val MSG_TIMEOUT_ERROR =
    "The server is taking too long to respond. Please check your connection and try again."
private const val MSG_SSL_ERROR =
    "Could not establish a secure connection. Please check your network and try again."
private const val MSG_SERVER_UNAVAILABLE =
    "Our servers are temporarily unavailable. Please try again in a few moments."
private const val MSG_SESSION_EXPIRED =
    "Your session has expired. Please sign in again."
private const val MSG_PERMISSION_DENIED =
    "You do not have permission to perform this action."
private const val MSG_RATE_LIMITED =
    "Too many requests right now. Please wait a moment and try again."
private const val MSG_NOT_FOUND =
    "The requested content is currently unavailable. Please refresh and try again."
private const val MSG_BAD_RESPONSE =
    "Received an unexpected response from the server. Please try again."

private val TECHNICAL_ERROR_REGEX = Regex(
    pattern = """(\b\d{1,3}(?:\.\d{1,3}){3}\b|\(port\s+\d+\)|after\s+\d+ms|failed to connect|unable to resolve host|econnrefused|connection reset|broken pipe|software caused connection abort|network is unreachable|timed?\s*out|ssl|certpath|mongodb|firebaseadmin|vercel|<html|server error\s*\(|request failed\s*\(|backend url is not configured|java\.[a-z.]+exception|okhttp|org\.json)""",
    option = RegexOption.IGNORE_CASE
)

fun Throwable.toUserFriendlyBackendMessage(fallback: String = MSG_CONNECTION_ERROR): String {
    val chain = generateSequence(this) { it.cause }.toList()

    if (chain.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException }) {
        return MSG_CONNECTION_ERROR
    }
    if (chain.any { it is SocketTimeoutException || it is InterruptedIOException }) {
        return MSG_TIMEOUT_ERROR
    }
    if (chain.any { it is EOFException }) {
        return MSG_CONNECTION_ERROR
    }
    if (chain.any { it is SSLException }) {
        return MSG_SSL_ERROR
    }
    if (chain.any { it is JSONException }) {
        return MSG_BAD_RESPONSE
    }

    val apiError = chain.filterIsInstance<BackendApiException>().firstOrNull()
    if (apiError != null) {
        val rawMsg = apiError.message.orEmpty().trim()
        val isTechnical = rawMsg.isBlank() || TECHNICAL_ERROR_REGEX.containsMatchIn(rawMsg)
        return when (apiError.httpStatus) {
            400, 409, 422 -> if (!isTechnical) rawMsg else fallback
            401 -> MSG_SESSION_EXPIRED
            403 -> if (!isTechnical) rawMsg else MSG_PERMISSION_DENIED
            404 -> if (!isTechnical && !rawMsg.contains("404")) rawMsg else MSG_NOT_FOUND
            408 -> MSG_TIMEOUT_ERROR
            429 -> MSG_RATE_LIMITED
            in 500..599 -> MSG_SERVER_UNAVAILABLE
            else -> if (!isTechnical) rawMsg else fallback
        }
    }

    val rawMessage = chain.firstNotNullOfOrNull { it.message?.trim()?.takeIf { m -> m.isNotEmpty() } }.orEmpty()
    if (rawMessage.isBlank()) return fallback

    val lower = rawMessage.lowercase()
    return when {
        lower.contains("timeout") || lower.contains("timed out") -> MSG_TIMEOUT_ERROR
        lower.contains("ssl") || lower.contains("cert") -> MSG_SSL_ERROR
        lower.contains("vercel") || lower.contains("mongodb") || lower.contains("<html") ||
            lower.contains("server error") || lower.contains("firebaseadmin") -> MSG_SERVER_UNAVAILABLE
        TECHNICAL_ERROR_REGEX.containsMatchIn(rawMessage) -> MSG_CONNECTION_ERROR
        else -> rawMessage
    }
}

fun Throwable.sanitizeForBackendFailure(fallback: String = MSG_CONNECTION_ERROR): Exception {
    val friendly = toUserFriendlyBackendMessage(fallback)
    val apiError = generateSequence(this) { it.cause }
        .filterIsInstance<BackendApiException>()
        .firstOrNull()
    return if (apiError != null) {
        BackendApiException(friendly, apiError.code, apiError.httpStatus)
    } else {
        IOException(friendly, this)
    }
}
