package com.vastutalks.app.ui.screens.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.call.AgoraCallManager
import com.vastutalks.app.data.model.CallType
import com.vastutalks.app.data.model.expertDisplayExtrasFor
import com.vastutalks.app.ui.components.DrawingCanvasOverlay
import com.vastutalks.app.ui.components.LocalVideoView
import com.vastutalks.app.ui.components.RemoteVideoView
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuPrimaryDark
import kotlinx.coroutines.delay

/**
 * In-call UI for the real signaling flow (LiveCallingScreen /
 * IncomingCallScreen). Cost and wallet balance here are simulated
 * from the same hardcoded per-expert rate used on Home/Profile — real
 * experts don't have actual billing set up, and there's no real
 * wallet backend yet, so this resets every call rather than
 * persisting anywhere. See README for what a real implementation
 * would need (ledger writes, server-authoritative balance, etc).
 */
@Composable
fun LiveInCallScreen(
    channelName: String,
    peerName: String,
    callType: CallType,
    onEndCall: (durationSeconds: Int, startTimeMillis: Long) -> Unit
) {
    val expertUid = remember(channelName) { channelName.removePrefix("expert-") }
    val extras = remember(expertUid) { expertDisplayExtrasFor(expertUid) }
    val ratePerMinute = extras.pricePerSession / 60.0
    val startingWalletBalance = 2450.0

    // channelName encodes the expert's own uid ("expert-<uid>"), so
    // comparing it to the signed-in user tells us which side of the
    // call this device is on — only the expert gets the draw tool.
    val isExpertOnThisDevice = remember(expertUid) {
        FirebaseAuth.getInstance().currentUser?.uid == expertUid
    }
    var showDrawingCanvas by remember { mutableStateOf(false) }

    var elapsedSeconds by remember { mutableStateOf(0) }
    val startTimeMillis = remember { System.currentTimeMillis() }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var isCameraOff by remember { mutableStateOf(false) }

    val remoteUid by AgoraCallManager.remoteUid.collectAsState()

    DisposableEffect(Unit) {
        onDispose { AgoraCallManager.leaveAndRelease() }
    }

    LaunchedEffect(isSpeakerOn) { AgoraCallManager.setSpeakerphoneEnabled(isSpeakerOn) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timerLabel = "%d:%02d".format(minutes, seconds)
    val cost = (elapsedSeconds / 60.0) * ratePerMinute
    val remainingBalance = (startingWalletBalance - cost).coerceAtLeast(0.0)

    val showRealRemoteVideo = callType == CallType.VIDEO && remoteUid != null && !isCameraOff

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(VastuPrimary, VastuPrimaryDark)))
    ) {
        if (showRealRemoteVideo) {
            RemoteVideoView(uid = remoteUid!!, modifier = Modifier.fillMaxSize())
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top pills: timer, live cost, wallet balance.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TopPill(text = timerLabel)
                TopPill(text = "Cost: ₹%.2f".format(cost))
                Box(modifier = Modifier.weight(1f))
                TopPill(text = "₹%.2f".format(remainingBalance))
            }

            if (!showRealRemoteVideo) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = "https://i.pravatar.cc/300?u=${extras.avatarSeed}-$expertUid",
                                contentDescription = null,
                                modifier = Modifier
                                    .size(124.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                        Box(modifier = Modifier.size(16.dp))
                        if (callType == CallType.VIDEO && remoteUid == null) {
                            Text(
                                text = "Connecting you with ${peerName.substringBefore(" ")}…",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.25f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(peerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(extras.specialty, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f))
            }

            // Local camera preview thumbnail, top-right-ish (video calls only).
            if (callType == CallType.VIDEO) {
                Box(
                    modifier = Modifier
                        .padding(end = 20.dp, top = 4.dp, bottom = 12.dp)
                        .align(Alignment.End)
                        .size(width = 90.dp, height = 120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCameraOff) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
                    } else {
                        LocalVideoView(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    isActive = isMuted,
                    onClick = { isMuted = !isMuted; AgoraCallManager.muteLocalAudio(isMuted) }
                )
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(Danger)
                        .clickable { onEndCall(elapsedSeconds, startTimeMillis) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White)
                }
                if (callType == CallType.VIDEO) {
                    LiveControlButton(
                        icon = Icons.Filled.Cameraswitch,
                        isActive = false,
                        onClick = { AgoraCallManager.switchCamera() }
                    )
                } else {
                    LiveControlButton(
                        icon = Icons.Filled.VolumeUp,
                        isActive = isSpeakerOn,
                        onClick = { isSpeakerOn = !isSpeakerOn }
                    )
                }
            }

            if (callType == CallType.VIDEO) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), contentAlignment = Alignment.Center) {
                    LiveControlButton(
                        icon = Icons.Filled.VideocamOff,
                        isActive = isCameraOff,
                        onClick = { isCameraOff = !isCameraOff; AgoraCallManager.muteLocalVideo(isCameraOff) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { /* TODO: no in-call chat feature yet — see README */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = "Chat", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                if (isExpertOnThisDevice) {
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { showDrawingCanvas = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Draw, contentDescription = "Sketch", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        if (showDrawingCanvas) {
            DrawingCanvasOverlay(
                timerLabel = timerLabel,
                peerName = peerName,
                peerSpecialty = extras.specialty,
                avatarUrl = "https://i.pravatar.cc/300?u=${extras.avatarSeed}-$expertUid",
                onClose = { showDrawingCanvas = false }
            )
        }
    }
}

@Composable
private fun TopPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun LiveControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (isActive) Color.White else Color.White.copy(alpha = 0.15f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (isActive) VastuPrimaryDark else Color.White, modifier = Modifier.size(22.dp))
    }
}