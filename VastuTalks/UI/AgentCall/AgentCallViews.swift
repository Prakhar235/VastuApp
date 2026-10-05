import AVFoundation
import SwiftUI

/// Ringing screen for a call with the AI agent Ananya. There's no
/// Firestore signaling or Agora token — she's always available, so she
/// "picks up" after a short ring.
struct AgentCallingView: View {
    let onConnected: () -> Void
    let onCancel: () -> Void

    @State private var pulse = false
    @State private var cancelled = false

    var body: some View {
        ZStack {
            Color.vastuCharcoal.ignoresSafeArea()
            VStack(spacing: 0) {
                Circle()
                    .fill(Color.vastuPrimary.opacity(0.25))
                    .frame(width: 120, height: 120)
                    .overlay(AvatarImage(seed: AnanyaAgent.avatarSeed, placeholder: .vastuPrimary)
                        .frame(width: 96, height: 96).clipShape(Circle()))
                    .scaleEffect(pulse ? 1.15 : 1)
                Spacer().frame(height: 28)
                Text(AnanyaAgent.name).font(.vt(20)).foregroundColor(.white)
                Spacer().frame(height: 6)
                Text("Calling…").font(.vt(14)).foregroundColor(.white.opacity(0.7))
                Spacer().frame(height: 6)
                Text("AI Vastu expert").font(.vt(11)).foregroundColor(.white.opacity(0.4))
                Spacer().frame(height: 64)
                Button {
                    cancelled = true
                    onCancel()
                } label: {
                    Circle().fill(Color.danger).frame(width: 64, height: 64)
                        .overlay(Image(systemName: "phone.down.fill").font(.system(size: 26)).foregroundColor(.white))
                }
                .accessibilityLabel("Cancel")
            }
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                if !cancelled { onConnected() }
            }
        }
    }
}

enum AgentCallPage { case board, chat }

enum AgentCallStatus {
    case joining, listening, hearing, thinking, speaking, muted, noMic, textOnly

    var label: String {
        switch self {
        case .joining: return "Joining…"
        case .listening: return "Listening"
        case .hearing: return "Hearing you"
        case .thinking: return "Thinking"
        case .speaking: return "Speaking"
        case .muted: return "You're muted"
        case .noMic: return "Mic access needed"
        case .textOnly: return "Chat to talk"
        }
    }

    var color: Color {
        switch self {
        case .joining, .muted, .textOnly: return CallTones.textMuted
        case .listening, .hearing: return .onlineGreen
        case .thinking: return .vastuSaffron
        case .speaking: return .vastuPrimary
        case .noMic: return .danger
        }
    }
}

/// Runs the call with Ananya: she greets the caller, then waits as long
/// as it takes for them to speak (CallerListener) and answers out loud
/// (AgentVoice). The caller can also type, sketch on the shared board,
/// or share a photo/video of a room. Everything said, every board
/// snapshot, photo and video is saved as the call goes (AgentCallRecorder).
@MainActor
final class AgentCallViewModel: ObservableObject {
    @Published var page = AgentCallPage.board
    @Published var unread = 0
    @Published private(set) var elapsedSeconds = 0
    @Published var isMuted = false { didSet { updateMic() } }
    @Published var penColor = whiteboardPalette[0]
    @Published var chatInput = ""
    @Published private(set) var messages: [AgentChatMessage] = []
    @Published private(set) var pendingTurns = 0 { didSet { updateMic() } }
    @Published private(set) var isTranscribing = false { didSet { updateMic() } }
    @Published private(set) var hasGreeted = false { didSet { updateMic() } }
    @Published private(set) var hasMicPermission = false { didSet { updateMic() } }
    @Published private(set) var isSpeaking = false { didSet { updateMic() } }
    @Published private(set) var isListening = false
    @Published private(set) var isHearing = false
    @Published var photoToReview: CapturedPhoto? { didSet { updateMic() } }
    @Published var videoToReview: CapturedVideo? { didSet { updateMic() } }
    /// The camera picker is showing.
    @Published var cameraMode: CameraMode? { didSet { updateMic() } }
    @Published var isAppActive = true { didSet { updateMic() } }
    @Published var isProcessingMedia = false
    @Published var toast: String?

    let board = WhiteboardModel()
    let agent = VastuAgent()
    private let voice = AgentVoice()
    private let listener = CallerListener()
    private let recorder = AgentCallRecorder()
    private var turnChain: Task<Void, Never>?
    private var micTask: Task<Void, Never>?
    private var micTarget = false
    private var timer: Timer?
    private var started = false
    private var finished = false

    var isAgentTyping: Bool { pendingTurns > 0 }

    var status: AgentCallStatus {
        if isAgentTyping || isTranscribing { return .thinking }
        if isSpeaking { return .speaking }
        if !hasGreeted { return .joining }
        if isHearing { return .hearing }
        if isListening { return .listening }
        if isMuted { return .muted }
        if !hasMicPermission { return .noMic }
        if !agent.isConfigured { return .textOnly }
        return .listening
    }

    var cost: Double { Double(elapsedSeconds) / 60 * (Double(AnanyaAgent.pricePerSession) / 60) }

    func start() {
        guard !started else { return }
        started = true

        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
        try? session.setActive(true)

        voice.onSpeakingChanged = { [weak self] speaking in self?.isSpeaking = speaking }
        listener.onStateChanged = { [weak self] in
            guard let self = self else { return }
            self.isListening = self.listener.isListening
            self.isHearing = self.listener.isHearingSpeech
        }
        listener.onUtterance = { [weak self] wav in self?.heard(wav) }

        session.requestRecordPermission { granted in
            DispatchQueue.main.async { self.hasMicPermission = granted }
        }

        // Ananya picks up and greets the caller.
        askAgent { [agent] in
            let turn = await agent.greet()
            await MainActor.run { self.hasGreeted = true }
            return turn
        }

        let tick = Timer(timeInterval: 1, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            Task { @MainActor in self.elapsedSeconds += 1 }
        }
        RunLoop.main.add(tick, forMode: .common)
        timer = tick
    }

    /// Leaving the screen (End call, app closed) saves the last board and closes the transcript.
    func finish() {
        guard started, !finished else { return }
        finished = true
        timer?.invalidate()
        micTask?.cancel()
        listener.stop()
        voice.stop()
        turnChain?.cancel()
        if board.hasUnsavedChanges { saveBoard(who: "Board", caption: "Board at end of call") }
        recorder.finish()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func toggleMic() {
        if !hasMicPermission {
            AVAudioSession.sharedInstance().requestRecordPermission { granted in
                DispatchQueue.main.async {
                    self.hasMicPermission = granted
                    if !granted { self.showToast("Turn on microphone access for Vastu Talks in Settings to talk to Ananya.") }
                }
            }
        } else {
            isMuted.toggle()
        }
    }

    func selectPage(_ newPage: AgentCallPage) {
        page = newPage
        if newPage == .chat { unread = 0 }
    }

    // MARK: Conversation

    /// Hands-free conversation: the mic is open whenever it's the caller's
    /// turn, and stays open until they actually say something. Closed while
    /// Ananya thinks or speaks (so she doesn't hear herself) and when muted.
    private var callerTurn: Bool {
        hasGreeted && hasMicPermission && agent.isConfigured && !isMuted && !isSpeaking && !isAgentTyping &&
            !isTranscribing && isAppActive && photoToReview == nil && videoToReview == nil && cameraMode == nil && !finished
    }

    private func updateMic() {
        let target = callerTurn
        guard target != micTarget else { return }
        micTarget = target
        micTask?.cancel()
        if target {
            micTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: 500_000_000) // let the speaker's last syllable die away first
                guard let self = self, !Task.isCancelled, self.callerTurn else { return }
                self.listener.start()
            }
        } else {
            listener.stop()
        }
    }

    private func heard(_ wav: Data) {
        micTarget = false // the listener stopped itself after this utterance
        isTranscribing = true
        Task {
            let text = await agent.transcribe(wav)
            isTranscribing = false
            if let text = text { userSaid(text) }
        }
    }

    private func addMessage(_ text: String, sender: ChatSender, image: UIImage? = nil) {
        messages.append(AgentChatMessage(id: messages.count, text: text, sender: sender, image: image))
        if page != .chat && sender == .agent { unread += 1 }
    }

    private func postAgentTurn(_ turn: AgentTurn) {
        guard !finished else { return }
        voice.speak(turn.say)
        addMessage(turn.say, sender: .agent)
        recorder.logLine(AnanyaAgent.name, turn.say)
        if let drawing = turn.board {
            board.showAgentDrawing(drawing)
            page = .board // she's explaining on the board — bring it into view
            saveBoard(who: AnanyaAgent.name, caption: "Drew on the board: \(drawing.title.isEmpty ? "sketch" : drawing.title)")
        }
    }

    /// Runs one agent turn at a time, in order.
    private func askAgent(_ request: @escaping () async -> AgentTurn) {
        pendingTurns += 1
        let previous = turnChain
        turnChain = Task { [weak self] in
            await previous?.value
            let turn = await request()
            guard let self = self else { return }
            self.postAgentTurn(turn)
            self.pendingTurns -= 1
        }
    }

    func userSaid(_ text: String) {
        addMessage(text, sender: .user)
        recorder.logLine("You", text)
        askAgent { [agent] in await agent.reply(text) }
    }

    func sendTyped() {
        let text = chatInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        chatInput = ""
        userSaid(text)
    }

    // MARK: Board

    /// Saves whatever is on the board right now.
    @discardableResult
    private func saveBoard(who: String, caption: String) -> UIImage? {
        guard !board.isEmpty else { return nil }
        let image = board.render()
        recorder.saveDrawing(image, who: who, caption: caption)
        board.markSaved()
        return image
    }

    func sendBoardToAgent() {
        let image = saveBoard(who: "You", caption: "Sent the board to \(AnanyaAgent.name)") ?? board.render()
        addMessage("", sender: .user, image: image.scaledDown(toMaxSide: 360))
        askAgent { [agent] in await agent.reviewSketch(image) }
    }

    func clearBoard() {
        if board.hasUnsavedChanges { saveBoard(who: "You", caption: "Board before clearing") }
        board.clear()
    }

    // MARK: Photos & videos

    func openCamera(_ mode: CameraMode) {
        guard CameraPicker.isAvailable else {
            showToast("No camera found on this device.")
            return
        }
        AVCaptureDevice.requestAccess(for: .video) { granted in
            DispatchQueue.main.async {
                if granted {
                    self.cameraMode = mode
                } else {
                    self.showToast(mode == .photo ? "Camera access is needed to take a photo." : "Camera access is needed to record a video.")
                }
            }
        }
    }

    func cameraTookPhoto(_ image: UIImage, metadata: [String: Any]?) {
        cameraMode = nil
        isProcessingMedia = true
        Task.detached(priority: .userInitiated) {
            let photo = MediaLoader.photo(image, metadata: metadata)
            await MainActor.run {
                self.isProcessingMedia = false
                self.photoToReview = photo
            }
        }
    }

    func cameraRecordedVideo(_ file: URL) {
        cameraMode = nil
        isProcessingMedia = true
        Task.detached(priority: .userInitiated) {
            let video = MediaLoader.video(file)
            await MainActor.run {
                self.isProcessingMedia = false
                if let video = video {
                    self.videoToReview = video
                } else {
                    try? FileManager.default.removeItem(at: file)
                    self.showToast("Couldn't read that video — try again.")
                }
            }
        }
    }

    func retakePhoto() {
        photoToReview = nil
        openCamera(.photo)
    }

    func discardVideo(retake: Bool) {
        if let file = videoToReview?.file { try? FileManager.default.removeItem(at: file) }
        videoToReview = nil
        if retake { openCamera(.video) }
    }

    /// Posts a shared photo or video in the chat as a thumbnail with its caption.
    private func postMedia(_ preview: UIImage, caption: String) {
        addMessage(caption, sender: .user, image: preview.scaledDown(toMaxSide: 480))
        page = .chat // show it with her answer; a drawing would bring the board back
    }

    func sendPhoto(question: String, facing: PhotoFacing?) {
        guard let photo = photoToReview else { return }
        photoToReview = nil
        let caption = (question.isEmpty ? "What does Vastu say about this space?" : question) +
            (facing.map { " · camera facing \($0.direction.label)" } ?? "")
        recorder.savePhoto(photo.image, caption: "Shared a photo: \(caption)")
        postMedia(photo.image, caption: caption)
        askAgent { [agent] in await agent.reviewPhoto(photo.image, question: question, facing: facing) }
    }

    func sendVideo(question: String, facing: PhotoFacing?) {
        guard let video = videoToReview else { return }
        videoToReview = nil
        let seconds = max(1, Int(video.durationSeconds))
        let caption = "Video (\(seconds)s): " + (question.isEmpty ? "What does Vastu say about this space?" : question) +
            (facing.map { " · started facing \($0.direction.label)" } ?? "")
        recorder.saveVideo(video.file, caption: "Shared a video: \(caption)")
        postMedia(video.frames[0], caption: caption)
        askAgent { [agent] in
            await agent.reviewVideo(frames: video.frames, durationSeconds: seconds, question: question, facing: facing)
        }
    }

    func showToast(_ message: String) {
        toast = message
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { [weak self] in
            if self?.toast == message { self?.toast = nil }
        }
    }
}

/// The call with the AI agent Ananya, once she picks up. Two pages,
/// switched from the header: the Board (default) — a shared whiteboard
/// both of them draw on, with live captions — and the Chat, the full
/// conversation plus a box to type.
struct AgentInCallView: View {
    let onEndCall: () -> Void

    @StateObject private var vm = AgentCallViewModel()
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            CallTones.ink.ignoresSafeArea()
            RadialGradient(colors: [Color.vastuPrimary.opacity(0.22), .clear], center: .topLeading, startRadius: 0, endRadius: 510)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                CallHeader(status: vm.status, isSpeaking: vm.isSpeaking,
                           timer: String(format: "%02d:%02d", vm.elapsedSeconds / 60, vm.elapsedSeconds % 60),
                           cost: String(format: "₹%.0f", vm.cost))

                PageSwitch(page: vm.page, unread: vm.unread, onSelect: { vm.selectPage($0) })
                    .padding(.top, 4)
                    .padding(.bottom, 12)

                ZStack {
                    if vm.page == .board {
                        BoardPage(vm: vm, board: vm.board)
                            .transition(.asymmetric(insertion: .move(edge: .leading).combined(with: .opacity),
                                                    removal: .move(edge: .leading).combined(with: .opacity)))
                    } else {
                        AgentChatPage(messages: vm.messages, isAgentTyping: vm.isAgentTyping,
                                      inputText: $vm.chatInput, onSend: { vm.sendTyped() })
                            .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity),
                                                    removal: .move(edge: .trailing).combined(with: .opacity)))
                    }
                }
                .frame(maxHeight: .infinity)
                .animation(.easeInOut(duration: 0.28), value: vm.page)

                CallControls(vm: vm, onEndCall: onEndCall)
            }

            if vm.isProcessingMedia {
                Color.black.opacity(0.4).ignoresSafeArea()
                ProgressView().tint(.white).scaleEffect(1.4)
            }

            if let photo = vm.photoToReview {
                CaptureReviewSheet(
                    frames: [photo.image], exifHeading: photo.exifHeading, isVideo: false, agentName: AnanyaAgent.firstName,
                    onRetake: { vm.retakePhoto() }, onDismiss: { vm.photoToReview = nil },
                    onSend: { vm.sendPhoto(question: $0, facing: $1) }
                )
                .transition(.opacity)
            }

            if let video = vm.videoToReview {
                CaptureReviewSheet(
                    frames: video.frames, exifHeading: nil, isVideo: true, agentName: AnanyaAgent.firstName, // videos carry no compass heading
                    onRetake: { vm.discardVideo(retake: true) }, onDismiss: { vm.discardVideo(retake: false) },
                    onSend: { vm.sendVideo(question: $0, facing: $1) }
                )
                .transition(.opacity)
            }

            if let toast = vm.toast {
                VStack {
                    Spacer()
                    Text(toast).font(.vt(13, .medium)).foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(Capsule().fill(Color.black.opacity(0.8)))
                        .padding(.bottom, 120)
                        .padding(.horizontal, 24)
                }
                .transition(.opacity)
                .allowsHitTesting(false)
            }
        }
        .fullScreenCover(item: $vm.cameraMode) { mode in
            CameraPicker(
                mode: mode,
                onPhoto: { vm.cameraTookPhoto($0, metadata: $1) },
                onVideo: { vm.cameraRecordedVideo($0) },
                onCancel: { vm.cameraMode = nil }
            )
            .ignoresSafeArea()
        }
        .onAppear { vm.start() }
        .onDisappear { vm.finish() }
        .onChange(of: scenePhase) { vm.isAppActive = $0 == .active }
    }
}

private struct CallHeader: View {
    let status: AgentCallStatus
    let isSpeaking: Bool
    let timer: String
    let cost: String

    var body: some View {
        HStack(spacing: 12) {
            AvatarImage(seed: AnanyaAgent.avatarSeed, placeholder: CallTones.surfaceRaised)
                .frame(width: 38, height: 38)
                .clipShape(Circle())
                .padding(4)
                .overlay(Circle().stroke(isSpeaking ? Color.vastuPrimary : .clear, lineWidth: 2))
                .animation(.easeInOut(duration: 0.25), value: isSpeaking)
            VStack(alignment: .leading, spacing: 2) {
                Text(AnanyaAgent.name).font(.vt(16, .semibold)).foregroundColor(CallTones.textPrimary)
                HStack(spacing: 6) {
                    StatusDot(status: status)
                    Text(status.label).font(.vt(12)).foregroundColor(CallTones.textMuted)
                }
            }
            Spacer()
            HStack(spacing: 8) {
                Text(timer).font(.system(size: 12, weight: .medium, design: .monospaced)).foregroundColor(CallTones.textPrimary)
                Circle().fill(CallTones.textMuted).frame(width: 3, height: 3)
                Text(cost).font(.vt(12, .medium)).foregroundColor(CallTones.textMuted)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(Capsule().fill(CallTones.surface))
            .overlay(Capsule().stroke(CallTones.hairline, lineWidth: 1))
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
    }
}

private struct StatusDot: View {
    let status: AgentCallStatus

    var body: some View {
        let pulses = status == .listening || status == .hearing || status == .thinking
        let period = status == .hearing ? 0.35 : 0.9
        TimelineView(.animation(paused: !pulses)) { timeline in
            let t = timeline.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: period * 2) / period
            let wave = t <= 1 ? t : 2 - t
            Circle()
                .fill(status.color)
                .frame(width: 7, height: 7)
                .opacity(pulses ? 0.35 + 0.65 * wave : 1)
        }
    }
}

private struct PageSwitch: View {
    let page: AgentCallPage
    let unread: Int
    let onSelect: (AgentCallPage) -> Void
    private let segment: CGFloat = 104

    var body: some View {
        ZStack(alignment: .leading) {
            Capsule().fill(CallTones.surfaceRaised)
                .frame(width: segment, height: 34)
                .offset(x: page == .board ? 0 : segment)
                .animation(.easeInOut(duration: 0.25), value: page)
            HStack(spacing: 0) {
                tab(.board, "Board")
                tab(.chat, "Chat")
            }
        }
        .padding(4)
        .background(Capsule().fill(CallTones.surface))
        .overlay(Capsule().stroke(CallTones.hairline, lineWidth: 1))
    }

    private func tab(_ p: AgentCallPage, _ title: String) -> some View {
        Button { onSelect(p) } label: {
            HStack(spacing: 6) {
                Text(title).font(.vt(13, p == page ? .semibold : .medium))
                    .foregroundColor(p == page ? CallTones.textPrimary : CallTones.textMuted)
                if p == .chat && unread > 0 {
                    Circle().fill(Color.vastuPrimary).frame(width: 18, height: 18)
                        .overlay(Text(unread > 9 ? "9+" : "\(unread)").font(.vt(10)).foregroundColor(.white))
                }
            }
            .frame(width: segment, height: 34)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct BoardPage: View {
    @ObservedObject var vm: AgentCallViewModel
    @ObservedObject var board: WhiteboardModel

    var body: some View {
        let latest = vm.messages.last { !$0.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        VStack(spacing: 0) {
            CallWhiteboard(board: board, penColor: vm.penColor)
                .padding(.horizontal, 16)

            // Live caption: the latest line, so the board page never needs the chat open.
            ZStack {
                if let line = latest {
                    VStack(spacing: 4) {
                        Text(line.sender == .agent ? AnanyaAgent.firstName.uppercased() : "YOU")
                            .font(.vt(10)).kerning(1.5)
                            .foregroundColor(line.sender == .agent ? .vastuSaffron : CallTones.textMuted)
                        Text(line.text).font(.vt(14)).foregroundColor(CallTones.textPrimary)
                            .lineLimit(3).multilineTextAlignment(.center).lineSpacing(3)
                    }
                    .id(line.id)
                    .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity),
                                            removal: .move(edge: .top).combined(with: .opacity)))
                }
            }
            .frame(maxWidth: .infinity, minHeight: 64)
            .padding(.horizontal, 28)
            .padding(.vertical, 12)
            .animation(.easeOut(duration: 0.3), value: latest?.id)

            BoardToolbar(vm: vm, board: board)
        }
    }
}

private struct BoardToolbar: View {
    @ObservedObject var vm: AgentCallViewModel
    @ObservedObject var board: WhiteboardModel

    var body: some View {
        let canUndo = !board.userStrokes.isEmpty
        let canClear = !board.isEmpty
        let canSend = !board.userStrokes.isEmpty
        HStack(spacing: 0) {
            // Colors take whatever width is left, so the buttons on the right never get squeezed.
            HStack(spacing: 2) {
                ForEach(whiteboardPalette, id: \.self) { color in
                    Button { vm.penColor = color } label: {
                        Circle().fill(color).frame(width: 18, height: 18)
                            .padding(5)
                            .overlay(Circle().stroke(color == vm.penColor ? Color.white : .clear, lineWidth: 2))
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
            }
            toolButton("arrow.uturn.backward", "Undo", enabled: canUndo) { board.undo() }
            toolButton("trash", "Clear board", enabled: canClear) { vm.clearBoard() }
            Button { vm.sendBoardToAgent() } label: {
                Text("Share").font(.vt(13, .semibold))
                    .foregroundColor(canSend ? .white : CallTones.textMuted)
                    .lineLimit(1)
                    .fixedSize()
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(Capsule().fill(canSend ? Color.vastuPrimary : CallTones.surfaceRaised))
            }
            .buttonStyle(.plain)
            .disabled(!canSend)
            .padding(.leading, 4)
        }
        .padding(.leading, 10)
        .padding(.trailing, 6)
        .padding(.vertical, 6)
        .background(Capsule().fill(CallTones.surface))
        .overlay(Capsule().stroke(CallTones.hairline, lineWidth: 1))
        .padding(.horizontal, 16)
    }

    private func toolButton(_ icon: String, _ label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 17))
                .foregroundColor(enabled ? CallTones.textPrimary : CallTones.textMuted.opacity(0.5))
                .frame(width: 36, height: 36)
        }
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

private struct CallControls: View {
    @ObservedObject var vm: AgentCallViewModel
    let onEndCall: () -> Void
    @State private var halo = false

    var body: some View {
        HStack(spacing: 28) {
            ZStack {
                if vm.isListening && !vm.isMuted {
                    Circle()
                        .fill(Color.onlineGreen.opacity(vm.isHearing ? 0.28 : 0.14))
                        .frame(width: 56, height: 56)
                        .scaleEffect(halo ? (vm.isHearing ? 1.35 : 1.15) : 1)
                        .animation(.easeInOut(duration: vm.isHearing ? 0.42 : 1.2).repeatForever(autoreverses: true), value: halo)
                        .onAppear { halo = true }
                        .onDisappear { halo = false }
                }
                Button { vm.toggleMic() } label: {
                    Circle().fill(vm.isMuted ? CallTones.textPrimary : CallTones.surfaceRaised).frame(width: 56, height: 56)
                        .overlay(Circle().stroke(CallTones.hairline, lineWidth: 1))
                        .overlay(Image(systemName: vm.isMuted ? "mic.slash.fill" : "mic.fill").font(.system(size: 21))
                            .foregroundColor(vm.isMuted ? CallTones.ink : CallTones.textPrimary))
                }
                .accessibilityLabel(vm.isMuted ? "Unmute" : "Mute")
            }
            .frame(width: 64, height: 64)

            Button(action: onEndCall) {
                Circle().fill(Color.danger).frame(width: 64, height: 64)
                    .overlay(Image(systemName: "phone.down.fill").font(.system(size: 24)).foregroundColor(.white))
            }
            .accessibilityLabel("End call")

            Menu {
                Button { vm.openCamera(.photo) } label: { Label("Take a photo", systemImage: "camera") }
                Button { vm.openCamera(.video) } label: { Label("Record a video (up to 30s)", systemImage: "video") }
            } label: {
                Circle().fill(CallTones.surfaceRaised).frame(width: 56, height: 56)
                    .overlay(Circle().stroke(CallTones.hairline, lineWidth: 1))
                    .overlay(Image(systemName: "camera").font(.system(size: 21)).foregroundColor(CallTones.textPrimary))
            }
            .accessibilityLabel("Share a photo or video for Vastu advice")
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 18)
        .padding(.bottom, 14)
    }
}
