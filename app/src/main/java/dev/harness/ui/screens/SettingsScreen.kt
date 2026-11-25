package dev.harness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.HarnessViewModel
import dev.harness.HarnessUiState
import dev.harness.ModelOption
import dev.harness.R
import dev.harness.core.settings.Provider
import dev.harness.core.settings.Providers
import dev.harness.ui.components.HBadge
import dev.harness.ui.components.HButton
import dev.harness.ui.components.HButtonDefaults
import dev.harness.ui.components.HCard
import dev.harness.ui.components.HChip
import dev.harness.ui.components.HDialog
import dev.harness.ui.components.HIconButton
import dev.harness.ui.components.HPasswordField
import dev.harness.ui.components.HSectionTitle
import dev.harness.ui.components.HTextField
import dev.harness.ui.components.CollapsibleSection
import dev.harness.ui.components.HCodeBlock
import dev.harness.ui.components.touchTargetClickable
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space
import androidx.compose.ui.semantics.Role

/** Settings: providers, keys, models, appearance, diagnostics, about. */
@Composable
fun SettingsScreen(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
    onBack: () -> Unit,
) {
    val colors = HarnessTheme
    var showAddCustom by rememberSaveable { mutableStateOf(false) }
    var showReinstallConfirm by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.a11y_back),
                onClick = onBack,
                tint = colors.textPrimary,
            )
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = Space.xs),
            )
        }

        // ------------------------------------------------ Provider
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

        // ------------------------------------------------ API key
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

        // ------------------------------------------------ Models
        HSectionTitle(stringResource(R.string.settings_section_models))
        HButton(
            stringResource(R.string.settings_get_models),
            onClick = { viewModel.openModelsDialog(uiState.selectedProvider) },
            variant = HButtonDefaults.Variant.Secondary,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            stringResource(R.string.settings_models_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        // ------------------------------------------------ Appearance
        HSectionTitle(stringResource(R.string.settings_section_appearance))
        HCard {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ThemeModeOption("system", uiState.themeMode, stringResource(R.string.settings_theme_system), viewModel::setThemeMode)
                    ThemeModeOption("light", uiState.themeMode, stringResource(R.string.settings_theme_light), viewModel::setThemeMode)
                    ThemeModeOption("dark", uiState.themeMode, stringResource(R.string.settings_theme_dark), viewModel::setThemeMode)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Text(
                        stringResource(R.string.settings_dynamic_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    ToggleSwitch(checked = uiState.dynamicColor, onCheckedChange = viewModel::setDynamicColor)
                }
                Text(
                    stringResource(R.string.settings_dynamic_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                Text(
                    stringResource(R.string.settings_font_size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    FontScaleOption(0.9f, uiState.fontScale, stringResource(R.string.settings_font_small), viewModel::setFontScale)
                    FontScaleOption(1f, uiState.fontScale, stringResource(R.string.settings_font_default), viewModel::setFontScale)
                    FontScaleOption(1.15f, uiState.fontScale, stringResource(R.string.settings_font_large), viewModel::setFontScale)
                    FontScaleOption(1.3f, uiState.fontScale, stringResource(R.string.settings_font_xlarge), viewModel::setFontScale)
                }
            }
        }

        // ------------------------------------------------ Default thinking
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

        // ------------------------------------------------ Storage
        HSectionTitle(stringResource(R.string.settings_section_storage))
        HButton(
            stringResource(R.string.settings_reinstall_env),
            onClick = { showReinstallConfirm = true },
            variant = HButtonDefaults.Variant.Danger,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            stringResource(R.string.settings_reinstall_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        // ------------------------------------------------ Diagnostics
        HSectionTitle(stringResource(R.string.settings_section_diagnostics))
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

        // ------------------------------------------------ About
        HSectionTitle(stringResource(R.string.settings_section_about))
        Text(
            stringResource(R.string.settings_about_version, appVersionName()),
            style = MonoStyle,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            stringResource(R.string.settings_licenses_text),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(Space.xxl))
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
                    .touchTargetClickable(role = Role.Button, onClick = { onSelect(provider.id) }, shape = shape)
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
                    color = colors.toolOk,
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
private fun appVersionName(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    } ?: "—"
}

@Composable
private fun ThemeModeOption(
    mode: String,
    current: String,
    label: String,
    onPick: (String) -> Unit,
) {
    HChip(text = label, selected = mode == current, onClick = { onPick(mode) })
}

@Composable
private fun FontScaleOption(
    scale: Float,
    current: Float,
    label: String,
    onPick: (Float) -> Unit,
) {
    HChip(text = label, selected = kotlin.math.abs(scale - current) < 0.01f, onClick = { onPick(scale) })
}

/** Minimal switch aligned with the palette (material3 Switch). */
@Composable
private fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedTrackColor = HarnessTheme.accentButton,
            checkedThumbColor = HarnessTheme.onAccent,
            uncheckedTrackColor = HarnessTheme.surfaceHigh,
            uncheckedThumbColor = HarnessTheme.textSecondary,
        ),
    )
}

/** "Obter modelos" dialog. */
@Composable
fun ModelsDialog(
    providerLabel: String,
    loading: Boolean,
    models: List<ModelOption>,
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
                Text(stringResource(R.string.models_save), color = if (!loading && models.isNotEmpty()) colors.accentText else colors.textTertiary)
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
private fun ModelRow(option: ModelOption, onToggle: () -> Unit) {
    val colors = HarnessTheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.shapeSm)
            .background(colors.surface)
            .touchTargetClickable { onToggle() }
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
                    androidx.compose.material.icons.Icons.Default.Check,
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
