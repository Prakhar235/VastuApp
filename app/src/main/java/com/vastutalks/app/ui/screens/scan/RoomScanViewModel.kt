package com.vastutalks.app.ui.screens.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastutalks.app.data.ai.RoomAnalysis
import com.vastutalks.app.data.ai.RoomFinding
import com.vastutalks.app.data.ai.RoomScanHandoff
import com.vastutalks.app.data.ai.VastuAgent
import com.vastutalks.app.data.ai.WallPhoto
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The home scan: a video walk-through from the main door (or a video or
 * photo from the gallery), then Ananya's read of it — a score, each thing
 * she found marked on the frame it's in, and for things that should move,
 * where to (see RoomResultScreen).
 *
 * The model can't watch video, so while recording the screen hands in a
 * frame every few seconds ([addFrame]) along with the compass direction
 * at that moment; those frames are what she reads.
 */
class RoomScanViewModel : ViewModel() {
    enum class Stage { CAPTURE, ANALYZING, RESULT, MAP, FAILED }

    private val agent = VastuAgent()
    private var analyzeJob: Job? = null
    /** The current analysis is already in the chat. */
    private var sentToChat = false
    /** Photos of a saved report being viewed again. */
    private var savedWalls: List<WallPhoto>? = null

    var stage by mutableStateOf(Stage.CAPTURE)
        private set

    var recording by mutableStateOf(false)
        private set
    /** Seconds recorded so far, for the REC timer. */
    var recordSeconds by mutableIntStateOf(0)
    /** How many times the frames held have been halved; the screen samples half as often after each. */
    var thinnings by mutableIntStateOf(0)
        private set

    /** Frames taken so far while recording. */
    private val held = mutableListOf<WallPhoto>()
    /** The frames sent for analysis, in order; findings refer to them by index. */
    private var frames by mutableStateOf<List<WallPhoto>>(emptyList())

    var analysis by mutableStateOf<RoomAnalysis?>(null)
        private set
    /** The finding shown on the map. */
    var mapFinding by mutableStateOf<RoomFinding?>(null)
        private set

    val walls: List<WallPhoto> get() = savedWalls ?: frames

    /** Shows a report saved with the chat, as it was. */
    fun showSaved(scan: RoomScanHandoff.Scan) {
        savedWalls = scan.walls
        analysis = scan.analysis
        sentToChat = true
        stage = Stage.RESULT
    }

    fun startRecording() {
        held.clear()
        thinnings = 0
        recordSeconds = 0
        recording = true
    }

    /** One frame from the live camera while recording. */
    fun addFrame(frame: WallPhoto) {
        if (!recording) return
        held += frame
        if (held.size >= MAX_HELD) { // a long walk: keep every other one and sample half as often
            val kept = held.filterIndexed { i, _ -> i % 2 == 0 }
            held.clear()
            held += kept
            thinnings++
        }
    }

    /** Stops recording and analyses what was recorded. */
    fun stopRecording() {
        if (!recording) return
        recording = false
        analyzeFrames(held.toList())
    }

    /** Stops recording without analysing (e.g. Back pressed mid-recording). */
    fun cancelRecording() {
        recording = false
        held.clear()
    }

    /** Analyses these frames (from a recording or an uploaded video or photo). */
    fun analyzeFrames(all: List<WallPhoto>) {
        if (all.isEmpty()) return
        frames = evenly(all, MAX_SENT)
        analyze()
    }

    fun analyze() {
        if (walls.isEmpty()) return
        stage = Stage.ANALYZING
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            val result = agent.analyzeRoom(walls)
            analysis = result
            sentToChat = false
            // Straight into the chat (and saved there), whether or not they ask about it.
            handOffToChat()
            stage = if (result != null) Stage.RESULT else Stage.FAILED
        }
    }

    /** Back to the camera, keeping the last frames for "Try again". */
    fun backToCapture() {
        analyzeJob?.cancel()
        stage = Stage.CAPTURE
    }

    fun showOnMap(finding: RoomFinding) {
        mapFinding = finding
        stage = Stage.MAP
    }

    fun closeMap() {
        stage = Stage.RESULT
    }

    /** Starts over with nothing recorded, e.g. after moving things around. */
    fun rescan() {
        analyzeJob?.cancel()
        cancelRecording()
        frames = emptyList()
        savedWalls = null
        analysis = null
        mapFinding = null
        stage = Stage.CAPTURE
    }

    /** Where a finished scan goes instead of the chat, e.g. into a call with Ananya. */
    var onHandOff: ((RoomScanHandoff.Scan) -> Unit)? = null

    /** Sends the scan to the chat with Ananya (once); false if there's nothing to send. */
    fun handOffToChat(): Boolean {
        val result = analysis ?: return false
        if (!sentToChat) {
            val scan = RoomScanHandoff.Scan(walls, result)
            onHandOff?.invoke(scan) ?: RoomScanHandoff.send(scan)
        }
        sentToChat = true
        return true
    }

    companion object {
        /** Frames sent to the model: enough to cover a home, few enough to stay quick. */
        const val MAX_SENT = 10
        /** Frames held while recording before halving them. */
        private const val MAX_HELD = 20

        /** [count] items spread evenly over [list], first and last included. */
        private fun <T> evenly(list: List<T>, count: Int): List<T> =
            if (list.size <= count) list
            else (0 until count).map { list[it * (list.size - 1) / (count - 1)] }
    }
}
