package com.vastutalks.app.ui.screens.expert

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.draw.shadow
import com.vastutalks.app.ui.components.softWashBackground
import com.vastutalks.app.ui.theme.PrimaryButtonGradient
import com.vastutalks.app.ui.theme.TextPrimary
import com.vastutalks.app.ui.theme.VastuCoral
import com.vastutalks.app.ui.theme.VastuSun
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vastutalks.app.data.model.CallPricing
import com.vastutalks.app.data.model.expertDisplayExtrasFor
import com.vastutalks.app.data.repository.UserRepository
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.ui.screens.home.expertStatusColor
import com.vastutalks.app.ui.screens.home.expertStatusLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import com.vastutalks.app.ui.theme.OnlineGreen
import com.vastutalks.app.ui.theme.SurfaceLight
import com.vastutalks.app.ui.theme.TextSecondary
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.WalletGold

@Composable
fun RealExpertProfileScreen(
    expertUid: String,
    expertName: String,
    onBack: () -> Unit,
    /** Starts a video call. */
    onCall: () -> Unit,
    onChat: () -> Unit
) {
    val extras = remember(expertUid) { expertDisplayExtrasFor(expertUid) } // only for the hidden price
    // Live, so the buttons and status follow the expert going online/offline or onto a call.
    val expert by remember(expertUid) { UserRepository().listenExpert(expertUid).catch { emit(null) } }
        .collectAsState(initial = null)
    // Re-checked every 30 s: "online" depends on how long ago their app checked in.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    // Offline experts can be called too (their phone rings through a push); just not mid-call.
    val canCall = expert?.isBusyAt(now) == false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .softWashBackground()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.8f))
                .statusBarsPadding()
                .clickable { onBack() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(22.dp))
            Text("Back", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(17.5.dp)
        ) {
            // Profile card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(21.dp), spotColor = Color(0x33A855F7), ambientColor = Color(0x11A855F7))
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color.White)
                    .padding(21.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        ProfileAvatar(
                            expert?.photo.orEmpty(), expertName, size = 98.dp,
                            modifier = Modifier
                                .shadow(16.dp, CircleShape)
                                .border(4.dp, Color.White, CircleShape)
                        )
                        if (expert?.isPresentAt(now) == true) {
                            Box(
                                modifier = Modifier
                                    .size(24.5.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset((-3.5).dp, (-3.5).dp)
                                    .border(3.dp, Color(0x6610B981), CircleShape)
                                    .padding(1.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                        }
                    }

                    Box(modifier = Modifier.height(14.dp))
                    Text(expertName, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = TextPrimary)
                    Text(expert?.displaySpecialty ?: "", color = VastuPrimary, fontWeight = FontWeight.Medium, fontSize = 12.25.sp, modifier = Modifier.padding(top = 3.5.dp))
                    Box(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(VastuSun.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = VastuSun, modifier = Modifier.size(14.dp))
                        Text(" ${extras.ratingLabel}", fontWeight = FontWeight.Bold, fontSize = 12.25.sp, color = TextPrimary)
                        Text(" • ${extras.reviewCount} reviews", fontSize = 10.5.sp, color = TextSecondary)
                    }
                }
            }

            Box(modifier = Modifier.height(17.5.dp))

            // Stat tiles
            Row(horizontalArrangement = Arrangement.spacedBy(10.5.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(icon = Icons.Outlined.WorkspacePremium, tint = VastuPrimary, label = "Experience", value = expert?.experienceYears?.takeIf { it > 0 }?.let { "$it yrs" } ?: "—", modifier = Modifier.weight(1f))
                StatTile(icon = Icons.Outlined.LocationOn, tint = VastuCoral, label = "Location", value = expert?.city?.ifBlank { null } ?: "—", modifier = Modifier.weight(1f))
                StatTile(
                    icon = Icons.Outlined.AutoAwesome, tint = VastuSun, label = "Rate",
                    value = if (CallPricing.PAID_CALLS) com.vastutalks.app.data.model.SampleShop.rupees(extras.pricePerSession) else "Free",
                    modifier = Modifier.weight(1f)
                )
            }

            Box(modifier = Modifier.height(17.5.dp))

            // About
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(21.dp), spotColor = Color(0x33A855F7), ambientColor = Color(0x11A855F7))
                    .clip(RoundedCornerShape(21.dp))
                    .background(Color.White)
                    .padding(17.5.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = VastuSun, modifier = Modifier.size(18.dp))
                        Text("  About Expert", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Box(modifier = Modifier.height(10.dp))
                    Text(expert?.displayBio ?: "", fontSize = 12.25.sp, color = TextSecondary, lineHeight = 20.sp)
                }
            }
        }

        // Bottom actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.8f))
                .padding(17.5.dp),
            horizontalArrangement = Arrangement.spacedBy(10.5.dp)
        ) {
            // Chat and calls both work while the expert is offline.
            CallButton(Icons.AutoMirrored.Outlined.Chat, "Chat Now", filled = true, enabled = true, onClick = onChat, modifier = Modifier.weight(1f))
            CallButton(Icons.Outlined.Videocam, "Video Call", filled = false, enabled = canCall, onClick = onCall, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CallButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = if (!enabled) TextSecondary else if (filled) Color.White else VastuPrimary
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                when {
                    !enabled -> Modifier.background(Color(0xFFE6E4EC))
                    filled -> Modifier.background(PrimaryButtonGradient)
                    else -> Modifier.background(Color.White).border(2.dp, VastuPrimary, RoundedCornerShape(14.dp))
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text("  $label", color = content, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = Color(0x22A855F7), ambientColor = Color(0x0AA855F7))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            Box(modifier = Modifier.height(7.dp))
            Text(label, fontSize = 10.5.sp, color = TextSecondary)
            Text(value, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
