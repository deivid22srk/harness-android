package dev.harness.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import dev.harness.R

/** JetBrains Mono, bundled (OFL, see docs/OFL-JetBrainsMono.txt) for code, diffs and metadata. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
    Font(R.font.jetbrainsmono_bold, FontWeight.Bold),
)

/** UI family: the platform sans (Roboto on most devices) — no runtime font provider. */
val HarnessSans = FontFamily.Default

private val BodyLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Type scale: display (marketing/onboarding hero), headline (screen titles),
 * title (bars/cards), body (chat + settings), label (buttons/chips).
 * Body sizes are tuned for chat readability at 1.0 font scale; the whole
 * scale is multiplied by the user's font-size preference in HarnessThemeProvider.
 */
val HarnessTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp,
        lineHeightStyle = BodyLineHeight,
    ),
    bodyMedium = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
        lineHeightStyle = BodyLineHeight,
    ),
    bodySmall = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp, lineHeight = 17.sp,
        lineHeightStyle = BodyLineHeight,
    ),
    labelLarge = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = HarnessSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp,
    ),
)

/** Metadata/code small mono. */
val MonoStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.5.sp,
    lineHeight = 18.sp,
)

/** Code block mono (slightly larger). */
val CodeStyle = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 20.sp,
)
