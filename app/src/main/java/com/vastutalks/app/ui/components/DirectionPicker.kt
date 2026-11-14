package com.vastutalks.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vastutalks.app.data.ai.CompassDirection
import com.vastutalks.app.data.ai.CompassDirection.E
import com.vastutalks.app.data.ai.CompassDirection.N
import com.vastutalks.app.data.ai.CompassDirection.NE
import com.vastutalks.app.data.ai.CompassDirection.NW
import com.vastutalks.app.data.ai.CompassDirection.S
import com.vastutalks.app.data.ai.CompassDirection.SE
import com.vastutalks.app.data.ai.CompassDirection.SW
import com.vastutalks.app.data.ai.CompassDirection.W
import com.vastutalks.app.data.ai.tr
import com.vastutalks.app.ui.theme.VastuPrimary

/**
 * Asks which way the camera is facing, for phones without a compass.
 * On a gyroscope phone ([HeadingSensor.GYRO]) the answer becomes the
 * reference that later turns are counted from; otherwise it's simply the
 * direction until the caller changes it.
 */
@Composable
fun DirectionPickerDialog(
    sensor: HeadingSensor,
    current: CompassDirection?,
    onPick: (CompassDirection) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CallTones.Ink)
                .border(1.dp, CallTones.Hairline, RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                tr("Which way is your camera facing?", "आपका कैमरा किस दिशा में है?"),
                color = CallTones.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                if (sensor == HeadingSensor.GYRO) tr(
                    "This phone has no compass. Point the camera at a wall or door whose direction you know, then tap it — the phone will keep track as you turn. If it drifts, set it again.",
                    "इस फ़ोन में कम्पास नहीं है। कैमरा किसी ऐसी दीवार या दरवाज़े की ओर करें जिसकी दिशा आपको पता है, फिर उसे टैप करें — आपके मुड़ने पर फ़ोन दिशा याद रखेगा। गड़बड़ लगे तो फिर से सेट करें।"
                ) else tr(
                    "This phone can't sense direction. Tap the way the camera is facing, and change it whenever you turn.",
                    "यह फ़ोन दिशा नहीं पहचान सकता। कैमरा जिस दिशा में है उसे टैप करें, और मुड़ने पर बदल दें।"
                ),
                color = CallTones.TextMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
            // Laid out like a compass rose, North at the top.
            listOf(listOf(NW, N, NE), listOf(W, null, E), listOf(SW, S, SE)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                    row.forEach { direction ->
                        if (direction == null) {
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Explore, contentDescription = null, tint = CallTones.TextMuted, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            DirectionCell(direction, selected = direction == current) { onPick(direction) }
                        }
                    }
                }
            }
            Text(
                tr("Not now", "अभी नहीं"),
                color = CallTones.TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DirectionCell(direction: CompassDirection, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (selected) VastuPrimary else CallTones.Surface)
            .border(1.dp, CallTones.Hairline, CircleShape)
            .clickable(role = Role.Button, onClickLabel = direction.localName, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(direction.short, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            direction.localName,
            color = if (selected) Color.White else CallTones.TextMuted,
            fontSize = 9.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
