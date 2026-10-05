package com.vastutalks.app.data.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The agent's marks on the caller's own photo (or one frame of their
 * video). Shapes use percent of the image: x of its width (left→right),
 * y of its height (top→bottom); a circle's r is percent of the width.
 */
data class PhotoMarks(val frame: Int, val shapes: List<BoardShape>)

/** Draws on real photos: a coordinate grid for the model, and the agent's marks for the caller. */
object ImageMarkup {

    /**
     * Copy of [src] with a faint numbered 10% grid, sent to the model so it
     * can say where things are in the picture. The caller never sees it.
     */
    fun withGrid(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val unit = minOf(w, h) / 100f
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            strokeWidth = maxOf(1f, unit * 0.25f)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFF00.toInt()
            textSize = unit * 3.6f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(unit * 0.6f, 0f, 0f, 0xFF000000.toInt())
        }
        for (i in 1..9) {
            val x = w * i / 10f
            val y = h * i / 10f
            canvas.drawLine(x, 0f, x, h, line)
            canvas.drawLine(0f, y, w, y, line)
            canvas.drawText("${i * 10}", x + unit * 0.6f, text.textSize + unit * 0.5f, text)
            canvas.drawText("${i * 10}", unit * 0.6f, y - unit * 0.6f, text)
        }
        return out
    }

    /** Copy of [src] with the agent's [shapes] drawn on it, labels in readable pills. */
    fun draw(src: Bitmap, shapes: List<BoardShape>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val unit = minOf(w, h) / 100f
        fun px(x: Float) = x / 100f * w
        fun py(y: Float) = y / 100f * h

        shapes.forEach { shape ->
            val color = markColor(shape.color)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = unit * 1.1f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                setShadowLayer(unit * 0.8f, 0f, 0f, 0xAA000000.toInt())
            }
            when (shape) {
                is BoardShape.Circle -> {
                    val cx = px(shape.x)
                    val cy = py(shape.y)
                    val r = maxOf(shape.r / 100f * w, unit * 3f)
                    canvas.drawCircle(cx, cy, r, stroke)
                    shape.label?.let { drawLabel(canvas, it, cx, cy + r + unit * 4f, color, unit) }
                }
                is BoardShape.Rect -> {
                    val r = RectF(px(shape.x), py(shape.y), px(shape.x + shape.w), py(shape.y + shape.h))
                    canvas.drawRoundRect(r, unit * 1.5f, unit * 1.5f, stroke)
                    shape.label?.let { drawLabel(canvas, it, r.centerX(), r.bottom + unit * 4f, color, unit) }
                }
                is BoardShape.Line -> {
                    val x1 = px(shape.x1); val y1 = py(shape.y1)
                    val x2 = px(shape.x2); val y2 = py(shape.y2)
                    canvas.drawLine(x1, y1, x2, y2, stroke)
                    if (shape.arrow) {
                        val angle = atan2(y2 - y1, x2 - x1)
                        val head = unit * 4f
                        val path = Path().apply {
                            moveTo(x2 + head * cos(angle + 2.6f), y2 + head * sin(angle + 2.6f))
                            lineTo(x2, y2)
                            lineTo(x2 + head * cos(angle - 2.6f), y2 + head * sin(angle - 2.6f))
                        }
                        canvas.drawPath(path, stroke)
                    }
                    shape.label?.let { drawLabel(canvas, it, (x1 + x2) / 2f, (y1 + y2) / 2f - unit * 3f, color, unit) }
                }
                is BoardShape.Label -> drawLabel(canvas, shape.text, px(shape.x), py(shape.y), color, unit)
            }
        }
        return out
    }

    /** Text on a dark pill with a coloured edge, kept inside the image. */
    private fun drawLabel(canvas: Canvas, text: String, cx: Float, cy: Float, color: Int, unit: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = unit * 4.2f
            typeface = Typeface.DEFAULT_BOLD
        }
        val padX = unit * 1.8f
        val padY = unit * 1.1f
        val textW = paint.measureText(text)
        val boxW = textW + padX * 2
        val boxH = paint.textSize + padY * 2
        val left = (cx - boxW / 2f).coerceIn(unit, canvas.width - boxW - unit)
        val top = (cy - boxH / 2f).coerceIn(unit, canvas.height - boxH - unit)
        val box = RectF(left, top, left + boxW, top + boxH)
        canvas.drawRoundRect(box, boxH / 2f, boxH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xDD15121F.toInt() })
        canvas.drawRoundRect(box, boxH / 2f, boxH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = unit * 0.5f
        })
        canvas.drawText(text, left + padX, top + padY + paint.textSize * 0.8f, paint)
    }

    /** Lenient on purpose — the model sometimes invents shades like "lightgreen". */
    private fun markColor(name: String): Int = name.lowercase().let { n ->
        when {
            "red" in n -> 0xFFFF4D4D.toInt()
            "green" in n -> 0xFF3DDC84.toInt()
            "blue" in n -> 0xFF4DA3FF.toInt()
            "saffron" in n || "yellow" in n || "orange" in n || "gold" in n -> 0xFFFFB020.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
    }
}
