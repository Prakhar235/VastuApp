import FirebaseAuth
import SwiftUI

struct RootView: View {
    @ObservedObject private var router = AppRouter.shared

    var body: some View {
        if !FirebaseSetup.isConfigured {
            FirebaseSetupNeededView()
        } else {
            NavigationStack(path: $router.path) {
                rootScreen
                    .hiddenNavigationBar()
                    .navigationDestination(for: Route.self) { route in
                        destination(route).hiddenNavigationBar()
                    }
            }
            .fullScreenCover(item: $router.incomingCall) { call in
                IncomingCallView(call: call)
            }
        }
    }

    @ViewBuilder private var rootScreen: some View {
        switch router.root {
        case .splash:
            SplashView { router.finishSplash() }
        case .onboarding:
            OnboardingView { router.finishOnboarding() }
        case .signIn:
            SignInView(
                onSignInSuccess: { router.setRoot(.home) },
                onCreateAccount: { router.push(.createAccount) }
            )
        case .home:
            HomeView()
        }
    }

    @ViewBuilder private func destination(_ route: Route) -> some View {
        switch route {
        case .createAccount:
            // There's no OTP step: Firebase has already created the account.
            CreateAccountView(onBack: { router.pop() }, onAccountCreated: { router.setRoot(.home) })
        case .profile:
            ProfileView(onBack: { router.pop() }, onSignOut: { router.signOut() })
        case .expertProfile(let uid, let name):
            RealExpertProfileView(
                expertUid: uid,
                expertName: name,
                onBack: { router.pop() },
                onChatNow: { /* No chat feature yet — same as Android. */ },
                onVideoCall: { router.push(.liveCalling(calleeUid: uid, calleeName: name, callType: .video)) }
            )
        case .liveCalling(let calleeUid, let calleeName, let callType):
            LiveCallingView(
                calleeUid: calleeUid,
                calleeName: calleeName,
                callType: callType,
                onConnected: { channelName, peerName in
                    router.replaceTop(with: .liveInCall(channelName: channelName, peerName: peerName, callType: callType))
                },
                onCancel: { router.pop() }
            )
        case .liveInCall(let channelName, let peerName, let callType):
            LiveInCallView(channelName: channelName, peerName: peerName, callType: callType) { duration, startMillis in
                CallLog.record(channelName: channelName, peerName: peerName, callType: callType,
                               durationSeconds: duration, startTimeMillis: startMillis)
                router.popToRoot()
            }
        case .agentCalling:
            AgentCallingView(
                onConnected: { router.replaceTop(with: .agentInCall) },
                onCancel: { router.pop() }
            )
        case .agentInCall:
            AgentInCallView(onEndCall: { router.popToRoot() })
        }
    }
}

/// Logs a finished call. Runs in a detached task so navigating away doesn't cut it short.
enum CallLog {
    static func record(channelName: String, peerName: String, callType: CallType, durationSeconds: Int, startTimeMillis: Int64) {
        // channelName is "expert-<expertUid>", which recovers the expert's uid.
        let otherPartyId = channelName.hasPrefix("expert-") ? String(channelName.dropFirst("expert-".count)) : channelName
        let myUid = Auth.auth().currentUser?.uid
        Task.detached {
            try? await CallHistoryRepository().logCall(CallHistoryEntry(
                expertId: otherPartyId, expertName: peerName, callType: callType.rawValue,
                startTimeMillis: startTimeMillis, durationSeconds: durationSeconds,
                cost: 0, // real experts don't have per-minute pricing set up yet
                channelName: channelName
            ))
            // Only count the consultation from the expert's own device, so one call isn't counted twice.
            if let myUid = myUid, myUid == otherPartyId {
                try? await UserRepository().incrementConsultationCount(expertUid: otherPartyId)
            }
        }
    }
}

/// Shown instead of crashing when GoogleService-Info.plist hasn't been added yet.
private struct FirebaseSetupNeededView: View {
    var body: some View {
        ZStack {
            Gradients.auth.ignoresSafeArea()
            VStack(spacing: 14) {
                Image(systemName: "sparkles").font(.system(size: 44, weight: .bold)).foregroundColor(.white)
                Text("Firebase isn't set up yet").font(.vt(22)).foregroundColor(.white)
                Text("Add GoogleService-Info.plist for the iOS app (bundle ID com.vastutalks.app) to the VastuTalks folder and rebuild. See README.md.")
                    .font(.vt(14, .medium))
                    .foregroundColor(.white.opacity(0.85))
                    .multilineTextAlignment(.center)
            }
            .padding(32)
        }
    }
}
