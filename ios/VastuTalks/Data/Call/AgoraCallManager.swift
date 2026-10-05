import AgoraRtcKit
import SwiftUI

/// Same values as the Android app's AgoraConfig.kt. The App ID isn't a
/// secret; the App Certificate is, and only the Cloud Function has it.
enum AgoraConfig {
    static let appId = "6245c3b5f0ce469aa17aeae3377901a9"

    /// Optional shortcut for testing without the Cloud Function: expert
    /// uid → a temp token generated in Agora Console for channel
    /// "expert-<uid>". Like any temp token, these expire after ~24h.
    static let hardcodedExpertTokens: [String: String] = [
        "1AdALcqpscOJc6oHkJXMp2uKhyG3": "007eJxTYJib43F+Rd2UGE7HgutthW8tdojcVEmYv/RwrpAa1zfOnbEKDGZGJqbJxkmmaQbJqSZmlomJhuaJqYmpxsbm5pYGhomWp5RDsxoCGRnWO3kwMzJAIIivzJBaUZBaVKJr6Jji6JNcWFCc7O+VbJbvke0V4VtgVOqdUeluzMAAAHGKKMo=",
        "IqjzQ3qjRDcW9MNkdCD0oeoFaMa2": "007eJxTYDi3q9hiR5T3d7FZPirOUW0PbGfxrf12U79aY7uprH8w2xYFBjMjE9Nk4yTTNIPkVBMzy8REQ/PE1MRUY2Nzc0sDw0RLXZXQrIZARoZfEyazMjJAIIivzJBaUZBaVKLrWZhVFWhcmBXkkhxu6euXneLsYpCfmu+W6JtoxMAAAEM/KHM="
    ]
}

enum CallConnectionState { case idle, joining, joined, failed }

/// Single shared owner of the Agora engine for the whole app — there's
/// only ever one call at a time. The calling screen (or the incoming
/// call screen) starts the join; the in-call screen observes the same
/// state and renders video; whichever is on top when the call ends
/// calls leaveAndRelease().
final class AgoraCallManager: NSObject, ObservableObject {
    static let shared = AgoraCallManager()

    @Published private(set) var connectionState: CallConnectionState = .idle
    @Published private(set) var remoteUid: UInt?
    @Published private(set) var errorMessage: String?

    private(set) var engine: AgoraRtcEngineKit?

    func joinChannel(channelName: String, token: String, isVideoCall: Bool) {
        guard !AgoraConfig.appId.isEmpty else {
            connectionState = .failed
            errorMessage = "Add your Agora App ID in AgoraCallManager.swift first — see README.md."
            return
        }
        connectionState = .joining
        errorMessage = nil
        remoteUid = nil

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
        if isVideoCall {
            rtc.enableVideo()
            rtc.startPreview()
        } else {
            rtc.disableVideo()
        }

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
    func muteLocalVideo(_ mute: Bool) { engine?.muteLocalVideoStream(mute) }
    func switchCamera() { engine?.switchCamera() }
    func setSpeakerphoneEnabled(_ enabled: Bool) { engine?.setEnableSpeakerphone(enabled) }

    /// Leaves the channel and fully tears down the engine. Safe to call more than once.
    func leaveAndRelease() {
        if let current = engine {
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
        DispatchQueue.main.async { self.connectionState = .joined }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        DispatchQueue.main.async { self.remoteUid = uid }
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didOfflineOfUid uid: UInt, reason: AgoraUserOfflineReason) {
        DispatchQueue.main.async {
            if self.remoteUid == uid { self.remoteUid = nil }
        }
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
