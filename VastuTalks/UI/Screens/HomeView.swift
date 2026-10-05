import FirebaseAuth
import FirebaseFirestore
import SwiftUI
import UserNotifications

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var role: UserRole? // nil while loading
    @Published var experts: [ExpertProfile] = []
    @Published var expertsError: String?
    @Published var incomingCall: CallRequest?

    private let userRepository = UserRepository()
    private let signaling = CallSignalingRepository()
    private var registration: ListenerRegistration?
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        Task {
            let role = (try? await userRepository.currentUserRole()) ?? .normalUser
            self.role = role
            if role == .normalUser {
                registration = userRepository.listenExperts { [weak self] result in
                    Task { @MainActor in
                        switch result {
                        case .success(let experts):
                            self?.experts = experts
                            self?.expertsError = nil
                        case .failure(let error):
                            self?.expertsError = error.localizedDescription
                        }
                    }
                }
            } else if let myUid = Auth.auth().currentUser?.uid {
                registration = signaling.listenForIncomingCalls(myUid: myUid) { [weak self] call in
                    Task { @MainActor in self?.incomingCall = call }
                }
                // Experts need notifications so their phone rings for calls.
                _ = try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
            }
        }
    }

    deinit { registration?.remove() }
}

struct HomeView: View {
    @StateObject private var viewModel = HomeViewModel()
    @ObservedObject private var router = AppRouter.shared

    var body: some View {
        VStack(spacing: 0) {
            // Top bar
            HStack {
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
                Button { /* Notifications screen isn't built yet — same as Android. */ } label: {
                    Image(systemName: "bell")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.vastuPrimary)
                        .frame(width: 44, height: 44)
                        .overlay(Circle().fill(Color.danger).frame(width: 9, height: 9).offset(x: 9, y: -9))
                }
                .accessibilityLabel("Notifications")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 16)

            switch viewModel.role {
            case .none:
                Spacer()
                ProgressView().tint(.vastuPrimary).scaleEffect(1.3)
                Spacer()
            case .vastuExpert:
                ExpertDashboard(incomingCall: viewModel.incomingCall)
            case .normalUser:
                NormalUserHome(experts: viewModel.experts, expertsError: viewModel.expertsError)
            }
        }
        .background(Color.surfaceLight.ignoresSafeArea())
        .onAppear { viewModel.start() }
    }
}

private struct ExpertDashboard: View {
    let incomingCall: CallRequest?

    @ObservedObject private var agora = AgoraCallManager.shared
    @State private var isAccepting = false
    @State private var acceptError: String?
    @State private var acceptedCall: CallRequest?

    var body: some View {
        VStack(spacing: 0) {
            if let call = incomingCall {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Incoming call").font(.vt(13)).foregroundColor(.textSecondary)
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
                .padding(.bottom, 20)
            }

            Spacer()
            VStack(spacing: 0) {
                Image(systemName: "headphones.circle")
                    .font(.system(size: 52))
                    .foregroundColor(.vastuPrimary.opacity(0.4))
                Spacer().frame(height: 16)
                Text("You're listed as a Vastu Expert").font(.vt(16)).foregroundColor(.vastuCharcoal)
                Spacer().frame(height: 6)
                Text("Normal users can find and call you. Keep the app open to receive calls in real time — incoming calls show up right here.")
                    .font(.vt(13))
                    .foregroundColor(.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }
            Spacer()
        }
        .padding(.horizontal, 20)
        .onChange(of: agora.connectionState) { state in
            guard state == .joined, let call = acceptedCall else { return }
            AppRouter.shared.push(.liveInCall(channelName: call.channelName, peerName: call.callerName, callType: call.type))
            acceptedCall = nil
            isAccepting = false
        }
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

private enum HomeTab { case experts, posts }

private struct NormalUserHome: View {
    let experts: [ExpertProfile]
    let expertsError: String?
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
            case .experts: ExpertsTab(experts: experts, expertsError: expertsError)
            case .posts: PostsFeed(expertNames: experts.map(\.name))
            }
        }
    }
}

private struct ExpertsTab: View {
    let experts: [ExpertProfile]
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
                        Text("\(experts.count) online now").font(.vt(13)).foregroundColor(.white.opacity(0.85))
                    }
                    Spacer()
                }
                .padding(18)
                .background(RoundedRectangle(cornerRadius: 20).fill(Gradients.primaryHorizontal))
                .padding(.horizontal, 20)

                Spacer().frame(height: 12)

                // Ananya, the AI Vastu agent — always available, no human expert needs to be online.
                Button { AppRouter.shared.push(.agentCalling) } label: {
                    HStack(spacing: 12) {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Color.vastuPrimary.opacity(0.1))
                            .frame(width: 36, height: 36)
                            .overlay(Image(systemName: "video.fill").font(.system(size: 15)).foregroundColor(.vastuPrimary))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Call Ananya — AI Vastu Expert").font(.vt(14)).foregroundColor(.vastuCharcoal)
                            Text("Talk, sketch or share photos and videos — available anytime").font(.vt(11)).foregroundColor(.textSecondary)
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 20)

                Spacer().frame(height: 20)

                LazyVStack(spacing: 14) {
                    if let error = expertsError {
                        Text("Couldn't load experts: \(error)").font(.vt(13)).foregroundColor(.danger)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 4)
                    } else if experts.isEmpty {
                        Text("No experts online right now — check back soon.").font(.vt(13)).foregroundColor(.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading).padding(.vertical, 4)
                    } else {
                        ForEach(experts) { expert in
                            Button { AppRouter.shared.push(.expertProfile(uid: expert.uid, name: expert.name)) } label: {
                                LiveExpertCard(expert: expert)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
            }
        }
    }
}

private struct LiveExpertCard: View {
    let expert: ExpertProfile

    var body: some View {
        let extras = ExpertDisplayExtras.forExpert(expert.uid)
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 14) {
                // Real photos aren't collected at signup yet — Pravatar serves
                // placeholder headshots, seeded per expert so the same one always shows.
                AvatarImage(seed: extras.avatarSeed(for: expert.uid))
                    .frame(width: 64, height: 64)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(alignment: .bottomTrailing) {
                        Circle().fill(Color.onlineGreen).frame(width: 12, height: 12)
                            .padding(2).background(Circle().fill(Color.white)).offset(x: 2, y: 2)
                    }

                VStack(alignment: .leading, spacing: 0) {
                    Text(expert.name).font(.vt(17)).foregroundColor(.vastuCharcoal).lineLimit(1)
                    Text(extras.specialty).font(.vt(13)).foregroundColor(.vastuPrimary)
                    Spacer().frame(height: 8)
                    HStack(spacing: 8) {
                        InfoPill(icon: "rosette", text: "\(extras.experienceYears) yrs")
                        InfoPill(icon: "mappin.circle.fill", text: extras.location, tint: .danger)
                    }
                }
                Spacer(minLength: 0)
                HStack(spacing: 3) {
                    Image(systemName: "star.fill").font(.system(size: 11))
                    Text(String(format: "%.1f", extras.rating)).font(.vt(13))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.walletGold))
            }

            Spacer().frame(height: 14)
            Rectangle().fill(Color.surfaceLight).frame(height: 1)
            Spacer().frame(height: 12)

            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text("\(extras.reviewCount) reviews").font(.vt(13)).foregroundColor(.textSecondary)
                Spacer()
                Text("₹\(extras.pricePerSession)").font(.vt(15)).foregroundColor(.vastuPrimary)
                Text("/session").font(.vt(12)).foregroundColor(.textSecondary)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color.white))
        .contentShape(Rectangle())
    }
}

private struct InfoPill: View {
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

// MARK: - Posts

private struct GeneratedPost: Identifiable {
    let id: Int
    let authorName: String
    let text: String
    let likeCount: Int
    let timeAgo: String
    let illustration: VastuIllustrationType
}

/// Small seeded generator, so authors/likes/times stay put while scrolling.
private struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        return z ^ (z >> 31)
    }
}

private struct PostsFeed: View {
    let expertNames: [String]

    private var posts: [GeneratedPost] {
        let pool = expertNames.isEmpty ? ["Vastu Talks Expert"] : expertNames
        var seed: UInt64 = 1469598103934665603
        for byte in expertNames.joined(separator: "|").utf8 { seed = (seed ^ UInt64(byte)) &* 1099511628211 }
        var random = SeededGenerator(seed: seed)
        let times = ["1h ago", "3h ago", "5h ago", "Yesterday", "2d ago", "3d ago", "1w ago"]
        return VastuPosts.texts.enumerated().map { index, text in
            GeneratedPost(
                id: index,
                authorName: pool[Int.random(in: 0..<pool.count, using: &random)],
                text: text,
                likeCount: Int.random(in: 4..<240, using: &random),
                timeAgo: times[Int.random(in: 0..<times.count, using: &random)],
                illustration: .forPost(text)
            )
        }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(posts) { PostCard(post: $0) }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
        }
    }
}

private struct PostCard: View {
    let post: GeneratedPost

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Circle()
                    .fill(Color.vastuPrimary.opacity(0.12))
                    .frame(width: 36, height: 36)
                    .overlay(Text(initials(post.authorName)).font(.vt(13)).foregroundColor(.vastuPrimary))
                VStack(alignment: .leading, spacing: 1) {
                    Text(post.authorName).font(.vt(13)).foregroundColor(.vastuCharcoal)
                    Text(post.timeAgo).font(.vt(11)).foregroundColor(.textSecondary)
                }
            }
            .padding(16)

            VastuIllustration(type: post.illustration)
                .frame(height: 150)
                .clipped()

            VStack(alignment: .leading, spacing: 10) {
                Text(post.text).font(.vt(13)).foregroundColor(.vastuCharcoal).lineSpacing(3)
                HStack(spacing: 3) {
                    Image(systemName: "heart.fill").font(.system(size: 12)).foregroundColor(.danger)
                    Text("\(post.likeCount)").font(.vt(12)).foregroundColor(.textSecondary)
                }
            }
            .padding(16)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
