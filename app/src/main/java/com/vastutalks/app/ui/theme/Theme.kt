package com.vastutalks.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = VastuPrimary,
    onPrimary = Color.White,
    secondary = VastuCopper,
    onSecondary = Color.White,
    background = SurfaceLight,
    onBackground = VastuCharcoal,
    surface = CardWhite,
    onSurface = VastuCharcoal,
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = VastuVioletStart,
    onPrimary = Color.White,
    secondary = VastuCopper,
    background = VastuCharcoal,
    surface = Color(0xFF1F1D29),
    onSurface = Color.White
)

@Composable
fun VastuTalksTheme(
    // Every screen in this app is hand-styled with hardcoded light
    // backgrounds (white cards, light surfaces, etc.), not built to
    // adapt to Material3's dark color scheme. Following the system's
    // dark-mode setting was causing text/icons that don't set an
    // explicit color to inherit the dark scheme's white "on surface"
    // color and disappear against those light backgrounds. Until the
    // app actually gets a proper dark theme pass, always use light.
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    !darkTheme
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VastuTypography,
        content = content
    )
}
