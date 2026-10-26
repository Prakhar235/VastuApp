package com.vastutalks.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vastutalks.app.ui.components.AgentChatMessage
import com.vastutalks.app.ui.components.ChatSender
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executors

/**
 * Keeps the text chat with Ananya on the phone, so it's still there next
 * time and she picks up where she left off. Saves three things in the
 * app's private storage (filesDir/agent_chat/<uid>/):
 *  - the chat as shown: every message, its images as JPEGs;
 *  - the conversation as the model sees it (VastuAgent.exportHistory),
 *    which includes her marks, so follow-up questions have the context;
 *  - the last photo or video frames she can still mark up.
 *
 * The call with her saves into the same chat, so what was said on a
 * call is there in the chat afterwards (and the other way round).
 *
 * Writes from every instance happen in order on one background thread.
 */
class AgentChatStore(context: Context) {
    // One chat per account, so signing in with another account on this phone doesn't show the last one's.
    private val dir = File(
        context.applicationContext.filesDir,
        "agent_chat/" + (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "signed_out")
    )
    private val chatFile = File(dir, "chat.json")

    class Saved(val messages: List<AgentChatMessage>, val history: String, val markable: List<Bitmap>)

    /** The saved chat, or null if there isn't one (or it can't be read). Call off the main thread. */
    fun load(): Saved? = runCatching {
        if (!chatFile.exists()) return null
        val json = JSONObject(chatFile.readText())
        val list = json.getJSONArray("messages")
        val messages = (0 until list.length()).map { i ->
            val m = list.getJSONObject(i)
            AgentChatMessage(
                id = m.getInt("id"),
                text = m.optString("text"),
                sender = if (m.optString("sender") == ChatSender.USER.name) ChatSender.USER else ChatSender.AGENT,
                image = m.optString("image").takeIf { it.isNotBlank() }
                    ?.let { BitmapFactory.decodeFile(File(dir, it).path)?.asImageBitmap() },
                hasReport = m.optBoolean("report")
            )
        }
        val markable = (0 until json.optInt("markableCount")).mapNotNull { i ->
            BitmapFactory.decodeFile(File(dir, markableName(i)).path)
        }
        Saved(messages, json.optString("history", "[]"), markable)
    }.onFailure { Log.w(TAG, "Couldn't load the saved chat", it) }.getOrNull()

    /**
     * Saves the chat and the model's history. Images already on disk aren't
     * written again. [fromCall] tells an open chat screen to reload it.
     */
    fun save(messages: List<AgentChatMessage>, history: String, markableCount: Int, fromCall: Boolean = false) {
        val snapshot = messages.toList()
        io.execute {
            runCatching {
                dir.mkdirs()
                val list = JSONArray()
                snapshot.forEach { m ->
                    val imageName = m.image?.let { image ->
                        "msg_${m.id}.jpg".also { name ->
                            val file = File(dir, name)
                            if (!file.exists()) file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 85, it) }
                        }
                    }
                    list.put(
                        JSONObject()
                            .put("id", m.id)
                            .put("text", m.text)
                            .put("sender", m.sender.name)
                            .put("image", imageName ?: "")
                            .put("report", m.hasReport)
                    )
                }
                val json = JSONObject().put("messages", list).put("history", history).put("markableCount", markableCount)
                // Write to a temp file first so a crash mid-write can't corrupt the saved chat.
                val tmp = File(dir, "chat.json.tmp")
                tmp.writeText(json.toString())
                tmp.renameTo(chatFile)
                if (fromCall) _callSaves.update { it + 1 }
            }.onFailure { Log.w(TAG, "Couldn't save the chat", it) }
        }
    }

    /** Saves the photo or video frames her marks go on (replacing the previous ones). */
    fun saveMarkable(images: List<Bitmap>) {
        io.execute {
            runCatching {
                dir.mkdirs()
                dir.listFiles { f -> f.name.startsWith("markable_") }?.forEach { it.delete() }
                images.forEachIndexed { i, bitmap ->
                    File(dir, markableName(i)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                }
            }.onFailure { Log.w(TAG, "Couldn't save the photo", it) }
        }
    }

    /** Saves a room scan's full report (photos, directions and the read) for the message [messageId]. */
    fun saveReport(messageId: Int, walls: List<WallPhoto>, analysis: RoomAnalysis) {
        io.execute {
            runCatching {
                val reportDir = File(dir, reportName(messageId)).apply { mkdirs() }
                val wallList = JSONArray()
                walls.forEachIndexed { i, wall ->
                    File(reportDir, "wall_$i.jpg").outputStream().use { wall.bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    wallList.put(JSONObject().put("degrees", wall.facing.degrees.toDouble()).put("source", wall.facing.source.name))
                }
                File(reportDir, "report.json").writeText(
                    JSONObject().put("walls", wallList).put("analysis", JSONObject(analysis.toJson())).toString()
                )
            }.onFailure { Log.w(TAG, "Couldn't save the room report", it) }
        }
    }

    /** The room report saved for [messageId], or null. Call off the main thread. */
    fun loadReport(messageId: Int): RoomScanHandoff.Scan? = runCatching {
        val reportDir = File(dir, reportName(messageId))
        val json = JSONObject(File(reportDir, "report.json").readText())
        val wallList = json.getJSONArray("walls")
        val walls = (0 until wallList.length()).map { i ->
            val w = wallList.getJSONObject(i)
            WallPhoto(
                BitmapFactory.decodeFile(File(reportDir, "wall_$i.jpg").path) ?: error("Missing photo $i"),
                PhotoFacing(w.getDouble("degrees").toFloat(), PhotoFacing.Source.valueOf(w.getString("source")))
            )
        }
        RoomScanHandoff.Scan(walls, RoomAnalysis.parse(json.getJSONObject("analysis").toString(), walls.size))
    }.onFailure { Log.w(TAG, "Couldn't load the room report", it) }.getOrNull()

    /** Deletes the saved chat, for a fresh start. */
    fun clear() {
        io.execute { dir.deleteRecursively() }
    }

    private fun markableName(i: Int) = "markable_$i.jpg"
    private fun reportName(messageId: Int) = "report_$messageId"

    companion object {
        private const val TAG = "AgentChatStore"
        private val io = Executors.newSingleThreadExecutor()
        private val _callSaves = MutableStateFlow(0)
        /** Bumped once each save from a call is on disk. */
        val callSaves: StateFlow<Int> get() = _callSaves
    }
}
