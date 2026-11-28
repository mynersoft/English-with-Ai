package com.example.feature.auth

import android.content.Context

object AuthPreferences {
    private const val PREFS_NAME = "english_ai_auth_prefs"
    private const val KEY_LAST_LOGIN_EMAIL = "last_login_email"

    internal fun normalizeEmail(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return if (trimmed.contains("@") && trimmed.contains(".")) trimmed else ""
    }

    fun saveLastLoginEmail(context: Context, email: String?) {
        val clean = normalizeEmail(email)
        if (clean.isEmpty()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_LOGIN_EMAIL, clean)
            .apply()
    }

    fun getLastLoginEmail(context: Context): String {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_LOGIN_EMAIL, "")
        return normalizeEmail(stored)
    }
}
