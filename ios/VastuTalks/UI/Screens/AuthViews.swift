import FirebaseAuth
import SwiftUI

typealias FirebaseAuthUser = FirebaseAuth.User

/// Shared by SignInView and CreateAccountView; each screen gets its own instance.
@MainActor
final class AuthViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let authRepository = AuthRepository()
    private let userRepository = UserRepository()

    func signIn(email: String, password: String, onSuccess: @escaping () -> Void) {
        if email.trimmingCharacters(in: .whitespaces).isEmpty || password.isEmpty {
            errorMessage = "Enter both email and password."
            return
        }
        isLoading = true
        errorMessage = nil
        Task {
            do {
                _ = try await authRepository.signIn(email: email, password: password)
                isLoading = false
                await PushToken.saveBestEffort()
                onSuccess()
            } catch {
                isLoading = false
                errorMessage = error.localizedDescription
            }
        }
    }

    func signUp(fullName: String, email: String, password: String, role: UserRole, onSuccess: @escaping () -> Void) {
        isLoading = true
        errorMessage = nil
        Task {
            let user: FirebaseAuthUser
            do {
                user = try await authRepository.signUp(email: email, password: password, displayName: fullName)
            } catch {
                isLoading = false
                errorMessage = error.localizedDescription
                return
            }
            // The account exists even if this profile write fails, so don't block on it —
            // worst case they land on Home with the default role.
            do {
                try await userRepository.createProfileForNewUser(uid: user.uid, name: fullName, email: email, role: role)
            } catch {
                errorMessage = "Account created, but saving your profile failed: \(error.localizedDescription)"
            }
            isLoading = false
            await PushToken.saveBestEffort()
            onSuccess()
        }
    }
}

private enum SignInMode { case password, otp }

struct SignInView: View {
    let onSignInSuccess: () -> Void
    let onCreateAccount: () -> Void

    @StateObject private var viewModel = AuthViewModel()
    @State private var mode = SignInMode.password
    @State private var email = ""
    @State private var password = ""

    var body: some View {
        ZStack {
            Color.surfaceLight.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Spacer().frame(height: 48)

                    // Small logo mark — no full-bleed gradient on this screen.
                    RoundedRectangle(cornerRadius: 20)
                        .fill(Color.vastuPrimary)
                        .frame(width: 64, height: 64)
                        .overlay(Image(systemName: "sparkles").font(.system(size: 28, weight: .bold)).foregroundColor(.white))

                    Spacer().frame(height: 24)
                    Text("Welcome Back!").font(.vt(30)).foregroundColor(.vastuCharcoal)
                    Text("Sign in to continue your journey")
                        .font(.vt(15))
                        .foregroundColor(.textSecondary)
                        .padding(.top, 4)
                        .padding(.bottom, 24)

                    SegmentTabs(
                        options: [(SignInMode.password, "Password", nil), (SignInMode.otp, "OTP", nil)],
                        selection: $mode
                    )

                    Spacer().frame(height: 20)

                    VStack(alignment: .leading, spacing: 0) {
                        Text("Email").font(.vt(14)).foregroundColor(.vastuCharcoal)
                        Spacer().frame(height: 8)
                        VastuTextField(text: $email, placeholder: "you@example.com", icon: "envelope.fill",
                                       keyboard: .emailAddress, contentType: .emailAddress)

                        if mode == .password {
                            Spacer().frame(height: 18)
                            Text("Password").font(.vt(14)).foregroundColor(.vastuCharcoal)
                            Spacer().frame(height: 8)
                            VastuTextField(text: $password, placeholder: "Enter password", icon: "lock.fill",
                                           isPassword: true, contentType: .password)
                            Spacer().frame(height: 14)
                            Button { /* Forgot password isn't wired yet — same as Android. */ } label: {
                                Text("Forgot Password?").font(.vt(14)).foregroundColor(.vastuPrimary)
                                    .frame(maxWidth: .infinity, alignment: .trailing)
                            }
                        } else {
                            Spacer().frame(height: 16)
                            Text("OTP sign-in isn't wired up yet — use Password to sign in for now.")
                                .font(.vt(13)).foregroundColor(.textSecondary)
                        }

                        if let error = viewModel.errorMessage {
                            Spacer().frame(height: 12)
                            Text(error).font(.vt(13)).foregroundColor(.errorText)
                        }

                        Spacer().frame(height: 24)
                        GradientButton(text: "Sign In", enabled: mode == .password, isLoading: viewModel.isLoading) {
                            hideKeyboard()
                            viewModel.signIn(email: email, password: password, onSuccess: onSignInSuccess)
                        }
                    }
                    .padding(24)
                    .background(RoundedRectangle(cornerRadius: 24).fill(Color.white))

                    Spacer().frame(height: 40)
                    HStack(spacing: 0) {
                        Text("Don't have an account? ").font(.vt(14)).foregroundColor(.textSecondary)
                        Button(action: onCreateAccount) {
                            Text("Sign Up").font(.vt(14)).foregroundColor(.vastuPrimary)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 28)
                }
                .padding(.horizontal, 28)
            }
            .scrollDismissesKeyboard(.interactively)
        }
    }
}

struct CreateAccountView: View {
    let onBack: () -> Void
    let onAccountCreated: () -> Void

    @StateObject private var viewModel = AuthViewModel()
    @State private var fullName = ""
    @State private var email = ""
    @State private var mobile = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var agreedToTerms = false
    @State private var role = UserRole.normalUser

    private var canSubmit: Bool {
        !fullName.trimmingCharacters(in: .whitespaces).isEmpty && !email.trimmingCharacters(in: .whitespaces).isEmpty &&
            !mobile.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty && password == confirmPassword && agreedToTerms
    }

    var body: some View {
        ZStack {
            Gradients.auth.ignoresSafeArea()
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    BackHeader(tint: .white, onBack: onBack)
                    Text("Create Account").font(.vt(28)).foregroundColor(.white)
                        .padding(.top, 8).padding(.leading, 8)
                    Text("Join Vastu Talks today").font(.vt(14)).foregroundColor(.white.opacity(0.85))
                        .padding(.top, 4).padding(.leading, 8).padding(.bottom, 16)
                }
                .padding(.horizontal, 8)

                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        label("I am a...")
                        SegmentTabs(
                            options: [(UserRole.normalUser, "Normal User", "person.fill"),
                                      (UserRole.vastuExpert, "Vastu Expert", "headphones")],
                            selection: $role,
                            height: 48, background: Color(hex: 0xF3F1F7), cornerRadius: 12, innerRadius: 9, fontSize: 13
                        )
                        if role == .vastuExpert {
                            Text("You'll be listed for normal users to call, and won't see the expert directory yourself.")
                                .font(.vt(12)).foregroundColor(.vastuPrimary).padding(.top, 6)
                        }

                        Group {
                            label("Full Name", top: 16)
                            VastuTextField(text: $fullName, placeholder: "John Doe", icon: "person.fill", contentType: .name)
                            label("Email", top: 16)
                            VastuTextField(text: $email, placeholder: "you@example.com", icon: "envelope.fill",
                                           keyboard: .emailAddress, contentType: .emailAddress)
                            label("Mobile Number", top: 16)
                            VastuTextField(text: $mobile, placeholder: "+91 98765 43210", icon: "phone.fill",
                                           keyboard: .phonePad, contentType: .telephoneNumber)
                            label("Password", top: 16)
                            VastuTextField(text: $password, placeholder: "Create password", icon: "lock.fill",
                                           isPassword: true, contentType: .newPassword)
                            label("Confirm Password", top: 16)
                            VastuTextField(text: $confirmPassword, placeholder: "Re-enter password", icon: "lock.fill",
                                           isPassword: true, contentType: .newPassword)
                        }

                        HStack(spacing: 10) {
                            Button { agreedToTerms.toggle() } label: {
                                RoundedRectangle(cornerRadius: 3)
                                    .stroke(agreedToTerms ? Color.vastuPrimary : Color.textSecondary, lineWidth: 2)
                                    .background(RoundedRectangle(cornerRadius: 3).fill(agreedToTerms ? Color.vastuPrimary : .clear))
                                    .overlay(Image(systemName: "checkmark").font(.system(size: 11, weight: .heavy))
                                        .foregroundColor(.white).opacity(agreedToTerms ? 1 : 0))
                                    .frame(width: 20, height: 20)
                                    .padding(12)
                            }
                            .accessibilityLabel("I agree to the Terms and Privacy Policy")
                            (Text("I agree to the ").foregroundColor(Color(hex: 0x4A4A4A)) +
                             Text("Terms").foregroundColor(.vastuPrimary) +
                             Text(" and ").foregroundColor(Color(hex: 0x4A4A4A)) +
                             Text("Privacy Policy").foregroundColor(.vastuPrimary))
                                .font(.vt(13))
                        }
                        .padding(.top, 6)
                        .padding(.leading, -12)

                        if let error = viewModel.errorMessage {
                            Spacer().frame(height: 16)
                            Text(error).font(.vt(13)).foregroundColor(.errorText)
                        }

                        Spacer().frame(height: 24)
                        GradientButton(text: "Create Account", enabled: canSubmit, isLoading: viewModel.isLoading) {
                            hideKeyboard()
                            viewModel.signUp(fullName: fullName, email: email, password: password, role: role,
                                             onSuccess: onAccountCreated)
                        }
                        Spacer().frame(height: 24)
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 28)
                }
                .scrollDismissesKeyboard(.interactively)
                .background(
                    UnevenTopRoundedRectangle(radius: 28).fill(Color.white).ignoresSafeArea(edges: .bottom)
                )
            }
        }
    }

    private func label(_ text: String, top: CGFloat = 0) -> some View {
        Text(text).font(.vt(13)).foregroundColor(.vastuCharcoal).padding(.top, top).padding(.bottom, 8)
    }
}

/// A rectangle with only its top corners rounded (iOS 16 has no built-in for this).
struct UnevenTopRoundedRectangle: Shape {
    let radius: CGFloat

    func path(in rect: CGRect) -> Path {
        Path(UIBezierPath(roundedRect: rect, byRoundingCorners: [.topLeft, .topRight],
                          cornerRadii: CGSize(width: radius, height: radius)).cgPath)
    }
}

func hideKeyboard() {
    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
}
