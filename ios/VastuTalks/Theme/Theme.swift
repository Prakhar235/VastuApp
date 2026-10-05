import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }

    // Brand purples — taken directly from the provided screens
    static let vastuVioletStart = Color(hex: 0x6A4CE0)
    static let vastuVioletEnd = Color(hex: 0x8B3FE0)
    static let vastuPinkEnd = Color(hex: 0xD6308E)
    static let vastuPrimary = Color(hex: 0x5B3FE0)
    static let vastuPrimaryDark = Color(hex: 0x3D2B9E)

    // Vastu-specific earth accents (used past the auth flow, for the
    // expert-discovery / call surfaces so the app reads as "Vastu" and
    // not generic fintech)
    static let vastuCopper = Color(hex: 0xC86E4A)
    static let vastuSaffron = Color(hex: 0xE8A33D)
    static let vastuCream = Color(hex: 0xFBF6EE)
    static let vastuCharcoal = Color(hex: 0x2B2B2B)

    // Neutral surfaces
    static let surfaceLight = Color(hex: 0xF7F6FB)
    static let inputFill = Color(hex: 0xF1F0F6)
    static let textSecondary = Color(hex: 0x6B6878)
    static let textHint = Color(hex: 0x9C99A8)
    static let success = Color(hex: 0x2E9E6B)
    static let danger = Color(hex: 0xE0473E)
    static let onlineGreen = Color(hex: 0x34C76B)
    static let errorText = Color(hex: 0xD32F2F)

    // Onboarding slide accents (slide 1 reuses vastuPrimary/PrimaryDark)
    static let onboardingPinkStart = Color(hex: 0xEA5B7B)
    static let onboardingPinkEnd = Color(hex: 0xD6308E)
    static let onboardingGreenStart = Color(hex: 0x3FCE7A)
    static let onboardingGreenEnd = Color(hex: 0x1FA85C)
    static let walletGold = Color(hex: 0xF2B33D)
    static let walletGoldDark = Color(hex: 0xE0902A)

    // Whiteboard / sketch colors
    static let boardBlue = Color(hex: 0x3E8EDE)
    static let boardRed = Color(hex: 0xE24C4C)
    static let boardGreen = Color(hex: 0x4CAF7A)
}

enum Gradients {
    static let auth = LinearGradient(
        colors: [.vastuVioletStart, .vastuVioletEnd, .vastuPinkEnd],
        startPoint: .top, endPoint: .bottom
    )
    static let primaryButton = LinearGradient(
        colors: [.vastuVioletStart, Color(hex: 0x6F3FE0)],
        startPoint: .leading, endPoint: .trailing
    )
    static let primaryHorizontal = LinearGradient(
        colors: [.vastuPrimary, .vastuPrimaryDark],
        startPoint: .leading, endPoint: .trailing
    )
}

/// Shared tones for the agent call's dark surfaces.
enum CallTones {
    static let ink = Color(hex: 0x110F18)
    static let surface = Color(hex: 0x1C1927)
    static let surfaceRaised = Color(hex: 0x262236)
    static let hairline = Color.white.opacity(0.08)
    static let textPrimary = Color(hex: 0xF4F2FA)
    static let textMuted = Color(hex: 0x9D98B3)
}

extension Font {
    /// The Android app's typography is bold throughout (every Material
    /// text style is FontWeight.Bold), so bold is the default here too.
    static func vt(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        .system(size: size, weight: weight)
    }
}

/// "Ananya Verma" -> "AV", for avatar placeholders.
func initials(_ name: String) -> String {
    name.split(separator: " ").compactMap { $0.first }.prefix(2).map(String.init).joined()
}

func pravatarURL(_ seed: String) -> URL? {
    URL(string: "https://i.pravatar.cc/300?u=\(seed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? seed)")
}

extension View {
    /// Every screen draws its own header, like the Android app.
    func hiddenNavigationBar() -> some View {
        toolbar(.hidden, for: .navigationBar)
    }
}
