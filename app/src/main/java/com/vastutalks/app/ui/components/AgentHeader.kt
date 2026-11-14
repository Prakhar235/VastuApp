package com.vastutalks.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vastutalks.app.data.ai.tr
import com.vastutalks.app.data.model.AnanyaAgent
import com.vastutalks.app.ui.theme.VastuPrimary

/** The dark backdrop with a violet glow shared by the agent chat and call. */
fun Modifier.agentScreenBackground(): Modifier = this
    .background(Brush.linearGradient(listOf(Color(0xFF0F0B2E), Color(0xFF221859), Color(0xFF3B1D6E))))
    .background(
        Brush.radialGradient(
            colors = listOf(Color(0xFF7C3AED).copy(alpha = 0.35f), Color.Transparent),
            center = Offset(540f, 0f),
            radius = 900f
        )
    )

/**
 * The top of both agent screens (chat and call), so they look the same:
 * Ananya with a status line and [actions] on the first row, then "Scan a
 * room" and the reply-language switch on the second.
 */
@Composable
fun AgentHeader(
    subtitle: String,
    statusDot: @Composable () -> Unit,
    onScanRoom: () -> Unit,
    isSpeaking: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val ringColor by animateColorAsState(if (isSpeaking) VastuPrimary else Color.Transparent, label = "ring")
    Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "वापस"), tint = LocalAgentTones.current.textPrimary)
                }
            } else {
                Spacer(Modifier.size(width = 12.dp, height = 44.dp))
            }
            Box(modifier = Modifier.size(46.dp).border(2.dp, ringColor, CircleShape).padding(4.dp)) {
                AiOrb(38.dp, ringWidth = 2.5.dp, iconSize = 17.dp, spinMillis = if (isSpeaking) 2500 else 6000)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp, end = 4.dp)) {
                Text(
                    AnanyaAgent.NAME,
                    color = LocalAgentTones.current.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    statusDot()
                    Text(
                        subtitle,
                        color = LocalAgentTones.current.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            actions()
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(LocalAgentTones.current.surface)
                    .border(1.dp, LocalAgentTones.current.hairline, RoundedCornerShape(18.dp))
                    .clickable(onClick = onScanRoom)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Explore, contentDescription = null, tint = VastuPrimary, modifier = Modifier.size(18.dp))
                Text(
                    tr("Scan a room", "कमरा स्कैन करें"),
                    color = LocalAgentTones.current.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            LanguageSwitch()
        }
    }
}

/** A round icon button for [AgentHeader]'s actions. */
@Composable
fun AgentHeaderButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(LocalAgentTones.current.surfaceRaised)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = LocalAgentTones.current.textPrimary, modifier = Modifier.size(20.dp))
    }
}
