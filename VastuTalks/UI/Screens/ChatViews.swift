import FirebaseFirestore
import SwiftUI

/// All of the signed-in user's chats, most recent first, with unread counts.
struct ChatsListView: View {
    let onBack: () -> Void
    let onOpenChat: (_ otherUid: String, _ otherName: String) -> Void

    @State private var chats: [Chat]?
    @State private var registration: ListenerRegistration?
    private let repository = ChatRepository()

    var body: some View {
        let myUid = repository.myUid ?? ""
        VStack(spacing: 0) {
            BackHeader(title: "Messages", titleSize: 18, onBack: onBack)
                .padding(.horizontal, 8).padding(.vertical, 8)
            if let list = chats {
                if list.isEmpty {
                    Spacer()
                    Text("No messages yet. Open an expert's profile and tap Chat to start a conversation.")
                        .font(.vt(14, .medium)).foregroundColor(.textSecondary).multilineTextAlignment(.center).padding(32)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(list) { chat in
                                let other = chat.otherUid(me: myUid)
                                let name = chat.names[other].flatMap { $0.isEmpty ? nil : $0 } ?? "Unknown"
                                Button { onOpenChat(other, name) } label: {
                                    ChatRow(chat: chat, myUid: myUid, otherUid: other, name: name)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 8)
                    }
                }
            } else {
                Spacer()
            }
        }
        .background(Color.surfaceLight.ignoresSafeArea())
        .onAppear {
            guard registration == nil else { return }
            registration = repository.listenMyChats { chats = $0 }
            if registration == nil { chats = [] }
        }
        .onDisappear {
            registration?.remove()
            registration = nil
        }
    }
}

private struct ChatRow: View {
    let chat: Chat
    let myUid: String
    let otherUid: String
    let name: String

    var body: some View {
        let unread = chat.unread[myUid] ?? 0
        HStack(spacing: 12) {
            ProfileAvatar(photo: chat.photos[otherUid] ?? "", name: name, size: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(name).font(.vt(15)).foregroundColor(.vastuCharcoal).lineLimit(1)
                Text((chat.lastSenderUid == myUid ? "You: " : "") + chat.lastMessage)
                    .font(.vt(13, unread > 0 ? .bold : .regular))
                    .foregroundColor(unread > 0 ? .vastuCharcoal : .textSecondary).lineLimit(1)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 4) {
                Text(timeAgo(chat.lastMessageAtMillis)).font(.vt(11, .regular)).foregroundColor(.textSecondary)
                if unread > 0 {
                    Text(unread > 9 ? "9+" : "\(unread)").font(.vt(10)).foregroundColor(.white)
                        .frame(width: 20, height: 20).background(Circle().fill(Color.vastuPrimary))
                }
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white))
        .contentShape(Rectangle())
    }
}

/// One chat between the signed-in user and `otherUid` (a user or an expert).
@MainActor
final class ConversationViewModel: ObservableObject {
    let otherUid: String
    let otherName: String
    let myUid: String
    let chatId: String

    @Published private(set) var messages: [ChatMessage] = []
    /// The other person's photo as saved in the chat (users' profiles are private).
    @Published private(set) var chatPhoto = ""
    /// Set if the other person is an expert (then calls are offered too).
    @Published private(set) var otherExpert: ExpertProfile?
    @Published var input = ""
    @Published var image: UIImage?
    @Published private(set) var sending = false
    @Published private(set) var error: String?

    private let chats = ChatRepository()
    private let users = UserRepository()
    private var me: UserProfile?
    private var registrations: [ListenerRegistration] = []
    private var started = false

    init(otherUid: String, otherName: String) {
        self.otherUid = otherUid
        self.otherName = otherName.isEmpty ? "Chat" : otherName
        myUid = chats.myUid ?? ""
        chatId = ChatRepository.chatId(myUid, otherUid)
    }

    var otherPhoto: String {
        if let photo = otherExpert?.photo, !photo.isEmpty { return photo }
        return chatPhoto
    }

    func start() {
        guard !started else { return }
        started = true
        registrations.append(chats.listenMessages(chatId: chatId) { [weak self] list in
            Task { @MainActor in self?.messages = list }
        })
        if let r = chats.listenMyChats({ [weak self] list in
            Task { @MainActor in
                guard let self = self else { return }
                self.chatPhoto = list.first { $0.id == self.chatId }?.photos[self.otherUid] ?? ""
            }
        }) { registrations.append(r) }
        if let r = users.listenMyProfile({ [weak self] profile in
            Task { @MainActor in self?.me = profile }
        }) { registrations.append(r) }
        Task { otherExpert = try? await users.getExpert(otherUid) }
    }

    func stop() {
        registrations.forEach { $0.remove() }
        registrations = []
        started = false
    }

    func markRead() {
        Task { await chats.markRead(chatId: chatId) }
    }

    var canSend: Bool { (!input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || image != nil) && !sending }

    func send() {
        guard canSend else { return }
        guard let profile = me else {
            error = "Still loading your profile — try again in a moment."
            return
        }
        sending = true
        error = nil
        let text = input
        let picture = image
        Task {
            do {
                try await chats.send(otherUid: otherUid, me: (profile.name, profile.photo),
                                     other: (otherName, otherExpert?.photo ?? ""), text: text, image: picture)
                input = ""
                image = nil
            } catch {
                self.error = "Couldn't send: \(error.localizedDescription)"
            }
            sending = false
        }
    }
}

struct ConversationView: View {
    let onBack: () -> Void
    /// Starts as audio; either side can turn the camera on during the call.
    let onCall: (_ expertUid: String, _ expertName: String) -> Void
    /// False when the chat is opened from inside a call (no call button then).
    var showCallButton = true

    @StateObject private var vm: ConversationViewModel
    @State private var source: MediaSource?
    @State private var toast: String?

    init(otherUid: String, otherName: String, onBack: @escaping () -> Void,
         onCall: @escaping (String, String) -> Void, showCallButton: Bool = true) {
        _vm = StateObject(wrappedValue: ConversationViewModel(otherUid: otherUid, otherName: otherName))
        self.onBack = onBack
        self.onCall = onCall
        self.showCallButton = showCallButton
    }

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "h:mm a"
        return f
    }()

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.vastuCharcoal).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                ProfileAvatar(photo: vm.otherPhoto, name: vm.otherName, size: 40)
                VStack(alignment: .leading, spacing: 1) {
                    Text(vm.otherName).font(.vt(16)).foregroundColor(.vastuCharcoal).lineLimit(1)
                    if let expert = vm.otherExpert {
                        Text(expert.displaySpecialty).font(.vt(12, .medium)).foregroundColor(.vastuPrimary)
                    }
                }
                Spacer()
                // Chatting with an expert: the other way to reach them.
                if showCallButton, let expert = vm.otherExpert {
                    Button { onCall(expert.uid, expert.name) } label: {
                        Image(systemName: "phone.fill").font(.system(size: 18)).foregroundColor(.vastuPrimary).frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Call")
                }
            }
            .padding(.horizontal, 4).padding(.vertical, 8)
            .background(Color.white)

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 8) {
                        if vm.messages.isEmpty {
                            Text("Say hello to \(vm.otherName.split(separator: " ").first.map(String.init) ?? vm.otherName) — you can also send photos of your space.")
                                .font(.vt(13, .medium)).foregroundColor(.textSecondary).multilineTextAlignment(.center)
                                .padding(.top, 40).padding(.horizontal, 24)
                        }
                        ForEach(vm.messages) { message in
                            MessageBubble(message: message, mine: message.senderUid == vm.myUid,
                                          time: Self.timeFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(message.createdAtMillis) / 1000)))
                                .id(message.id)
                        }
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: vm.messages.count) { _ in
                    vm.markRead()
                    if let last = vm.messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                if let picked = vm.image {
                    Image(uiImage: picked).resizable().scaledToFill()
                        .frame(width: 84, height: 84).clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(alignment: .topTrailing) {
                            Button { vm.image = nil } label: {
                                Circle().fill(Color.black.opacity(0.55)).frame(width: 22, height: 22)
                                    .overlay(Image(systemName: "xmark").font(.system(size: 10, weight: .bold)).foregroundColor(.white))
                            }
                            .padding(4)
                            .accessibilityLabel("Remove photo")
                        }
                        .padding(.leading, 4)
                }
                if let error = vm.error {
                    Text(error).font(.vt(12, .medium)).foregroundColor(.danger).padding(.leading, 4)
                }
                HStack(spacing: 8) {
                    Menu {
                        Button { MediaPermissions.openCamera(.camera, open: { source = $0 }, onError: { toast = $0 }) } label: {
                            Label("Take a photo", systemImage: "camera")
                        }
                        Button { source = .gallery } label: { Label("Choose from gallery", systemImage: "photo.on.rectangle") }
                    } label: {
                        Image(systemName: "photo.badge.plus").font(.system(size: 20)).foregroundColor(.vastuPrimary).frame(width: 40, height: 40)
                    }
                    .accessibilityLabel("Add a photo")
                    TextField("Message…", text: $vm.input, axis: .vertical)
                        .lineLimit(1...4)
                        .font(.vt(14, .regular))
                        .foregroundColor(.vastuCharcoal)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 22).fill(Color.inputFill))
                        .onChange(of: vm.input) { if $0.count > 2000 { vm.input = String($0.prefix(2000)) } }
                    Button { vm.send() } label: {
                        Circle().fill(vm.canSend ? Color.vastuPrimary : Color.vastuPrimary.opacity(0.35)).frame(width: 44, height: 44)
                            .overlay(Image(systemName: "paperplane.fill").font(.system(size: 16)).foregroundColor(.white))
                    }
                    .disabled(!vm.canSend)
                    .accessibilityLabel("Send")
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 8)
            .background(Color.white)
        }
        .background(Color.surfaceLight.ignoresSafeArea())
        .toast($toast)
        .mediaPicker($source, onPhoto: { vm.image = $0.image }, onError: { toast = $0 })
        // While this chat is open its messages are read, and don't also pop up as notifications.
        .onAppear {
            ChatRepository.openChatId = vm.chatId
            vm.start()
            vm.markRead()
        }
        .onDisappear {
            if ChatRepository.openChatId == vm.chatId { ChatRepository.openChatId = nil }
            vm.stop()
        }
    }
}

private struct MessageBubble: View {
    let message: ChatMessage
    let mine: Bool
    let time: String

    var body: some View {
        HStack {
            if mine { Spacer(minLength: 60) }
            VStack(alignment: .trailing, spacing: 0) {
                if let image = ProfilePhotos.decode(message.image) {
                    Image(uiImage: image).resizable().scaledToFit()
                        .frame(maxWidth: 260, maxHeight: 260)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .padding(4)
                        .accessibilityLabel("Photo")
                }
                VStack(alignment: .trailing, spacing: 2) {
                    if !message.text.isEmpty {
                        Text(message.text).font(.vt(14, .regular)).foregroundColor(mine ? .white : .vastuCharcoal)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Text(time).font(.vt(10, .regular)).foregroundColor(mine ? .white.opacity(0.75) : .textSecondary)
                }
                .padding(.horizontal, 12).padding(.vertical, 8)
            }
            .background(BubbleShape(tight: mine ? .topRight : .topLeft).fill(mine ? Color.vastuPrimary : .white))
            if !mine { Spacer(minLength: 60) }
        }
    }
}
