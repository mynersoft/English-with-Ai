package com.example.core.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.MainActivity
import com.example.R
import com.example.core.reminder.DailySessionReminderPrefs
import com.example.domain.model.ChatIncomingNotification
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object ChatNotificationConstants {
    const val CHANNEL_ID = "chat_messages"
    const val EXTRA_OPEN_CHAT_ROOM_ID = "extra_open_chat_room_id"
    const val EXTRA_OPEN_CHAT_MESSAGE_ID = "extra_open_chat_message_id"

    fun notificationIdForRoom(roomId: String): Int {
        return 5000 + ((roomId.hashCode() and 0x7fffffff) % 100_000)
    }
}

object ActiveChatRoomTracker {
    @Volatile
    var activeRoomId: String? = null
        private set

    fun setActiveRoom(roomId: String?) {
        activeRoomId = roomId
    }

    fun isViewingRoom(roomId: String): Boolean {
        return !roomId.isBlank() && activeRoomId == roomId
    }
}

object ChatMessageNotifier {
    private const val PREFS_NAME = "english_ai_chat_notifications"
    private const val KEY_SEEN_IDS = "seen_message_ids"
    private const val KEY_STACK_PREFIX = "room_stack_"
    private const val MAX_SEEN_IDS = 120
    private const val MAX_STACK_PER_ROOM = 6

    private val lock = Any()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val existing = manager.getNotificationChannel(ChatNotificationConstants.CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            ChatNotificationConstants.CHANNEL_ID,
            appContext.getString(R.string.chat_messages_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.chat_messages_channel_desc)
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    fun showIncomingMessage(
        context: Context,
        notification: ChatIncomingNotification
    ): Boolean {
        val appContext = context.applicationContext
        if (notification.messageId.isBlank() || notification.roomId.isBlank()) return false
        if (ActiveChatRoomTracker.isViewingRoom(notification.roomId)) {
            markMessageSeen(appContext, notification.messageId)
            return false
        }

        val stackEntries: List<StackedChatEntry>
        synchronized(lock) {
            if (hasSeenMessage(appContext, notification.messageId)) {
                return false
            }
            markMessageSeen(appContext, notification.messageId)
            stackEntries = appendRoomStack(appContext, notification)
        }

        if (!DailySessionReminderPrefs.hasNotificationPermission(appContext)) {
            return false
        }

        ensureChannel(appContext)

        val mePerson = Person.Builder()
            .setKey("me")
            .setName("You")
            .build()

        val isGroup = notification.roomType == "group"
        val conversationTitle = if (isGroup) {
            "${notification.roomEmoji} ${notification.roomName}".trim()
        } else {
            notification.senderName.ifBlank { notification.roomName }
        }

        val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
            .setGroupConversation(isGroup)
            .setConversationTitle(conversationTitle)

        stackEntries.forEach { entry ->
            val senderPerson = Person.Builder()
                .setKey(entry.senderId.ifBlank { entry.senderName })
                .setName(entry.senderName)
                .build()
            messagingStyle.addMessage(entry.text, entry.timestampMs, senderPerson)
        }

        val notificationId = ChatNotificationConstants.notificationIdForRoom(notification.roomId)
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ChatNotificationConstants.EXTRA_OPEN_CHAT_ROOM_ID, notification.roomId)
            putExtra(ChatNotificationConstants.EXTRA_OPEN_CHAT_MESSAGE_ID, notification.messageId)
        }

        val contentIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summaryLine = if (stackEntries.size > 1) {
            "${stackEntries.size} new messages"
        } else {
            notification.textPreview.ifBlank { "New message" }
        }

        val built = NotificationCompat.Builder(appContext, ChatNotificationConstants.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(conversationTitle)
            .setContentText(
                if (isGroup) "${notification.senderName}: $summaryLine" else summaryLine
            )
            .setStyle(messagingStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setNumber(stackEntries.size)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setGroup("chat_room_${notification.roomId}")
            .build()

        return runCatching {
            NotificationManagerCompat.from(appContext).notify(notificationId, built)
            true
        }.getOrDefault(false)
    }

    fun clearRoomNotifications(context: Context, roomId: String) {
        if (roomId.isBlank()) return
        val appContext = context.applicationContext
        synchronized(lock) {
            prefs(appContext).edit().remove("$KEY_STACK_PREFIX$roomId").apply()
        }
        NotificationManagerCompat.from(appContext)
            .cancel(ChatNotificationConstants.notificationIdForRoom(roomId))
    }

    fun clearAll(context: Context) {
        val appContext = context.applicationContext
        synchronized(lock) {
            val allKeys = prefs(appContext).all.keys
                .filter { it.startsWith(KEY_STACK_PREFIX) }
            val editor = prefs(appContext).edit()
            allKeys.forEach { key ->
                val roomId = key.removePrefix(KEY_STACK_PREFIX)
                if (roomId.isNotBlank()) {
                    NotificationManagerCompat.from(appContext)
                        .cancel(ChatNotificationConstants.notificationIdForRoom(roomId))
                }
                editor.remove(key)
            }
            editor.apply()
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun hasSeenMessage(context: Context, messageId: String): Boolean {
        val raw = prefs(context).getString(KEY_SEEN_IDS, null) ?: return false
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return false
        for (i in 0 until arr.length()) {
            if (arr.optString(i) == messageId) return true
        }
        return false
    }

    private fun markMessageSeen(context: Context, messageId: String) {
        val p = prefs(context)
        val existing = runCatching { JSONArray(p.getString(KEY_SEEN_IDS, "[]")) }
            .getOrElse { JSONArray() }
        val list = mutableListOf<String>()
        for (i in 0 until existing.length()) {
            val id = existing.optString(i)
            if (id.isNotBlank() && id != messageId) list.add(id)
        }
        list.add(messageId)
        val capped = list.takeLast(MAX_SEEN_IDS)
        p.edit().putString(KEY_SEEN_IDS, JSONArray(capped).toString()).apply()
    }

    private fun appendRoomStack(
        context: Context,
        item: ChatIncomingNotification
    ): List<StackedChatEntry> {
        val p = prefs(context)
        val key = "$KEY_STACK_PREFIX${item.roomId}"
        val raw = p.getString(key, null)
        val entries = mutableListOf<StackedChatEntry>()
        if (!raw.isNullOrBlank()) {
            val arr = runCatching { JSONArray(raw) }.getOrNull()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    entries.add(
                        StackedChatEntry(
                            senderId = obj.optString("senderId"),
                            senderName = obj.optString("senderName", "Learner"),
                            text = obj.optString("text"),
                            timestampMs = obj.optLong("timestampMs", System.currentTimeMillis())
                        )
                    )
                }
            }
        }

        val tsMs = runCatching {
            Instant.parse(item.createdAt).toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }

        entries.add(
            StackedChatEntry(
                senderId = item.senderId,
                senderName = item.senderName.ifBlank { "Learner" },
                text = item.textPreview.ifBlank { "New message" },
                timestampMs = tsMs
            )
        )

        val capped = entries.takeLast(MAX_STACK_PER_ROOM)
        val out = JSONArray()
        capped.forEach { e ->
            out.put(
                JSONObject()
                    .put("senderId", e.senderId)
                    .put("senderName", e.senderName)
                    .put("text", e.text)
                    .put("timestampMs", e.timestampMs)
            )
        }
        p.edit().putString(key, out.toString()).apply()
        return capped
    }

    private data class StackedChatEntry(
        val senderId: String,
        val senderName: String,
        val text: String,
        val timestampMs: Long
    )
}
