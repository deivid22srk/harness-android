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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.harness.HarnessUiState
import dev.harness.HarnessViewModel
import dev.harness.PackagesUiState
import dev.harness.PackagesViewModel
import dev.harness.SettingsSection
import dev.harness.R
import dev.harness.ui.components.HIconButton
import dev.harness.ui.components.HSectionTitle
import dev.harness.ui.components.HTextField
import dev.harness.ui.components.settings.SettingsCategoryRow
import dev.harness.ui.screens.settings.AboutSection
import dev.harness.ui.screens.settings.AccountSection
import dev.harness.ui.screens.settings.AppearanceSection
import dev.harness.ui.screens.settings.ChatSection
import dev.harness.ui.screens.settings.DiagnosticsSection
import dev.harness.ui.screens.settings.EnvironmentSection
import dev.harness.ui.screens.settings.PrivacySection
import dev.harness.ui.screens.settings.SessionsSection
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.Space

private val HUB_WIDTH = 360.dp

/** One searchable entry of the settings hub. */
private data class CategoryEntry(
    val section: SettingsSection,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    /** Search keywords so the hub finds settings by any related term. */
    val keywords: String = "",
)

/**
 * Configurações (Frente B): hub com categorias + busca — cada categoria abre
 * sua própria tela; em telas largas (tablet/dobrável) mostra lista-detalhe.
 * A rota (`settings/{categoria}`) vive em [HarnessUiState.settingsSection]
 * com predictive back.
 */
@Composable
fun SettingsScreen(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
    packagesViewModel: PackagesViewModel,
    packagesState: PackagesUiState,
    onBack: () -> Unit,
    wideScreen: Boolean = false,
) {
    val colors = HarnessTheme
    var packagesCount by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit, packagesState.global.size, packagesState.projectPackages.size) {
        packagesCount = viewModel.installedPackagesCount()
    }
    val entries = rememberSettingsEntries(uiState, viewModel, packagesCount)
    val query = uiState.settingsSearch.trim()
    val visible = if (query.isEmpty()) {
        entries
    } else {
        entries.filter { entry ->
            entry.title.contains(query, ignoreCase = true) || entry.keywords.contains(query, ignoreCase = true)
        }
    }
    val searchMisses = query.isNotEmpty() && visible.isEmpty()

    if (wideScreen) {
        // Lista-detalhe: hub à esquerda, categoria aberta à direita.
        Row(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
            Box(Modifier.width(HUB_WIDTH).fillMaxSize().background(colors.background)) {
                HubList(
                    uiState = uiState,
                    viewModel = viewModel,
                    entries = visible,
                    searchMisses = searchMisses,
                    onBack = onBack,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Space.md, end = Space.lg, top = Space.lg),
            ) {
                HSectionTitle(sectionTitle(uiState.settingsSection))
                SectionContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    packagesViewModel = packagesViewModel,
                    packagesState = packagesState,
                    section = uiState.settingsSection,
                    onBack = onBack,
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
            when (uiState.settingsSection) {
                null -> HubList(
                    uiState = uiState,
                    viewModel = viewModel,
                    entries = visible,
                    searchMisses = searchMisses,
                    onBack = onBack,
                )

                else -> Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.xs, vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_back),
                            onClick = viewModel::backToSettingsHub,
                            tint = colors.textPrimary,
                        )
                        Text(
                            sectionTitle(uiState.settingsSection),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(start = Space.xs),
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        SectionContent(
                            uiState = uiState,
                            viewModel = viewModel,
                            packagesViewModel = packagesViewModel,
                            packagesState = packagesState,
                            section = uiState.settingsSection,
                            onBack = onBack,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubList(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
    entries: List<CategoryEntry>,
    searchMisses: Boolean,
    onBack: () -> Unit,
) {
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
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
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = Space.xs),
            )
        }

        HTextField(
            value = uiState.settingsSearch,
            onValueChange = viewModel::setSettingsSearch,
            label = stringResource(R.string.settings_search_hint),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_search"),
        )

        Spacer(Modifier.height(Space.sm))

        if (searchMisses) {
            Text(
                stringResource(R.string.settings_search_miss),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = Space.lg),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.testTag("settings_hub_list"),
            ) {
                items(entries, key = { it.section.name }) { entry ->
                    SettingsCategoryRow(
                        icon = entry.icon,
                        title = entry.title,
                        subtitle = entry.subtitle,
                        onClick = { viewModel.openSettingsSection(entry.section) },
                    )
                }
                item { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }
}

@Composable
private fun SectionContent(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
    packagesViewModel: PackagesViewModel,
    packagesState: PackagesUiState,
    section: SettingsSection?,
    onBack: () -> Unit,
) {
    when (section) {
        SettingsSection.Account -> AccountSection(uiState, viewModel)
        SettingsSection.Appearance -> AppearanceSection(uiState, viewModel)
        SettingsSection.Chat -> ChatSection(uiState, viewModel)
        SettingsSection.Sessions -> SessionsSection(uiState, viewModel)
        SettingsSection.Packages -> PackagesScreen(
            packagesState = packagesState,
            harnessState = uiState,
            packagesViewModel = packagesViewModel,
            harnessViewModel = viewModel,
            onBack = viewModel::backToSettingsHub,
        )
        SettingsSection.Environment -> EnvironmentSection(uiState, viewModel, packagesViewModel)
        SettingsSection.Privacy -> PrivacySection(uiState, viewModel)
        SettingsSection.Diagnostics -> DiagnosticsSection(uiState, viewModel)
        SettingsSection.About -> AboutSection()
        else -> Unit
    }
}

@Composable
private fun sectionTitle(section: SettingsSection?): String = stringResource(
    when (section) {
        SettingsSection.Account -> R.string.settings_cat_account
        SettingsSection.Appearance -> R.string.settings_cat_appearance
        SettingsSection.Chat -> R.string.settings_cat_chat
        SettingsSection.Sessions -> R.string.settings_cat_sessions
        SettingsSection.Packages -> R.string.settings_cat_packages
        SettingsSection.Environment -> R.string.settings_cat_environment
        SettingsSection.Privacy -> R.string.settings_cat_privacy
        SettingsSection.Diagnostics -> R.string.settings_cat_diagnostics
        SettingsSection.About -> R.string.settings_cat_about
        null -> R.string.settings_title
    },
)

/** Builds the hub entries with LIVE subtitles (current values). */
@Composable
private fun rememberSettingsEntries(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
    packagesCount: Int?,
): List<CategoryEntry> {
    val accountTitle = stringResource(R.string.settings_cat_account)
    val keyOk = uiState.providers.any { viewModel.hasApiKey(it.id) }
    val accountSub = stringResource(
        if (keyOk) R.string.settings_hub_account_ok else R.string.settings_hub_account_missing,
    )
    val appearanceTitle = stringResource(R.string.settings_cat_appearance)
    val appearanceSub = stringResource(
        R.string.settings_hub_appearance_subtitle,
        stringResource(
            when (uiState.themeMode) {
                "light" -> R.string.settings_theme_light
                "dark" -> R.string.settings_theme_dark
                else -> R.string.settings_theme_system
            },
        ),
    )
    val chatTitle = stringResource(R.string.settings_cat_chat)
    val chatOnCount = listOf(
        uiState.autoScroll,
        uiState.renderMarkdown,
        uiState.sendOnEnter,
        uiState.hapticFeedback,
    ).count { it }
    val chatSub = stringResource(R.string.settings_hub_chat_subtitle, chatOnCount)
    val sessionsTitle = stringResource(R.string.settings_cat_sessions)
    val sessionsSub = stringResource(
        if (viewModel.resumeLastSessionEnabled()) {
            R.string.settings_hub_resume_on
        } else {
            R.string.settings_hub_resume_off
        },
    )
    val packagesTitle = stringResource(R.string.settings_cat_packages)
    val packagesSub = packagesCount?.let {
        stringResource(R.string.settings_hub_packages_count, it)
    } ?: stringResource(R.string.settings_hub_packages_subtitle)
    val envTitle = stringResource(R.string.settings_cat_environment)
    val envSub = stringResource(R.string.settings_hub_environment_subtitle)
    val privacyTitle = stringResource(R.string.settings_cat_privacy)
    val privacySub = stringResource(R.string.settings_hub_privacy_subtitle)
    val diagTitle = stringResource(R.string.settings_cat_diagnostics)
    val diagSub = stringResource(R.string.settings_hub_diagnostics_subtitle)
    val aboutTitle = stringResource(R.string.settings_cat_about)
    val aboutSub = stringResource(R.string.settings_hub_about_subtitle)

    return remember(accountSub, appearanceSub, chatSub, sessionsSub) {
        listOf(
            CategoryEntry(SettingsSection.Account, Icons.Default.Person, accountTitle, accountSub, "chave api provedor modelos raciocínio thinking token"),
            CategoryEntry(SettingsSection.Appearance, Icons.Default.Palette, appearanceTitle, appearanceSub, "tema claro escuro cores dinâmicas material you fonte"),
            CategoryEntry(SettingsSection.Chat, Icons.Default.Message, chatTitle, chatSub, "rolagem markdown enter haptic vibração steering follow-up fila entrega"),
            CategoryEntry(SettingsSection.Sessions, Icons.Default.History, sessionsTitle, sessionsSub, "sessões retomar histórico armazenamento limpar antigas"),
            CategoryEntry(SettingsSection.Packages, Icons.Default.Extension, packagesTitle, packagesSub, "pacotes npm instalar remover atualizar extensões skills"),
            CategoryEntry(SettingsSection.Environment, Icons.Default.Memory, envTitle, envSub, "alpine node pi versão atualizar reinstalar ambiente reparar"),
            CategoryEntry(SettingsSection.Privacy, Icons.Default.Lock, privacyTitle, privacySub, "chave keystore cifrada apagar dados segurança privacidade"),
            CategoryEntry(SettingsSection.Diagnostics, Icons.Default.BugReport, diagTitle, diagSub, "logs diagnóstico teste copiar"),
            CategoryEntry(SettingsSection.About, Icons.Default.Info, aboutTitle, aboutSub, "versão licenças links sobre"),
        )
    }
}
