package com.vastutalks.app.data.model

/**
 * Calls with experts are free for now: prices, the wallet and per-call
 * cost are hidden everywhere. Flip this to show them again once real
 * billing exists.
 */
object CallPricing {
    const val PAID_CALLS = false
}

/**
 * Rating, likes and price shown for an expert. There's no rating system
 * yet — these will be calculated from completed calls — so every expert
 * shows 0 for now rather than made-up numbers.
 */
data class ExpertDisplayExtras(
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val likes: Int = 0,
    val pricePerSession: Int = 0
) {
    /** "0" with no ratings, otherwise one decimal ("4.8"). */
    val ratingLabel: String get() = if (rating <= 0.0) "0" else String.format(java.util.Locale.US, "%.1f", rating)
}

private val NO_RATINGS_YET = ExpertDisplayExtras()

/** Until ratings are calculated from calls, every expert has none. */
@Suppress("UNUSED_PARAMETER")
fun expertDisplayExtrasFor(uid: String): ExpertDisplayExtras = NO_RATINGS_YET
