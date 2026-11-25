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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.HarnessViewModel
import dev.harness.HarnessUiState
import dev.harness.R
import dev.harness.core.projects.Project
import dev.harness.ui.components.HBadge
import dev.harness.ui.components.HBottomSheet
import dev.harness.ui.components.HButton
import dev.harness.ui.components.HButtonDefaults
import dev.harness.ui.components.HCard
import dev.harness.ui.components.HDialog
import dev.harness.ui.components.HEmptyState
import dev.harness.ui.components.HIconButton
import dev.harness.ui.components.HTextField
import dev.harness.ui.components.touchTargetClickable
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space
import java.util.Locale

/**
 * Projects: search, create, clone (with progress), open, delete with undo.
 */
@Composable
fun ProjectsScreen(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
) {
    val colors = HarnessTheme
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showClone by rememberSaveable { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Project?>(null) }
    val projects = viewModel.visibleProjects()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.lg),
    ) {
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.projects_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Text(
                    stringResource(R.string.projects_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            HButton(
                stringResource(R.string.projects_new),
                onClick = { showCreate = true },
                variant = HButtonDefaults.Variant.Secondary,
            )
            Spacer(Modifier.size(Space.xs))
            HButton(
                stringResource(R.string.projects_clone),
                onClick = { showClone = true },
                variant = HButtonDefaults.Variant.Primary,
            )
            HIconButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.projects_open_settings),
                onClick = viewModel::openSettings,
            )
        }

        Spacer(Modifier.height(Space.md))
        HTextField(
            value = uiState.projectsQuery,
            onValueChange = viewModel::setProjectsQuery,
            placeholder = stringResource(R.string.projects_search_hint),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        Spacer(Modifier.height(Space.sm))

        when {
            uiState.cloning -> CloningCard()
            projects.isEmpty() && uiState.projectsQuery.isBlank() -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                HEmptyState(
                    title = stringResource(R.string.projects_empty_title),
                    description = stringResource(R.string.projects_empty_desc),
                    icon = Icons.Default.List,
                    actionLabel = stringResource(R.string.projects_new),
                    onAction = { showCreate = true },
                )
            }
            projects.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                HEmptyState(
                    title = uiState.projectsQuery,
                    description = stringResource(R.string.projects_search_hint),
                )
            }
            else -> LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                items(projects, key = { it.hostPath }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { viewModel.openProject(project) },
                        onDelete = { deleteCandidate = project },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            onDismiss = { showCreate = false },
            onCreate = {
                viewModel.createProject(it)
                showCreate = false
            },
        )
    }
    if (showClone) {
        CloneProjectDialog(
            cloning = uiState.cloning,
            onDismiss = { showClone = false },
            onClone = {
                viewModel.cloneProject(it)
                showClone = false
            },
        )
    }
    deleteCandidate?.let { project ->
        HDialog(
            title = stringResource(R.string.projects_delete_confirm_title),
            text = {
                Text(
                    stringResource(R.string.projects_delete_confirm_text, project.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            onDismiss = { deleteCandidate = null },
            confirmLabel = stringResource(R.string.projects_delete_confirm_yes),
            onConfirm = {
                viewModel.deleteProject(project)
                deleteCandidate = null
            },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun CloningCard() {
    val colors = HarnessTheme
    HCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accent)
            Column {
                Text(
                    stringResource(R.string.projects_cloning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                Text(
                    stringResource(R.string.projects_cloning_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = HarnessTheme
    var menuOpen by remember { mutableStateOf(false) }
    HCard(onClick = onOpen, onClickLabel = project.name) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(colors.accentContainer, Radii.shapeSm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.List,
                    contentDescription = null,
                    tint = colors.onAccentContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HBadge(
                    text = stringResource(
                        if (project.isGitRepo) R.string.projects_git_repo else R.string.projects_local_folder,
                    ),
                    color = if (project.isGitRepo) colors.accentText else colors.textTertiary,
                )
            }
            Box {
                HIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.a11y_more_options),
                    onClick = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.projects_delete), color = colors.toolErrorText) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = colors.toolErrorText)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val colors = HarnessTheme
    HDialog(
        title = stringResource(R.string.projects_create_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.projects_create_cta),
        onConfirm = { onCreate(name.trim()) },
        confirmEnabled = name.isNotBlank(),
        dismissLabel = stringResource(R.string.action_cancel),
        text = {
            HTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.projects_create_name_hint),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            )
        },
    )
}

@Composable
private fun CloneProjectDialog(
    cloning: Boolean,
    onDismiss: () -> Unit,
    onClone: (String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    val valid = remember(url) {
        url.trim().startsWith("https://") || url.trim().startsWith("http://") || url.trim().startsWith("git@")
    }
    HDialog(
        title = stringResource(R.string.projects_clone_title),
        onDismiss = onDismiss,
        confirmLabel = if (cloning) stringResource(R.string.projects_cloning) else stringResource(R.string.projects_clone_cta),
        onConfirm = { onClone(url.trim()) },
        confirmEnabled = valid && !cloning,
        dismissLabel = stringResource(R.string.action_cancel),
        text = {
            HTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = stringResource(R.string.projects_clone_url_hint),
                singleLine = true,
                enabled = !cloning,
                supportingText = stringResource(R.string.projects_cloning_note),
                isError = url.isNotBlank() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            )
        },
    )
}
