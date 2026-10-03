package com.vastutalks.app.data.model

/** Stored at users/{uid}. Firestore needs the no-arg constructor, hence all defaults. */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = UserRole.NORMAL_USER.name,
    val createdAtMillis: Long = 0L,
    /** Set by VastuMessagingService.onNewToken; used by the Cloud Function to actually ring this device. */
    val fcmToken: String? = null
)
