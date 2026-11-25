package dev.harness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.harness.core.state.NoticeLevel
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/** Centered empty state: icon, title, description and optional action. */
@Composable
fun HEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = HarnessTheme
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        icon?.let {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(Radii.shapeLg)
                    .background(colors.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(it, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(26.dp))
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            HButton(actionLabel, onAction, variant = HButtonDefaults.Variant.Secondary)
        }
    }
}

/**
 * Error state with a human message, optional technical detail (collapsible)
 * and up to two actions (retry / copy diagnostics).
 */
@Composable
fun HErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    diagnostics: String? = null,
    diagnosticsLabel: String = "Detalhes técnicos",
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = HarnessTheme
    Column(
        modifier = modifier.fillMaxWidth().padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(Radii.shapeLg)
                .background(colors.toolErrorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = colors.toolErrorText,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (retryLabel != null && onRetry != null) {
                HButton(retryLabel, onRetry, variant = HButtonDefaults.Variant.Primary)
            }
            if (secondaryLabel != null && onSecondary != null) {
                HButton(secondaryLabel, onSecondary, variant = HButtonDefaults.Variant.Secondary)
            }
        }
        diagnostics?.let { detail ->
            CollapsibleSection(title = diagnosticsLabel) {
                HCodeBlock(code = detail, language = "log", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** App-level snackbar rendering [dev.harness.core.state.NoticeLevel]-tagged messages. */
@Composable
fun HNoticeSnackbarHost(
    hostState: SnackbarHostState,
    message: String?,
    level: NoticeLevel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    LaunchedEffect(message) {
        if (message != null) {
            val shown = hostState.showSnackbar(
                message = message,
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
            if (shown == androidx.compose.material3.SnackbarResult.Dismissed) onDismiss()
        }
    }
    SnackbarHost(hostState, modifier) { data ->
        val colors = HarnessTheme
        val (container, content) = when (level) {
            NoticeLevel.Error -> colors.toolErrorContainer to colors.toolErrorText
            NoticeLevel.Warning -> colors.toolRunningContainer to colors.toolRunning
            NoticeLevel.Info -> colors.accentContainer to colors.onAccentContainer
        }
        Snackbar(
            containerColor = container,
            contentColor = content,
            actionContentColor = content,
            shape = RoundedCornerShape(Radii.md),
            action = {
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = { onAction(); onDismiss() }) {
                        Text(actionLabel, color = content)
                    }
                }
            },
        ) { Text(data.visuals.message) }
    }
}
