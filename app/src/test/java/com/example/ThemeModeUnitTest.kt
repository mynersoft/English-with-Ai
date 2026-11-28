package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.AppThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeModeUnitTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("english_ai_theme_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AppThemeManager.resetForTest()
    }

    @After
    fun tearDown() {
        AppThemeManager.resetForTest()
    }

    @Test
    fun defaultThemeModeIsSystem() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeManager.getThemeMode(context))
        assertEquals(AppThemeMode.SYSTEM, AppThemeManager.observeThemeMode(context).value)
    }

    @Test
    fun setThemeModePersistsAcrossReload() {
        AppThemeManager.setThemeMode(context, AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, AppThemeManager.themeMode.value)

        // Simulate fresh process reload
        AppThemeManager.resetForTest()
        assertEquals(AppThemeMode.DARK, AppThemeManager.getThemeMode(context))

        AppThemeManager.setThemeMode(context, AppThemeMode.LIGHT)
        AppThemeManager.resetForTest()
        assertEquals(AppThemeMode.LIGHT, AppThemeManager.getThemeMode(context))
    }

    @Test
    fun toggleLightDarkSwitchesBasedOnResolvedState() {
        // Starting from SYSTEM with light system -> toggles to DARK -> then LIGHT
        AppThemeManager.toggleLightDark(context, isSystemDark = false)
        assertEquals(AppThemeMode.DARK, AppThemeManager.getThemeMode(context))

        AppThemeManager.toggleLightDark(context, isSystemDark = false)
        assertEquals(AppThemeMode.LIGHT, AppThemeManager.getThemeMode(context))

        // Reset to SYSTEM with dark system -> toggles to LIGHT -> then DARK
        AppThemeManager.setThemeMode(context, AppThemeMode.SYSTEM)
        AppThemeManager.toggleLightDark(context, isSystemDark = true)
        assertEquals(AppThemeMode.LIGHT, AppThemeManager.getThemeMode(context))

        AppThemeManager.toggleLightDark(context, isSystemDark = true)
        assertEquals(AppThemeMode.DARK, AppThemeManager.getThemeMode(context))
    }

    @Test
    fun isDarkResolvesAllModesCorrectly() {
        assertFalse(AppThemeManager.isDark(AppThemeMode.SYSTEM, isSystemDark = false))
        assertTrue(AppThemeManager.isDark(AppThemeMode.SYSTEM, isSystemDark = true))
        assertFalse(AppThemeManager.isDark(AppThemeMode.LIGHT, isSystemDark = true))
        assertFalse(AppThemeManager.isDark(AppThemeMode.LIGHT, isSystemDark = false))
        assertTrue(AppThemeManager.isDark(AppThemeMode.DARK, isSystemDark = false))
        assertTrue(AppThemeManager.isDark(AppThemeMode.DARK, isSystemDark = true))
    }

    @Test
    fun fromStorageKeyNormalizesAndFallsBackToSystem() {
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStorageKey("dark"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStorageKey(" LIGHT "))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey("system"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStorageKey("unknown_value"))
    }
}
