package com.example

import com.example.data.repository.LocalSpeakingStats
import com.example.data.repository.StudentDashboardRepository
import com.example.domain.model.CourseReportData
import com.example.domain.model.CourseReportModule
import com.example.domain.model.StudentDashboardData
import com.example.domain.model.WeeklyActivityDay
import com.example.feature.dashboard.AppMainTab
import com.example.feature.speaking.combineDraftAndSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentDashboardTest {

    @Test
    fun `bottom navigation exposes all 6 features in English`() {
        val labels = AppMainTab.entries.map { it.label }
        assertEquals(listOf("Home", "Vocab", "Patterns", "Speak", "Chat", "Progress"), labels)
    }

    @Test
    fun `default student dashboard models have safe zeroed defaults`() {
        val data = StudentDashboardData()
        assertEquals("Free", data.user.planLabel)
        assertEquals(0, data.stats.streakDays)
        assertEquals(0, data.stats.speakingMinutes)
        assertEquals(0, data.stats.readingPassagesCompleted)
        assertEquals(0, data.stats.readingMinutes)
        assertEquals(0, data.readingReport.completedPassages)
        assertEquals(15, data.readingReport.dailyGoalMinutes)
        assertEquals(0, data.courseReport.overallPercent)
        assertTrue(data.weeklyActivity.isEmpty())
    }

    @Test
    fun `speaking progress minutes and merge update AI speaking report`() {
        assertEquals(0, LocalSpeakingStats.computeSpeakingMinutes(0L))
        assertEquals(1, LocalSpeakingStats.computeSpeakingMinutes(15L))
        assertEquals(2, LocalSpeakingStats.computeSpeakingMinutes(125L))

        val base = StudentDashboardData(
            weeklyActivity = listOf(
                WeeklyActivityDay(
                    date = "2026-11-25",
                    dayNumber = "25",
                    dayShort = "Wed",
                    isToday = true,
                    patternsActive = false,
                    speakingActive = false,
                    vocabularyActive = true,
                    readingActive = false,
                    practiceActive = false
                )
            ),
            courseReport = CourseReportData(
                overallPercent = 0,
                modules = listOf(
                    CourseReportModule(
                        key = "speaking",
                        title = "AI Speaking",
                        percent = 0,
                        summary = "0 speaking turns & sessions completed",
                        route = "speaking"
                    )
                )
            )
        )

        val merged = StudentDashboardRepository.mergeSpeakingProgress(
            data = base,
            local = LocalSpeakingStats(
                turns = 4,
                sessions = 1,
                totalSeconds = 180L,
                activeDates = setOf("2026-11-25")
            )
        )

        assertEquals(1, merged.stats.speakingSessions)
        assertEquals(3, merged.stats.speakingMinutes)
        assertTrue(merged.weeklyActivity.first().speakingActive)
        val speakingMod = merged.courseReport.modules.first { it.key == "speaking" }
        assertTrue(speakingMod.percent > 0)
        assertTrue(speakingMod.summary.contains("3 min talked with AI"))
        assertTrue(speakingMod.summary.contains("4 turns"))
    }

    @Test
    fun `combineDraftAndSpeech merges existing draft with recognized speech`() {
        assertEquals("hello world", combineDraftAndSpeech("", "hello world"))
        assertEquals("I want to practice", combineDraftAndSpeech("I want to", "practice"))
    }
}
