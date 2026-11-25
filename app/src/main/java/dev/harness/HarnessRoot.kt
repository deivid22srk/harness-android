package dev.harness

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.harness.R
import dev.harness.core.rpc.ExtensionUiRequest
import dev.harness.ui.components.HNoticeSnackbarHost
import dev.harness.ui.components.touchTargetClickable
import dev.harness.ui.screens.ChatScreen
import dev.harness.ui.screens.OnboardingScreen
import dev.harness.ui.screens.ProjectsScreen
import dev.harness.ui.screens.SessionsScreen
import dev.harness.ui.screens.SettingsScreen
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/**
 * Root composable: screen switching (kept dependency-free — no nav library),
 * one-shot extension-UI dialogs, back navigation (predictive-back compatible)
 * and the global snackbar host for [UiNotice]s.
 */
@Composable
fun HarnessRoot(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
    onRequestBatteryExemption: () -> Unit,
    wideScreen: Boolean = false,
) {
    val colors = HarnessTheme
    val snackbarHostState = remember { SnackbarHostState() }
    val sessionsViewModel: SessionsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = SessionsViewModel.factory(viewModel.appForFactory()),
    )
    val sessionsState by sessionsViewModel.uiState.collectAsStateWithLifecycle()
    val packagesViewModel: PackagesViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = PackagesViewModel.factory(viewModel.appForFactory()),
    )
    val packagesState by packagesViewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(
        enabled = uiState.screen == Screen.Settings ||
            uiState.screen == Screen.Chat ||
            uiState.screen == Screen.Sessions,
    ) {
        when (uiState.screen) {
            // Settings: back leaves the open category first (predictive back),
            // then the screen.
            Screen.Settings -> {
                if (uiState.settingsSection != null) viewModel.backToSettingsHub() else viewModel.backToProjects()
            }
            Screen.Chat -> viewModel.backToProjects()
            Screen.Sessions -> viewModel.backFromSessions()
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        when (uiState.screen) {
            Screen.Onboarding -> OnboardingScreen(
                state = uiState.provision,
                onRetry = { viewModel.startProvisioning(force = false) },
            )

            Screen.Projects -> ProjectsScreen(
                uiState = uiState,
                viewModel = viewModel,
            )

            Screen.Settings -> SettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                packagesViewModel = packagesViewModel,
                packagesState = packagesState,
                onBack = viewModel::backToProjects,
                wideScreen = wideScreen,
            )

            Screen.Chat -> ChatScreen(
                uiState = uiState,
                viewModel = viewModel,
                sessionsState = sessionsState,
                sessionsViewModel = sessionsViewModel,
            )

            Screen.Sessions -> SessionsScreen(
                sessionsState = sessionsState,
                harnessState = uiState,
                sessionsViewModel = sessionsViewModel,
                harnessViewModel = viewModel,
            )
        }

        // Global snackbar for VM notices (errors, undo actions).
        val notice = uiState.notice
        HNoticeSnackbarHost(
            hostState = snackbarHostState,
            message = notice?.text,
            level = notice?.level ?: dev.harness.core.state.NoticeLevel.Info,
            onDismiss = viewModel::dismissNotice,
            actionLabel = notice?.action?.let { stringResource(R.string.projects_undo) },
            onAction = viewModel::onNoticeAction,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Space.lg),
        )

        uiState.pendingUi.firstOrNull()?.let { request ->
            ExtensionUiDialog(
                request = request,
                onRespond = { value, confirmed, cancelled ->
                    viewModel.respondExtensionUi(request, value, confirmed, cancelled)
                },
            )
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
    Dialog(onDismissRequest = { onRespond(null, null, true) }) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated, Radii.shapeLg)
                .padding(Space.lg),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.md),
        ) {
            request.title?.let {
                Text(
                    it,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
            }
            request.message?.let {
                Text(
                    it,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }

            when (request.method) {
                "confirm" -> {
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)) {
                        TextButton(onClick = { onRespond(null, true, false) }) {
                            Text(stringResource(R.string.action_confirm), color = colors.toolOk)
                        }
                        TextButton(onClick = { onRespond(null, false, false) }) {
                            Text(stringResource(R.string.action_cancel), color = colors.textSecondary)
                        }
                    }
                }
                "select" -> {
                    request.options.forEach { option ->
                        Text(
                            option,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .touchTargetClickable { onRespond(option, null, false) }
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
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Space.sm)) {
                        TextButton(onClick = { onRespond(draft, null, false) }) {
                            Text(stringResource(R.string.action_send), color = colors.accentText)
                        }
                        TextButton(onClick = { onRespond(null, null, true) }) {
                            Text(stringResource(R.string.action_cancel), color = colors.textSecondary)
                        }
                    }
                }
                else -> {
                    TextButton(onClick = { onRespond(null, null, true) }) {
                        Text(stringResource(R.string.action_close), color = colors.textSecondary)
                    }
                }
            }
        }
    }
}
