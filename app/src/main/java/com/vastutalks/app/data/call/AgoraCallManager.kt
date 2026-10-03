package com.vastutalks.app.data.call

import android.content.Context
import android.util.Log
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallConnectionState { IDLE, JOINING, JOINED, FAILED }

/**
 * Single shared owner of the Agora RtcEngine for the whole app.
 *
 * There's only ever one call happening at a time in this app, so a
 * singleton is simpler than threading a ViewModel instance through
 * LiveCallingScreen -> LiveInCallScreen (which are two different
 * NavHost back-stack entries and would otherwise need two ViewModels
 * that somehow share one engine). LiveCallingScreen/IncomingCallActivity
 * start the join; LiveInCallScreen observes the same state and renders
 * video; whichever screen is on top when the call ends calls [leaveAndRelease].
 */
object AgoraCallManager {
    private const val TAG = "AgoraCallManager"

    private var engine: RtcEngine? = null

    private val _connectionState = MutableStateFlow(CallConnectionState.IDLE)
    val connectionState: StateFlow<CallConnectionState> = _connectionState.asStateFlow()

    private val _remoteUid = MutableStateFlow<Int?>(null)
    val remoteUid: StateFlow<Int?> = _remoteUid.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "Joined channel=$channel uid=$uid")
            _connectionState.value = CallConnectionState.JOINED
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "Remote user joined uid=$uid")
            _remoteUid.value = uid
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.i(TAG, "Remote user left uid=$uid reason=$reason")
            if (_remoteUid.value == uid) {
                _remoteUid.value = null
            }
        }

        override fun onError(err: Int) {
            Log.e(TAG, "Agora error code=$err")
            _connectionState.value = CallConnectionState.FAILED
            _errorMessage.value = friendlyErrorFor(err)
        }
    }

    /** Returns the live engine so screens can build local/remote video views. Null until joinChannel() has run. */
    fun currentEngine(): RtcEngine? = engine

    /**
     * Creates the engine (if needed) and joins [channelName] with a
     * token fetched fresh per call (from the generateAgoraToken Cloud
     * Function, or the HARDCODED_EXPERT_TOKENS map — see
     * TokenRepository). Used by LiveCallingScreen/IncomingCallActivity
     * for real calls between signed-up users.
     */
    fun joinChannelWithToken(context: Context, channelName: String, token: String, isVideoCall: Boolean) {
        if (AgoraConfig.APP_ID.isBlank() || AgoraConfig.APP_ID == "PUT_YOUR_AGORA_APP_ID_HERE") {
            _connectionState.value = CallConnectionState.FAILED
            _errorMessage.value = "Add your Agora App ID in AgoraConfig.kt first — see README.md."
            return
        }
        joinInternal(context, channelName, token, isVideoCall)
    }

    private fun joinInternal(context: Context, channelName: String, token: String, isVideoCall: Boolean) {
        _connectionState.value = CallConnectionState.JOINING
        _errorMessage.value = null
        _remoteUid.value = null

        try {
            val rtcEngine = engine ?: RtcEngine.create(
                RtcEngineConfig().apply {
                    mContext = context.applicationContext
                    mAppId = AgoraConfig.APP_ID
                    mEventHandler = eventHandler
                }
            ).also { engine = it }

            rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)

            if (isVideoCall) {
                rtcEngine.enableVideo()
                rtcEngine.startPreview()
            } else {
                rtcEngine.disableVideo()
            }

            val options = ChannelMediaOptions().apply {
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                autoSubscribeAudio = true
                autoSubscribeVideo = true
                publishMicrophoneTrack = true
                publishCameraTrack = isVideoCall
            }

            rtcEngine.joinChannel(token, channelName, 0, options)
        } catch (e: Exception) {
            Log.e(TAG, "joinChannel failed", e)
            _connectionState.value = CallConnectionState.FAILED
            _errorMessage.value = e.message ?: "Couldn't start the call engine."
        }
    }

    fun muteLocalAudio(mute: Boolean) {
        engine?.muteLocalAudioStream(mute)
    }

    fun muteLocalVideo(mute: Boolean) {
        engine?.muteLocalVideoStream(mute)
    }

    fun switchCamera() {
        engine?.switchCamera()
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        engine?.setEnableSpeakerphone(enabled)
    }

    /** Leaves the channel and fully tears down the engine. Safe to call multiple times. */
    fun leaveAndRelease() {
        val current = engine ?: run {
            _connectionState.value = CallConnectionState.IDLE
            _remoteUid.value = null
            _errorMessage.value = null
            return
        }
        current.leaveChannel()
        current.stopPreview()
        RtcEngine.destroy()
        engine = null
        _connectionState.value = CallConnectionState.IDLE
        _remoteUid.value = null
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun friendlyErrorFor(err: Int): String = when (err) {
        Constants.ERR_INVALID_APP_ID -> "Invalid Agora App ID — check AgoraConfig.kt."
        Constants.ERR_INVALID_CHANNEL_NAME -> "Invalid channel name."
        Constants.ERR_INVALID_TOKEN, Constants.ERR_TOKEN_EXPIRED -> "Invalid or expired token — see TokenRepository (HARDCODED_EXPERT_TOKENS or the Cloud Function) for how tokens get refreshed."
        else -> "Call engine error (code $err). Check Logcat for \"AgoraCallManager\"."
    }

    /** Creates a bare video-rendering surface for either local (uid null) or a specific remote uid. */
    fun createRendererView(context: Context) = android.view.SurfaceView(context)

    fun setupLocalVideo(view: android.view.SurfaceView) {
        engine?.setupLocalVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, 0))
    }

    fun setupRemoteVideo(view: android.view.SurfaceView, uid: Int) {
        engine?.setupRemoteVideo(VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid))
    }
}
