package dev.harness.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.harness.R
import dev.harness.core.env.ProvisionState
import dev.harness.ui.components.CollapsibleSection
import dev.harness.ui.components.HButton
import dev.harness.ui.components.HButtonDefaults
import dev.harness.ui.components.HCodeBlock
import dev.harness.ui.components.HProgressBar
import dev.harness.ui.components.ProgressStep
import dev.harness.ui.components.HProgressSteps
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Space

/**
 * "Preparando o ambiente": real step checklist, determinate progress with ETA,
 * collapsible technical log and human error states with retry/copy actions.
 * Provisioning runs in the app-scoped container, so closing the app mid-way
 * resumes automatically when reopened.
 */
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
            .padding(Space.xl)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.accentText,
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xxl))

        when (state) {
            is ProvisionState.Failed -> OnboardingFailure(state, onRetry)
            is ProvisionState.Ready -> {
                // The root VM advances to Projects; nothing to render here.
            }
            else -> OnboardingProgress(state)
        }
    }
}

@Composable
private fun OnboardingProgress(state: ProvisionState) {
    val colors = HarnessTheme
    val progress = state.progress.coerceIn(0f, 1f)
    HProgressBar(progress)
    Spacer(Modifier.height(Space.xs))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = provisionStatusLabel(state),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            state.etaText()?.let { eta ->
                Text(eta, style = MonoStyle, color = colors.textTertiary)
            }
            Text(
                stringResource(R.string.onboarding_progress_format, (progress * 100).toInt()),
                style = MonoStyle,
                color = colors.textSecondary,
            )
        }
    }
    Spacer(Modifier.height(Space.lg))
    HProgressSteps(provisionSteps(state))
    Spacer(Modifier.height(Space.xl))
    Text(
        stringResource(R.string.onboarding_first_time_note),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
        textAlign = TextAlign.Center,
    )
}

/** Localized phase label — core's ProvisionState.label stays untranslated. */
@Composable
private fun provisionStatusLabel(state: ProvisionState): String = stringResource(
    when (state) {
        is ProvisionState.Idle -> R.string.onboarding_starting
        is ProvisionState.Preparing -> R.string.onboarding_step_check
        is ProvisionState.Downloading -> R.string.onboarding_step_download
        is ProvisionState.Extracting, is ProvisionState.Configuring -> R.string.onboarding_step_extract
        is ProvisionState.InstallingPackages -> R.string.onboarding_step_packages
        is ProvisionState.InstallingAgent -> R.string.onboarding_step_agent
        is ProvisionState.Ready -> R.string.onboarding_step_agent
        is ProvisionState.Failed -> R.string.onboarding_error_title
    },
)

/** Rough ETA heuristics per phase (seconds, calibrated for ~4 MB + apk/npm). */
private fun ProvisionState.etaText(): String? = when (this) {
    is ProvisionState.Downloading -> {
        val totalSeconds = 25
        val remaining = (totalSeconds * (1f - fraction)).toInt().coerceAtLeast(1)
        if (remaining >= 60) "~${remaining / 60}min" else "~${remaining}s"
    }
    is ProvisionState.Extracting -> "~10s"
    is ProvisionState.InstallingPackages -> "~${((total - step).coerceAtLeast(1) * 12)}s"
    is ProvisionState.InstallingAgent -> "~${((total - step).coerceAtLeast(1) * 15)}s"
    else -> null
}

@Composable
private fun OnboardingFailure(
    state: ProvisionState.Failed,
    onRetry: () -> Unit,
) {
    val colors = HarnessTheme
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            stringResource(R.string.onboarding_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.toolErrorText,
        )
        state.diagnostics?.let { diag ->
            CollapsibleSection(title = stringResource(R.string.onboarding_log_title)) {
                HCodeBlock(code = diag, language = "log", modifier = Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            HButton(
                stringResource(R.string.onboarding_retry),
                onRetry,
                variant = HButtonDefaults.Variant.Primary,
            )
            HButton(
                stringResource(R.string.onboarding_copy_diag),
                {
                    clipboard.setText(
                        AnnotatedString((state.diagnostics ?: state.message).orEmpty()),
                    )
                },
                variant = HButtonDefaults.Variant.Secondary,
            )
        }
    }
}

@Composable
private fun provisionSteps(state: ProvisionState): List<ProgressStep> {
    val labels = listOf(
        stringResource(R.string.onboarding_step_check),
        stringResource(R.string.onboarding_step_download),
        stringResource(R.string.onboarding_step_extract),
        stringResource(R.string.onboarding_step_packages),
        stringResource(R.string.onboarding_step_agent),
    )
    val currentIndex = when (state) {
        is ProvisionState.Idle -> -1
        is ProvisionState.Preparing -> 0
        is ProvisionState.Downloading -> 1
        is ProvisionState.Extracting -> 2
        is ProvisionState.Configuring -> 2
        is ProvisionState.InstallingPackages -> 3
        is ProvisionState.InstallingAgent -> 4
        is ProvisionState.Ready -> labels.size
        is ProvisionState.Failed -> -1
    }
    return ProgressStep.build(labels, currentIndex)
}
