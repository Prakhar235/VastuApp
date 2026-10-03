package com.vastutalks.app.data.model

/**
 * Stored at experts/{uid} — created only for accounts that signed up
 * as a Vastu Expert. This is what powers the live expert directory
 * normal users see. It only carries what a real signup actually
 * collects today (name/email); see ExpertDisplayExtras.kt for the
 * hardcoded specialty/bio/pricing/photo shown alongside it until a
 * real profile-editing flow exists.
 */
data class ExpertProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val isOnline: Boolean = true,
    val createdAtMillis: Long = 0L,
    /** Total video + audio calls this expert has completed — bumped in VastuNavGraph when a call they were on ends. */
    val consultationsCompleted: Long = 0L
)
