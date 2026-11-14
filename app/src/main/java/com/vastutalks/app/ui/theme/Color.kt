package com.vastutalks.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette from the "Vastu Talks (Make)" design.
val VastuPrimary = Color(0xFF6C5CE7)
val VastuIndigo = Color(0xFF4F46E5)
val VastuPrimaryDark = Color(0xFF3D2B9E)
val VastuPrimarySoft = Color(0xFFF3F0FF)
val VastuLilac = Color(0xFFC084FC)
val VastuSun = Color(0xFFFFD93D)
val VastuCoral = Color(0xFFFF6B6B)

// Night hero (login / register / Ananya voice / scan analysis)
val NightDeep = Color(0xFF0F0B2E)
val NightMid = Color(0xFF241A5C)
val NightPlum = Color(0xFF3B1D6E)
val NightOrbCore = Color(0xFF14103A)

// Text
val TextPrimary = Color(0xFF1A202C)
val TextBody = Color(0xFF4A5568)
val TextSecondary = Color(0xFF718096)
val TextHint = Color(0xFFA0AEC0)

// Surfaces
val SurfaceLight = Color(0xFFF7F7FB)
val CardWhite = Color(0xFFFFFFFF)
val InputFill = Color(0xFFF6F6F9)
val CardBorder = Color(0xFFECECF3)
val Divider = Color(0xFFEFEFF4)

// States
val Success = Color(0xFF16A34A)
val Danger = Color(0xFFEF4444)
val OnlineGreen = Color(0xFF22C55E)
val StarGold = Color(0xFFF5B301)
val WhatsAppGreen = Color(0xFF25D366)

// Older names still used around the app, mapped onto the new palette.
val VastuVioletStart = VastuPrimary
val VastuCharcoal = TextPrimary
val VastuCopper = VastuCoral
val VastuSaffron = VastuSun
val WalletGold = VastuSun
val WalletGoldDark = Color(0xFFCA8A04)
val OnboardingPinkStart = Color(0xFFF472B6)
val OnboardingPinkEnd = Color(0xFFDB2777)
val OnboardingGreenStart = Color(0xFF51CF66)
val OnboardingGreenEnd = Color(0xFF16A34A)

/** Soft lavender wash behind onboarding, expert detail and chat. */
val SoftWashGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFEEF2FF), Color(0xFFFAF5FF), Color(0xFFFDF2F8))
)

/** Dark hero at the top of sign in / create account. */
val NightGradient = Brush.linearGradient(
    colors = listOf(NightDeep, NightMid, NightPlum)
)

val AuthGradient = NightGradient

val PrimaryButtonGradient = Brush.horizontalGradient(
    colors = listOf(VastuPrimary, VastuIndigo)
)

/** "AI Vastu" headline highlight. */
val SunsetTextGradient = Brush.horizontalGradient(
    colors = listOf(VastuSun, Color(0xFFF472B6), VastuLilac)
)

/** Behind calls with a human expert. */
val ExpertCallGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF6C5CE7), Color(0xFF7E22CE), Color(0xFF3730A3))
)
val EndCallRed = Color(0xFFF56565)
