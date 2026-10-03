package com.vastutalks.app.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vastutalks.app.ui.components.CompassIllustration
import com.vastutalks.app.ui.theme.AuthGradient
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val entrance = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entrance.animateTo(1f, animationSpec = tween(600))
        delay(1200)
        onFinished()
    }

    // Slow pulsing glow behind the compass badge — purely decorative,
    // keeps the splash feeling alive rather than static while it waits.
    val glowTransition = rememberInfiniteTransition(label = "splash_glow")
    val glowScale by glowTransition.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(1400), repeatMode = RepeatMode.Reverse),
        label = "splash_glow_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(entrance.value)
                .scale(0.85f + entrance.value * 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                )
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    CompassIllustration()
                }
            }
            Box(modifier = Modifier.size(20.dp))
            Text("Vastu Talks", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Connect with verified Vastu experts", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}
