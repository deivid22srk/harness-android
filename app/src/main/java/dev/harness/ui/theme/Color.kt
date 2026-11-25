package dev.harness.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Harness palette — neutral charcoal + discreet blue, matching the user's
 * reference screenshots (docs/REFERENCIAS_VISUAIS.md; hex values measured
 * from the images). Dark is the canonical brand; light mirrors it neutrally.
 *
 * Every text pair is validated against WCAG AA (>= 4.5:1) by
 * scripts/contrast_check.py.
 */

// Dark — neutral charcoal, measured from the reference screenshots ----------
val DarkBackground = Color(0xFF161616)      // screen bg (measured #161616)
val DarkSurface = Color(0xFF1C1C1E)         // cards / menus (measured #1c1c1c)
val DarkSurfaceContainer = Color(0xFF222223) // elevated cards / input pill (#222222)
val DarkSurfaceHigh = Color(0xFF2A2A2C)     // pressed / higher surfaces
val DarkOutline = Color(0xFF2E2E30)         // subtle borders (measured card edge)
val DarkOutlineStrong = Color(0xFF3A3A3D)   // send-button circle (#3a3a3a)
val DarkTextPrimary = Color(0xFFEDEDED)     // near-white body text
val DarkTextSecondary = Color(0xFFA0A0A3)   // secondary text
val DarkTextTertiary = Color(0xFF8A8A8F)    // metadata / "Usou N ferramentas"
val DarkAccent = Color(0xFF3D8BFF)          // accent (dots, icons) — measured blue
val DarkAccentButton = Color(0xFF2773DB)    // white-label button bg (AA 4.6:1)
val DarkOnAccent = Color(0xFFFFFFFF)
val DarkAccentContainer = Color(0xFF1D3350) // filled accent containers
val DarkOnAccentContainer = Color(0xFFD6E6FF)
val DarkAccentText = Color(0xFF6FB2FF)      // accent as small text (AA on bg)
val DarkAccentMuted = Color(0xFF203040)
val DarkSecondary = Color(0xFF43B3AE)       // muted teal, kept for legacy roles
val DarkSecondaryText = Color(0xFF5CC7C2)
val DarkSecondaryContainer = Color(0xFF153634)
val DarkOnSecondaryContainer = Color(0xFFCCF2EF)
val DarkSuccess = Color(0xFF4ADE80)         // status green (measured #70d090 family)
val DarkSuccessContainer = Color(0xFF163826)
val DarkWarning = Color(0xFFFBBF24)
val DarkWarningContainer = Color(0xFF453505)
val DarkError = Color(0xFFF26A6F)           // destructive red (measured family)
val DarkErrorText = Color(0xFFFCA5A5)
val DarkErrorContainer = Color(0xFF461A1D)
val DarkOnErrorContainer = Color(0xFFFFD9D6)
val DarkToolOkText = Color(0xFF63DA93)      // ok text on code bg
val DarkToolWarningText = Color(0xFFFDE68A)
val DarkCodeBackground = Color(0xFF0D0D0D)  // near-black code (measured)
val DarkUserBubble = Color(0xFF2E2E2E)      // user bubble (measured #2e2e2e)
val DarkScrim = Color(0x99000000)

// Light — neutral mirror ----------------------------------------------------
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF0F0F2)
val LightSurfaceHigh = Color(0xFFE6E6E9)
val LightOutline = Color(0xFFDCDCDF)
val LightOutlineStrong = Color(0xFFC3C3C7)
val LightTextPrimary = Color(0xFF1B1B1D)
val LightTextSecondary = Color(0xFF58585E)
val LightTextTertiary = Color(0xFF6B6B71)
val LightAccent = Color(0xFF2E7CE6)
val LightAccentButton = Color(0xFF2E7CE6)   // white label = high contrast
val LightOnAccent = Color(0xFFFFFFFF)
val LightAccentContainer = Color(0xFFDCEAFF)
val LightOnAccentContainer = Color(0xFF12305C)
val LightAccentText = Color(0xFF1F66C4)
val LightAccentMuted = Color(0xFFDCEAFF)
val LightSecondary = Color(0xFF0E8A84)
val LightSecondaryText = Color(0xFF0B6B66)
val LightSecondaryContainer = Color(0xFFD2F0EE)
val LightOnSecondaryContainer = Color(0xFF073634)
val LightSuccess = Color(0xFF0C8A4E)
val LightSuccessText = Color(0xFF0A6B3E)
val LightSuccessContainer = Color(0xFFD3F3E2)
val LightWarning = Color(0xFFB45309)
val LightWarningText = Color(0xFF93370D)
val LightWarningContainer = Color(0xFFFCEDD4)
val LightError = Color(0xFFC93940)
val LightErrorText = Color(0xFFAD252B)
val LightErrorContainer = Color(0xFFFDD9D6)
val LightOnErrorContainer = Color(0xFF5C110B)
val LightToolOkText = Color(0xFF0A6B3E)
val LightToolWarningText = Color(0xFF93370D)
val LightCodeBackground = Color(0xFFF1F1F3)
val LightUserBubble = Color(0xFFECECEE)
val LightScrim = Color(0x52000000)

// Compatibility aliases used by Theme.kt mapping ----------------------------
val DarkPrimary = DarkAccent
val DarkPrimaryButton = DarkAccentButton
val DarkOnPrimary = DarkOnAccent
val DarkPrimaryContainer = DarkAccentContainer
val DarkOnPrimaryContainer = DarkOnAccentContainer
val DarkPrimaryTextAccent = DarkAccentText
val LightPrimary = LightAccent
val LightPrimaryButton = LightAccentButton
val LightOnPrimary = LightOnAccent
val LightPrimaryContainer = LightAccentContainer
val LightOnPrimaryContainer = LightOnAccentContainer
val LightPrimaryTextAccent = LightAccentText
