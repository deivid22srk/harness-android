package dev.harness.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.R
import dev.harness.core.state.DiffLine
import dev.harness.core.state.DiffSummary
import dev.harness.core.state.ToolItem
import dev.harness.core.state.ToolStatus
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space
import androidx.compose.ui.res.stringResource
import java.util.Locale

/**
 * One agent tool call ("leu arquivo", "editou", "rodou comando"): compact row
 * that expands into a readable diff and/or output. Expansion survives list
 * recycling (rememberSaveable).
 */
@Composable
fun ToolCallCard(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    var expanded by rememberSaveable(tool.id) { androidx.compose.runtime.mutableStateOf(false) }

    val statusLabel = stringResource(
        when (tool.status) {
            ToolStatus.Running -> R.string.tool_status_running
            ToolStatus.Ok -> R.string.tool_status_ok
            ToolStatus.Error -> R.string.tool_status_error
            ToolStatus.Cancelled -> R.string.tool_status_cancelled
        },
    )
    val rowDescription = "${tool.label}. $statusLabel. " +
        tool.summary.lineSequence().firstOrNull().orEmpty().take(120) +
        (tool.durationMs?.let { ". ${formatDuration(it)}" } ?: "")

    Column(
        modifier
            .fillMaxWidth()
            .clip(Radii.shapeSm)
            .semantics(mergeDescendants = true) { contentDescription = rowDescription },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .touchTargetClickable(
                    role = Role.Button,
                    onClick = { expanded = !expanded },
                    shape = Radii.shapeSm,
                )
                .padding(horizontal = Space.xs, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Chevron(expanded)
            StatusDot(tool.status)
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tool.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                    )
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = toolStatusColor(tool.status),
                    )
                }
                if (tool.summary.isNotBlank()) {
                    Text(
                        tool.summary.lineSequence().firstOrNull().orEmpty(),
                        style = MonoStyle,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            tool.diff?.let {
                Text("+${it.added}", style = MonoStyle, color = colors.toolOk)
                Text("−${it.removed}", style = MonoStyle, color = colors.toolError)
            }
            tool.durationMs?.let {
                Text(formatDuration(it), style = MonoStyle, color = colors.textTertiary)
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Space.lg, bottom = Space.sm, end = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                tool.diff?.let { DiffView(it) }
                if (tool.output.isNotBlank()) {
                    HOutputBlock(text = tool.output.trim(), maxLines = 14)
                }
            }
        }
    }
}

@Composable
private fun Chevron(expanded: Boolean) {
    val colors = HarnessTheme
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(150),
        label = "chevron",
    )
    androidx.compose.material3.Icon(
        Icons.Default.KeyboardArrowRight,
        contentDescription = null,
        tint = colors.textTertiary,
        modifier = Modifier
            .size(16.dp)
            .rotate(rotation),
    )
}

@Composable
private fun StatusDot(status: ToolStatus) {
    val colors = HarnessTheme
    if (status == ToolStatus.Running) {
        val transition = rememberInfiniteTransition(label = "toolPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "toolPulseAlpha",
        )
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.toolRunning.copy(alpha = alpha)),
        )
    } else {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(toolStatusColor(status)),
        )
    }
}

@Composable
private fun toolStatusColor(status: ToolStatus): Color = when (status) {
    ToolStatus.Running -> HarnessTheme.toolRunning
    ToolStatus.Ok -> HarnessTheme.toolOk
    ToolStatus.Error -> HarnessTheme.toolError
    ToolStatus.Cancelled -> HarnessTheme.textTertiary
}

/** Readable diff: path header + colored added/removed/context lines. */
@Composable
fun DiffView(diff: DiffSummary, modifier: Modifier = Modifier) {
    val colors = HarnessTheme
    val shape = RoundedCornerShape(Radii.sm)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.codeBackground, shape)
            .border(1.dp, colors.border, shape)
            .padding(Space.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            diff.path,
            style = MonoStyle,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        SelectionContainer(
            Modifier.horizontalScroll(rememberScrollState()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                diff.previewLines.forEach { line ->
                    val (prefix, tint) = when (line.kind) {
                        DiffLine.Kind.Added -> "+" to colors.toolOk
                        DiffLine.Kind.Removed -> "−" to colors.toolError
                        DiffLine.Kind.Header -> "" to colors.textTertiary
                        DiffLine.Kind.Context -> " " to colors.textSecondary
                    }
                    if (line.kind == DiffLine.Kind.Header) {
                        Text(
                            line.text,
                            style = MonoStyle.copy(fontWeight = FontWeight.Medium),
                            color = tint,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    } else {
                        Text(
                            "$prefix ${line.text}",
                            style = MonoStyle,
                            color = tint,
                            maxLines = 4,
                        )
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format(Locale.ROOT, "%.1fs", ms / 1000.0)
    else -> "${ms / 60_000}m"
}
