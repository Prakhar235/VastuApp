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
/// write a CallRequest → wait for Accept/Decline → fetch a token → join Agora.
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
        .onAppear(perform: start)
        .onDisappear { statusListener?.remove() }
        .onChange(of: agora.connectionState) { state in
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
            do {
                let request = try await CallSignalingRepository().createCallRequest(
                    callerUid: me.uid, callerName: me.displayName ?? "Someone",
                    calleeUid: calleeUid, calleeName: calleeName, callType: callType
                )
                callId = request.callId
                listen(to: request.callId)
            } catch {
                errorText = error.localizedDescription
            }
        }
    }

    /// Watches the call request for the callee's response.
    private func listen(to id: String) {
        var joining = false
        statusListener = CallSignalingRepository().listenForCallStatus(callId: id) { call in
            guard let call = call else { return }
            switch CallRequestStatus(rawValue: call.status) {
            case .accepted:
                guard !joining else { return }
                joining = true
                statusText = "Connecting…"
                channelName = call.channelName
                Task {
                    do {
                        let token = try await TokenRepository().fetchOrCreateExpertToken(expertUid: calleeUid, channelName: call.channelName)
                        AgoraCallManager.shared.joinChannel(channelName: call.channelName, token: token, isVideoCall: callType == .video)
                    } catch {
                        errorText = error.localizedDescription
                    }
                }
            case .declined:
                errorText = "\(calleeName) declined the call."
            default:
                break
            }
        }
    }
}

/// In-call UI for the real signaling flow. Cost and wallet balance are
/// simulated from the expert's placeholder rate — there's no billing
/// backend yet, same as Android.
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
    @State private var isCameraOff = false
    @State private var showDrawingCanvas = false
    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var expertUid: String {
        channelName.hasPrefix("expert-") ? String(channelName.dropFirst("expert-".count)) : channelName
    }

    var body: some View {
        let extras = ExpertDisplayExtras.forExpert(expertUid)
        // Only the expert gets the draw tool: the channel encodes the expert's own uid.
        let isExpertOnThisDevice = Auth.auth().currentUser?.uid == expertUid
        let cost = Double(elapsedSeconds) / 60 * (Double(extras.pricePerSession) / 60)
        let remaining = max(0, 2450 - cost)
        let timerLabel = String(format: "%d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
        let showRemoteVideo = callType == .video && agora.remoteUid != nil && !isCameraOff

        ZStack {
            LinearGradient(colors: [.vastuPrimary, .vastuPrimaryDark], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            if showRemoteVideo, let uid = agora.remoteUid {
                RemoteVideoView(uid: uid).ignoresSafeArea()
            }

            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    pill(timerLabel)
                    pill(String(format: "Cost: ₹%.2f", cost))
                    Spacer()
                    pill(String(format: "₹%.2f", remaining))
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                if !showRemoteVideo {
                    Spacer()
                    VStack(spacing: 16) {
                        Circle().fill(Color.white.opacity(0.15)).frame(width: 140, height: 140)
                            .overlay(AvatarImage(seed: extras.avatarSeed(for: expertUid), placeholder: .white)
                                .frame(width: 124, height: 124).clipShape(Circle()))
                        if callType == .video && agora.remoteUid == nil {
                            Text("Connecting you with \(peerName.split(separator: " ").first.map(String.init) ?? peerName)…")
                                .font(.vt(13)).foregroundColor(.white.opacity(0.7)).multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                        }
                        VStack(spacing: 2) {
                            Text(peerName).font(.vt(16)).foregroundColor(.white)
                            Text(extras.specialty).font(.vt(12)).foregroundColor(.white.opacity(0.8))
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.black.opacity(0.25)))
                    }
                    Spacer()
                } else {
                    Spacer()
                }

                // Local camera preview thumbnail (video calls only).
                if callType == .video {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16).fill(Color.black.opacity(0.35))
                        if isCameraOff {
                            Image(systemName: "person.fill").font(.system(size: 34)).foregroundColor(.white.opacity(0.6))
                        } else {
                            LocalVideoView().clipShape(RoundedRectangle(cornerRadius: 16))
                        }
                    }
                    .frame(width: 90, height: 120)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.trailing, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 12)
                }

                HStack {
                    Spacer()
                    controlButton(isMuted ? "mic.slash.fill" : "mic.fill", active: isMuted) {
                        isMuted.toggle()
                        AgoraCallManager.shared.muteLocalAudio(isMuted)
                    }
                    Spacer()
                    endCallButton(label: "End call") { onEndCall(elapsedSeconds, startTimeMillis) }
                    Spacer()
                    if callType == .video {
                        controlButton("arrow.triangle.2.circlepath.camera.fill", active: false) { AgoraCallManager.shared.switchCamera() }
                    } else {
                        controlButton("speaker.wave.2.fill", active: isSpeakerOn) { isSpeakerOn.toggle() }
                    }
                    Spacer()
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 20)

                if callType == .video {
                    controlButton("video.slash.fill", active: isCameraOff) {
                        isCameraOff.toggle()
                        AgoraCallManager.shared.muteLocalVideo(isCameraOff)
                    }
                    .padding(.bottom, 24)
                }

                HStack(spacing: 16) {
                    smallButton("bubble.left.and.bubble.right.fill", label: "Chat") { /* No in-call chat yet — same as Android. */ }
                    if isExpertOnThisDevice {
                        smallButton("pencil.tip", label: "Sketch") { showDrawingCanvas = true }
                    }
                }
                .padding(.bottom, 20)
            }

            if showDrawingCanvas {
                DrawingCanvasOverlay(
                    timerLabel: timerLabel,
                    peerName: peerName,
                    peerSpecialty: extras.specialty,
                    avatarSeed: extras.avatarSeed(for: expertUid)
                ) { _ in showDrawingCanvas = false }
            }
        }
        .onReceive(ticker) { _ in elapsedSeconds += 1 }
        .onAppear { AgoraCallManager.shared.setSpeakerphoneEnabled(isSpeakerOn) }
        .onChange(of: isSpeakerOn) { AgoraCallManager.shared.setSpeakerphoneEnabled($0) }
        .onDisappear { AgoraCallManager.shared.leaveAndRelease() }
    }

    private func pill(_ text: String) -> some View {
        Text(text).font(.vt(12)).foregroundColor(.white)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.18)))
    }

    private func controlButton(_ icon: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Circle().fill(active ? Color.white : Color.white.opacity(0.15)).frame(width: 56, height: 56)
                .overlay(Image(systemName: icon).font(.system(size: 20)).foregroundColor(active ? .vastuPrimaryDark : .white))
        }
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
    let avatarSeed: String
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
                        AvatarImage(seed: avatarSeed, placeholder: .white).frame(width: 28, height: 28).clipShape(Circle())
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
/// the iOS counterpart of Android's IncomingCallActivity.
struct IncomingCallView: View {
    let call: IncomingCallPush

    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var agora = AgoraCallManager.shared
    @State private var isConnecting = false
    @State private var errorText: String?

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
        .onChange(of: agora.connectionState) { state in
            guard state == .joined else { return }
            // Joined — land straight on the in-call screen, no detour through Home.
            AppRouter.shared.setRoot(.home, path: [.liveInCall(channelName: call.channelName, peerName: call.callerName,
                                                               callType: isVideo ? .video : .audio)])
            dismiss()
        }
    }

    private func accept() {
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
