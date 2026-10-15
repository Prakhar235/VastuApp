package com.vastutalks.app.data.call

import android.content.Context
import android.util.Log
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.DataStreamConfig
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
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val _connectionState = MutableStateFlow(CallConnectionState.IDLE)
    val connectionState: StateFlow<CallConnectionState> = _connectionState.asStateFlow()

    private val _remoteUid = MutableStateFlow<Int?>(null)
    val remoteUid: StateFlow<Int?> = _remoteUid.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** This device is sending camera video (a video call, or an audio call switched to video). */
    private val _localVideoOn = MutableStateFlow(false)
    val localVideoOn: StateFlow<Boolean> = _localVideoOn.asStateFlow()

    /** The other person is sending camera video. */
    private val _remoteVideoOn = MutableStateFlow(false)
    val remoteVideoOn: StateFlow<Boolean> = _remoteVideoOn.asStateFlow()

    /** The other person was in the call and has hung up (or dropped out). The call is over. */
    private val _remoteLeft = MutableStateFlow(false)
    val remoteLeft: StateFlow<Boolean> = _remoteLeft.asStateFlow()

    /**
     * A reliable, ordered data stream both sides use to tell each other
     * "camera on/off" and "hanging up" straight away, so neither depends on
     * how quickly (or whether) Agora's own media-state callbacks arrive.
     */
    private var controlStreamId: Int? = null
    private const val MSG_VIDEO_ON = "video:on"
    private const val MSG_VIDEO_OFF = "video:off"
    private const val MSG_BYE = "bye"

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "Joined channel=$channel uid=$uid")
            mainHandler.post {
                controlStreamId = engine?.createDataStream(DataStreamConfig().apply { syncWithAudio = false; ordered = true })
                    ?.takeIf { it >= 0 }
                // The other side may have shown up before the stream existed.
                if (_remoteUid.value != null) sendControl(if (_localVideoOn.value) MSG_VIDEO_ON else MSG_VIDEO_OFF)
                _connectionState.value = CallConnectionState.JOINED
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "Remote user joined uid=$uid")
            _remoteUid.value = uid
            // Tell the newcomer whether our camera is on.
            sendControl(if (_localVideoOn.value) MSG_VIDEO_ON else MSG_VIDEO_OFF)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            // QUIT: they hung up. DROPPED: Agora already waited ~20 s for them to come back.
            Log.i(TAG, "Remote user left uid=$uid reason=$reason")
            if (_remoteUid.value == uid) remoteHungUp()
        }

        override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray?) {
            val message = data?.toString(Charsets.UTF_8) ?: return
            Log.i(TAG, "Control message from uid=$uid: $message")
            if (_remoteUid.value == null) _remoteUid.value = uid
            when (message) {
                MSG_VIDEO_ON -> _remoteVideoOn.value = true
                MSG_VIDEO_OFF -> _remoteVideoOn.value = false
                MSG_BYE -> remoteHungUp()
            }
        }

        // Agora's own signals for the other side's camera, kept alongside the
        // control messages in case the other app is an older build without them.
        // The two older callbacks are only trusted for "camera went off".
        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            Log.i(TAG, "Remote video uid=$uid state=$state reason=$reason")
            when {
                reason == Constants.REMOTE_VIDEO_STATE_REASON_REMOTE_MUTED ||
                    reason == Constants.REMOTE_VIDEO_STATE_REASON_REMOTE_OFFLINE -> _remoteVideoOn.value = false
                state == Constants.REMOTE_VIDEO_STATE_STARTING || state == Constants.REMOTE_VIDEO_STATE_DECODING -> _remoteVideoOn.value = true
                state == Constants.REMOTE_VIDEO_STATE_STOPPED || state == Constants.REMOTE_VIDEO_STATE_FAILED -> _remoteVideoOn.value = false
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onUserMuteVideo(uid: Int, muted: Boolean) {
            Log.i(TAG, "Remote video muted uid=$uid muted=$muted")
            if (muted) _remoteVideoOn.value = false
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onUserEnableLocalVideo(uid: Int, enabled: Boolean) {
            Log.i(TAG, "Remote camera enabled uid=$uid enabled=$enabled")
            if (!enabled) _remoteVideoOn.value = false
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
        _remoteLeft.value = false
        controlStreamId = null

        try {
            val rtcEngine = engine ?: RtcEngine.create(
                RtcEngineConfig().apply {
                    mContext = context.applicationContext
                    mAppId = AgoraConfig.APP_ID
                    mEventHandler = eventHandler
                }
            ).also { engine = it }

            rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)

            // Video is always enabled so either side can switch an audio call to
            // video (and still see the other's camera); an audio call just
            // doesn't capture or send its own camera until switched.
            rtcEngine.enableVideo()
            rtcEngine.enableLocalVideo(isVideoCall)
            if (isVideoCall) rtcEngine.startPreview()
            _localVideoOn.value = isVideoCall
            _remoteVideoOn.value = false

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

    /** Switches this side between audio only and sending camera video, mid-call, and tells the other side. */
    fun setLocalVideo(on: Boolean) {
        val rtcEngine = engine ?: return
        rtcEngine.enableLocalVideo(on)
        if (on) rtcEngine.startPreview() else rtcEngine.stopPreview()
        rtcEngine.updateChannelMediaOptions(ChannelMediaOptions().apply { publishCameraTrack = on })
        rtcEngine.muteLocalVideoStream(!on)
        _localVideoOn.value = on
        sendControl(if (on) MSG_VIDEO_ON else MSG_VIDEO_OFF)
    }

    private fun sendControl(message: String) {
        val id = controlStreamId ?: return
        val result = engine?.sendStreamMessage(id, message.toByteArray(Charsets.UTF_8))
        if (result != null && result < 0) Log.w(TAG, "Couldn't send \"$message\" (code $result)")
    }

    private fun remoteHungUp() {
        _remoteUid.value = null
        _remoteVideoOn.value = false
        _remoteLeft.value = true
    }

    fun switchCamera() {
        engine?.switchCamera()
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        engine?.setEnableSpeakerphone(enabled)
    }

    /**
     * Tells the other side we're hanging up, then leaves the channel and
     * fully tears down the engine. Safe to call multiple times.
     */
    fun leaveAndRelease() {
        _localVideoOn.value = false
        _remoteVideoOn.value = false
        _remoteLeft.value = false
        val current = engine ?: run {
            _connectionState.value = CallConnectionState.IDLE
            _remoteUid.value = null
            _errorMessage.value = null
            return
        }
        sendControl(MSG_BYE)
        controlStreamId = null
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
