package com.vastutalks.app.ui.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vastutalks.app.ui.theme.TextBody
import com.vastutalks.app.ui.theme.VastuIndigo
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.ui.theme.CardBorder
import com.vastutalks.app.ui.theme.NightDeep
import com.vastutalks.app.ui.theme.NightMid
import com.vastutalks.app.ui.theme.NightOrbCore
import com.vastutalks.app.ui.theme.NightPlum
import com.vastutalks.app.ui.theme.TextHint
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuLilac
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuSun

// Shared pieces of the "Vastu Talks (Make)" design.

private val OrbColors = listOf(VastuLilac, Color(0xFF6366F1), Color(0xFFF472B6), VastuSun, VastuLilac)

/** The app mark: a compass ring with a yellow needle. */
@Composable
fun CompassMark(size: Dp, modifier: Modifier = Modifier, ring: Color = Color.White, needle: Color = VastuSun) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        drawCircle(ring, radius = 10f * s, style = Stroke(width = 1.6f * s))
        val p = Path().apply {
            moveTo(16.24f * s, 7.76f * s)
            lineTo(14.12f * s, 14.12f * s)
            lineTo(7.76f * s, 16.24f * s)
            lineTo(9.88f * s, 9.88f * s)
            close()
        }
        drawPath(p, needle)
    }
}

/**
 * Ananya's orb: a spinning rainbow ring around a dark core with the
 * compass. [spinMillis] sets how fast the ring turns.
 */
@Composable
fun AiOrb(size: Dp, modifier: Modifier = Modifier, spinMillis: Int = 6000, ringWidth: Dp = 4.dp, iconSize: Dp = size * 0.4f, animated: Boolean = true) {
    val angle = if (animated) {
        val spin = rememberInfiniteTransition(label = "orb")
        spin.animateFloat(0f, 360f, infiniteRepeatable(tween(spinMillis, easing = LinearEasing)), label = "angle").value
    } else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(VastuLilac.copy(alpha = 0.45f), Color.Transparent)), radius = this.size.minDimension * 0.62f)
                }
        )
        Box(
            Modifier
                .fillMaxSize()
                .rotate(angle)
                .clip(CircleShape)
                .background(Brush.sweepGradient(OrbColors))
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(ringWidth)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF3B2F8F), NightOrbCore), center = Offset.Unspecified)),
            contentAlignment = Alignment.Center
        ) {
            CompassMark(iconSize)
        }
    }
}

/** Night-sky panel with violet/pink glows and a faint dot grid. */
@Composable
fun NightHero(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(NightDeep, NightMid, NightPlum)))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(VastuPrimary.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width, 0f), radius = 520f),
                    radius = 520f, center = Offset(size.width, 0f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFDB2777).copy(alpha = 0.32f), Color.Transparent), center = Offset(0f, size.height), radius = 480f),
                    radius = 480f, center = Offset(0f, size.height)
                )
                val step = 18.dp.toPx()
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(Color.White.copy(alpha = 0.07f), radius = 1.dp.toPx(), center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            },
        content = content
    )
}

/** Soft lavender page background with two blurred colour blobs. */
fun Modifier.softWashBackground(): Modifier = this
    .background(Brush.linearGradient(listOf(Color(0xFFEEF2FF), Color(0xFFFAF5FF), Color(0xFFFDF2F8))))
    .drawBehind {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFE9D5FF).copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width, 0f), radius = 600f),
            radius = 600f, center = Offset(size.width, 0f)
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFBCFE8).copy(alpha = 0.5f), Color.Transparent), center = Offset(0f, size.height), radius = 650f),
            radius = 650f, center = Offset(0f, size.height)
        )
    }

/** Solid violet call to action, 50dp tall. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    height: Dp = 50.dp,
    brush: Brush? = null,
    radius: Dp = 14.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    iconSize: Dp = 18.dp
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .then(if (brush != null && enabled) Modifier.background(brush) else Modifier.background(VastuPrimary.copy(alpha = if (enabled) 1f else 0.4f)))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
        if (trailingIcon != null) {
            Spacer(Modifier.width(7.dp))
            Icon(trailingIcon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** White button with a hairline border. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = TextPrimary,
    height: Dp = 50.dp,
    borderColor: Color = Color(0xFFE7E7EE),
    textColor: Color = TextPrimary,
    leading: (@Composable () -> Unit)? = null,
    radius: Dp = 14.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.5.sp,
    iconSize: Dp = 17.dp
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(Color.White)
            .border(1.dp, borderColor, RoundedCornerShape(radius))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        if (icon != null) Icon(icon, null, tint = iconTint, modifier = Modifier.size(iconSize))
        if (leading != null || icon != null) Spacer(Modifier.width(if (icon != null && leading == null) 7.dp else 9.dp))
        Text(text, color = textColor, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
    }
}

/** Text tabs with a violet underline under the selected one. */
@Composable
fun UnderlineTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(CardBorder)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Column(
                    Modifier
                        .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                        .clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        label,
                        color = if (on) TextPrimary else TextHint,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (on) VastuPrimary else Color.Transparent)
                    )
                }
            }
        }
    }
}

/** Small rounded square icon button with a hairline border (header actions). */
@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = TextPrimary,
    background: Color = Color.White,
    borderColor: Color? = CardBorder,
    badge: Boolean = false
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(18.dp))
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 9.dp, end = 10.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF6B6B))
            )
        }
    }
}

/** White rounded card with the design's hairline border. */
@Composable
fun DesignCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(Color.White)
            .border(1.dp, CardBorder, RoundedCornerShape(radius))
            .padding(padding),
        content = content
    )
}

/** A frosted pill on dark backgrounds (chips in the night hero). */
@Composable
fun GlassPill(text: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        leading?.invoke()
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

/** "256-bit encrypted · Your data stays private" line under the auth forms. */
@Composable
fun SecureNote(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.VerifiedUser, null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("256-bit encrypted · Your data stays private", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** "or" divider between two auth options. */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFEFEFF4)))
        Text("or", color = TextHint, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFEFEFF4)))
    }
}

/** The design's dark pill toast ("Brass Vastu Pyramid added to cart"). */
@Composable
fun DesignToast(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.25.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(TextPrimary)
            .padding(horizontal = 17.5.dp, vertical = 8.75.dp)
    )
}

/** Diagonal-striped picture placeholder with a small mono label (store products, posts without a photo). */
@Composable
fun HatchedPlaceholder(label: String, modifier: Modifier = Modifier, radius: Dp = 12.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(Color(0xFFF1EFF8))
            .drawBehind {
                val step = 16.dp.toPx()
                val stripe = 8.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(
                        Color(0xFFE9E6F3),
                        start = Offset(x, size.height),
                        end = Offset(x + size.height, 0f),
                        strokeWidth = stripe * 0.7071f
                    )
                    x += step / 0.7071f
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFF8B85A8), fontSize = 9.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

/**
 * The design's "EN | हिं" pill toggle. Light: lavender track, white selected
 * pill in violet. [dark]: frosted track on night screens, white pill in deep purple.
 */
@Composable
fun DesignLangToggle(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    height: Dp = 28.dp,
    onChanged: (com.vastutalks.app.data.ai.AgentLanguage) -> Unit = {}
) {
    val current = com.vastutalks.app.data.ai.AgentLanguagePref.current
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (dark) Color.White.copy(alpha = 0.1f) else Color(0xFFF1EFF8))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(com.vastutalks.app.data.ai.AgentLanguage.ENGLISH to "EN", com.vastutalks.app.data.ai.AgentLanguage.HINDI to "हिं").forEach { (language, label) ->
            val on = current == language
            Box(
                Modifier
                    .height(height)
                    .defaultMinSize(minWidth = if (dark) 36.dp else 40.dp)
                    .then(if (on && !dark) Modifier.shadow(3.dp, RoundedCornerShape(999.dp), ambientColor = VastuPrimary, spotColor = VastuPrimary) else Modifier)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable {
                        if (!on) {
                            com.vastutalks.app.data.ai.AgentLanguagePref.set(language)
                            onChanged(language)
                        }
                    }
                    .padding(horizontal = if (dark) 9.dp else 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = when {
                        on && dark -> Color(0xFF4C1D95)
                        on -> VastuPrimary
                        dark -> Color.White.copy(alpha = 0.75f)
                        else -> TextSecondary
                    },
                    fontSize = if (dark) 11.sp else 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** The design's 44×26 toggle switch. */
@Composable
fun DesignSwitch(checked: Boolean, onToggle: () -> Unit, contentDescription: String, modifier: Modifier = Modifier) {
    val knob by androidx.compose.animation.core.animateDpAsState(if (checked) 18.dp else 0.dp, label = "knob")
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) VastuPrimary else Color(0xFFE2E8F0))
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onToggle)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(20.dp)
                .shadow(1.5.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * The design's bottom sheet: dark scrim over the screen and a white card
 * rising from the bottom (top corners [radius]). Tapping the scrim or Back
 * closes it. Put it last inside a full-screen Box.
 */
@Composable
fun DesignSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    scrim: Color = Color.Black.copy(alpha = 0.6f),
    contentPadding: Dp = 24.5.dp,
    showHandle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        modifier
            .fillMaxSize()
            .background(scrim)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .imePadding(), // the sheet rides up on the keyboard instead of hiding under it
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .wrapContentHeight(Alignment.Bottom)
                .clip(RoundedCornerShape(topStart = radius, topEnd = radius))
                .background(Color.White)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            if (showHandle) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 6.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFE2E8F0))
                )
            }
            content()
        }
    }
}

/** Sheet title with the round grey close button. */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, gradientTitle: Boolean = false) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (gradientTitle) {
            Text(
                title,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(listOf(VastuPrimary, VastuIndigo))),
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        }
        Box(
            Modifier
                .size(if (gradientTitle) 35.dp else 34.dp)
                .clip(CircleShape)
                .background(if (gradientTitle) Color(0xFFF3F4F6) else Color(0xFFF4F4F8))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, "Close", tint = if (gradientTitle) TextPrimary else TextBody, modifier = Modifier.size(if (gradientTitle) 20.dp else 17.dp))
        }
    }
}
