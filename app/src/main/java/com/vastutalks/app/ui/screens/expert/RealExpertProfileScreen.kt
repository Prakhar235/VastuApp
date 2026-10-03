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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vastutalks.app.data.model.expertDisplayExtrasFor
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
    onChatNow: () -> Unit,
    onVideoCall: () -> Unit
) {
    val extras = remember(expertUid) { expertDisplayExtrasFor(expertUid) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceLight)
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
                        AsyncImage(
                            model = "https://i.pravatar.cc/300?u=${extras.avatarSeed}-$expertUid",
                            contentDescription = null,
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                                .background(VastuPrimary.copy(alpha = 0.08f))
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(3.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(OnlineGreen))
                        }
                    }

                    Box(modifier = Modifier.height(16.dp))
                    Text(expertName, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF2B2B2B))
                    Text(extras.specialty, color = VastuPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Box(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(WalletGold.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = WalletGold, modifier = Modifier.size(15.dp))
                            Text(" ${extras.rating}  ·  ${extras.reviewCount} reviews", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2B2B2B))
                        }
                    }
                }
            }

            Box(modifier = Modifier.height(16.dp))

            // Stat tiles
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(icon = Icons.Filled.WorkspacePremium, label = "Experience", value = "${extras.experienceYears} yrs", modifier = Modifier.weight(1f))
                StatTile(icon = Icons.Filled.LocationOn, label = "Location", value = extras.location, modifier = Modifier.weight(1f))
                StatTile(icon = Icons.Outlined.AutoAwesome, label = "Rate", value = "₹${extras.pricePerSession}", modifier = Modifier.weight(1f))
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
                    Text(extras.bio, fontSize = 14.sp, color = TextSecondary, lineHeight = 21.sp)
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VastuPrimary)
                    .clickable { onChatNow() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("  Chat Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable { onVideoCall() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = VastuPrimary, modifier = Modifier.size(18.dp))
                    Text("  Video Call", color = VastuPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
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
