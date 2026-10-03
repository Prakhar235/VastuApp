package com.vastutalks.app.data.model

/**
 * Real expert signups only capture name/email (see ExpertProfile) —
 * there's no specialty/bio/photo/pricing collection step yet. Until
 * that's built, every expert cycles through one of these hardcoded
 * "profile templates" (matching the values from the provided design
 * mockups) so the UI has something to show. The expert's real name
 * and uid are always used on top of this — only these presentational
 * details are placeholders.
 */
data class ExpertDisplayExtras(
    val specialty: String,
    val experienceYears: Int,
    val location: String,
    val rating: Double,
    val reviewCount: Int,
    val pricePerSession: Int,
    val avatarSeed: String,
    val bio: String
)

private val templates = listOf(
    ExpertDisplayExtras(
        specialty = "Residential Vastu",
        experienceYears = 15,
        location = "Mumbai",
        rating = 4.9,
        reviewCount = 328,
        pricePerSession = 1200,
        avatarSeed = "template-1",
        bio = "Certified Vastu expert specializing in residential spaces, helping families create balanced, harmonious homes."
    ),
    ExpertDisplayExtras(
        specialty = "Commercial Vastu",
        experienceYears = 12,
        location = "Delhi",
        rating = 4.8,
        reviewCount = 256,
        pricePerSession = 1500,
        avatarSeed = "template-2",
        bio = "Certified Vastu expert specializing in commercial vastu. With years of experience helping clients create harmonious spaces."
    ),
    ExpertDisplayExtras(
        specialty = "Spiritual Consultation",
        experienceYears = 20,
        location = "Bangalore",
        rating = 4.9,
        reviewCount = 412,
        pricePerSession = 2000,
        avatarSeed = "template-3",
        bio = "Certified Vastu expert blending traditional spiritual consultation with modern space-planning guidance."
    ),
    ExpertDisplayExtras(
        specialty = "Interior Vastu",
        experienceYears = 9,
        location = "Pune",
        rating = 4.7,
        reviewCount = 143,
        pricePerSession = 999,
        avatarSeed = "template-4",
        bio = "Certified Vastu expert focused on interior layouts, furniture placement, and room-by-room energy flow."
    )
)

/** Deterministic per-expert placeholder profile — same expert always gets the same template. */
fun expertDisplayExtrasFor(uid: String): ExpertDisplayExtras {
    val index = (uid.hashCode().let { if (it < 0) -it else it }) % templates.size
    return templates[index]
}
