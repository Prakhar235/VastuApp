package com.vastutalks.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.vastutalks.app.data.call.AgoraCallManager

/**
 * Renders the local camera preview. Call this once video is enabled
 * (i.e. right before/after joinChannel for a video call) — Agora's
 * SurfaceView needs the engine to already exist.
 */
@Composable
fun LocalVideoView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            AgoraCallManager.createRendererView(context).also { view ->
                AgoraCallManager.setupLocalVideo(view)
            }
        }
    )
}

/**
 * Renders a specific remote user's video. [uid] should be the value
 * observed from [AgoraCallManager.remoteUid] once it's non-null.
 */
@Composable
fun RemoteVideoView(uid: Int, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            AgoraCallManager.createRendererView(context).also { view ->
                AgoraCallManager.setupRemoteVideo(view, uid)
            }
        }
    )
}
