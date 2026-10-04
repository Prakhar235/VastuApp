package com.vastutalks.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.vastutalks.app.data.ai.PhotoFacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A short clip from the camera: the video file itself (to keep with the
 * call) and a handful of frames spread across it — the AI model can't
 * watch video, so it's shown these instead, in order.
 */
class CapturedVideo(val file: File, val frames: List<Bitmap>, val durationMs: Long)

/**
 * Returns a function that opens the camera in video mode (capped at
 * [maxSeconds]) and hands back the clip with up to [maxFrames] frames
 * pulled out of it, each scaled to at most [frameMaxSide] px.
 */
@Composable
fun rememberVideoCapture(
    maxSeconds: Int = 30,
    maxFrames: Int = 8,
    frameMaxSide: Int = 1024,
    onError: (String) -> Unit,
    onCaptured: (CapturedVideo) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(CaptureShortVideo(maxSeconds)) { saved ->
        val file = pendingFile ?: return@rememberLauncherForActivityResult
        pendingFile = null
        if (!saved || file.length() == 0L) { // user backed out of the camera
            file.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val video = withContext(Dispatchers.IO) {
                runCatching { loadVideo(file, maxFrames, frameMaxSide) }.getOrNull()
            }
            if (video != null) {
                currentOnCaptured(video)
            } else {
                file.delete()
                currentOnError("Couldn't read that video — try again.")
            }
        }
    }

    fun openCamera() {
        val dir = File(context.cacheDir, "call_photos").apply { mkdirs() }
        val file = File(dir, "video_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        pendingFile = file
        try {
            cameraLauncher.launch(uri)
        } catch (e: ActivityNotFoundException) {
            pendingFile = null
            currentOnError("No camera app found on this device.")
        }
    }

    return rememberCameraPermissionGate(
        onDenied = { currentOnError("Camera access is needed to record a video.") },
        open = ::openCamera
    )
}

/** The standard video capture, with a length limit so clips stay quick to process. */
private class CaptureShortVideo(private val maxSeconds: Int) : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).putExtra(MediaStore.EXTRA_DURATION_LIMIT, maxSeconds)
}

/** Pulls evenly spaced frames: about one every 3 seconds, at least 3 and at most [maxFrames]. */
private fun loadVideo(file: File, maxFrames: Int, maxSide: Int): CapturedVideo? {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
        val count = (durationMs / 3_000L + 1).toInt().coerceIn(3, maxFrames)
        val frames = (0 until count).mapNotNull { i ->
            // Sample the middle of each slice, so we skip the shaky first and last moments.
            val atUs = (durationMs * 1000L) * (2 * i + 1) / (2 * count)
            retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST)?.let { scaleDown(it, maxSide) }
        }
        return if (frames.isEmpty()) null else CapturedVideo(file, frames, durationMs)
    } finally {
        retriever.release()
    }
}

private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
    val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
    if (scale >= 1f) return bitmap
    return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        .also { bitmap.recycle() }
}

/** Review sheet for a just-recorded video: browse its frames, add a note, set the starting direction. */
@Composable
fun VideoReviewSheet(
    video: CapturedVideo,
    agentName: String,
    onRetake: () -> Unit,
    onDismiss: () -> Unit,
    onSend: (question: String, facing: PhotoFacing?) -> Unit
) = CaptureReviewSheet(
    frames = video.frames,
    exifHeading = null, // video files don't carry a compass heading
    isVideo = true,
    agentName = agentName,
    onRetake = onRetake,
    onDismiss = onDismiss,
    onSend = onSend
)
