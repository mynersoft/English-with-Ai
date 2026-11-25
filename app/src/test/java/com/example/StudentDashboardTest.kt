package com.example

import com.example.domain.model.StudentDashboardData
import com.example.feature.dashboard.AppMainTab
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
        assertEquals(0, data.stats.readingPassagesCompleted)
        assertEquals(0, data.stats.readingMinutes)
        assertEquals(0, data.readingReport.completedPassages)
        assertEquals(15, data.readingReport.dailyGoalMinutes)
        assertEquals(0, data.courseReport.overallPercent)
        assertTrue(data.weeklyActivity.isEmpty())
    }
}
