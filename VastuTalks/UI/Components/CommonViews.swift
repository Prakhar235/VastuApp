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
}
