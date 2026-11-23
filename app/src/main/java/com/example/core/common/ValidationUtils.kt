package com.example.core.common

import android.os.Build
import android.util.Patterns
import java.security.MessageDigest

data class PasswordStrength(
    val hasMinLength: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasDigit: Boolean,
    val hasSpecialChar: Boolean
) {
    val score: Int
        get() = listOf(hasMinLength, hasUppercase, hasLowercase, hasDigit, hasSpecialChar).count { it }

    val isStrongEnough: Boolean
        get() = hasMinLength && hasUppercase && hasLowercase && hasDigit

    val label: String
        get() = when (score) {
            0, 1, 2 -> "Weak"
            3 -> "Fair"
            4 -> "Strong"
            else -> "Very Strong"
        }
}

object ValidationUtils {
    const val MIN_NAME_LENGTH = 2
    const val MAX_NAME_LENGTH = 60
    const val MAX_EMAIL_LENGTH = 254

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.length in 3..MAX_EMAIL_LENGTH &&
            Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
    }

    fun isValidDisplayName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed.length in MIN_NAME_LENGTH..MAX_NAME_LENGTH
    }

    fun evaluatePassword(password: String): PasswordStrength {
        return PasswordStrength(
            hasMinLength = password.length >= 8,
            hasUppercase = password.any { it.isUpperCase() },
            hasLowercase = password.any { it.isLowerCase() },
            hasDigit = password.any { it.isDigit() },
            hasSpecialChar = password.any { !it.isLetterOrDigit() }
        )
    }

    fun currentDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val base = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }.ifBlank { "Android Device" }
        return "$base (Android ${Build.VERSION.RELEASE})".take(100)
    }

    fun currentDeviceSessionId(): String {
        val raw = "${Build.BRAND}-${Build.MODEL}-${Build.HARDWARE}-${Build.FINGERPRINT}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        val hex = digest.take(12).joinToString("") { "%02x".format(it) }
        return "dev_$hex"
    }
}
