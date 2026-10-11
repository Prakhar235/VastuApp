package com.vastutalks.app.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.repository.PostRepository
import com.vastutalks.app.data.repository.UserRepository
import com.vastutalks.app.ui.components.GradientButton
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.ui.components.rememberPhotoCapture
import com.vastutalks.app.ui.components.rememberPhotoPicker
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.InputFill
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextHint
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.launch

private const val MAX_POST_LENGTH = 1500

/**
 * Where a Vastu expert writes a post for every user's Posts tab: some
 * text and, optionally, a picture from the camera or gallery.
 */
@Composable
fun CreatePostScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val toast: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    val takePhoto = rememberPhotoCapture(onError = toast, onCaptured = { image = it.bitmap })
    val pickPhoto = rememberPhotoPicker(onError = toast, onPicked = { image = it.bitmap })
    val me = FirebaseAuth.getInstance().currentUser

    fun publish() {
        val uid = me?.uid ?: return
        posting = true
        error = null
        scope.launch {
            val author = UserRepository().getExpert(uid).getOrNull()
            val result = if (author == null) Result.failure(IllegalStateException("Only Vastu experts can post."))
            else PostRepository().create(author, text, image)
            posting = false
            result.fold(
                onSuccess = { toast("Posted"); onDone() },
                onFailure = { error = "Couldn't post: ${it.message}" }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDone, enabled = !posting) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color(0xFF2B2B2B))
            }
            Text("New post", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                ProfileAvatar("", me?.displayName.orEmpty(), size = 36.dp)
                Text(
                    "Share a Vastu tip or update with everyone",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_POST_LENGTH) },
                placeholder = { Text("e.g. Keep the North-East corner of your home light and clutter-free…", color = TextHint) },
                textStyle = TextStyle(color = Color(0xFF2B2B2B), fontSize = 15.sp, lineHeight = 22.sp),
                minLines = 6,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = InputFill,
                    focusedBorderColor = VastuPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${text.length}/$MAX_POST_LENGTH",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
            )

            image?.let { picked ->
                Box(modifier = Modifier.padding(top = 12.dp)) {
                    Image(
                        remember(picked) { picked.asImageBitmap() },
                        contentDescription = "Picture for the post",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { image = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove picture", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Row(modifier = Modifier.padding(top = 12.dp)) {
                AttachButton(Icons.Outlined.PhotoCamera, "Take photo", onClick = takePhoto)
                Box(modifier = Modifier.size(10.dp))
                AttachButton(Icons.Outlined.PhotoLibrary, "Gallery", onClick = pickPhoto)
            }

            error?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
        }

        GradientButton(
            text = "Post",
            enabled = text.isNotBlank() && !posting,
            isLoading = posting,
            onClick = ::publish,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun AttachButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = VastuPrimary, modifier = Modifier.size(18.dp))
        Text(" $label", color = VastuPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
