package dev.harness.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density

/**
 * Semantic color tokens for Harness. Every component reads from this class —
 * never from raw Color values — so themes stay consistent and swappable
 * (including the Material You dynamic variant).
 */
@Immutable
data class HarnessColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHigh: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentButton: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val accentText: Color,
    val accentMuted: Color,
    val secondary: Color,
    val secondaryText: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val toolRunning: Color,
    val toolRunningContainer: Color,
    val toolOk: Color,
    val toolOkContainer: Color,
    val toolWarning: Color,
    val toolWarningContainer: Color,
    val toolError: Color,
    val toolErrorText: Color,
    val toolErrorContainer: Color,
    val onErrorContainer: Color,
    val codeBackground: Color,
    val userBubble: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val DarkHarnessColors = HarnessColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceElevated = DarkSurfaceContainer,
    surfaceHigh = DarkSurfaceHigh,
    border = DarkOutline,
    borderStrong = DarkOutlineStrong,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    accent = DarkPrimary,
    accentButton = DarkPrimaryButton,
    onAccent = DarkOnPrimary,
    accentContainer = DarkPrimaryContainer,
    onAccentContainer = DarkOnPrimaryContainer,
    accentText = DarkPrimaryTextAccent,
    accentMuted = DarkPrimaryContainer,
    secondary = DarkSecondary,
    secondaryText = DarkSecondaryText,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    toolRunning = DarkWarning,
    toolRunningContainer = DarkWarningContainer,
    toolOk = DarkSuccess,
    toolOkContainer = DarkSuccessContainer,
    toolWarning = DarkWarning,
    toolWarningContainer = DarkWarningContainer,
    toolError = DarkError,
    toolErrorText = DarkErrorText,
    toolErrorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    codeBackground = DarkCodeBackground,
    userBubble = DarkUserBubble,
    scrim = DarkScrim,
    isDark = true,
)

val LightHarnessColors = HarnessColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceElevated = LightSurfaceContainer,
    surfaceHigh = LightSurfaceHigh,
    border = LightOutline,
    borderStrong = LightOutlineStrong,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textTertiary = LightTextTertiary,
    accent = LightPrimary,
    accentButton = LightPrimaryButton,
    onAccent = LightOnPrimary,
    accentContainer = LightPrimaryContainer,
    onAccentContainer = LightOnPrimaryContainer,
    accentText = LightPrimaryTextAccent,
    accentMuted = LightPrimaryContainer,
    secondary = LightSecondary,
    secondaryText = LightSecondaryText,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    toolRunning = LightWarning,
    toolRunningContainer = LightWarningContainer,
    toolOk = LightSuccess,
    toolOkContainer = LightSuccessContainer,
    toolWarning = LightWarning,
    toolWarningContainer = LightWarningContainer,
    toolError = LightError,
    toolErrorText = LightErrorText,
    toolErrorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    codeBackground = LightCodeBackground,
    userBubble = LightUserBubble,
    scrim = LightScrim,
    isDark = false,
)

val LocalHarnessColors = staticCompositionLocalOf { DarkHarnessColors }

/** Shortcut: read the current design tokens inside composables. */
val HarnessTheme: HarnessColors
    @Composable get() = LocalHarnessColors.current

/**
 * Builds the full Material 3 [ColorScheme] from the semantic palette so
 * built-in M3 components (dialogs, sheets, text fields) match the brand.
 */
private fun mapScheme(c: HarnessColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.accentButton,
        onPrimary = c.onAccent,
        primaryContainer = c.accentContainer,
        onPrimaryContainer = c.onAccentContainer,
        secondary = c.secondary,
        onSecondary = DarkBackground,
        secondaryContainer = c.secondaryContainer,
        onSecondaryContainer = c.onSecondaryContainer,
        tertiary = c.secondary,
        onTertiary = DarkBackground,
        error = c.toolError,
        onError = DarkBackground,
        errorContainer = c.toolErrorContainer,
        onErrorContainer = c.onErrorContainer,
        background = c.background,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceElevated,
        onSurfaceVariant = c.textSecondary,
        surfaceContainerLowest = c.codeBackground,
        surfaceContainerLow = c.background,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceElevated,
        surfaceContainerHighest = c.surfaceHigh,
        outline = c.borderStrong,
        outlineVariant = c.border,
        inverseSurface = LightSurface,
        inverseOnSurface = LightTextPrimary,
        scrim = c.scrim,
    )
} else {
    lightColorScheme(
        primary = c.accentButton,
        onPrimary = c.onAccent,
        primaryContainer = c.accentContainer,
        onPrimaryContainer = c.onAccentContainer,
        secondary = c.secondary,
        onSecondary = LightBackground,
        secondaryContainer = c.secondaryContainer,
        onSecondaryContainer = c.onSecondaryContainer,
        tertiary = c.secondary,
        onTertiary = LightBackground,
        error = c.toolError,
        onError = LightBackground,
        errorContainer = c.toolErrorContainer,
        onErrorContainer = c.onErrorContainer,
        background = c.background,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceElevated,
        onSurfaceVariant = c.textSecondary,
        surfaceContainerLowest = LightSurface,
        surfaceContainerLow = c.background,
        surfaceContainer = c.surfaceElevated,
        surfaceContainerHigh = c.surfaceHigh,
        surfaceContainerHighest = LightSurfaceHigh,
        outline = c.borderStrong,
        outlineVariant = c.border,
        inverseSurface = DarkSurface,
        inverseOnSurface = DarkTextPrimary,
        scrim = c.scrim,
    )
}

/**
 * Material You dynamic palette derived from the wallpaper (API 31+), mapped
 * back into our semantic slots; falls back to the Aurora brand palette
 * below API 31 or when disabled by the user.
 */
@Composable
private fun dynamicHarnessColors(dark: Boolean): HarnessColors? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return if (dark) {
        DarkHarnessColors.copy(
            background = scheme.background,
            surface = scheme.surface,
            surfaceElevated = scheme.surfaceContainer,
            surfaceHigh = scheme.surfaceContainerHigh,
            border = scheme.outlineVariant,
            borderStrong = scheme.outline,
            accentButton = scheme.primary,
            accentContainer = scheme.primaryContainer,
            onAccentContainer = scheme.onPrimaryContainer,
            accentText = scheme.primary,
            accentMuted = scheme.primaryContainer,
            secondaryContainer = scheme.secondaryContainer,
            onSecondaryContainer = scheme.onSecondaryContainer,
            codeBackground = scheme.surfaceContainerLowest,
            userBubble = scheme.secondaryContainer,
        )
    } else {
        LightHarnessColors.copy(
            background = scheme.background,
            surface = scheme.surface,
            surfaceElevated = scheme.surfaceContainer,
            surfaceHigh = scheme.surfaceContainerHigh,
            border = scheme.outlineVariant,
            borderStrong = scheme.outline,
            accentButton = scheme.primary,
            accentContainer = scheme.primaryContainer,
            onAccentContainer = scheme.onPrimaryContainer,
            accentText = scheme.primary,
            accentMuted = scheme.primaryContainer,
            secondaryContainer = scheme.secondaryContainer,
            onSecondaryContainer = scheme.onSecondaryContainer,
            codeBackground = scheme.surfaceContainerLowest,
            userBubble = scheme.secondaryContainer,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Root theme provider.
 *
 * @param darkTheme     resolved dark mode (already considering user override).
 * @param dynamicColor  Material You dynamic palette when the device supports it.
 * @param fontScale     user font-size multiplier (1f = system default).
 */
@Composable
fun HarnessThemeProvider(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colors = if (dynamicColor) {
        dynamicHarnessColors(darkTheme) ?: if (darkTheme) DarkHarnessColors else LightHarnessColors
    } else {
        if (darkTheme) DarkHarnessColors else LightHarnessColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            WindowCompatCompat.setStatusBarAppearance(window, darkTheme)
        }
    }

    val base = LocalDensity.current
    val scaled = Density(base.density, base.fontScale * fontScale)

    CompositionLocalProvider(LocalHarnessColors provides colors) {
        MaterialTheme(
            colorScheme = mapScheme(colors),
            typography = HarnessTypography,
            shapes = androidx.compose.material3.Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(Radii.sm),
                small = androidx.compose.foundation.shape.RoundedCornerShape(Radii.sm),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(Radii.md),
                large = androidx.compose.foundation.shape.RoundedCornerShape(Radii.lg),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(Radii.xl),
            ),
        ) {
            CompositionLocalProvider(LocalDensity provides scaled, content = content)
        }
    }
}

/** Helper kept separate so the status-bar side effect stays small and testable. */
private object WindowCompatCompat {
    fun setStatusBarAppearance(window: android.view.Window, darkTheme: Boolean) {
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !darkTheme
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = !darkTheme
    }
}
