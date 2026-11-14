package com.vastutalks.app.ui.screens.call

import androidx.compose.foundation.border
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
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
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.call.AgoraCallManager
import com.vastutalks.app.ui.screens.chat.ConversationScreen
import com.vastutalks.app.ui.screens.chat.ConversationViewModel
import androidx.lifecycle.viewmodel.initializer
import com.vastutalks.app.data.model.CallPricing
import com.vastutalks.app.data.model.ExpertProfile
import com.vastutalks.app.ui.components.ProfileAvatar
import com.vastutalks.app.data.model.CallType
import com.vastutalks.app.data.model.expertDisplayExtrasFor
import com.vastutalks.app.ui.components.DarkScreenStatusBar
import com.vastutalks.app.ui.components.DrawingCanvasOverlay
import com.vastutalks.app.ui.components.LocalVideoView
import com.vastutalks.app.ui.components.RemoteVideoView
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuPrimaryDark
import com.vastutalks.app.data.repository.CallSignalingRepository
import com.vastutalks.app.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long to wait for the other person to join after the call was answered before giving up. */
private const val REMOTE_JOIN_TIMEOUT_MILLIS = 30_000L

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val expertUid = remember(channelName) { channelName.removePrefix("expert-") }
    val extras = remember(expertUid) { expertDisplayExtrasFor(expertUid) }
    val ratePerMinute = extras.pricePerSession / 60.0
    val startingWalletBalance = com.vastutalks.app.data.model.SampleShop.balance.toDouble()

    // channelName encodes the expert's own uid ("expert-<uid>"), so
    // comparing it to the signed-in user tells us which side of the
    // call this device is on — only the expert gets the draw tool.
    val isExpertOnThisDevice = remember(expertUid) {
        FirebaseAuth.getInstance().currentUser?.uid == expertUid
    }
    var showDrawingCanvas by remember { mutableStateOf(false) }

    // The user sees the expert's real photo and specialty; the expert sees
    // the caller's initials (users' profiles are private to them).
    var expert by remember { mutableStateOf<ExpertProfile?>(null) }
    LaunchedEffect(expertUid) {
        if (!isExpertOnThisDevice) expert = UserRepository().getExpert(expertUid).getOrNull()
    }
    val peerPhoto = if (isExpertOnThisDevice) "" else expert?.photo.orEmpty()
    val peerSubtitle = if (isExpertOnThisDevice) "Client" else expert?.displaySpecialty ?: "Vastu Expert"

    var elapsedSeconds by remember { mutableStateOf(0) }
    val startTimeMillis = remember { System.currentTimeMillis() }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    // Either side can switch between audio and video at any point in the call;
    // [callType] is only how it started.
    val isVideoOn by AgoraCallManager.localVideoOn.collectAsState()
    val isRemoteVideoOn by AgoraCallManager.remoteVideoOn.collectAsState()
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) AgoraCallManager.setLocalVideo(true)
        else android.widget.Toast.makeText(context, "Camera permission is needed to switch to video.", android.widget.Toast.LENGTH_SHORT).show()
    }
    fun toggleVideo() {
        when {
            isVideoOn -> AgoraCallManager.setLocalVideo(false)
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
                AgoraCallManager.setLocalVideo(true)
            else -> cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val remoteUid by AgoraCallManager.remoteUid.collectAsState()

    // Chat with the other person without leaving the call. The user knows the
    // expert's uid from the channel; the expert looks up who called.
    var peerUid by remember { mutableStateOf(if (isExpertOnThisDevice) null else expertUid) }
    var showChat by remember { mutableStateOf(false) }
    LaunchedEffect(expertUid) {
        if (isExpertOnThisDevice) peerUid = CallSignalingRepository().latestAcceptedCallerUid(expertUid)
    }

    // The expert shows as "on a call" to everyone else until this screen closes.
    // Cleared from a scope that outlives the screen, so it isn't cancelled on the way out.
    DisposableEffect(Unit) {
        if (isExpertOnThisDevice) CoroutineScope(Dispatchers.IO).launch { UserRepository().setInCall(true) }
        onDispose {
            AgoraCallManager.leaveAndRelease()
            if (isExpertOnThisDevice) CoroutineScope(Dispatchers.IO).launch { UserRepository().setInCall(false) }
        }
    }

    // When the other person hangs up (or drops out), end the call here too
    // instead of sitting in an empty call. Same if they never join.
    val remoteLeft by AgoraCallManager.remoteLeft.collectAsState()
    var callEnded by remember { mutableStateOf(false) }
    fun endCall() {
        if (callEnded) return
        callEnded = true
        onEndCall(elapsedSeconds, startTimeMillis)
    }
    LaunchedEffect(remoteLeft) {
        if (remoteLeft) {
            android.widget.Toast.makeText(context, "$peerName ended the call", android.widget.Toast.LENGTH_SHORT).show()
            endCall()
        }
    }
    LaunchedEffect(remoteUid) {
        if (remoteUid == null && !remoteLeft) {
            delay(REMOTE_JOIN_TIMEOUT_MILLIS)
            android.widget.Toast.makeText(context, "$peerName couldn't join the call", android.widget.Toast.LENGTH_SHORT).show()
            endCall()
        }
    }
    // Back hangs up properly (and logs the call) rather than just leaving the screen.
    androidx.activity.compose.BackHandler { endCall() }

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

    val showRealRemoteVideo = remoteUid != null && isRemoteVideoOn

    DarkScreenStatusBar()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.vastutalks.app.ui.theme.ExpertCallGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            // Top bar: timer, live cost, wallet balance (sample wallet; calls are free for now).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .statusBarsPadding()
                    .padding(horizontal = 21.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopPill {
                    Icon(Icons.Outlined.Schedule, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(" " + "%02d:%02d".format(minutes, seconds), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Box(Modifier.size(8.dp))
                TopPill {
                    Text("Cost: ", color = Color.White.copy(alpha = 0.8f), fontSize = 10.5.sp)
                    Text(if (CallPricing.PAID_CALLS) "₹%.2f".format(cost) else "Free", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.25.sp)
                }
                Box(modifier = Modifier.weight(1f))
                TopPill {
                    Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        " " + if (CallPricing.PAID_CALLS) "₹%.2f".format(remainingBalance) else "₹%.2f".format(com.vastutalks.app.data.model.SampleShop.balance.toDouble()),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

            // The other person: their video, or their photo, on a rounded stage.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 21.dp, end = 21.dp, top = 14.dp, bottom = 14.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .shadow(24.dp, RoundedCornerShape(21.dp))
                        .clip(RoundedCornerShape(21.dp))
                        .background(Brush.linearGradient(listOf(Color(0x4D6C5CE7), Color(0x80312E81)))),
                    contentAlignment = Alignment.Center
                ) {
                    if (showRealRemoteVideo) {
                        RemoteVideoView(uid = remoteUid!!, modifier = Modifier.fillMaxSize())
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(134.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                ProfileAvatar(peerPhoto, peerName, size = 126.dp)
                            }
                            if (remoteUid == null) {
                                Text(
                                    text = "Connecting...",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.25.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 14.dp)
                                )
                            }
                        }
                    }
                }
                // Your camera (tap to switch front/back). Hidden under the chat: it draws above everything (see LocalVideoView).
                if (!showChat) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 21.dp, end = 10.5.dp)
                            .size(width = 98.dp, height = 126.dp)
                            .shadow(20.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isVideoOn) Color.Black.copy(alpha = 0.6f) else Color(0xFF111827))
                            .border(2.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .clickable(enabled = isVideoOn, onClickLabel = "Switch camera") { AgoraCallManager.switchCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isVideoOn) LocalVideoView(modifier = Modifier.fillMaxSize())
                        else Icon(Icons.Filled.VideocamOff, contentDescription = "Camera off", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.5.dp, bottom = 17.5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.5.dp)
                ) {
                    Text(peerName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(peerSubtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 10.5.sp, modifier = Modifier.padding(top = 1.75.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 21.dp),
                horizontalArrangement = Arrangement.spacedBy(17.5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    alert = isMuted,
                    description = if (isMuted) "Unmute" else "Mute",
                    onClick = { isMuted = !isMuted; AgoraCallManager.muteLocalAudio(isMuted) }
                )
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(16.dp, CircleShape, spotColor = Color(0x66F56565))
                        .clip(CircleShape)
                        .background(com.vastutalks.app.ui.theme.EndCallRed)
                        .clickable { endCall() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                LiveControlButton(
                    icon = if (isVideoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    alert = !isVideoOn,
                    description = if (isVideoOn) "Turn camera off" else "Turn camera on",
                    onClick = ::toggleVideo
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 17.5.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                SmallCallButton(Icons.Filled.Chat, "Chat") {
                    if (peerUid != null) showChat = true
                    else android.widget.Toast.makeText(context, "Chat is still loading — try again in a moment.", android.widget.Toast.LENGTH_SHORT).show()
                }
                SmallCallButton(if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff, if (isSpeakerOn) "Speaker off" else "Speaker on") { isSpeakerOn = !isSpeakerOn }
                if (isExpertOnThisDevice) SmallCallButton(Icons.Filled.Draw, "Sketch") { showDrawingCanvas = true }
            }
        }

        peerUid?.takeIf { showChat }?.let { uid ->
            InCallChat(peerUid = uid, peerName = peerName, onClose = { showChat = false })
        }

        if (showDrawingCanvas) {
            DrawingCanvasOverlay(
                timerLabel = timerLabel,
                peerName = peerName,
                peerSpecialty = peerSubtitle,
                avatarUrl = "",
                onClose = { showDrawingCanvas = false }
            )
        }
    }
}

@Composable
private fun TopPill(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 8.75.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Mute / camera: frosted when normal, red when off (as in the design). */
@Composable
private fun LiveControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    alert: Boolean,
    description: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(57.dp)
            .clip(CircleShape)
            .background(if (alert) Color(0xFFF56565) else Color.White.copy(alpha = 0.2f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SmallCallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(39.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/**
 * The chat with the other person, over the call (which keeps going
 * underneath). Back or the arrow returns to the call.
 */
@Composable
private fun InCallChat(peerUid: String, peerName: String, onClose: () -> Unit) {
    val viewModel: ConversationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "in-call-chat-$peerUid",
        factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                ConversationViewModel(
                    androidx.lifecycle.SavedStateHandle(
                        mapOf("otherUid" to peerUid, "otherName" to java.net.URLEncoder.encode(peerName.ifBlank { "Chat" }, "UTF-8"))
                    )
                )
            }
        }
    )
    androidx.activity.compose.BackHandler(onBack = onClose)
    ConversationScreen(onBack = onClose, onCall = { _, _ -> }, showCallButton = false, viewModel = viewModel)
}
