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
 * offline-capable TTS engine already installed). This gives the AI
 * agent Ananya an actual spoken voice during AgentInCallScreen instead of
 * just text on screen.
 *
 * Speaks whatever VastuAgent replies; [isSpeaking] lets the call
 * screen pause speech recognition so the agent doesn't hear herself.
 */
class AgentVoice(private val tts: TextToSpeech?, private val pending: PendingUtterances) {
    val isSpeaking: Boolean get() = pending.isSpeaking.value
    val isReady: Boolean get() = tts != null

    fun speak(text: String) {
        if (tts == null) return
        // Hindi replies are in Devanagari, which the English voice can't read.
        val isHindi = text.any { it in 'ऀ'..'ॿ' }
        val hindiResult = if (isHindi) tts.setLanguage(HINDI) else null
        if (hindiResult == null || hindiResult < TextToSpeech.LANG_AVAILABLE) useEnglish(tts)
        android.util.Log.d("AgentVoice", "speak hindi=$isHindi setLanguage(hi)=$hindiResult engine=${tts.defaultEngine}")
        val id = "u${System.nanoTime()}"
        pending.add(id, text)
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) pending.remove(id)
        else pending.watch(tts)
    }

    fun stop() {
        tts?.stop()
        pending.clear()
    }
}

private val HINDI = Locale("hi", "IN")
private const val GOOGLE_TTS = "com.google.android.tts"

/** Indian English, or US English on devices without it. */
private fun useEnglish(tts: TextToSpeech) {
    if (tts.setLanguage(Locale("en", "IN")) < TextToSpeech.LANG_AVAILABLE) tts.language = Locale.US
}

/**
 * Which utterances are still queued or playing. Some engines (Samsung's)
 * don't reliably report "done", which kept the mic closed for up to a
 * minute after a long reply — so the engine is also polled, and each
 * utterance has a timeout as a last resort.
 */
class PendingUtterances {
    val isSpeaking = mutableStateOf(false)
    private val ids = mutableSetOf<String>()
    private val main = Handler(Looper.getMainLooper())

    fun add(id: String, text: String) = main.post {
        ids.add(id)
        isSpeaking.value = true
        main.postDelayed({ remove(id) }, 3_000L + text.length * 90L)
    }

    private var watching = false

    /** Marks speech finished once the engine has been quiet for two checks in a row. */
    fun watch(tts: TextToSpeech) = main.post {
        if (watching) return@post
        watching = true
        var quietChecks = 0
        main.postDelayed(object : Runnable {
            override fun run() {
                if (!isSpeaking.value) { watching = false; return }
                quietChecks = if (tts.isSpeaking) 0 else quietChecks + 1
                if (quietChecks >= 2) {
                    android.util.Log.d("AgentVoice", "Engine went quiet without reporting done")
                    ids.clear()
                    isSpeaking.value = false
                    watching = false
                } else main.postDelayed(this, 250)
            }
        }, 1_000) // give the engine time to start
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
fun rememberAgentVoice(): AgentVoice {
    val context = LocalContext.current
    val ttsHolder = remember { mutableStateOf<TextToSpeech?>(null) }
    val pending = remember { PendingUtterances() }

    DisposableEffect(Unit) {
        val instance = arrayOfNulls<TextToSpeech>(1)
        // Google's engine has Hindi voices; some phones' default engines don't
        // (Samsung's has none, so Hindi replies came out silent). Android falls
        // back to the default engine when Google's isn't installed.
        instance[0] = TextToSpeech(context.applicationContext, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val tts = instance[0]
                tts?.let(::useEnglish)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        android.util.Log.d("AgentVoice", "done $utteranceId")
                        pending.remove(utteranceId)
                    }
                    override fun onStop(utteranceId: String?, interrupted: Boolean) { pending.remove(utteranceId) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { pending.remove(utteranceId) }
                })
                ttsHolder.value = instance[0]
            }
        }, GOOGLE_TTS)
        onDispose {
            instance[0]?.stop()
            instance[0]?.shutdown()
        }
    }

    return remember(ttsHolder.value) { AgentVoice(ttsHolder.value, pending) }
}
