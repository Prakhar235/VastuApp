package com.vastutalks.app.ui.components

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Thin wrapper around Android's built-in TextToSpeech engine — no
 * extra dependency, no network call required (most devices have an
 * offline-capable TTS engine already installed). This gives the demo
 * "agent" an actual spoken voice during DemoInCallScreen instead of
 * just text on screen.
 *
 * Speaks whatever VastuAgent replies; [isSpeaking] lets the call
 * screen pause speech recognition so the agent doesn't hear herself.
 */
class DemoVoice(private val tts: TextToSpeech?, private val pending: PendingUtterances) {
    val isSpeaking: Boolean get() = pending.isSpeaking.value
    val isReady: Boolean get() = tts != null

    fun speak(text: String) {
        if (tts == null) return
        val id = "u${System.nanoTime()}"
        pending.add(id, text)
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) pending.remove(id)
    }

    fun stop() {
        tts?.stop()
        pending.clear()
    }
}

/**
 * Which utterances are still queued or playing. Each one also gets a
 * generous timeout, so a TTS engine that never reports "done" can't
 * leave the agent stuck "speaking" (which would keep the mic closed).
 */
class PendingUtterances {
    val isSpeaking = mutableStateOf(false)
    private val ids = mutableSetOf<String>()
    private val main = Handler(Looper.getMainLooper())

    fun add(id: String, text: String) = main.post {
        ids.add(id)
        isSpeaking.value = true
        main.postDelayed({ remove(id) }, 4_000L + text.length * 120L)
    }

    fun remove(id: String?) = main.post {
        ids.remove(id)
        isSpeaking.value = ids.isNotEmpty()
    }

    fun clear() = main.post {
        ids.clear()
        isSpeaking.value = false
    }
}

@Composable
fun rememberDemoVoice(): DemoVoice {
    val context = LocalContext.current
    val ttsHolder = remember { mutableStateOf<TextToSpeech?>(null) }
    val pending = remember { PendingUtterances() }

    DisposableEffect(Unit) {
        val instance = arrayOfNulls<TextToSpeech>(1)
        instance[0] = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val tts = instance[0]
                if (tts != null && tts.setLanguage(Locale("en", "IN")) < TextToSpeech.LANG_AVAILABLE) {
                    tts.language = Locale.US
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { pending.remove(utteranceId) }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { pending.remove(utteranceId) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { pending.remove(utteranceId) }
                })
                ttsHolder.value = instance[0]
            }
        }
        onDispose {
            instance[0]?.stop()
            instance[0]?.shutdown()
        }
    }

    return remember(ttsHolder.value) { DemoVoice(ttsHolder.value, pending) }
}
