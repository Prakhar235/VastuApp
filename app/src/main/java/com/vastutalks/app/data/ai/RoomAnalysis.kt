package com.vastutalks.app.data.ai

import android.graphics.Bitmap
import org.json.JSONObject

/** One wall's photo from a room scan, and which way the camera faced when it was taken. */
class WallPhoto(val bitmap: Bitmap, val facing: PhotoFacing)

/** The nine Vastu zones of a room, as a 3×3 grid with North at the top. */
enum class RoomZone(val row: Int, val col: Int, val english: String, val hindi: String) {
    NW(0, 0, "NW", "उ-प"),
    N(0, 1, "North · wealth", "उत्तर · धन"),
    NE(0, 2, "NE · Ishan", "उ-पू · ईशान"),
    W(1, 0, "West", "पश्चिम"),
    CENTER(1, 1, "Brahmasthan", "ब्रह्मस्थान"),
    E(1, 2, "East", "पूर्व"),
    SW(2, 0, "SW", "द-प"),
    S(2, 1, "South", "दक्षिण"),
    SE(2, 2, "SE · Agni", "द-पू · अग्नि");

    val label: String get() = tr(english, hindi)

    companion object {
        fun parse(code: String?): RoomZone? = when (code?.trim()?.uppercase()) {
            null, "", "NULL" -> null
            "C", "CENTRE", "CENTER", "BRAHMASTHAN" -> CENTER
            else -> entries.firstOrNull { it.name == code.trim().uppercase() }
        }
    }
}

enum class Severity { MAJOR, MINOR, GOOD }

/**
 * One thing Ananya judged in the room: where it is in which photo, how
 * serious it is, why, how to fix it without moving it, and — when it
 * should move — the zone it belongs in plus a few questions to check the
 * move is possible.
 */
data class RoomFinding(
    val number: Int,
    val title: String,
    val objectName: String,
    val severity: Severity,
    val photo: Int,
    val mark: BoardShape?,
    val zone: RoomZone?,
    val betterZone: RoomZone?,
    val why: String,
    val remedies: List<String>,
    val checks: List<String>
)

data class RoomAnalysis(
    val room: String,
    val score: Int,
    val verdict: String,
    val summary: String,
    val findings: List<RoomFinding>
) {
    /** The photo with its findings drawn on it, numbered, for sharing into the chat. */
    fun markedPhoto(walls: List<WallPhoto>, photo: Int): Bitmap? {
        val wall = walls.getOrNull(photo) ?: return null
        val shapes = findings.filter { it.photo == photo }.mapNotNull { f ->
            val color = when (f.severity) { Severity.MAJOR -> "red"; Severity.MINOR -> "saffron"; Severity.GOOD -> "green" }
            when (val m = f.mark) {
                is BoardShape.Rect -> m.copy(label = "${f.number} ${f.objectName}", color = color)
                is BoardShape.Circle -> m.copy(label = "${f.number} ${f.objectName}", color = color)
                else -> null
            }
        }
        return ImageMarkup.draw(wall.bitmap, shapes)
    }

    /** The whole read as text, for the chat and for her memory of it. */
    fun asText(): String = buildString {
        append("$room · ${tr("Vastu score", "वास्तु स्कोर")} $score/100 ($verdict). $summary")
        findings.forEach { f ->
            append("\n${f.number}. ${f.title} — ${f.why}")
            if (f.betterZone != null) append(" ${tr("Better spot", "बेहतर जगह")}: ${f.betterZone.label}.")
            if (f.severity != Severity.GOOD && f.remedies.isNotEmpty()) {
                append(" **${f.remedies.joinToString("; ")}**")
            }
        }
    }

    companion object {
        /** What the model is asked to return for a scan (see VastuAgent.analyzeRoom). */
        fun prompt(): String = """
            You are ${com.vastutalks.app.data.model.AnanyaAgent.NAME}, a practical Residential Vastu consultant in the
            VastuTalks app. The user photographed the walls of ONE room, guided by the phone's compass. Each photo
            has a faint yellow grid numbered along the top edge (x, 0 to 100, left to right) and the left edge
            (y, 0 to 100, top to bottom); the user sees it without the grid.

            Do a full Vastu read of the room: work out what room it is; find every Vastu-relevant thing you can
            really see (doors, windows, bed, stove, sink, mirror, desk, safe, puja shelf or idols, TV and
            electronics, heavy furniture, plants, water, clocks, pictures, colours, clutter, beams, light); work out
            which of the nine zones of the room each is in from the camera directions given; and judge each one.
            Never invent an object you can't see. At most 8 findings, problems first (major, then minor), then
            what is already good.

            ${languageRule()}

            Reply with a single JSON object:
            {
              "room": "Living room",
              "score": 68,
              "verdict": "Fair",
              "summary": "1 major issue, 1 minor. 2 things are well placed.",
              "findings": [
                {
                  "title": "TV on the North wall",
                  "object": "TV",
                  "severity": "major",
                  "photo": 0,
                  "mark": {"type": "rect", "x": 24, "y": 30, "w": 38, "h": 26},
                  "zone": "N",
                  "better_zone": "SE",
                  "why": "North is the wealth zone; heavy electronics block it. The TV is fire element, which belongs in the South-East.",
                  "remedies": ["Cover the TV with a cloth when it's off", "Keep a green plant on the TV unit"],
                  "checks": ["Is there a power socket on the South-East wall?", "Is there room for the TV unit there (~1.8 m)?"]
                }
              ]
            }
            score: 0-100 for the whole room. verdict: 1-2 words. summary: one short sentence counting the findings.
            title: 3-6 words naming the thing and where it is. object: 1-2 words. severity: "major", "minor" or
            "good". photo: index of the photo it is in (0 = first). mark: where it is in that photo, in percent of
            the image — "rect" with x, y, w, h for big things, or "circle" with x, y and r (r is percent of the
            width) for small ones — aimed right on the object. zone and better_zone: one of NW, N, NE, W, C, E, SW, S,
            SE (C = centre / Brahmasthan); better_zone only when the thing should move, else null. why: 1-2
            sentences. remedies: for problems, 1-3 easy fixes that don't need moving it; [] for good things.
            checks: only with better_zone, 1-2 yes/no questions to see if the move is practical; else [].
        """.trimIndent()

        private fun languageRule(): String = if (AgentLanguagePref.isHindi) """
            Write every text field in simple everyday Hindi in Devanagari script, preferring Hindi words over
            Urdu/Persian ones; common English words people use in Hindi are fine (टीवी, बेडरूम). Name directions in
            Hindi (उत्तर, दक्षिण-पूर्व). The example values below are in English only to show the shape.
        """.trimIndent() else """
            Write every text field in simple, clear English.
        """.trimIndent()

        fun parse(raw: String, photoCount: Int): RoomAnalysis {
            val json = JSONObject(raw)
            val list = json.optJSONArray("findings")
            val findings = (0 until (list?.length() ?: 0)).mapNotNull { i -> list!!.optJSONObject(i) }
                .take(8)
                .map { o ->
                    RoomFinding(
                        number = 0,
                        title = o.optString("title").clean(),
                        objectName = o.optString("object").clean().ifBlank { o.optString("title").clean() },
                        severity = when (o.optString("severity").lowercase()) {
                            "major" -> Severity.MAJOR
                            "good", "ok" -> Severity.GOOD
                            else -> Severity.MINOR
                        },
                        photo = o.optInt("photo", 0).coerceIn(0, maxOf(0, photoCount - 1)),
                        mark = parseMark(o.optJSONObject("mark")),
                        zone = RoomZone.parse(o.optString("zone")),
                        betterZone = RoomZone.parse(o.optString("better_zone")),
                        why = o.optString("why").clean(),
                        remedies = o.optJSONArray("remedies").strings(),
                        checks = o.optJSONArray("checks").strings()
                    )
                }
                .filter { it.title.isNotBlank() }
                .sortedBy { it.severity.ordinal }
                .mapIndexed { i, f -> f.copy(number = i + 1, betterZone = f.betterZone?.takeIf { it != f.zone }) }
            return RoomAnalysis(
                room = json.optString("room").clean().ifBlank { tr("Room", "कमरा") },
                score = json.optInt("score", 0).coerceIn(0, 100),
                verdict = json.optString("verdict").clean(),
                summary = json.optString("summary").clean(),
                findings = findings
            )
        }

        private fun parseMark(o: JSONObject?): BoardShape? {
            if (o == null) return null
            fun f(key: String) = o.optDouble(key, 0.0).toFloat().coerceIn(0f, 100f)
            return when (o.optString("type")) {
                "rect" -> BoardShape.Rect(f("x"), f("y"), f("w").coerceAtMost(100f - f("x")), f("h").coerceAtMost(100f - f("y")), null, "white")
                "circle" -> BoardShape.Circle(f("x"), f("y"), f("r"), null, "white")
                else -> null
            }
        }

        private fun String.clean() = replace("**", "").trim().takeUnless { it == "null" }.orEmpty()

        private fun org.json.JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { optString(it).clean() }.filter { it.isNotBlank() }
    }
}
