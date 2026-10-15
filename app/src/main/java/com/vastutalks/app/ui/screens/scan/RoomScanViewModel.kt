package com.vastutalks.app.ui.screens.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastutalks.app.data.ai.CompassDirection
import com.vastutalks.app.data.ai.RoomAnalysis
import com.vastutalks.app.data.ai.RoomFinding
import com.vastutalks.app.data.ai.RoomScanHandoff
import com.vastutalks.app.data.ai.VastuAgent
import com.vastutalks.app.data.ai.WallPhoto
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The guided room scan: one photo of each wall (North, East, South, West,
 * in that order; any can be skipped), then Ananya's read of the whole
 * room — a score, each thing she found marked on its photo, and for
 * things that should move, where to (see RoomResultScreen).
 */
class RoomScanViewModel : ViewModel() {
    enum class Stage { CAPTURE, ANALYZING, RESULT, MAP, FAILED }

    private val agent = VastuAgent()
    private var analyzeJob: Job? = null
    /** The current analysis is already in the chat. */
    private var sentToChat = false
    /** Photos of a saved report being viewed again (they aren't in [photos]). */
    private var savedWalls: List<WallPhoto>? = null

    var stage by mutableStateOf(Stage.CAPTURE)
        private set

    /** Index into [WALLS] of the wall being photographed. */
    var step by mutableIntStateOf(0)
        private set
    val photos = mutableStateMapOf<CompassDirection, WallPhoto>()
    val skipped = mutableStateMapOf<CompassDirection, Boolean>()

    var analysis by mutableStateOf<RoomAnalysis?>(null)
        private set
    /** The finding shown on the map. */
    var mapFinding by mutableStateOf<RoomFinding?>(null)
        private set

    val currentWall: CompassDirection get() = WALLS[step]

    /** The photos sent for analysis, in wall order; findings refer to them by index. */
    val walls: List<WallPhoto> get() = savedWalls ?: WALLS.mapNotNull { photos[it] }

    /** Shows a report saved with the chat, as it was. */
    fun showSaved(scan: RoomScanHandoff.Scan) {
        savedWalls = scan.walls
        analysis = scan.analysis
        sentToChat = true
        stage = Stage.RESULT
    }

    fun goToWall(direction: CompassDirection) {
        step = WALLS.indexOf(direction)
    }

    fun onPhoto(photo: WallPhoto) {
        photos[currentWall] = photo
        skipped.remove(currentWall)
        advance()
    }

    fun skip() {
        if (currentWall !in photos) skipped[currentWall] = true
        advance()
    }

    /** Next wall still to do, or the analysis once every wall is photographed or skipped. */
    private fun advance() {
        val next = (1..WALLS.size).map { (step + it) % WALLS.size }
            .firstOrNull { WALLS[it] !in photos && skipped[WALLS[it]] != true }
        if (next != null) step = next else if (photos.isNotEmpty()) analyze()
    }

    fun analyze() {
        if (photos.isEmpty()) return
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

    /** Back to taking photos, keeping the ones already taken. */
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

    /** Starts over with no photos, e.g. after moving things around. */
    fun rescan() {
        analyzeJob?.cancel()
        photos.clear()
        skipped.clear()
        savedWalls = null
        analysis = null
        mapFinding = null
        step = 0
        stage = Stage.CAPTURE
    }

    /** Sends the scan to the chat with Ananya (once); false if there's nothing to send. */
    fun handOffToChat(): Boolean {
        val result = analysis ?: return false
        if (!sentToChat) RoomScanHandoff.send(RoomScanHandoff.Scan(walls, result))
        sentToChat = true
        return true
    }

    companion object {
        val WALLS = listOf(CompassDirection.N, CompassDirection.E, CompassDirection.S, CompassDirection.W)
    }
}
