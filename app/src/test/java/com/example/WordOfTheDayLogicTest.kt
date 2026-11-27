package com.example

import com.example.core.wordofday.WordOfTheDayEntry
import com.example.core.wordofday.WordOfTheDayRules
import com.example.data.repository.WordLookupCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WordOfTheDayLogicTest {

    @Test
    fun nextMorningTriggerSchedules8AmSameOrNextDay() {
        val beforeEight = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 27, 6, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sameDayTrigger = Calendar.getInstance().apply {
            timeInMillis = WordOfTheDayRules.nextMorningTriggerMillis(beforeEight.timeInMillis)
        }
        assertEquals(27, sameDayTrigger.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, sameDayTrigger.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, sameDayTrigger.get(Calendar.MINUTE))

        val afterEight = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 27, 9, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextDayTrigger = Calendar.getInstance().apply {
            timeInMillis = WordOfTheDayRules.nextMorningTriggerMillis(afterEight.timeInMillis)
        }
        assertEquals(28, nextDayTrigger.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, nextDayTrigger.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextDayTrigger.get(Calendar.MINUTE))
    }

    @Test
    fun pickNextUnshownWordSkipsShownWordsAndFormatsNotificationContent() {
        val pool = listOf(
            WordOfTheDayEntry(
                word = "Grateful",
                meaning = "কৃতজ্ঞ",
                pronunciation = "/ˈɡreɪt.fəl/",
                partOfSpeech = "adjective",
                exampleEn = "I am grateful for your help."
            ),
            WordOfTheDayEntry(
                word = "Consistent",
                meaning = "ধারাবাহিক",
                pronunciation = "/kənˈsɪs.tənt/",
                partOfSpeech = "adjective",
                exampleEn = "Consistent practice brings steady progress.",
                exampleBn = "ধারাবাহিক অনুশীলন স্থির অগ্রগতি আনে।"
            )
        )

        val picked = WordOfTheDayRules.pickNextUnshownWord(
            candidates = pool,
            shownKeys = setOf("grateful"),
            seedDateKey = "2026-11-27"
        )
        assertNotNull(picked)
        assertEquals("Consistent", picked?.word)

        val title = WordOfTheDayRules.formatNotificationTitle(picked!!)
        val bigText = WordOfTheDayRules.formatNotificationBigText(picked)
        assertTrue(title.contains("Consistent"))
        assertTrue(bigText.contains("ধারাবাহিক"))
        assertTrue(bigText.contains("Consistent practice brings steady progress."))
        assertFalse(WordOfTheDayRules.shouldNotifyForDate("2026-11-27", "2026-11-27"))
        assertTrue(WordOfTheDayRules.shouldNotifyForDate("2026-11-26", "2026-11-27"))
    }

    @Test
    fun wordLookupCandidatesResolveInflectedAndIrregularWords() {
        assertTrue(WordLookupCandidates.build("brushes").contains("brush"))
        assertTrue(WordLookupCandidates.build("walked").contains("walk"))
        assertTrue(WordLookupCandidates.build("writing").contains("write"))
        assertTrue(WordLookupCandidates.build("studies").contains("study"))
        assertTrue(WordLookupCandidates.build("friend's").contains("friend"))
        assertTrue(WordLookupCandidates.build("went").contains("go"))
        assertTrue(WordLookupCandidates.build("children").contains("child"))
    }
}
