package dev.harness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens for Harness. Kept deliberately small: one accent, a tight
 * neutrals ramp, generous spacing. See docs/DESIGN.md for the rationale.
 */
data class HarnessColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentMuted: Color,
    val codeBackground: Color,
    val toolRunning: Color,
    val toolOk: Color,
    val toolError: Color,
    val userBubble: Color,
    val isDark: Boolean,
)

val DarkHarnessColors = HarnessColors(
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF111111),
    surfaceElevated = Color(0xFF171717),
    border = Color(0xFF1F1F1F),
    textPrimary = Color(0xFFEDEDED),
    textSecondary = Color(0xFF8A8A8A),
    textTertiary = Color(0xFF5C5C5C),
    accent = Color(0xFFE8E8E8),
    accentMuted = Color(0xFF2A2A2A),
    codeBackground = Color(0xFF161616),
    toolRunning = Color(0xFFC79A4A),
    toolOk = Color(0xFF5E9E6E),
    toolError = Color(0xFFC4665E),
    userBubble = Color(0xFF141414),
    isDark = true,
)

val LightHarnessColors = HarnessColors(
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF2F2F2),
    border = Color(0xFFE4E4E4),
    textPrimary = Color(0xFF141414),
    textSecondary = Color(0xFF6A6A6A),
    textTertiary = Color(0xFF9A9A9A),
    accent = Color(0xFF1A1A1A),
    accentMuted = Color(0xFFECECEC),
    codeBackground = Color(0xFFF1F1F1),
    toolRunning = Color(0xFF9A6B1E),
    toolOk = Color(0xFF2F7A45),
    toolError = Color(0xFFB23B33),
    userBubble = Color(0xFFF0F0F0),
    isDark = false,
)

val LocalHarnessColors = staticCompositionLocalOf { DarkHarnessColors }

/** Spacing scale (dp). */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radii {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val pill = 100.dp
}

private val HarnessTypography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    )
}

val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.5.sp,
    lineHeight = 18.sp,
)

@Composable
fun HarnessThemeProvider(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkHarnessColors else LightHarnessColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    }
    CompositionLocalProvider(LocalHarnessColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = HarnessTypography, content = content)
    }
}

/** Shortcut: read the current design tokens inside composables. */
val HarnessTheme: HarnessColors
    @Composable get() = LocalHarnessColors.current
