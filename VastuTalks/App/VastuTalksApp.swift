import AudioToolbox
import FirebaseMessaging
import SwiftUI
import UserNotifications

@main
struct VastuTalksApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(.light)
        }
    }
}

/// An incoming call delivered by push (the onCallRequestCreated Cloud Function).
struct IncomingCallPush: Identifiable, Equatable {
    let callId: String
    let callerName: String
    let channelName: String
    let callType: String
    var id: String { callId }

    init?(userInfo: [AnyHashable: Any]) {
        guard userInfo["type"] as? String == "incoming_call",
              let callId = userInfo["callId"] as? String,
              let channelName = userInfo["channelName"] as? String, !channelName.isEmpty else { return nil }
        self.callId = callId
        self.channelName = channelName
        callerName = userInfo["callerName"] as? String ?? "Someone"
        callType = userInfo["callType"] as? String ?? "AUDIO"
    }
}

/// Makes sure an incoming call rings in one place only. While the expert's
/// Home is on screen it shows the call itself (and rings), so the push
/// banner is skipped, or removed if it got there first; anywhere else the
/// push notification does the ringing.
enum IncomingCallAlerts {
    /// True while the expert's Home is on screen and showing incoming calls itself.
    static var inAppRingerVisible = false

    private static var ringTimer: Timer?

    /// Rings and vibrates (repeating) for the in-app incoming call card.
    static func startRinging() {
        guard ringTimer == nil else { return }
        let ring = {
            AudioServicesPlayAlertSound(SystemSoundID(1005))
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
        ring()
        ringTimer = Timer.scheduledTimer(withTimeInterval: 2.5, repeats: true) { _ in ring() }
    }

    static func stopRinging() {
        ringTimer?.invalidate()
        ringTimer = nil
    }

    /// Removes the "Incoming call" banner for `callId` from Notification Centre.
    static func clearNotification(callId: String) {
        let center = UNUserNotificationCenter.current()
        center.getDeliveredNotifications { delivered in
            let ids = delivered.filter { $0.request.content.userInfo["callId"] as? String == callId }.map(\.request.identifier)
            center.removeDeliveredNotifications(withIdentifiers: ids)
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        FirebaseSetup.configure()
        guard FirebaseSetup.isConfigured else { return true }
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        application.registerForRemoteNotifications()
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("Push registration failed (needs the Push Notifications capability): \(error.localizedDescription)")
    }

    /// Token rotation — save it so calls keep reaching this device.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        Task { try? await UserRepository().saveFcmToken(token) }
    }

    /// Pushes that arrive while the app is open: shown as a banner, except an
    /// incoming call the expert's Home is already showing, and a message in
    /// the chat that's open on screen.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let userInfo = notification.request.content.userInfo
        switch userInfo["type"] as? String {
        case "incoming_call" where IncomingCallAlerts.inAppRingerVisible:
            completionHandler([])
        case "chat" where (userInfo["chatId"] as? String) == ChatRepository.openChatId:
            completionHandler([])
        default:
            completionHandler([.banner, .list, .sound])
        }
    }

    /// Tapping a notification: an incoming call opens the full-screen call
    /// screen; a chat message opens that chat; a like or comment opens the post.
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        DispatchQueue.main.async {
            let router = AppRouter.shared
            switch userInfo["type"] as? String {
            case "incoming_call":
                if let call = IncomingCallPush(userInfo: userInfo) { router.incomingCall = call }
            case "chat":
                if let uid = userInfo["senderUid"] as? String {
                    router.open(fromNotification: .conversation(otherUid: uid, otherName: userInfo["senderName"] as? String ?? ""))
                }
            case "post_activity":
                if let postId = userInfo["postId"] as? String {
                    router.open(fromNotification: .postDetail(postId: postId))
                }
            default:
                break
            }
        }
        completionHandler()
    }
}
