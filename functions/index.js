const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { RtcTokenBuilder, RtcRole } = require("agora-token");

admin.initializeApp();

const TOKEN_EXPIRATION_SECONDS = 23 * 60 * 60; // ~23h — just under Agora's 24h hard cap, cached and reused client-side

/**
 * onCallRequestCreated — Firestore trigger.
 *
 * Fires the instant a caller writes a new document to callRequests/.
 * Looks up the callee's saved FCM device token (set by the Android
 * app's VastuMessagingService.onNewToken) and sends a high-priority,
 * data-only push. Data-only means Android hands it straight to
 * onMessageReceived even in the background, which is what lets the
 * app build its own full-screen "ringing" notification instead of a
 * generic banner.
 */
exports.onCallRequestCreated = functions.firestore
  .document("callRequests/{callId}")
  .onCreate(async (snapshot, context) => {
    const call = snapshot.data();
    if (!call || !call.calleeUid) return null;

    const calleeDoc = await admin.firestore().collection("users").doc(call.calleeUid).get();
    const fcmToken = calleeDoc.data()?.fcmToken;

    if (!fcmToken) {
      functions.logger.warn(`No fcmToken for callee ${call.calleeUid} — can't ring their device.`);
      return null;
    }

    const message = {
      token: fcmToken,
      data: {
        type: "incoming_call",
        callId: context.params.callId,
        callerName: call.callerName || "Someone",
        channelName: call.channelName || "",
        callType: call.callType || "AUDIO",
      },
      android: {
        priority: "high",
      },
      apns: {
        headers: {
          "apns-priority": "10",
        },
      },
    };

    try {
      await admin.messaging().send(message);
    } catch (e) {
      functions.logger.error("Failed to send incoming-call push", e);
    }
    return null;
  });

/**
 * generateAgoraToken — HTTPS Callable Cloud Function.
 *
 * Mints a short-lived Agora RTC token for a given channel + uid.
 * This is the ONLY place your Agora App Certificate should ever
 * live — never put it in the Android app.
 *
 * Deploy: see the "Deploying the token Cloud Function" section in
 * README.md. Requires the Firebase Blaze (pay-as-you-go) plan.
 *
 * Before deploying, set your Agora credentials as function config:
 *   firebase functions:config:set agora.app_id="YOUR_APP_ID" agora.app_certificate="YOUR_APP_CERTIFICATE"
 * (Or, on newer firebase-tools, use `firebase functions:secrets:set` —
 * see the comment near the bottom of this file for that variant.)
 */
exports.generateAgoraToken = functions.https.onCall((data, context) => {
  // Only signed-in Firebase Auth users can mint a token.
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "You must be signed in to start a call."
    );
  }

  const channelName = data.channelName;
  const uid = Number.isInteger(data.uid) ? data.uid : 0;

  if (!channelName || typeof channelName !== "string") {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "channelName is required."
    );
  }

  const appId = functions.config().agora?.app_id;
  const appCertificate = functions.config().agora?.app_certificate;

  if (!appId || !appCertificate) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Agora credentials aren't configured on the server. Run " +
        "'firebase functions:config:set agora.app_id=... agora.app_certificate=...' " +
        "and redeploy."
    );
  }

  const expirationTimeInSeconds = Math.floor(Date.now() / 1000) + TOKEN_EXPIRATION_SECONDS;

  const token = RtcTokenBuilder.buildTokenWithUid(
    appId,
    appCertificate,
    channelName,
    uid,
    RtcRole.PUBLISHER,
    expirationTimeInSeconds,
    expirationTimeInSeconds
  );

  return { token, channelName, uid, expiresAt: expirationTimeInSeconds };
});

/**
 * If your firebase-tools version has deprecated `functions:config`
 * in favor of Secret Manager, swap the credential lookup above for:
 *
 *   const { defineSecret } = require("firebase-functions/params");
 *   const AGORA_APP_ID = defineSecret("AGORA_APP_ID");
 *   const AGORA_APP_CERTIFICATE = defineSecret("AGORA_APP_CERTIFICATE");
 *
 *   exports.generateAgoraToken = functions
 *     .runWith({ secrets: [AGORA_APP_ID, AGORA_APP_CERTIFICATE] })
 *     .https.onCall((data, context) => {
 *       const appId = AGORA_APP_ID.value();
 *       const appCertificate = AGORA_APP_CERTIFICATE.value();
 *       // ...rest identical
 *     });
 *
 * and set secrets with:
 *   firebase functions:secrets:set AGORA_APP_ID
 *   firebase functions:secrets:set AGORA_APP_CERTIFICATE
 */
