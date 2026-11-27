package com.example.core.wordofday

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object WordOfTheDayStorage {
    private const val PREFS_NAME = "word_of_the_day_prefs"
    private const val KEY_CACHED_POOL = "cached_pool_json"
    private const val KEY_SHOWN_WORDS = "shown_word_keys"
    private const val KEY_LAST_NOTIFIED_DATE = "last_notified_date"
    private const val KEY_CURRENT_ENTRY = "current_entry_json"
    private const val KEY_LAST_SYNC_DATE = "last_sync_date"
    private const val MAX_SHOWN_HISTORY = 250

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun todayDateKey(nowMillis: Long = System.currentTimeMillis()): String =
        WordOfTheDayRules.formatDateKey(nowMillis)

    fun getLastNotifiedDate(context: Context): String? =
        prefs(context).getString(KEY_LAST_NOTIFIED_DATE, null)

    fun markNotifiedToday(context: Context, dateKey: String = todayDateKey()) {
        prefs(context).edit().putString(KEY_LAST_NOTIFIED_DATE, dateKey).apply()
    }

    fun getLastSyncDate(context: Context): String? =
        prefs(context).getString(KEY_LAST_SYNC_DATE, null)

    fun markSyncedToday(context: Context, dateKey: String = todayDateKey()) {
        prefs(context).edit().putString(KEY_LAST_SYNC_DATE, dateKey).apply()
    }

    fun getShownWordKeys(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SHOWN_WORDS, emptySet())?.toSet() ?: emptySet()

    fun markWordShown(context: Context, entry: WordOfTheDayEntry) {
        val current = getShownWordKeys(context).toMutableList()
        val key = entry.normalizedKey()
        if (key.isBlank()) return
        current.remove(key)
        current.add(key)
        val trimmed = if (current.size > MAX_SHOWN_HISTORY) {
            current.takeLast(MAX_SHOWN_HISTORY)
        } else {
            current
        }
        prefs(context).edit()
            .putStringSet(KEY_SHOWN_WORDS, trimmed.toSet())
            .putString(KEY_CURRENT_ENTRY, entry.toJson().toString())
            .apply()
    }

    fun saveCurrentEntry(context: Context, entry: WordOfTheDayEntry) {
        prefs(context).edit().putString(KEY_CURRENT_ENTRY, entry.toJson().toString()).apply()
    }

    fun getCurrentEntry(context: Context): WordOfTheDayEntry? {
        val raw = prefs(context).getString(KEY_CURRENT_ENTRY, null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return WordOfTheDayEntry.fromJson(json)
    }

    fun saveCachedPool(context: Context, entries: List<WordOfTheDayEntry>) {
        val clean = entries
            .filter { it.word.isNotBlank() && it.meaning.isNotBlank() }
            .distinctBy { it.normalizedKey() }
        if (clean.isEmpty()) return
        val arr = JSONArray()
        clean.forEach { arr.put(it.toJson()) }
        prefs(context).edit()
            .putString(KEY_CACHED_POOL, arr.toString())
            .putString(KEY_LAST_SYNC_DATE, todayDateKey())
            .apply()
    }

    fun getCachedPool(context: Context): List<WordOfTheDayEntry> {
        val raw = prefs(context).getString(KEY_CACHED_POOL, null)
        if (!raw.isNullOrBlank()) {
            val arr = runCatching { JSONArray(raw) }.getOrNull()
            if (arr != null) {
                val parsed = buildList {
                    for (i in 0 until arr.length()) {
                        WordOfTheDayEntry.fromJson(arr.optJSONObject(i))?.let(::add)
                    }
                }
                if (parsed.isNotEmpty()) return parsed
            }
        }
        return BUILTIN_STARTER_WORDS
    }

    private val BUILTIN_STARTER_WORDS = listOf(
        WordOfTheDayEntry(
            word = "Grateful",
            meaning = "কৃতজ্ঞ",
            pronunciation = "/ˈɡreɪt.fəl/",
            partOfSpeech = "adjective",
            level = "A2",
            exampleEn = "I am grateful for your help today.",
            exampleBn = "আজ আপনার সাহায্যের জন্য আমি কৃতজ্ঞ।"
        ),
        WordOfTheDayEntry(
            word = "Improve",
            meaning = "উন্নতি করা",
            pronunciation = "/ɪmˈpruːv/",
            partOfSpeech = "verb",
            level = "A2",
            exampleEn = "Reading every morning helps improve your English.",
            exampleBn = "প্রতিদিন সকালে পড়লে তোমার ইংরেজির উন্নতি হবে।"
        ),
        WordOfTheDayEntry(
            word = "Confident",
            meaning = "আত্মবিশ্বাসী",
            pronunciation = "/ˈkɒn.fɪ.dənt/",
            partOfSpeech = "adjective",
            level = "A2",
            exampleEn = "She felt confident before speaking in class.",
            exampleBn = "ক্লাসে কথা বলার আগে সে আত্মবিশ্বাসী অনুভব করেছিল।"
        ),
        WordOfTheDayEntry(
            word = "Determine",
            meaning = "স্থির করা / নির্ধারণ করা",
            pronunciation = "/dɪˈtɜː.mɪn/",
            partOfSpeech = "verb",
            level = "B1",
            exampleEn = "Your daily habits determine your future success.",
            exampleBn = "তোমার প্রতিদিনের অভ্যাস তোমার ভবিষ্যতের সাফল্য নির্ধারণ করে।"
        ),
        WordOfTheDayEntry(
            word = "Curiosity",
            meaning = "কৌতূহল",
            pronunciation = "/ˌkjʊə.riˈɒs.ə.ti/",
            partOfSpeech = "noun",
            level = "B1",
            exampleEn = "Curiosity encourages learners to ask thoughtful questions.",
            exampleBn = "কৌতূহল শিক্ষার্থীদের চিন্তাশীল প্রশ্ন করতে উৎসাহিত করে।"
        ),
        WordOfTheDayEntry(
            word = "Consistent",
            meaning = "ধারাবাহিক",
            pronunciation = "/kənˈsɪs.tənt/",
            partOfSpeech = "adjective",
            level = "B1",
            exampleEn = "Consistent practice brings steady progress.",
            exampleBn = "ধারাবাহিক অনুশীলন স্থির অগ্রগতি আনে।"
        ),
        WordOfTheDayEntry(
            word = "Encourage",
            meaning = "উৎসাহিত করা",
            pronunciation = "/ɪnˈkʌr.ɪdʒ/",
            partOfSpeech = "verb",
            level = "A2",
            exampleEn = "Good teachers encourage their students every day.",
            exampleBn = "ভালো শিক্ষকরা প্রতিদিন তাদের শিক্ষার্থীদের উৎসাহিত করেন।"
        ),
        WordOfTheDayEntry(
            word = "Solution",
            meaning = "সমাধান",
            pronunciation = "/səˈluː.ʃən/",
            partOfSpeech = "noun",
            level = "A2",
            exampleEn = "We found a simple solution to the problem.",
            exampleBn = "আমরা সমস্যাটির একটি সহজ সমাধান খুঁজে পেয়েছি।"
        ),
        WordOfTheDayEntry(
            word = "Achieve",
            meaning = "অর্জন করা",
            pronunciation = "/əˈtʃiːv/",
            partOfSpeech = "verb",
            level = "A2",
            exampleEn = "You can achieve your goal step by step.",
            exampleBn = "তুমি ধাপে ধাপে তোমার লক্ষ্য অর্জন করতে পারো।"
        ),
        WordOfTheDayEntry(
            word = "Patience",
            meaning = "ধৈর্য",
            pronunciation = "/ˈpeɪ.ʃəns/",
            partOfSpeech = "noun",
            level = "B1",
            exampleEn = "Learning a new language requires patience.",
            exampleBn = "নতুন ভাষা শেখার জন্য ধৈর্যের প্রয়োজন।"
        )
    )
}
