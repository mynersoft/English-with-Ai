package com.example

import com.example.core.ui.CelebrationRules
import com.example.core.ui.CelebrationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationLogicTest {

    @Test
    fun `word mastered triggers only when transitioning to known`() {
        assertTrue(CelebrationRules.shouldCelebrateWordMastered(knowsWord = true, wasAlreadyMastered = false))
        assertFalse(CelebrationRules.shouldCelebrateWordMastered(knowsWord = true, wasAlreadyMastered = true))
        assertFalse(CelebrationRules.shouldCelebrateWordMastered(knowsWord = false, wasAlreadyMastered = false))
    }

    @Test
    fun `vocab daily goal triggers once per goal threshold and again if goal is raised`() {
        assertTrue(
            CelebrationRules.shouldCelebrateVocabDailyGoal(
                newWordsDone = 5,
                dailyGoal = 5,
                isGoalCompleted = true,
                wasGoalCompletedBefore = false,
                alreadyCelebratedGoalForDate = null
            )
        )
        assertFalse(
            CelebrationRules.shouldCelebrateVocabDailyGoal(
                newWordsDone = 6,
                dailyGoal = 5,
                isGoalCompleted = true,
                wasGoalCompletedBefore = true,
                alreadyCelebratedGoalForDate = 5
            )
        )
        assertTrue(
            CelebrationRules.shouldCelebrateVocabDailyGoal(
                newWordsDone = 10,
                dailyGoal = 10,
                isGoalCompleted = true,
                wasGoalCompletedBefore = false,
                alreadyCelebratedGoalForDate = 5
            )
        )
    }

    @Test
    fun `reading daily goal triggers when today minutes reach configured goal`() {
        assertFalse(CelebrationRules.shouldCelebrateReadingDailyGoal(14, 15, null))
        assertTrue(CelebrationRules.shouldCelebrateReadingDailyGoal(15, 15, null))
        assertFalse(CelebrationRules.shouldCelebrateReadingDailyGoal(18, 15, 15))
        assertTrue(CelebrationRules.shouldCelebrateReadingDailyGoal(20, 20, 15))
    }

    @Test
    fun `pattern completion celebrates daily goal first then individual completions`() {
        val first = CelebrationRules.resolvePatternCompletionCelebration(
            patternTitle = "I feel like...",
            alreadyCelebratedDailyGoalToday = false
        )
        assertEquals(CelebrationStyle.GOAL_BURST, first.style)
        assertTrue(first.title.contains("Daily Pattern Goal"))

        val second = CelebrationRules.resolvePatternCompletionCelebration(
            patternTitle = "I'm planning to...",
            alreadyCelebratedDailyGoalToday = true
        )
        assertEquals(CelebrationStyle.CONFETTI, second.style)
        assertEquals("Pattern Completed!", second.title)
    }
}
