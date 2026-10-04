package com.vastutalks.app.data.ai

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.vastutalks.app.data.model.AnanyaAgent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Saves a call with the AI agent as it happens: every line said (by the caller or
 * Ananya) is appended to transcript.txt straight away, and every board
 * snapshot (PNG), camera photo (JPEG) and video (MP4) is written — so
 * nothing is lost if the app is killed mid-call.
 *
 * Files go to the app's folder
 *   Android/data/com.vastutalks.app/files/AgentCalls/<call time>/
 * and, on Android 10+, are also copied where the user can find them:
 * drawings to Pictures/VastuTalks/<call time>/ (shows in Gallery), the
 * transcript to Documents/VastuTalks/ when the call ends.
 */
class AgentCallRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val startedAt = System.currentTimeMillis()
    private val callName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(startedAt))
    private val io = Executors.newSingleThreadExecutor()
    private var drawingCount = 0
    private var photoCount = 0
    private var videoCount = 0

    val folder: File = File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "AgentCalls/$callName")
    private val transcript = File(folder, "transcript.txt")

    init {
        io.execute {
            folder.mkdirs()
            transcript.writeText("VastuTalks call with ${AnanyaAgent.NAME} (AI agent) — $callName\n\n")
        }
    }

    fun logLine(speaker: String, text: String) {
        val line = "[${elapsed()}] $speaker: $text\n"
        io.execute { runCatching { transcript.appendText(line) }.onFailure { Log.w(TAG, "Transcript write failed", it) } }
    }

    /** Saves a board snapshot and notes it in the transcript. Returns the file name used. */
    fun saveDrawing(bitmap: Bitmap, who: String, caption: String): String {
        drawingCount++
        val name = "drawing_%02d_%s.png".format(drawingCount, who.lowercase().replace(' ', '_'))
        logLine(who, "[$caption — saved as $name]")
        io.execute {
            runCatching {
                File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                publish(name, "image/png", "${Environment.DIRECTORY_PICTURES}/VastuTalks/$callName") { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }.onFailure { Log.w(TAG, "Drawing save failed", it) }
        }
        return name
    }

    /** Saves a camera photo the caller shared, and notes it in the transcript. */
    fun savePhoto(bitmap: Bitmap, caption: String) {
        photoCount++
        val name = "photo_%02d.jpg".format(photoCount)
        logLine("You", "[$caption — saved as $name]")
        io.execute {
            runCatching {
                File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                publish(name, "image/jpeg", "${Environment.DIRECTORY_PICTURES}/VastuTalks/$callName") { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }.onFailure { Log.w(TAG, "Photo save failed", it) }
        }
    }

    /** Moves a video the caller shared into the call's folder (deleting [source]), and notes it in the transcript. */
    fun saveVideo(source: File, caption: String) {
        videoCount++
        val name = "video_%02d.mp4".format(videoCount)
        logLine("You", "[$caption — saved as $name]")
        io.execute {
            runCatching {
                val saved = File(folder, name)
                if (!source.renameTo(saved)) {
                    source.copyTo(saved, overwrite = true)
                    source.delete()
                }
                publish(name, "video/mp4", "${Environment.DIRECTORY_MOVIES}/VastuTalks/$callName") { out ->
                    saved.inputStream().use { it.copyTo(out) }
                }
            }.onFailure { Log.w(TAG, "Video save failed", it) }
        }
    }

    /** Call when the call ends: closes the transcript and copies it to Documents/VastuTalks. */
    fun finish() {
        io.execute {
            runCatching {
                transcript.appendText("\nCall ended after ${elapsed()}.\n")
                publish("${callName}_transcript.txt", "text/plain", "${Environment.DIRECTORY_DOCUMENTS}/VastuTalks") { out ->
                    out.write(transcript.readBytes())
                }
            }.onFailure { Log.w(TAG, "Transcript publish failed", it) }
        }
        io.shutdown()
    }

    private fun publish(name: String, mime: String, relativePath: String, write: (java.io.OutputStream) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val collection = if (mime.startsWith("image/")) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else if (mime.startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val uri = appContext.contentResolver.insert(collection, values) ?: return
        appContext.contentResolver.openOutputStream(uri)?.use(write)
    }

    private fun elapsed(): String {
        val s = (System.currentTimeMillis() - startedAt) / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private companion object {
        const val TAG = "AgentCallRecorder"
    }
}
