package com.example.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val platform: String = "Android",
    val authProvider: String = "password",
    val activeSession: Boolean = true,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)
