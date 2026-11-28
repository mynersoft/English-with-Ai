package com.example.domain.model

enum class CallMode(val apiValue: String, val label: String) {
    DIRECT("direct", "1-on-1 Call"),
    RANDOM("random", "Random Call"),
    GROUP_PUBLIC("group_public", "Public Group"),
    GROUP_PRIVATE("group_private", "Private Group");

    val isOneOnOne: Boolean
        get() = this == DIRECT || this == RANDOM

    companion object {
        fun fromApi(raw: String?): CallMode =
            entries.firstOrNull { it.apiValue.equals(raw?.trim(), ignoreCase = true) } ?: GROUP_PUBLIC
    }
}

enum class CallProviderType(val apiValue: String) {
    FREE_P2P("free_p2p"),
    LIVEKIT("livekit"),
    AGORA("agora"),
    DAILY("daily");

    companion object {
        fun fromApi(raw: String?): CallProviderType =
            entries.firstOrNull { it.apiValue.equals(raw?.trim(), ignoreCase = true) } ?: FREE_P2P
    }
}

data class CallProviderConfig(
    val provider: CallProviderType = CallProviderType.FREE_P2P,
    val providerLabel: String = "Free WebRTC P2P ($0)",
    val costTier: String = "free",
    val transportMode: String = "p2p_mesh",
    val signalingMode: String = "http_poll",
    val maxGroupParticipants: Int = 6,
    val hasTurnRelay: Boolean = false,
    val upgradeReady: Boolean = true,
    val fallbackReason: String? = null
)

data class CallIceServerConfig(
    val urls: List<String> = emptyList(),
    val username: String? = null,
    val credential: String? = null
)

data class CallSfuSessionConfig(
    val provider: CallProviderType,
    val serverUrl: String? = null,
    val roomName: String = "",
    val token: String? = null,
    val appId: String? = null
)

data class CallTransportSession(
    val provider: CallProviderType = CallProviderType.FREE_P2P,
    val providerLabel: String = "Free WebRTC P2P ($0)",
    val costTier: String = "free",
    val transportMode: String = "p2p_mesh",
    val signalingMode: String = "http_poll",
    val maxGroupParticipants: Int = 6,
    val hasTurnRelay: Boolean = false,
    val iceServers: List<CallIceServerConfig> = emptyList(),
    val sfu: CallSfuSessionConfig? = null
)

data class CallParticipantSummary(
    val userId: String,
    val name: String,
    val badge: String = "Student",
    val audioMuted: Boolean = false,
    val videoEnabled: Boolean = true,
    val isSelf: Boolean = false
)

data class CallRoomSummary(
    val id: String,
    val mode: CallMode = CallMode.GROUP_PUBLIC,
    val visibility: String = "public",
    val title: String = "English Video Room",
    val topic: String = "",
    val learningLevel: String = "all",
    val hostUserId: String = "",
    val hostName: String = "Learner",
    val targetUserId: String? = null,
    val targetName: String = "",
    val roomCode: String = "",
    val status: String = "active", // ringing | active | ended | declined | missed
    val endedReason: String = "",
    val isRandom: Boolean = false,
    val provider: CallProviderType = CallProviderType.FREE_P2P,
    val maxParticipants: Int = 6,
    val participantCount: Int = 0,
    val participants: List<CallParticipantSummary> = emptyList(),
    val isHost: Boolean = false,
    val isJoined: Boolean = false,
    val startedAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val selfUserId: String
        get() = participants.firstOrNull { it.isSelf }?.userId ?: if (isHost) hostUserId else targetUserId.orEmpty()

    val effectiveRandom: Boolean
        get() = isRandom || mode == CallMode.RANDOM || topic.contains("Random", ignoreCase = true)
}

data class CallLobbySnapshot(
    val config: CallProviderConfig = CallProviderConfig(),
    val publicRooms: List<CallRoomSummary> = emptyList(),
    val privateRooms: List<CallRoomSummary> = emptyList(),
    val directCalls: List<CallRoomSummary> = emptyList(),
    val incomingInvites: List<CallRoomSummary> = emptyList()
)

data class CallJoinResult(
    val room: CallRoomSummary,
    val transport: CallTransportSession
)

data class CallSignalMessage(
    val id: String,
    val roomId: String,
    val fromUserId: String,
    val toUserId: String,
    val type: String, // offer | answer | ice | renegotiate | bye
    val payload: String,
    val createdAt: String
)

data class CallRoomPollResult(
    val serverTime: String,
    val room: CallRoomSummary,
    val signals: List<CallSignalMessage>
)
