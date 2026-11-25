package dev.harness.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/** One line of the provisioning checklist. */
data class ProgressStep(
    val label: String,
    val state: State,
) {
    enum class State { Pending, Active, Done, Failed }

    companion object {
        /** Convenience factory: marks every step before [activeIndex] as done. */
        fun build(labels: List<String>, activeIndex: Int, failed: Boolean = false): List<ProgressStep> =
            labels.mapIndexed { index, label ->
                when {
                    failed && index == activeIndex -> ProgressStep(label, State.Failed)
                    index < activeIndex -> ProgressStep(label, State.Done)
                    index == activeIndex -> ProgressStep(label, State.Active)
                    else -> ProgressStep(label, State.Pending)
                }
            }
    }
}

/** Vertical checklist with colored dots — used by onboarding. */
@Composable
fun HProgressSteps(
    steps: List<ProgressStep>,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    Column(modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        steps.forEach { step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                val dotColor = when (step.state) {
                    ProgressStep.State.Done -> colors.toolOk
                    ProgressStep.State.Active -> colors.accent
                    ProgressStep.State.Failed -> colors.toolError
                    ProgressStep.State.Pending -> colors.borderStrong
                }
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dotColor),
                )
                Text(
                    step.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (step.state == ProgressStep.State.Active) FontWeight.Medium else FontWeight.Normal,
                    color = when (step.state) {
                        ProgressStep.State.Pending -> colors.textTertiary
                        else -> colors.textPrimary
                    },
                )
            }
        }
    }
}

/** Determinate progress bar with animated fraction. */
@Composable
fun HProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val colors = HarnessTheme
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 200),
        label = "provisionProgress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(Radii.shapePill)
            .background(colors.surfaceHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(Radii.shapePill)
                .background(color ?: colors.accent),
        )
    }
}
