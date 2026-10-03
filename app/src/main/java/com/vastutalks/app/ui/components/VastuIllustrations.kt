package com.vastutalks.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateDrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.ui.theme.VastuCharcoal
import com.vastutalks.app.ui.theme.VastuCopper
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuSaffron
import kotlin.math.cos
import kotlin.math.sin

enum class VastuIllustrationType { COMPASS, HOUSE, OM, WATER, FLAME, PLANT, MIRROR, YANTRA }

/** Picks a thematically-relevant illustration based on keywords in a post's text, falling back to a generic mandala. */
fun illustrationTypeFor(postText: String): VastuIllustrationType {
    val text = postText.lowercase()
    return when {
        "fire" in text || "kitchen" in text || "stove" in text || "flame" in text -> VastuIllustrationType.FLAME
        "water" in text || "fountain" in text || "aquarium" in text || "fish" in text -> VastuIllustrationType.WATER
        "tulsi" in text || "plant" in text || "garden" in text || "basil" in text -> VastuIllustrationType.PLANT
        "mirror" in text -> VastuIllustrationType.MIRROR
        "pooja" in text || "prayer" in text -> VastuIllustrationType.OM
        "compass" in text || "north" in text || "south" in text || "east" in text || "west" in text || "direction" in text || "zone" in text -> VastuIllustrationType.COMPASS
        "door" in text || "entrance" in text || "staircase" in text || "house" in text || "room" in text || "office" in text || "bedroom" in text -> VastuIllustrationType.HOUSE
        else -> VastuIllustrationType.YANTRA
    }
}

@Composable
fun VastuIllustration(type: VastuIllustrationType, modifier: Modifier = Modifier) {
    val backgroundTint = when (type) {
        VastuIllustrationType.FLAME -> Color(0xFFFFF1E6)
        VastuIllustrationType.WATER -> Color(0xFFE8F1FF)
        VastuIllustrationType.PLANT -> Color(0xFFEAF7E9)
        VastuIllustrationType.MIRROR -> Color(0xFFF1F0F5)
        VastuIllustrationType.OM -> Color(0xFFFFF5E0)
        VastuIllustrationType.COMPASS -> Color(0xFFEFEBFF)
        VastuIllustrationType.HOUSE -> Color(0xFFEFF6FF)
        VastuIllustrationType.YANTRA -> Color(0xFFF6EFEA)
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier.background(backgroundTint),
        contentAlignment = Alignment.Center
    ) {
        when (type) {
            VastuIllustrationType.COMPASS -> CompassIllustration()
            VastuIllustrationType.HOUSE -> HouseIllustration()
            VastuIllustrationType.OM -> OmIllustration()
            VastuIllustrationType.WATER -> WaterDropIllustration()
            VastuIllustrationType.FLAME -> FlameIllustration()
            VastuIllustrationType.PLANT -> PlantIllustration()
            VastuIllustrationType.MIRROR -> MirrorIllustration()
            VastuIllustrationType.YANTRA -> YantraIllustration()
        }
    }
}

@Composable
fun CompassIllustration() {
    val transition = rememberInfiniteTransition(label = "compass")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "compass_rotation"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 3.2f
        drawCircle(VastuPrimary, radius, center, style = Stroke(width = 6f, cap = StrokeCap.Round))
        for (angleDeg in listOf(0, 90, 180, 270)) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val start = Offset(center.x + (radius - 14) * cos(rad).toFloat(), center.y + (radius - 14) * sin(rad).toFloat())
            val end = Offset(center.x + (radius + 8) * cos(rad).toFloat(), center.y + (radius + 8) * sin(rad).toFloat())
            drawLine(VastuCopper, start, end, strokeWidth = 5f, cap = StrokeCap.Round)
        }
        rotateDrawScope(degrees = rotation, pivot = center) {
            val needle = Path().apply {
                moveTo(center.x, center.y - radius + 6)
                lineTo(center.x - 12, center.y)
                lineTo(center.x, center.y + radius - 6)
                lineTo(center.x + 12, center.y)
                close()
            }
            drawPath(needle, VastuSaffron)
        }
        drawCircle(VastuCharcoal, 6f, center)
    }
}

@Composable
private fun HouseIllustration() {
    val transition = rememberInfiniteTransition(label = "house")
    val bounce by transition.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "house_bounce"
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .rotate(0f)
    ) {
        val w = size.width
        val h = size.height
        val bodyTop = h * 0.5f + bounce
        val bodyRect = androidx.compose.ui.geometry.Rect(w * 0.32f, bodyTop, w * 0.68f, h * 0.72f)
        drawRect(VastuPrimary, topLeft = bodyRect.topLeft, size = bodyRect.size)

        val roof = Path().apply {
            moveTo(w * 0.28f, bodyTop)
            lineTo(w * 0.5f, h * 0.3f + bounce)
            lineTo(w * 0.72f, bodyTop)
            close()
        }
        drawPath(roof, VastuCopper)

        val doorRect = androidx.compose.ui.geometry.Rect(w * 0.46f, h * 0.6f + bounce, w * 0.54f, h * 0.72f)
        drawRect(VastuSaffron, topLeft = doorRect.topLeft, size = doorRect.size)
    }
}

@Composable
private fun OmIllustration() {
    val transition = rememberInfiniteTransition(label = "om")
    val scaleAnim by transition.animateFloat(
        initialValue = 0.9f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "om_scale"
    )
    Text(
        text = "ॐ",
        color = VastuCopper,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        modifier = Modifier.scale(scaleAnim)
    )
}

@Composable
private fun WaterDropIllustration() {
    val transition = rememberInfiniteTransition(label = "water")
    val bob by transition.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "water_bob"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2
        val cy = size.height / 2 + bob
        val r = size.minDimension / 5f
        val drop = Path().apply {
            moveTo(cx, cy - r * 1.6f)
            quadraticTo(cx + r * 1.3f, cy - r * 0.2f, cx, cy + r)
            quadraticTo(cx - r * 1.3f, cy - r * 0.2f, cx, cy - r * 1.6f)
            close()
        }
        drawPath(drop, Color(0xFF3E8EDE))
        drawCircle(Color.White.copy(alpha = 0.5f), r * 0.25f, Offset(cx - r * 0.3f, cy - r * 0.1f))
    }
}

@Composable
private fun FlameIllustration() {
    val transition = rememberInfiniteTransition(label = "flame")
    val flicker by transition.animateFloat(
        initialValue = 0.92f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "flame_flicker"
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .scale(flicker)
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 4.5f
        val outer = Path().apply {
            moveTo(cx, cy - r * 1.8f)
            quadraticTo(cx + r * 1.4f, cy, cx, cy + r * 1.6f)
            quadraticTo(cx - r * 1.4f, cy, cx, cy - r * 1.8f)
            close()
        }
        drawPath(outer, Color(0xFFE8622C))
        val inner = Path().apply {
            moveTo(cx, cy - r * 0.9f)
            quadraticTo(cx + r * 0.7f, cy + r * 0.2f, cx, cy + r * 1.1f)
            quadraticTo(cx - r * 0.7f, cy + r * 0.2f, cx, cy - r * 0.9f)
            close()
        }
        drawPath(inner, VastuSaffron)
    }
}

@Composable
private fun PlantIllustration() {
    val transition = rememberInfiniteTransition(label = "plant")
    val sway by transition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "plant_sway"
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .rotate(sway)
    ) {
        val w = size.width
        val h = size.height
        val pot = Path().apply {
            moveTo(w * 0.4f, h * 0.62f)
            lineTo(w * 0.6f, h * 0.62f)
            lineTo(w * 0.56f, h * 0.78f)
            lineTo(w * 0.44f, h * 0.78f)
            close()
        }
        drawPath(pot, VastuCopper)
        val leafColor = Color(0xFF4C9A5B)
        drawOval(leafColor, topLeft = Offset(w * 0.32f, h * 0.3f), size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.32f))
        drawOval(leafColor, topLeft = Offset(w * 0.5f, h * 0.24f), size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.36f))
        drawOval(leafColor, topLeft = Offset(w * 0.5f, h * 0.42f), size = androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.3f))
    }
}

@Composable
private fun MirrorIllustration() {
    val transition = rememberInfiniteTransition(label = "mirror")
    val shine by transition.animateFloat(
        initialValue = 0.15f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "mirror_shine"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            Color(0xFFB9C4D6),
            topLeft = Offset(w * 0.3f, h * 0.2f),
            size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
        )
        drawLine(
            Color.White.copy(alpha = shine),
            Offset(w * 0.36f, h * 0.28f),
            Offset(w * 0.6f, h * 0.72f),
            strokeWidth = 10f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun YantraIllustration() {
    val transition = rememberInfiniteTransition(label = "yantra")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "yantra_rotation"
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .rotate(rotation)
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val cell = size.minDimension / 8f
        val colors = listOf(VastuPrimary, VastuCopper, VastuSaffron)
        var colorIndex = 0
        for (row in -1..1) {
            for (col in -1..1) {
                drawRect(
                    colors[colorIndex % colors.size].copy(alpha = 0.75f),
                    topLeft = Offset(cx + col * cell * 1.4f - cell / 2, cy + row * cell * 1.4f - cell / 2),
                    size = androidx.compose.ui.geometry.Size(cell, cell)
                )
                colorIndex++
            }
        }
    }
}
