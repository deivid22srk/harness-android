package dev.harness.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.HarnessUiState
import dev.harness.HarnessViewModel
import dev.harness.R
import dev.harness.core.settings.Provider
import dev.harness.core.settings.Providers
import dev.harness.ui.components.CollapsibleSection
import dev.harness.ui.components.HButton
import dev.harness.ui.components.HButtonDefaults
import dev.harness.ui.components.HCard
import dev.harness.ui.components.HChip
import dev.harness.ui.components.HCodeBlock
import dev.harness.ui.components.HDialog
import dev.harness.ui.components.HPasswordField
import dev.harness.ui.components.HSectionTitle
import dev.harness.ui.components.HTextField
import dev.harness.ui.components.settings.SettingsChoiceRow
import dev.harness.ui.components.settings.SettingsDangerRow
import dev.harness.ui.components.settings.SettingsInfoRow
import dev.harness.ui.components.settings.SettingsSliderRow
import dev.harness.ui.components.settings.SettingsSwitchRow
import dev.harness.ui.components.touchTargetClickable
import dev.harness.ui.screens.thinkingLabel
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/**
 * Category screens of the settings hub (Frente B). Each section owns ONLY its
 * category's settings, uses the shared rows and holds zero business logic —
 * every change is dispatched to [HarnessViewModel].
 */

/** Conta e provedor: provedores, chave, modelos e nível de raciocínio. */
@Composable
fun AccountSection(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
) {
    var showAddCustom by rememberSaveable { mutableStateOf(false) }
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        HSectionTitle(stringResource(R.string.settings_section_provider))
        ProviderList(
            providers = uiState.providers,
            selected = uiState.selectedProvider,
            onSelect = viewModel::selectProvider,
            onRemove = viewModel::removeCustomProvider,
        )
        HButton(
            stringResource(R.string.settings_add_provider),
            onClick = { showAddCustom = true },
            variant = HButtonDefaults.Variant.Ghost,
        )

        val activeProvider = uiState.providers.firstOrNull { it.id == uiState.selectedProvider }
        HSectionTitle(
            stringResource(
                R.string.settings_key_section,
                activeProvider?.label ?: uiState.selectedProvider,
            ),
        )
        ProviderKeySection(
            uiState = uiState,
            activeProvider = activeProvider,
            onSave = viewModel::saveApiKey,
        )

        HSectionTitle(stringResource(R.string.settings_section_models))
        HButton(
            stringResource(R.string.settings_get_models),
            onClick = { viewModel.openModelsDialog(uiState.selectedProvider) },
            variant = HButtonDefaults.Variant.Secondary,
        )
        Text(
            stringResource(R.string.settings_models_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        HSectionTitle(stringResource(R.string.settings_section_thinking))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            listOf("off", "low", "medium", "high").forEach { level ->
                HChip(
                    text = thinkingLabel(level),
                    selected = uiState.thinkingLevel == level,
                    onClick = { viewModel.pickThinking(level) },
                )
            }
        }
        Spacer(Modifier.height(Space.xl))
    }

    if (showAddCustom) {
        AddCustomProviderDialog(
            onDismiss = { showAddCustom = false },
            onAdd = { id, label, baseUrl, api, models ->
                viewModel.addCustomProvider(id, label, baseUrl, api, models)
                showAddCustom = false
            },
        )
    }

    uiState.modelsDialogProvider?.let { providerId ->
        val label = uiState.providers.firstOrNull { it.id == providerId }?.label ?: providerId
        ModelsDialog(
            providerLabel = label,
            loading = uiState.modelsLoading,
            models = uiState.models,
            error = uiState.modelsError,
            onToggle = viewModel::toggleModel,
            onSelectAll = viewModel::selectAllModels,
            onSave = viewModel::saveModelSelection,
            onDismiss = viewModel::closeModelsDialog,
        )
    }
}

/** Aparência: tema, cores dinâmicas e tamanho da fonte. */
@Composable
fun AppearanceSection(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
) {
    var themeSheetOpen by rememberSaveable { mutableStateOf(false) }
    val themeOptions = listOf(
        stringResource(R.string.settings_theme_system),
        stringResource(R.string.settings_theme_light),
        stringResource(R.string.settings_theme_dark),
    )
    val themeIds = listOf("system", "light", "dark")
    val currentTheme = themeIds.indexOf(uiState.themeMode)
    val fontLabels = listOf("P", "M", "G", "GG")
    val fontScales = listOf(0.9f, 1f, 1.15f, 1.3f)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        SettingsChoiceRow(
            title = stringResource(R.string.settings_theme_title),
            subtitle = stringResource(R.string.settings_theme_subtitle),
            currentLabel = themeOptions.getOrElse(currentTheme) { themeOptions[0] },
            onClick = { themeSheetOpen = true },
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_dynamic_label),
            subtitle = stringResource(R.string.settings_dynamic_note),
            checked = uiState.dynamicColor,
            onCheckedChange = viewModel::setDynamicColor,
        )
        SettingsSliderRow(
            title = stringResource(R.string.settings_font_size),
            valueLabel = fontLabels.getOrElse(fontScales.indexOfClosest(uiState.fontScale)) { "M" },
            value = uiState.fontScale.coerceIn(0.9f, 1.3f),
            steps = 2,
            onValueChange = { value ->
                fontScales.minByOrNull { kotlin.math.abs(it - value) }?.let { viewModel.setFontScale(it) }
            },
        )
        Spacer(Modifier.height(Space.xl))
    }

    if (themeSheetOpen) {
        dev.harness.ui.components.settings.HBottomSheetChoice(
            title = stringResource(R.string.settings_theme_title),
            options = themeOptions,
            selectedIndex = currentTheme.coerceAtLeast(0),
            onDismiss = { themeSheetOpen = false },
            onSelect = { viewModel.setThemeMode(themeIds[it]) },
        )
    }
}

private fun List<Float>.indexOfClosest(value: Float): Int =
    indices.minByOrNull { kotlin.math.abs(this[it] - value) } ?: 1

/** Chat: comportamento da conversa. */
@Composable
fun ChatSection(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_chat_autoscroll),
            subtitle = stringResource(R.string.settings_chat_autoscroll_note),
            checked = uiState.autoScroll,
            onCheckedChange = viewModel::setAutoScroll,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_chat_markdown),
            subtitle = stringResource(R.string.settings_chat_markdown_note),
            checked = uiState.renderMarkdown,
            onCheckedChange = viewModel::setRenderMarkdown,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_chat_enter),
            subtitle = stringResource(R.string.settings_chat_enter_note),
            checked = uiState.sendOnEnter,
            onCheckedChange = viewModel::setSendOnEnter,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_chat_haptic),
            subtitle = stringResource(R.string.settings_chat_haptic_note),
            checked = uiState.hapticFeedback,
            onCheckedChange = viewModel::setHapticFeedback,
        )
        Spacer(Modifier.height(Space.xl))
    }
}

/** Projetos e sessões: retomada e uso de armazenamento. */
@Composable
fun SessionsSection(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
) {
    var usage by remember { mutableStateOf<HarnessViewModel.StorageUsage?>(null) }
    var resume by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        usage = viewModel.storageUsage()
        resume = viewModel.settingsSnapshot().resumeLastSession
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_sessions_resume),
            subtitle = stringResource(R.string.settings_sessions_resume_note),
            checked = resume,
            onCheckedChange = {
                resume = it
                viewModel.setResumeLastSession(it)
            },
        )
        HSectionTitle(stringResource(R.string.settings_sessions_storage))
        usage?.let { u ->
            SettingsInfoRow(
                title = stringResource(R.string.settings_storage_env),
                value = formatBytes(u.rootfsBytes),
            )
            SettingsInfoRow(
                title = stringResource(R.string.settings_storage_sessions),
                value = formatBytes(u.sessionsBytes),
            )
            SettingsInfoRow(
                title = stringResource(R.string.settings_storage_projects),
                value = formatBytes(u.projectsBytes),
            )
            SettingsInfoRow(
                title = stringResource(R.string.settings_storage_total),
                value = formatBytes(u.totalBytes),
            )
        }
        Text(
            stringResource(R.string.settings_sessions_source_note),
            style = MaterialTheme.typography.bodySmall,
            color = HarnessTheme.textTertiary,
        )
        Spacer(Modifier.height(Space.xl))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576f)
    bytes >= 1 shl 10 -> "%d KB".format(bytes / 1024)
    else -> "$bytes B"
}

/** Ambiente: versões, conexão e reparo. */
@Composable
fun EnvironmentSection(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
) {
    var showReinstallConfirm by rememberSaveable { mutableStateOf(false) }
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        val version = remember { viewModel.environmentVersion() }
        SettingsInfoRow(
            title = stringResource(R.string.settings_env_version),
            value = version,
        )
        SettingsInfoRow(
            title = stringResource(R.string.settings_env_connection),
            value = connectionLabel(uiState.connection),
        )
        HSectionTitle(stringResource(R.string.settings_section_storage))
        SettingsDangerRow(
            title = stringResource(R.string.settings_reinstall_env),
            description = stringResource(R.string.settings_reinstall_note),
            actionLabel = stringResource(R.string.settings_reinstall_action),
            onAction = { showReinstallConfirm = true },
        )
        Spacer(Modifier.height(Space.xl))
    }
    if (showReinstallConfirm) {
        HDialog(
            title = stringResource(R.string.settings_reinstall_confirm_title),
            text = {
                Text(
                    stringResource(R.string.settings_reinstall_confirm_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            onDismiss = { showReinstallConfirm = false },
            confirmLabel = stringResource(R.string.settings_reinstall_confirm_yes),
            onConfirm = {
                viewModel.clearEnvironment()
                showReinstallConfirm = false
            },
            confirmEnabled = true,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun connectionLabel(connection: dev.harness.core.pi.ConnectionState): String = stringResource(
    when (connection) {
        dev.harness.core.pi.ConnectionState.Connected -> R.string.conn_connected
        dev.harness.core.pi.ConnectionState.Starting -> R.string.conn_starting
        dev.harness.core.pi.ConnectionState.Restarting -> R.string.conn_restarting
        dev.harness.core.pi.ConnectionState.Stopped -> R.string.conn_stopped
        dev.harness.core.pi.ConnectionState.Failed -> R.string.conn_failed
    },
)

/** Privacidade e segurança: onde a chave vive e como apagar dados. */
@Composable
fun PrivacySection(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
) {
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        val keyCount = remember(uiState.providers) { uiState.providers.count { viewModel.hasApiKey(it.id) } }
        SettingsInfoRow(
            title = stringResource(R.string.settings_privacy_key_storage),
            value = stringResource(R.string.settings_privacy_key_storage_value),
            subtitle = stringResource(R.string.settings_privacy_key_storage_note),
        )
        SettingsInfoRow(
            title = stringResource(R.string.settings_privacy_keys_configured),
            value = keyCount.toString(),
        )
        Text(
            stringResource(R.string.settings_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        HSectionTitle(stringResource(R.string.settings_privacy_danger))
        SettingsDangerRow(
            title = stringResource(R.string.settings_privacy_clear),
            description = stringResource(R.string.settings_privacy_clear_note),
            actionLabel = stringResource(R.string.settings_privacy_clear_action),
            onAction = { showClearConfirm = true },
        )
        Spacer(Modifier.height(Space.xl))
    }
    if (showClearConfirm) {
        HDialog(
            title = stringResource(R.string.settings_privacy_clear_confirm_title),
            text = {
                Text(
                    stringResource(R.string.settings_privacy_clear_confirm_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            onDismiss = { showClearConfirm = false },
            confirmLabel = stringResource(R.string.settings_privacy_clear_action),
            onConfirm = {
                viewModel.clearEnvironment()
                showClearConfirm = false
            },
            confirmEnabled = true,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

/** Diagnóstico: logs, cópia e teste do ambiente. */
@Composable
fun DiagnosticsSection(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
) {
    val clipboard = LocalClipboardManager.current
    var envTest by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        CollapsibleSection(title = stringResource(R.string.onboarding_log_title)) {
            HCodeBlock(
                code = uiState.diagnostics.ifBlank { stringResource(R.string.settings_diagnostics_empty) },
                language = "log",
            )
            Spacer(Modifier.height(Space.sm))
            HButton(
                stringResource(R.string.settings_copy_diagnostics),
                onClick = { clipboard.setText(AnnotatedString(uiState.diagnostics)) },
                variant = HButtonDefaults.Variant.Secondary,
            )
        }
        HSectionTitle(stringResource(R.string.settings_diag_test))
        HButton(
            stringResource(R.string.settings_diag_test_run),
            onClick = { viewModel.testEnvironment { result -> envTest = result } },
            variant = HButtonDefaults.Variant.Secondary,
        )
        envTest?.let {
            HCodeBlock(code = it, language = "log")
        }
        Spacer(Modifier.height(Space.xl))
    }
}

/** Sobre: versão, licenças e links. */
@Composable
fun AboutSection() {
    val colors = HarnessTheme
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        SettingsInfoRow(
            title = stringResource(R.string.settings_about_version_label),
            value = appVersionName(),
        )
        SettingsInfoRow(
            title = stringResource(R.string.settings_about_runtime),
            value = stringResource(R.string.settings_about_runtime_value),
        )
        Text(
            stringResource(R.string.settings_licenses_text),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        HCard {
            Column(
                Modifier.fillMaxWidth().padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    stringResource(R.string.settings_about_links),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    "github.com/deivid22srk/harness-android",
                    style = MonoStyle,
                    color = colors.accentText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .touchTargetClickable { uri.openUri("https://github.com/deivid22srk/harness-android") },
                )
            }
        }
        Spacer(Modifier.height(Space.xl))
    }
}

// ---------------------------------------------------------------------------
// Shared pieces migrated from the former single-screen Settings
// ---------------------------------------------------------------------------

@Composable
private fun ProviderList(
    providers: List<Provider>,
    selected: String,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val colors = HarnessTheme
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        providers.forEach { provider ->
            val isSelected = provider.id == selected
            val isCustom = provider.api != null
            val shape = Radii.shapeSm
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .touchTargetClickable(
                        role = Role.Button,
                        onClick = { onSelect(provider.id) },
                        shape = shape,
                    )
                    .background(if (isSelected) colors.accentContainer else colors.surfaceElevated, shape)
                    .padding(horizontal = Space.md, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        provider.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) colors.onAccentContainer else colors.textPrimary,
                    )
                    if (isCustom) {
                        Text(
                            provider.baseUrl.orEmpty(),
                            style = MonoStyle,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isCustom) {
                    HButton(
                        stringResource(R.string.provider_remove),
                        onClick = { onRemove(provider.id) },
                        variant = HButtonDefaults.Variant.Ghost,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderKeySection(
    uiState: HarnessUiState,
    activeProvider: Provider?,
    onSave: (String, String) -> Boolean,
) {
    val colors = HarnessTheme
    var keyDraft by rememberSaveable(uiState.selectedProvider) { mutableStateOf("") }
    var keySaved by rememberSaveable(uiState.selectedProvider) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        HPasswordField(
            value = keyDraft,
            onValueChange = {
                keyDraft = it
                keySaved = false
            },
            placeholder = activeProvider?.keyHint?.ifBlank { null }
                ?: stringResource(R.string.settings_key_hint),
            visibilityLabel = stringResource(R.string.settings_key_show),
            hideLabel = stringResource(R.string.settings_key_hide),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            HButton(
                stringResource(R.string.settings_key_save),
                onClick = {
                    if (onSave(uiState.selectedProvider, keyDraft)) {
                        keyDraft = ""
                        keySaved = true
                    }
                },
                enabled = keyDraft.isNotBlank(),
                variant = HButtonDefaults.Variant.Primary,
            )
            if (keySaved) {
                Text(
                    stringResource(R.string.settings_key_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.toolOkText,
                )
            }
        }
        Text(
            stringResource(R.string.settings_key_security_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
    }
}

/** App version from PackageManager (buildConfig is not enabled). */
@Composable
internal fun appVersionName(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    } ?: "—"
}

/** "Obter modelos" dialog. */
@Composable
internal fun ModelsDialog(
    providerLabel: String,
    loading: Boolean,
    models: List<dev.harness.ModelOption>,
    error: String?,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HarnessTheme
    val selectedCount = models.count { it.selected }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        title = {
            Column {
                Text(stringResource(R.string.models_title, providerLabel), color = colors.textPrimary)
                Text(
                    stringResource(R.string.models_selected_count, selectedCount, models.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                when {
                    loading -> Row(
                        Modifier.fillMaxWidth().padding(Space.lg),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.accent,
                        )
                        Spacer(Modifier.size(Space.md))
                        Text(stringResource(R.string.models_loading), color = colors.textSecondary)
                    }
                    error != null -> Text(error, color = colors.toolErrorText, style = MaterialTheme.typography.bodySmall)
                    models.isEmpty() -> Text(
                        stringResource(R.string.models_empty),
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            HButton(stringResource(R.string.models_select_all), { onSelectAll(true) }, variant = HButtonDefaults.Variant.Ghost)
                            HButton(stringResource(R.string.models_clear), { onSelectAll(false) }, variant = HButtonDefaults.Variant.Ghost)
                        }
                        Spacer(Modifier.height(Space.xs))
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(models, key = { it.qualifiedId }) { option ->
                                ModelRow(option) { onToggle(option.qualifiedId) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onSave, enabled = !loading && models.isNotEmpty()) {
                Text(
                    stringResource(R.string.models_save),
                    color = if (!loading && models.isNotEmpty()) colors.accentText else colors.textTertiary,
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun ModelRow(option: dev.harness.ModelOption, onToggle: () -> Unit) {
    val colors = HarnessTheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.shapeSm)
            .background(colors.surface)
            .toggleable(
                value = option.selected,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = Space.md, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(Radii.shapeSm)
                .background(if (option.selected) colors.toolOk else colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (option.selected) {
                androidx.compose.material3.Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.background,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(option.id, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1)
            Text(option.provider, style = MonoStyle, color = colors.textTertiary, maxLines = 1)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit,
) {
    var id by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf("") }
    var api by rememberSaveable { mutableStateOf(Providers.APIS.first()) }
    var models by rememberSaveable { mutableStateOf("") }
    var apiExpanded by rememberSaveable { mutableStateOf(false) }
    val colors = HarnessTheme
    val valid = id.isNotBlank() && baseUrl.startsWith("http") && models.isNotBlank()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        title = { Text(stringResource(R.string.provider_custom_title), color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                HTextField(id, { id = it }, label = stringResource(R.string.provider_custom_id), placeholder = "meu-provedor", singleLine = true)
                HTextField(label, { label = it }, label = stringResource(R.string.provider_custom_name), placeholder = "Meu Provedor", singleLine = true)
                HTextField(
                    baseUrl, { baseUrl = it },
                    label = stringResource(R.string.provider_custom_base_url),
                    placeholder = "https://api.exemplo.com/v1",
                    singleLine = true,
                    isError = baseUrl.isNotBlank() && !baseUrl.startsWith("http"),
                )
                ExposedDropdownMenuBox(
                    expanded = apiExpanded,
                    onExpandedChange = { apiExpanded = it },
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = api,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.provider_custom_api)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = apiExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.borderStrong,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded = apiExpanded,
                        onDismissRequest = { apiExpanded = false },
                    ) {
                        Providers.APIS.forEach { option ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(option, style = MonoStyle) },
                                onClick = {
                                    api = option
                                    apiExpanded = false
                                },
                            )
                        }
                    }
                }
                HTextField(
                    models, { models = it },
                    label = stringResource(R.string.provider_custom_models),
                    placeholder = stringResource(R.string.provider_custom_models_hint),
                    minLines = 2,
                )
                Text(
                    stringResource(R.string.provider_custom_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = valid, onClick = { onAdd(id, label, baseUrl, api, models) }) {
                Text(
                    stringResource(R.string.provider_custom_add),
                    color = if (valid) colors.accentText else colors.textTertiary,
                )
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = colors.textSecondary)
            }
        },
    )
}
