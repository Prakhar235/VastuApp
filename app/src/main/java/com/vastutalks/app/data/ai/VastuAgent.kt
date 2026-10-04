package com.vastutalks.app.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.vastutalks.app.BuildConfig
import com.vastutalks.app.data.model.DemoAgentReplies
import com.vastutalks.app.data.model.DemoExpert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * One thing the agent draws on her whiteboard. Coordinates are on a
 * 0–100 grid (x left→right, y top→bottom; top of the board = North),
 * so the model doesn't need to know the device's screen size.
 */
sealed class BoardShape {
    abstract val color: String

    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val label: String?, override val color: String) : BoardShape()
    data class Circle(val x: Float, val y: Float, val r: Float, val label: String?, override val color: String) : BoardShape()
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val arrow: Boolean, override val color: String) : BoardShape()
    data class Label(val x: Float, val y: Float, val text: String, override val color: String) : BoardShape()
}

data class BoardDrawing(val title: String, val shapes: List<BoardShape>)

/** What the agent says out loud (and posts to chat), plus an optional whiteboard drawing. */
data class AgentTurn(val say: String, val board: BoardDrawing? = null)

/**
 * The demo call's AI agent: an OpenAI chat model playing [DemoExpert],
 * with memory of the conversation, able to see the user's sketches and
 * to answer with a whiteboard drawing when a layout is easier shown
 * than said.
 *
 * Calls the OpenAI API directly with the key from local.properties
 * (BuildConfig.OPENAI_API_KEY). Falls back to [DemoAgentReplies] if
 * there's no key or the request fails, so the demo never dead-ends.
 */
class VastuAgent(
    private val apiKey: String = BuildConfig.OPENAI_API_KEY,
    private val model: String = MODEL
) {
    private val history = mutableListOf<JSONObject>()

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** Greeting when the agent "picks up" the call. */
    suspend fun greet(): AgentTurn =
        respond("(The caller has just connected and nothing has been drawn or shared yet. Greet them warmly in one or two sentences and ask what they'd like help with. Do not draw.)", record = false)

    suspend fun reply(userText: String): AgentTurn = respond(userText)

    suspend fun reviewSketch(sketch: Bitmap, note: String = "Here's my sketch"): AgentTurn =
        respond(note, image = sketch)

    /**
     * Vastu read of a camera photo of the caller's space. Sent at higher
     * resolution than sketches so doors, windows, mirrors and furniture
     * are actually visible to the model. With [facing], the model is told
     * which direction each part of the frame is in, so it can judge
     * placements (stove in the South-East, mirror on the North wall…)
     * instead of guessing.
     */
    suspend fun reviewPhoto(photo: Bitmap, question: String?, facing: PhotoFacing?): AgentTurn {
        val note = buildString {
            append("(The caller took this photo of their home with their camera")
            if (!question.isNullOrBlank()) append(" and asks: \"$question\"")
            append(". ")
            if (facing != null) {
                append(facing.describeForAgent())
                append(" First work out what space this is and pick out the important things in it — doors, windows, ")
                append("bed, stove, sink, mirror, desk, safe, puja shelf, toilet, heavy furniture, plants, colours, clutter, ")
                append("light — and which direction each one is in using those directions. Then judge them by Vastu for ")
                append("those directions, mention what is already good, and give the two or three most useful corrections. ")
                append("Name the directions when you speak. You may use up to five sentences here. If moving something ")
                append("would help, draw a simple North-up plan of the room with what you saw placed in its real direction ")
                append("and an arrow showing where it should go.)")
            } else {
                append("Say what space it is and what you notice — doors, windows, furniture, mirrors, colours, ")
                append("clutter, light — then give the two or three most useful Vastu suggestions for it. You may use ")
                append("up to five sentences here. The camera direction is unknown, so ask which way it was facing. If ")
                append("moving something would help, draw a simple plan of the space showing where it should go.)")
            }
        }
        return respond(note, image = photo, detail = PhotoDetail.HIGH)
    }

    /**
     * Speech-to-text for one caller utterance (a 16 kHz WAV from
     * DemoListener). Returns null if nothing intelligible was said or
     * the request failed.
     */
    suspend fun transcribe(wav: ByteArray): String? {
        if (!isConfigured) return null
        return try {
            withContext(Dispatchers.IO) {
                val boundary = "----vastu${System.currentTimeMillis()}"
                val body = ByteArrayOutputStream().apply {
                    fun field(name: String, value: String) = write(
                        "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray()
                    )
                    field("model", TRANSCRIBE_MODEL)
                    field("prompt", "A caller asking a Vastu Shastra consultant about their home, possibly in Indian English or Hinglish.")
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
                    write(wav)
                    write("\r\n--$boundary--\r\n".toByteArray())
                }.toByteArray()
                val raw = send(TRANSCRIBE_ENDPOINT, "multipart/form-data; boundary=$boundary", body)
                JSONObject(raw).optString("text").trim().takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            android.util.Log.w("VastuAgent", "Transcription failed", e)
            null
        }
    }

    private suspend fun respond(
        userText: String,
        image: Bitmap? = null,
        record: Boolean = true,
        detail: PhotoDetail = PhotoDetail.LOW
    ): AgentTurn {
        if (!isConfigured) return fallback(userText, image)

        val userMessage = JSONObject().put("role", "user").put("content", userContent(userText, image, detail))
        return try {
            val raw = withContext(Dispatchers.IO) { post(buildRequest(userMessage)) }
            val turn = parseTurn(raw)
            if (record) {
                // A high-detail photo costs ~25k tokens; keep a low-detail copy in
                // the history so follow-up questions stay fast and cheap.
                history.add(
                    if (image != null && detail != PhotoDetail.LOW) {
                        JSONObject().put("role", "user").put("content", userContent(userText, image, PhotoDetail.LOW))
                    } else userMessage
                )
            }
            history.add(JSONObject().put("role", "assistant").put("content", raw))
            while (history.size > MAX_HISTORY) history.removeAt(0)
            turn
        } catch (e: Exception) {
            android.util.Log.w("VastuAgent", "OpenAI request failed, using canned reply", e)
            fallback(userText, image)
        }
    }

    private fun fallback(userText: String, image: Bitmap?): AgentTurn = AgentTurn(
        if (image != null) "Thanks for sharing that! Let me know which area of it you'd like suggestions for."
        else DemoAgentReplies.replyFor(userText)
    )

    private fun userContent(text: String, image: Bitmap?, detail: PhotoDetail): Any {
        if (image == null) return text
        return JSONArray()
            .put(JSONObject().put("type", "text").put("text", text))
            .put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject()
                        .put("url", "data:image/jpeg;base64,${encode(image, detail.maxSide)}")
                        .put("detail", detail.apiValue)
                )
            )
    }

    private fun buildRequest(userMessage: JSONObject): JSONObject {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        history.forEach { messages.put(it) }
        messages.put(userMessage)
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("temperature", 0.7)
    }

    private fun post(body: JSONObject): String =
        JSONObject(send(ENDPOINT, "application/json", body.toString().toByteArray()))
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")

    private fun send(endpoint: String, contentType: String, body: ByteArray): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("OpenAI HTTP $code: $text")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun encode(bitmap: Bitmap, maxSide: Int): String {
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private enum class PhotoDetail(val apiValue: String, val maxSide: Int) {
        LOW("low", 512),
        HIGH("high", 1024)
    }

    companion object {
        /** Swap for any newer vision-capable chat model on your account. */
        const val MODEL = "gpt-4o-mini"
        const val TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe"
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val TRANSCRIBE_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        private const val MAX_HISTORY = 24

        private val SYSTEM_PROMPT = """
            You are ${DemoExpert.NAME}, a warm, practical ${DemoExpert.SPECIALTY} consultant on a live voice call
            inside the VastuTalks app. Everything you "say" is read aloud by text-to-speech, so speak naturally:
            1-3 short sentences, no lists, no markdown, no emojis. Stay on Vastu Shastra, home layout and related
            topics; gently steer back if asked about something unrelated. After answering, stop and let the
            caller talk — don't ramble or ask several questions at once.

            You and the caller share a square whiteboard for the whole call. The caller sketches on it with
            their finger and can send you a picture of the whole board (their strokes plus anything you drew)
            — refer to what you actually see. When a direction, room placement or layout is easier to show
            than to say, draw on it. The board is a 100x100 grid: x goes left to right (West to East), y goes
            top to bottom (North to South), so the top edge is North. Your drawing appears on top of the
            caller's sketch using the same grid, so you can mark up their plan directly. Each new drawing of
            yours replaces your previous one. Keep drawings simple (3-12 shapes) and label rooms.

            Always reply with a single JSON object:
            {
              "say": "what you speak aloud",
              "board": null | {
                "title": "short caption",
                "shapes": [
                  {"type": "rect", "x": 10, "y": 10, "w": 30, "h": 25, "label": "Kitchen", "color": "saffron"},
                  {"type": "circle", "x": 50, "y": 50, "r": 6, "label": "Brahmasthan", "color": "white"},
                  {"type": "arrow", "x1": 50, "y1": 90, "x2": 50, "y2": 65, "color": "blue"},
                  {"type": "line", "x1": 0, "y1": 50, "x2": 100, "y2": 50, "color": "copper"},
                  {"type": "text", "x": 80, "y": 15, "text": "Ishan (NE)", "color": "white"}
                ]
              }
            }
            Colors: white, saffron, copper, blue, red, green. When you draw, have "say" talk the caller through it.
        """.trimIndent()

        internal fun parseTurn(raw: String): AgentTurn {
            val json = JSONObject(raw)
            val say = json.optString("say").ifBlank { "Sorry, could you say that again?" }
            val board = json.optJSONObject("board")?.let { b ->
                val shapes = b.optJSONArray("shapes") ?: return@let null
                val parsed = (0 until shapes.length()).mapNotNull { i -> parseShape(shapes.optJSONObject(i)) }
                if (parsed.isEmpty()) null else BoardDrawing(b.optString("title"), parsed)
            }
            return AgentTurn(say, board)
        }

        private fun parseShape(o: JSONObject?): BoardShape? {
            if (o == null) return null
            fun f(key: String) = o.optDouble(key, 0.0).toFloat().coerceIn(0f, 100f)
            fun label() = o.optString("label").takeIf { it.isNotBlank() && it != "null" }
            val color = o.optString("color", "white")
            return when (o.optString("type")) {
                "rect" -> BoardShape.Rect(f("x"), f("y"), f("w"), f("h"), label(), color)
                "circle" -> BoardShape.Circle(f("x"), f("y"), f("r"), label(), color)
                "line" -> BoardShape.Line(f("x1"), f("y1"), f("x2"), f("y2"), arrow = false, color = color)
                "arrow" -> BoardShape.Line(f("x1"), f("y1"), f("x2"), f("y2"), arrow = true, color = color)
                "text" -> o.optString("text").takeIf { it.isNotBlank() }?.let { BoardShape.Label(f("x"), f("y"), it, color) }
                else -> null
            }
        }
    }
}
