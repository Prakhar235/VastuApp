package com.vastutalks.app.ui.components

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

private const val SAMPLE_RATE = 16_000
private const val FRAME_SAMPLES = 480 // 30 ms
private const val START_FRAMES = 3 // ~90 ms of loud audio = caller started talking
private const val END_SILENCE_FRAMES = 37 // ~1.1 s of quiet = caller finished
private const val PRE_ROLL_FRAMES = 10 // keep ~300 ms before speech so the first word isn't clipped
private const val MIN_SPEECH_FRAMES = 12 // ignore blips shorter than ~360 ms
private const val MAX_SPEECH_FRAMES = 1000 // cap one utterance at ~30 s

/**
 * The AI agent's ears. While [rememberCallerListener]'s `active` is
 * true it keeps the mic open and waits — for as long as it takes — for
 * the caller to start talking, then records until they pause and hands
 * the utterance over as a 16 kHz mono WAV (for VastuAgent.transcribe).
 *
 * Uses AudioRecord with a simple adaptive-noise-floor voice detector
 * instead of Android's SpeechRecognizer, which times out after a few
 * seconds of silence and on some devices (e.g. Samsung) fails to
 * restart afterwards.
 */
class CallerListener internal constructor() {
    /** Mic is open and waiting for speech. */
    var isListening by mutableStateOf(false)
        internal set

    /** Caller is talking right now. */
    var isHearingSpeech by mutableStateOf(false)
        internal set
}

@Composable
fun rememberCallerListener(active: Boolean, onUtterance: (ByteArray) -> Unit): CallerListener {
    val listener = remember { CallerListener() }
    val currentOnUtterance by rememberUpdatedState(onUtterance)

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        listener.isListening = true
        try {
            val wav = withContext(Dispatchers.IO) { captureUtterance { listener.isHearingSpeech = it } }
            if (wav != null) currentOnUtterance(wav)
        } finally {
            listener.isListening = false
            listener.isHearingSpeech = false
        }
    }
    return listener
}

/** Blocks until one utterance is captured (or the coroutine is cancelled → null). */
@SuppressLint("MissingPermission") // Caller only activates this once RECORD_AUDIO is granted.
private suspend fun captureUtterance(onSpeech: (Boolean) -> Unit): ByteArray? = withContext(Dispatchers.IO) {
    val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val recorder = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        max(minBuffer, FRAME_SAMPLES * 2 * 8)
    )
    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        recorder.release()
        return@withContext null
    }

    val frame = ShortArray(FRAME_SAMPLES)
    val preRoll = ArrayDeque<ShortArray>()
    val speech = mutableListOf<ShortArray>()
    var noiseFloor = 150.0
    var loudRun = 0
    var quietRun = 0
    var inSpeech = false

    try {
        recorder.startRecording()
        while (isActive) {
            val read = recorder.read(frame, 0, FRAME_SAMPLES)
            if (read <= 0) continue
            val chunk = frame.copyOf(read)
            val level = rms(chunk)

            if (!inSpeech) {
                preRoll.addLast(chunk)
                if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                loudRun = if (level > max(noiseFloor * 3.0, 400.0)) loudRun + 1 else 0
                if (loudRun >= START_FRAMES) {
                    inSpeech = true
                    quietRun = 0
                    speech.addAll(preRoll)
                    preRoll.clear()
                    onSpeech(true)
                } else {
                    // Track background noise only while nobody is talking.
                    noiseFloor = (noiseFloor * 0.97 + level * 0.03).coerceIn(50.0, 3000.0)
                }
            } else {
                speech.add(chunk)
                quietRun = if (level < max(noiseFloor * 2.0, 300.0)) quietRun + 1 else 0
                if (quietRun >= END_SILENCE_FRAMES || speech.size >= MAX_SPEECH_FRAMES) {
                    if (speech.size - quietRun >= MIN_SPEECH_FRAMES) {
                        return@withContext toWav(speech)
                    }
                    // Too short to be a real sentence (a cough, a tap) — keep waiting.
                    speech.clear()
                    inSpeech = false
                    loudRun = 0
                    onSpeech(false)
                }
            }
        }
        null
    } finally {
        runCatching { recorder.stop() }
        recorder.release()
    }
}

private fun rms(samples: ShortArray): Double {
    var sum = 0.0
    for (s in samples) sum += s.toDouble() * s
    return sqrt(sum / samples.size)
}

private fun toWav(frames: List<ShortArray>): ByteArray {
    val sampleCount = frames.sumOf { it.size }
    val dataBytes = sampleCount * 2
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
        putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(dataBytes)
    }
    val out = ByteArrayOutputStream(44 + dataBytes)
    out.write(header.array())
    val pcm = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    frames.forEach { f -> f.forEach { pcm.putShort(it) } }
    out.write(pcm.array())
    return out.toByteArray()
}
