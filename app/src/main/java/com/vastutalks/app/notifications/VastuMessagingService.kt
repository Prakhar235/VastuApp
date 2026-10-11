package com.vastutalks.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vastutalks.app.INCOMING_CALL_CHANNEL_ID
import com.vastutalks.app.MESSAGES_CHANNEL_ID
import com.vastutalks.app.MainActivity
import com.vastutalks.app.data.repository.ChatRepository
import com.vastutalks.app.POST_ACTIVITY_CHANNEL_ID
import com.vastutalks.app.R
import com.vastutalks.app.call.IncomingCallActivity
import com.vastutalks.app.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the data-only push the "onCallRequestCreated" Cloud
 * Function sends when someone calls this device's user. Data-only
 * (no "notification" block) means Android always hands it to
 * onMessageReceived, even in the background — we build the
 * notification ourselves so we can attach a fullScreenIntent that
 * launches IncomingCallActivity over the lock screen, like a real
 * incoming call.
 *
 * Caveat noted in README: several Android OEMs (Xiaomi/MIUI, Oppo,
 * etc.) kill background apps aggressively enough that even this can
 * get suppressed unless the user manually allows the app to run in
 * the background / disables battery optimization for it. Nothing in
 * app code fully works around that.
 */
class VastuMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Fire-and-forget: this service has no natural coroutine scope,
        // and a missed token save just means the next app open retries it.
        CoroutineScope(Dispatchers.IO).launch {
            UserRepository().saveFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data["type"] == "chat") {
            val chatId = data["chatId"] ?: return
            // Already reading this chat: the message is on screen.
            if (ChatRepository.openChatId == chatId) return
            showChatNotification(chatId, data["senderUid"] ?: return, data["senderName"].orEmpty(), data["body"].orEmpty())
            return
        }
        if (data["type"] == "post_activity") {
            showPostActivityNotification(data["postId"] ?: return, data["title"].orEmpty(), data["body"].orEmpty())
            return
        }
        if (data["type"] != "incoming_call") return

        val callId = data["callId"] ?: return
        val callerName = data["callerName"] ?: "Someone"
        val channelName = data["channelName"] ?: return
        val callType = data["callType"] ?: "AUDIO"

        showIncomingCallNotification(callId, callerName, channelName, callType)
    }

    /** A chat message (sent by onChatMessageCreated in /functions); tapping it opens the chat. */
    private fun showChatNotification(chatId: String, senderUid: String, senderName: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // the unread badge in the app still shows it
        }
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_OPEN_CHAT_UID, senderUid)
            putExtra(MainActivity.EXTRA_OPEN_CHAT_NAME, senderName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val notification = NotificationCompat.Builder(this, MESSAGES_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(senderName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        // One per chat: a newer message replaces the older one.
        NotificationManagerCompat.from(this).notify("chat:$chatId".hashCode(), notification)
    }

    /** A like, comment or reply (sent by the Cloud Functions in /functions); tapping it opens the post. */
    private fun showPostActivityNotification(postId: String, title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return // still listed in the app's notifications screen
        }
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainActivity.EXTRA_OPEN_POST_ID, postId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            postId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val notification = NotificationCompat.Builder(this, POST_ACTIVITY_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        // One per post, so a burst of likes updates a single notification.
        NotificationManagerCompat.from(this).notify("post:$postId".hashCode(), notification)
    }

    private fun showIncomingCallNotification(callId: String, callerName: String, channelName: String, callType: String) {
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, callerName)
            putExtra(IncomingCallActivity.EXTRA_CHANNEL_NAME, channelName)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, callType)
        }

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, callId.hashCode(), fullScreenIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, INCOMING_CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Incoming ${if (callType == "VIDEO") "video" else "audio"} call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        // Without this permission (Android 13+, requested from HomeScreen
        // for expert accounts) the notification silently can't post —
        // there's no way to force it from a background service.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        NotificationManagerCompat.from(this).notify(callId.hashCode(), notification)
    }
}
