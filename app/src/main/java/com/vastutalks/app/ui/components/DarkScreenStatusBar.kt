package com.vastutalks.app.ui.components

import android.app.Activity
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light status-bar icons (clock, battery) while a dark screen — Ananya's
 * chat and calls, the room scan, expert calls — is showing. The theme
 * makes them dark for the app's light screens, which leaves them
 * invisible on these. Counted, so going from one dark screen to another
 * doesn't flip them back in between; the theme checks [isActive] too.
 */
@Composable
fun DarkScreenStatusBar() {
    val view = LocalView.current
    DisposableEffect(view) {
        DarkScreenStatusBarState.count++
        applyStatusBarIcons(view)
        onDispose {
            DarkScreenStatusBarState.count--
            applyStatusBarIcons(view)
        }
    }
    // Re-applied after each recomposition, in case something else reset it.
    SideEffect { applyStatusBarIcons(view) }
}

object DarkScreenStatusBarState {
    internal var count = 0
    /** True while any dark screen is showing. */
    val isActive: Boolean get() = count > 0
}

private fun applyStatusBarIcons(view: View) {
    val window = (view.context as? Activity)?.window ?: return
    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !DarkScreenStatusBarState.isActive
}
