package com.vastutalks.app.data.repository

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Profile photos (and post pictures) are stored as small JPEGs right on the Firestore
 * profile (users/{uid}.photo, and experts/{uid}.photo so the directory
 * can show them) instead of in Firebase Storage, which isn't set up for
 * this project. Cropped square and kept to ~[SIZE] px so each one is
 * roughly 20–40 KB — far under Firestore's 1 MB document limit.
 */
object ProfilePhotos {
    private const val SIZE = 384

    fun encode(bitmap: Bitmap): String {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * A post's picture: kept at its own shape (not cropped), at most
     * [POST_IMAGE_SIZE] px on the long side, ~60–150 KB.
     */
    fun encodePostImage(bitmap: Bitmap): String {
        val scale = minOf(1f, POST_IMAGE_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private const val POST_IMAGE_SIZE = 960

    /** The JPEG bytes of a stored photo, or null if there isn't one (Coil can load the bytes directly). */
    fun decode(photo: String): ByteArray? =
        photo.takeIf { it.isNotBlank() }?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
}
