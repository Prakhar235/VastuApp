package com.vastutalks.app.ui.screens.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.ui.components.PrimaryButton
import com.vastutalks.app.ui.components.softWashBackground
import com.vastutalks.app.ui.theme.PrimaryButtonGradient
import com.vastutalks.app.ui.theme.TextHint
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuSun
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val icon: ImageVector,
    val gradient: List<Color>,
    val glow: Color,
    val badge: String?,
    val title: String,
    val subtitle: String,
    val chipA: Pair<String, Color>,
    val chipB: Pair<String, Color>
)

private val slides = listOf(
    OnboardingSlide(
        icon = Icons.Outlined.Explore,
        gradient = listOf(Color(0xFFC084FC), Color(0xFF6366F1)),
        glow = Color(0xFFA78BFA),
        badge = null,
        title = "Welcome to Vastu Talks",
        subtitle = "Discover India’s First AI Vastu and transform your space with personalized Vastu insights for a happier, more harmonious life!",
        chipA = "AI-powered Vastu" to VastuPrimary,
        chipB = "Instant insights" to Color(0xFF51CF66)
    ),
    OnboardingSlide(
        icon = Icons.AutoMirrored.Outlined.Chat,
        gradient = listOf(Color(0xFFF472B6), Color(0xFFEF4444)),
        glow = Color(0xFFF9A8D4),
        badge = "Anytime, Anywhere",
        title = "Expert Guidance",
        subtitle = "Chat or video call with experienced consultants who understand your needs.",
        chipA = "Certified experts" to Color(0xFFFF6B6B),
        chipB = "Chat · Video" to Color(0xFF51CF66)
    ),
    OnboardingSlide(
        icon = Icons.Outlined.AutoAwesome,
        gradient = listOf(Color(0xFF4ADE80), Color(0xFF10B981)),
        glow = Color(0xFF86EFAC),
        badge = "Positive Energy",
        title = "Transform Your Space",
        subtitle = "Receive customized recommendations to enhance prosperity and well-being.",
        chipA = "Prosperity" to VastuSun,
        chipB = "Well-being" to Color(0xFF10B981)
    )
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == slides.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .softWashBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 21.dp, vertical = 20.dp)
    ) {
        Text(
            "Skip",
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDone() }
                .padding(8.dp)
        )

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val slide = slides[page]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SlideArt(slide)
                Spacer(Modifier.height(28.dp))
                if (slide.badge != null) {
                    Text(
                        slide.badge,
                        color = VastuPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .shadow(1.dp, RoundedCornerShape(999.dp))
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White)
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                }
                Text(
                    slide.title,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    slide.subtitle,
                    fontSize = 12.5.sp,
                    lineHeight = 20.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 290.dp)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            slides.indices.forEach { index ->
                val selected = pagerState.currentPage == index
                val w by animateDpAsState(if (selected) 28.dp else 7.dp, label = "dot")
                Box(
                    Modifier
                        .height(7.dp)
                        .width(w)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) VastuPrimary else Color(0xFFE2E8F0))
                )
            }
        }
        Spacer(Modifier.height(28.dp))

        PrimaryButton(
            text = if (isLast) "Get Started" else "Continue",
            onClick = {
                if (isLast) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            trailingIcon = Icons.Filled.ChevronRight,
            brush = PrimaryButtonGradient,
            modifier = Modifier.shadow(12.dp, RoundedCornerShape(14.dp), spotColor = Color(0xFFA855F7))
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Already have an account? ", color = TextSecondary, fontSize = 12.5.sp)
            Text(
                "Sign In",
                color = VastuPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDone() }
            )
        }
    }
}

/** Compass dial with the slide's icon tile and two floating chips. */
@Composable
private fun SlideArt(slide: OnboardingSlide) {
    val spin = rememberInfiniteTransition(label = "dial")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "angle")
    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        // Glow
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(slide.glow.copy(alpha = 0.55f), Color.Transparent)), radius = size.minDimension / 2.2f)
                }
        )
        // Dashed rotating dial with two beads
        Canvas(
            Modifier
                .fillMaxSize()
                .rotate(angle)
        ) {
            val r = size.minDimension / 2 - 5.dp.toPx()
            drawCircle(
                VastuPrimary.copy(alpha = 0.35f),
                radius = r,
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            )
            drawCircle(VastuPrimary.copy(alpha = 0.15f), radius = 9.dp.toPx(), center = Offset(center.x, center.y - r))
            drawCircle(VastuPrimary, radius = 5.dp.toPx(), center = Offset(center.x, center.y - r))
            drawCircle(VastuSun, radius = 3.5.dp.toPx(), center = Offset(center.x - r * 0.68f, center.y + r * 0.72f))
        }
        // Inner frosted disc
        Box(
            Modifier
                .padding(34.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.55f))
                .border(1.dp, VastuPrimary.copy(alpha = 0.18f), CircleShape)
        )
        Text("N", color = VastuPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
        Text("S", color = TextHint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp))
        Text("W", color = TextHint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterStart).padding(start = 42.dp))
        Text("E", color = TextHint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 42.dp))
        // Icon tile
        Box(
            Modifier
                .size(124.dp)
                .shadow(24.dp, RoundedCornerShape(38.dp), spotColor = VastuPrimary)
                .clip(RoundedCornerShape(38.dp))
                .background(Brush.linearGradient(slide.gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(slide.icon, null, tint = Color.White, modifier = Modifier.size(60.dp))
        }
        FloatingChip(slide.chipA, Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = 28.dp))
        FloatingChip(slide.chipB, Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = (-30).dp))
    }
}

@Composable
private fun FloatingChip(chip: Pair<String, Color>, modifier: Modifier) {
    Row(
        modifier
            .shadow(10.dp, RoundedCornerShape(999.dp), spotColor = VastuPrimary)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(chip.second))
        Text(chip.first, color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
