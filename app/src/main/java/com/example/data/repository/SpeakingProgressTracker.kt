package com.example.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocalSpeakingStats(
    val turns: Int = 0,
    val sessions: Int = 0,
    val totalSeconds: Long = 0L,
    val activeDates: Set<String> = emptySet()
) {
    val minutes: Int
        get() = computeSpeakingMinutes(totalSeconds)

    companion object {
        fun computeSpeakingMinutes(totalSeconds: Long): Int = when {
            totalSeconds <= 0L -> 0
            totalSeconds < 60L -> 1
            else -> (totalSeconds / 60L).toInt()
        }
    }
}

/**
 * Tracks AI Speaking turns, sessions, active days, and talk duration locally per signed-in user
 * so Student Profile & Progress always displays accurate AI speaking report & talk minutes.
 */
class SpeakingProgressTracker(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "speaking_progress_prefs",
        Context.MODE_PRIVATE
    )

    private fun uid(): String = auth.currentUser?.uid ?: "guest"

    fun getStats(): LocalSpeakingStats {
        val u = uid()
        return LocalSpeakingStats(
            turns = prefs.getInt("turns_$u", 0),
            sessions = prefs.getInt("sessions_$u", 0),
            totalSeconds = prefs.getLong("seconds_$u", 0L),
            activeDates = prefs.getStringSet("dates_$u", emptySet())?.toSet() ?: emptySet()
        )
    }

    fun recordTurn(isNewSession: Boolean, durationSeconds: Int) {
        val u = uid()
        val currentTurns = prefs.getInt("turns_$u", 0)
        val currentSessions = prefs.getInt("sessions_$u", 0)
        val currentSeconds = prefs.getLong("seconds_$u", 0L)
        val dates = (prefs.getStringSet("dates_$u", emptySet()) ?: emptySet()).toMutableSet()
        dates.add(todayIsoDate())

        val safeDuration = durationSeconds.coerceAtLeast(5).toLong()
        prefs.edit()
            .putInt("turns_$u", currentTurns + 1)
            .putInt("sessions_$u", currentSessions + if (isNewSession) 1 else 0)
            .putLong("seconds_$u", currentSeconds + safeDuration)
            .putStringSet("dates_$u", dates)
            .apply()
    }

    fun addSeconds(durationSeconds: Int) {
        if (durationSeconds <= 0) return
        val u = uid()
        val currentSeconds = prefs.getLong("seconds_$u", 0L)
        prefs.edit()
            .putLong("seconds_$u", currentSeconds + durationSeconds.toLong())
            .apply()
    }

    private fun todayIsoDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
