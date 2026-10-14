package com.vastutalks.app.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Carries a finished room scan from the scan screen into the chat with
 * Ananya ("Ask Ananya about this room"), so she can answer follow-ups
 * about it. Held in memory only; the chat saves it once it's added.
 */
object RoomScanHandoff {
    class Scan(val walls: List<WallPhoto>, val analysis: RoomAnalysis)

    private val _pending = MutableStateFlow<Scan?>(null)
    val pending: StateFlow<Scan?> get() = _pending

    fun send(scan: Scan) {
        _pending.value = scan
    }

    /** The waiting scan, if any, handed over once. */
    fun take(): Scan? = _pending.getAndUpdate { null }
}
