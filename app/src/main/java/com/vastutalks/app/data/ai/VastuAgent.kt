package com.vastutalks.app.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.vastutalks.app.BuildConfig
import com.vastutalks.app.data.model.AgentFallbackReplies
import com.vastutalks.app.data.model.AnanyaAgent
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
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val arrow: Boolean, override val color: String, val label: String? = null) : BoardShape()
    data class Label(val x: Float, val y: Float, val text: String, override val color: String) : BoardShape()
}

data class BoardDrawing(val title: String, val shapes: List<BoardShape>)

/**
 * What the agent says out loud (and posts to chat), plus an optional
 * whiteboard drawing and/or the caller's latest photo with her marks on it.
 * [details] is her written, point-by-point read of a photo — one line per
 * numbered mark — shown in the chat but not read aloud.
 */
data class AgentTurn(
    val say: String,
    val board: BoardDrawing? = null,
    val markedPhoto: Bitmap? = null,
    val details: String? = null
) {
    /** What goes in the chat bubble: the reply plus the written details. */
    val chatText: String get() = if (details.isNullOrBlank()) say else "$say\n\n$details"
}

/** On a voice call she keeps it short; in the text chat she can write more. */
enum class AgentMode { CALL, CHAT }

/**
 * Ananya, the AI agent: an OpenAI chat model playing [AnanyaAgent],
 * with memory of the conversation, able to see the caller's photos and
 * videos and to answer by marking up the photo itself (circles, arrows,
 * labels — see ImageMarkup). The shared whiteboard is off for now
 * ([BOARD_ENABLED]).
 *
 * Calls the OpenAI API directly with the key from local.properties
 * (BuildConfig.OPENAI_API_KEY). Falls back to [AgentFallbackReplies] if
 * there's no key or the request fails, so the call never dead-ends.
 */
class VastuAgent(
    private val mode: AgentMode = AgentMode.CALL,
    private val apiKey: String = BuildConfig.OPENAI_API_KEY,
    private val model: String = MODEL,
    private val visionModel: String = VISION_MODEL
) {
    private val history = mutableListOf<JSONObject>()

    /**
     * The caller's latest photo, or their latest video's frames, without
     * the grid — what the agent's marks get drawn on, in later turns too.
     */
    @Volatile private var markable: List<Bitmap> = emptyList()

    /** What the agent last said, to spot the mic picking up her own voice. */
    @Volatile private var lastSay: String = ""

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** The photo or video frames her marks go on, to save with the conversation. */
    val markableImages: List<Bitmap> get() = markable

    /** The conversation as the model sees it (her replies with their marks), to save between sessions. */
    fun exportHistory(): String = JSONArray(history).toString()

    /**
     * Picks a saved conversation back up, so she remembers what was asked,
     * what she saw and what she marked; [images] is the last photo or video
     * frames, so follow-ups can still be marked on it.
     */
    fun restore(historyJson: String, images: List<Bitmap>) {
        val saved = JSONArray(historyJson)
        history.clear()
        for (i in 0 until saved.length()) saved.optJSONObject(i)?.let(history::add)
        trimHistory()
        markable = images
    }

    /** Greeting when the agent "picks up" the call, or the chat opens. */
    suspend fun greet(): AgentTurn = respond(
        if (mode == AgentMode.CALL) "(The caller has just connected and nothing has been drawn or shared yet. Greet them warmly in ${languageName()} in one or two sentences and ask what they'd like help with. Do not draw or mark anything.)"
        else "(The user has just opened the chat and nothing has been shared yet. Greet them warmly in ${languageName()} in one or two sentences, and tell them they can ask a question, upload a photo of any room, or scan a whole room wall by wall, for Vastu suggestions marked on the photo. Do not mark anything.)",
        record = false
    )

    suspend fun reply(userText: String): AgentTurn = respond(userText)

    /**
     * A turn on a video call: what the caller said plus what their camera
     * shows at that moment ([view], with [facing] when the compass knows
     * it), so she can talk about what's in front of them and mark it.
     * Sent at low detail to keep spoken replies quick.
     */
    suspend fun replyWithView(userText: String, view: Bitmap, facing: PhotoFacing?): AgentTurn {
        val note = buildString {
            append("(Video call: the caller's camera is on and this image is what it shows right now. ")
            if (facing != null) append(facing.describeForAgent()).append(" ")
            append("Answer what they said. Use the view when it's relevant — name what you see and its direction — ")
            append("and mark it when pointing something out helps; don't describe the view if they asked about ")
            append("something else.) They said: ")
            append(userText)
        }
        markable = listOf(view)
        return respond(note, images = listOf(ImageMarkup.withGrid(view)), model = visionModel)
    }

    /**
     * A full Vastu read of one room from its wall photos (the guided room
     * scan): a score, and each thing found with where it is, how serious,
     * how to fix it and where it should go. Not part of the conversation;
     * see [attachRoomScan] to bring it into the chat. Null if there's no
     * API key or the request failed.
     */
    suspend fun analyzeRoom(walls: List<WallPhoto>): RoomAnalysis? {
        if (!isConfigured || walls.isEmpty()) return null
        val note = buildString {
            append("Here are ${walls.size} photos of the room, in order (photo 0 to ${walls.size - 1}).")
            walls.forEachIndexed { i, wall -> append(" Photo $i: ").append(wall.facing.describeForAgent()) }
        }
        val request = JSONObject()
            .put("model", visionModel)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", RoomAnalysis.prompt()))
                    .put(JSONObject().put("role", "user").put("content", userContent(note, walls.map { ImageMarkup.withGrid(it.bitmap) }, PhotoDetail.HIGH)))
            )
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("temperature", 0.4)
        return try {
            val started = System.currentTimeMillis()
            val raw = withContext(Dispatchers.IO) { post(request) }
            android.util.Log.d("VastuAgent", "Room analysed in ${System.currentTimeMillis() - started} ms")
            RoomAnalysis.parse(raw, walls.size)
        } catch (e: Exception) {
            android.util.Log.w("VastuAgent", "Room analysis failed", e)
            null
        }
    }

    /**
     * Brings a room scan into the conversation, so follow-up questions
     * about the room work: she remembers the photos and her read of them,
     * and later marks go on those photos (frame = photo index).
     */
    fun attachRoomScan(walls: List<WallPhoto>, analysis: RoomAnalysis) {
        val note = buildString {
            append("(I scanned my room wall by wall: ${walls.size} photos, in order (frame 0 to ${walls.size - 1}).")
            walls.forEachIndexed { i, wall -> append(" Frame $i: ").append(wall.facing.describeForAgent()) }
            append(")")
        }
        history.add(JSONObject().put("role", "user").put("content", userContent(note, walls.map { ImageMarkup.withGrid(it.bitmap) }, PhotoDetail.LOW)))
        history.add(JSONObject().put("role", "assistant").put("content", JSONObject().put("say", analysis.asText()).toString()))
        markable = walls.map { it.bitmap }
        trimHistory()
    }

    suspend fun reviewSketch(sketch: Bitmap, note: String = "Here's my sketch"): AgentTurn =
        respond(note, images = listOf(sketch))

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
            append("(The caller shared this photo of their home")
            if (!question.isNullOrBlank()) append(" and asks: \"$question\"")
            append(". ")
            if (facing != null) append(facing.describeForAgent()).append(" ")
            else append("The camera direction is unknown; judge what you can without directions and ask which way it faced. ")
            append(PHOTO_READ_STEPS)
            append(")")
        }
        markable = listOf(photo)
        return respond(note, images = listOf(ImageMarkup.withGrid(photo)), detail = PhotoDetail.HIGH, model = visionModel)
    }

    /**
     * Vastu read of a short video of the caller's space. The model can't
     * watch video, so it gets [frames] spread evenly across the clip, in
     * order, at low detail (eight high-detail frames would cost ~200k
     * tokens). [facing] is the direction the camera pointed when
     * recording started; the model follows the pan from there.
     */
    suspend fun reviewVideo(frames: List<Bitmap>, durationMs: Long, question: String?, facing: PhotoFacing?): AgentTurn {
        val seconds = (durationMs / 1000).coerceAtLeast(1)
        val note = buildString {
            append("(The caller recorded a ${seconds}-second video of their home")
            if (!question.isNullOrBlank()) append(" and asks: \"$question\"")
            append(". You can't watch it, so here are ${frames.size} frames taken evenly across it, in order ")
            append("(frame 0 to ${frames.size - 1}). ")
            if (facing != null) {
                append("For the first frame: ")
                append(facing.describeForAgent())
                append(" The caller probably turned or walked while recording, so work out where later frames point by ")
                append("following the pan — if things slide towards the left between frames, the camera is turning ")
                append("right (clockwise, e.g. from North towards East). Only name a direction for something when you're ")
                append("fairly sure. ")
            } else {
                append("The camera direction is unknown, so ask which way the video started facing. ")
            }
            append("Treat the frames as one walkthrough, not separate photos. ")
            append(PHOTO_READ_STEPS)
            append(" Put the marks on the one frame that shows the most of what you judge.)")
        }
        markable = frames
        return respond(note, images = frames.map(ImageMarkup::withGrid), historyImages = 3, model = visionModel)
    }

    /**
     * Speech-to-text for one caller utterance (a 16 kHz WAV from
     * CallerListener). Returns null if nothing intelligible was said or
     * the request failed.
     *
     * Spoken Hindi and Urdu sound almost the same, so auto-detection
     * sometimes writes Hindi speech in Urdu (Arabic) script. The app only
     * supports English and Hindi, so in that case it's re-run with
     * whisper-1, which sticks to the language it's given (the gpt-4o
     * transcribers only treat it as a hint and can still answer in Urdu).
     */
    suspend fun transcribe(wav: ByteArray): String? {
        if (!isConfigured) return null
        return try {
            withContext(Dispatchers.IO) {
                val started = System.currentTimeMillis()
                val text = transcribeAs(wav, TRANSCRIBE_MODEL, language = null)
                val heard = if (text != null && text.any { it.isArabicScript() }) {
                    transcribeAs(wav, HINDI_TRANSCRIBE_MODEL, language = "hi") ?: text
                } else text
                android.util.Log.d(
                    "VastuAgent",
                    "Heard in ${System.currentTimeMillis() - started} ms: \"$text\"" + if (heard != text) " → as Hindi: \"$heard\"" else ""
                )
                heard?.takeUnless { isEchoOfAgent(it) }
            }
        } catch (e: Exception) {
            android.util.Log.w("VastuAgent", "Transcription failed", e)
            null
        }
    }

    /** True if [heard] is just a piece of the agent's last reply (her voice leaking into the mic). */
    private fun isEchoOfAgent(heard: String): Boolean {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val h = norm(heard)
        return h.length >= 6 && norm(lastSay).contains(h)
    }

    private fun transcribeAs(wav: ByteArray, model: String, language: String?): String? {
        val boundary = "----vastu${System.currentTimeMillis()}"
        val body = ByteArrayOutputStream().apply {
            fun field(name: String, value: String) = write(
                "--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray()
            )
            field("model", model)
            field("prompt", TRANSCRIBE_PROMPT)
            if (language != null) field("language", language)
            write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
            write(wav)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val raw = send(TRANSCRIBE_ENDPOINT, "multipart/form-data; boundary=$boundary", body)
        val text = JSONObject(raw).optString("text").trim()
        // The prompt echoed back (sometimes as "Context: <prompt>") means nothing intelligible was said.
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val heard = norm(text)
        val prompt = norm(TRANSCRIBE_PROMPT)
        val echoesPrompt = heard.length > 10 && (prompt.contains(heard) || heard.contains(prompt.take(30)))
        return text.takeIf { it.isNotBlank() && !echoesPrompt }
    }

    private suspend fun respond(
        userText: String,
        images: List<Bitmap> = emptyList(),
        record: Boolean = true,
        detail: PhotoDetail = PhotoDetail.LOW,
        historyImages: Int = 1,
        model: String = this.model
    ): AgentTurn {
        if (!isConfigured) return fallback(userText, images)

        val userMessage = JSONObject().put("role", "user").put("content", userContent(userText, images, detail))
        return try {
            val started = System.currentTimeMillis()
            val raw = withContext(Dispatchers.IO) { post(buildRequest(userMessage, model)) }
            val turn = parseTurn(raw).let { parsed ->
                val marks = parseMarks(raw)
                val target = marks?.let { markable.getOrNull(it.frame.coerceIn(0, markable.size - 1)) }
                if (marks != null && target != null) parsed.copy(markedPhoto = ImageMarkup.draw(target, marks.shapes)) else parsed
            }
            android.util.Log.d("VastuAgent", "Replied in ${System.currentTimeMillis() - started} ms: \"${turn.say}\"")
            lastSay = turn.say
            if (record) {
                // A high-detail photo costs ~25k tokens and a video's frames add up;
                // keep a few low-detail images in the history so follow-up questions
                // stay fast and cheap.
                history.add(
                    if (images.isNotEmpty() && (detail != PhotoDetail.LOW || images.size > historyImages)) {
                        val kept = if (images.size <= historyImages) images
                        else List(historyImages) { i -> images[i * (images.size - 1) / maxOf(1, historyImages - 1)] }
                        JSONObject().put("role", "user").put("content", userContent(userText, kept, PhotoDetail.LOW))
                    } else userMessage
                )
            }
            history.add(JSONObject().put("role", "assistant").put("content", raw))
            trimHistory()
            turn
        } catch (e: Exception) {
            android.util.Log.w("VastuAgent", "OpenAI request failed, using canned reply", e)
            fallback(userText, images)
        }
    }

    /**
     * Keeps the history to [maxHistory] messages. Only the latest
     * [KEEP_IMAGE_TURNS] photo turns keep their images; older ones become
     * a text note, so a long chat stays fast and cheap but she still knows
     * what was shared and what she said about it.
     */
    private fun trimHistory() {
        while (history.size > maxHistory) history.removeAt(0)
        var imageTurns = 0
        for (i in history.indices.reversed()) {
            val content = history[i].opt("content") as? JSONArray ?: continue
            if (++imageTurns <= KEEP_IMAGE_TURNS) continue
            val text = content.optJSONObject(0)?.optString("text").orEmpty()
            history[i] = JSONObject().put("role", "user").put("content", "$text [photo shared earlier; no longer attached]")
        }
    }

    private val maxHistory get() = if (mode == AgentMode.CHAT) MAX_CHAT_HISTORY else MAX_HISTORY

    private fun fallback(userText: String, images: List<Bitmap>): AgentTurn = AgentTurn(
        if (images.isNotEmpty()) tr("Thanks for sharing! I can't look at it properly right now — please send it again in a little while.", "शेयर करने के लिए धन्यवाद! अभी मैं इसे ठीक से देख नहीं पा रही हूँ — थोड़ी देर बाद फिर से भेजिए।")
        else AgentFallbackReplies.replyFor(userText)
    )

    private fun userContent(text: String, images: List<Bitmap>, detail: PhotoDetail): Any {
        if (images.isEmpty()) return text
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        images.forEach { image ->
            content.put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject()
                        .put("url", "data:image/jpeg;base64,${encode(image, detail.maxSide)}")
                        .put("detail", detail.apiValue)
                )
            )
        }
        return content
    }

    private fun buildRequest(userMessage: JSONObject, model: String): JSONObject {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt(mode)))
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

    /**
     * No disconnect() here on purpose: reading the response to the end and
     * closing the stream hands the socket back to the keep-alive pool, so
     * the next request (each spoken turn makes two) skips a fresh TLS
     * handshake.
     */
    private fun send(endpoint: String, contentType: String, body: ByteArray): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("OpenAI HTTP $code: $text")
        return text
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
        private fun languageName() = if (AgentLanguagePref.isHindi) "Hindi" else "English"

        /** Swap for any newer vision-capable chat model on your account. */
        const val MODEL = "gpt-4o-mini"
        /**
         * For turns where the caller shares a photo or video. Tested on real
         * room photos: gpt-4.1 puts marks on the right object; gpt-4o-mini and
         * gpt-4o often circle the wrong thing. Similar speed, costs more.
         */
        const val VISION_MODEL = "gpt-4.1"
        const val TRANSCRIBE_MODEL = "gpt-4o-mini-transcribe"
        /** Re-transcribes speech that came back in Urdu script; honours `language` strictly. */
        const val HINDI_TRANSCRIBE_MODEL = "whisper-1"
        private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val TRANSCRIBE_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        private const val MAX_HISTORY = 24
        /** The text chat is saved and picked up again later, so she remembers more of it. */
        private const val MAX_CHAT_HISTORY = 80
        private const val KEEP_IMAGE_TURNS = 2

        // Keep this free of example sentences: on noisy or near-silent audio the
        // transcriber can return the prompt itself, which the agent then "hears".
        private const val TRANSCRIBE_PROMPT =
            "A caller asking a Vastu Shastra consultant about their home, in English, Hindi or Hinglish."

        /**
         * What she does with every shared photo, asked or not: find what's in
         * it, judge each thing, and mark and number it so the written list
         * and the photo line up.
         */
        private const val PHOTO_READ_STEPS =
            "Even if they asked nothing specific, do a full Vastu read of it: 1) work out what room or space this is; " +
                "2) find every Vastu-relevant thing you can actually see — doors, windows, bed, stove, sink, mirror, desk, " +
                "safe or locker, puja shelf or idols, toilet, staircase, heavy furniture, TV, plants, water, clocks, " +
                "pictures, colours, clutter, beams, light — and, where the directions are known, which direction each " +
                "is in; 3) judge each one by Vastu: what is already good, what is a problem and why. Mark every thing " +
                "you judge on the photo (up to 8 marks), numbered in the same order as your \"details\" list. In \"say\" " +
                "give the overall verdict and the two or three most important corrections, naming the directions."

        private fun systemPrompt(mode: AgentMode): String {
            val style = if (mode == AgentMode.CALL) """
                You are ${AnanyaAgent.NAME}, a warm, practical ${AnanyaAgent.SPECIALTY} consultant on a live voice call
                inside the VastuTalks app. Everything you "say" is read aloud by text-to-speech, so speak naturally:
                1-3 short sentences (up to five when reading a photo or video), no lists, no emojis, and no markdown
                except the ** highlighting below.
                After answering, stop and let the caller talk — don't ramble or ask several questions at once.
                Don't open every reply with a greeting; greet only once, at the start of the call.
            """.trimIndent() else """
                You are ${AnanyaAgent.NAME}, a warm, practical ${AnanyaAgent.SPECIALTY} consultant chatting by text
                inside the VastuTalks app. Write plain text (no emojis, and no markdown except the ** highlighting below): a short paragraph, or a few
                short lines starting with "• " when listing steps. Be specific and practical, and don't pad.
                Don't open every reply with a greeting; greet only once, at the start of the chat.
            """.trimIndent()
            return style + "\n" + """
                Stay on Vastu Shastra, home layout and related topics; gently steer back if asked about something
                unrelated.
            """.trimIndent() + "\n\n" + languageRule() + "\n\n" + """
                Highlighting: wrap every suggestion — each thing you advise the caller to do, change, move, add or
                avoid — in double asterisks, e.g. "**Keep the bed in the South-West corner**", so it stands out in
                the chat. Highlight only the advice itself (a short phrase or one sentence), not explanations,
                greetings or praise. This applies to "say" and to each line of "details"; never use asterisks in
                mark labels.
            """.trimIndent() + "\n\n" + (if (BOARD_ENABLED) BOARD_PROMPT else "") + MARKS_PROMPT
        }

        /**
         * The reply language, from the switch on the chat and call screens
         * (English by default). Read on every request, so switching takes
         * effect from her next reply even mid-conversation.
         */
        private fun languageRule(): String = if (AgentLanguagePref.isHindi) """
            Language: the user has chosen Hindi. Always reply in simple everyday Hindi written in Devanagari
            script, preferring Hindi words over Urdu/Persian ones (सुझाव not मशवरा, दिशा not तरफ़, ज़रूर is fine),
            even if the user writes in English or Hinglish, and even if earlier replies were in English. Common
            English words people use in Hindi are fine (बेडरूम, किचन, मिरर). Never reply in Urdu, Arabic or any
            other language, and never write in Urdu/Arabic script. Speech-to-text sometimes writes the user's Hindi
            in Urdu script by mistake — treat that as Hindi. Never tell the user they are speaking Urdu, and never
            comment on which language they use. You are a woman: in Hindi use feminine forms for yourself (मैं कर
            सकती हूँ, मैं बताती हूँ). Name directions in Hindi with the Vastu name where it helps (उत्तर-पूर्व /
            ईशान). Labels on drawings and photo marks are short and in Hindi.
        """.trimIndent() else """
            Language: the user has chosen English. Always reply in simple, clear English, even if the user writes
            in Hindi or Hinglish, and even if earlier replies were in Hindi (they can switch the app to Hindi
            themselves). Never reply in Urdu or any other language. Name directions in English, adding the Vastu
            name where it helps (North-East / Ishan). Labels on drawings and photo marks are short and in English.
        """.trimIndent()

        /** The shared whiteboard is hidden for now; flip this to bring it back (also shows the Board page). */
        const val BOARD_ENABLED = false

        private val BOARD_PROMPT = """
            You and the caller share a square whiteboard for the whole call. The caller sketches on it with
            their finger and can send you a picture of the whole board (their strokes plus anything you drew)
            — refer to what you actually see. When a direction, room placement or layout is easier to show
            than to say, draw on it. The board is a 100x100 grid: x goes left to right (West to East), y goes
            top to bottom (North to South), so the top edge is North. Your drawing appears on top of the
            caller's sketch using the same grid, so you can mark up their plan directly. Each new drawing of
            yours replaces your previous one. Keep drawings simple (3-12 shapes) and label rooms. To draw, add
            to your JSON reply:
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
            Colors: white, saffron, copper, blue, red, green. When you draw, have "say" talk the caller through it.

        """.trimIndent()

        private val MARKS_PROMPT = """
            When the caller shares a photo or video of their home, mark up that actual image so they can see
            exactly what you mean. The images you get have a faint yellow grid numbered along the top edge (x, 0
            to 100, left to right) and the left edge (y, 0 to 100, top to bottom); use it to put each mark right
            on the object, aiming at its centre; use a rect for big things like a bed or cupboard. The caller sees
            their photo without the grid. Only mark things you can really see — never guess an object that isn't
            there. Circle or box a problem in red, something good in green, and when you suggest moving
            something, add a green arrow from it to a visible spot where it should go. Start each circle or box
            label with its number, then 2-4 words ("1 Mirror facing bed", "2 Puja spot good"); arrows and text
            need no number. Never label a wall or corner with a direction unless you know which way the camera
            faced. Have "say" point to the marks by number and colour ("red circle 1", "green arrow"). Marks go on the
            most recent photo or video, also in follow-up answers about it; for a video, say which frame. Don't
            add marks when no photo or video has been shared or when you aren't talking about something in it.

            With marks, also fill "details": one line per numbered mark, in order, written for the caller to
            read — "1. <what it is and where> — <fine / problem: why> — **<what to do>**" — so the photo and the
            list match. Leave "details" null when there are no marks.

            Always reply with a single JSON object:
            {
              "say": "what you speak aloud",
              "details": null | "1. ...\n2. ...",
              "marks": null | {
                "frame": 0,
                "shapes": [
                  {"type": "circle", "x": 62, "y": 40, "r": 8, "label": "1 Mirror facing bed", "color": "red"},
                  {"type": "rect", "x": 10, "y": 55, "w": 30, "h": 25, "label": "2 Bed", "color": "green"},
                  {"type": "arrow", "x1": 30, "y1": 70, "x2": 15, "y2": 35, "label": "Move bed here", "color": "green"},
                  {"type": "text", "x": 80, "y": 12, "text": "North wall", "color": "saffron"}
                ]
              }
            }
            Marks use percent of the image: x of its width, y of its height; a circle's r is percent of the width.
        """.trimIndent()

        /** Photo marks in a reply, or null when there are none. */
        internal fun parseMarks(raw: String): PhotoMarks? {
            val m = JSONObject(raw).optJSONObject("marks") ?: return null
            val shapes = m.optJSONArray("shapes") ?: return null
            val parsed = (0 until shapes.length()).mapNotNull { i -> parseShape(shapes.optJSONObject(i)) }
            return if (parsed.isEmpty()) null else PhotoMarks(m.optInt("frame", 0), parsed)
        }

        internal fun parseTurn(raw: String): AgentTurn {
            val json = JSONObject(raw)
            val say = json.optString("say").ifBlank { tr("Sorry, could you say that again?", "माफ़ कीजिए, क्या आप फिर से बता सकते हैं?") }
            val details = json.optString("details").trim().takeIf { it.isNotBlank() && it != "null" }
            val board = json.optJSONObject("board")?.takeIf { BOARD_ENABLED }?.let { b ->
                val shapes = b.optJSONArray("shapes") ?: return@let null
                val parsed = (0 until shapes.length()).mapNotNull { i -> parseShape(shapes.optJSONObject(i)) }
                if (parsed.isEmpty()) null else BoardDrawing(b.optString("title"), parsed)
            }
            return AgentTurn(say, board, details = details)
        }

        /** Arabic, Urdu and Persian letters (including the presentation-form blocks). */
        private fun Char.isArabicScript() =
            this in '؀'..'ۿ' || this in 'ݐ'..'ݿ' || this in 'ࢠ'..'ࣿ' ||
                this in 'ﭐ'..'﷿' || this in 'ﹰ'..'﻿'

        private fun parseShape(o: JSONObject?): BoardShape? {
            if (o == null) return null
            fun f(key: String) = o.optDouble(key, 0.0).toFloat().coerceIn(0f, 100f)
            fun label() = o.optString("label").replace("**", "").takeIf { it.isNotBlank() && it != "null" }
            val color = o.optString("color", "white")
            return when (o.optString("type")) {
                "rect" -> BoardShape.Rect(f("x"), f("y"), f("w"), f("h"), label(), color)
                "circle" -> BoardShape.Circle(f("x"), f("y"), f("r"), label(), color)
                "line" -> BoardShape.Line(f("x1"), f("y1"), f("x2"), f("y2"), arrow = false, color = color)
                "arrow" -> BoardShape.Line(f("x1"), f("y1"), f("x2"), f("y2"), arrow = true, color = color, label = label())
                "text" -> o.optString("text").replace("**", "").takeIf { it.isNotBlank() }?.let { BoardShape.Label(f("x"), f("y"), it, color) }
                else -> null
            }
        }
    }
}
