package com.example

import com.example.feature.admin.AdminStudentRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AdminStudentsLogicTest {

    @Test
    fun accountAndSubscriptionOptionsContainExpectedValues() {
        assertEquals(listOf("active", "suspended", "deactivated"), AdminStudentRules.ACCOUNT_STATUS_OPTIONS)
        assertEquals(listOf("free", "trial", "active", "expired", "cancelled"), AdminStudentRules.SUBSCRIPTION_STATUS_OPTIONS)
        assertEquals(listOf("free", "monthly", "yearly", "lifetime"), AdminStudentRules.SUBSCRIPTION_PLAN_OPTIONS)
    }

    @Test
    fun statusNormalizationAndShortDateFormattingAreClean() {
        assertEquals("Suspended", AdminStudentRules.normalizeStatusLabel("suspended"))
        assertEquals("Active", AdminStudentRules.normalizeStatusLabel("active"))
        assertEquals("Yearly", AdminStudentRules.normalizeStatusLabel("yearly"))
        assertEquals("2026-11-27", AdminStudentRules.formatShortDate("2026-11-27T08:00:00.000Z"))
        assertEquals("Never", AdminStudentRules.formatShortDate(null))
    }

    @Test
    fun addDaysIsoProducesValidUtcTimestampOffset() {
        val base = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val plus30 = AdminStudentRules.addDaysIso(base, 30)
        assertTrue(plus30.startsWith("2026-12-01T00:00:00Z"))
    }
}
