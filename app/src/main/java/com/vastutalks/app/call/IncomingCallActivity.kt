package com.vastutalks.app.call

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.vastutalks.app.MainActivity
import com.vastutalks.app.data.call.AgoraCallManager
import com.vastutalks.app.data.call.CallConnectionState
import com.vastutalks.app.data.call.TokenRepository
import com.vastutalks.app.data.model.CallRequestStatus
import com.vastutalks.app.data.repository.CallSignalingRepository
import com.vastutalks.app.ui.theme.Danger
import com.vastutalks.app.ui.theme.Success
import com.vastutalks.app.ui.theme.VastuCharcoal
import com.vastutalks.app.ui.theme.VastuPrimary
import com.vastutalks.app.ui.theme.VastuTalksTheme
import kotlinx.coroutines.launch

/**
 * Full-screen incoming-call UI launched by VastuMessagingService's
 * notification fullScreenIntent. Shows over the lock screen — this
 * is what makes it feel like the phone is actually "ringing" rather
 * than just a notification banner.
 */
class IncomingCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_CALL_TYPE = "call_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: run { finish(); return }
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Someone"
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: run { finish(); return }
        val callType = intent.getStringExtra(EXTRA_CALL_TYPE) ?: "AUDIO"

        // The full-screen notification did its job (woke/unlocked the
        // screen); dismiss it now so it doesn't linger once this UI is up.
        getSystemService(NotificationManager::class.java)?.cancel(callId.hashCode())

        setContent {
            VastuTalksTheme {
                IncomingCallContent(
                    callerName = callerName,
                    callType = callType,
                    onAccept = { acceptCall(callId, channelName, callType) },
                    onDecline = { declineCall(callId) },
                    onJoined = { launchIntoCall(channelName, callerName, callType) }
                )
            }
        }
    }

    private fun acceptCall(callId: String, channelName: String, callType: String) {
        val signalingRepository = CallSignalingRepository()
        val tokenRepository = TokenRepository()

        lifecycleScope.launch {
            signalingRepository.updateStatus(callId, CallRequestStatus.ACCEPTED)
            val calleeUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val tokenResult = tokenRepository.fetchOrCreateExpertToken(calleeUid, channelName)
            tokenResult.onSuccess { token ->
                AgoraCallManager.joinChannelWithToken(this@IncomingCallActivity, channelName, token, callType == "VIDEO")
            }
        }
    }

    private fun declineCall(callId: String) {
        lifecycleScope.launch {
            CallSignalingRepository().updateStatus(callId, CallRequestStatus.DECLINED)
        }
        finish()
    }

    private fun launchIntoCall(channelName: String, peerName: String, callType: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_DEEP_LINK_CHANNEL, channelName)
            putExtra(MainActivity.EXTRA_DEEP_LINK_PEER_NAME, peerName)
            putExtra(MainActivity.EXTRA_DEEP_LINK_CALL_TYPE, callType)
        }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun IncomingCallContent(
    callerName: String,
    callType: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onJoined: () -> Unit
) {
    var isConnecting by remember { mutableStateOf(false) }
    val connectionState by AgoraCallManager.connectionState.collectAsState()

    LaunchedEffect(connectionState) {
        if (connectionState == CallConnectionState.JOINED) {
            onJoined()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(VastuCharcoal)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(110.dp).clip(CircleShape).background(VastuPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = callerName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp
                )
            }
            Box(modifier = Modifier.padding(top = 24.dp)) {
                Text(callerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Box(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    if (callType == "VIDEO") "Incoming video call" else "Incoming audio call",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }
            if (isConnecting) {
                Box(modifier = Modifier.padding(top = 48.dp)) {
                    CircularProgressIndicator(color = VastuPrimary)
                }
            }
        }

        if (!isConnecting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Danger)
                        .clickable { onDecline() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Success)
                        .clickable {
                            isConnecting = true
                            onAccept()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
