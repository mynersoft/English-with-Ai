package com.example.core.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DailySessionRules {
    const val DEFAULT_REMINDER_HOUR = 19
    const val DEFAULT_REMINDER_MINUTE = 0

    fun formatDateKey(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date(millis))
    }

    fun shouldShowLoginReminderBanner(
        lastLoggedInDate: String?,
        dismissedBannerDate: String?,
        todayDate: String
    ): Boolean {
        return lastLoggedInDate != todayDate && dismissedBannerDate != todayDate
    }

    fun shouldFireNotificationToday(
        lastLoggedInDate: String?,
        lastNotifiedDate: String?,
        todayDate: String
    ): Boolean {
        return lastLoggedInDate != todayDate && lastNotifiedDate != todayDate
    }

    fun nextDailyTriggerMillis(
        nowMillis: Long,
        hour: Int = DEFAULT_REMINDER_HOUR,
        minute: Int = DEFAULT_REMINDER_MINUTE
    ): Long {
        val clampedHour = hour.coerceIn(0, 23)
        val clampedMinute = minute.coerceIn(0, 59)
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, clampedHour)
            set(Calendar.MINUTE, clampedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }
}

object DailySessionReminderPrefs {
    private const val PREFS_NAME = "daily_session_reminder_prefs"
    private const val KEY_LAST_LOGGED_IN_DATE = "last_logged_in_date"
    private const val KEY_LAST_NOTIFIED_DATE = "last_notified_date"
    private const val KEY_DISMISSED_BANNER_DATE = "dismissed_banner_date"
    private const val KEY_PERMISSION_PROMPTED = "notification_permission_prompted"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun todayDateKey(nowMillis: Long = System.currentTimeMillis()): String =
        DailySessionRules.formatDateKey(nowMillis)

    fun hasLoggedInToday(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val today = todayDateKey(nowMillis)
        return prefs(context).getString(KEY_LAST_LOGGED_IN_DATE, null) == today
    }

    fun markLoggedInToday(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val today = todayDateKey(nowMillis)
        prefs(context).edit()
            .putString(KEY_LAST_LOGGED_IN_DATE, today)
            .apply()
        DailySessionReminderScheduler.cancelNotification(context)
    }

    fun shouldShowLoginBanner(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        return DailySessionRules.shouldShowLoginReminderBanner(
            lastLoggedInDate = p.getString(KEY_LAST_LOGGED_IN_DATE, null),
            dismissedBannerDate = p.getString(KEY_DISMISSED_BANNER_DATE, null),
            todayDate = todayDateKey(nowMillis)
        )
    }

    fun dismissLoginBannerForToday(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_DISMISSED_BANNER_DATE, todayDateKey(nowMillis))
            .apply()
    }

    fun shouldNotifyToday(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        return DailySessionRules.shouldFireNotificationToday(
            lastLoggedInDate = p.getString(KEY_LAST_LOGGED_IN_DATE, null),
            lastNotifiedDate = p.getString(KEY_LAST_NOTIFIED_DATE, null),
            todayDate = todayDateKey(nowMillis)
        )
    }

    fun markNotifiedToday(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putString(KEY_LAST_NOTIFIED_DATE, todayDateKey(nowMillis))
            .apply()
    }

    fun hasPromptedNotificationPermission(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERMISSION_PROMPTED, false)

    fun markNotificationPermissionPrompted(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_PERMISSION_PROMPTED, true)
            .apply()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

class DailySessionReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DailySessionReminderScheduler.postNotificationIfEligible(context)
    }

    companion object {
        const val CHANNEL_ID = "daily_session_reminder"
        const val NOTIFICATION_ID = 4205
        const val REQ_CODE = 4205
    }
}

class DailySessionBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DailySessionReminderScheduler.scheduleDailyReminder(context)
        }
    }
}

object DailySessionReminderScheduler {
    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            DailySessionReminderReceiver.CHANNEL_ID,
            context.getString(R.string.daily_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.daily_reminder_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun scheduleDailyReminder(
        context: Context,
        hour: Int = DailySessionRules.DEFAULT_REMINDER_HOUR,
        minute: Int = DailySessionRules.DEFAULT_REMINDER_MINUTE
    ) {
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, DailySessionReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            appContext,
            DailySessionReminderReceiver.REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMillis = DailySessionRules.nextDailyTriggerMillis(
            nowMillis = System.currentTimeMillis(),
            hour = hour,
            minute = minute
        )
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            AlarmManager.INTERVAL_DAY,
            pi
        )
    }

    fun postNotificationIfEligible(context: Context) {
        val appContext = context.applicationContext
        if (!DailySessionReminderPrefs.shouldNotifyToday(appContext)) return
        if (!DailySessionReminderPrefs.hasNotificationPermission(appContext)) return

        ensureNotificationChannel(appContext)

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            appContext,
            DailySessionReminderReceiver.REQ_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, DailySessionReminderReceiver.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.daily_reminder_notif_title))
            .setContentText(appContext.getString(R.string.daily_reminder_notif_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(appContext.getString(R.string.daily_reminder_notif_body))
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext)
                .notify(DailySessionReminderReceiver.NOTIFICATION_ID, notification)
            DailySessionReminderPrefs.markNotifiedToday(appContext)
        }
    }

    fun cancelNotification(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context.applicationContext)
                .cancel(DailySessionReminderReceiver.NOTIFICATION_ID)
        }
    }
}
