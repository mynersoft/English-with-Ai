package com.example.core.chat

import android.content.Context
import com.example.data.repository.ChatRepository
import com.example.domain.model.ChatIncomingNotification
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChatMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveCachedFcmToken(applicationContext, token)
        if (Firebase.auth.currentUser != null) {
            serviceScope.launch {
                runCatching { ChatRepository().registerPushToken(token) }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data["type"] != "chat_message") return

        val roomId = data["roomId"].orEmpty()
        val messageId = data["messageId"].orEmpty()
        if (roomId.isBlank() || messageId.isBlank()) return

        val notification = ChatIncomingNotification(
            messageId = messageId,
            roomId = roomId,
            roomName = data["roomName"].orEmpty().ifBlank { "Study Room" },
            roomType = data["roomType"].orEmpty().ifBlank { "group" },
            roomEmoji = data["roomEmoji"].orEmpty().ifBlank { "🦁" },
            senderId = data["senderId"].orEmpty(),
            senderName = data["senderName"].orEmpty().ifBlank { "Learner" },
            senderBadge = data["senderBadge"].orEmpty(),
            textPreview = data["textPreview"].orEmpty().ifBlank { "New message" },
            createdAt = data["createdAt"].orEmpty()
        )
        ChatMessageNotifier.showIncomingMessage(applicationContext, notification)
    }

    companion object {
        private const val PREFS_NAME = "english_ai_fcm_token"
        private const val KEY_FCM_TOKEN = "fcm_token"

        fun saveCachedFcmToken(context: Context, token: String) {
            if (token.isBlank()) return
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token.trim())
                .apply()
        }

        fun getCachedFcmToken(context: Context): String? {
            return context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, null)
                ?.takeIf { it.isNotBlank() }
        }
    }
}
