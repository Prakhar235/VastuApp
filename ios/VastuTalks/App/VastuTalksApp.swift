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

    /// Show the "Incoming call" banner (with sound) even while the app is open.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    /// Tapping the banner opens the full-screen incoming-call screen.
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if let call = IncomingCallPush(userInfo: response.notification.request.content.userInfo) {
            DispatchQueue.main.async { AppRouter.shared.incomingCall = call }
        }
        completionHandler()
    }
}
