package com.example.core.wordofday

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.core.reminder.DailySessionReminderPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WordOfTheDayNotificationConstants {
    const val CHANNEL_ID = "word_of_the_day"
    const val NOTIFICATION_ID = 4308
    const val REQ_CODE = 4308
    const val EXTRA_OPEN_WORD_OF_THE_DAY = "extra_open_word_of_the_day"
    const val EXTRA_WORD_JSON = "extra_word_of_the_day_json"
}

class WordOfTheDayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WordOfTheDayScheduler.postMorningNotificationIfEligible(appContext)
                WordOfTheDayScheduler.scheduleMorningNotification(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class WordOfTheDayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            WordOfTheDayScheduler.scheduleMorningNotification(context)
        }
    }
}

object WordOfTheDayScheduler {
    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            WordOfTheDayNotificationConstants.CHANNEL_ID,
            context.getString(R.string.word_of_the_day_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.word_of_the_day_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun scheduleMorningNotification(
        context: Context,
        hour: Int = WordOfTheDayRules.DEFAULT_MORNING_HOUR,
        minute: Int = WordOfTheDayRules.DEFAULT_MORNING_MINUTE
    ) {
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appContext, WordOfTheDayReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            appContext,
            WordOfTheDayNotificationConstants.REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMillis = WordOfTheDayRules.nextMorningTriggerMillis(
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

    suspend fun postMorningNotificationIfEligible(
        context: Context,
        force: Boolean = false
    ): Boolean {
        val appContext = context.applicationContext
        val todayKey = WordOfTheDayStorage.todayDateKey()
        if (!force && !WordOfTheDayRules.shouldNotifyForDate(
                WordOfTheDayStorage.getLastNotifiedDate(appContext),
                todayKey
            )
        ) {
            return false
        }
        if (!DailySessionReminderPrefs.hasNotificationPermission(appContext)) {
            return false
        }

        val entry = WordOfTheDaySync.resolveEntryForNotification(appContext, todayKey) ?: return false
        ensureNotificationChannel(appContext)

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WordOfTheDayNotificationConstants.EXTRA_OPEN_WORD_OF_THE_DAY, true)
            putExtra(WordOfTheDayNotificationConstants.EXTRA_WORD_JSON, entry.toJson().toString())
        }
        val contentPi = PendingIntent.getActivity(
            appContext,
            WordOfTheDayNotificationConstants.REQ_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = WordOfTheDayRules.formatNotificationTitle(entry)
        val summary = WordOfTheDayRules.formatNotificationSummary(entry)
        val bigText = WordOfTheDayRules.formatNotificationBigText(entry)

        val notification = NotificationCompat.Builder(
            appContext,
            WordOfTheDayNotificationConstants.CHANNEL_ID
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(bigText)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .build()

        return runCatching {
            NotificationManagerCompat.from(appContext)
                .notify(WordOfTheDayNotificationConstants.NOTIFICATION_ID, notification)
            WordOfTheDayStorage.markWordShown(appContext, entry)
            WordOfTheDayStorage.markNotifiedToday(appContext, todayKey)
            true
        }.getOrDefault(false)
    }
}
