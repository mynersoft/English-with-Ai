package com.example.data.repository

import com.example.core.network.NetworkClient
import com.example.core.network.sanitizeForBackendFailure
import com.example.domain.model.CallIceServerConfig
import com.example.domain.model.CallJoinResult
import com.example.domain.model.CallLobbySnapshot
import com.example.domain.model.CallMode
import com.example.domain.model.CallParticipantSummary
import com.example.domain.model.CallProviderConfig
import com.example.domain.model.CallProviderType
import com.example.domain.model.CallRoomPollResult
import com.example.domain.model.CallRoomSummary
import com.example.domain.model.CallSfuSessionConfig
import com.example.domain.model.CallSignalMessage
import com.example.domain.model.CallTransportSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class CallRepository {

    suspend fun fetchConfig(): Result<CallProviderConfig> = safeCall {
        val data = requestJson("GET", "/api/calls/config")
        parseProviderConfig(data.optJSONObject("config") ?: data)
    }

    suspend fun fetchLobby(): Result<CallLobbySnapshot> = safeCall {
        val data = requestJson("GET", "/api/calls/rooms")
        parseLobbySnapshot(data)
    }

    suspend fun fetchIncomingInvites(): Result<List<CallRoomSummary>> = safeCall {
        val data = requestJson("GET", "/api/calls/invites")
        val arr = data.optJSONArray("invites") ?: JSONArray()
        List(arr.length()) { i -> parseRoom(arr.getJSONObject(i)) }
    }

    suspend fun createDirectCall(
        targetUserId: String,
        topic: String = "1-on-1 English Video Call",
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ): Result<CallJoinResult> = safeCall {
        val payload = JSONObject()
            .put("mode", CallMode.DIRECT.apiValue)
            .put("targetUserId", targetUserId)
            .put("topic", topic.trim())
            .put("audioMuted", audioMuted)
            .put("videoEnabled", videoEnabled)
        val data = requestJson("POST", "/api/calls/rooms", payload)
        parseJoinResult(data)
    }

    suspend fun startRandomCall(
        excludeUserIds: List<String> = emptyList(),
        previousRoomId: String? = null,
        topic: String = "Random 1-on-1 English Practice",
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ): Result<CallJoinResult> = safeCall {
        val payload = JSONObject()
            .put("topic", topic.trim())
            .put("audioMuted", audioMuted)
            .put("videoEnabled", videoEnabled)
            .put("excludeUserIds", JSONArray(excludeUserIds.distinct()))
        if (!previousRoomId.isNullOrBlank()) {
            payload.put("previousRoomId", previousRoomId)
        }
        try {
            val data = requestJson("POST", "/api/calls/random", payload)
            parseJoinResult(data)
        } catch (err: BackendApiException) {
            if (err.httpStatus == 404 && err.code != "NO_FREE_PARTNER") {
                startRandomCallClientFallback(
                    excludeUserIds = excludeUserIds,
                    topic = topic,
                    audioMuted = audioMuted,
                    videoEnabled = videoEnabled
                )
            } else {
                throw err
            }
        }
    }

    private suspend fun startRandomCallClientFallback(
        excludeUserIds: List<String>,
        topic: String,
        audioMuted: Boolean,
        videoEnabled: Boolean
    ): CallJoinResult {
        val excluded = excludeUserIds.map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
        val lobbyData = requestJson("GET", "/api/calls/rooms")
        val lobby = parseLobbySnapshot(lobbyData)

        // If someone is already calling us in a random/direct invite not excluded, connect immediately
        val matchingInvite = lobby.incomingInvites.firstOrNull { invite ->
            invite.status == "ringing" &&
                invite.hostUserId.isNotBlank() &&
                !excluded.contains(invite.hostUserId) &&
                invite.effectiveRandom
        }
        if (matchingInvite != null) {
            val acceptPayload = JSONObject()
                .put("action", "accept")
                .put("audioMuted", audioMuted)
                .put("videoEnabled", videoEnabled)
            val acceptData = requestJson("POST", "/api/calls/rooms/${matchingInvite.id}/respond", acceptPayload)
            if (acceptData.has("transport") && !acceptData.isNull("transport")) {
                val res = parseJoinResult(acceptData)
                return res.copy(room = res.room.copy(isRandom = true))
            }
        }

        val busyIds = collectBusyUserIds(
            lobby.publicRooms + lobby.privateRooms + lobby.directCalls
        )
        excluded.addAll(busyIds)

        val studentsData = requestJson("GET", "/api/chat/students")
        val studentsArr = studentsData.optJSONArray("students") ?: JSONArray()
        val candidates = buildList {
            for (i in 0 until studentsArr.length()) {
                val obj = studentsArr.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                if (id.isNotEmpty() && !excluded.contains(id)) {
                    add(id)
                }
            }
        }
        if (candidates.isEmpty()) {
            throw BackendApiException(
                "No free students available right now. Everyone else is in another call or declined.",
                "NO_FREE_PARTNER",
                404
            )
        }
        val pickedId = candidates.random()
        val directPayload = JSONObject()
            .put("mode", CallMode.DIRECT.apiValue)
            .put("targetUserId", pickedId)
            .put("topic", topic.trim())
            .put("audioMuted", audioMuted)
            .put("videoEnabled", videoEnabled)
        val createdData = requestJson("POST", "/api/calls/rooms", directPayload)
        val joined = parseJoinResult(createdData)
        return joined.copy(room = joined.room.copy(isRandom = true))
    }

    suspend fun createGroupRoom(
        mode: CallMode,
        title: String,
        topic: String = "",
        learningLevel: String = "all",
        maxParticipants: Int? = null,
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ): Result<CallJoinResult> = safeCall {
        val payload = JSONObject()
            .put("mode", mode.apiValue)
            .put("title", title.trim())
            .put("topic", topic.trim())
            .put("learningLevel", learningLevel)
            .put("audioMuted", audioMuted)
            .put("videoEnabled", videoEnabled)
        if (maxParticipants != null) {
            payload.put("maxParticipants", maxParticipants)
        }
        val data = requestJson("POST", "/api/calls/rooms", payload)
        parseJoinResult(data)
    }

    suspend fun joinRoom(
        roomId: String,
        roomCode: String? = null,
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ): Result<CallJoinResult> = safeCall {
        val payload = JSONObject()
            .put("audioMuted", audioMuted)
            .put("videoEnabled", videoEnabled)
        if (!roomCode.isNullOrBlank()) {
            payload.put("roomCode", roomCode.trim())
        }
        val data = requestJson("POST", "/api/calls/rooms/$roomId/join", payload)
        parseJoinResult(data)
    }

    suspend fun joinPrivateByCode(
        roomCode: String,
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ): Result<CallJoinResult> = safeCall {
        val payload = JSONObject()
            .put("roomCode", roomCode.trim())
            .put("audioMuted", audioMuted)
            .put("videoEnabled", videoEnabled)
        val data = requestJson("POST", "/api/calls/rooms/join-by-code", payload)
        parseJoinResult(data)
    }

    suspend fun respondToCall(
        roomId: String,
        action: String,
        audioMuted: Boolean = false,
        videoEnabled: Boolean = true
    ): Result<CallJoinResult?> = safeCall {
        val payload = JSONObject()
            .put("action", action)
            .put("audioMuted", audioMuted)
            .put("videoEnabled", videoEnabled)
        val data = requestJson("POST", "/api/calls/rooms/$roomId/respond", payload)
        if (data.has("transport") && !data.isNull("transport")) {
            parseJoinResult(data)
        } else {
            null
        }
    }

    suspend fun leaveRoom(roomId: String, endRoom: Boolean = false): Result<Unit> = safeCall {
        val payload = JSONObject().put("endRoom", endRoom)
        requestJson("POST", "/api/calls/rooms/$roomId/leave", payload)
        Unit
    }

    suspend fun pollRoom(
        roomId: String,
        afterIso: String? = null,
        audioMuted: Boolean? = null,
        videoEnabled: Boolean? = null
    ): Result<CallRoomPollResult> = safeCall {
        val q = buildString {
            val parts = mutableListOf<String>()
            if (!afterIso.isNullOrBlank()) {
                parts += "after=${URLEncoder.encode(afterIso, "UTF-8")}"
            }
            if (audioMuted != null) {
                parts += "audioMuted=${if (audioMuted) "1" else "0"}"
            }
            if (videoEnabled != null) {
                parts += "videoEnabled=${if (videoEnabled) "1" else "0"}"
            }
            if (parts.isNotEmpty()) {
                append("?").append(parts.joinToString("&"))
            }
        }
        val data = requestJson("GET", "/api/calls/rooms/$roomId$q")
        val sigArr = data.optJSONArray("signals") ?: JSONArray()
        CallRoomPollResult(
            serverTime = data.optString("serverTime"),
            room = parseRoom(data.getJSONObject("room")),
            signals = List(sigArr.length()) { i -> parseSignal(sigArr.getJSONObject(i)) }
        )
    }

    suspend fun sendSignal(
        roomId: String,
        toUserId: String,
        type: String,
        payload: String
    ): Result<Unit> = safeCall {
        val body = JSONObject()
            .put("toUserId", toUserId)
            .put("type", type)
            .put("payload", payload)
        requestJson("POST", "/api/calls/rooms/$roomId/signal", body)
        Unit
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
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
        body: JSONObject? = null
    ): JSONObject = NetworkClient.executeAuthorizedJson(
        method = method,
        path = path,
        body = body,
        notFoundHint = "Video Call API not found on server (404). Deploy the latest backend to Vercel."
    )

    companion object {
        fun parseProviderConfig(o: JSONObject): CallProviderConfig {
            return CallProviderConfig(
                provider = CallProviderType.fromApi(o.optString("provider")),
                providerLabel = o.optString("providerLabel", "Free WebRTC P2P ($0)"),
                costTier = o.optString("costTier", "free"),
                transportMode = o.optString("transportMode", "p2p_mesh"),
                signalingMode = o.optString("signalingMode", "http_poll"),
                maxGroupParticipants = o.optInt("maxGroupParticipants", 6),
                hasTurnRelay = o.optBoolean("hasTurnRelay", false),
                upgradeReady = o.optBoolean("upgradeReady", true),
                fallbackReason = o.optString("fallbackReason").takeIf { it.isNotBlank() && it != "null" }
            )
        }

        fun parseLobbySnapshot(data: JSONObject): CallLobbySnapshot {
            val configObj = data.optJSONObject("config") ?: JSONObject()
            val pubArr = data.optJSONArray("publicRooms") ?: JSONArray()
            val privArr = data.optJSONArray("privateRooms") ?: JSONArray()
            val directArr = data.optJSONArray("directCalls") ?: JSONArray()
            val inviteArr = data.optJSONArray("incomingInvites") ?: JSONArray()
            return CallLobbySnapshot(
                config = parseProviderConfig(configObj),
                publicRooms = List(pubArr.length()) { i -> parseRoom(pubArr.getJSONObject(i)) },
                privateRooms = List(privArr.length()) { i -> parseRoom(privArr.getJSONObject(i)) },
                directCalls = List(directArr.length()) { i -> parseRoom(directArr.getJSONObject(i)) },
                incomingInvites = List(inviteArr.length()) { i -> parseRoom(inviteArr.getJSONObject(i)) }
            )
        }

        fun parseJoinResult(data: JSONObject): CallJoinResult {
            val room = parseRoom(data.getJSONObject("room"))
            val transport = parseTransportSession(data.getJSONObject("transport"))
            return CallJoinResult(room = room, transport = transport)
        }

        fun parseTransportSession(o: JSONObject): CallTransportSession {
            val iceArr = o.optJSONArray("iceServers") ?: JSONArray()
            val iceServers = List(iceArr.length()) { i ->
                val item = iceArr.getJSONObject(i)
                val urlsArr = item.optJSONArray("urls")
                val urls = if (urlsArr != null) {
                    List(urlsArr.length()) { j -> urlsArr.optString(j) }.filter { it.isNotBlank() }
                } else {
                    listOfNotNull(item.optString("urls").takeIf { it.isNotBlank() })
                }
                CallIceServerConfig(
                    urls = urls,
                    username = item.optString("username").takeIf { it.isNotBlank() && it != "null" },
                    credential = item.optString("credential").takeIf { it.isNotBlank() && it != "null" }
                )
            }.filter { it.urls.isNotEmpty() }

            val sfuObj = o.optJSONObject("sfu")
            val sfu = if (sfuObj != null) {
                CallSfuSessionConfig(
                    provider = CallProviderType.fromApi(sfuObj.optString("provider")),
                    serverUrl = sfuObj.optString("serverUrl").takeIf { it.isNotBlank() && it != "null" },
                    roomName = sfuObj.optString("roomName"),
                    token = sfuObj.optString("token").takeIf { it.isNotBlank() && it != "null" },
                    appId = sfuObj.optString("appId").takeIf { it.isNotBlank() && it != "null" }
                )
            } else null

            return CallTransportSession(
                provider = CallProviderType.fromApi(o.optString("provider")),
                providerLabel = o.optString("providerLabel", "Free WebRTC P2P ($0)"),
                costTier = o.optString("costTier", "free"),
                transportMode = o.optString("transportMode", "p2p_mesh"),
                signalingMode = o.optString("signalingMode", "http_poll"),
                maxGroupParticipants = o.optInt("maxGroupParticipants", 6),
                hasTurnRelay = o.optBoolean("hasTurnRelay", false),
                iceServers = iceServers,
                sfu = sfu
            )
        }

        fun parseRoom(o: JSONObject): CallRoomSummary {
            val partsArr = o.optJSONArray("participants") ?: JSONArray()
            val participants = List(partsArr.length()) { i ->
                val p = partsArr.getJSONObject(i)
                CallParticipantSummary(
                    userId = p.optString("userId"),
                    name = p.optString("name", "Learner"),
                    badge = p.optString("badge", "Student"),
                    audioMuted = p.optBoolean("audioMuted", false),
                    videoEnabled = p.optBoolean("videoEnabled", true),
                    isSelf = p.optBoolean("isSelf", false)
                )
            }
            val mode = CallMode.fromApi(o.optString("mode"))
            val topic = o.optString("topic")
            return CallRoomSummary(
                id = o.optString("id"),
                mode = mode,
                visibility = o.optString("visibility", "public"),
                title = o.optString("title", "English Video Room"),
                topic = topic,
                learningLevel = o.optString("learningLevel", "all"),
                hostUserId = o.optString("hostUserId"),
                hostName = o.optString("hostName", "Learner"),
                targetUserId = o.optString("targetUserId").takeIf { it.isNotBlank() && it != "null" },
                targetName = o.optString("targetName"),
                roomCode = o.optString("roomCode"),
                status = o.optString("status", "active"),
                endedReason = o.optString("endedReason"),
                isRandom = o.optBoolean("isRandom", mode == CallMode.RANDOM || topic.contains("Random", ignoreCase = true)),
                provider = CallProviderType.fromApi(o.optString("provider")),
                maxParticipants = o.optInt("maxParticipants", 6),
                participantCount = o.optInt("participantCount", participants.size),
                participants = participants,
                isHost = o.optBoolean("isHost", false),
                isJoined = o.optBoolean("isJoined", false),
                startedAt = o.optString("startedAt").takeIf { it.isNotBlank() && it != "null" },
                createdAt = o.optString("createdAt"),
                updatedAt = o.optString("updatedAt")
            )
        }

        fun collectBusyUserIds(rooms: List<CallRoomSummary>): Set<String> {
            val busy = mutableSetOf<String>()
            for (room in rooms) {
                if (room.status != "ringing" && room.status != "active") continue
                for (p in room.participants) {
                    if (p.userId.isNotBlank()) busy.add(p.userId)
                }
                if (room.status == "ringing" || room.participants.isNotEmpty()) {
                    if (room.hostUserId.isNotBlank()) busy.add(room.hostUserId)
                }
                val targetId = room.targetUserId
                if (!targetId.isNullOrBlank() && (room.status == "ringing" || room.mode.isOneOnOne)) {
                    busy.add(targetId)
                }
            }
            return busy
        }

        fun parseSignal(o: JSONObject): CallSignalMessage {
            return CallSignalMessage(
                id = o.optString("id"),
                roomId = o.optString("roomId"),
                fromUserId = o.optString("fromUserId"),
                toUserId = o.optString("toUserId"),
                type = o.optString("type"),
                payload = o.optString("payload"),
                createdAt = o.optString("createdAt")
            )
        }
    }
}
