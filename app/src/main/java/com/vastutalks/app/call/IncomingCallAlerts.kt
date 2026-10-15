package com.vastutalks.app.call

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationManagerCompat

/**
 * Makes sure an incoming call rings in one place only. While the expert's
 * Home is on screen it shows the call itself (and rings), so the push
 * notification is skipped, or cancelled if it got there first; anywhere
 * else the push notification (and IncomingCallActivity) does the ringing.
 */
object IncomingCallAlerts {
    /** True while the expert's Home is on screen and showing incoming calls itself. */
    @Volatile
    var inAppRingerVisible = false

    /** Notification id for a call, shared with VastuMessagingService. */
    fun notificationId(callId: String) = callId.hashCode()

    fun cancelNotification(context: Context, callId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(callId))
    }

    private var ringtone: Ringtone? = null

    /** Rings and vibrates (looping) for the in-app incoming call card. */
    fun startRinging(context: Context) {
        if (ringtone?.isPlaying == true) return
        val app = context.applicationContext
        ringtone = RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
        vibrator(app)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 500, 800, 500), 0))
    }

    fun stopRinging(context: Context) {
        ringtone?.stop()
        ringtone = null
        vibrator(context.applicationContext)?.cancel()
    }

    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
}
