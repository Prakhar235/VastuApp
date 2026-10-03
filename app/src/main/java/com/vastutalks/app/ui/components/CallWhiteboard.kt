package com.vastutalks.app.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.vastutalks.app.data.ai.BoardDrawing
import com.vastutalks.app.data.ai.BoardShape
import com.vastutalks.app.ui.theme.VastuCopper
import com.vastutalks.app.ui.theme.VastuSaffron
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val MS_PER_SHAPE = 550
private val BoardBackground = Color(0xFF1A1824)

val WhiteboardPalette = listOf(Color.White, VastuSaffron, VastuCopper, Color(0xFF3E8EDE), Color(0xFFE24C4C))

/** A finger stroke, with points stored as 0–1 fractions of the board so it renders the same at any size. */
data class UserStroke(val points: List<Offset>, val color: Color)

/**
 * Everything on the demo call's shared whiteboard: the caller's
 * freehand strokes plus Ananya's latest drawing (from VastuAgent),
 * both on the same square grid with North at the top.
 */
class WhiteboardState {
    val userStrokes = mutableStateListOf<UserStroke>()
    var agentDrawing by mutableStateOf<BoardDrawing?>(null)
        private set
    internal val agentProgress = Animatable(0f)

    /** True when the board holds something that hasn't been saved as a picture yet. */
    var hasUnsavedChanges by mutableStateOf(false)
        private set

    val isEmpty: Boolean get() = userStrokes.isEmpty() && agentDrawing == null

    fun addStroke(stroke: UserStroke) {
        userStrokes.add(stroke)
        hasUnsavedChanges = true
    }

    fun undo() {
        if (userStrokes.isNotEmpty()) userStrokes.removeAt(userStrokes.lastIndex)
    }

    fun showAgentDrawing(drawing: BoardDrawing) {
        agentDrawing = drawing
        hasUnsavedChanges = true
    }

    fun clear() {
        userStrokes.clear()
        agentDrawing = null
        hasUnsavedChanges = false
    }

    fun markSaved() {
        hasUnsavedChanges = false
    }

    /** The whole board, fully drawn (no animation), as a square bitmap — for saving and for sending to the agent. */
    fun render(sizePx: Int = 1024): Bitmap {
        val image = ImageBitmap(sizePx, sizePx)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(sizePx.toFloat(), sizePx.toFloat())) {
            drawBoard(userStrokes, current = null, currentColor = Color.White, agentDrawing, agentProgress = Float.MAX_VALUE)
        }
        return image.asAndroidBitmap()
    }
}

@Composable
fun rememberWhiteboardState(): WhiteboardState = remember { WhiteboardState() }

/** The board itself: square, as large as fits, drawable by finger, animating Ananya's drawings in stroke by stroke. */
@Composable
fun CallWhiteboard(state: WhiteboardState, penColor: Color, modifier: Modifier = Modifier) {
    val current = remember { mutableStateListOf<Offset>() }

    LaunchedEffect(state.agentDrawing) {
        val drawing = state.agentDrawing ?: return@LaunchedEffect
        state.agentProgress.snapTo(0f)
        state.agentProgress.animateTo(
            drawing.shapes.size.toFloat(),
            tween(durationMillis = drawing.shapes.size * MS_PER_SHAPE, easing = LinearEasing)
        )
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, maxHeight.value).dp
        Canvas(
            modifier = Modifier
                .size(side)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                .pointerInput(penColor) {
                    fun norm(o: Offset) = Offset(o.x / size.width, o.y / size.height)
                    detectDragGestures(
                        onDragStart = { current.clear(); current.add(norm(it)) },
                        onDrag = { change, _ -> current.add(norm(change.position)); change.consume() },
                        onDragEnd = {
                            if (current.size > 1) state.addStroke(UserStroke(current.toList(), penColor))
                            current.clear()
                        },
                        onDragCancel = { current.clear() }
                    )
                }
                .pointerInput(penColor) {
                    detectTapGestures { o ->
                        val p = Offset(o.x / size.width, o.y / size.height)
                        state.addStroke(UserStroke(listOf(p, p), penColor))
                    }
                }
        ) {
            drawBoard(state.userStrokes, current, penColor, state.agentDrawing, state.agentProgress.value)
        }
    }
}

private fun DrawScope.drawBoard(
    strokes: List<UserStroke>,
    current: List<Offset>?,
    currentColor: Color,
    agentDrawing: BoardDrawing?,
    agentProgress: Float
) {
    drawRect(BoardBackground)
    drawGrid()
    drawCompass()
    strokes.forEach { drawUserStroke(it.points, it.color) }
    if (!current.isNullOrEmpty()) drawUserStroke(current, currentColor)
    agentDrawing?.let { drawing ->
        if (drawing.title.isNotBlank()) {
            drawLabel(drawing.title, Offset(size.width / 2f, size.height * 0.04f), Color.White, alpha = 0.85f)
        }
        drawing.shapes.forEachIndexed { i, shape ->
            val fraction = (agentProgress - i).coerceIn(0f, 1f)
            if (fraction > 0f) drawShape(shape, fraction)
        }
    }
}

private val DrawScope.unit get() = size.minDimension / 100f

private fun DrawScope.drawGrid() {
    val color = Color.White.copy(alpha = 0.05f)
    for (i in 1 until 10) {
        val x = size.width * i / 10f
        val y = size.height * i / 10f
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
    }
}

private fun DrawScope.drawUserStroke(points: List<Offset>, color: Color) {
    val path = Path()
    points.forEachIndexed { i, p ->
        val x = p.x * size.width
        val y = p.y * size.height
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = unit * 0.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Lenient on purpose — the model sometimes invents shades like "lightgreen". */
private fun boardColor(name: String): Color = name.lowercase().let { n ->
    when {
        "saffron" in n || "yellow" in n || "orange" in n || "gold" in n -> VastuSaffron
        "copper" in n || "brown" in n -> VastuCopper
        "blue" in n -> Color(0xFF3E8EDE)
        "red" in n -> Color(0xFFE24C4C)
        "green" in n -> Color(0xFF4CAF7A)
        else -> Color.White
    }
}

private fun DrawScope.px(x: Float, y: Float) = Offset(x / 100f * size.width, y / 100f * size.height)

private fun DrawScope.drawShape(shape: BoardShape, fraction: Float) {
    val color = boardColor(shape.color)
    val stroke = Stroke(width = unit * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (shape) {
        is BoardShape.Rect -> {
            val r = Rect(px(shape.x, shape.y), px(shape.x + shape.w, shape.y + shape.h))
            drawPartial(Path().apply { addRect(r) }, fraction, color, stroke)
            if (fraction >= 1f && shape.label != null) drawLabel(shape.label, r.center, color)
        }
        is BoardShape.Circle -> {
            val center = px(shape.x, shape.y)
            val radius = shape.r * unit
            drawPartial(Path().apply { addOval(Rect(center, radius)) }, fraction, color, stroke)
            if (fraction >= 1f && shape.label != null) drawLabel(shape.label, center + Offset(0f, radius + unit * 3f), color)
        }
        is BoardShape.Line -> {
            val start = px(shape.x1, shape.y1)
            val end = px(shape.x2, shape.y2)
            drawPartial(Path().apply { moveTo(start.x, start.y); lineTo(end.x, end.y) }, fraction, color, stroke)
            if (shape.arrow && fraction >= 1f) {
                val angle = atan2(end.y - start.y, end.x - start.x)
                val head = unit * 2.5f
                listOf(angle + 2.6f, angle - 2.6f).forEach { a ->
                    drawLine(color, end, end + Offset(head * cos(a), head * sin(a)), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
        }
        is BoardShape.Label -> drawLabel(shape.text, px(shape.x, shape.y), color, alpha = fraction)
    }
}

/** Draws only the first [fraction] of [path]'s length, so shapes appear to be drawn by hand. */
private fun DrawScope.drawPartial(path: Path, fraction: Float, color: Color, stroke: Stroke) {
    if (fraction >= 1f) {
        drawPath(path, color, style = stroke)
        return
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val segment = Path()
    measure.getSegment(0f, measure.length * fraction, segment, true)
    drawPath(segment, color, style = stroke)
}

private fun DrawScope.drawLabel(text: String, at: Offset, color: Color, alpha: Float = 1f) {
    val paint = Paint().apply {
        this.color = color.copy(alpha = alpha).toArgb()
        textSize = unit * 3.2f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    drawIntoCanvas { it.nativeCanvas.drawText(text, at.x, at.y + paint.textSize / 3f, paint) }
}

private fun DrawScope.drawCompass() {
    val color = Color.White.copy(alpha = 0.45f)
    val top = Offset(size.width - unit * 5f, unit * 3f)
    val w = unit * 0.4f
    drawLine(color, top + Offset(0f, unit * 5f), top, strokeWidth = w, cap = StrokeCap.Round)
    drawLine(color, top, top + Offset(-unit, unit * 1.5f), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(color, top, top + Offset(unit, unit * 1.5f), strokeWidth = w, cap = StrokeCap.Round)
    drawLabel("N", top + Offset(0f, unit * 7.5f), Color.White, alpha = 0.55f)
}
