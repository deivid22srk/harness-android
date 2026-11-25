package dev.harness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MinTouchTarget
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/** Button emphasis variants. */
object HButtonDefaults {
    enum class Variant { Primary, Secondary, Ghost, Danger }
}

/**
 * Primary action button.
 *  - [HButtonDefaults.Variant.Primary]   filled brand button (main action)
 *  - [HButtonDefaults.Variant.Secondary] tonal container (alternative action)
 *  - [HButtonDefaults.Variant.Ghost]     text-only (tertiary action)
 *  - [HButtonDefaults.Variant.Danger]    destructive action
 */
@Composable
fun HButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: HButtonDefaults.Variant = HButtonDefaults.Variant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    iconDescription: String? = null,
) {
    val colors = HarnessTheme
    val shape = RoundedCornerShape(Radii.md)
    val container: Color
    val content: Color
    when (variant) {
        HButtonDefaults.Variant.Primary -> {
            container = if (enabled) colors.accentButton else colors.accentMuted
            content = if (enabled) colors.onAccent else colors.textTertiary
        }
        HButtonDefaults.Variant.Secondary -> {
            container = if (enabled) colors.accentContainer else colors.surfaceElevated
            content = if (enabled) colors.onAccentContainer else colors.textTertiary
        }
        HButtonDefaults.Variant.Ghost -> {
            container = Color.Transparent
            content = if (enabled) colors.accentText else colors.textTertiary
        }
        HButtonDefaults.Variant.Danger -> {
            container = if (enabled) colors.toolErrorContainer else colors.surfaceElevated
            content = if (enabled) colors.toolErrorText else colors.textTertiary
        }
    }

    Box(
        modifier = modifier
            .touchTargetClickable(
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
                shape = shape,
                rippleColor = content.copy(alpha = 0.15f),
            )
            .background(container, shape)
            .defaultMinSize(minWidth = 64.dp, minHeight = MinTouchTarget)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = iconDescription,
                        tint = content,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

/** Icon-only button with a guaranteed 48 dp touch target and semantics. */
@Composable
fun HIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    size: Dp = MinTouchTarget,
    filled: Boolean = false,
) {
    val colors = HarnessTheme
    val resolvedTint = tint ?: colors.textSecondary
    val shape = RoundedCornerShape(Radii.md)
    Box(
        modifier = modifier
            .touchTargetClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                shape = shape,
                rippleColor = resolvedTint.copy(alpha = 0.15f),
            )
            .then(
                if (filled) Modifier.background(
                    if (enabled) colors.accentContainer else colors.surfaceElevated,
                    shape,
                ) else Modifier,
            )
            .size(size),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) resolvedTint else colors.textTertiary,
            modifier = Modifier.size(22.dp),
        )
    }
}
