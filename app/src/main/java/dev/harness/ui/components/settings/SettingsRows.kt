package dev.harness.ui.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.harness.ui.components.HBottomSheet
import dev.harness.ui.components.touchTargetClickable
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.HarnessThemeProvider
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/**
 * Settings library rows (Frente B). Every row reads the design tokens, keeps
 * ≥48 dp touch targets and merges semantics for TalkBack; the hub and all
 * category screens compose these instead of hand-rolled layouts.
 */

/** Container background used by every row (uniform surface + radius). */
@Composable
private fun Modifier.rowSurface(): Modifier {
    val colors = HarnessTheme
    return this
        .clip(Radii.shapeLg)
        .background(colors.surfaceElevated)
}

/** Row for the settings HUB: icon, title and the current value as subtitle. */
@Composable
fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = HarnessTheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .rowSurface()
            .touchTargetClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(Radii.shapeMd)
                .background(colors.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onAccentContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Base two-line row layout shared by switch/choice/info rows. */
@Composable
private fun RowShell(
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = HarnessTheme
    val clickable = if (onClick != null) {
        Modifier.touchTargetClickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .rowSurface()
            .then(clickable)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        leadingIcon?.let {
            Icon(it, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.textPrimary else colors.textTertiary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        trailing()
    }
}

/** Boolean setting: labeled switch with the state in [contentDescription]. */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = HarnessTheme
    val stateDescription = if (checked) "ligado" else "desligado"
    RowShell(
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$title. $stateDescription" },
        enabled = enabled,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.accentButton,
                    checkedThumbColor = colors.onAccent,
                    uncheckedTrackColor = colors.surfaceHigh,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedBorderColor = colors.borderStrong,
                ),
            )
        },
    )
}

/**
 * Single-choice setting rendered inline; tapping opens a bottom sheet with
 * radio options ([HBottomSheetChoice] handles the sheet).
 */
@Composable
fun SettingsChoiceRow(
    title: String,
    currentLabel: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = HarnessTheme
    RowShell(
        title = title,
        subtitle = subtitle,
        leadingIcon = leadingIcon,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
        trailing = {
            Text(
                currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.accentText,
            )
        },
    )
}

/**
 * Discrete slider row (N anchors). Used for the font scale: more predictable
 * than a continuous slider and exposes the value via contentDescription.
 */
@Composable
fun SettingsSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = HarnessTheme
    Column(
        modifier
            .fillMaxWidth()
            .rowSurface()
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accentText,
                modifier = Modifier.semantics { contentDescription = "$title: $valueLabel" },
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.xs)
                .semantics { contentDescription = "$title: $valueLabel" },
            colors = SliderDefaults.colors(
                thumbColor = colors.accentButton,
                activeTrackColor = colors.accentButton,
                inactiveTrackColor = colors.surfaceHigh,
            ),
        )
    }
}

/** Read-only information row (versions, storage, where the key is stored). */
@Composable
fun SettingsInfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = HarnessTheme
    RowShell(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        trailing = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
            )
        },
    )
}

/**
 * Destructive action row (danger zone): separate from normal rows, red
 * styling; the caller shows the confirmation dialog.
 */
@Composable
fun SettingsDangerRow(
    title: String,
    description: String,
    actionLabel: String,
    modifier: Modifier = Modifier,
    onAction: () -> Unit,
) {
    val colors = HarnessTheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.shapeLg)
            .background(colors.toolErrorContainer)
            .touchTargetClickable(role = Role.Button, onClick = onAction)
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.toolErrorText)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.toolErrorText)
        }
        Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = colors.toolErrorText)
    }
}

/** Bottom sheet with radio options for [SettingsChoiceRow]. */
@Composable
fun HBottomSheetChoice(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    HBottomSheet(title = title, onDismiss = onDismiss) {
        options.forEachIndexed { index, option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .touchTargetClickable(role = Role.RadioButton) {
                        onSelect(index)
                        onDismiss()
                    }
                    .padding(horizontal = Space.sm, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                RadioButton(selected = index == selectedIndex, onClick = null)
                Text(option, style = MaterialTheme.typography.bodyLarge, color = HarnessTheme.textPrimary)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews: dark + light + large font (PROMPT §2 rule 2)
// ---------------------------------------------------------------------------

@Composable
private fun SettingsSample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsCategoryRow(
            icon = Icons.Default.Settings,
            title = "Aparência",
            subtitle = "Tema: Escuro",
            onClick = {},
        )
        SettingsSwitchRow(
            title = "Retomar última sessão",
            subtitle = "Abre o projeto na sessão mais recente",
            checked = true,
            onCheckedChange = {},
        )
        SettingsChoiceRow(
            title = "Tema",
            currentLabel = "Escuro",
            onClick = {},
        )
        var slider by remember { mutableFloatStateOf(1f) }
        SettingsSliderRow(
            title = "Tamanho da fonte",
            valueLabel = "M",
            value = slider,
            steps = 2,
            onValueChange = { slider = it },
        )
        SettingsInfoRow(title = "Versão do Pi", value = "1.6.0")
        SettingsDangerRow(
            title = "Reinstalar ambiente",
            description = "Remove o Alpine e as ferramentas instaladas.",
            actionLabel = "Reinstalar",
            onAction = {},
        )
    }
}

@Preview(name = "Escuro", showBackground = true, backgroundColor = 0xFF0B0E14, widthDp = 360)
@Composable
private fun SettingsRowsDarkPreview() {
    HarnessThemeProvider(darkTheme = true, dynamicColor = false, fontScale = 1f) {
        MaterialTheme { SettingsSample() }
    }
}

@Preview(name = "Claro", showBackground = true, backgroundColor = 0xFFF7F8FB, widthDp = 360)
@Composable
private fun SettingsRowsLightPreview() {
    HarnessThemeProvider(darkTheme = false, dynamicColor = false, fontScale = 1f) {
        MaterialTheme { SettingsSample() }
    }
}

@Preview(name = "Fonte grande", showBackground = true, backgroundColor = 0xFF0B0E14, widthDp = 360, fontScale = 1.4f)
@Composable
private fun SettingsRowsLargeFontPreview() {
    HarnessThemeProvider(darkTheme = true, dynamicColor = false, fontScale = 1.3f) {
        MaterialTheme { SettingsSample() }
    }
}
