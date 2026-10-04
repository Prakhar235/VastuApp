package com.vastutalks.app.ui.screens.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.data.call.AgoraCallManager
import com.vastutalks.app.data.call.CallConnectionState
import com.vastutalks.app.data.call.TokenRepository
import com.vastutalks.app.data.model.CallRequestStatus
import com.vastutalks.app.data.model.CallType
import com.vastutalks.app.data.repository.CallSignalingRepository
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.VastuCharcoal
import com.vastutalks.app.ui.theme.VastuPrimary
import kotlinx.coroutines.launch

/**
 * Caller side of a real (Firestore-signaled) call to a signed-up
 * expert (as opposed to a call with the AI agent Ananya — see
 * AgentCallingScreen).
 *
 * Flow: write a CallRequest -> wait for the callee to Accept/Decline
 * (live Firestore listener) -> once accepted, fetch a fresh token
 * from the generateAgoraToken Cloud Function -> join Agora.
 */
@Composable
fun LiveCallingScreen(
    calleeUid: String,
    calleeName: String,
    callType: CallType,
    onConnected: (channelName: String, peerName: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val signalingRepository = remember { CallSignalingRepository() }
    val tokenRepository = remember { TokenRepository() }
    val coroutineScope = rememberCoroutineScope()

    var statusText by remember { mutableStateOf("Calling $calleeName…") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var permissionsDenied by remember { mutableStateOf(false) }
    var callId by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        val camGranted = callType == CallType.AUDIO || grants[Manifest.permission.CAMERA] == true
        if (!(micGranted && camGranted)) {
            permissionsDenied = true
        }
    }

    LaunchedEffect(calleeUid, callType) {
        val needsCamera = callType == CallType.VIDEO
        val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = !needsCamera || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!micOk || !camOk) {
            val toRequest = buildList {
                if (!micOk) add(Manifest.permission.RECORD_AUDIO)
                if (needsCamera && !camOk) add(Manifest.permission.CAMERA)
            }
            permissionLauncher.launch(toRequest.toTypedArray())
        }

        val me = FirebaseAuth.getInstance().currentUser
        if (me == null) {
            errorText = "You're not signed in."
            return@LaunchedEffect
        }

        val result = signalingRepository.createCallRequest(
            callerUid = me.uid,
            callerName = me.displayName ?: "Someone",
            calleeUid = calleeUid,
            calleeName = calleeName,
            callType = callType.name
        )
        result.fold(
            onSuccess = { request -> callId = request.callId },
            onFailure = { e -> errorText = e.message ?: "Couldn't start the call." }
        )
    }

    // Watch the call request for the callee's response.
    LaunchedEffect(callId) {
        val id = callId ?: return@LaunchedEffect
        signalingRepository.listenForCallStatus(id).collect { call ->
            when (call?.status) {
                CallRequestStatus.ACCEPTED.name -> {
                    statusText = "Connecting…"
                    coroutineScope.launch {
                        val tokenResult = tokenRepository.fetchOrCreateExpertToken(calleeUid, call.channelName)
                        tokenResult.fold(
                            onSuccess = { token ->
                                AgoraCallManager.joinChannelWithToken(context, call.channelName, token, callType == CallType.VIDEO)
                            },
                            onFailure = { e ->
                                errorText = e.message ?: "Couldn't fetch a call token. Is the Cloud Function deployed?"
                            }
                        )
                    }
                }
                CallRequestStatus.DECLINED.name -> errorText = "$calleeName declined the call."
                else -> {}
            }
        }
    }

    val connectionState by AgoraCallManager.connectionState.collectAsState()
    val agoraError by AgoraCallManager.errorMessage.collectAsState()
    LaunchedEffect(connectionState) {
        if (connectionState == CallConnectionState.JOINED) {
            val id = callId
            if (id != null) {
                onConnected("call-$id", calleeName)
            }
        }
    }
    LaunchedEffect(agoraError) {
        if (agoraError != null) errorText = agoraError
    }

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val scale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "pulse_scale"
    )

    Box(modifier = Modifier.fillMaxSize().background(VastuCharcoal), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(120.dp).scale(scale).clip(CircleShape).background(VastuPrimary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(VastuPrimary), contentAlignment = Alignment.Center) {
                    Text(
                        text = calleeName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp
                    )
                }
            }
            Box(modifier = Modifier.height(28.dp))
            Text(calleeName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Box(modifier = Modifier.height(6.dp))
            Text(
                text = when {
                    permissionsDenied -> "Microphone/camera permission is needed to place this call."
                    errorText != null -> errorText ?: ""
                    else -> statusText
                },
                color = if (permissionsDenied || errorText != null) Color(0xFFFFB4A9) else Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Box(modifier = Modifier.height(64.dp))
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Danger)
                    .clickable {
                        val id = callId
                        if (id != null) {
                            coroutineScope.launch { signalingRepository.updateStatus(id, CallRequestStatus.CANCELLED) }
                        }
                        AgoraCallManager.leaveAndRelease()
                        onCancel()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CallEnd, contentDescription = "Cancel call", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}
