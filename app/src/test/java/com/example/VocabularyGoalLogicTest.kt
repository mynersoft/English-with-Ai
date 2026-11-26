package com.example

import com.example.data.repository.VocabularyRepository
import com.example.domain.model.VocabSettings
import com.example.domain.model.VocabWord
import com.example.domain.model.VocabWordProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyGoalLogicTest {

    @Test
    fun `dailyGoal of 5 or more is preserved without capping at 5`() {
        val normalized30 = VocabularyRepository.normalizeVocabSettings(
            VocabSettings(dailyGoal = 30, preferredLevel = "a1")
        )
        assertEquals(30, normalized30.dailyGoal)
        assertEquals("A1", normalized30.preferredLevel)

        val normalized50 = VocabularyRepository.normalizeVocabSettings(
            VocabSettings(dailyGoal = 50, preferredLevel = "all")
        )
        assertEquals(50, normalized50.dailyGoal)
        assertEquals("ALL", normalized50.preferredLevel)
    }

    @Test
    fun `dailyGoal is clamped between 5 and 500 and invalid fields fall back safely`() {
        val belowMin = VocabularyRepository.normalizeVocabSettings(
            VocabSettings(
                dailyGoal = 2,
                preferredLevel = "INVALID",
                preferredCategoryIds = listOf(" cat1 ", "", "cat1", "cat2"),
                reminderTime = "99:99",
                timezone = "  "
            )
        )
        assertEquals(5, belowMin.dailyGoal)
        assertEquals("A1", belowMin.preferredLevel)
        assertEquals(listOf("cat1", "cat2"), belowMin.preferredCategoryIds)
        assertEquals("20:00", belowMin.reminderTime)
        assertEquals("Asia/Dhaka", belowMin.timezone)

        val aboveMax = VocabularyRepository.normalizeVocabSettings(
            VocabSettings(dailyGoal = 999)
        )
        assertEquals(500, aboveMax.dailyGoal)
    }

    @Test
    fun `overflow words beyond remote 5 count toward configured goal up to effectiveGoal`() {
        assertEquals(
            6,
            VocabularyRepository.computeEffectiveDoneCount(
                remoteDone = 5,
                localAllCount = 1,
                overflowCount = 1,
                effectiveGoal = 30
            )
        )
        assertEquals(
            30,
            VocabularyRepository.computeEffectiveDoneCount(
                remoteDone = 5,
                localAllCount = 30,
                overflowCount = 28,
                effectiveGoal = 30
            )
        )
    }

    @Test
    fun `isWordKnownOrLearned excludes known and previously learned words`() {
        val brandNew = VocabWord(
            id = "w1",
            word = "apple",
            meaning = "আপেল",
            progress = VocabWordProgress(status = "new", isKnown = false)
        )
        val knownWord = VocabWord(
            id = "w2",
            word = "school",
            meaning = "বিদ্যালয়",
            progress = VocabWordProgress(status = "mastered", isKnown = true, learnedAt = "2026-11-25")
        )
        val learningWord = VocabWord(
            id = "w3",
            word = "afraid",
            meaning = "ভীত",
            progress = VocabWordProgress(status = "learning", reviewCount = 1, wrongCount = 1)
        )
        assertFalse(VocabularyRepository.isWordKnownOrLearned(brandNew))
        assertTrue(VocabularyRepository.isWordKnownOrLearned(knownWord))
        assertTrue(VocabularyRepository.isWordKnownOrLearned(learningWord))
    }
}
