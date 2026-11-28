package com.example.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode(val storageKey: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStorageKey(raw: String?): AppThemeMode {
            return entries.firstOrNull { it.storageKey.equals(raw?.trim(), ignoreCase = true) } ?: SYSTEM
        }
    }
}

object AppThemeManager {
    private const val PREFS_NAME = "english_ai_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    @Volatile
    private var initialized = false
    private val _themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun observeThemeMode(context: Context): StateFlow<AppThemeMode> {
        ensureLoaded(context)
        return themeMode
    }

    fun getThemeMode(context: Context): AppThemeMode {
        ensureLoaded(context)
        return _themeMode.value
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.storageKey).apply()
        initialized = true
        _themeMode.value = mode
    }

    fun toggleLightDark(context: Context, isSystemDark: Boolean) {
        val currentDark = isDark(getThemeMode(context), isSystemDark)
        val next = if (currentDark) AppThemeMode.LIGHT else AppThemeMode.DARK
        setThemeMode(context, next)
    }

    fun isDark(mode: AppThemeMode, isSystemDark: Boolean): Boolean {
        return when (mode) {
            AppThemeMode.SYSTEM -> isSystemDark
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    }

    internal fun resetForTest() {
        initialized = false
        _themeMode.value = AppThemeMode.SYSTEM
    }

    private fun ensureLoaded(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                _themeMode.value = AppThemeMode.fromStorageKey(prefs.getString(KEY_THEME_MODE, null))
                initialized = true
            }
        }
    }
}

@Composable
fun resolveIsDarkTheme(mode: AppThemeMode): Boolean {
    return AppThemeManager.isDark(mode, isSystemInDarkTheme())
}
