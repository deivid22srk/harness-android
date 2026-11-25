package dev.harness.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/**
 * Branded card: `surfaceContainer` background, hairline border, `Radii.md`
 * corners. Optionally clickable (adds ripple + Role, keeps the 48 dp rule).
 */
@Composable
fun HCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(Space.lg),
    shape: Shape = Radii.shapeMd,
    container: Color? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = HarnessTheme
    val bg = container ?: colors.surfaceElevated
    val cardShape = shape
    val base = if (onClick != null) {
        Modifier.touchTargetClickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
            shape = cardShape,
            rippleColor = colors.textPrimary.copy(alpha = 0.06f),
        )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(base)
            .clip(cardShape)
            .background(bg, cardShape)
            .border(1.dp, colors.border, cardShape),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Hairline divider matching the outline token. */
@Composable
fun HDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(HarnessTheme.border),
    )
}

/** Small status pill: colored dot + label, used for connection/status. */
@Composable
fun HBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val colors = HarnessTheme
    Row(
        modifier = modifier
            .clip(Radii.shapePill)
            .background(if (filled) color.copy(alpha = 0.16f) else colors.surfaceElevated)
            .padding(horizontal = Space.sm, vertical = Space.xs)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(Modifier.size(6.dp).clip(Radii.shapePill).background(color))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) color else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shimmering skeleton block for loading placeholders. */
@Composable
fun HSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    rounded: Shape = Radii.shapeSm,
) {
    val colors = HarnessTheme
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(rounded)
            .background(colors.surfaceHigh.copy(alpha = alpha)),
    )
}

/** Selectable chip used by the composer and settings. */
@Composable
fun HChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    selectedColor: Color? = null,
    selectedTextColor: Color? = null,
) {
    val colors = HarnessTheme
    val bg = if (selected) (selectedColor ?: colors.accentContainer) else colors.surfaceElevated
    val fg = if (selected) (selectedTextColor ?: colors.onAccentContainer) else colors.textSecondary
    Row(
        modifier = modifier
            .touchTargetClickable(enabled = enabled, role = Role.Button, onClick = onClick, shape = Radii.shapePill)
            .background(bg, Radii.shapePill)
            .defaultMinSize(minHeight = 40.dp)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}
