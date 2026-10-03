package com.vastutalks.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

const val INCOMING_CALL_CHANNEL_ID = "incoming_calls"

class VastuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createIncomingCallChannel()
    }

    /**
     * High-importance channel with the phone's default ringtone and a
     * vibration pattern — this is what makes the incoming-call
     * notification actually ring/vibrate, not just silently appear.
     * Once created, its sound/vibration can only be changed by the
     * user in system settings (Android's design, not a bug), so keep
     * this ID stable.
     */
    private fun createIncomingCallChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            INCOMING_CALL_CHANNEL_ID,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Rings when a normal user calls you"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 500, 800, 500, 800)
            setSound(ringtoneUri, audioAttributes)
            lockscreenVisibility = NotificationCompatVisibilityPublic
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}

/** android.app.Notification.VISIBILITY_PUBLIC, spelled out to avoid an extra import for one constant. */
private const val NotificationCompatVisibilityPublic = 1
