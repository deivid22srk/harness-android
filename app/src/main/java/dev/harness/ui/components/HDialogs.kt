package dev.harness.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/**
 * Branded dialog on top of the M3 [AlertDialog] (theme colors already match
 * via the mapped ColorScheme). Keeps call sites terse and consistent.
 */
@Composable
fun HDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: (@Composable () -> Unit)? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
) {
    val colors = HarnessTheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.surfaceElevated,
        title = { Text(title, color = colors.textPrimary) },
        text = text,
        confirmButton = {
            if (confirmLabel != null && onConfirm != null) {
                TextButton(enabled = confirmEnabled, onClick = onConfirm) {
                    Text(confirmLabel, color = if (confirmEnabled) colors.accentText else colors.textTertiary)
                }
            }
        },
        dismissButton = {
            if (dismissLabel != null) {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel, color = colors.textSecondary)
                }
            }
        },
    )
}

/** Branded modal bottom sheet with drag handle and surface token. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = HarnessTheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceElevated,
        shape = Radii.shapeXl,
        modifier = modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            content()
        }
    }
}

/**
 * Expandable section header ("Raciocínio", "Detalhes técnicos"). Expansion
 * survives process death via [rememberSaveable].
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val colors = HarnessTheme
    val expandLabel = androidx.compose.ui.res.stringResource(dev.harness.R.string.a11y_expand, title)
    val collapseLabel = androidx.compose.ui.res.stringResource(dev.harness.R.string.a11y_collapse, title)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .touchTargetClickable(role = Role.Button, onClick = { open = !open }, shape = Radii.shapeSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.xs),
        ) {
            Icon(
                if (open) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (open) collapseLabel else expandLabel,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.padding(top = Space.sm)) { content() }
        }
    }
}
