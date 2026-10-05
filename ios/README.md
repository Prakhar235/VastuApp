# Vastu Talks (iOS)

The iOS version of the Vastu Talks app — same screens, same features and
the same backend as the Android app (`../VastuTalks 25`), so Android and iOS
users can call each other. This folder is separate from the Android project.

- SwiftUI, iOS 16+, iPhone (portrait)
- Firebase (Auth, Firestore, Functions, Messaging) — Swift Package Manager, pinned to 10.29.0
- Agora RTC 4.5.1 for real video/audio calls — same engine version as Android
- Ananya, the AI agent: OpenAI chat + transcription, system text-to-speech, the shared
  whiteboard, and photo/video sharing with compass direction

Builds with Xcode 14.2 or newer.

## How to open
1. Open `VastuTalks.xcodeproj` in Xcode. It downloads the Swift packages on
   first open (Agora and Firebase are large — give it a few minutes).
2. Add your Firebase config (below), and optionally the OpenAI key.
3. Pick an iPhone simulator or your phone and press Run.

To run on a real iPhone, select the **VastuTalks** target → *Signing & Capabilities*
and choose your Team.

## Firebase config (required)
1. In the [Firebase Console](https://console.firebase.google.com/), open the
   **same project** the Android app uses → Project settings → **Add app → iOS**,
   with bundle ID `com.vastutalks.app`.
2. Download `GoogleService-Info.plist` and put it at
   `VastuTalks/GoogleService-Info.plist` (next to the `App` and `UI` folders).
   Keep it out of git, like `google-services.json` on Android.
3. Rebuild. Without it the app shows a "Firebase isn't set up yet" screen
   instead of crashing.

Email/password auth, Firestore rules and the Cloud Functions are shared with
Android — nothing else to set up for them.

## Ananya's OpenAI key (optional)
Copy `Config/Secrets.example.xcconfig` to `Config/Secrets.xcconfig` (keep it out of git)
and set `OPENAI_API_KEY`. Without it Ananya answers with canned replies, like the
Android app without `OPENAI_API_KEY`. As on Android, a key built into the app can
be extracted — route it through a Cloud Function before shipping.

## Incoming-call ringing (push notifications)
Experts' phones ring through Firebase Cloud Messaging, like on Android:
1. You need a **paid Apple Developer account** (push isn't available on a free
   Personal Team).
2. In the Apple Developer portal create an **APNs key**, then upload it in
   Firebase Console → Project settings → Cloud Messaging → Apple app configuration.
3. iPhones only show pushes that carry a visible alert, so the shared
   `onCallRequestCreated` Cloud Function needs a small addition. Apply
   `functions-ios-push.patch` from this folder to the Android project and redeploy:
   ```
   cd "../VastuTalks 25"
   git apply "../VastuTalks iOS/functions-ios-push.patch"
   firebase deploy --only functions
   ```
   Android ignores the added `apns` block, so its behaviour doesn't change.

The iPhone shows an "Incoming video/audio call" banner with sound; tapping it
opens the full-screen Accept/Decline screen. With the app open on Home, experts
also see the live incoming-call card, same as Android.

**Signing with a free Personal Team?** Delete the `aps-environment` entry from
`Config/VastuTalks.entitlements`, or Xcode won't sign the app. Everything except
ringing works.

## Where the AI call files go
Each call with Ananya is saved as it happens to the app's
`Documents/AgentCalls/<call time>/` folder (transcript, board drawings, photos,
videos), which shows in the **Files** app under *On My iPhone › Vastu Talks*.
Drawings, photos and videos are also added to the photo library.

## Differences from Android
- **Ringing:** iOS can't show a full-screen call over the lock screen without
  CallKit + VoIP pushes (which FCM can't send). It shows a notification banner
  with sound instead.
- **Camera direction:** the compass maths is the same, using CoreMotion.
- **Caller's call history:** on Android the caller's history entry stores
  `call-<callId>` as the expert ID. iOS stores the expert's real uid, so the
  in-call screen shows the expert's proper profile picture.

## Changing the project
The `.xcodeproj` is generated from `project.yml` with
[XcodeGen](https://github.com/yonaskolb/XcodeGen). After editing `project.yml`,
run `xcodegen generate` in this folder. Adding new Swift files under
`VastuTalks/` also needs a regenerate (or add them in Xcode as usual).
