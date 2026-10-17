import FirebaseAuth
import FirebaseFirestore
import SwiftUI
import UserNotifications

@MainActor
final class HomeViewModel: ObservableObject {
    @Published private(set) var role: UserRole? // nil while loading
    @Published private(set) var experts: [ExpertProfile] = []
    @Published private(set) var expertsError: String?
    @Published private(set) var incomingCall: CallRequest?
    /// Expert accounts: their own directory entry (for the online/offline switch).
    @Published private(set) var myExpertProfile: ExpertProfile?
    /// A photo the expert just picked, shown straight away while it saves.
    @Published private(set) var pendingPhoto: UIImage?
    @Published private(set) var photoError: String?
    /// Normal users: every expert's posts, newest first. Experts: their own posts.
    @Published private(set) var posts: [Post] = []
    /// Ticks every 30 s, so "online" (which depends on how long ago an expert's
    /// app checked in) updates even when nothing in Firestore changes.
    @Published private(set) var nowMillis = millisNow()
    @Published private(set) var likedPostIds: Set<String> = []
    /// Unread notifications, for the badge on the bell.
    @Published private(set) var unreadNotifications: Int64 = 0
    /// Unread chat messages across all chats, for the badge on the messages icon.
    @Published private(set) var unreadMessages: Int64 = 0

    private let userRepository = UserRepository()
    private let signaling = CallSignalingRepository()
    private let postRepository = PostRepository()
    private var registrations: [ListenerRegistration] = []
    private var loops: [Task<Void, Never>] = []
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        Task {
            let role = (try? await userRepository.currentUserRole()) ?? .normalUser
            self.role = role
            // Everyone gets notifications: experts for calls, likes and comments;
            // users for chat messages and when an expert replies to their comment.
            // Not awaited, so the screen loads while the permission prompt is up.
            Task { _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) }

            // Both roles can like posts, chat and get notifications.
            add(postRepository.listenMyLikes { [weak self] ids in Task { @MainActor in self?.likedPostIds = ids } })
            let chats = ChatRepository()
            let me = chats.myUid ?? ""
            add(chats.listenMyChats { [weak self] list in
                Task { @MainActor in self?.unreadMessages = list.reduce(0) { $0 + ($1.unread[me] ?? 0) } }
            })
            add(NotificationRepository().listen { [weak self] list in
                Task { @MainActor in self?.unreadNotifications = Int64(list.filter { !$0.read }.count) }
            })

            if role == .normalUser {
                loops.append(Task { [weak self] in
                    while !Task.isCancelled {
                        try? await Task.sleep(nanoseconds: 30_000_000_000)
                        self?.nowMillis = millisNow()
                    }
                })
                add(postRepository.listenFeed { [weak self] posts in Task { @MainActor in self?.posts = posts } })
                add(userRepository.listenExperts { [weak self] result in
                    Task { @MainActor in
                        switch result {
                        case .success(let experts):
                            self?.experts = experts
                            self?.expertsError = nil
                        case .failure(let error):
                            self?.expertsError = error.localizedDescription
                        }
                    }
                })
            } else if let myUid = Auth.auth().currentUser?.uid {
                // Check in while the app is running, so users see this expert as online.
                loops.append(Task { [userRepository] in
                    while !Task.isCancelled {
                        try? await userRepository.heartbeat()
                        try? await Task.sleep(nanoseconds: ExpertProfile.heartbeatNanos)
                    }
                })
                add(postRepository.listenByAuthor(myUid) { [weak self] posts in Task { @MainActor in self?.posts = posts } })
                add(userRepository.listenExpert(myUid) { [weak self] me in Task { @MainActor in self?.myExpertProfile = me } })
                add(signaling.listenForIncomingCalls(myUid: myUid) { [weak self] call in
                    Task { @MainActor in self?.incomingCall = call }
                })
            }
        }
    }

    private func add(_ registration: ListenerRegistration?) {
        if let registration = registration { registrations.append(registration) }
    }

    deinit {
        registrations.forEach { $0.remove() }
        loops.forEach { $0.cancel() }
    }

    /// Expert changed their photo on Home: save it to their profile and directory entry.
    func updatePhoto(_ image: UIImage) {
        pendingPhoto = image
        photoError = nil
        Task {
            let encoded = await Task.detached { ProfilePhotos.encode(image) }.value
            do {
                try await userRepository.updatePhoto(encoded)
            } catch {
                photoError = "Couldn't save your photo: \(error.localizedDescription)"
            }
            pendingPhoto = nil
        }
    }

    /// Saves the expert's edited details; gives an error message, or nil on success.
    func updateDetails(city: String, experienceYears: Int, bio: String) async -> String? {
        do {
            try await userRepository.updateExpertDetails(city: city.trimmingCharacters(in: .whitespaces), experienceYears: experienceYears,
                                                         bio: bio.trimmingCharacters(in: .whitespacesAndNewlines))
            return nil
        } catch {
            return "Couldn't save: \(error.localizedDescription)"
        }
    }

    func toggleLike(_ postId: String) {
        let liked = likedPostIds.contains(postId)
        Task { try? await postRepository.setLiked(postId, !liked) }
    }

    func deletePost(_ postId: String) {
        Task { try? await postRepository.delete(postId) }
    }

    func setOnline(_ online: Bool) {
        Task { try? await userRepository.setOnline(online) }
    }
}

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    @ObservedObject private var router = AppRouter.shared

    var body: some View {
        VStack(spacing: 0) {
            // Top bar
            HStack(spacing: 0) {
                Button { router.push(.profile) } label: {
                    Image(systemName: "line.3.horizontal")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.vastuCharcoal)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Menu")
                Spacer()
                VStack(spacing: 2) {
                    Text("Vastu Experts").font(.vt(20)).foregroundColor(.vastuPrimary)
                    Text(viewModel.role == .vastuExpert ? "Your expert dashboard" : "Connect with certified consultants")
                        .font(.vt(12)).foregroundColor(.textSecondary)
                }
                Spacer()
                badgeButton("bubble.left.and.bubble.right", label: "Messages", count: viewModel.unreadMessages) { router.push(.chats) }
                badgeButton("bell", label: "Notifications", count: viewModel.unreadNotifications) { router.push(.notifications) }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 16)

            switch viewModel.role {
            case .none:
                Spacer()
                ProgressView().tint(.vastuPrimary).scaleEffect(1.3)
                Spacer()
            case .vastuExpert:
                ExpertDashboard(viewModel: viewModel)
            case .normalUser:
                NormalUserHome(viewModel: viewModel)
            }
        }
        .background(Color.surfaceLight.ignoresSafeArea())
        .onAppear { viewModel.start() }
    }

    private func badgeButton(_ icon: String, label: String, count: Int64, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 19, weight: .semibold))
                .foregroundColor(.vastuPrimary)
                .frame(width: 44, height: 44)
                .overlay(alignment: .topTrailing) {
                    if count > 0 { CountBadge(count: count).offset(x: -2, y: 4) }
                }
        }
        .accessibilityLabel(count > 0 ? "\(label), \(count) unread" : label)
    }
}

// MARK: - Expert dashboard

private struct ExpertDashboard: View {
    @ObservedObject var viewModel: HomeViewModel

    @ObservedObject private var agora = AgoraCallManager.shared
    @State private var isAccepting = false
    @State private var acceptError: String?
    @State private var acceptedCall: CallRequest?
    @State private var editingDetails = false
    @State private var visible = false

    var body: some View {
        let me = viewModel.myExpertProfile
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // The expert as users see them; tap the photo to change it.
                HStack(spacing: 16) {
                    ProfilePhotoEditor(photo: me?.photo ?? "", name: me?.name ?? "", size: 64,
                                       picked: viewModel.pendingPhoto, onPicked: { viewModel.updatePhoto($0) })
                    VStack(alignment: .leading, spacing: 2) {
                        Text(me?.name ?? "").font(.vt(17)).foregroundColor(.vastuCharcoal).lineLimit(1)
                        Text(me?.displaySpecialty ?? "").font(.vt(13)).foregroundColor(.vastuPrimary)
                        Text(viewModel.pendingPhoto != nil ? "Saving your photo…"
                             : viewModel.photoError ?? ((me?.photo ?? "").isEmpty ? "Tap the photo to add one — users see it when choosing an expert"
                                                       : "Tap the photo to change it"))
                            .font(.vt(12, .medium)).foregroundColor(viewModel.photoError != nil ? .danger : .textSecondary)
                    }
                    Spacer(minLength: 0)
                }
                .padding(16)
                .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))

                if let me = me {
                    detailsCard(me).padding(.top, 8)
                }

                // Availability switch: shows users whether the expert is around. Offline
                // experts can still be called — their phone rings through a push.
                let isOnline = me?.isOnline ?? true
                HStack(spacing: 12) {
                    Circle().fill(isOnline ? Color.onlineGreen : .textSecondary).frame(width: 10, height: 10)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(isOnline ? "You're online" : "You're offline").font(.vt(15)).foregroundColor(.vastuCharcoal)
                        Text(isOnline ? "Users can call you for audio and video consultations" : "Users can still call you — your phone will ring")
                            .font(.vt(12, .medium)).foregroundColor(.textSecondary)
                    }
                    Spacer()
                    Toggle("", isOn: Binding(get: { isOnline }, set: { viewModel.setOnline($0) }))
                        .labelsHidden().tint(.onlineGreen)
                }
                .padding(.horizontal, 18).padding(.vertical, 12)
                .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
                .padding(.top, 12)

                if let call = viewModel.incomingCall {
                    incomingCard(call).padding(.top, 16)
                }

                // The expert's posts, shown in every user's Posts tab.
                HStack {
                    Text("Your posts").font(.vt(16)).foregroundColor(.vastuCharcoal)
                    Spacer()
                    Button { AppRouter.shared.push(.createPost) } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "plus").font(.system(size: 13, weight: .bold))
                            Text("New post").font(.vt(13))
                        }
                        .foregroundColor(.white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 12).fill(Color.vastuPrimary))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 20)
                .padding(.bottom, 12)

                if viewModel.posts.isEmpty {
                    Text("Share Vastu tips and updates — your posts appear in every user's Posts tab.")
                        .font(.vt(13, .medium)).foregroundColor(.textSecondary).padding(.vertical, 8)
                } else {
                    LazyVStack(spacing: 12) {
                        ForEach(viewModel.posts) { post in
                            PostCard(post: post, liked: viewModel.likedPostIds.contains(post.id),
                                     onLike: { viewModel.toggleLike(post.id) },
                                     onOpen: { AppRouter.shared.push(.postDetail(postId: post.id)) },
                                     onDelete: { viewModel.deletePost(post.id) })
                        }
                    }
                }
                Spacer().frame(height: 20)
            }
            .padding(.horizontal, 20)
        }
        .sheet(isPresented: $editingDetails) {
            if let me = viewModel.myExpertProfile {
                EditExpertDetailsSheet(me: me) { await viewModel.updateDetails(city: $0, experienceYears: $1, bio: $2) }
            }
        }
        // While this screen is in front it shows and rings for incoming calls itself,
        // so the push banner doesn't ring as well (see IncomingCallAlerts).
        .onAppear {
            visible = true
            IncomingCallAlerts.inAppRingerVisible = true
            updateRinging()
        }
        .onDisappear {
            visible = false
            IncomingCallAlerts.inAppRingerVisible = false
            IncomingCallAlerts.stopRinging()
        }
        .onChange(of: viewModel.incomingCall?.callId) { _ in updateRinging() }
        .onChange(of: isAccepting) { _ in updateRinging() }
        .onChange(of: agora.connectionState) { state in
            guard state == .joined, let call = acceptedCall else { return }
            AppRouter.shared.push(.liveInCall(channelName: call.channelName, peerName: call.callerName, callType: call.type))
            acceptedCall = nil
            isAccepting = false
        }
    }

    /// One ringer: the card rings while it's waiting for an answer, and takes
    /// over from the push notification if that arrived first.
    private func updateRinging() {
        if visible, let call = viewModel.incomingCall, !isAccepting {
            IncomingCallAlerts.clearNotification(callId: call.callId)
            IncomingCallAlerts.startRinging()
        } else {
            IncomingCallAlerts.stopRinging()
        }
    }

    /// What users see on the expert's card and profile — city, experience and About — with Edit.
    private func detailsCard(_ me: ExpertProfile) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                InfoPill(icon: "rosette", text: me.experienceYears > 0 ? "\(me.experienceYears) yrs" : "Experience not set")
                InfoPill(icon: "mappin.circle.fill", text: me.city.isEmpty ? "City not set" : me.city, tint: .danger)
                Spacer()
                Button("Edit") { editingDetails = true }.font(.vt(14)).foregroundColor(.vastuPrimary)
            }
            Text(me.bio.isEmpty ? "Add a short introduction so users know how you can help." : me.bio)
                .font(.vt(13, .medium)).foregroundColor(.textSecondary).lineLimit(3).lineSpacing(3)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
    }

    private func incomingCard(_ call: CallRequest) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(call.type == .video ? "Incoming video call" : "Incoming audio call").font(.vt(13)).foregroundColor(.textSecondary)
            Text(call.callerName).font(.vt(18)).foregroundColor(.vastuCharcoal)
            if let error = acceptError {
                Text(error).font(.vt(12)).foregroundColor(.danger).padding(.top, 8)
            }
            Spacer().frame(height: 16)
            if isAccepting {
                ProgressView().tint(.vastuPrimary).frame(maxWidth: .infinity)
            } else {
                HStack(spacing: 12) {
                    pillButton("Decline", color: .danger) {
                        Task { try? await CallSignalingRepository().updateStatus(callId: call.callId, status: .declined) }
                    }
                    pillButton("Accept", color: .success) { accept(call) }
                }
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
    }

    private func accept(_ call: CallRequest) {
        isAccepting = true
        acceptError = nil
        Task {
            do {
                try await CallSignalingRepository().updateStatus(callId: call.callId, status: .accepted)
            } catch {
                acceptError = error.localizedDescription
                isAccepting = false
                return
            }
            await CallPermissions.request(video: call.type == .video)
            do {
                let token = try await TokenRepository().fetchOrCreateExpertToken(expertUid: call.calleeUid, channelName: call.channelName)
                acceptedCall = call
                AgoraCallManager.shared.joinChannel(channelName: call.channelName, token: token, isVideoCall: call.type == .video)
            } catch {
                acceptError = error.localizedDescription
                isAccepting = false
            }
        }
    }

    private func pillButton(_ title: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.vt(16)).foregroundColor(.white)
                .frame(maxWidth: .infinity).frame(height: 48)
                .background(Capsule().fill(color))
        }
        .buttonStyle(.plain)
    }
}

private struct EditExpertDetailsSheet: View {
    let me: ExpertProfile
    let onSave: (_ city: String, _ experienceYears: Int, _ bio: String) async -> String?

    @Environment(\.dismiss) private var dismiss
    @State private var city = ""
    @State private var experience = ""
    @State private var bio = ""
    @State private var saving = false
    @State private var error: String?
    @State private var loaded = false

    private var canSave: Bool {
        !city.trimmingCharacters(in: .whitespaces).isEmpty && Int(experience) != nil &&
            !bio.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !saving
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    label("City")
                    VastuTextField(text: $city, placeholder: "e.g. Mumbai", icon: "mappin.circle.fill", contentType: .addressCity)
                    label("Years of Experience", top: 14)
                    VastuTextField(text: $experience, placeholder: "e.g. 10", icon: "rosette", keyboard: .numberPad)
                        .onChange(of: experience) { experience = String($0.filter(\.isNumber).prefix(2)) }
                    label("About You", top: 14)
                    MultilineField(text: $bio, placeholder: "Your experience, approach and what you help clients with", maxLength: 400)
                    if let error = error {
                        Text(error).font(.vt(12, .medium)).foregroundColor(.danger).padding(.top, 10)
                    }
                }
                .padding(20)
            }
            .navigationTitle("Edit your details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(saving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "Saving…" : "Save") {
                        saving = true
                        error = nil
                        Task {
                            let failure = await onSave(city, Int(experience) ?? 0, bio)
                            saving = false
                            if let failure = failure { error = failure } else { dismiss() }
                        }
                    }
                    .disabled(!canSave)
                }
            }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            city = me.city
            experience = me.experienceYears > 0 ? "\(me.experienceYears)" : ""
            bio = me.bio
        }
    }

    private func label(_ text: String, top: CGFloat = 0) -> some View {
        Text(text).font(.vt(13)).foregroundColor(.vastuCharcoal).padding(.top, top).padding(.bottom, 6)
    }
}

// MARK: - Normal user

private enum HomeTab { case experts, posts }

private struct NormalUserHome: View {
    @ObservedObject var viewModel: HomeViewModel
    @State private var tab = HomeTab.experts

    var body: some View {
        VStack(spacing: 0) {
            // Search bar (no search screen yet — same as Android)
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").font(.system(size: 17)).foregroundColor(.textSecondary)
                Text("Search by name or specialty").font(.vt(14)).foregroundColor(.textSecondary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .frame(height: 48)
            .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
            .padding(.horizontal, 20)

            Spacer().frame(height: 16)

            SegmentTabs(
                options: [(HomeTab.experts, "Experts", nil), (HomeTab.posts, "Posts", nil)],
                selection: $tab,
                height: 40, cornerRadius: 20, innerRadius: 16, fontSize: 13, unselectedColor: .textSecondary
            )
            .padding(.horizontal, 20)

            Spacer().frame(height: 16)

            switch tab {
            case .experts:
                ExpertsTab(experts: viewModel.experts.sortedForDirectory(now: viewModel.nowMillis),
                           now: viewModel.nowMillis, expertsError: viewModel.expertsError)
            case .posts:
                PostsFeed(viewModel: viewModel)
            }
        }
    }
}

private struct ExpertsTab: View {
    let experts: [ExpertProfile]
    let now: Int64
    let expertsError: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                HStack(spacing: 14) {
                    RoundedRectangle(cornerRadius: 14)
                        .fill(Color.white.opacity(0.2))
                        .frame(width: 44, height: 44)
                        .overlay(Image(systemName: "sparkles").font(.system(size: 20, weight: .bold)).foregroundColor(.white))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Top Rated Consultants").font(.vt(16)).foregroundColor(.white)
                        Text("\(experts.filter { $0.isPresent(at: now) }.count) online now").font(.vt(13)).foregroundColor(.white.opacity(0.85))
                    }
                    Spacer()
                }
                .padding(18)
                .background(RoundedRectangle(cornerRadius: 20).fill(Gradients.primaryHorizontal))
                .padding(.horizontal, 20)

                Spacer().frame(height: 12)

                // Ananya, the AI Vastu agent — always available, no human expert needs
                // to be online. Chat or call her; the room scan lives in her chat.
                AnanyaCard()
                    .padding(.horizontal, 20)

                Spacer().frame(height: 20)

                LazyVStack(spacing: 14) {
                    if let error = expertsError {
                        Text("Couldn't load experts: \(error)").font(.vt(13)).foregroundColor(.danger)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 4)
                    } else if experts.isEmpty {
                        Text("No experts have joined yet — check back soon.").font(.vt(13)).foregroundColor(.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 4)
                    } else {
                        ForEach(experts) { expert in
                            LiveExpertCard(expert: expert, now: now)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
    }
}

/// Ananya, the AI Vastu expert, with her two ways in: chat and call.
private struct AnanyaCard: View {
    var body: some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.vastuPrimary.opacity(0.1))
                    .frame(width: 36, height: 36)
                    .overlay(Image(systemName: "sparkles").font(.system(size: 16)).foregroundColor(.vastuPrimary))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Ananya — AI Vastu expert").font(.vt(14)).foregroundColor(.vastuCharcoal)
                    Text("Available anytime · English or Hindi · share room photos").font(.vt(11)).foregroundColor(.textSecondary)
                }
                Spacer()
            }
            HStack(spacing: 8) {
                CallChip(icon: "bubble.left.and.bubble.right.fill", label: "Chat") { AppRouter.shared.push(.agentChat) }
                if AnanyaAgent.callEnabled {
                    CallChip(icon: "phone.fill", label: "Call") { AppRouter.shared.push(.agentCalling(video: false)) }
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
    }
}

func expertStatusLabel(_ expert: ExpertProfile, now: Int64) -> String {
    if !expert.isPresent(at: now) { return "Offline" }
    if expert.isBusy(at: now) { return "On another call" }
    return "Online now"
}

func expertStatusColor(_ expert: ExpertProfile, now: Int64) -> Color {
    if !expert.isPresent(at: now) { return .textSecondary }
    if expert.isBusy(at: now) { return .walletGold }
    return .onlineGreen
}

private struct LiveExpertCard: View {
    let expert: ExpertProfile
    let now: Int64

    var body: some View {
        let router = AppRouter.shared
        VStack(alignment: .leading, spacing: 0) {
            Button { router.push(.expertProfile(uid: expert.uid, name: expert.name)) } label: {
                HStack(alignment: .top, spacing: 14) {
                    ProfileAvatar(photo: expert.photo, name: expert.name, size: 64, cornerRadius: 16)
                        .overlay(alignment: .bottomTrailing) {
                            Circle().fill(expertStatusColor(expert, now: now)).frame(width: 12, height: 12)
                                .padding(2).background(Circle().fill(Color.white)).offset(x: 2, y: 2)
                        }
                    VStack(alignment: .leading, spacing: 0) {
                        Text(expert.name).font(.vt(17)).foregroundColor(.vastuCharcoal).lineLimit(1)
                        Text(expert.displaySpecialty).font(.vt(13)).foregroundColor(.vastuPrimary)
                        if expert.experienceYears > 0 || !expert.city.isEmpty {
                            HStack(spacing: 8) {
                                if expert.experienceYears > 0 { InfoPill(icon: "rosette", text: "\(expert.experienceYears) yrs") }
                                if !expert.city.isEmpty { InfoPill(icon: "mappin.circle.fill", text: expert.city, tint: .danger) }
                            }
                            .padding(.top, 8)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Rectangle().fill(Color.surfaceLight).frame(height: 1).padding(.vertical, 12)

            HStack(spacing: 0) {
                Text(expertStatusLabel(expert, now: now)).font(.vt(13)).foregroundColor(expertStatusColor(expert, now: now))
                Text("  ·  " + (CallPricing.paidCalls ? "₹\(ExpertDisplayExtras.forExpert(expert.uid).pricePerSession)/session"
                                : "\(expert.consultationsLabel) · Free"))
                    .font(.vt(12, .medium)).foregroundColor(.textSecondary).lineLimit(1)
            }
            // Chat or call. Both work while they're offline (a call rings their phone
            // through a push); only a call they're already on blocks a new one. A call
            // starts as audio; either side can turn the camera on.
            HStack(spacing: 8) {
                CallChip(icon: "bubble.left.and.bubble.right.fill", label: "Chat") {
                    router.push(.conversation(otherUid: expert.uid, otherName: expert.name))
                }
                CallChip(icon: "phone.fill", label: "Call", enabled: !expert.isBusy(at: now)) {
                    router.push(.liveCalling(calleeUid: expert.uid, calleeName: expert.name, callType: .audio))
                }
            }
            .padding(.top, 10)
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color.white))
    }
}

/// Every expert's posts, newest first; tapping an author opens their profile.
private struct PostsFeed: View {
    @ObservedObject var viewModel: HomeViewModel

    var body: some View {
        if viewModel.posts.isEmpty {
            Spacer()
            Text("No posts yet. Tips and updates from Vastu experts will show up here.")
                .font(.vt(14, .medium)).foregroundColor(.textSecondary).multilineTextAlignment(.center).padding(32)
            Spacer()
        } else {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(viewModel.posts) { post in
                        PostCard(post: post, liked: viewModel.likedPostIds.contains(post.id),
                                 onLike: { viewModel.toggleLike(post.id) },
                                 onOpen: { AppRouter.shared.push(.postDetail(postId: post.id)) },
                                 onAuthorTap: { AppRouter.shared.push(.expertProfile(uid: post.authorUid, name: post.authorName)) })
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
    }
}

/// "Chat" / "Call" button on an expert card; greyed out when they can't be reached that way.
private struct CallChip: View {
    let icon: String
    let label: String
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                Image(systemName: icon).font(.system(size: 13))
                Text(label).font(.vt(13))
            }
            .foregroundColor(enabled ? .white : .textSecondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 9)
            .background(RoundedRectangle(cornerRadius: 12).fill(enabled ? Color.vastuPrimary : Color.surfaceLight))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

struct InfoPill: View {
    let icon: String
    let text: String
    var tint: Color = .vastuPrimary

    var body: some View {
        HStack(spacing: 3) {
            Image(systemName: icon).font(.system(size: 10)).foregroundColor(tint)
            Text(text).font(.vt(11)).foregroundColor(.textSecondary)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color.surfaceLight))
    }
}

/// A taller text box for longer writing (an expert's About), in the app's field style.
struct MultilineField: View {
    @Binding var text: String
    let placeholder: String
    var maxLength = 400

    var body: some View {
        ZStack(alignment: .topLeading) {
            if text.isEmpty {
                Text(placeholder).font(.vt(15, .regular)).foregroundColor(.textHint).padding(.horizontal, 16).padding(.vertical, 14)
            }
            TextField("", text: $text, axis: .vertical)
                .lineLimit(3...)
                .font(.vt(15, .regular)).foregroundColor(.vastuCharcoal)
                .padding(.horizontal, 16).padding(.vertical, 14)
                .onChange(of: text) { if $0.count > maxLength { text = String($0.prefix(maxLength)) } }
        }
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.inputFill))
    }
}
