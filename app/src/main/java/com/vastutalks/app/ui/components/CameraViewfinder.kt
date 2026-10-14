package com.vastutalks.app.ui.components

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Takes photos from a [CameraViewfinder] once its camera is running. */
class ViewfinderState {
    internal var imageCapture: ImageCapture? by mutableStateOf(null)
    internal var previewView: PreviewView? = null

    /**
     * What the preview shows right now, as a bitmap — instant and silent,
     * unlike [takePhoto], but only at screen resolution. Null until the
     * first frame.
     */
    fun snapshot(): android.graphics.Bitmap? = if (imageCapture == null) null else previewView?.bitmap

    /** True once the camera is up and a photo can be taken. */
    val isReady: Boolean get() = imageCapture != null

    /**
     * Takes a photo and loads it like any other (EXIF rotation fixed,
     * scaled to at most [maxSide] px). Null if the camera isn't ready or
     * the capture failed.
     */
    suspend fun takePhoto(context: Context, maxSide: Int = 1600): CapturedPhoto? {
        val capture = imageCapture ?: return null
        val dir = File(context.cacheDir, "call_photos").apply { mkdirs() }
        val file = File(dir, "wall_${System.currentTimeMillis()}.jpg")
        val saved = suspendCancellableCoroutine { cont ->
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = cont.resume(true)
                    override fun onError(exception: ImageCaptureException) {
                        android.util.Log.w("CameraViewfinder", "Capture failed", exception)
                        cont.resume(false)
                    }
                }
            )
        }
        if (!saved) return null
        return withContext(Dispatchers.IO) {
            runCatching { loadPhoto(context, Uri.fromFile(file), maxSide) }.getOrNull().also { file.delete() }
        }
    }
}

/**
 * Live preview from the back camera, filling [modifier]. Needs the CAMERA
 * permission to have been granted already.
 */
@Composable
fun CameraViewfinder(state: ViewfinderState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    state.previewView = previewView

    LaunchedEffect(lifecycleOwner) {
        val cameras = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (e: Exception) {
            android.util.Log.w("CameraViewfinder", "No camera provider", e)
            return@LaunchedEffect
        }
        try {
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            cameras.unbindAll()
            cameras.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            state.imageCapture = capture
            awaitCancellation()
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("CameraViewfinder", "Couldn't start the camera", e)
        } catch (e: IllegalStateException) {
            android.util.Log.w("CameraViewfinder", "Couldn't start the camera", e)
        } finally {
            state.imageCapture = null
            cameras.unbindAll()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
