package com.vastutalks.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vastutalks.app.ui.navigation.CallDeepLink
import com.vastutalks.app.ui.navigation.VastuNavGraph
import com.vastutalks.app.ui.theme.VastuTalksTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEEP_LINK_CHANNEL = "deep_link_channel"
        const val EXTRA_DEEP_LINK_PEER_NAME = "deep_link_peer_name"
        const val EXTRA_DEEP_LINK_CALL_TYPE = "deep_link_call_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Deliberately passing null here, not savedInstanceState: Compose
        // Navigation's back stack survives process death via saved-state
        // restoration, which was letting the app skip straight back to
        // whatever screen (e.g. Home) was last visible instead of always
        // showing Splash first. Discarding saved state forces every launch
        // — including a cold start after Android killed the background
        // process — to rebuild the NavHost from Routes.SPLASH.
        super.onCreate(null)
        enableEdgeToEdge()

        // Set when IncomingCallActivity has already accepted a call and
        // joined the Agora channel — this activity's job is just to land
        // straight on the in-call screen instead of Home.
        val channelName = intent.getStringExtra(EXTRA_DEEP_LINK_CHANNEL)
        val peerName = intent.getStringExtra(EXTRA_DEEP_LINK_PEER_NAME)
        val callType = intent.getStringExtra(EXTRA_DEEP_LINK_CALL_TYPE)
        val deepLink = if (channelName != null && peerName != null && callType != null) {
            CallDeepLink(channelName, peerName, callType)
        } else {
            null
        }

        setContent {
            VastuTalksTheme {
                VastuNavGraph(initialCallDeepLink = deepLink)
            }
        }
    }
}
