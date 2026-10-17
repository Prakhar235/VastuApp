import SwiftUI

struct GradientButton: View {
    let text: String
    var enabled: Bool = true
    var isLoading: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                RoundedRectangle(cornerRadius: 16).fill(Gradients.primaryButton)
                if isLoading {
                    ProgressView().tint(.white)
                } else {
                    Text(text).font(.vt(16)).foregroundColor(.white)
                }
            }
            .frame(height: 54)
            .opacity(enabled ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!enabled || isLoading)
    }
}

struct VastuTextField: View {
    @Binding var text: String
    let placeholder: String
    let icon: String
    var isPassword = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?

    @State private var isRevealed = false
    @FocusState private var isFocused: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(.vastuPrimary)
                .frame(width: 24)
            ZStack(alignment: .leading) {
                if text.isEmpty {
                    Text(placeholder).font(.vt(16)).foregroundColor(.textHint)
                }
                Group {
                    if isPassword && !isRevealed {
                        SecureField("", text: $text)
                    } else {
                        TextField("", text: $text)
                    }
                }
                .font(.vt(16))
                .foregroundColor(.vastuCharcoal)
                .keyboardType(keyboard)
                .textContentType(contentType)
                .textInputAutocapitalization(keyboard == .emailAddress || isPassword ? .never : .words)
                .autocorrectionDisabled(keyboard == .emailAddress || isPassword)
                .focused($isFocused)
            }
            if isPassword {
                Button { isRevealed.toggle() } label: {
                    Image(systemName: isRevealed ? "eye.slash.fill" : "eye.fill")
                        .foregroundColor(.textHint)
                }
                .accessibilityLabel(isRevealed ? "Hide password" : "Show password")
            }
        }
        .padding(.horizontal, 16)
        .frame(height: 56)
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.inputFill))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(isFocused ? Color.vastuPrimary : Color.inputFill, lineWidth: isFocused ? 2 : 1)
        )
        .contentShape(Rectangle())
        .onTapGesture { isFocused = true }
    }
}

/// A two-option pill switcher (Password/OTP, Experts/Posts, Profile/History…).
struct SegmentTabs<T: Hashable>: View {
    let options: [(value: T, label: String, icon: String?)]
    @Binding var selection: T
    var height: CGFloat = 52
    var background: Color = .white
    var cornerRadius: CGFloat = 26
    var innerRadius: CGFloat = 22
    var fontSize: CGFloat = 14
    var unselectedColor: Color = .textSecondary

    var body: some View {
        HStack(spacing: 0) {
            ForEach(options, id: \.value) { option in
                let selected = option.value == selection
                Button { selection = option.value } label: {
                    HStack(spacing: 6) {
                        if let icon = option.icon {
                            Image(systemName: icon).font(.system(size: 13, weight: .bold))
                        }
                        Text(option.label).font(.vt(fontSize))
                    }
                    .foregroundColor(selected ? .white : unselectedColor)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(RoundedRectangle(cornerRadius: innerRadius).fill(selected ? Color.vastuPrimary : .clear))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .frame(height: height)
        .background(RoundedRectangle(cornerRadius: cornerRadius).fill(background))
    }
}

/// Pravatar photo with a tinted placeholder while it loads.
struct AvatarImage: View {
    let seed: String
    var placeholder: Color = Color.vastuPrimary.opacity(0.08)

    var body: some View {
        AsyncImage(url: pravatarURL(seed)) { phase in
            if let image = phase.image {
                image.resizable().scaledToFill()
            } else {
                placeholder
            }
        }
    }
}

/// Header row with a back chevron, used by Profile / Expert profile / Create account.
struct BackHeader: View {
    var title: String = "Back"
    var tint: Color = .vastuCharcoal
    var titleSize: CGFloat = 16
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(tint)
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("Back")
            Text(title).font(.vt(titleSize)).foregroundColor(tint)
            Spacer()
        }
    }
}

extension View {
    /// Repeating 0→1→0 pulse value for "breathing" animations.
    func pulsing(_ isOn: Binding<Bool>, duration: Double) -> some View {
        onAppear {
            withAnimation(.easeInOut(duration: duration).repeatForever(autoreverses: true)) { isOn.wrappedValue = true }
        }
    }

    /// Light status-bar icons while this (dark) screen is showing.
    func darkScreenStatusBar() -> some View {
        onAppear { StatusBarStyle.darkScreenAppeared() }
            .onDisappear { StatusBarStyle.darkScreenDisappeared() }
    }

    /// A short message at the bottom of the screen while `message` is set; it clears itself after a moment.
    func toast(_ message: Binding<String?>) -> some View {
        overlay(alignment: .bottom) {
            if let text = message.wrappedValue {
                Text(text).font(.vt(13, .medium)).foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(Capsule().fill(Color.black.opacity(0.8)))
                    .padding(.horizontal, 24)
                    .padding(.bottom, 110)
                    .transition(.opacity)
                    .allowsHitTesting(false)
                    .task(id: text) {
                        try? await Task.sleep(nanoseconds: 2_500_000_000)
                        if message.wrappedValue == text { message.wrappedValue = nil }
                    }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: message.wrappedValue)
    }
}

/// Status-bar icon colour (Info.plist turns off per-screen control, so it's
/// set app-wide). Counted, so going from one dark screen to another keeps
/// the light icons whichever order appear/disappear arrive in.
enum StatusBarStyle {
    private static var darkScreens = 0

    static func darkScreenAppeared() {
        darkScreens += 1
        apply()
    }

    static func darkScreenDisappeared() {
        darkScreens = max(0, darkScreens - 1)
        apply()
    }

    private static func apply() {
        UIApplication.shared.statusBarStyle = darkScreens > 0 ? .lightContent : .darkContent
    }
}

/// A person's profile photo (stored as a base64 JPEG — see ProfilePhotos),
/// or their initials on the app colour when they have none.
struct ProfileAvatar: View {
    let photo: String
    let name: String
    let size: CGFloat
    /// nil for a circle.
    var cornerRadius: CGFloat?
    /// Shown instead of `photo` (a picture just chosen, not saved yet).
    var picked: UIImage?

    var body: some View {
        let image = picked ?? ProfilePhotos.decode(photo)
        ZStack {
            Color.vastuPrimary
            if let image = image {
                Image(uiImage: image).resizable().scaledToFill()
                    .accessibilityLabel("Photo of \(name)")
            } else {
                Text(avatarInitials(name)).font(.vt(size * 0.36)).foregroundColor(.white)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius ?? size / 2))
    }
}

/// "ananya verma" → "AV"; "?" with no name.
func avatarInitials(_ name: String) -> String {
    let letters = name.split(whereSeparator: \.isWhitespace).compactMap(\.first).prefix(2).map { String($0).uppercased() }.joined()
    return letters.isEmpty ? "?" : letters
}

/// An avatar with a camera badge: tapping it offers "Take a photo" or
/// "Choose from gallery" and hands back the picture.
struct ProfilePhotoEditor: View {
    let photo: String
    let name: String
    let size: CGFloat
    var picked: UIImage?
    var cornerRadius: CGFloat?
    let onPicked: (UIImage) -> Void

    @State private var choosing = false
    @State private var source: MediaSource?
    @State private var error: String?

    var body: some View {
        Button { choosing = true } label: {
            ProfileAvatar(photo: photo, name: name, size: size, cornerRadius: cornerRadius, picked: picked)
                .overlay(alignment: .bottomTrailing) {
                    Circle().fill(Color.white).frame(width: max(24, size * 0.34), height: max(24, size * 0.34))
                        .overlay(Circle().stroke(Color.vastuPrimary.opacity(0.25), lineWidth: 1))
                        .overlay(Image(systemName: "camera.fill").font(.system(size: max(11, size * 0.15))).foregroundColor(.vastuPrimary))
                        .offset(x: 2, y: 2)
                }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Change photo")
        .confirmationDialog("Profile photo", isPresented: $choosing) {
            Button("Take a photo") { MediaPermissions.openCamera(.camera, open: { source = $0 }, onError: { error = $0 }) }
            Button("Choose from gallery") { source = .gallery }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Take a new photo or choose one from your gallery.")
        }
        .mediaPicker($source, onPhoto: { onPicked($0.image) }, onError: { error = $0 })
        .alert(error ?? "", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("OK", role: .cancel) {}
        }
    }
}

/// "EN | हिं" switch for the language Ananya replies in (and the agent
/// screens use). Takes effect from her next reply.
struct LanguageSwitch: View {
    var onChanged: (AgentLanguage) -> Void = { _ in }
    @ObservedObject private var language = AgentLanguagePref.shared

    var body: some View {
        HStack(spacing: 0) {
            ForEach([(AgentLanguage.english, "EN"), (AgentLanguage.hindi, "हिं")], id: \.0) { option, label in
                let selected = language.current == option
                Button {
                    guard !selected else { return }
                    language.set(option)
                    onChanged(option)
                } label: {
                    Text(label).font(.vt(13, .semibold))
                        .foregroundColor(selected ? .white : CallTones.textMuted)
                        .frame(width: 40, height: 32)
                        .background(RoundedRectangle(cornerRadius: 15).fill(selected ? Color.vastuPrimary : .clear))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(3)
        .background(RoundedRectangle(cornerRadius: 18).fill(CallTones.surface))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(CallTones.hairline, lineWidth: 1))
    }
}

/// "5 minutes ago", "yesterday"…
func timeAgo(_ millis: Int64) -> String {
    let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
    if Date().timeIntervalSince(date) < 60 { return "Just now" }
    let formatter = RelativeDateTimeFormatter()
    formatter.unitsStyle = .full
    return formatter.localizedString(for: date, relativeTo: Date())
}

/// A small red count badge ("9+" past nine), for unread messages and notifications.
struct CountBadge: View {
    let count: Int64

    var body: some View {
        Text(count > 9 ? "9+" : "\(count)").font(.vt(10)).foregroundColor(.white)
            .frame(minWidth: 18, minHeight: 18)
            .background(Circle().fill(Color.danger))
    }
}
