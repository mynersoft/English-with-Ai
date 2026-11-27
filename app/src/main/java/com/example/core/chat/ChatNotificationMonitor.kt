package com.example.core.chat

import android.content.Context
import com.example.data.repository.ChatRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import java.time.Instant

object ChatNotificationMonitor {
    private const val PREFS_NAME = "english_ai_chat_monitor"
    private const val KEY_LAST_POLL_ISO_PREFIX = "last_poll_iso_"
    private const val POLL_INTERVAL_MS = 8_000L

    suspend fun syncCurrentFcmToken(
        context: Context,
        repo: ChatRepository = ChatRepository()
    ) {
        if (Firebase.auth.currentUser == null) return
        val token = runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: ChatMessagingService.getCachedFcmToken(context)
            ?: return

        ChatMessagingService.saveCachedFcmToken(context, token)
        runCatching { repo.registerPushToken(token) }
    }

    suspend fun unregisterCurrentFcmToken(
        context: Context,
        repo: ChatRepository = ChatRepository()
    ) {
        val token = ChatMessagingService.getCachedFcmToken(context)
            ?: runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
        if (!token.isNullOrBlank()) {
            runCatching { repo.unregisterPushToken(token) }
        }
        ChatMessageNotifier.clearAll(context)
    }

    suspend fun runMonitorLoop(
        context: Context,
        userId: String,
        isOnline: Boolean,
        repo: ChatRepository = ChatRepository()
    ) {
        if (!isOnline || userId.isBlank()) return
        val appContext = context.applicationContext
        syncCurrentFcmToken(appContext, repo)

        var lastPollIso = getOrInitLastPollIso(appContext, userId)
        while (currentCoroutineContext().isActive && Firebase.auth.currentUser != null) {
            repo.fetchIncomingNotifications(afterIso = lastPollIso)
                .onSuccess { batch ->
                    batch.notifications.forEach { item ->
                        ChatMessageNotifier.showIncomingMessage(appContext, item)
                    }
                    val nextCursor = batch.notifications.lastOrNull()?.createdAt
                        ?.takeIf { it.isNotBlank() }
                        ?: batch.serverTime.takeIf { it.isNotBlank() }
                    if (!nextCursor.isNullOrBlank()) {
                        lastPollIso = nextCursor
                        saveLastPollIso(appContext, userId, nextCursor)
                    }
                }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun getOrInitLastPollIso(context: Context, userId: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$KEY_LAST_POLL_ISO_PREFIX$userId"
        val existing = prefs.getString(key, null)
        if (!existing.isNullOrBlank()) return existing
        val nowIso = Instant.now().toString()
        prefs.edit().putString(key, nowIso).apply()
        return nowIso
    }

    private fun saveLastPollIso(context: Context, userId: String, iso: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$KEY_LAST_POLL_ISO_PREFIX$userId", iso)
            .apply()
    }
}
