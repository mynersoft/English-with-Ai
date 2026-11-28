package com.example.core.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.core.reminder.DailySessionReminderPrefs

object CallNotificationConstants {
    const val CHANNEL_ID = "call_invites"
    const val EXTRA_OPEN_CALL_ROOM_ID = "extra_open_call_room_id"
    const val EXTRA_OPEN_CALL_CALLER_NAME = "extra_open_call_caller_name"

    fun notificationIdForCall(roomId: String): Int {
        return 9000 + ((roomId.hashCode() and 0x7fffffff) % 10_000)
    }
}

object ActiveCallTracker {
    @Volatile
    var activeCallRoomId: String? = null
        private set

    fun setActiveCall(roomId: String?) {
        activeCallRoomId = roomId
    }
}

object CallInviteNotifier {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CallNotificationConstants.CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CallNotificationConstants.CHANNEL_ID,
            appContext.getString(R.string.call_invites_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = appContext.getString(R.string.call_invites_channel_desc)
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun showIncomingCallInvite(
        context: Context,
        callRoomId: String,
        callerName: String,
        title: String
    ): Boolean {
        val appContext = context.applicationContext
        if (callRoomId.isBlank()) return false
        if (ActiveCallTracker.activeCallRoomId == callRoomId) return false
        if (!DailySessionReminderPrefs.hasNotificationPermission(appContext)) return false

        ensureChannel(appContext)
        val notificationId = CallNotificationConstants.notificationIdForCall(callRoomId)
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CallNotificationConstants.EXTRA_OPEN_CALL_ROOM_ID, callRoomId)
            putExtra(CallNotificationConstants.EXTRA_OPEN_CALL_CALLER_NAME, callerName)
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeCaller = callerName.ifBlank { "Classmate" }
        val built = NotificationCompat.Builder(appContext, CallNotificationConstants.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming Video Call")
            .setContentText("$safeCaller is inviting you: ${title.ifBlank { "1-on-1 English Call" }}")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(appContext).notify(notificationId, built)
            true
        }.getOrDefault(false)
    }

    fun clearCallNotification(context: Context, callRoomId: String) {
        if (callRoomId.isBlank()) return
        NotificationManagerCompat.from(context.applicationContext)
            .cancel(CallNotificationConstants.notificationIdForCall(callRoomId))
    }
}
