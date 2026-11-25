package dev.harness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.R
import dev.harness.core.pi.ConnectionState
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/*
 * Connection diagnostics sheet (PROMPT §4.3.9): version, uptime, state, last
 * error, stderr tail, provider test and quick actions. Rendered by
 * ChatScreen when the connection indicator is tapped.
 */

/** Metadata row used across the sheet. */
@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    val colors = HarnessTheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            value,
            style = if (mono) MonoStyle else MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = if (mono) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ConnectionSheet(
    state: ConnectionState,
    piVersion: String?,
    uptime: String,
    lastError: String?,
    stderrTail: String,
    providerTest: String?,
    onTestProvider: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HarnessTheme
    val clipboard = LocalClipboardManager.current
    val stateLabel = stringResource(
        when (state) {
            ConnectionState.Connected -> R.string.conn_connected
            ConnectionState.Starting -> R.string.conn_starting
            ConnectionState.NoProvider -> R.string.conn_no_provider
            ConnectionState.Restarting -> R.string.conn_restarting
            ConnectionState.Stopped -> R.string.conn_stopped
            ConnectionState.Failed -> R.string.conn_failed
        },
    )

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        InfoRow(stringResource(R.string.conn_sheet_state), stateLabel)
        InfoRow(stringResource(R.string.conn_sheet_version), piVersion ?: "—")
        InfoRow(stringResource(R.string.conn_sheet_uptime), uptime)
        if (!lastError.isNullOrBlank()) {
            Text(
                lastError,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.toolErrorText,
            )
        }

        // Provider test result (after the button is pressed).
        providerTest?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        // stderr tail — bounded mono block.
        if (stderrTail.isNotBlank()) {
            Text(
                stringResource(R.string.conn_sheet_stderr_title),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Text(
                stderrTail.takeLast(4000),
                style = MonoStyle.copy(fontFamily = FontFamily.Monospace),
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radii.shapeSm)
                    .background(colors.codeBackground)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(Space.md),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            HButton(
                text = stringResource(R.string.conn_sheet_test),
                onClick = onTestProvider,
                variant = HButtonDefaults.Variant.Secondary,
                modifier = Modifier.weight(1f),
            )
            HButton(
                text = stringResource(R.string.action_copy),
                onClick = {
                    val full = buildString {
                        appendLine(stateLabel)
                        appendLine("pi: ${piVersion ?: "—"}")
                        appendLine("uptime: $uptime")
                        lastError?.let { appendLine(it) }
                        providerTest?.let { appendLine(it) }
                        appendLine()
                        appendLine(stderrTail)
                    }
                    clipboard.setText(AnnotatedString(full))
                },
                variant = HButtonDefaults.Variant.Secondary,
                modifier = Modifier.weight(1f),
            )
        }
        HButton(
            text = stringResource(R.string.conn_sheet_restart),
            onClick = {
                onRestart()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
