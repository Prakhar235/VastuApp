import AgoraRtcKit
import SwiftUI

/// Same values as the Android app's AgoraConfig.kt. The App ID isn't a
/// secret; the App Certificate is, and only the Cloud Function has it.
enum AgoraConfig {
    static let appId = "6245c3b5f0ce469aa17aeae3377901a9"

    /// Empty now that the generateAgoraToken Cloud Function is deployed —
    /// every call gets a fresh token from it. Optional shortcut for testing
    /// without the Cloud Function: expert uid → a temp token generated in
    /// Agora Console for channel "expert-<uid>" (these expire after ~24h).
    static let hardcodedExpertTokens: [String: String] = [:]
}

enum CallConnectionState { case idle, joining, joined, failed }

/// Single shared owner of the Agora engine for the whole app — there's
/// only ever one call at a time. The calling screen (or the incoming
/// call screen) starts the join; the in-call screen observes the same
/// state and renders video; whichever is on top when the call ends
/// calls leaveAndRelease().
///
/// Either side can switch between audio and video mid-call. Camera on/off
/// and hanging up are sent to the other side straight away over a small
/// Agora data stream (same messages as the Android app), alongside
/// Agora's own media-state callbacks.
final class AgoraCallManager: NSObject, ObservableObject {
    static let shared = AgoraCallManager()

    @Published private(set) var connectionState: CallConnectionState = .idle
    @Published private(set) var remoteUid: UInt?
    @Published private(set) var errorMessage: String?
    /// This device is sending camera video (a video call, or an audio call switched to video).
    @Published private(set) var localVideoOn = false
    /// The other person is sending camera video.
    @Published private(set) var remoteVideoOn = false
    /// The other person was in the call and has hung up (or dropped out). The call is over.
    @Published private(set) var remoteLeft = false

    private(set) var engine: AgoraRtcEngineKit?
    /// Reliable, ordered stream both sides use for "camera on/off" and "hanging up".
    private var controlStreamId: Int?
    private static let msgVideoOn = "video:on"
    private static let msgVideoOff = "video:off"
    private static let msgBye = "bye"

    func joinChannel(channelName: String, token: String, isVideoCall: Bool) {
        guard !AgoraConfig.appId.isEmpty else {
            connectionState = .failed
            errorMessage = "Add your Agora App ID in AgoraCallManager.swift first — see README.md."
            return
        }
        connectionState = .joining
        errorMessage = nil
        remoteUid = nil
        remoteLeft = false
        controlStreamId = nil

        let rtc: AgoraRtcEngineKit
        if let existing = engine {
            rtc = existing
        } else {
            let config = AgoraRtcEngineConfig()
            config.appId = AgoraConfig.appId
            rtc = AgoraRtcEngineKit.sharedEngine(with: config, delegate: self)
            engine = rtc
        }

        rtc.setChannelProfile(.communication)
        // Video is always enabled so either side can switch an audio call to
        // video (and still see the other's camera); an audio call just
        // doesn't capture or send its own camera until switched.
        rtc.enableVideo()
        rtc.enableLocalVideo(isVideoCall)
        if isVideoCall { rtc.startPreview() }
        localVideoOn = isVideoCall
        remoteVideoOn = false

        let options = AgoraRtcChannelMediaOptions()
        options.clientRoleType = .broadcaster
        options.channelProfile = .communication
        options.autoSubscribeAudio = true
        options.autoSubscribeVideo = true
        options.publishMicrophoneTrack = true
        options.publishCameraTrack = isVideoCall

        let result = rtc.joinChannel(byToken: token, channelId: channelName, uid: 0, mediaOptions: options, joinSuccess: nil)
        if result != 0 {
            connectionState = .failed
            errorMessage = friendlyError(Int(result))
        }
    }

    func muteLocalAudio(_ mute: Bool) { engine?.muteLocalAudioStream(mute) }
    func switchCamera() { engine?.switchCamera() }
    func setSpeakerphoneEnabled(_ enabled: Bool) { engine?.setEnableSpeakerphone(enabled) }

    /// Switches this side between audio only and sending camera video, mid-call, and tells the other side.
    func setLocalVideo(_ on: Bool) {
        guard let rtc = engine else { return }
        rtc.enableLocalVideo(on)
        if on { rtc.startPreview() } else { rtc.stopPreview() }
        let options = AgoraRtcChannelMediaOptions()
        options.publishCameraTrack = on
        rtc.updateChannel(with: options)
        rtc.muteLocalVideoStream(!on)
        localVideoOn = on
        sendControl(on ? Self.msgVideoOn : Self.msgVideoOff)
    }

    /// Tells the other side we're hanging up, then leaves the channel and
    /// fully tears down the engine. Safe to call more than once.
    func leaveAndRelease() {
        localVideoOn = false
        remoteVideoOn = false
        remoteLeft = false
        if let current = engine {
            sendControl(Self.msgBye)
            controlStreamId = nil
            current.leaveChannel(nil)
            current.stopPreview()
            AgoraRtcEngineKit.destroy()
            engine = nil
        }
        connectionState = .idle
        remoteUid = nil
        errorMessage = nil
    }

    func setupLocalVideo(_ view: UIView) {
        let canvas = AgoraRtcVideoCanvas()
        canvas.view = view
        canvas.renderMode = .hidden
        canvas.uid = 0
        engine?.setupLocalVideo(canvas)
    }

    func setupRemoteVideo(_ view: UIView, uid: UInt) {
        let canvas = AgoraRtcVideoCanvas()
        canvas.view = view
        canvas.renderMode = .hidden
        canvas.uid = uid
        engine?.setupRemoteVideo(canvas)
    }

    private func sendControl(_ message: String) {
        guard let id = controlStreamId, let data = message.data(using: .utf8) else { return }
        let result = engine?.sendStreamMessage(id, data: data) ?? 0
        if result < 0 { print("AgoraCallManager: couldn't send \"\(message)\" (code \(result))") }
    }

    private func remoteHungUp() {
        remoteUid = nil
        remoteVideoOn = false
        remoteLeft = true
    }

    private func friendlyError(_ code: Int) -> String {
        switch code {
        case 101: return "Invalid Agora App ID — check AgoraConfig."
        case 102: return "Invalid channel name."
        case 109, 110: return "Invalid or expired token — see TokenRepository (hardcodedExpertTokens or the Cloud Function) for how tokens get refreshed."
        default: return "Call engine error (code \(code))."
        }
    }
}

extension AgoraCallManager: AgoraRtcEngineDelegate {
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        DispatchQueue.main.async {
            let config = AgoraDataStreamConfig()
            config.ordered = true
            config.syncWithAudio = false
            var streamId = 0
            if engine.createDataStream(&streamId, config: config) == 0, streamId >= 0 {
                self.controlStreamId = streamId
            }
            // The other side may have shown up before the stream existed.
            if self.remoteUid != nil { self.sendControl(self.localVideoOn ? Self.msgVideoOn : Self.msgVideoOff) }
            self.connectionState = .joined
        }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        DispatchQueue.main.async {
            self.remoteUid = uid
            // Tell the newcomer whether our camera is on.
            self.sendControl(self.localVideoOn ? Self.msgVideoOn : Self.msgVideoOff)
        }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didOfflineOfUid uid: UInt, reason: AgoraUserOfflineReason) {
        // Quit: they hung up. Dropped: Agora already waited ~20 s for them to come back.
        DispatchQueue.main.async {
            if self.remoteUid == uid { self.remoteHungUp() }
        }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, receiveStreamMessageFromUid uid: UInt, streamId: Int, data: Data) {
        let message = String(data: data, encoding: .utf8)
        DispatchQueue.main.async {
            if self.remoteUid == nil { self.remoteUid = uid }
            switch message {
            case Self.msgVideoOn: self.remoteVideoOn = true
            case Self.msgVideoOff: self.remoteVideoOn = false
            case Self.msgBye: self.remoteHungUp()
            default: break
            }
        }
    }

    // Agora's own signals for the other side's camera, kept alongside the
    // control messages in case the other app is an older build without them.
    func rtcEngine(_ engine: AgoraRtcEngineKit, remoteVideoStateChangedOfUid uid: UInt, state: AgoraVideoRemoteState,
                   reason: AgoraVideoRemoteReason, elapsed: Int) {
        DispatchQueue.main.async {
            if reason == .remoteMuted || reason == .remoteOffline {
                self.remoteVideoOn = false
            } else if state == .starting || state == .decoding {
                self.remoteVideoOn = true
            } else if state == .stopped || state == .failed {
                self.remoteVideoOn = false
            }
        }
    }

    /// Only trusted for "camera went off".
    func rtcEngine(_ engine: AgoraRtcEngineKit, didVideoMuted muted: Bool, byUid uid: UInt) {
        if muted { DispatchQueue.main.async { self.remoteVideoOn = false } }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didOccurError errorCode: AgoraErrorCode) {
        DispatchQueue.main.async {
            self.connectionState = .failed
            self.errorMessage = self.friendlyError(errorCode.rawValue)
        }
    }
}

/// Local camera preview. Use once video is enabled (the engine must exist).
struct LocalVideoView: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        AgoraCallManager.shared.setupLocalVideo(view)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}

/// A specific remote user's video.
struct RemoteVideoView: UIViewRepresentable {
    let uid: UInt

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .black
        AgoraCallManager.shared.setupRemoteVideo(view, uid: uid)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {}
}
