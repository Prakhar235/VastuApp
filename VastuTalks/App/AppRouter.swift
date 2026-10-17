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
    /// `video`: start with the camera on; switchable during the call either way.
    case agentCalling(video: Bool)
    case agentInCall(video: Bool)
    /// Text chat with Ananya (same agent, no voice).
    case agentChat
    /// Guided room scan: a photo of each wall, then Ananya's read of the room.
    case roomScan
    /// A room report saved in the chat with Ananya, opened from its message.
    case roomReport(messageId: Int)
    /// One-to-one chat with a user or expert, and the list of all chats.
    case conversation(otherUid: String, otherName: String)
    case chats
    case postDetail(postId: String)
    case notifications
    /// Vastu experts write a post for the users' Posts tab.
    case createPost
}

final class AppRouter: ObservableObject {
    static let shared = AppRouter()

    @Published var root: RootScreen = .splash
    @Published var path: [Route] = []
    /// Set when the user taps an incoming-call notification.
    @Published var incomingCall: IncomingCallPush?
    /// Opened from a chat or like/comment notification before Home was up; shown after Splash.
    var pendingRoute: Route?

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

    /// Back to `route` if it's further down the stack; false if it isn't there.
    func popBack(to route: Route) -> Bool {
        guard let index = path.lastIndex(of: route) else { return false }
        path.removeSubrange((index + 1)...)
        return true
    }

    /// Opens a screen from a notification: straight away when signed in and
    /// past Splash, otherwise once Splash lands on Home.
    func open(fromNotification route: Route) {
        if root == .home {
            push(route)
        } else {
            pendingRoute = route
        }
    }

    /// Where Splash goes: a signed-in user lands on Home, a signed-out
    /// user who hasn't seen onboarding sees it once first.
    func finishSplash() {
        if Auth.auth().currentUser != nil {
            setRoot(.home, path: pendingRoute.map { [$0] } ?? [])
            pendingRoute = nil
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
        Task { @MainActor in
            // Experts show as offline straight away (fails harmlessly for normal users).
            try? await UserRepository().heartbeat(present: false)
            AuthRepository().signOut()
            setRoot(.signIn)
        }
    }
}
