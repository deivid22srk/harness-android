package dev.harness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.harness.core.env.ProvisionState
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/** Calm onboarding: one title, one progress bar, short non-technical step labels. */
@Composable
fun OnboardingScreen(
    state: ProvisionState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Harness",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = "Preparando o ambiente de codificação",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xxl))

        when (state) {
            is ProvisionState.Failed -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.md))
                        .background(colors.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(Radii.md))
                        .padding(Space.lg),
                ) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = colors.toolError)
                }
                Spacer(Modifier.height(Space.lg))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentMuted, contentColor = colors.textPrimary),
                ) { Text("Tentar novamente") }
            }
            else -> {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                    color = colors.accent,
                    trackColor = colors.accentMuted,
                )
                Spacer(Modifier.height(Space.lg))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(state.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    Text(state.detailText, style = MonoStyle, color = colors.textTertiary)
                }
                Spacer(Modifier.height(Space.lg))
                val steps = provisionSteps(state)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    steps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (step.done) colors.toolOk else colors.accentMuted),
                            )
                            Text(
                                step.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (step.active) colors.textPrimary else colors.textTertiary,
                            )
                            if (step.active) {
                                Text("…", style = MonoStyle, color = colors.textSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Space.lg))
                Text(
                    if (state is ProvisionState.Idle) "Iniciando…" else "Na primeira vez baixamos o ambiente (~4 MB) e instalamos as ferramentas. Depois disso é instantâneo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private data class ProvisionStep(val label: String, val done: Boolean, val active: Boolean)

/** Maps the coarse provisioning state to the visible checklist. */
private fun provisionSteps(state: ProvisionState): List<ProvisionStep> {
    val order = listOf(
        "Verificando ambiente",
        "Baixando ambiente",
        "Extraindo ambiente",
        "Instalando ferramentas",
        "Preparando o Pi",
    )
    val currentIndex = when (state) {
        is ProvisionState.Idle -> -1
        is ProvisionState.Preparing -> 0
        is ProvisionState.Downloading -> 1
        is ProvisionState.Extracting -> 2
        is ProvisionState.Configuring -> 2
        is ProvisionState.InstallingPackages -> 3
        is ProvisionState.InstallingAgent -> 4
        is ProvisionState.Ready -> order.size
        is ProvisionState.Failed -> -1
    }
    return order.mapIndexed { index, label ->
        ProvisionStep(
            label = label,
            done = state is ProvisionState.Ready || index < currentIndex,
            active = index == currentIndex,
        )
    }
}
