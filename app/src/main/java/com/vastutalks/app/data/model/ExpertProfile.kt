package com.vastutalks.app.data.model

import com.google.firebase.firestore.Exclude

/**
 * Stored at experts/{uid} — created only for accounts that signed up
 * as a Vastu Expert. This is what powers the live expert directory
 * normal users see, so it's readable by every signed-in user (the
 * expert's phone number stays private in users/{uid}).
 *
 * Accounts made before the sign-up form asked for specialty,
 * experience, city, bio and photo have those blank; the UI shows
 * neutral placeholders for them.
 */
data class ExpertProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    /** Profile photo as a small base64 JPEG (see ProfilePhotos); blank if none. */
    val photo: String = "",
    val specialty: String = "",
    val experienceYears: Int = 0,
    val city: String = "",
    val bio: String = "",
    /**
     * The expert's online/offline switch on their Home — whether they
     * *want* calls. Whether they can actually take one also needs their
     * app to be running: see [isPresentAt]. Firestore maps a Kotlin
     * `isOnline` property to the field "online" (bean naming), so writes
     * must use that name — see UserRepository.setOnline.
     */
    val isOnline: Boolean = true,
    /**
     * Last check-in from the expert's app (every minute while it's running
     * — see HomeViewModel). Accounts that never checked in have 0, so they
     * show as offline.
     */
    val lastSeenMillis: Long = 0L,
    val createdAtMillis: Long = 0L,
    /** Total video + audio calls this expert has completed — bumped in VastuNavGraph when a call they were on ends. */
    val consultationsCompleted: Long = 0L,
    /** True while the expert is on a call (set from their device), so others see "busy" instead of ringing them. */
    val inCall: Boolean = false,
    /** When [inCall] was last set, so a flag left behind by a crashed app doesn't keep them busy forever. */
    val inCallSinceMillis: Long = 0L
) {
    /** Switched on and their app checked in within [PRESENCE_TIMEOUT_MILLIS] of [now]. */
    fun isPresentAt(now: Long): Boolean = isOnline && now - lastSeenMillis < PRESENCE_TIMEOUT_MILLIS

    /** On a call at [now] (ignoring a stale flag more than [MAX_CALL_MILLIS] old). */
    fun isBusyAt(now: Long): Boolean = inCall && now - inCallSinceMillis < MAX_CALL_MILLIS

    /** Can be called at [now]. */
    fun isAvailableAt(now: Long): Boolean = isPresentAt(now) && !isBusyAt(now)

    @get:Exclude
    val displaySpecialty: String get() = specialty.ifBlank { "Vastu Expert" }

    @get:Exclude
    val displayBio: String get() = bio.ifBlank { "$name hasn't added an introduction yet." }

    /** "New" until they've done a consultation; there are no real ratings yet, so none are shown. */
    @get:Exclude
    val consultationsLabel: String get() = when (consultationsCompleted) {
        0L -> "New"
        1L -> "1 consultation"
        else -> "$consultationsCompleted consultations"
    }

    companion object {
        const val MAX_CALL_MILLIS = 2 * 60 * 60 * 1000L
        /** How often an expert's app checks in. */
        const val HEARTBEAT_MILLIS = 60 * 1000L
        /** Missed about three check-ins → shown as offline. */
        const val PRESENCE_TIMEOUT_MILLIS = 3 * 60 * 1000L
    }
}

/** Callable experts first, then busy, then offline; alphabetical within each. */
fun List<ExpertProfile>.sortedForDirectory(now: Long): List<ExpertProfile> =
    sortedWith(compareBy({ !it.isPresentAt(now) }, { it.isBusyAt(now) }, { it.name.lowercase() }))
