package com.vastutalks.app.data.model

/**
 * One completed (or ended) call, logged to Firestore right after the
 * user leaves LiveInCallScreen (i.e. the call ends). Needs a no-arg constructor with defaults
 * for Firestore's automatic deserialization (toObject), which is why
 * every field has a default value.
 */
data class CallHistoryEntry(
    val id: String = "",
    val expertId: String = "",
    val expertName: String = "",
    val callType: String = "", // "AUDIO" or "VIDEO" — CallType.name
    val startTimeMillis: Long = 0L,
    val durationSeconds: Int = 0,
    val cost: Int = 0,
    val channelName: String = ""
)
