package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class LearningLevel(val displayName: String, val subtitle: String) {
    Beginner("Beginner", "Foundations, simple words & Bangla guidance"),
    Intermediate("Intermediate", "Everyday speaking, grammar & fluency"),
    Advanced("Advanced", "Workplace, interviews & natural expression");

    companion object {
        fun fromRaw(raw: String?): LearningLevel =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Beginner
    }
}

enum class AccountStatus {
    ACTIVE,
    DEACTIVATED;

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ACTIVE
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val authProvider: String = "password",
    val emailVerified: Boolean = false,
    val learningLevel: String = LearningLevel.Beginner.name,
    val dailyGoalMinutes: Long = 20L,
    val banglaExplanations: Boolean = true,
    val accountStatus: String = AccountStatus.ACTIVE.name,
    val currentDay: Long = 1L,
    val streakDays: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val safeLevel: LearningLevel get() = LearningLevel.fromRaw(learningLevel)
    val safeAccountStatus: AccountStatus get() = AccountStatus.fromRaw(accountStatus)
}
