package com.example.core.wordofday

import androidx.compose.runtime.Immutable
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Immutable
data class WordOfTheDayEntry(
    val vocabularyId: String = "",
    val word: String,
    val meaning: String,
    val pronunciation: String = "",
    val partOfSpeech: String = "",
    val level: String = "A1",
    val exampleEn: String = "",
    val exampleBn: String = "",
    val dateKey: String = ""
) {
    fun normalizedKey(): String = word.trim().lowercase(Locale.US)

    fun toJson(): JSONObject = JSONObject()
        .put("id", vocabularyId)
        .put("word", word)
        .put("meaning", meaning)
        .put("pronunciation", pronunciation)
        .put("partOfSpeech", partOfSpeech)
        .put("level", level)
        .put("exampleEn", exampleEn)
        .put("exampleBn", exampleBn)
        .put("date", dateKey)

    companion object {
        fun fromJson(obj: JSONObject?): WordOfTheDayEntry? {
            if (obj == null) return null
            val word = obj.optString("word").trim()
            val meaning = obj.optString("meaning").trim()
            if (word.isBlank() || meaning.isBlank()) return null
            val examplesArr = obj.optJSONArray("examples")
            val firstEx = examplesArr?.optJSONObject(0)
            val exEn = obj.optString("exampleEn").trim()
                .ifBlank { firstEx?.optString("en")?.trim().orEmpty() }
            val exBn = obj.optString("exampleBn").trim()
                .ifBlank { firstEx?.optString("bn")?.trim().orEmpty() }
            return WordOfTheDayEntry(
                vocabularyId = obj.optString("id").trim(),
                word = word,
                meaning = meaning,
                pronunciation = obj.optString("pronunciation").trim(),
                partOfSpeech = obj.optString("partOfSpeech").trim(),
                level = obj.optString("level", "A1").trim().ifBlank { "A1" },
                exampleEn = exEn,
                exampleBn = exBn,
                dateKey = obj.optString("date").trim()
            )
        }
    }
}

object WordOfTheDayRules {
    const val DEFAULT_MORNING_HOUR = 8
    const val DEFAULT_MORNING_MINUTE = 0

    fun formatDateKey(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date(millis))
    }

    fun nextMorningTriggerMillis(
        nowMillis: Long,
        hour: Int = DEFAULT_MORNING_HOUR,
        minute: Int = DEFAULT_MORNING_MINUTE
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

    fun shouldNotifyForDate(lastNotifiedDate: String?, todayDateKey: String): Boolean {
        return lastNotifiedDate != todayDateKey
    }

    fun pickNextUnshownWord(
        candidates: List<WordOfTheDayEntry>,
        shownKeys: Set<String>,
        seedDateKey: String
    ): WordOfTheDayEntry? {
        val valid = candidates.filter { it.word.isNotBlank() && it.meaning.isNotBlank() }
        if (valid.isEmpty()) return null

        val unshownExactDate = valid.firstOrNull {
            it.dateKey == seedDateKey && !shownKeys.contains(it.normalizedKey())
        }
        if (unshownExactDate != null) return unshownExactDate

        val firstUnshown = valid.firstOrNull { !shownKeys.contains(it.normalizedKey()) }
        if (firstUnshown != null) return firstUnshown

        val idx = abs(seedDateKey.hashCode()) % valid.size
        return valid[idx]
    }

    fun formatNotificationTitle(entry: WordOfTheDayEntry): String =
        "Word of the Day: ${entry.word}"

    fun formatNotificationSummary(entry: WordOfTheDayEntry): String {
        val ex = entry.exampleEn.trim()
        return if (ex.isNotBlank()) {
            "${entry.meaning} — \"$ex\""
        } else {
            entry.meaning
        }
    }

    fun formatNotificationBigText(entry: WordOfTheDayEntry): String = buildString {
        append("Meaning: ").append(entry.meaning)
        val meta = listOfNotNull(
            entry.pronunciation.takeIf { it.isNotBlank() },
            entry.partOfSpeech.takeIf { it.isNotBlank() }
        )
        if (meta.isNotEmpty()) {
            append("\n").append(meta.joinToString(" • "))
        }
        if (entry.exampleEn.isNotBlank()) {
            append("\nExample: \"").append(entry.exampleEn).append("\"")
        }
        if (entry.exampleBn.isNotBlank()) {
            append("\n(").append(entry.exampleBn).append(")")
        }
    }
}
