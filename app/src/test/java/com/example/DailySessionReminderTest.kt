package com.example

import com.example.core.reminder.DailySessionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DailySessionReminderTest {

    @Test
    fun `shows login reminder banner when user has not logged in or dismissed today`() {
        assertTrue(
            DailySessionRules.shouldShowLoginReminderBanner(
                lastLoggedInDate = null,
                dismissedBannerDate = null,
                todayDate = "2026-11-26"
            )
        )
        assertTrue(
            DailySessionRules.shouldShowLoginReminderBanner(
                lastLoggedInDate = "2026-11-25",
                dismissedBannerDate = "2026-11-25",
                todayDate = "2026-11-26"
            )
        )
    }

    @Test
    fun `hides login reminder banner if logged in today or dismissed today`() {
        assertFalse(
            DailySessionRules.shouldShowLoginReminderBanner(
                lastLoggedInDate = "2026-11-26",
                dismissedBannerDate = null,
                todayDate = "2026-11-26"
            )
        )
        assertFalse(
            DailySessionRules.shouldShowLoginReminderBanner(
                lastLoggedInDate = "2026-11-25",
                dismissedBannerDate = "2026-11-26",
                todayDate = "2026-11-26"
            )
        )
    }

    @Test
    fun `fires notification once per day only when not logged in today`() {
        assertTrue(
            DailySessionRules.shouldFireNotificationToday(
                lastLoggedInDate = "2026-11-25",
                lastNotifiedDate = "2026-11-25",
                todayDate = "2026-11-26"
            )
        )
        assertFalse(
            DailySessionRules.shouldFireNotificationToday(
                lastLoggedInDate = "2026-11-26",
                lastNotifiedDate = null,
                todayDate = "2026-11-26"
            )
        )
        assertFalse(
            DailySessionRules.shouldFireNotificationToday(
                lastLoggedInDate = null,
                lastNotifiedDate = "2026-11-26",
                todayDate = "2026-11-26"
            )
        )
    }

    @Test
    fun `nextDailyTriggerMillis schedules today if before 19_00 and tomorrow if after`() {
        val morning = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 26, 9, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val triggerToday = DailySessionRules.nextDailyTriggerMillis(morning, hour = 19, minute = 0)
        val todayCal = Calendar.getInstance().apply { timeInMillis = triggerToday }
        assertEquals(26, todayCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, todayCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, todayCal.get(Calendar.MINUTE))

        val evening = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 26, 20, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val triggerTomorrow = DailySessionRules.nextDailyTriggerMillis(evening, hour = 19, minute = 0)
        val tomorrowCal = Calendar.getInstance().apply { timeInMillis = triggerTomorrow }
        assertEquals(27, tomorrowCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, tomorrowCal.get(Calendar.HOUR_OF_DAY))
    }
}
