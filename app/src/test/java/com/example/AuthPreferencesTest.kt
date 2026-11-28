package com.example

import com.example.feature.auth.AuthPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthPreferencesTest {

    @Test
    fun normalizeEmail_trimsValidEmailsAndRejectsBlankOrInvalid() {
        assertEquals("student@example.com", AuthPreferences.normalizeEmail("  student@example.com  "))
        assertEquals("", AuthPreferences.normalizeEmail(null))
        assertEquals("", AuthPreferences.normalizeEmail("   "))
        assertEquals("", AuthPreferences.normalizeEmail("not-an-email"))
    }
}
