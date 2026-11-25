package dev.harness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.harness.HarnessUiState
import dev.harness.HarnessViewModel
import dev.harness.PackagesUiState
import dev.harness.PackagesViewModel
import dev.harness.R
import dev.harness.core.pi.InstalledPackage
import dev.harness.core.pi.PackageFeature
import dev.harness.ui.components.CollapsibleSection
import dev.harness.ui.components.HButton
import dev.harness.ui.components.HButtonDefaults
import dev.harness.ui.components.HCodeBlock
import dev.harness.ui.components.HDialog
import dev.harness.ui.components.HEmptyState
import dev.harness.ui.components.HErrorState
import dev.harness.ui.components.HIconButton
import dev.harness.ui.components.HSectionTitle
import dev.harness.ui.components.HSkeleton
import dev.harness.ui.components.HTextField
import dev.harness.ui.components.touchTargetClickable
import dev.harness.ui.components.settings.SettingsChoiceRow
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val EXAMPLES = "npm:pi-web-access; git:github.com/exemplo/pi-tools@v1; ./pi-tools"

/**
 * "Pacotes do Pi" (Frente C): instalados (Global / Do projeto), explorador npm
 * com aviso de segurança obrigatório, log ao vivo com cancelamento e oferta de
 * reiniciar a sessão após cada mutação (§5.3).
 */
@Composable
fun PackagesScreen(
    packagesState: PackagesUiState,
    harnessState: HarnessUiState,
    packagesViewModel: PackagesViewModel,
    harnessViewModel: HarnessViewModel,
    onBack: () -> Unit,
) {
    val colors = HarnessTheme
    val clipboard = LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(harnessState.activeProject?.hostPath) {
        packagesViewModel.load(harnessState.activeProject)
    }
    androidx.compose.runtime.LaunchedEffect(packagesState.error) {
        packagesState.error?.let { /* surfaced inline; keep state until dismissed */ }
    }

    var sourceDraft by rememberSaveable { mutableStateOf("") }
    var scopeIsProject by rememberSaveable { mutableStateOf(false) }
    var scopePickerOpen by rememberSaveable { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<InstalledPackage?>(null) }

    Box(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Space.lg),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.a11y_back),
                    onClick = onBack,
                    tint = colors.textPrimary,
                )
                Column(Modifier.weight(1f).padding(start = Space.xs)) {
                    Text(
                        stringResource(R.string.settings_cat_packages),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        stringResource(R.string.packages_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                HIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.packages_update_all_a11y),
                    onClick = { packagesViewModel.updateAll(onDone = {}) },
                    tint = colors.textSecondary,
                )
            }

            if (packagesState.needsReload) {
                ReloadBanner(
                    onRestartNow = {
                        packagesViewModel.clearReloadHint()
                        harnessViewModel.restartSessionAfterPackageChange()
                    },
                    onDismiss = packagesViewModel::clearReloadHint,
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (packagesState.loading) {
                    LoadingPackages()
                } else if (packagesState.error != null && packagesState.global.isEmpty() && packagesState.projectPackages.isEmpty()) {
                    HErrorState(
                        title = stringResource(R.string.packages_error_title),
                        message = packagesState.error,
                        retryLabel = stringResource(R.string.action_retry),
                        onRetry = packagesViewModel::refresh,
                    )
                } else {
                    HSectionTitle(stringResource(R.string.packages_global_title))
                    if (packagesState.global.isEmpty()) {
                        Text(
                            stringResource(R.string.packages_empty_global),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    } else {
                        packagesState.global.forEach { pkg ->
                            PackageRow(
                                pkg = pkg,
                                onRemove = { removeTarget = pkg },
                                onUpdate = { packagesViewModel.updatePackage(pkg, onDone = {}) },
                            )
                        }
                    }

                    HSectionTitle(stringResource(R.string.packages_project_title))
                    if (packagesState.projectPackages.isEmpty()) {
                        Text(
                            stringResource(R.string.packages_empty_project),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    } else {
                        packagesState.projectPackages.forEach { pkg ->
                            PackageRow(
                                pkg = pkg,
                                onRemove = { removeTarget = pkg },
                                onUpdate = { packagesViewModel.updatePackage(pkg, onDone = {}) },
                            )
                        }
                    }

                    // ------------------------------------------------ Install
                    HSectionTitle(stringResource(R.string.packages_install_title))
                    HTextField(
                        value = sourceDraft,
                        onValueChange = { sourceDraft = it },
                        label = stringResource(R.string.packages_source_label),
                        placeholder = "npm:pi-web-access",
                        singleLine = true,
                        supportingText = stringResource(R.string.packages_source_hint),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        EXAMPLES.split(";").forEach { example ->
                            val value = example.trim()
                            if (value.isNotEmpty()) {
                                HChipSmall(value) { sourceDraft = value }
                            }
                        }
                    }
                    SettingsChoiceRow(
                        title = stringResource(R.string.packages_scope),
                        currentLabel = if (scopeIsProject) {
                            stringResource(R.string.packages_scope_project)
                        } else {
                            stringResource(R.string.packages_scope_global)
                        },
                        onClick = { scopePickerOpen = true },
                    )
                    HButton(
                        text = stringResource(R.string.packages_install_action),
                        onClick = {
                            packagesViewModel.requestInstall(sourceDraft, scopeIsProject)
                        },
                        icon = Icons.Default.Add,
                        modifier = Modifier.padding(vertical = Space.xs),
                    )

                    // ------------------------------------------------ Explorer
                    HSectionTitle(stringResource(R.string.packages_explore_title))
                    Text(
                        stringResource(R.string.packages_explore_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        HTextField(
                            value = packagesState.search.query,
                            onValueChange = packagesViewModel::setSearchQuery,
                            label = stringResource(R.string.packages_search_label),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        HButton(
                            text = stringResource(R.string.packages_search_action),
                            onClick = { packagesViewModel.search() },
                            icon = Icons.Default.Search,
                        )
                    }
                    packagesState.search.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.toolErrorText)
                    }
                    if (packagesState.search.loading) {
                        LoadingPackages()
                    } else {
                        packagesState.search.results.take(20).forEach { result ->
                            SearchResultRow(
                                name = result.name,
                                version = result.version,
                                description = result.description,
                                publisher = result.publisher,
                                downloads = result.monthlyDownloads,
                                date = result.date,
                                features = result.features,
                                onUse = { sourceDraft = result.installCommand },
                            )
                        }
                    }

                    // --------------------------------------------- Operation log
                    packagesState.operation?.let { op ->
                        HSectionTitle(stringResource(R.string.packages_log_title))
                        CollapsibleSection(title = opLabel(op, stringResource(R.string.packages_running))) {
                            HCodeBlock(
                                code = op.log.takeLast(80).joinToString("\n").ifBlank { stringResource(R.string.packages_log_empty) },
                                language = "log",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                if (op.running) {
                                    HButton(
                                        text = stringResource(R.string.packages_cancel),
                                        onClick = packagesViewModel::cancelOperation,
                                        variant = HButtonDefaults.Variant.Danger,
                                    )
                                }
                                HButton(
                                    text = stringResource(R.string.settings_copy_diagnostics),
                                    onClick = { clipboard.setText(AnnotatedString(op.log.joinToString("\n"))) },
                                    variant = HButtonDefaults.Variant.Secondary,
                                )
                            }
                        }
                    }

                    packagesState.error?.let { err ->
                        dev.harness.ui.components.HErrorState(
                            title = stringResource(R.string.packages_op_error_title),
                            message = err,
                            diagnosticsLabel = stringResource(R.string.packages_error_details),
                            diagnostics = packagesState.errorDetail,
                            retryLabel = stringResource(R.string.action_retry),
                            onRetry = packagesViewModel::refresh,
                        )
                    }
                }
                Spacer(Modifier.height(Space.xl))
            }
        }
    }

    // Scope bottom sheet
    if (scopePickerOpen) {
        dev.harness.ui.components.settings.HBottomSheetChoice(
            title = stringResource(R.string.packages_scope),
            options = listOf(
                stringResource(R.string.packages_scope_global),
                stringResource(R.string.packages_scope_project),
            ),
            selectedIndex = if (scopeIsProject) 1 else 0,
            onDismiss = { scopePickerOpen = false },
            onSelect = { index ->
                scopeIsProject = index == 1
                scopePickerOpen = false
            },
        )
    }

    // SECURITY confirmation (mandatory before any install — §5.3.2)
    packagesState.pendingInstall?.let { pending ->
        HDialog(
            title = stringResource(R.string.packages_security_title),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        stringResource(R.string.packages_security_body, pending.source),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Text(
                        stringResource(R.string.packages_security_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.toolErrorText,
                        fontStyle = FontStyle.Italic,
                    )
                }
            },
            onDismiss = packagesViewModel::cancelPendingInstall,
            confirmLabel = stringResource(R.string.packages_security_confirm),
            onConfirm = { packagesViewModel.confirmPendingInstall(onDone = { sourceDraft = "" }) },
            confirmEnabled = true,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    // Remove confirmation
    removeTarget?.let { pkg ->
        HDialog(
            title = stringResource(R.string.packages_remove_title),
            text = {
                Text(
                    stringResource(R.string.packages_remove_body, pkg.source),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            onDismiss = { removeTarget = null },
            confirmLabel = stringResource(R.string.sessions_delete),
            onConfirm = {
                removeTarget = null
                packagesViewModel.removePackage(pkg, onDone = {})
            },
            confirmEnabled = true,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun HChipSmall(text: String, onClick: () -> Unit) {
    val colors = HarnessTheme
    Text(
        text,
        style = MonoStyle,
        color = colors.accentText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(Radii.shapePill)
            .background(colors.surfaceElevated)
            .touchTargetClickable(onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

@Composable
private fun ReloadBanner(onRestartNow: () -> Unit, onDismiss: () -> Unit) {
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radii.shapeLg)
            .background(colors.accentContainer)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            stringResource(R.string.packages_reload_title),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onAccentContainer,
        )
        Text(
            stringResource(R.string.packages_reload_body),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onAccentContainer,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            HButton(
                text = stringResource(R.string.packages_reload_now),
                onClick = onRestartNow,
                variant = HButtonDefaults.Variant.Secondary,
            )
            HButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                variant = HButtonDefaults.Variant.Ghost,
            )
        }
    }
}

@Composable
private fun opLabel(op: dev.harness.core.pi.PackageOperation, runningLabel: String): String = when {
    op.running -> runningLabel
    else -> when (op.kind) {
        dev.harness.core.pi.PackageOperation.OpKind.INSTALL -> stringResource(R.string.packages_log_done_install, op.exitCode ?: -1)
        dev.harness.core.pi.PackageOperation.OpKind.REMOVE -> stringResource(R.string.packages_log_done_remove, op.exitCode ?: -1)
        dev.harness.core.pi.PackageOperation.OpKind.UPDATE -> stringResource(R.string.packages_log_done_update, op.exitCode ?: -1)
    }
}

@Composable
private fun PackageRow(
    pkg: InstalledPackage,
    onRemove: () -> Unit,
    onUpdate: () -> Unit,
) {
    val colors = HarnessTheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.shapeLg)
            .background(colors.surfaceElevated)
            .padding(Space.md)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(
            imageVector = when (pkg.kind) {
                dev.harness.core.pi.PackageKind.NPM -> Icons.Default.Public
                dev.harness.core.pi.PackageKind.GIT, dev.harness.core.pi.PackageKind.URL -> Icons.Default.CloudDownload
                dev.harness.core.pi.PackageKind.LOCAL -> Icons.Default.ContentCopy
            },
            contentDescription = stringResource(R.string.packages_kind_a11y, pkg.kind.name),
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                pkg.identity,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                pkg.ref?.let {
                    Text(it, style = MonoStyle, color = colors.textSecondary)
                }
                if (pkg.pinned) {
                    dev.harness.ui.components.HBadge(
                        text = stringResource(R.string.packages_pinned),
                        color = colors.accent,
                    )
                }
            }
            pkg.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pkg.features.isNotEmpty()) {
                Text(
                    featuresText(pkg.features),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                )
            }
        }
        HIconButton(
            icon = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.packages_update_one_a11y, pkg.identity),
            onClick = onUpdate,
            tint = colors.textSecondary,
        )
        HIconButton(
            icon = Icons.Default.Delete,
            contentDescription = stringResource(R.string.packages_remove_a11y, pkg.identity),
            onClick = onRemove,
            tint = colors.toolErrorText,
        )
    }
}

@Composable
private fun featureLabel(feature: PackageFeature): String = stringResource(
    when (feature) {
        PackageFeature.EXTENSIONS -> R.string.packages_feature_extensions
        PackageFeature.SKILLS -> R.string.packages_feature_skills
        PackageFeature.PROMPTS -> R.string.packages_feature_prompts
        PackageFeature.THEMES -> R.string.packages_feature_themes
    },
)

/** Labels built with the inline buildList so stringResource stays composable-legal. */
@Composable
private fun featuresText(features: Set<PackageFeature>): String = buildList {
    if (PackageFeature.EXTENSIONS in features) add(stringResource(R.string.packages_feature_extensions))
    if (PackageFeature.SKILLS in features) add(stringResource(R.string.packages_feature_skills))
    if (PackageFeature.PROMPTS in features) add(stringResource(R.string.packages_feature_prompts))
    if (PackageFeature.THEMES in features) add(stringResource(R.string.packages_feature_themes))
}.joinToString(" · ")

@Composable
private fun SearchResultRow(
    name: String,
    version: String,
    description: String?,
    publisher: String?,
    downloads: Long,
    date: String?,
    features: Set<PackageFeature>,
    onUse: () -> Unit,
) {
    val colors = HarnessTheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.shapeLg)
            .background(colors.surfaceElevated)
            .touchTargetClickable(onClick = onUse)
            .padding(Space.md)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1)
            Text(
                listOfNotNull(
                    version.takeIf { it.isNotBlank() },
                    publisher?.let { "@$it" },
                    if (downloads > 0) "%s/mês".format(formatCompact(downloads)) else null,
                    date?.let { relativeIso(it) },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (features.isNotEmpty()) {
                Text(
                    featuresText(features),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accentText,
                )
            }
        }
        HButton(
            text = stringResource(R.string.packages_use),
            onClick = onUse,
            variant = HButtonDefaults.Variant.Ghost,
        )
    }
}

private fun formatCompact(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}

/** "3 d atrás"-style relative date from an npm ISO timestamp. */
@Composable
private fun relativeIso(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val instant = runCatching { ZonedDateTime.parse(iso) }.getOrNull() ?: return null
    val minutes = java.time.Duration.between(instant, ZonedDateTime.now()).toMinutes()
    return when {
        minutes < 60 -> stringResource(R.string.time_minutes, minutes.coerceAtLeast(1))
        minutes < 60 * 24 -> stringResource(R.string.time_hours, minutes / 60)
        else -> stringResource(R.string.time_days, minutes / (60 * 24))
    }
}

@Composable
private fun LoadingPackages() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.md)) {
        repeat(3) {
            HSkeleton(height = 20.dp, modifier = Modifier.fillMaxWidth(0.8f))
        }
    }
}
