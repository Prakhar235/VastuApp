const { onDocumentCreated, onDocumentDeleted } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const { RtcTokenBuilder, RtcRole } = require("agora-token");
const { FieldValue } = require("firebase-admin/firestore");

admin.initializeApp();
const db = admin.firestore();

/**
 * The Agora App ID isn't a secret (it's also in the app, AgoraConfig.kt);
 * keep the two in sync. The App Certificate IS a secret and lives only
 * in Secret Manager — set it once with:
 *   firebase functions:secrets:set AGORA_APP_CERTIFICATE
 */
const AGORA_APP_ID = "6245c3b5f0ce469aa17aeae3377901a9";
const AGORA_APP_CERTIFICATE = defineSecret("AGORA_APP_CERTIFICATE");

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
exports.onCallRequestCreated = onDocumentCreated("callRequests/{callId}", async (event) => {
  const call = event.data?.data();
  if (!call || !call.calleeUid) return;

  const calleeDoc = await admin.firestore().collection("users").doc(call.calleeUid).get();
  const fcmToken = calleeDoc.data()?.fcmToken;

  if (!fcmToken) {
    logger.warn(`No fcmToken for callee ${call.calleeUid} — can't ring their device.`);
    return;
  }

  const message = {
    token: fcmToken,
    data: {
      type: "incoming_call",
      callId: event.params.callId,
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
    logger.error("Failed to send incoming-call push", e);
  }
});

/**
 * generateAgoraToken — HTTPS Callable Cloud Function.
 *
 * Mints a short-lived Agora RTC token for a given channel + uid.
 * This is the ONLY place your Agora App Certificate should ever
 * live — never put it in the Android app.
 */
exports.generateAgoraToken = onCall({ secrets: [AGORA_APP_CERTIFICATE] }, (request) => {
  // Only signed-in Firebase Auth users can mint a token.
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "You must be signed in to start a call.");
  }

  const channelName = request.data?.channelName;
  const uid = Number.isInteger(request.data?.uid) ? request.data.uid : 0;

  if (!channelName || typeof channelName !== "string") {
    throw new HttpsError("invalid-argument", "channelName is required.");
  }

  const appCertificate = AGORA_APP_CERTIFICATE.value();
  if (!appCertificate) {
    throw new HttpsError(
      "failed-precondition",
      "Agora App Certificate isn't configured on the server. Run " +
        "'firebase functions:secrets:set AGORA_APP_CERTIFICATE' and redeploy."
    );
  }

  const expirationTimeInSeconds = Math.floor(Date.now() / 1000) + TOKEN_EXPIRATION_SECONDS;

  const token = RtcTokenBuilder.buildTokenWithUid(
    AGORA_APP_ID,
    appCertificate,
    channelName,
    uid,
    RtcRole.PUBLISHER,
    expirationTimeInSeconds,
    expirationTimeInSeconds
  );

  return { token, channelName, uid, expiresAt: expirationTimeInSeconds };
});

// ---------------------------------------------------------------------------
// Post likes and comments
//
// Likes live at users/{uid}/likes/{postId} (so each user's app can watch
// its own likes with one listener) and comments at
// posts/{postId}/comments/{commentId}. Clients never write a post's
// likeCount/commentCount themselves — these triggers keep them right —
// and the triggers also notify people:
//   - a like or comment on a post   -> the post's author
//   - an expert replying to a comment -> that comment's author
// Each notification is saved to users/{uid}/notifications (the in-app
// list behind the bell) and pushed to their phone.
// ---------------------------------------------------------------------------

/** Name and photo of a user, for the notification. */
async function profileOf(uid) {
  const data = (await db.collection("users").doc(uid).get()).data() || {};
  return { name: data.name || "Someone", photo: data.photo || "", fcmToken: data.fcmToken };
}

function snippet(text, max = 80) {
  const t = (text || "").replace(/\s+/g, " ").trim();
  return t.length > max ? t.slice(0, max - 1) + "…" : t;
}

/**
 * Saves a notification for [toUid] and pushes it to their phone. [type] is
 * "like", "comment" or "reply"; [body] is the line shown under the title.
 */
async function notify(toUid, { type, postId, actor, title, body }) {
  await db.collection("users").doc(toUid).collection("notifications").add({
    type,
    postId,
    actorName: actor.name,
    actorPhoto: actor.photo,
    title,
    body,
    createdAtMillis: Date.now(),
    read: false,
  });

  const { fcmToken } = await profileOf(toUid);
  if (!fcmToken) return;
  try {
    // Data-only, like the incoming-call push: the app builds the
    // notification itself so tapping it can open the post.
    await admin.messaging().send({
      token: fcmToken,
      data: { type: "post_activity", postId, title, body },
      android: { priority: "high" },
    });
  } catch (e) {
    logger.warn(`Couldn't push ${type} notification to ${toUid}`, e);
  }
}

/**
 * Copies the post's newest few comments onto the post (recentComments),
 * so feeds can show them under each post without loading every thread.
 * Photos are left out to keep the post small.
 */
const RECENT_COMMENTS = 3;
async function refreshRecentComments(postRef) {
  const snap = await postRef.collection("comments").orderBy("createdAtMillis", "desc").limit(RECENT_COMMENTS).get();
  const recent = snap.docs.reverse().map((d) => {
    const c = d.data();
    return {
      id: d.id,
      authorName: c.authorName || "",
      authorIsExpert: !!c.authorIsExpert,
      text: snippet(c.text, 200),
      replyToName: c.replyToName || "",
      createdAtMillis: c.createdAtMillis || 0,
    };
  });
  await postRef.update({ recentComments: recent });
}

exports.onPostLiked = onDocumentCreated("users/{uid}/likes/{postId}", async (event) => {
  const { uid, postId } = event.params;
  const postRef = db.collection("posts").doc(postId);
  const post = (await postRef.get()).data();
  if (!post) return;
  await postRef.update({ likeCount: FieldValue.increment(1) });

  if (post.authorUid === uid) return; // liking your own post
  const actor = await profileOf(uid);
  await notify(post.authorUid, {
    type: "like",
    postId,
    actor,
    title: `${actor.name} liked your post`,
    body: snippet(post.text),
  });
});

exports.onPostUnliked = onDocumentDeleted("users/{uid}/likes/{postId}", async (event) => {
  const postRef = db.collection("posts").doc(event.params.postId);
  if (!(await postRef.get()).exists) return;
  await postRef.update({ likeCount: FieldValue.increment(-1) });
});

exports.onCommentCreated = onDocumentCreated("posts/{postId}/comments/{commentId}", async (event) => {
  const comment = event.data?.data();
  if (!comment) return;
  const { postId } = event.params;
  const postRef = db.collection("posts").doc(postId);
  const post = (await postRef.get()).data();
  if (!post) return;
  await postRef.update({ commentCount: FieldValue.increment(1) });
  await refreshRecentComments(postRef);

  const actor = { name: comment.authorName || "Someone", photo: comment.authorPhoto || "" };
  const notified = new Set([comment.authorUid]); // never notify yourself, or anyone twice

  // An expert answering someone's comment: tell that person. replyToUid is
  // whoever they tapped "Reply" on (replies are one level deep, so that may
  // be a follow-up in the thread rather than its first comment).
  if (comment.replyToCommentId && comment.replyToUid) {
    const fromExpert = (await db.collection("experts").doc(comment.authorUid).get()).exists;
    if (fromExpert && !notified.has(comment.replyToUid)) {
      notified.add(comment.replyToUid);
      await notify(comment.replyToUid, {
        type: "reply",
        postId,
        actor,
        title: `${actor.name} replied to your comment`,
        body: snippet(comment.text),
      });
    }
  }

  // Any comment or reply on a post: tell the post's author.
  if (!notified.has(post.authorUid)) {
    await notify(post.authorUid, {
      type: "comment",
      postId,
      actor,
      title: `${actor.name} commented on your post`,
      body: snippet(comment.text),
    });
  }
});

exports.onCommentDeleted = onDocumentDeleted("posts/{postId}/comments/{commentId}", async (event) => {
  const postRef = db.collection("posts").doc(event.params.postId);
  if (!(await postRef.get()).exists) return;
  await postRef.update({ commentCount: FieldValue.increment(-1) });
  await refreshRecentComments(postRef);
});

// ---------------------------------------------------------------------------
// Chats between users and experts: chats/{chatId}/messages/{messageId},
// where chatId is the two uids sorted and joined with "_". The sender's app
// writes the message and the chat's last-message fields; this keeps the
// recipient's unread count and pushes them a notification.
// ---------------------------------------------------------------------------

exports.onChatMessageCreated = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
  const message = event.data?.data();
  if (!message) return;
  const { chatId } = event.params;
  const recipient = chatId.split("_").find((uid) => uid !== message.senderUid);
  if (!recipient) return;

  const chatRef = db.collection("chats").doc(chatId);
  await chatRef.set({ unread: { [recipient]: FieldValue.increment(1) } }, { merge: true });

  const chat = (await chatRef.get()).data() || {};
  const senderName = chat.names?.[message.senderUid] || "New message";
  const { fcmToken } = await profileOf(recipient);
  if (!fcmToken) return;
  try {
    await admin.messaging().send({
      token: fcmToken,
      data: {
        type: "chat",
        chatId,
        senderUid: message.senderUid,
        senderName,
        body: message.text ? snippet(message.text, 120) : "📷 Photo",
      },
      android: { priority: "high" },
    });
  } catch (e) {
    logger.warn(`Couldn't push chat message to ${recipient}`, e);
  }
});
