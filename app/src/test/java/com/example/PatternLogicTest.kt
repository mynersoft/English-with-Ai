package com.example

import com.example.data.repository.PatternSummary
import com.example.feature.pattern.PatternListUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class PatternLogicTest {

    private fun samplePatterns(): List<PatternSummary> = listOf(
        PatternSummary(
            id = "p1",
            title = "I feel like + V-ing",
            meaning = "আমার কিছু করতে ইচ্ছা করছে",
            categoryName = "Daily Life",
            isPremium = false,
            order = 1,
            sentenceCount = 5,
            audioTotal = 0,
            audioReady = 0,
            titleAudioUrl = null,
            meaningAudioUrl = null
        ),
        PatternSummary(
            id = "p2",
            title = "It takes time to + V1",
            meaning = "কোনো কিছু করতে সময় লাগে",
            categoryName = "Conversation",
            isPremium = false,
            order = 2,
            sentenceCount = 4,
            audioTotal = 0,
            audioReady = 0,
            titleAudioUrl = null,
            meaningAudioUrl = null
        ),
        PatternSummary(
            id = "p3",
            title = "Used to + V1",
            meaning = "অতীতে কোনো কিছু করতাম",
            categoryName = "Daily Life",
            isPremium = true,
            order = 3,
            sentenceCount = 6,
            audioTotal = 0,
            audioReady = 0,
            titleAudioUrl = null,
            meaningAudioUrl = null
        )
    )

    @Test
    fun `pattern summary defaults categoryName to All`() {
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
        assertEquals("All", summary.categoryName)
    }

    @Test
    fun `pattern list state defaults to All category and filters by selectedCategory`() {
        val items = samplePatterns()
        val state = PatternListUiState(isLoading = false, patterns = items)
        assertEquals("All", state.selectedCategory)

        val dailyLife = state.copy(selectedCategory = "Daily Life")
        val filtered = dailyLife.patterns.filter {
            it.categoryName.equals(dailyLife.selectedCategory, ignoreCase = true)
        }
        assertEquals(listOf("p1", "p3"), filtered.map { it.id })
    }
}
