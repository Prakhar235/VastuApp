import Combine
import SwiftUI

/// The text chat with Ananya: the same agent as the call (VastuAgent in
/// chat mode, so she writes fuller answers instead of speaking), in
/// English or Hindi (the menu in the header). A photo shared here — from
/// the camera or the gallery — comes back marked up with a numbered list
/// of what she found; a room scan (RoomScanView) arrives through
/// RoomScanHandoff with its marked photo and read.
///
/// The chat is saved on the phone (AgentChatStore) as it goes, and picked
/// up again next time — both the messages and what the model remembers —
/// until the user starts a new chat.
@MainActor
final class AgentChatViewModel: ObservableObject {
    @Published private(set) var messages: [AgentChatMessage] = []
    @Published private(set) var pendingTurns = 0
    @Published var input = ""
    /// False until the saved chat has been read, so nothing typed gets mixed into it.
    @Published private(set) var isLoaded = false
    @Published var photoToReview: CapturedPhoto?
    @Published var videoToReview: CapturedVideo?
    @Published var mediaSource: MediaSource?
    @Published var toast: String?

    private let store = AgentChatStore()
    private var agent = VastuAgent(mode: .chat)
    /// Bumped by startNewChat(), so a reply still on its way to the old chat is dropped.
    private var chatGeneration = 0
    private var savedMarkable: [UIImage] = []
    private var nextId = 0
    private var turnChain: Task<Void, Never>?
    private var saveChain: Task<Void, Never>?
    private var handoff: AnyCancellable?
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        Task {
            let store = self.store
            let saved = await Task.detached { store.load() }.value
            if let saved = saved, !saved.messages.isEmpty {
                messages = saved.messages
                nextId = (saved.messages.map(\.id).max() ?? 0) + 1
                await agent.restore(historyJson: saved.history, images: saved.markable)
                savedMarkable = saved.markable
                isLoaded = true
            } else {
                isLoaded = true
                ask { [agent] in await agent.greet() }
            }
            handoff = RoomScanHandoff.shared.$pending.compactMap { $0 }.receive(on: DispatchQueue.main).sink { [weak self] _ in
                if let scan = RoomScanHandoff.shared.take() { self?.addRoomScan(scan) }
            }
        }
    }

    /// Runs one step at a time, in order (agent turns and room scans).
    private func enqueue(_ step: @escaping @MainActor () async -> Void) {
        let previous = turnChain
        turnChain = Task {
            await previous?.value
            await step()
        }
    }

    /// Puts a finished room scan into the chat and into her memory of it.
    private func addRoomScan(_ scan: RoomScanHandoff.Scan) {
        let generation = chatGeneration
        enqueue { [self] in
            guard generation == chatGeneration, let first = scan.walls.first else { return }
            await agent.attachRoomScan(walls: scan.walls, analysis: scan.analysis)
            add(tr("I scanned my \(scan.analysis.room.lowercased()) (\(scan.walls.count) walls)",
                   "मैंने अपना \(scan.analysis.room) स्कैन किया (\(scan.walls.count) दीवारें)"),
                .user, image: first.image.scaledDown(toMaxSide: 480))
            let photo = scan.analysis.findings.first?.photo ?? 0
            store.saveReport(messageId: nextId, walls: scan.walls, analysis: scan.analysis)
            add(scan.analysis.asText(), .agent,
                image: scan.analysis.markedPhoto(walls: scan.walls, photo: photo)?.scaledDown(toMaxSide: 1024), hasReport: true)
        }
    }

    /// Clears the chat (on screen and on the phone) and has her greet afresh.
    func startNewChat() {
        chatGeneration += 1
        store.clear()
        messages = []
        nextId = 0
        savedMarkable = []
        agent = VastuAgent(mode: .chat)
        ask { [agent] in await agent.greet() }
    }

    private func add(_ text: String, _ sender: ChatSender, image: UIImage? = nil, hasReport: Bool = false) {
        messages.append(AgentChatMessage(id: nextId, text: text, sender: sender, image: image, hasReport: hasReport))
        nextId += 1
        save()
    }

    private func save() {
        let snapshot = messages
        let agent = self.agent
        let previous = saveChain
        saveChain = Task {
            await previous?.value
            let images = await agent.markable
            let history = await agent.exportHistory()
            let unchanged = images.count == savedMarkable.count && zip(images, savedMarkable).allSatisfy { $0 === $1 }
            if !unchanged {
                store.saveMarkable(images)
                savedMarkable = images
            }
            store.save(messages: snapshot, history: history, markableCount: images.count)
        }
    }

    /// Runs one agent turn at a time, posting her reply (and marked photo) to the chat.
    private func ask(_ request: @escaping () async -> AgentTurn) {
        let generation = chatGeneration
        pendingTurns += 1
        enqueue { [self] in
            if generation == chatGeneration {
                let turn = await request()
                if generation == chatGeneration { add(turn.chatText, .agent, image: turn.markedPhoto) }
            }
            pendingTurns -= 1
        }
    }

    func sendTyped() {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, isLoaded else { return }
        input = ""
        add(text, .user)
        ask { [agent] in await agent.reply(text) }
    }

    func sendPhoto(question: String, facing: PhotoFacing?) {
        guard let photo = photoToReview, isLoaded else { return }
        photoToReview = nil
        postMedia(photo.image, caption: (question.isEmpty ? defaultQuestion : question) +
                  (facing.map { tr(" · camera facing \($0.direction.label)", " · कैमरा \($0.direction.label) की ओर") } ?? ""))
        ask { [agent] in await agent.reviewPhoto(photo.image, question: question, facing: facing) }
    }

    func sendVideo(question: String, facing: PhotoFacing?) {
        guard let video = videoToReview, isLoaded else { return }
        videoToReview = nil
        try? FileManager.default.removeItem(at: video.file) // only the frames are needed from here on
        let seconds = max(1, Int(video.durationSeconds))
        postMedia(video.frames[0], caption: tr("Video (\(seconds)s): ", "वीडियो (\(seconds) सेकंड): ") +
                  (question.isEmpty ? defaultQuestion : question) +
                  (facing.map { tr(" · started facing \($0.direction.label)", " · शुरुआत \($0.direction.label) की ओर") } ?? ""))
        ask { [agent] in await agent.reviewVideo(frames: video.frames, durationSeconds: seconds, question: question, facing: facing) }
    }

    func discardVideo(retake: Bool) {
        if let file = videoToReview?.file { try? FileManager.default.removeItem(at: file) }
        videoToReview = nil
        if retake { openMedia(.video) }
    }

    func openMedia(_ source: MediaSource) {
        MediaPermissions.openCamera(source, open: { self.mediaSource = $0 }, onError: { self.toast = $0 })
    }

    private func postMedia(_ preview: UIImage, caption: String) {
        add(caption, .user, image: preview.scaledDown(toMaxSide: 480))
    }

    private var defaultQuestion: String {
        tr("What does Vastu say about this space?", "इस जगह के बारे में वास्तु क्या कहता है?")
    }
}

struct AgentChatView: View {
    let onBack: () -> Void
    /// Starts as audio; the camera can be turned on during the call.
    let onCall: () -> Void
    let onScanRoom: () -> Void
    /// Opens the full room report saved with that chat message.
    let onOpenReport: (_ messageId: Int) -> Void

    @StateObject private var vm = AgentChatViewModel()
    @ObservedObject private var language = AgentLanguagePref.shared
    @State private var confirmNewChat = false

    var body: some View {
        ZStack {
            CallTones.ink.ignoresSafeArea()
            VStack(spacing: 0) {
                header
                AgentChatPage(
                    messages: vm.messages,
                    isAgentTyping: vm.pendingTurns > 0,
                    inputText: $vm.input,
                    onSend: { vm.sendTyped() },
                    media: ShareMediaActions(
                        takePhoto: { vm.openMedia(.camera) },
                        pickPhoto: { vm.openMedia(.gallery) },
                        recordVideo: { vm.openMedia(.video) },
                        scanRoom: onScanRoom
                    ),
                    onOpenReport: { onOpenReport($0.id) },
                    emptyHint: tr("Ask a question, send a photo of any room, or scan a whole room — \(AnanyaAgent.firstName) marks her Vastu suggestions right on the photo.",
                                  "सवाल पूछें, किसी कमरे की फ़ोटो भेजें या पूरा कमरा स्कैन करें — \(AnanyaAgent.firstName) फ़ोटो पर ही निशान लगाकर वास्तु सुझाव देंगी।")
                )
            }

            if let photo = vm.photoToReview {
                CaptureReviewSheet(
                    frames: [photo.image], exifHeading: photo.exifHeading, isVideo: false, agentName: AnanyaAgent.firstName,
                    onRetake: { vm.photoToReview = nil; vm.openMedia(.camera) }, onDismiss: { vm.photoToReview = nil },
                    onSend: { vm.sendPhoto(question: $0, facing: $1) }
                )
            }
            if let video = vm.videoToReview {
                CaptureReviewSheet(
                    frames: video.frames, exifHeading: nil, isVideo: true, agentName: AnanyaAgent.firstName,
                    onRetake: { vm.discardVideo(retake: true) }, onDismiss: { vm.discardVideo(retake: false) },
                    onSend: { vm.sendVideo(question: $0, facing: $1) }
                )
            }
        }
        .toast($vm.toast)
        .mediaPicker($vm.mediaSource, onPhoto: { vm.photoToReview = $0 }, onVideo: { vm.videoToReview = $0 }, onError: { vm.toast = $0 })
        .alert(tr("Start a new chat?", "नई चैट शुरू करें?"), isPresented: $confirmNewChat) {
            Button(tr("Cancel", "रहने दें"), role: .cancel) {}
            Button(tr("New chat", "नई चैट")) { vm.startNewChat() }
        } message: {
            Text(tr("The old chat and \(AnanyaAgent.firstName)'s memory of it will both be cleared.",
                    "पुरानी चैट और \(AnanyaAgent.firstName) को उसकी याद, दोनों मिट जाएँगी।"))
        }
        .darkScreenStatusBar()
        .onAppear { vm.start() }
    }

    /// Back, Ananya, then room scan, call, and a menu with the reply
    /// language and "New chat". Kept to one line so it fits narrow phones.
    private var header: some View {
        HStack(spacing: 4) {
            Button(action: onBack) {
                Image(systemName: "chevron.left").font(.system(size: 18, weight: .semibold))
                    .foregroundColor(CallTones.textPrimary).frame(width: 44, height: 44)
            }
            .accessibilityLabel(tr("Back", "वापस"))
            AvatarImage(seed: AnanyaAgent.avatarSeed, placeholder: CallTones.surfaceRaised)
                .frame(width: 40, height: 40).clipShape(Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(AnanyaAgent.name).font(.vt(16, .semibold)).foregroundColor(CallTones.textPrimary).lineLimit(1)
                HStack(spacing: 6) {
                    Circle().fill(Color.onlineGreen).frame(width: 7, height: 7)
                    Text(vm.pendingTurns > 0 ? tr("Typing…", "लिख रही हैं…") : tr("AI Vastu expert", "AI वास्तु विशेषज्ञ"))
                        .font(.vt(12, .medium)).foregroundColor(CallTones.textMuted).lineLimit(1)
                }
            }
            .padding(.leading, 6)
            Spacer(minLength: 4)
            headerButton("safari", tr("Scan a room", "कमरा स्कैन करें"), action: onScanRoom)
            if AnanyaAgent.callEnabled {
                headerButton("phone.fill", tr("Call \(AnanyaAgent.name)", "\(AnanyaAgent.name) को कॉल करें"), action: onCall)
            }
            Menu {
                Section(tr("Reply language", "जवाब की भाषा")) {
                    ForEach([(AgentLanguage.english, "English"), (AgentLanguage.hindi, "हिंदी")], id: \.0) { option, label in
                        Button { language.set(option) } label: {
                            if language.current == option { Label(label, systemImage: "checkmark") } else { Text(label) }
                        }
                    }
                }
                Button { confirmNewChat = true } label: { Label(tr("New chat", "नई चैट"), systemImage: "square.and.pencil") }
            } label: {
                Image(systemName: "ellipsis").rotationEffect(.degrees(90)).font(.system(size: 18, weight: .semibold))
                    .foregroundColor(CallTones.textPrimary).frame(width: 40, height: 40)
            }
            .accessibilityLabel(tr("More options", "और विकल्प"))
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
    }

    private func headerButton(_ icon: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Circle().fill(CallTones.surfaceRaised).frame(width: 40, height: 40)
                .overlay(Image(systemName: icon).font(.system(size: 17)).foregroundColor(CallTones.textPrimary))
        }
        .padding(.horizontal, 2)
        .accessibilityLabel(label)
    }
}
