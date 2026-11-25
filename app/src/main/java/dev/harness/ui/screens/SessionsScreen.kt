package dev.harness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.HarnessUiState
import dev.harness.HarnessViewModel
import dev.harness.R
import dev.harness.SessionsUiState
import dev.harness.SessionsViewModel
import dev.harness.core.sessions.SessionSummary
import dev.harness.ui.components.HBadge
import dev.harness.ui.components.HButton
import dev.harness.ui.components.HDialog
import dev.harness.ui.components.HEmptyState
import dev.harness.ui.components.HErrorState
import dev.harness.ui.components.HIconButton
import dev.harness.ui.components.HSkeleton
import dev.harness.ui.components.HTextField
import dev.harness.ui.components.touchTargetClickable
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/**
 * "Sessões do projeto": busca, itens com ações (renomear/duplicar/excluir),
 * nova sessão em destaque, estados vazio/erro/carregamento e desfazer.
 * Zero lógica de negócio: renderiza [SessionsUiState] e despacha eventos.
 */
@Composable
fun SessionsScreen(
    sessionsState: SessionsUiState,
    harnessState: HarnessUiState,
    sessionsViewModel: SessionsViewModel,
    harnessViewModel: HarnessViewModel,
) {
    val colors = HarnessTheme
    val snackbar = remember { SnackbarHostState() }

    // Load on entry and whenever the project or the active session changes.
    LaunchedEffect(harnessState.activeProject?.hostPath, harnessState.chat.sessionFile) {
        val project = harnessState.activeProject ?: return@LaunchedEffect
        sessionsViewModel.load(
            project = project,
            activeSessionFile = harnessState.chat.sessionFile
                ?.let { sessionsViewModel.guestToHost(it) },
        )
    }

    // Session-list errors surface as a one-shot snackbar.
    LaunchedEffect(sessionsState.error) {
        sessionsState.error?.let {
            snackbar.showSnackbar(it)
            sessionsViewModel.dismissError()
        }
    }
    LaunchedEffect(sessionsState.pendingUndo) {
        sessionsState.pendingUndo?.let { summary ->
            val result = snackbar.showSnackbar(
                message = sessionsViewModel.undoMessage(summary),
                actionLabel = sessionsViewModel.undoLabel(),
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                sessionsViewModel.undoDelete()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            SessionsTopBar(
                project = sessionsState.project?.name,
                onBack = harnessViewModel::backFromSessions,
            )
            HTextField(
                value = sessionsState.query,
                onValueChange = sessionsViewModel::setQuery,
                label = stringResource(R.string.sessions_search_hint),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.sm)
                    .testTag("sessions_search"),
            )

            HButton(
                text = stringResource(R.string.sessions_new),
                onClick = harnessViewModel::startNewSessionInProject,
                icon = Icons.Default.Add,
                modifier = Modifier
                    .padding(horizontal = Space.lg, vertical = Space.xs)
                    .testTag("sessions_new"),
            )

            when {
                sessionsState.loading && sessionsState.sessions.isEmpty() -> SkeletonList()
                sessionsState.error != null && sessionsState.sessions.isEmpty() -> HErrorState(
                    title = stringResource(R.string.sessions_error_title),
                    message = sessionsState.error,
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = { sessionsViewModel.refresh(sessionsState.activeSessionFile) },
                )

                sessionsState.visibleSessions.isEmpty() && sessionsState.query.isBlank() -> HEmptyState(
                    title = stringResource(R.string.sessions_empty_title),
                    description = stringResource(R.string.sessions_empty_body),
                    icon = Icons.Default.List,
                )

                sessionsState.visibleSessions.isEmpty() -> HEmptyState(
                    title = stringResource(R.string.sessions_search_empty_title),
                    description = stringResource(R.string.sessions_search_empty_body),
                    icon = Icons.Default.Search,
                )

                else -> SessionList(
                    state = sessionsState,
                    sessionsViewModel = sessionsViewModel,
                    harnessViewModel = harnessViewModel,
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.lg),
        )
    }
}

@Composable
private fun SkeletonList() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        repeat(5) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                HSkeleton(height = 18.dp, modifier = Modifier.fillMaxWidth(0.7f))
                HSkeleton(height = 14.dp, modifier = Modifier.fillMaxWidth(0.45f))
            }
        }
    }
}

@Composable
private fun SessionsTopBar(project: String?, onBack: () -> Unit) {
    val colors = HarnessTheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xs, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.a11y_back),
            onClick = onBack,
            tint = colors.textPrimary,
        )
        Column(Modifier.weight(1f).padding(horizontal = Space.xs)) {
            Text(
                stringResource(R.string.sessions_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            project?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SessionList(
    state: SessionsUiState,
    sessionsViewModel: SessionsViewModel,
    harnessViewModel: HarnessViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("sessions_list"),
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        items(state.visibleSessions, key = { it.file }) { summary ->
            val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
                        sessionsViewModel.delete(summary, summary.file == state.activeSessionFile)
                        true
                    } else {
                        false
                    }
                },
            )
            androidx.compose.material3.SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    val colors = HarnessTheme
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radii.shapeLg)
                            .background(colors.toolErrorContainer)
                            .padding(horizontal = Space.lg)
                            .semantics { contentDescription = "Excluir ${summary.title}" },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.sessions_delete),
                            tint = colors.toolErrorText,
                        )
                    }
                },
            ) {
                SessionRow(
                    summary = summary,
                    isActive = summary.file == state.activeSessionFile,
                    onOpen = { harnessViewModel.openSession(summary) },
                    onRename = {
                        sessionsViewModel.rename(
                            summary = summary,
                            newName = it,
                            isActive = summary.file == state.activeSessionFile,
                            onActiveRename = harnessViewModel::renameActiveSession,
                        )
                    },
                    onDelete = { sessionsViewModel.delete(summary, summary.file == state.activeSessionFile) },
                    onDuplicate = { harnessViewModel.duplicateSession(summary) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    summary: SessionSummary,
    isActive: Boolean,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val colors = HarnessTheme
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radii.shapeLg)
            .background(if (isActive) colors.accentContainer else colors.surfaceElevated)
            .touchTargetClickable(onClick = onOpen)
            .padding(Space.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    relativeDate(summary.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) colors.onAccentContainer else colors.textTertiary,
                )
            }
            if (isActive) {
                HBadge(
                    text = stringResource(R.string.sessions_active),
                    color = colors.accent,
                    modifier = Modifier.padding(end = Space.xs),
                )
            }
            Box {
                HIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.sessions_actions_a11y, summary.title),
                    onClick = { menuOpen = true },
                    tint = if (isActive) colors.onAccentContainer else colors.textSecondary,
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sessions_rename)) },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            renameOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sessions_duplicate)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sessions_delete), color = colors.toolErrorText) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = colors.toolErrorText)
                        },
                        onClick = {
                            menuOpen = false
                            deleteOpen = true
                        },
                    )
                }
            }
        }
        if (summary.messageCount > 0 || summary.model != null) {
            Text(
                listOfNotNull(
                    stringResource(R.string.sessions_message_count, summary.messageCount),
                    summary.model,
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = if (isActive) colors.onAccentContainer else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (renameOpen) {
        var draft by remember(summary.file) { mutableStateOf(summary.name.orEmpty()) }
        HDialog(
            title = stringResource(R.string.sessions_rename_title),
            onDismiss = { renameOpen = false },
            confirmLabel = stringResource(R.string.action_confirm),
            confirmEnabled = draft.isNotBlank(),
            onConfirm = {
                onRename(draft)
                renameOpen = false
            },
            dismissLabel = stringResource(R.string.action_cancel),
            text = {
                HTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = stringResource(R.string.sessions_rename_label),
                    singleLine = true,
                )
            },
        )
    }
    if (deleteOpen) {
        HDialog(
            title = stringResource(R.string.sessions_delete_title),
            onDismiss = { deleteOpen = false },
            confirmLabel = stringResource(R.string.sessions_delete),
            onConfirm = {
                onDelete()
                deleteOpen = false
            },
            dismissLabel = stringResource(R.string.action_cancel),
            text = {
                Text(
                    stringResource(R.string.sessions_delete_body, summary.title),
                    color = colors.textSecondary,
                )
            },
        )
    }
}

/** "há 2 h"-style relative dates in pt-BR, computed from the file mtime. */
@Composable
private fun relativeDate(timestampMs: Long): String {
    if (timestampMs <= 0) return ""
    val diff = System.currentTimeMillis() - timestampMs
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.time_now)
        minutes < 60 -> stringResource(R.string.time_minutes, minutes)
        minutes < 60 * 24 -> stringResource(R.string.time_hours, minutes / 60)
        minutes < 60L * 24 * 30 -> stringResource(R.string.time_days, minutes / (60 * 24))
        else -> stringResource(R.string.time_months, minutes / (60L * 24 * 30))
    }
}


// ---------------------------------------------------------------------------
// Previews: dark + light + large font (PROMPT §2 rule 2)
// ---------------------------------------------------------------------------

@Composable
private fun SessionRowSample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SessionRow(
            summary = dev.harness.core.sessions.SessionSummary(
                file = "/x/2026-11-20T10-00-00-000Z_a.jsonl",
                sessionId = "aaaa",
                name = "Correção de login",
                firstUserText = "Corrija o bug de login",
                messageCount = 12,
                model = "anthropic/claude-sonnet-4-5",
                updatedAt = System.currentTimeMillis() - 3_600_000,
                truncated = false,
                corrupt = false,
            ),
            isActive = true,
            onOpen = {},
            onRename = {},
            onDelete = {},
            onDuplicate = {},
        )
        SessionRow(
            summary = dev.harness.core.sessions.SessionSummary(
                file = "/x/2026-11-19T09-00-00-000Z_b.jsonl",
                sessionId = "bbbb",
                name = null,
                firstUserText = "Explique este código",
                messageCount = 4,
                model = "google/gemini-2.5-pro",
                updatedAt = System.currentTimeMillis() - 86_400_000,
                truncated = false,
                corrupt = false,
            ),
            isActive = false,
            onOpen = {},
            onRename = {},
            onDelete = {},
            onDuplicate = {},
        )
    }
}

@Preview(name = "Escuro", showBackground = true, backgroundColor = 0xFF0B0E14, widthDp = 360)
@Composable
private fun SessionRowsDarkPreview() {
    dev.harness.ui.theme.HarnessThemeProvider(darkTheme = true, dynamicColor = false, fontScale = 1f) {
        MaterialTheme { SessionRowSample() }
    }
}

@Preview(name = "Claro", showBackground = true, backgroundColor = 0xFFF7F8FB, widthDp = 360)
@Composable
private fun SessionRowsLightPreview() {
    dev.harness.ui.theme.HarnessThemeProvider(darkTheme = false, dynamicColor = false, fontScale = 1f) {
        MaterialTheme { SessionRowSample() }
    }
}

@Preview(name = "Fonte grande", showBackground = true, backgroundColor = 0xFF0B0E14, widthDp = 360, fontScale = 1.4f)
@Composable
private fun SessionRowsLargeFontPreview() {
    dev.harness.ui.theme.HarnessThemeProvider(darkTheme = true, dynamicColor = false, fontScale = 1.3f) {
        MaterialTheme { SessionRowSample() }
    }
}
