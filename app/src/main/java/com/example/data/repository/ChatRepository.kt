package com.example.data.repository

import com.example.BuildConfig
import com.example.core.network.NetworkClient
import com.example.core.network.sanitizeForBackendFailure
import com.example.domain.model.ChatAiCorrection
import com.example.domain.model.ChatCorrectionToken
import com.example.domain.model.ChatIncomingNotification
import com.example.domain.model.ChatMemberSummary
import com.example.domain.model.ChatMessageItem
import com.example.domain.model.ChatNotificationsBatch
import com.example.domain.model.ChatReplyQuote
import com.example.domain.model.ChatRoomSummary
import com.example.domain.model.ChatRoomsSnapshot
import com.example.domain.model.ChatStudentSummary
import com.example.domain.model.ChatThreadSnapshot
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class ChatRepository {

    suspend fun fetchRooms(): Result<ChatRoomsSnapshot> = safeCall {
        val data = requestJson("GET", "/api/chat/rooms")
        val groupArr = data.optJSONArray("groupRooms") ?: JSONArray()
        val directArr = data.optJSONArray("directRooms") ?: JSONArray()
        val startersArr = data.optJSONArray("starterChips") ?: JSONArray()
        ChatRoomsSnapshot(
            aiConfigured = data.optBoolean("aiConfigured", false),
            groupRooms = List(groupArr.length()) { i -> parseRoom(groupArr.getJSONObject(i)) },
            directRooms = List(directArr.length()) { i -> parseRoom(directArr.getJSONObject(i)) },
            starterChips = List(startersArr.length()) { i -> startersArr.optString(i) }
                .filter { it.isNotBlank() }
        )
    }

    suspend fun createGroupRoom(
        name: String,
        description: String,
        avatarEmoji: String
    ): Result<ChatRoomSummary> = safeCall {
        val payload = JSONObject()
            .put("roomType", "group")
            .put("name", name.trim())
            .put("description", description.trim())
            .put("avatarEmoji", avatarEmoji.ifBlank { "🦁" })
        val data = requestJson("POST", "/api/chat/rooms", payload)
        parseRoom(data.getJSONObject("room"))
    }

    suspend fun startDirectChat(peerUserId: String): Result<ChatRoomSummary> = safeCall {
        val payload = JSONObject()
            .put("roomType", "direct")
            .put("peerUserId", peerUserId)
        val data = requestJson("POST", "/api/chat/rooms", payload)
        parseRoom(data.getJSONObject("room"))
    }

    suspend fun searchStudents(search: String = ""): Result<List<ChatStudentSummary>> = safeCall {
        val q = if (search.isNotBlank()) "?search=${URLEncoder.encode(search.trim(), "UTF-8")}" else ""
        val data = requestJson("GET", "/api/chat/students$q")
        val arr = data.optJSONArray("students") ?: JSONArray()
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ChatStudentSummary(
                id = o.optString("id"),
                name = o.optString("name", "Student"),
                badge = o.optString("badge", "Student"),
                learningLevel = o.optString("learningLevel", "beginner")
            )
        }
    }

    suspend fun toggleMembership(roomId: String, join: Boolean): Result<Boolean> = safeCall {
        val payload = JSONObject().put("action", if (join) "join" else "leave")
        val data = requestJson("POST", "/api/chat/rooms/$roomId/join", payload)
        data.optBoolean("joined", join)
    }

    suspend fun fetchMessages(
        roomId: String,
        afterIso: String? = null
    ): Result<ChatThreadSnapshot> = safeCall {
        val q = buildString {
            append("?limit=50")
            if (!afterIso.isNullOrBlank()) {
                append("&after=").append(URLEncoder.encode(afterIso, "UTF-8"))
            }
        }
        val data = requestJson("GET", "/api/chat/rooms/$roomId/messages$q")
        val membersArr = data.optJSONArray("members") ?: JSONArray()
        val msgArr = data.optJSONArray("messages") ?: JSONArray()
        ChatThreadSnapshot(
            aiConfigured = data.optBoolean("aiConfigured", false),
            room = parseRoom(data.getJSONObject("room")),
            members = List(membersArr.length()) { i ->
                val m = membersArr.getJSONObject(i)
                ChatMemberSummary(
                    id = m.optString("id"),
                    name = m.optString("name", "Student"),
                    badge = m.optString("badge", "Student")
                )
            },
            messages = List(msgArr.length()) { i -> parseMessage(msgArr.getJSONObject(i)) }
        )
    }

    suspend fun sendMessage(
        roomId: String,
        text: String,
        replyToMessageId: String? = null,
        autoCorrect: Boolean = true
    ): Result<ChatMessageItem> = safeCall(NetworkClient.speaking) {
        val payload = JSONObject()
            .put("text", text.trim())
            .put("autoCorrect", autoCorrect)
        if (!replyToMessageId.isNullOrBlank()) {
            payload.put("replyToMessageId", replyToMessageId)
        }
        val data = requestJson("POST", "/api/chat/rooms/$roomId/messages", payload, NetworkClient.speaking)
        parseMessage(data.getJSONObject("message"))
    }

    suspend fun correctMessage(messageId: String): Result<ChatMessageItem> =
        safeCall(NetworkClient.speaking) {
            val data = requestJson("POST", "/api/chat/messages/$messageId/correct", JSONObject(), NetworkClient.speaking)
            parseMessage(data.getJSONObject("message"))
        }

    suspend fun extractHandwritingText(
        imageBase64: String,
        mimeType: String = "image/jpeg"
    ): Result<String> = safeCall(NetworkClient.speaking) {
        val payload = JSONObject()
            .put("imageBase64", imageBase64)
            .put("mimeType", mimeType)
        val data = requestJson("POST", "/api/chat/ocr", payload, NetworkClient.speaking)
        val text = data.optString("text").trim()
        if (text.isEmpty()) {
            throw BackendApiException("No readable handwriting text found.", "NO_TEXT_FOUND", 422)
        }
        text
    }

    suspend fun fetchIncomingNotifications(
        afterIso: String? = null,
        limit: Int = 25
    ): Result<ChatNotificationsBatch> = safeCall {
        val q = buildString {
            append("?limit=").append(limit.coerceIn(1, 50))
            if (!afterIso.isNullOrBlank()) {
                append("&after=").append(URLEncoder.encode(afterIso, "UTF-8"))
            }
        }
        val data = requestJson("GET", "/api/chat/notifications$q")
        val arr = data.optJSONArray("notifications") ?: JSONArray()
        val list = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ChatIncomingNotification(
                messageId = o.optString("messageId"),
                roomId = o.optString("roomId"),
                roomName = o.optString("roomName", "Study Room"),
                roomType = o.optString("roomType", "group"),
                roomEmoji = o.optString("roomEmoji", "🦁").ifBlank { "🦁" },
                senderId = o.optString("senderId"),
                senderName = o.optString("senderName", "Learner"),
                senderBadge = o.optString("senderBadge"),
                textPreview = o.optString("textPreview"),
                createdAt = o.optString("createdAt")
            )
        }.filter { it.messageId.isNotBlank() && it.roomId.isNotBlank() }

        ChatNotificationsBatch(
            serverTime = data.optString("serverTime"),
            notifications = list
        )
    }

    suspend fun registerPushToken(token: String): Result<Unit> = safeCall {
        val trimmed = token.trim()
        if (trimmed.isBlank()) return@safeCall Unit
        val payload = JSONObject().put("token", trimmed)
        requestJson("POST", "/api/chat/push-token", payload)
        Unit
    }

    suspend fun unregisterPushToken(token: String): Result<Unit> = safeCall {
        val trimmed = token.trim()
        if (trimmed.isBlank()) return@safeCall Unit
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        requestJson("DELETE", "/api/chat/push-token?token=$encoded")
        Unit
    }

    // ---------- Helpers ----------

    private suspend fun <T> safeCall(
        client: OkHttpClient = NetworkClient.standard,
        block: suspend () -> T
    ): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.sanitizeForBackendFailure())
        }
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JSONObject? = null,
        client: OkHttpClient = NetworkClient.standard
    ): JSONObject = NetworkClient.executeAuthorizedJson(
        method = method,
        path = path,
        body = body,
        client = client,
        notFoundHint = "Chat API not found on server (404). Deploy the latest backend to Vercel."
    )

    private fun parseRoom(o: JSONObject): ChatRoomSummary {
        return ChatRoomSummary(
            id = o.optString("id"),
            name = o.optString("name", "Study Room"),
            description = o.optString("description"),
            avatarEmoji = o.optString("avatarEmoji", "🦁").ifBlank { "🦁" },
            roomType = o.optString("roomType", "group"),
            memberCount = o.optInt("memberCount", 0),
            maxMembers = o.optInt("maxMembers", 50),
            onlineCount = o.optInt("onlineCount", 0),
            isMember = o.optBoolean("isMember", false),
            lastMessageText = o.optString("lastMessageText"),
            lastMessageAt = o.optString("lastMessageAt").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun parseMessage(o: JSONObject): ChatMessageItem {
        val replyObj = o.optJSONObject("replyTo")
        val reply = if (replyObj != null && !replyObj.optString("messageId").isNullOrBlank()) {
            ChatReplyQuote(
                messageId = replyObj.optString("messageId"),
                senderName = replyObj.optString("senderName", "Learner"),
                textPreview = replyObj.optString("textPreview")
            )
        } else null

        val aiObj = o.optJSONObject("aiCorrection")
        val tokensArr = aiObj?.optJSONArray("tokens") ?: JSONArray()
        val ai = if (aiObj != null) {
            ChatAiCorrection(
                status = aiObj.optString("status", "none"),
                hasMistake = aiObj.optBoolean("hasMistake", false),
                correctedText = aiObj.optString("correctedText"),
                tokens = List(tokensArr.length()) { i ->
                    val t = tokensArr.getJSONObject(i)
                    ChatCorrectionToken(
                        text = t.optString("text"),
                        changed = t.optBoolean("changed", false)
                    )
                },
                explanationBn = aiObj.optString("explanationBn")
            )
        } else ChatAiCorrection()

        return ChatMessageItem(
            id = o.optString("id"),
            roomId = o.optString("roomId"),
            senderId = o.optString("senderId"),
            senderName = o.optString("senderName", "Learner"),
            senderBadge = o.optString("senderBadge"),
            isMine = o.optBoolean("isMine", false),
            text = o.optString("text"),
            replyTo = reply,
            aiCorrection = ai,
            createdAt = o.optString("createdAt")
        )
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
