package dev.harness.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Harness "Aurora" palette — dark-first, validated against WCAG AA with
 * scripts/contrast_check.py (every text pair >= 4.5:1; see docs/DESIGN.md).
 *
 * The dark theme is the canonical brand; the light theme mirrors it.
 */

// Dark ------------------------------------------------------------------
val DarkBackground = Color(0xFF0B0E14)
val DarkSurface = Color(0xFF12161F)
val DarkSurfaceContainer = Color(0xFF1A1F2B)
val DarkSurfaceHigh = Color(0xFF222838)
val DarkOutline = Color(0xFF2A3040)
val DarkOutlineStrong = Color(0xFF3A4156)
val DarkTextPrimary = Color(0xFFE8EBF3)
val DarkTextSecondary = Color(0xFF9AA3B8)
val DarkTextTertiary = Color(0xFF818CA3) // metadata text — 5.72:1 on bg, AA ok
val DarkPrimary = Color(0xFF7C5CFF)
val DarkPrimaryButton = Color(0xFF6841E0) // white label on this = 6.14:1
val DarkOnPrimary = Color(0xFFFFFFFF)
val DarkPrimaryContainer = Color(0xFF3B2E80)
val DarkOnPrimaryContainer = Color(0xFFEDE9FF)
val DarkPrimaryTextAccent = Color(0xFFA18BFF) // accent used as small text = 7.04:1
val DarkSecondary = Color(0xFF22D3EE)
val DarkSecondaryText = Color(0xFF22D3EE)
val DarkSecondaryContainer = Color(0xFF123F4B)
val DarkOnSecondaryContainer = Color(0xFFD9F7FF)
val DarkSuccess = Color(0xFF34D399)
val DarkSuccessContainer = Color(0xFF12402F)
val DarkWarning = Color(0xFFFBBF24)
val DarkWarningContainer = Color(0xFF4A3508)
val DarkError = Color(0xFFF87171)
val DarkErrorText = Color(0xFFFCA5A5) // error small text = 10.18:1
val DarkErrorContainer = Color(0xFF4A1A1C)
val DarkOnErrorContainer = Color(0xFFFFD9D6)
val DarkToolOkText = Color(0xFF34D399) // ok text on code bg = 9.76:1
val DarkToolWarningText = Color(0xFFFDE68A) // warning text on warning container
val DarkCodeBackground = Color(0xFF0E1219)
val DarkUserBubble = Color(0xFF232945)
val DarkScrim = Color(0x99000000)

// Light -----------------------------------------------------------------
val LightBackground = Color(0xFFF7F8FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFEEF0F6)
val LightSurfaceHigh = Color(0xFFE4E7F0)
val LightOutline = Color(0xFFD5D9E4)
val LightOutlineStrong = Color(0xFFB8BECF)
val LightTextPrimary = Color(0xFF0F1320)
val LightTextSecondary = Color(0xFF5A6378)
val LightTextTertiary = Color(0xFF646D82) // metadata text — 4.88:1 on bg, AA ok
val LightPrimary = Color(0xFF5B3FE0)
val LightPrimaryButton = Color(0xFF5B3FE0) // white label on this = 6.50:1
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE4DCFF)
val LightOnPrimaryContainer = Color(0xFF2A2050)
val LightPrimaryTextAccent = Color(0xFF5B3FE0) // 6.12:1 on light background
val LightSecondary = Color(0xFF0891B2)
val LightSecondaryText = Color(0xFF0E7490) // small cyan text = 5.36:1 on white
val LightSecondaryContainer = Color(0xFFD6F3FB)
val LightOnSecondaryContainer = Color(0xFF083344)
val LightSuccess = Color(0xFF059669)
val LightSuccessText = Color(0xFF067647)
val LightSuccessContainer = Color(0xFFD3F3E5)
val LightWarning = Color(0xFFB45309)
val LightWarningText = Color(0xFF93370D)
val LightWarningContainer = Color(0xFFFCEDD4)
val LightError = Color(0xFFDC2626)
val LightErrorText = Color(0xFFB42318)
val LightErrorContainer = Color(0xFFFDD9D6)
val LightOnErrorContainer = Color(0xFF5C110B)
val LightToolOkText = Color(0xFF067647) // ok text = 5.69:1 white / 5.13:1 codeBg
val LightToolWarningText = Color(0xFF93370D) // 6.52:1 on warning container
val LightCodeBackground = Color(0xFFF1F3F9)
val LightUserBubble = Color(0xFFE4DCFF)
val LightScrim = Color(0x52000000)
