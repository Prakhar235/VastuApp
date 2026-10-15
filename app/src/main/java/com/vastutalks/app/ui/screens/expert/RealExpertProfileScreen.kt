package com.vastutalks.app.ui.screens.expert

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
    /** Starts as audio; either side can turn the camera on during the call. */
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
            .background(SurfaceLight)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = Color(0xFF2B2B2B))
            }
            Text("Back", color = Color(0xFF2B2B2B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            // Profile card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        ProfileAvatar(expert?.photo.orEmpty(), expertName, size = 112.dp)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(expert?.let { expertStatusColor(it, now) } ?: TextSecondary))
                        }
                    }

                    Box(modifier = Modifier.height(16.dp))
                    Text(expertName, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF2B2B2B))
                    Text(expert?.displaySpecialty ?: "", color = VastuPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    expert?.let {
                        Text(expertStatusLabel(it, now), color = expertStatusColor(it, now), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Box(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(WalletGold.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = WalletGold, modifier = Modifier.size(15.dp))
                            Text(" ${expert?.consultationsLabel ?: "New"}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2B2B2B))
                        }
                    }
                }
            }

            Box(modifier = Modifier.height(16.dp))

            // Stat tiles
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(icon = Icons.Filled.WorkspacePremium, label = "Experience", value = expert?.experienceYears?.takeIf { it > 0 }?.let { "$it yrs" } ?: "—", modifier = Modifier.weight(1f))
                StatTile(icon = Icons.Filled.LocationOn, label = "Location", value = expert?.city?.ifBlank { null } ?: "—", modifier = Modifier.weight(1f))
                if (CallPricing.PAID_CALLS) {
                    StatTile(icon = Icons.Outlined.AutoAwesome, label = "Rate", value = "₹${extras.pricePerSession}", modifier = Modifier.weight(1f))
                } else {
                    StatTile(icon = Icons.Outlined.AutoAwesome, label = "Consultations", value = "${expert?.consultationsCompleted ?: 0}", modifier = Modifier.weight(1f))
                }
            }

            Box(modifier = Modifier.height(16.dp))

            // About
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = WalletGold, modifier = Modifier.size(18.dp))
                        Text("  About Expert", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF2B2B2B))
                    }
                    Box(modifier = Modifier.height(10.dp))
                    Text(expert?.displayBio ?: "", fontSize = 14.sp, color = TextSecondary, lineHeight = 21.sp)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f))

        // Bottom actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chat and calls both work while the expert is offline.
            CallButton(Icons.AutoMirrored.Outlined.Chat, "Chat", filled = true, enabled = true, onClick = onChat, modifier = Modifier.weight(1f))
            CallButton(Icons.Filled.Call, "Call", filled = false, enabled = canCall, onClick = onCall, modifier = Modifier.weight(1f))
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
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (!enabled) Color(0xFFE6E4EC) else if (filled) VastuPrimary else Color.White)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text("  $label", color = content, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Box(modifier = Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B2B2B))
        }
    }
}
