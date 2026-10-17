import AVFoundation
import FirebaseAuth
import FirebaseFirestore
import SwiftUI

enum CallPermissions {
    /// Asks for the microphone (and camera for video calls). Returns false if any was refused.
    @discardableResult
    static func request(video: Bool) async -> Bool {
        let mic = await AVCaptureDevice.requestAccess(for: .audio)
        let cam = video ? await AVCaptureDevice.requestAccess(for: .video) : true
        return mic && cam
    }
}

/// How long a call rings before the caller gives up.
private let ringTimeoutNanos: UInt64 = 45_000_000_000
/// How long to wait for the other person to join after the call was answered.
private let remoteJoinTimeoutNanos: UInt64 = 30_000_000_000

/// The pulsing initials/avatar badge on ringing screens.
private struct PulsingBadge<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @State private var pulse = false

    var body: some View {
        Circle()
            .fill(Color.vastuPrimary.opacity(0.25))
            .frame(width: 120, height: 120)
            .overlay(content())
            .scaleEffect(pulse ? 1.15 : 1)
            .onAppear {
                withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true }
            }
    }
}

private func endCallButton(label: String, action: @escaping () -> Void) -> some View {
    Button(action: action) {
        Circle().fill(Color.danger).frame(width: 64, height: 64)
            .overlay(Image(systemName: "phone.down.fill").font(.system(size: 24)).foregroundColor(.white))
    }
    .accessibilityLabel(label)
}

/// Caller side of a real (Firestore-signaled) call to a signed-up expert:
/// check they aren't on another call (being offline is fine: the push rings
/// their phone) → write a CallRequest → wait for Accept/Decline (giving up
/// after 45 s) → fetch a token → join Agora.
struct LiveCallingView: View {
    let calleeUid: String
    let calleeName: String
    let callType: CallType
    let onConnected: (_ channelName: String, _ peerName: String) -> Void
    let onCancel: () -> Void

    @ObservedObject private var agora = AgoraCallManager.shared
    @State private var statusText = ""
    @State private var errorText: String?
    @State private var permissionsDenied = false
    @State private var callId: String?
    @State private var channelName: String?
    @State private var statusListener: ListenerRegistration?
    @State private var answered = false
    @State private var started = false

    var body: some View {
        ZStack {
            Color.vastuCharcoal.ignoresSafeArea()
            VStack(spacing: 0) {
                PulsingBadge {
                    Circle().fill(Color.vastuPrimary).frame(width: 96, height: 96)
                        .overlay(Text(initials(calleeName)).font(.vt(28)).foregroundColor(.white))
                }
                Spacer().frame(height: 28)
                Text(calleeName).font(.vt(20)).foregroundColor(.white)
                Spacer().frame(height: 6)
                Text(permissionsDenied ? "Microphone/camera permission is needed to place this call." : (errorText ?? statusText))
                    .font(.vt(14))
                    .foregroundColor(permissionsDenied || errorText != nil ? Color(hex: 0xFFB4A9) : .white.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                Spacer().frame(height: 64)
                endCallButton(label: "Cancel call") {
                    if let id = callId {
                        Task { try? await CallSignalingRepository().updateStatus(callId: id, status: .cancelled) }
                    }
                    statusListener?.remove()
                    AgoraCallManager.shared.leaveAndRelease()
                    onCancel()
                }
            }
        }
        .darkScreenStatusBar()
        .onAppear(perform: start)
        .onDisappear { statusListener?.remove() }
        .onChange(of: agora.connectionState) { state in
            // The Agora channel ("expert-<uid>"), which the in-call screen and the
            // call-history logging use to tell which side is the expert.
            if state == .joined, let channel = channelName { onConnected(channel, calleeName) }
        }
        .onChange(of: agora.errorMessage) { message in
            if let message = message { errorText = message }
        }
    }

    private func start() {
        guard !started else { return }
        started = true
        statusText = "Calling \(calleeName)…"
        Task {
            if !(await CallPermissions.request(video: callType == .video)) { permissionsDenied = true }
            guard let me = Auth.auth().currentUser else {
                errorText = "You're not signed in."
                return
            }
            // Offline is fine — the push rings their phone — but don't ring someone mid-call.
            if let expert = try? await UserRepository().getExpert(calleeUid) {
                let now = millisNow()
                if expert.isBusy(at: now) {
                    errorText = "\(calleeName) is on another call. Please try again in a few minutes."
                    return
                }
                if !expert.isPresent(at: now) {
                    statusText = "Ringing \(calleeName)'s phone…\nThey're offline, so it may take a moment."
                }
            }
            do {
                let request = try await CallSignalingRepository().createCallRequest(
                    callerUid: me.uid, callerName: me.displayName ?? "Someone",
                    calleeUid: calleeUid, calleeName: calleeName, callType: callType
                )
                callId = request.callId
                channelName = request.channelName
                listen(to: request.callId)
                // Stop ringing if nobody answers.
                try? await Task.sleep(nanoseconds: ringTimeoutNanos)
                if !answered && callId == request.callId {
                    try? await CallSignalingRepository().updateStatus(callId: request.callId, status: .cancelled)
                    errorText = "\(calleeName) didn't answer. Please try again later."
                }
            } catch {
                errorText = error.localizedDescription
            }
        }
    }

    /// Watches the call request for the callee's response.
    private func listen(to id: String) {
        statusListener = CallSignalingRepository().listenForCallStatus(callId: id) { call in
            guard let call = call else { return }
            switch CallRequestStatus(rawValue: call.status) {
            case .accepted:
                guard !answered else { return } // the listener can fire again for the same accept
                answered = true
                statusText = "Connecting…"
                Task {
                    do {
                        let token = try await TokenRepository().fetchOrCreateExpertToken(expertUid: calleeUid, channelName: call.channelName)
                        AgoraCallManager.shared.joinChannel(channelName: call.channelName, token: token, isVideoCall: callType == .video)
                    } catch {
                        errorText = error.localizedDescription
                    }
                }
            case .declined:
                answered = true
                errorText = "\(calleeName) declined the call."
            default:
                break
            }
        }
    }
}

/// In-call UI for the real signaling flow. Either side can switch between
/// audio and video at any point (`callType` is only how it started); the
/// call ends for both when one hangs up. Prices and the wallet stay hidden
/// while calls are free (CallPricing).
struct LiveInCallView: View {
    let channelName: String
    let peerName: String
    let callType: CallType
    let onEndCall: (_ durationSeconds: Int, _ startTimeMillis: Int64) -> Void

    @ObservedObject private var agora = AgoraCallManager.shared
    @State private var elapsedSeconds = 0
    @State private var startTimeMillis = millisNow()
    @State private var isMuted = false
    @State private var isSpeakerOn = true
    @State private var showDrawingCanvas = false
    @State private var showChat = false
    @State private var expert: ExpertProfile?
    /// Who to chat with: the user knows the expert's uid from the channel; the expert looks up who called.
    @State private var peerUid: String?
    @State private var callEnded = false
    @State private var toast: String?
    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var expertUid: String {
        channelName.hasPrefix("expert-") ? String(channelName.dropFirst("expert-".count)) : channelName
    }

    /// The channel encodes the expert's own uid, so this tells which side of the call this device is on.
    private var isExpertOnThisDevice: Bool { Auth.auth().currentUser?.uid == expertUid }

    var body: some View {
        // The user sees the expert's real photo and specialty; the expert sees
        // the caller's initials (users' profiles are private to them).
        let peerPhoto = isExpertOnThisDevice ? "" : expert?.photo ?? ""
        let peerSubtitle = isExpertOnThisDevice ? "Client" : expert?.displaySpecialty ?? "Vastu Expert"
        let ratePerMinute = Double(ExpertDisplayExtras.forExpert(expertUid).pricePerSession) / 60
        let cost = Double(elapsedSeconds) / 60 * ratePerMinute
        let timerLabel = String(format: "%d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
        let showRemoteVideo = agora.remoteUid != nil && agora.remoteVideoOn

        ZStack {
            LinearGradient(colors: [.vastuPrimary, .vastuPrimaryDark], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            if showRemoteVideo, let uid = agora.remoteUid {
                RemoteVideoView(uid: uid).ignoresSafeArea()
            }

            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    pill(timerLabel)
                    pill(agora.localVideoOn || agora.remoteVideoOn ? "Video call" : "Audio call")
                    Spacer()
                    if CallPricing.paidCalls {
                        pill(String(format: "Cost: ₹%.2f", cost))
                        pill(String(format: "₹%.2f", max(0, 2450 - cost)))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                Spacer()
                if !showRemoteVideo {
                    VStack(spacing: 16) {
                        Circle().fill(Color.white.opacity(0.15)).frame(width: 140, height: 140)
                            .overlay(ProfileAvatar(photo: peerPhoto, name: peerName, size: 124))
                        if agora.localVideoOn && agora.remoteUid == nil {
                            Text("Connecting you with \(peerName.split(separator: " ").first.map(String.init) ?? peerName)…")
                                .font(.vt(13)).foregroundColor(.white.opacity(0.7)).multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                        }
                        VStack(spacing: 2) {
                            Text(peerName).font(.vt(16)).foregroundColor(.white)
                            Text(peerSubtitle).font(.vt(12)).foregroundColor(.white.opacity(0.8))
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.black.opacity(0.25)))
                    }
                    Spacer()
                }

                // Your own camera, on top of the other person's video, while this side sends video
                // (hidden under the chat).
                if agora.localVideoOn && !showChat {
                    LocalVideoView()
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .frame(width: 90, height: 120)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .padding(.trailing, 20)
                        .padding(.top, 4)
                        .padding(.bottom, 12)
                }

                HStack {
                    Spacer()
                    controlButton(isMuted ? "mic.slash.fill" : "mic.fill", active: isMuted, label: isMuted ? "Unmute" : "Mute") {
                        isMuted.toggle()
                        AgoraCallManager.shared.muteLocalAudio(isMuted)
                    }
                    Spacer()
                    endCallButton(label: "End call") { endCall() }
                    Spacer()
                    controlButton(agora.localVideoOn ? "video.fill" : "video.slash.fill", active: agora.localVideoOn,
                                  label: agora.localVideoOn ? "Switch to audio only" : "Switch to video") { toggleVideo() }
                    Spacer()
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 20)

                HStack(spacing: 24) {
                    controlButton("speaker.wave.2.fill", active: isSpeakerOn, label: isSpeakerOn ? "Speaker off" : "Speaker on") {
                        isSpeakerOn.toggle()
                    }
                    if agora.localVideoOn {
                        controlButton("arrow.triangle.2.circlepath.camera.fill", active: false, label: "Switch camera") {
                            AgoraCallManager.shared.switchCamera()
                        }
                    }
                }
                .padding(.bottom, 16)

                HStack(spacing: 16) {
                    smallButton("bubble.left.and.bubble.right.fill", label: "Chat") {
                        if peerUid != nil { showChat = true } else { toast = "Chat is still loading — try again in a moment." }
                    }
                    if isExpertOnThisDevice {
                        smallButton("pencil.tip", label: "Sketch") { showDrawingCanvas = true }
                    }
                }
                .padding(.bottom, 20)
            }

            // The chat with the other person, over the call (which keeps going underneath).
            // An overlay, not a full-screen cover, so this screen doesn't get onDisappear and hang up.
            if showChat, let uid = peerUid {
                ConversationView(otherUid: uid, otherName: peerName, onBack: { showChat = false },
                                 onCall: { _, _ in }, showCallButton: false)
                    .transition(.move(edge: .bottom))
            }

            if showDrawingCanvas {
                DrawingCanvasOverlay(timerLabel: timerLabel, peerName: peerName, peerSpecialty: peerSubtitle,
                                     peerPhoto: peerPhoto) { _ in showDrawingCanvas = false }
            }
        }
        .animation(.easeInOut(duration: 0.25), value: showChat)
        .toast($toast)
        .darkScreenStatusBar()
        .onReceive(ticker) { _ in elapsedSeconds += 1 }
        .onAppear(perform: start)
        .onChange(of: isSpeakerOn) { AgoraCallManager.shared.setSpeakerphoneEnabled($0) }
        // When the other person hangs up (or drops out), end the call here too.
        .onChange(of: agora.remoteLeft) { left in
            guard left else { return }
            toast = "\(peerName) ended the call"
            endCall()
        }
        // Same if they never join.
        .task(id: agora.remoteUid == nil) {
            guard agora.remoteUid == nil else { return }
            try? await Task.sleep(nanoseconds: remoteJoinTimeoutNanos)
            guard !Task.isCancelled, agora.remoteUid == nil, !agora.remoteLeft else { return }
            toast = "\(peerName) couldn't join the call"
            endCall()
        }
        .onDisappear {
            AgoraCallManager.shared.leaveAndRelease()
            if isExpertOnThisDevice { Task.detached { try? await UserRepository().setInCall(false) } }
        }
    }

    private func start() {
        AgoraCallManager.shared.setSpeakerphoneEnabled(isSpeakerOn)
        if isExpertOnThisDevice {
            // The expert shows as "on a call" to everyone else until this screen closes.
            Task.detached { try? await UserRepository().setInCall(true) }
            let expertUid = self.expertUid
            Task { peerUid = await CallSignalingRepository().latestAcceptedCallerUid(expertUid: expertUid) }
        } else {
            peerUid = expertUid
            let expertUid = self.expertUid
            Task { expert = try? await UserRepository().getExpert(expertUid) }
        }
    }

    private func endCall() {
        guard !callEnded else { return }
        callEnded = true
        onEndCall(elapsedSeconds, startTimeMillis)
    }

    private func toggleVideo() {
        if agora.localVideoOn {
            AgoraCallManager.shared.setLocalVideo(false)
            return
        }
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                if granted {
                    AgoraCallManager.shared.setLocalVideo(true)
                } else {
                    toast = "Camera permission is needed to switch to video."
                }
            }
        }
    }

    private func pill(_ text: String) -> some View {
        Text(text).font(.vt(12)).foregroundColor(.white)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.18)))
    }

    private func controlButton(_ icon: String, active: Bool, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Circle().fill(active ? Color.white : Color.white.opacity(0.15)).frame(width: 56, height: 56)
                .overlay(Image(systemName: icon).font(.system(size: 20)).foregroundColor(active ? .vastuPrimaryDark : .white))
        }
        .accessibilityLabel(label)
    }

    private func smallButton(_ icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Circle().fill(Color.white.opacity(0.15)).frame(width: 48, height: 48)
                .overlay(Image(systemName: icon).font(.system(size: 18)).foregroundColor(.white))
        }
        .accessibilityLabel(label)
    }
}

/// A local freehand drawing surface over the in-call screen — lets the
/// expert sketch during a consultation. Not a real shared whiteboard
/// yet: strokes stay on this device (same as Android).
struct DrawingCanvasOverlay: View {
    let timerLabel: String
    let peerName: String
    let peerSpecialty: String
    let peerPhoto: String
    let onClose: (UIImage?) -> Void

    private struct Stroke { var points: [CGPoint]; let color: Color }
    private static let palette: [Color] = [.white, .vastuSaffron, .vastuCopper, .boardBlue, .boardRed]

    @State private var strokes: [Stroke] = []
    @State private var current: Stroke?
    @State private var color = Color.white
    @State private var canvasSize = CGSize.zero

    var body: some View {
        ZStack {
            Color(hex: 0x1A1824).ignoresSafeArea()
            GeometryReader { geo in
                Canvas { ctx, _ in
                    for stroke in strokes + (current.map { [$0] } ?? []) {
                        ctx.stroke(path(stroke.points), with: .color(stroke.color),
                                   style: StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                    }
                }
                .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                    if current == nil { current = Stroke(points: [value.startLocation], color: color) }
                    current?.points.append(value.location)
                }.onEnded { _ in
                    if let stroke = current { strokes.append(stroke) }
                    current = nil
                })
                .onAppear { canvasSize = geo.size }
            }
            .ignoresSafeArea()

            VStack {
                HStack(spacing: 12) {
                    HStack(spacing: 8) {
                        ProfileAvatar(photo: peerPhoto, name: peerName, size: 28)
                        Text(peerName).font(.vt(13)).foregroundColor(.white).lineLimit(1)
                        Text("·  \(peerSpecialty)").font(.vt(11)).foregroundColor(.white.opacity(0.7)).lineLimit(1)
                    }
                    Spacer()
                    Text(timerLabel).font(.vt(12)).foregroundColor(.white)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.15)))
                    Button(action: finish) {
                        HStack(spacing: 4) {
                            Image(systemName: "checkmark").font(.system(size: 13, weight: .bold))
                            Text("Done").font(.vt(13))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.vastuPrimary))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                Spacer()

                HStack {
                    HStack(spacing: 10) {
                        ForEach(Self.palette, id: \.self) { c in
                            Circle().fill(c).frame(width: 32, height: 32)
                                .overlay(Circle().stroke(Color.white.opacity(0.9), lineWidth: c == color ? 3 : 0))
                                .onTapGesture { color = c }
                        }
                    }
                    Spacer()
                    Button { strokes = [] } label: {
                        Circle().fill(Color.white.opacity(0.15)).frame(width: 40, height: 40)
                            .overlay(Image(systemName: "trash.fill").font(.system(size: 16)).foregroundColor(.white))
                    }
                    .accessibilityLabel("Clear")
                }
                .padding(20)
            }
        }
    }

    private func path(_ points: [CGPoint]) -> Path {
        var p = Path()
        guard let first = points.first else { return p }
        p.move(to: first)
        points.dropFirst().forEach { p.addLine(to: $0) }
        return p
    }

    private func finish() {
        guard !strokes.isEmpty, canvasSize.width > 0 else { return onClose(nil) }
        let image = UIGraphicsImageRenderer(size: canvasSize).image { context in
            UIColor(red: 0x1A / 255, green: 0x18 / 255, blue: 0x24 / 255, alpha: 1).setFill()
            context.fill(CGRect(origin: .zero, size: canvasSize))
            for stroke in strokes {
                let bezier = UIBezierPath(cgPath: path(stroke.points).cgPath)
                bezier.lineWidth = 3
                bezier.lineCapStyle = .round
                bezier.lineJoinStyle = .round
                UIColor(stroke.color).setStroke()
                bezier.stroke()
            }
        }
        onClose(image)
    }
}

/// Full-screen incoming-call UI, opened by tapping the push notification —
/// the iOS counterpart of Android's IncomingCallActivity. Closes by itself
/// if the call stops ringing some other way (the caller hung up, it was
/// answered or declined in the app, or it timed out).
struct IncomingCallView: View {
    let call: IncomingCallPush

    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var agora = AgoraCallManager.shared
    @State private var isConnecting = false
    @State private var errorText: String?
    @State private var statusListener: ListenerRegistration?

    private var isVideo: Bool { call.callType == "VIDEO" }

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.vastuCharcoal.ignoresSafeArea()
            VStack(spacing: 0) {
                Circle().fill(Color.vastuPrimary).frame(width: 110, height: 110)
                    .overlay(Text(initials(call.callerName)).font(.vt(32)).foregroundColor(.white))
                Text(call.callerName).font(.vt(22)).foregroundColor(.white).padding(.top, 24)
                Text(isVideo ? "Incoming video call" : "Incoming audio call")
                    .font(.vt(14)).foregroundColor(.white.opacity(0.7)).padding(.top, 6)
                if let error = errorText {
                    Text(error).font(.vt(13)).foregroundColor(Color(hex: 0xFFB4A9))
                        .multilineTextAlignment(.center).padding(.horizontal, 32).padding(.top, 16)
                }
                if isConnecting {
                    ProgressView().tint(.vastuPrimary).scaleEffect(1.4).padding(.top, 48)
                }
                Spacer()
            }
            .padding(.top, 80)
            .frame(maxWidth: .infinity)

            if !isConnecting {
                HStack {
                    Spacer()
                    Button(action: decline) {
                        Circle().fill(Color.danger).frame(width: 64, height: 64)
                            .overlay(Image(systemName: "phone.down.fill").font(.system(size: 24)).foregroundColor(.white))
                    }
                    .accessibilityLabel("Decline")
                    Spacer()
                    Button(action: accept) {
                        Circle().fill(Color.success).frame(width: 64, height: 64)
                            .overlay(Image(systemName: "phone.fill").font(.system(size: 24)).foregroundColor(.white))
                    }
                    .accessibilityLabel("Accept")
                    Spacer()
                }
                .padding(.bottom, 56)
            }
        }
        .darkScreenStatusBar()
        .onAppear {
            IncomingCallAlerts.clearNotification(callId: call.callId)
            statusListener = CallSignalingRepository().listenForCallStatus(callId: call.callId) { request in
                guard !isConnecting, let request = request, request.status != CallRequestStatus.ringing.rawValue else { return }
                dismiss()
            }
        }
        .onDisappear { statusListener?.remove() }
        .onChange(of: agora.connectionState) { state in
            guard state == .joined else { return }
            // Joined — land straight on the in-call screen, no detour through Home.
            AppRouter.shared.setRoot(.home, path: [.liveInCall(channelName: call.channelName, peerName: call.callerName,
                                                               callType: isVideo ? .video : .audio)])
            dismiss()
        }
    }

    private func accept() {
        // Accepted here, so the status changing to ACCEPTED shouldn't close this screen.
        isConnecting = true
        errorText = nil
        Task {
            try? await CallSignalingRepository().updateStatus(callId: call.callId, status: .accepted)
            await CallPermissions.request(video: isVideo)
            guard let calleeUid = Auth.auth().currentUser?.uid else {
                errorText = "You're not signed in."
                isConnecting = false
                return
            }
            do {
                let token = try await TokenRepository().fetchOrCreateExpertToken(expertUid: calleeUid, channelName: call.channelName)
                AgoraCallManager.shared.joinChannel(channelName: call.channelName, token: token, isVideoCall: isVideo)
            } catch {
                errorText = error.localizedDescription
                isConnecting = false
            }
        }
    }

    private func decline() {
        Task { try? await CallSignalingRepository().updateStatus(callId: call.callId, status: .declined) }
        dismiss()
    }
}
