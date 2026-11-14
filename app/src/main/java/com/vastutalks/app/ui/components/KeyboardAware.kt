package com.vastutalks.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Keeps a text field, plus [spaceBelow] under it (room for the button that
 * usually follows), on screen while the keyboard is open.
 *
 * The screen still has to make room for the keyboard (`imePadding()`) and
 * be scrollable; this then scrolls it. Compose only scrolls a field into
 * view when it gains focus, which happens before the keyboard has finished
 * sliding up, so on its own the field (and certainly the button) often
 * ends up underneath. Here the request is repeated each time the keyboard
 * height changes and runs once it has settled.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.keepAboveKeyboard(spaceBelow: Dp = 80.dp): Modifier = composed {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density)
    val extra = with(density) { spaceBelow.toPx() }

    LaunchedEffect(focused, keyboard) {
        if (focused && keyboard > 0) {
            delay(KEYBOARD_SETTLE_MS) // restarted on every frame of the slide, so this runs after the last
            requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + extra))
        }
    }

    this
        .bringIntoViewRequester(requester)
        .onSizeChanged { size = it }
        .onFocusEvent { focused = it.hasFocus }
}

private const val KEYBOARD_SETTLE_MS = 80L
