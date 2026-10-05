import SwiftUI

struct SplashView: View {
    let onFinished: () -> Void

    @State private var entered = false
    @State private var glow = false

    var body: some View {
        ZStack {
            Gradients.auth.ignoresSafeArea()
            VStack(spacing: 0) {
                ZStack {
                    // Slow pulsing glow behind the compass badge — keeps the splash alive while it waits.
                    Circle()
                        .fill(Color.white.opacity(0.14))
                        .frame(width: 96, height: 96)
                        .scaleEffect(glow ? 1.18 : 1)
                    CompassIllustration()
                        .frame(width: 84, height: 84)
                        .background(Circle().fill(Color.white.opacity(0.22)))
                        .clipShape(Circle())
                }
                Spacer().frame(height: 20)
                Text("Vastu Talks").font(.vt(26)).foregroundColor(.white)
                Text("Connect with verified Vastu experts").font(.vt(13)).foregroundColor(.white.opacity(0.85))
            }
            .opacity(entered ? 1 : 0)
            .scaleEffect(entered ? 1 : 0.85)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) { entered = true }
            withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) { glow = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) { onFinished() }
        }
    }
}

private struct OnboardingSlide {
    let icon: String
    let colors: [Color]
    let badge: String
    let title: String
    let subtitle: String
}

private let slides = [
    OnboardingSlide(
        icon: "house.fill", colors: [.vastuPrimary, .vastuPrimaryDark],
        badge: "Your spiritual guide", title: "Welcome to Vastu Talks",
        subtitle: "Connect with certified Vastu experts for personalized guidance on harmonizing your spaces."
    ),
    OnboardingSlide(
        icon: "message.fill", colors: [.onboardingPinkStart, .onboardingPinkEnd],
        badge: "Anytime, Anywhere", title: "Expert Guidance",
        subtitle: "Chat or video call with experienced consultants who understand your needs."
    ),
    OnboardingSlide(
        icon: "sparkles", colors: [.onboardingGreenStart, .onboardingGreenEnd],
        badge: "Positive Energy", title: "Transform Your Space",
        subtitle: "Receive customized recommendations to enhance prosperity and well-being."
    )
]

struct OnboardingView: View {
    let onDone: () -> Void
    @State private var page = 0

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.surfaceLight.ignoresSafeArea()

            VStack(spacing: 0) {
                TabView(selection: $page) {
                    ForEach(slides.indices, id: \.self) { index in
                        slideView(slides[index]).tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))

                HStack(spacing: 8) {
                    ForEach(slides.indices, id: \.self) { index in
                        Capsule()
                            .fill(index == page ? Color.vastuPrimary : Color.textSecondary.opacity(0.25))
                            .frame(width: index == page ? 24 : 8, height: 8)
                    }
                }
                .animation(.easeInOut(duration: 0.2), value: page)

                Spacer().frame(height: 24)

                let isLast = page == slides.count - 1
                Button {
                    if isLast { onDone() } else { withAnimation { page += 1 } }
                } label: {
                    HStack(spacing: 2) {
                        Text(isLast ? "Get Started" : "Continue").font(.vt(16))
                        Image(systemName: "chevron.right").font(.system(size: 15, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(RoundedRectangle(cornerRadius: 28).fill(Color.vastuPrimary))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 28)
            .padding(.vertical, 24)

            Button(action: onDone) {
                Text("Skip").font(.vt(14)).foregroundColor(.textSecondary).padding(20)
            }
        }
    }

    private func slideView(_ slide: OnboardingSlide) -> some View {
        VStack(spacing: 0) {
            Spacer()
            RoundedRectangle(cornerRadius: 32)
                .fill(LinearGradient(colors: slide.colors, startPoint: .topLeading, endPoint: .bottomTrailing))
                .frame(width: 140, height: 140)
                .overlay(Image(systemName: slide.icon).font(.system(size: 60, weight: .bold)).foregroundColor(.white))
            Spacer().frame(height: 28)
            Text(slide.badge)
                .font(.vt(13))
                .foregroundColor(.vastuPrimary)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Capsule().fill(Color.white))
            Spacer().frame(height: 20)
            Text(slide.title)
                .font(.vt(30))
                .foregroundColor(.vastuCharcoal)
                .multilineTextAlignment(.center)
            Spacer().frame(height: 14)
            Text(slide.subtitle)
                .font(.vt(15))
                .foregroundColor(.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 12)
            Spacer()
        }
    }
}
