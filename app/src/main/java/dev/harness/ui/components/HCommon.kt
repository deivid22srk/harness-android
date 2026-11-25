package dev.harness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.harness.ui.theme.MinTouchTarget

/**
 * Clickable with a guaranteed minimum touch target of 48 dp, a Material
 * ripple, and an explicit [Role] for TalkBack. Used by every interactive
 * element in the library so accessibility is enforced centrally.
 */
@Composable
fun Modifier.touchTargetClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onLongClick: (() -> Unit)? = null,
    shape: Shape? = null,
    rippleColor: Color? = null,
    onClick: () -> Unit,
): Modifier {
    // One interaction source per call site instance, not per recomposition.
    val interaction = remember { MutableInteractionSource() }
    val clickable = if (onLongClick != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
            interactionSource = interaction,
            indication = ripple(color = rippleColor ?: Color.Unspecified),
        )
    } else {
        Modifier.combinedClickable(
            enabled = enabled,
            role = role,
            onClick = onClick,
            interactionSource = interaction,
            indication = ripple(color = rippleColor ?: Color.Unspecified),
        )
    }
    return this
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .then(clickable)
        .defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
}

/** Solid background helper for containers. */
fun Modifier.containerBackground(color: Color, shape: Shape): Modifier =
    this.clip(shape).background(color, shape)
