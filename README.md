# Vastu Talks (Android)

A Jetpack Compose Android app for connecting users with Vastu Shastra
experts over video or audio call.

## Tech stack
- Kotlin
- Jetpack Compose (Material 3)
- Navigation Compose (single-activity, all screens are composables)
- Coil (image loading — wired in for when real expert photos arrive)

## How to open
1. Open this folder directly in Android Studio (Iguana or newer recommended).
2. Let Gradle sync. Android Studio will regenerate `gradle-wrapper.jar`
   automatically the first time you sync — it's intentionally not
   checked in here since it's a binary and this environment has no
   network access to fetch it.
3. Run the `app` configuration on an emulator or device (minSdk 24,
   so anything from Android 7.0 up).

### If you hit version-mismatch errors
This project pins AGP 8.6.1, Kotlin 2.0.21 (with the
`org.jetbrains.kotlin.plugin.compose` plugin for the Compose
compiler), Gradle 8.9, and Compose BOM 2025.09.00 — all mutually
compatible as of writing. If Android Studio still reports a
metadata/version mismatch, it's almost always because Android
Studio's bundled AGP/Kotlin plugin or a global Gradle cache resolved
something newer than what's pinned here. The fix is the same either
way: bump the plugin versions in the root `build.gradle.kts` (and the
Compose BOM / core-ktx in `app/build.gradle.kts`) to match whatever
your IDE suggests, then sync again.

## Screen flow
```
Splash
  -> Onboarding (first launch only, signed-out users)
       -> Sign In
            -> Create Account (role picker: Normal User / Vastu Expert)
       -> Home
            Normal User: live expert directory -> Expert Profile -> Chat/Video Call
            Vastu Expert: incoming-call dashboard (Accept/Decline)
            -> Profile (tap the menu icon) -> call History tab, Log Out
```

## What's real vs. stubbed

**Real / fully wired:**
- All navigation between every screen
- Firebase Authentication (email/password) for Sign In and Create Account
- Role-based signup (Normal User / Vastu Expert) backed by Firestore
- A live expert directory (Firestore `experts` collection), not sample data
- Real Agora video/audio calling with a live signaling layer (Firestore `callRequests`)
- Real-time incoming-call notifications for experts (Firestore listener; push notifications need the Cloud Function — see below)
- Call history logged to Firestore and shown on the Profile screen
- Call duration timer and live cost calculation during a call

**Intentionally stubbed:**
- The **OTP tab** on Sign In — UI-only, not wired to phone/email OTP.
  Only the Password tab actually signs in. See "Adding phone OTP
  later" below if you want to add it.
- "Forgot Password" — still a TODO in `VastuNavGraph`.
- Chat Now (on Expert Profile and in-call) — no chat feature exists,
  it's a no-op placeholder.
- Real-time push notifications for incoming calls — the expert sees
  the Accept/Decline card live via a Firestore listener, but only if
  their app is already open on Home. Actual push (ringing on a locked
  screen) needs the `onCallRequestCreated` Cloud Function, which
  needs Firebase's Blaze plan — see "Token reuse + real ringing" below.
- Wallet/credits balance on Profile and the live per-second cost
  shown during a call — both are simulated from a hardcoded
  per-expert rate, not backed by a real ledger.
- Notifications, Settings, Payment Methods, and Transaction History
  rows on Profile — visible in the UI but not wired to anything yet.

## Wiring up Firebase Auth

Sign In (Password tab) and Create Account call real Firebase
Authentication (`AuthRepository.kt` / `AuthViewModel.kt`), but the
project needs your own Firebase config before it'll build and run:

1. In the [Firebase Console](https://console.firebase.google.com/),
   create a project (or use an existing one) and add an Android app
   with package name `com.vastutalks.app`.
2. In **Authentication -> Sign-in method**, enable the **Email/Password**
   provider.
3. Download the generated `google-services.json` from the Firebase
   Console and place it at `app/google-services.json` (sitting next
   to `app/build.gradle.kts`). A `app/google-services.json.SAMPLE` is
   included so you can see the shape of the file — it is **not** a
   real config and the build will fail (with a clear message) until
   you replace it with your own.
4. Sync Gradle and run. Without step 3, Gradle fails immediately at
   the `google-services` plugin step, so you'll know right away if
   it's missing.

What's wired:
- **Create Account** -> `FirebaseAuth.createUserWithEmailAndPassword`,
  with the full name saved as the Firebase user's display name, plus
  a role (Normal User / Vastu Expert) written to Firestore.
- **Sign In** (Password tab) -> `FirebaseAuth.signInWithEmailAndPassword`.
- **Session persistence** — `VastuNavGraph`'s splash step checks
  `FirebaseAuth.getInstance().currentUser` and skips Sign In if a
  session already exists.
- **Sign out** — a "Log Out" row on the Profile screen calls
  `FirebaseAuth.signOut()` and returns to Sign In.
- Firebase error codes (wrong password, unknown user, email already
  in use, weak password, etc.) are mapped to short user-facing
  strings in `AuthRepository.mapAuthError` and shown under the form.

### Adding phone OTP later
If you want the OTP tab to be real:
1. Enable **Phone** as a sign-in provider in Firebase Console.
2. Add your debug and release keystore **SHA-1/SHA-256** fingerprints
   under the Android app's settings in Firebase Console (phone auth
   won't work without this).
3. Use `PhoneAuthProvider.verifyPhoneNumber(...)` to send the code,
   and `PhoneAuthProvider.getCredential(verificationId, code)` +
   `FirebaseAuth.signInWithCredential(...)` in a new verification
   screen to confirm it.
4. Test on a real device or an emulator with Google Play services —
   phone auth's auto-verification/reCAPTCHA fallback is unreliable
   on plain AVDs without Play Services.

### Adding email/password reset
`onForgotPassword` in `VastuNavGraph` is a no-op — wire it to
`FirebaseAuth.sendPasswordResetEmail(email)` and a small confirmation
screen/dialog when you're ready.

## Wiring up Agora calling

Real calls (`LiveCallingScreen` -> `LiveInCallScreen`, using
`AgoraCallManager.kt`) need your own Agora project before anything
connects:

1. Create a project at [console.agora.io](https://console.agora.io/)
   (the free tier is plenty for testing).
2. Copy the **App ID** from the project and paste it into
   `AgoraConfig.kt` (`app/src/main/java/com/vastutalks/app/data/call/AgoraConfig.kt`),
   replacing the placeholder.
3. Tokens (required for joining a channel) are handled by the
   `generateAgoraToken` Cloud Function, or by hand via
   `AgoraConfig.HARDCODED_EXPERT_TOKENS` for local testing — see
   "Wiring up roles, the live expert directory, and real-time
   calling" and "Token reuse + real ringing" below for the full
   walkthrough, since token handling is tied to the real signup/role
   flow, not a fixed test channel.

What's wired: real mic/camera capture, real channel join, real local
+ remote video (`LocalVideoView`/`RemoteVideoView` in
`ui/components/AgoraVideoView.kt`), mute/camera-off/switch-camera/
speaker controls, and cleanup (`leaveAndRelease()`) on End Call or
back-out.

What's not wired: mid-call token refresh (`onTokenPrivilegeWillExpire`)
— fine for short test calls, but a production app would want it for
longer sessions.

## Wiring up Profile & call history

There's a new **Profile** screen (tap the person icon on Home's top
bar) showing:
- **Credential data from Firebase Auth** — display name, email, and
  "member since" date, read from `FirebaseAuth.currentUser`.
- **Call history from Firestore** — every call now gets logged when
  it ends, with the expert, call type, real duration, real cost, and
  the actual time the call happened.

This needs Cloud Firestore enabled on your Firebase project (a
separate step from Authentication):

1. In the [Firebase Console](https://console.firebase.google.com/),
   open your project -> **Firestore Database** -> **Create database**.
   Any region/mode is fine for testing (start in test mode, or set
   the rules below directly).
2. Under **Rules**, replace the default with something that scopes
   each user to their own history:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{userId}/callHistory/{callId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```
   Without this, Firestore's default rules either block everything
   (test mode after it expires) or allow anyone to read/write
   anything (open test mode) — neither is what you want long-term.
3. Rebuild and run. No other config needed — no separate "Firestore ID"
   or anything, it rides on the same `google-services.json`/App ID
   you already have.

What's wired:
- **`CallHistoryEntry.kt`** — the record shape: expert, call type,
  start time (`startTimeMillis`), duration, cost, channel name.
- **`CallHistoryRepository.kt`** — writes to and reads from
  `users/{uid}/callHistory/{autoId}` in Firestore.
- **`LiveInCallScreen`** reports real elapsed seconds, real cost, and
  a real start timestamp through `onEndCall(...)`.
- **`VastuNavGraph`** logs the call to Firestore the moment `onEndCall`
  fires, using a coroutine scope tied to the NavHost itself (not the
  in-call screen) so the write isn't cut short by navigating away.
- **`ProfileScreen`/`ProfileViewModel`** load that history back out,
  newest first, and show it alongside the signed-in user's Firebase
  profile info.

What's not wired:
- **Editing profile info** (changing name/email) — read-only for now.
- **Retrying a failed history write** — if the Firestore write fails
  (e.g. no network right as the call ends), it's silently dropped;
  the call summary/rating flow still works either way. A production
  build would want to queue and retry this.

## Wiring up roles, the live expert directory, and real-time calling

This is the biggest addition: **Create Account** now has an "I am a..."
toggle (Normal User / Vastu Expert). What happens next depends on
which one you pick:

- **Normal User** → lands on Home with a live expert directory
  section, populated from Firestore's `experts` collection (real
  signed-up experts only — there's no sample/demo data anymore).
- **Vastu Expert** → Home shows an empty dashboard state instead of
  any expert list ("You're listed as a Vastu Expert...") and a live
  Firestore listener waits for incoming calls, which appear as an
  Accept/Decline card right on that screen the instant a normal user
  calls them — no push notifications needed, no polling.

### New Firestore collections
- `users/{uid}` — role, name, email (written at signup).
- `experts/{uid}` — created only for Vastu Expert signups; this is
  what the live directory queries (`isOnline == true`).
- `callRequests/{callId}` — the signaling channel: caller writes one
  with `status: RINGING`; callee's listener picks it up; accept/decline
  updates `status`, which the caller is also watching live.

**Set up Firestore rules** (if you haven't already for call history):
copy `firestore.rules` from the project root into Firestore Console →
Rules, or deploy it with `firebase deploy --only firestore:rules`
once you've run `firebase use --add` (see below). It covers `users`,
`experts`, and `callRequests` with per-user/per-participant access.

### Deploying the token Cloud Function

Real calls need a fresh Agora token per call, minted server-side (see
the "why" in the chat where this was built — short version: a token
generated once can't be safely reused, and your Agora **App
Certificate** must never live in the Android app). This is a
Cloud Function at `/functions`, using Agora's official `agora-token`
npm package.

1. **Enable Blaze (pay-as-you-go) billing** on your Firebase project
   — Cloud Functions require it. There's a generous free tier; a
   handful of test calls costs nothing.
2. Install the Firebase CLI if you don't have it: `npm install -g firebase-tools`, then `firebase login`.
3. From the project root: `firebase use --add` and pick your Firebase
   project (the same one as `google-services.json`).
4. Set your Agora credentials as function config — **App
   Certificate**, not App ID alone, from the same Agora Console page
   you got your App ID from:
   ```
   firebase functions:config:set agora.app_id="YOUR_AGORA_APP_ID" agora.app_certificate="YOUR_AGORA_APP_CERTIFICATE"
   ```
   (If your `firebase-tools` version has deprecated `functions:config`
   in favor of Secret Manager, `functions/index.js` has the swapped-in
   code for that as a comment at the bottom of the file.)
5. Install dependencies and deploy:
   ```
   cd functions
   npm install
   cd ..
   firebase deploy --only functions
   ```
6. Rebuild and run the Android app — `TokenRepository.kt` calls this
   function by name (`generateAgoraToken`), so nothing else needs
   configuring on the app side.

If this function isn't deployed yet, real calls will fail at the
"Connecting…" step with a token-fetch error — that's expected and
tells you deployment is the next step, not a code bug.

### Manually adding a token (no Cloud Function / no Blaze plan)

If you don't want to enable Firebase's Blaze plan, you can skip the
Cloud Function entirely and paste tokens in by hand instead. Roles
and the live expert directory work exactly the same either way — the
only difference is you regenerate a token yourself roughly once a
day per expert, instead of the app doing it automatically.

1. **Find the expert's uid.** Firebase Console → Firestore Database →
   `experts` collection — the document ID *is* the expert's uid.
2. **Generate a temp token in Agora Console** for channel name
   `expert-<that-uid>` (e.g. `expert-AbCd1234...`) — exactly like you
   did for testing earlier, just with this specific channel name.
3. **Add it to Firestore.** Firebase Console → Firestore Database →
   start a new collection called `expertTokens` (if it doesn't exist
   yet) → new document, and set the **Document ID** to the expert's
   uid (same one from step 1). Add three fields:
   - `token` (string) — the temp token you just generated
   - `channelName` (string) — `expert-<that-uid>`, matching exactly
   - `expiresAtMillis` (number) — any large number like
     `9999999999999` is fine here (this only controls when *our app*
     bothers checking for a fresh one, not when Agora actually
     expires it — see the next point)
4. **It'll still stop working after ~24h** — that's Agora enforcing
   the token's real expiry, regardless of what you put in
   `expiresAtMillis`. When a call fails with an "invalid or expired
   token" error, just repeat steps 2–3 with a fresh token.

Both the caller and the callee read this same document (uid=0
wildcard tokens work for anyone joining that channel), so one token
covers everyone calling that expert, not just one caller.

**Even simpler — hardcode it in the app instead of Firestore.** If
you're already rebuilding via Android Studio anyway, skip the
Firestore Console step entirely: open `AgoraConfig.kt`
(`app/src/main/java/com/vastutalks/app/data/call/AgoraConfig.kt`) and
add an entry to `HARDCODED_EXPERT_TOKENS`:
```kotlin
val HARDCODED_EXPERT_TOKENS: Map<String, String> = mapOf(
    "AbCd1234exampleUid" to "007eJx..."
)
```
Same steps 1–2 above to get the uid and generate the token — just
paste here instead of into Firestore. `TokenRepository` checks this
map first, before anything else. Same 24h-expiry caveat applies:
when it stops working, generate a fresh token, update this map,
rebuild.

Real-time "ringing" (the full-screen notification / actual phone
ring) still requires the `onCallRequestCreated` Cloud Function, which
needs Blaze — without it, an expert only sees an incoming call if
they already have the app open on Home (the live Firestore listener
there works fine on the free plan; it's just not a background push).

### Testing the whole flow
1. Deploy the Cloud Function (above) and set Firestore rules.
2. Sign up **two** accounts (two emulators, or one emulator + a real
   device) — one as **Vastu Expert**, one as **Normal User**.
3. On the expert account, just stay on Home (that's the "waiting for
   calls" screen).
4. On the normal-user account, the expert should already appear under
   **Live experts** — tap the video or audio icon on their card.
5. The expert's screen should show an incoming-call card within a
   second or two (that's the Firestore listener firing) — tap Accept.
6. Both sides should connect into the same Agora channel and you can
   test audio/video, mute, camera switch, and End Call.

### What's not wired
- **Expert bios/pricing/specialties** — real expert signups only
  capture name/email right now, unlike the rich sample data. Add
  fields to `ExpertProfile.kt` and the signup flow if you want that.
- **Going offline** — there's no toggle for an expert to mark
  themselves unavailable; `isOnline` is set to `true` at signup and
  never changed. A real app would flip it on sign-out/app-background.
- **Missed-call handling** — if the caller cancels or the callee
  never responds, the `callRequests` doc just sits at `RINGING`
  forever with no timeout/cleanup. Worth adding a Cloud Function or
  client-side timeout for a production build.

## Token reuse + real ringing (FCM)

Two follow-up changes on top of the roles/live-calling feature above:

### Tokens now last ~23h and get reused
Each expert's calls all use one fixed channel (`expert-<uid>`) instead
of a new random channel per call — that's what makes a single token
reusable across a whole day of calls instead of one-per-call. The
Cloud Function now issues tokens valid ~23h, and `TokenRepository.fetchOrCreateExpertToken`
caches the result in Firestore's new `expertTokens/{expertUid}`
collection; both the caller and the callee read/write that same
cache, so most calls don't hit the Cloud Function at all after the
first one each day. (`firestore.rules` was updated for this new
collection — redeploy rules if you already deployed before this change.)

Trade-off: since a channel is now tied to the expert rather than the
call, only one call can be active on an expert's channel at a time —
which matches "an expert only takes one call at once" anyway, so this
isn't really a new limitation in practice.

### Real ringing via Firebase Cloud Messaging
Previously "incoming call" only worked if the expert happened to have
the app open on Home. Now:
- Every signed-in device saves its FCM token to `users/{uid}.fcmToken`
  (`AuthViewModel` after sign-in/sign-up, `VastuMessagingService.onNewToken`
  on token rotation).
- A new Cloud Function, `onCallRequestCreated`, fires the instant a
  `callRequests` document is created and sends a high-priority,
  data-only push to the callee's device.
- `VastuMessagingService` receives that push (even in the background)
  and posts a full-screen notification — on a locked/backgrounded
  device this launches `IncomingCallActivity` right over the lock
  screen, plays the phone's default ringtone, and vibrates (that's
  what the new `incoming_calls` notification channel in
  `VastuApplication.kt` configures).
- Accepting from that screen joins the Agora channel immediately, then
  hands off to `MainActivity` (via `CallDeepLink`) which lands
  straight on the in-call screen — no detour through Home.

**Setup needed:**
1. Redeploy the Cloud Function — it now exports two functions, not
   one: `firebase deploy --only functions`.
2. Redeploy Firestore rules (the new `expertTokens` collection):
   `firebase deploy --only firestore:rules`.
3. Nothing else — `firebase-messaging-ktx` and the notification
   permission request are already wired in; the app asks for
   `POST_NOTIFICATIONS` the moment a Vastu Expert account lands on
   Home.

**Known limitations, being upfront about them:**
- **Aggressive OEM battery management** (MIUI/Xiaomi, ColorOS/Oppo,
  some Samsung modes, etc.) can suppress background FCM delivery
  entirely unless the user manually disables battery optimization or
  "auto-start" restrictions for the app. This is an OS/OEM-level
  restriction, not something fixable from app code — worth testing on
  the actual devices you care about.
- **App fully force-stopped** (not just backgrounded) — Android
  generally won't deliver FCM messages to a force-stopped app at all
  until it's opened again. Backgrounded (not force-stopped) is fine.
- **No missed-call/voicemail flow** — if the push doesn't arrive or
  the expert doesn't answer, the caller just sees "declined" once
  their own call-status listener eventually reads a stale/unaccepted
  state, or waits indefinitely if nothing times it out. A production
  app would want a timeout that auto-marks the call as missed.

## Design notes
- Colors live in `ui/theme/Color.kt`. The purple gradient
  (`AuthGradient`) is taken directly from your two reference screens.
  Copper/saffron accents (`VastuCopper`, `VastuSaffron`) are used past
  the auth flow — on the home screen's direction chips and the
  expert-profile pricing rows — so the app reads as a Vastu product
  rather than generic fintech once you're past sign-in.
- Fonts currently fall back to the system default. To match the exact
  look of your screenshots, drop `.ttf` files into
  `app/src/main/res/font/` and point `displayFontFamily` /
  `bodyFontFamily` in `ui/theme/Type.kt` at them.
- The home screen's "Browse by Vastu zone" strip (N/NE/E/SE/S/SW/W/NW)
  is a deliberate structural choice, not decoration — Vastu zones are
  the actual content categories experts specialize in.

## Known gaps to fill before shipping
- No backend: auth, expert data, call billing, and ratings are all
  client-side/in-memory for this prototype.
- No persisted session — closing the app returns to Sign In.
- No real payments/wallet — pricing is shown but nothing is charged.
- No push notifications for incoming calls.
