package dev.harness

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.core.pi.ConnectionState
import dev.harness.core.rpc.ExtensionUiRequest
import dev.harness.ui.screens.ChatList
import dev.harness.ui.screens.Composer
import dev.harness.ui.screens.ModelPickerSheet
import dev.harness.ui.screens.ModelsDialog
import dev.harness.ui.screens.OnboardingScreen
import dev.harness.ui.screens.ProjectsScreen
import dev.harness.ui.screens.SettingsScreen
import dev.harness.ui.screens.ThinkingPickerSheet
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

@Composable
fun HarnessRoot(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
    onRequestBatteryExemption: () -> Unit,
) {
    val colors = HarnessTheme
    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (uiState.screen) {
            Screen.Onboarding -> OnboardingScreen(
                state = uiState.provision,
                onRetry = { viewModel.startProvisioning(force = false) },
            )

            Screen.Projects -> ProjectsScreen(
                projects = uiState.projects,
                cloning = uiState.cloning,
                error = uiState.projectsError,
                onOpen = viewModel::openProject,
                onCreate = viewModel::createProject,
                onClone = viewModel::cloneProject,
                onOpenSettings = viewModel::openSettings,
            )

            Screen.Settings -> SettingsScreen(
                providers = uiState.providers,
                selectedProvider = uiState.selectedProvider,
                thinkingLevel = uiState.thinkingLevel,
                diagnostics = uiState.diagnostics,
                onSelectProvider = viewModel::selectProvider,
                onSaveKey = viewModel::saveApiKey,
                onAddCustomProvider = viewModel::addCustomProvider,
                onRemoveCustomProvider = viewModel::removeCustomProvider,
                onOpenModels = viewModel::openModelsDialog,
                onClearEnvironment = viewModel::clearEnvironment,
                onBack = viewModel::backToProjects,
            )

            Screen.Chat -> ChatScreen(
                uiState = uiState,
                viewModel = viewModel,
            )
        }

        uiState.pendingUi.firstOrNull()?.let { request ->
            ExtensionUiDialog(
                request = request,
                onRespond = { value, confirmed, cancelled ->
                    viewModel.respondExtensionUi(request, value, confirmed, cancelled)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(uiState: HarnessUiState, viewModel: HarnessViewModel) {
    val colors = HarnessTheme
    // Only the models the user selected (or the provider's default) are offered.
    val models = remember(uiState.selectedProvider, uiState.providers) {
        val provider = uiState.providers.firstOrNull { it.id == uiState.selectedProvider }
        val selected = viewModel.selectedModelsFor(uiState.selectedProvider)
        when {
            selected.isNotEmpty() -> selected.map { uiState.selectedProvider to it }
            provider?.defaultModel != null -> listOf(uiState.selectedProvider to provider.defaultModel!!)
            else -> emptyList()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = uiState.activeProject?.name ?: "Harness",
            subtitle = uiState.chat.sessionName ?: "nova sessão",
            modelName = uiState.chat.model?.name,
            thinking = uiState.thinkingLevel,
            connection = uiState.connection,
            onNewSession = viewModel::newSession,
            onSettings = viewModel::openSettings,
            onBack = viewModel::backToProjects,
        )
        Box(Modifier.weight(1f)) {
            ChatList(state = uiState.chat)
        }
        Composer(
            state = uiState.chat,
            onSend = viewModel::send,
            onStop = viewModel::stopAgent,
            onOpenModelPicker = { viewModel.setPicker(HarnessViewModel.Pickers.Model, true) },
            onOpenThinkingPicker = { viewModel.setPicker(HarnessViewModel.Pickers.Thinking, true) },
            onAttach = { viewModel.setPicker(HarnessViewModel.Pickers.Attach, true) },
        )
    }

    if (uiState.showModelPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setPicker(HarnessViewModel.Pickers.Model, false) },
            sheetState = rememberModalBottomSheetState(),
            containerColor = colors.surfaceElevated,
        ) {
            ModelPickerSheet(
                state = uiState.chat,
                models = models,
                onPick = viewModel::pickModel,
                onDismiss = { viewModel.setPicker(HarnessViewModel.Pickers.Model, false) },
            )
        }
    }

    if (uiState.showThinkingPicker) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setPicker(HarnessViewModel.Pickers.Thinking, false) },
            sheetState = rememberModalBottomSheetState(),
            containerColor = colors.surfaceElevated,
        ) {
            ThinkingPickerSheet(
                levels = uiState.chat.availableThinkingLevels.ifEmpty {
                    listOf("off", "minimal", "low", "medium", "high")
                },
                current = uiState.thinkingLevel,
                onPick = viewModel::pickThinking,
                onDismiss = { viewModel.setPicker(HarnessViewModel.Pickers.Thinking, false) },
            )
        }
    }

    if (uiState.showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setPicker(HarnessViewModel.Pickers.Attach, false) },
            containerColor = colors.surfaceElevated,
        ) {
            AttachSheet(
                onPick = { viewModel.setPicker(HarnessViewModel.Pickers.Attach, false) },
            )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    modelName: String?,
    thinking: String,
    connection: ConnectionState,
    onNewSession: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = HarnessTheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                ConnectionIndicator(connection)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
                modelName?.let {
                    Text(it, style = MonoStyle, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        TopBarButton(Icons.Default.Add) { onNewSession() }
        TopBarButton(Icons.Default.Settings) { onSettings() }
        TopBarButton(Icons.Default.KeyboardArrowDown) { onBack() }
    }
}

@Composable
private fun ConnectionIndicator(state: ConnectionState) {
    val colors = HarnessTheme
    val (label, color) = when (state) {
        ConnectionState.Connected -> "conectado" to colors.toolOk
        ConnectionState.Starting -> "iniciando" to colors.toolRunning
        ConnectionState.Restarting -> "reiniciando" to colors.toolRunning
        ConnectionState.Stopped -> "parado" to colors.textTertiary
        ConnectionState.Failed -> "falha" to colors.toolError
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun TopBarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val colors = HarnessTheme
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(Radii.sm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary)
    }
}

@Composable
private fun AttachSheet(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        listOf("Câmera", "Fotos", "Arquivos", "Localização").forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.sm))
                    .clickable { onPick(option) }
                    .padding(vertical = Space.md, horizontal = Space.sm),
            ) {
                Text(option, style = MaterialTheme.typography.bodyLarge, color = HarnessTheme.textPrimary)
            }
        }
    }
}

/**
 * Native dialog for the Pi "RPC Extension UI" sub-protocol
 * (select / confirm / input / editor). Fire-and-forget methods never reach here.
 */
@Composable
private fun ExtensionUiDialog(
    request: ExtensionUiRequest,
    onRespond: (value: String?, confirmed: Boolean?, cancelled: Boolean) -> Unit,
) {
    val colors = HarnessTheme
    var draft by remember(request.id) { mutableStateOf(request.prefill.orEmpty()) }
    androidx.compose.ui.window.Dialog(onDismissRequest = { onRespond(null, null, true) }) {
        Column(
            Modifier
                .clip(RoundedCornerShape(Radii.lg))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, RoundedCornerShape(Radii.lg))
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            request.title?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            }
            request.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }

            when (request.method) {
                "confirm" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        TextButton(onClick = { onRespond(null, true, false) }) { Text("Confirmar", color = colors.toolOk) }
                        TextButton(onClick = { onRespond(null, false, false) }) { Text("Cancelar", color = colors.textSecondary) }
                    }
                }
                "select" -> {
                    request.options.forEach { option ->
                        Text(
                            option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radii.sm))
                                .clickable { onRespond(option, null, false) }
                                .padding(vertical = Space.sm),
                        )
                    }
                }
                "input", "editor" -> {
                    androidx.compose.material3.OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(request.placeholder.orEmpty()) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        TextButton(onClick = { onRespond(draft, null, false) }) { Text("Enviar", color = colors.textPrimary) }
                        TextButton(onClick = { onRespond(null, null, true) }) { Text("Cancelar", color = colors.textSecondary) }
                    }
                }
                else -> {
                    TextButton(onClick = { onRespond(null, null, true) }) { Text("Fechar", color = colors.textSecondary) }
                }
            }
        }
    }
}
