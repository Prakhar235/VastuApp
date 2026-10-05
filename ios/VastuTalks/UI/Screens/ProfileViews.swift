import FirebaseAuth
import SwiftUI

@MainActor
final class ProfileViewModel: ObservableObject {
    @Published var isLoading = true
    @Published var history: [CallHistoryEntry] = []
    @Published var errorMessage: String?

    /// The Firebase "credential data" the profile shows — name and email.
    var currentUser: FirebaseAuth.User? { Auth.auth().currentUser }

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
                        RoundedRectangle(cornerRadius: 18)
                            .fill(Color.vastuPrimary)
                            .frame(width: 64, height: 64)
                            .overlay(Image(systemName: "person.fill").font(.system(size: 30)).foregroundColor(.white.opacity(0.85)))
                            .overlay(alignment: .bottomTrailing) {
                                // Not wired yet — no photo-upload flow exists.
                                Circle().fill(Color.white).frame(width: 24, height: 24)
                                    .overlay(Image(systemName: "camera.fill").font(.system(size: 11)).foregroundColor(.vastuPrimary))
                            }
                        VStack(alignment: .leading, spacing: 1) {
                            Text(displayName).font(.vt(19)).foregroundColor(.vastuCharcoal)
                            Text(email).font(.vt(13)).foregroundColor(.textSecondary)
                            // Not wired yet — no edit-profile screen exists.
                            Text("Edit Profile").font(.vt(13)).foregroundColor(.vastuPrimary)
                        }
                    }

                    // Available Credits — simulated wallet; no real credits/ledger backend yet.
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
        .onAppear { viewModel.loadHistory() }
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
                Text("₹\(entry.cost)").font(.vt(12)).foregroundColor(.textSecondary)
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
    let onChatNow: () -> Void
    let onVideoCall: () -> Void

    var body: some View {
        let extras = ExpertDisplayExtras.forExpert(expertUid)
        VStack(spacing: 0) {
            BackHeader(onBack: onBack).padding(.horizontal, 8).padding(.vertical, 8)

            ScrollView {
                VStack(spacing: 16) {
                    // Profile card
                    VStack(spacing: 0) {
                        AvatarImage(seed: extras.avatarSeed(for: expertUid))
                            .frame(width: 112, height: 112)
                            .clipShape(Circle())
                            .overlay(alignment: .bottomTrailing) {
                                Circle().fill(Color.onlineGreen).frame(width: 18, height: 18)
                                    .padding(3).background(Circle().fill(Color.white))
                            }
                        Spacer().frame(height: 16)
                        Text(expertName).font(.vt(24)).foregroundColor(.vastuCharcoal).multilineTextAlignment(.center)
                        Text(extras.specialty).font(.vt(15)).foregroundColor(.vastuPrimary)
                        Spacer().frame(height: 10)
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill").font(.system(size: 13)).foregroundColor(.walletGold)
                            Text("\(String(format: "%.1f", extras.rating))  ·  \(extras.reviewCount) reviews")
                                .font(.vt(13)).foregroundColor(.vastuCharcoal)
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color.walletGold.opacity(0.18)))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(24)
                    .background(RoundedRectangle(cornerRadius: 24).fill(Color.white))

                    HStack(spacing: 12) {
                        StatTile(icon: "rosette", label: "Experience", value: "\(extras.experienceYears) yrs")
                        StatTile(icon: "mappin.circle.fill", label: "Location", value: extras.location)
                        StatTile(icon: "sparkles", label: "Rate", value: "₹\(extras.pricePerSession)")
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 8) {
                            Image(systemName: "sparkles").font(.system(size: 16)).foregroundColor(.walletGold)
                            Text("About Expert").font(.vt(16)).foregroundColor(.vastuCharcoal)
                        }
                        Text(extras.bio).font(.vt(14)).foregroundColor(.textSecondary).lineSpacing(4)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(RoundedRectangle(cornerRadius: 20).fill(Color.white))
                }
                .padding(.horizontal, 20)
            }

            HStack(spacing: 12) {
                actionButton(icon: "phone.fill", title: "Chat Now", filled: true, action: onChatNow)
                actionButton(icon: "video.fill", title: "Video Call", filled: false, action: onVideoCall)
            }
            .padding(20)
        }
        .background(Color.surfaceLight.ignoresSafeArea())
    }

    private func actionButton(icon: String, title: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 16))
                Text(title).font(.vt(15))
            }
            .foregroundColor(filled ? .white : .vastuPrimary)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(RoundedRectangle(cornerRadius: 16).fill(filled ? Color.vastuPrimary : .white))
        }
        .buttonStyle(.plain)
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
            Text(label).font(.vt(12)).foregroundColor(.textSecondary)
            Text(value).font(.vt(15)).foregroundColor(.vastuCharcoal).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.white))
    }
}
