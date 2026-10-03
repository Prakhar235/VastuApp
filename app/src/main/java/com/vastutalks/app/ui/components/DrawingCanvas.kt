package com.vastutalks.app.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.ui.theme.VastuCopper
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuSaffron

private data class DrawnStroke(val path: Path, val color: Color)

private val palette = listOf(Color.White, VastuSaffron, VastuCopper, Color(0xFF3E8EDE), Color(0xFFE24C4C))

/**
 * A local, in-memory freehand drawing surface shown over the in-call
 * screen — a demo of "expert sketches something during a
 * consultation," not a real shared whiteboard yet.
 *
 * On Done, whatever was drawn is rendered to a bitmap and handed back
 * via [onClose] (null if nothing was drawn) so the caller can post it
 * into the chat — see DemoInCallScreen.
 *
 * What's NOT here (needed for a real version): the strokes drawn here
 * only exist on this device and are never sent to the other
 * participant in real time — only the final flattened image gets
 * shared, and only into this same device's own demo chat. A real
 * implementation would broadcast each stroke as it's drawn — e.g. via
 * Agora's low-latency data channel (`RtcEngine.sendStreamMessage`)
 * for real-time sync during the call, or a Firestore document under
 * the call for a simpler (higher latency) version.
 */
@Composable
fun DrawingCanvasOverlay(
    timerLabel: String,
    peerName: String,
    peerSpecialty: String,
    avatarUrl: String,
    onClose: (ImageBitmap?) -> Unit
) {
    val strokes = remember { mutableStateOf(listOf<DrawnStroke>()) }
    var currentColor by remember { mutableStateOf(palette.first()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun finishAndClose() {
        if (strokes.value.isEmpty() || canvasSize.width == 0 || canvasSize.height == 0) {
            onClose(null)
            return
        }
        val bitmap = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        val androidCanvas = android.graphics.Canvas(bitmap)
        androidCanvas.drawColor(android.graphics.Color.parseColor("#1A1824"))
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        strokes.value.forEach { stroke ->
            paint.color = stroke.color.toArgb()
            androidCanvas.drawPath(stroke.path.asAndroidPath(), paint)
        }
        onClose(bitmap.asImageBitmap())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1824))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(currentColor) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                        },
                        onDrag = { change, _ ->
                            currentPath?.lineTo(change.position.x, change.position.y)
                            change.consume()
                        },
                        onDragEnd = {
                            currentPath?.let { path ->
                                strokes.value = strokes.value + DrawnStroke(path, currentColor)
                            }
                            currentPath = null
                        }
                    )
                }
        ) {
            strokes.value.forEach { stroke ->
                drawPath(
                    stroke.path,
                    color = stroke.color,
                    style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            currentPath?.let { path ->
                drawPath(
                    path,
                    color = currentColor,
                    style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // Top bar: same calling profile + timer as the call screen, plus close.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                coil.compose.AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.White)
                )
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        peerName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        "  ·  $peerSpecialty",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        timerLabel,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(VastuPrimary)
                        .clickable { finishAndClose() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        "Done",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Bottom bar: color palette + clear.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == currentColor) 3.dp else 0.dp,
                                color = Color.White.copy(alpha = 0.9f),
                                shape = CircleShape
                            )
                            .clickable { currentColor = color }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { strokes.value = emptyList() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
