package com.vastutalks.app.data.ai

import kotlin.math.roundToInt

/** The eight Vastu directions, clockwise from North. */
enum class CompassDirection(val short: String, val label: String, val vastuName: String) {
    N("N", "North", "Uttar"),
    NE("NE", "North-East", "Ishan"),
    E("E", "East", "Purva"),
    SE("SE", "South-East", "Agneya"),
    S("S", "South", "Dakshin"),
    SW("SW", "South-West", "Nairutya"),
    W("W", "West", "Paschim"),
    NW("NW", "North-West", "Vayavya");

    val degrees: Float get() = ordinal * 45f

    /** The direction [steps] eighth-turns clockwise from this one (negative = anticlockwise). */
    fun turn(steps: Int): CompassDirection = entries[Math.floorMod(ordinal + steps, entries.size)]

    companion object {
        fun of(degrees: Float): CompassDirection =
            entries[Math.floorMod((degrees / 45f).roundToInt(), entries.size)]
    }
}

/**
 * Which way the camera was pointing when a photo was taken, in degrees
 * clockwise from (magnetic) North, and how we know.
 */
data class PhotoFacing(val degrees: Float, val source: Source) {
    enum class Source {
        /** Written into the photo's EXIF by the camera app (GPSImgDirection). */
        PHOTO,
        /** Read from the phone's compass while the caller reviewed the photo. */
        COMPASS,
        /** Picked by the caller. */
        CALLER
    }

    val direction: CompassDirection get() = CompassDirection.of(degrees)

    /** Tells the model where everything in the frame sits, so it doesn't have to work it out. */
    fun describeForAgent(): String {
        val d = direction
        val how = when (source) {
            Source.PHOTO -> "according to the compass reading saved in the photo (about ${degrees.roundToInt()}° from North)"
            Source.COMPASS -> "according to the phone's compass (about ${degrees.roundToInt()}° from North)"
            Source.CALLER -> "according to the caller"
        }
        return "The camera was pointing ${d.label} (${d.vastuName}) $how. So the wall or area straight ahead in the " +
            "photo is on the ${d.label} side of the room, the left edge of the frame is towards ${d.turn(-2).label}, " +
            "the right edge is towards ${d.turn(2).label}, the far-left corner is ${d.turn(-1).label}, the far-right " +
            "corner is ${d.turn(1).label}, and the photographer is standing on the ${d.turn(4).label} side."
    }
}
