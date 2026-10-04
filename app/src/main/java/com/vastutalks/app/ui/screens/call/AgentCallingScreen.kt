package com.vastutalks.app.ui.screens.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vastutalks.app.data.model.AnanyaAgent
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.VastuCharcoal
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.delay

/**
 * Ringing screen for a call with the AI agent Ananya. There's no
 * Firestore signaling or Agora token — she's always available, so she
 * "picks up" after a short ring.
 */
@Composable
fun AgentCallingScreen(
    onConnected: () -> Unit,
    onCancel: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2500)
        onConnected()
    }

    val pulseAnim = rememberInfiniteTransition(label = "ringing_pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "ringing_pulse_scale"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(VastuCharcoal),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(120.dp).scale(scale).clip(CircleShape).background(VastuPrimary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = "https://i.pravatar.cc/300?u=${AnanyaAgent.AVATAR_SEED}",
                    contentDescription = null,
                    modifier = Modifier.size(96.dp).clip(CircleShape).background(VastuPrimary)
                )
            }
            Box(modifier = Modifier.size(28.dp))
            Text(AnanyaAgent.NAME, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Box(modifier = Modifier.size(6.dp))
            Text("Calling…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Box(modifier = Modifier.size(6.dp))
            Text("AI Vastu expert", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
            Box(modifier = Modifier.size(64.dp))
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Danger)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}
