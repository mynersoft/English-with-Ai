package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.VocabularyRepository
import com.example.domain.model.VocabDashboardData
import com.example.domain.model.VocabSettings
import com.example.feature.vocabulary.CsvImportHelper
import com.example.feature.vocabulary.QuizUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VocabularyLogicTest {

    @Test
    fun `offline dashboard cache returns null initially and parses valid cached payload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("english_ai_vocab_cache", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val repo = VocabularyRepository(context)
        assertNull(repo.getCachedDashboard())

        prefs.edit().putString(
            "last_dashboard_json",
            """
            {
              "greetingName": "Mahir",
              "date": "2026-11-24",
              "timezone": "Asia/Dhaka",
              "dailyGoal": 5,
              "newWordsDone": 3,
              "isGoalCompleted": false,
              "reviewedToday": 2,
              "dueReviews": 4,
              "totalLearned": 15,
              "masteredCount": 2,
              "streak": 6,
              "categories": [],
              "recentActivity": []
            }
            """.trimIndent()
        ).commit()

        val cached: VocabDashboardData? = repo.getCachedDashboard()
        assertNotNull(cached)
        assertEquals("Mahir", cached?.greetingName)
        assertEquals(5, cached?.dailyGoal)
        assertEquals(3, cached?.newWordsDone)
        assertEquals(6, cached?.streak)
        assertEquals(true, cached?.isFromOfflineCache)
    }

    @Test
    fun `default vocabulary settings and quiz state follow spec defaults`() {
        val defaults = VocabSettings()
        assertEquals(5, defaults.dailyGoal)
        assertEquals("A1", defaults.preferredLevel)
        assertEquals("Asia/Dhaka", defaults.timezone)
        assertTrue(defaults.autoPronounce)

        val quiz = QuizUiState()
        assertEquals(0, quiz.currentIndex)
        assertNull(quiz.result)
    }

    @Test
    fun `csv helper strips BOM, counts quoted rows accurately, and splits large CSVs into batches`() {
        val raw = "\uFEFFword,meaning,level\r\n" +
            "\"calm\",\"শান্ত,\nনিরব\",A1\r\n" +
            "brave,সাহসী,A1\r\n" +
            "kind,দয়ালু,A1\r\n"
        assertEquals(3, CsvImportHelper.countDataRows(raw))
        val chunks = CsvImportHelper.splitIntoChunks(raw, maxRowsPerChunk = 2)
        assertEquals(2, chunks.size)
        assertEquals(2, CsvImportHelper.countDataRows(chunks[0]))
        assertEquals(1, CsvImportHelper.countDataRows(chunks[1]))
        assertTrue(chunks[1].startsWith("word,meaning,level\n"))
    }
}
