import FirebaseAuth
import FirebaseFirestore
import SwiftUI

@MainActor
final class ProfileViewModel: ObservableObject {
    @Published var isLoading = true
    @Published var history: [CallHistoryEntry] = []
    @Published var errorMessage: String?
    /// users/{uid}: phone and photo (name/email come from Firebase Auth).
    @Published private(set) var profile: UserProfile?
    /// A photo just picked, shown while it saves.
    @Published private(set) var pendingPhoto: UIImage?

    private var registration: ListenerRegistration?

    /// The Firebase "credential data" the profile shows — name and email.
    var currentUser: FirebaseAuth.User? { Auth.auth().currentUser }

    func start() {
        loadHistory()
        if registration == nil {
            registration = UserRepository().listenMyProfile { [weak self] profile in
                Task { @MainActor in self?.profile = profile }
            }
        }
    }

    deinit { registration?.remove() }

    func loadHistory() {
        isLoading = true
        errorMessage = nil
        Task {
            do {
                history = try await CallHistoryRepository().fetchHistory()
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    func updatePhoto(_ image: UIImage) {
        pendingPhoto = image
        Task {
            let encoded = await Task.detached { ProfilePhotos.encode(image) }.value
            do {
                try await UserRepository().updatePhoto(encoded)
            } catch {
                errorMessage = "Couldn't save your photo: \(error.localizedDescription)"
            }
            pendingPhoto = nil
        }
    }
}

private enum ProfileTab { case profile, history }

struct ProfileView: View {
    let onBack: () -> Void
    let onSignOut: () -> Void

    @StateObject private var viewModel = ProfileViewModel()
    @State private var tab = ProfileTab.profile

    var body: some View {
        let user = viewModel.currentUser
        let displayName = (user?.displayName).flatMap { $0.isEmpty ? nil : $0 } ?? "Vastu Talks user"
        let email = user?.email ?? "No email on file"

        VStack(spacing: 0) {
            BackHeader(title: "My Profile", titleSize: 18, onBack: onBack)
                .padding(.horizontal, 8)
                .padding(.vertical, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Firebase credential data — name, email, avatar.
                    HStack(spacing: 14) {
                        ProfilePhotoEditor(photo: viewModel.profile?.photo ?? "", name: displayName, size: 64,
                                           picked: viewModel.pendingPhoto, cornerRadius: 18) { viewModel.updatePhoto($0) }
                        VStack(alignment: .leading, spacing: 1) {
                            Text(displayName).font(.vt(19)).foregroundColor(.vastuCharcoal)
                            Text(email).font(.vt(13)).foregroundColor(.textSecondary)
                            if let phone = viewModel.profile?.phone, !phone.isEmpty {
                                Text(phone).font(.vt(13)).foregroundColor(.textSecondary)
                            }
                            Text(viewModel.pendingPhoto != nil ? "Saving photo…" : "Tap the photo to change it")
                                .font(.vt(12)).foregroundColor(.vastuPrimary)
                        }
                    }

                    // Available Credits — simulated wallet; no real credits/ledger
                    // backend yet, and hidden while calls are free.
                    if CallPricing.paidCalls {
                        WalletCard()
                    }

                    SegmentTabs(
                        options: [(ProfileTab.profile, "Profile", nil), (ProfileTab.history, "History", nil)],
                        selection: $tab,
                        height: 48, cornerRadius: 24, innerRadius: 20
                    )

                    if tab == .profile {
                        sectionHeader("ACCOUNT")
                        settingsGroup {
                            SettingsRow(icon: "person.fill", label: "Edit Profile")
                            divider
                            SettingsRow(icon: "bell.fill", label: "Notifications", badgeCount: 3)
                            divider
                            SettingsRow(icon: "gearshape.fill", label: "Settings")
                        }
                        sectionHeader("PAYMENT & BILLING")
                        settingsGroup {
                            SettingsRow(icon: "creditcard.fill", label: "Payment Methods")
                            divider
                            SettingsRow(icon: "clock.arrow.circlepath", label: "Transaction History")
                        }
                        Button(action: onSignOut) {
                            HStack(spacing: 14) {
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(Color.danger.opacity(0.1))
                                    .frame(width: 36, height: 36)
                                    .overlay(Image(systemName: "rectangle.portrait.and.arrow.right").font(.system(size: 16)).foregroundColor(.danger))
                                Text("Log Out").font(.vt(15)).foregroundColor(.danger)
                                Spacer()
                            }
                            .padding(.horizontal, 18)
                            .padding(.vertical, 16)
                            .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
                        }
                        .buttonStyle(.plain)
                    } else {
                        Text("Call history").font(.vt(16)).foregroundColor(.vastuCharcoal)
                        if viewModel.isLoading {
                            ProgressView().tint(.vastuPrimary).frame(maxWidth: .infinity).padding(.vertical, 32)
                        } else if let error = viewModel.errorMessage {
                            Text(error).font(.vt(13)).foregroundColor(.danger).padding(.vertical, 16)
                        } else if viewModel.history.isEmpty {
                            Text("No calls yet — your consultations will show up here once you finish one.")
                                .font(.vt(13)).foregroundColor(.textSecondary).padding(.vertical, 16)
                        } else {
                            ForEach(viewModel.history) { CallHistoryRow(entry: $0) }
                        }
                    }
                    Spacer().frame(height: 24)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
            }
        }
        .background(Color.surfaceLight.ignoresSafeArea())
        .onAppear { viewModel.start() }
    }

    private var divider: some View { Rectangle().fill(Color.surfaceLight).frame(height: 1) }

    private func sectionHeader(_ text: String) -> some View {
        Text(text).font(.vt(12)).kerning(1).foregroundColor(.textSecondary)
    }

    private func settingsGroup<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(spacing: 0, content: content)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.white))
    }
}

/// Simulated wallet (only while CallPricing.paidCalls is on).
private struct WalletCard: View {
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    Image(systemName: "wallet.pass.fill").font(.system(size: 14))
                    Text("Available Credits").font(.vt(13))
                }
                .foregroundColor(.white)
                Spacer().frame(height: 6)
                Text("₹2,450").font(.vt(30)).foregroundColor(.white)
                Spacer().frame(height: 4)
                Text("Lifetime earned: ₹5,000").font(.vt(12)).foregroundColor(.white.opacity(0.85))
            }
            Spacer()
            HStack(spacing: 4) {
                Image(systemName: "plus").font(.system(size: 14, weight: .bold))
                Text("Add").font(.vt(14))
            }
            .foregroundColor(.white)
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
            .background(Capsule().fill(Color.vastuCharcoal))
        }
        .padding(20)
        .background(RoundedRectangle(cornerRadius: 20).fill(
            LinearGradient(colors: [.walletGold, .walletGoldDark], startPoint: .leading, endPoint: .trailing)))
    }
}

private struct SettingsRow: View {
    let icon: String
    let label: String
    var badgeCount: Int?

    var body: some View {
        // Not wired yet — these all need dedicated screens (same as Android).
        HStack(spacing: 0) {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.vastuPrimary.opacity(0.1))
                .frame(width: 36, height: 36)
                .overlay(Image(systemName: icon).font(.system(size: 16)).foregroundColor(.vastuPrimary))
            Text(label).font(.vt(15)).foregroundColor(.vastuCharcoal).padding(.leading, 14)
            Spacer()
            if let badge = badgeCount {
                Text("\(badge)").font(.vt(11)).foregroundColor(.white)
                    .padding(.horizontal, 7).padding(.vertical, 2)
                    .background(Capsule().fill(Color.danger))
                    .padding(.trailing, 8)
            }
            Image(systemName: "chevron.right").font(.system(size: 14, weight: .semibold)).foregroundColor(.textSecondary)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
    }
}

private struct CallHistoryRow: View {
    let entry: CallHistoryEntry

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy · h:mm a"
        return f
    }()

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(Color.vastuPrimary.opacity(0.1))
                .frame(width: 40, height: 40)
                .overlay(Image(systemName: entry.callType == CallType.video.rawValue ? "video.fill" : "phone.arrow.up.right.fill")
                    .font(.system(size: 15)).foregroundColor(.vastuPrimary))
            VStack(alignment: .leading, spacing: 1) {
                Text(entry.expertName).font(.vt(14)).foregroundColor(.vastuCharcoal)
                Text(Self.formatter.string(from: Date(timeIntervalSince1970: TimeInterval(entry.startTimeMillis) / 1000)))
                    .font(.vt(12)).foregroundColor(.textSecondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 1) {
                Text(String(format: "%d:%02d", entry.durationSeconds / 60, entry.durationSeconds % 60))
                    .font(.vt(13)).foregroundColor(.vastuCharcoal)
                if CallPricing.paidCalls {
                    Text("₹\(entry.cost)").font(.vt(12)).foregroundColor(.textSecondary)
                }
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
    }
}

struct RealExpertProfileView: View {
    let expertUid: String
    let expertName: String
    let onBack: () -> Void
    /// Starts as audio; either side can turn the camera on during the call.
    let onCall: () -> Void
    let onChat: () -> Void

    // Live, so the buttons and status follow the expert going online/offline or onto a call.
    @State private var expert: ExpertProfile?
    @State private var registration: ListenerRegistration?
    // Re-checked every 30 s: "online" depends on how long ago their app checked in.
    @State private var now = millisNow()
    private let clock = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    var body: some View {
        // Offline experts can be called too (their phone rings through a push); just not mid-call.
        let canCall = expert.map { !$0.isBusy(at: now) } ?? false
        VStack(spacing: 0) {
            BackHeader(onBack: onBack).padding(.horizontal, 8).padding(.vertical, 8)

            ScrollView {
                VStack(spacing: 16) {
                    // Profile card
                    VStack(spacing: 0) {
                        ProfileAvatar(photo: expert?.photo ?? "", name: expertName, size: 112)
                            .overlay(alignment: .bottomTrailing) {
                                Circle().fill(expert.map { expertStatusColor($0, now: now) } ?? .textSecondary).frame(width: 18, height: 18)
                                    .padding(3).background(Circle().fill(Color.white))
                            }
                        Spacer().frame(height: 16)
                        Text(expertName).font(.vt(24)).foregroundColor(.vastuCharcoal).multilineTextAlignment(.center)
                        Text(expert?.displaySpecialty ?? "").font(.vt(15)).foregroundColor(.vastuPrimary)
                        if let expert = expert {
                            Text(expertStatusLabel(expert, now: now)).font(.vt(13)).foregroundColor(expertStatusColor(expert, now: now))
                                .padding(.top, 4)
                        }
                        Spacer().frame(height: 10)
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill").font(.system(size: 13)).foregroundColor(.walletGold)
                            Text(expert?.consultationsLabel ?? "New").font(.vt(13)).foregroundColor(.vastuCharcoal)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.walletGold.opacity(0.18)))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(24)
                    .background(RoundedRectangle(cornerRadius: 24).fill(Color.white))

                    HStack(spacing: 12) {
                        StatTile(icon: "rosette", label: "Experience", value: (expert?.experienceYears ?? 0) > 0 ? "\(expert!.experienceYears) yrs" : "—")
                        StatTile(icon: "mappin.circle.fill", label: "Location", value: (expert?.city).flatMap { $0.isEmpty ? nil : $0 } ?? "—")
                        if CallPricing.paidCalls {
                            StatTile(icon: "sparkles", label: "Rate", value: "₹\(ExpertDisplayExtras.forExpert(expertUid).pricePerSession)")
                        } else {
                            StatTile(icon: "sparkles", label: "Consultations", value: "\(expert?.consultationsCompleted ?? 0)")
                        }
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 8) {
                            Image(systemName: "sparkles").font(.system(size: 16)).foregroundColor(.walletGold)
                            Text("About Expert").font(.vt(16)).foregroundColor(.vastuCharcoal)
                        }
                        Text(expert?.displayBio ?? "").font(.vt(14)).foregroundColor(.textSecondary).lineSpacing(4)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(RoundedRectangle(cornerRadius: 20).fill(Color.white))
                }
                .padding(.horizontal, 20)
            }

            // Chat and calls both work while the expert is offline.
            HStack(spacing: 12) {
                actionButton(icon: "bubble.left.and.bubble.right.fill", title: "Chat", filled: true, enabled: true, action: onChat)
                actionButton(icon: "phone.fill", title: "Call", filled: false, enabled: canCall, action: onCall)
            }
            .padding(20)
        }
        .background(Color.surfaceLight.ignoresSafeArea())
        .onReceive(clock) { _ in now = millisNow() }
        .onAppear {
            guard registration == nil else { return }
            registration = UserRepository().listenExpert(expertUid) { expert = $0 }
        }
        .onDisappear {
            registration?.remove()
            registration = nil
        }
    }

    private func actionButton(icon: String, title: String, filled: Bool, enabled: Bool, action: @escaping () -> Void) -> some View {
        let content: Color = !enabled ? .textSecondary : filled ? .white : .vastuPrimary
        return Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 16))
                Text(title).font(.vt(15))
            }
            .foregroundColor(content)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(RoundedRectangle(cornerRadius: 16).fill(!enabled ? Color(hex: 0xE6E4EC) : filled ? Color.vastuPrimary : .white))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

private struct StatTile: View {
    let icon: String
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 0) {
            Image(systemName: icon).font(.system(size: 18)).foregroundColor(.textSecondary)
            Spacer().frame(height: 6)
            Text(label).font(.vt(12)).foregroundColor(.textSecondary).lineLimit(1).minimumScaleFactor(0.8)
            Text(value).font(.vt(15)).foregroundColor(.vastuCharcoal).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
    }
}
