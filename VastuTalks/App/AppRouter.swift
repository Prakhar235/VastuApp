import FirebaseAuth
import SwiftUI

enum RootScreen {
    case splash, onboarding, signIn, home
}

/// Screens pushed on top of the root, mirroring the Android nav graph's routes.
enum Route: Hashable {
    case createAccount
    case profile
    case expertProfile(uid: String, name: String)
    case liveCalling(calleeUid: String, calleeName: String, callType: CallType)
    case liveInCall(channelName: String, peerName: String, callType: CallType)
    case agentCalling
    case agentInCall
}

final class AppRouter: ObservableObject {
    static let shared = AppRouter()

    @Published var root: RootScreen = .splash
    @Published var path: [Route] = []
    /// Set when the user taps an incoming-call notification.
    @Published var incomingCall: IncomingCallPush?

    func setRoot(_ screen: RootScreen, path newPath: [Route] = []) {
        path = newPath
        root = screen
    }

    func push(_ route: Route) { path.append(route) }

    func pop() {
        if !path.isEmpty { path.removeLast() }
    }

    /// Replaces the top screen (Android's popUpTo(current) { inclusive = true }).
    func replaceTop(with route: Route) {
        if !path.isEmpty { path.removeLast() }
        path.append(route)
    }

    func popToRoot() { path = [] }

    /// Where Splash goes: a signed-in user lands on Home, a signed-out
    /// user who hasn't seen onboarding sees it once first.
    func finishSplash() {
        if Auth.auth().currentUser != nil {
            setRoot(.home)
        } else if !UserDefaults.standard.bool(forKey: "has_seen_onboarding") {
            setRoot(.onboarding)
        } else {
            setRoot(.signIn)
        }
    }

    func finishOnboarding() {
        UserDefaults.standard.set(true, forKey: "has_seen_onboarding")
        setRoot(.signIn)
    }

    func signOut() {
        AuthRepository().signOut()
        setRoot(.signIn)
    }
}
