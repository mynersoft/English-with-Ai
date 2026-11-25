package com.example.domain.model

data class ChatRoomSummary(
    val id: String,
    val name: String,
    val description: String = "",
    val avatarEmoji: String = "🦁",
    val roomType: String = "group",
    val memberCount: Int = 0,
    val maxMembers: Int = 50,
    val onlineCount: Int = 0,
    val isMember: Boolean = false,
    val lastMessageText: String = "",
    val lastMessageAt: String? = null
)

data class ChatMemberSummary(
    val id: String,
    val name: String,
    val badge: String = "Student"
)

data class ChatStudentSummary(
    val id: String,
    val name: String,
    val badge: String = "Student",
    val learningLevel: String = "beginner"
)

data class ChatReplyQuote(
    val messageId: String,
    val senderName: String,
    val textPreview: String
)

data class ChatCorrectionToken(
    val text: String,
    val changed: Boolean = false
)

data class ChatAiCorrection(
    val status: String = "none", // "none" | "corrected" | "correct" | "unavailable"
    val hasMistake: Boolean = false,
    val correctedText: String = "",
    val tokens: List<ChatCorrectionToken> = emptyList(),
    val explanationBn: String = ""
)

data class ChatMessageItem(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val senderBadge: String = "",
    val isMine: Boolean = false,
    val text: String,
    val replyTo: ChatReplyQuote? = null,
    val aiCorrection: ChatAiCorrection = ChatAiCorrection(),
    val createdAt: String = ""
)

data class ChatRoomsSnapshot(
    val aiConfigured: Boolean = false,
    val groupRooms: List<ChatRoomSummary> = emptyList(),
    val directRooms: List<ChatRoomSummary> = emptyList(),
    val starterChips: List<String> = emptyList()
)

data class ChatThreadSnapshot(
    val aiConfigured: Boolean = false,
    val room: ChatRoomSummary,
    val members: List<ChatMemberSummary> = emptyList(),
    val messages: List<ChatMessageItem> = emptyList()
)
