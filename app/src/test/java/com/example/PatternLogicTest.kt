package com.example

import com.example.data.repository.PatternSummary
import com.example.feature.pattern.PatternFilter
import com.example.feature.pattern.PatternListUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PatternLogicTest {

    private fun samplePatterns(): List<PatternSummary> = listOf(
        PatternSummary(
            id = "p1",
            title = "I feel like + V-ing",
            meaning = "আমার কিছু করতে ইচ্ছা করছে",
            isPremium = false,
            order = 1,
            sentenceCount = 5,
            audioTotal = 0,
            audioReady = 0,
            titleAudioUrl = null,
            meaningAudioUrl = null,
            isCompleted = true
        ),
        PatternSummary(
            id = "p2",
            title = "It takes time to + V1",
            meaning = "কোনো কিছু করতে সময় লাগে",
            isPremium = false,
            order = 2,
            sentenceCount = 4,
            audioTotal = 0,
            audioReady = 0,
            titleAudioUrl = null,
            meaningAudioUrl = null,
            isCompleted = false
        ),
        PatternSummary(
            id = "p3",
            title = "Used to + V1",
            meaning = "অতীতে কোনো কিছু করতাম",
            isPremium = true,
            order = 3,
            sentenceCount = 6,
            audioTotal = 0,
            audioReady = 0,
            titleAudioUrl = null,
            meaningAudioUrl = null,
            isCompleted = true
        )
    )

    @Test
    fun `pattern summary defaults isCompleted to false`() {
        val summary = PatternSummary(
            id = "p0",
            title = "Test",
            meaning = "Meaning",
            isPremium = false,
            order = 0,
            sentenceCount = 0,
            audioTotal = 0,
            audioReady = 0,
            titleAudioUrl = null,
            meaningAudioUrl = null
        )
        assertFalse(summary.isCompleted)
    }

    @Test
    fun `pattern list state counts and filters learned and unlearned patterns accurately`() {
        val items = samplePatterns()
        val allState = PatternListUiState(isLoading = false, patterns = items, filter = PatternFilter.ALL)
        assertEquals(2, allState.learnedCount)
        assertEquals(1, allState.unlearnedCount)
        assertEquals(listOf("p1", "p2", "p3"), allState.filteredPatterns.map { it.id })

        val learnedOnly = allState.copy(filter = PatternFilter.LEARNED)
        assertEquals(listOf("p1", "p3"), learnedOnly.filteredPatterns.map { it.id })

        val toLearnOnly = allState.copy(filter = PatternFilter.TO_LEARN)
        assertEquals(listOf("p2"), toLearnOnly.filteredPatterns.map { it.id })
    }
}
