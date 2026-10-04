package com.vastutalks.app.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.vastutalks.app.data.ai.CompassDirection
import com.vastutalks.app.data.ai.PhotoFacing
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Returns a function that opens the camera and hands back the photo,
 * full-size (via a FileProvider, not the tiny preview thumbnail), with
 * EXIF rotation applied and scaled to at most [maxSide] px.
 *
 * Also hands back the compass heading the camera app saved in the photo
 * (EXIF GPSImgDirection), when it saved one — many don't.
 *
 * Asks for CAMERA permission first — the app declares it for video
 * calls, and once declared, Android refuses camera intents without it.
 */
@Composable
fun rememberPhotoCapture(
    maxSide: Int = 1600,
    onError: (String) -> Unit,
    onCaptured: (CapturedPhoto) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingUri ?: return@rememberLauncherForActivityResult
        pendingUri = null
        if (!saved) return@rememberLauncherForActivityResult // user backed out of the camera
        scope.launch {
            val photo = withContext(Dispatchers.IO) { runCatching { loadPhoto(context, uri, maxSide) }.getOrNull() }
            if (photo != null) currentOnCaptured(photo) else currentOnError("Couldn't read that photo — try again.")
        }
    }

    fun openCamera() {
        val dir = File(context.cacheDir, "call_photos").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        pendingUri = uri
        try {
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingUri = null
            currentOnError("No camera app found on this device.")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera() else currentOnError("Camera access is needed to take a photo.")
    }

    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

/** A photo from the camera, plus the compass heading it was taken at if the camera app recorded one. */
class CapturedPhoto(val bitmap: Bitmap, val exifHeading: Float?)

/** Decodes without loading the full 12 MP image into memory, then fixes camera rotation. */
private fun loadPhoto(context: Context, uri: Uri, maxSide: Int): CapturedPhoto? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null

    val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) }
    val rotation = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val heading = exif?.getAttributeDouble(ExifInterface.TAG_GPS_IMG_DIRECTION, -1.0)
        ?.takeIf { it in 0.0..360.0 }?.toFloat()

    val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
    if (rotation == 0f && scale == 1f) return CapturedPhoto(decoded, heading)
    val matrix = Matrix().apply { postRotate(rotation); postScale(scale, scale) }
    return CapturedPhoto(Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true), heading)
}

/**
 * Full-screen preview of a just-taken photo: the caller can add an
 * optional note ("bedroom"), retake it, or send it.
 *
 * Also settles which way the camera was facing, so the agent can tell
 * which direction each thing in the photo is in: the heading saved in
 * the photo if there is one, otherwise the live compass (the caller is
 * asked to point the phone the way they took the photo), and the caller
 * can always tap a direction to set it by hand.
 */
@Composable
fun PhotoReviewSheet(
    photo: CapturedPhoto,
    agentName: String,
    onRetake: () -> Unit,
    onDismiss: () -> Unit,
    onSend: (question: String, facing: PhotoFacing?) -> Unit
) {
    var question by remember { mutableStateOf("") }
    val image = remember(photo) { photo.bitmap.asImageBitmap() }
    val liveHeading by rememberCameraHeading()
    var picked by remember(photo) { mutableStateOf<CompassDirection?>(null) }
    val facing = when {
        picked != null -> PhotoFacing(picked!!.degrees, PhotoFacing.Source.CALLER)
        photo.exifHeading != null -> PhotoFacing(photo.exifHeading, PhotoFacing.Source.PHOTO)
        liveHeading != null -> PhotoFacing(liveHeading!!, PhotoFacing.Source.COMPASS)
        else -> null
    }
    fun send() = onSend(question.trim(), facing)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallTones.Ink.copy(alpha = 0.97f))
            // Swallow touches so nothing behind the sheet (like the board) reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ask about this space", color = CallTones.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$agentName will suggest Vastu improvements",
                        color = CallTones.TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CallTones.Surface)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Discard photo", tint = CallTones.TextPrimary, modifier = Modifier.size(18.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CallTones.Surface),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = image,
                    contentDescription = "Photo to send",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            FacingPicker(
                facing = facing,
                onPick = { picked = if (picked == it) null else it } // tap again to go back to automatic
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(CallTones.Surface)
                    .border(1.dp, CallTones.Hairline, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (question.isEmpty()) {
                    Text(
                        "Add a note (optional) — e.g. “bedroom, door faces east”",
                        color = CallTones.TextMuted,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = question,
                    onValueChange = { question = it },
                    textStyle = TextStyle(color = CallTones.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(VastuPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .border(1.dp, CallTones.Hairline, RoundedCornerShape(26.dp))
                        .background(CallTones.Surface)
                        .clickable { onRetake() }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Retake", color = CallTones.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Box(
                    modifier = Modifier
                        .weight(1.6f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(VastuPrimary)
                        .clickable { send() }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Ask $agentName", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** "Camera facing East" plus a row of the eight directions to correct it. */
@Composable
private fun FacingPicker(facing: PhotoFacing?, onPick: (CompassDirection) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            text = facing?.let { "Camera facing ${it.direction.label} (${it.direction.vastuName})" } ?: "Which way was the camera facing?",
            color = CallTones.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = when (facing?.source) {
                PhotoFacing.Source.PHOTO -> "From the compass reading saved in the photo · tap to change"
                PhotoFacing.Source.COMPASS -> "Live compass — point your phone the way you took the photo, or tap a direction"
                PhotoFacing.Source.CALLER -> "Set by you · tap it again to use the compass"
                null -> "No compass on this phone — tap a direction"
            },
            color = CallTones.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompassDirection.entries.forEach { direction ->
                val selected = facing?.direction == direction
                val manual = selected && facing?.source == PhotoFacing.Source.CALLER
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                manual -> VastuPrimary
                                selected -> VastuPrimary.copy(alpha = 0.35f)
                                else -> CallTones.Surface
                            }
                        )
                        .border(1.dp, CallTones.Hairline, RoundedCornerShape(12.dp))
                        .clickable { onPick(direction) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        direction.short,
                        color = if (selected) Color.White else CallTones.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
