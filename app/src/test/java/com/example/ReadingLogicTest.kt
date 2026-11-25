package com.example

import com.example.domain.model.ReadingCefrLevel
import com.example.domain.model.ReadingQuestionType
import com.example.domain.model.WordMatchStatus
import com.example.feature.reading.ReadingSentenceSplitter
import com.example.feature.reading.audio.AudioProviderConfig
import com.example.feature.reading.audio.AudioStatus
import com.example.feature.reading.audio.TextMatchComparator
import com.example.feature.reading.audio.TextMatchingAssessmentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingLogicTest {

    @Test
    fun `text match comparator returns 100 percent for case and punctuation variations`() {
        val result = TextMatchComparator.compare(
            expectedText = "Good morning, Sarah! How are you today?",
            spokenText = "good morning sarah how are you today"
        )
        assertEquals(7, result.matchedCount)
        assertEquals(100, result.matchPercent)
        assertTrue(result.missingWords.isEmpty())
        assertTrue(result.extraWords.isEmpty())
        assertTrue(result.mismatchedPairs.isEmpty())
    }

    @Test
    fun `text match comparator detects missing, extra, and mismatched words`() {
        val result = TextMatchComparator.compare(
            expectedText = "I walk to the park every morning",
            spokenText = "I run to park every single morning"
        )
        // Expected: I(M) walk->run(MISMATCH) to(M) the(MISSING) park(M) every(M) +(single EXTRA) morning(M)
        assertEquals(5, result.matchedCount)
        assertEquals(listOf("the"), result.missingWords)
        assertEquals(listOf("single"), result.extraWords)
        assertEquals(listOf("walk" to "run"), result.mismatchedPairs)
        assertTrue(result.expectedTokens.any { it.status == WordMatchStatus.MISMATCHED })
        assertTrue(result.spokenTokens.any { it.status == WordMatchStatus.EXTRA })
    }

    @Test
    fun `text match comparator handles empty spoken input safely`() {
        val result = TextMatchComparator.compare("Hello world.", "")
        assertEquals(0, result.matchedCount)
        assertEquals(0, result.matchPercent)
        assertEquals(2, result.missingWords.size)
    }

    @Test
    fun `pronunciation assessment provider uses Text Matching label never pronunciation score`() {
        val provider = TextMatchingAssessmentProvider()
        assertEquals("Text Matching", provider.label)
    }

    @Test
    fun `sentence splitter preserves abbreviations and times while assigning stable IDs`() {
        val sentences = ReadingSentenceSplitter.splitEnglishText(
            "Dr. Rahman wakes up at 7.30 a.m.! He drinks tea. Does he read newspapers?"
        )
        assertEquals(3, sentences.size)
        assertEquals("s_1", sentences[0].sentenceId)
        assertEquals("Dr. Rahman wakes up at 7.30 a.m.!", sentences[0].en)
        assertEquals("s_2", sentences[1].sentenceId)
        assertEquals("He drinks tea.", sentences[1].en)
        assertEquals("s_3", sentences[2].sentenceId)
        assertEquals("Does he read newspapers?", sentences[2].en)
    }

    @Test
    fun `provider config defaults to free local providers`() {
        val cfg = AudioProviderConfig()
        assertEquals(AudioProviderConfig.PROVIDER_ANDROID_TTS, cfg.ttsProviderId)
        assertEquals(AudioProviderConfig.PROVIDER_ANDROID_STT, cfg.sttProviderId)
        assertEquals(AudioProviderConfig.PROVIDER_ANDROID_MEDIA_PLAYER, cfg.playerProviderId)
        assertEquals(AudioProviderConfig.PROVIDER_LOCAL_TEXT_MATCHING, cfg.assessmentProviderId)
    }

    @Test
    fun `reading enums cover all CEFR levels, question types, and audio statuses`() {
        assertEquals(listOf("ALL", "A1", "A2", "B1", "B2", "C1", "C2"), ReadingCefrLevel.entries.map { it.code })
        assertEquals(ReadingQuestionType.TRUE_FALSE, ReadingQuestionType.fromWire("true_false"))
        assertEquals(ReadingQuestionType.SHORT_ANSWER, ReadingQuestionType.fromWire("short_answer"))
        assertEquals(ReadingQuestionType.MCQ, ReadingQuestionType.fromWire("unknown"))
        assertEquals(
            listOf("Ready", "Loading", "Playing", "Paused", "Completed", "Error", "Unsupported"),
            AudioStatus.entries.map { it.label }
        )
    }

    @Test
    fun `android reading viewmodels expose single-arg Application constructor for ViewModelProvider`() {
        val settingsCtor = com.example.feature.reading.ReadingSettingsViewModel::class.java
            .getConstructor(android.app.Application::class.java)
        val readerCtor = com.example.feature.reading.PassageReaderViewModel::class.java
            .getConstructor(android.app.Application::class.java)
        val dashboardCtor = com.example.feature.reading.ReadingDashboardViewModel::class.java
            .getConstructor()
        val adminCtor = com.example.feature.reading.ReadingAdminViewModel::class.java
            .getConstructor()
        assertTrue(settingsCtor != null && readerCtor != null && dashboardCtor != null && adminCtor != null)
    }
}
