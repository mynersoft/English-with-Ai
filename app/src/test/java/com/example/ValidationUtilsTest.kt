package com.example

import com.example.core.common.ValidationUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ValidationUtilsTest {

    @Test
    fun `validates email formats accurately`() {
        assertTrue(ValidationUtils.isValidEmail("learner@englishai.app"))
        assertFalse(ValidationUtils.isValidEmail("not-an-email"))
        assertFalse(ValidationUtils.isValidEmail(""))
    }

    @Test
    fun `evaluates password strength rules accurately`() {
        val weak = ValidationUtils.evaluatePassword("abc123")
        assertFalse(weak.isStrongEnough)

        val strong = ValidationUtils.evaluatePassword("English2026!")
        assertTrue(strong.isStrongEnough)
        assertEquals(5, strong.score)
        assertEquals("Very Strong", strong.label)
    }

    @Test
    fun `validates display names within length bounds`() {
        assertFalse(ValidationUtils.isValidDisplayName("A"))
        assertTrue(ValidationUtils.isValidDisplayName("Mahir Rahman"))
        assertFalse(ValidationUtils.isValidDisplayName("X".repeat(65)))
    }
}
