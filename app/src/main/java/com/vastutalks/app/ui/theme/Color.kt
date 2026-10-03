package com.vastutalks.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// Brand purples — taken directly from the provided screens
val VastuVioletStart = Color(0xFF6A4CE0)
val VastuVioletEnd = Color(0xFF8B3FE0)
val VastuPinkEnd = Color(0xFFD6308E)
val VastuPrimary = Color(0xFF5B3FE0)
val VastuPrimaryDark = Color(0xFF3D2B9E)

// Vastu-specific earth accents (used past the auth flow, for the
// expert-discovery / call surfaces so the app reads as "Vastu" and
// not generic fintech)
val VastuCopper = Color(0xFFC86E4A)
val VastuSaffron = Color(0xFFE8A33D)
val VastuCream = Color(0xFFFBF6EE)
val VastuCharcoal = Color(0xFF2B2B2B)

// Neutral surfaces
val SurfaceLight = Color(0xFFF7F6FB)
val CardWhite = Color(0xFFFFFFFF)
val InputFill = Color(0xFFF1F0F6)
val TextSecondary = Color(0xFF6B6878)
val TextHint = Color(0xFF9C99A8)
val Success = Color(0xFF2E9E6B)
val Danger = Color(0xFFE0473E)
val OnlineGreen = Color(0xFF34C76B)

// Onboarding slide accent colors (matching the provided mockups) —
// slide 1 reuses VastuPrimary/PrimaryDark, these cover slides 2 and 3.
val OnboardingPinkStart = Color(0xFFEA5B7B)
val OnboardingPinkEnd = Color(0xFFD6308E)
val OnboardingGreenStart = Color(0xFF3FCE7A)
val OnboardingGreenEnd = Color(0xFF1FA85C)
val WalletGold = Color(0xFFF2B33D)
val WalletGoldDark = Color(0xFFE0902A)

val AuthGradient = Brush.verticalGradient(
    colors = listOf(VastuVioletStart, VastuVioletEnd, VastuPinkEnd)
)

val PrimaryButtonGradient = Brush.horizontalGradient(
    colors = listOf(VastuVioletStart, Color(0xFF6F3FE0))
)

val DirectionalGradient = Brush.linearGradient(
    colors = listOf(VastuCopper, VastuSaffron)
)
