package com.vastutalks.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.data.repository.ProfilePhotos
import com.vastutalks.app.ui.theme.VastuPrimary

/**
 * A person's profile photo (stored as a base64 JPEG — see ProfilePhotos),
 * or their initials on the app colour when they have none.
 */
@Composable
fun ProfileAvatar(
    photo: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    val image = remember(photo) {
        ProfilePhotos.decode(photo)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    AvatarImage(image, name, size, modifier, shape)
}

@Composable
private fun AvatarImage(image: ImageBitmap?, name: String, size: Dp, modifier: Modifier, shape: Shape) {
    Box(
        modifier = modifier.size(size).clip(shape).background(VastuPrimary),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(image, contentDescription = "Photo of $name", contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(
                initials(name),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp
            )
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("").ifEmpty { "?" }

/**
 * An avatar with a camera badge: tapping it asks "Take a photo" or
 * "Choose from gallery" and hands back the picture. Shows [picked] if
 * set (a photo just chosen, not saved yet), otherwise the stored [photo].
 */
@Composable
fun ProfilePhotoEditor(
    photo: String,
    name: String,
    size: Dp,
    onPicked: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
    picked: Bitmap? = null,
    shape: Shape = CircleShape
) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    val toast: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    val takePhoto = rememberPhotoCapture(onError = toast, onCaptured = { onPicked(it.bitmap) })
    val pickPhoto = rememberPhotoPicker(onError = toast, onPicked = { onPicked(it.bitmap) })

    Box(modifier = modifier.clickable { choosing = true }) {
        if (picked != null) {
            val image = remember(picked) { picked.asImageBitmap() }
            AvatarImage(image, name, size, Modifier, shape)
        } else {
            ProfileAvatar(photo, name, size, shape = shape)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size((size.value * 0.34f).coerceAtLeast(24f).dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, VastuPrimary.copy(alpha = 0.25f), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "Change photo", tint = VastuPrimary)
        }
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Profile photo") },
            text = { Text("Take a new photo or choose one from your gallery.") },
            confirmButton = {
                TextButton(onClick = { choosing = false; pickPhoto() }) { Text("Choose from gallery") }
            },
            dismissButton = {
                TextButton(onClick = { choosing = false; takePhoto() }) { Text("Take a photo") }
            }
        )
    }
}
