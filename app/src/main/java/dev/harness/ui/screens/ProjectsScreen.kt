package dev.harness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.harness.core.projects.Project
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

@Composable
fun ProjectsScreen(
    projects: List<Project>,
    cloning: Boolean,
    error: String?,
    onOpen: (Project) -> Unit,
    onCreate: (String) -> Unit,
    onClone: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    var newName by remember { mutableStateOf("") }
    var cloneUrl by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var showClone by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Space.lg),
    ) {
        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Projetos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Text("Escolha onde o agente vai trabalhar", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            TextButton(onClick = {
                showCreate = !showCreate
                showClone = false
            }) { Text("Novo", color = colors.textPrimary) }
            TextButton(onClick = {
                showClone = !showClone
                showCreate = false
            }) { Text("Clonar", color = colors.textPrimary) }
        }

        if (showCreate) {
            Row(Modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("nome-da-pasta") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(Space.sm))
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Criar",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(28.dp).clickable {
                        if (newName.isNotBlank()) {
                            onCreate(newName); newName = ""; showCreate = false
                        }
                    },
                )
            }
        }
        if (showClone) {
            Row(Modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = cloneUrl,
                    onValueChange = { cloneUrl = it },
                    placeholder = { Text("https://github.com/…") },
                    singleLine = true,
                    enabled = !cloning,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(Space.sm))
                TextButton(
                    onClick = { if (cloneUrl.isNotBlank()) onClone(cloneUrl) },
                    enabled = !cloning && cloneUrl.isNotBlank(),
                ) { Text(if (cloning) "Clonando…" else "Clonar", color = colors.textPrimary) }
            }
        }

        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.toolError, modifier = Modifier.padding(vertical = Space.xs))
        }

        Spacer(Modifier.height(Space.sm))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            items(projects, key = { it.hostPath }) { project ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.md))
                        .border(1.dp, colors.border, RoundedCornerShape(Radii.md))
                        .background(colors.surface)
                        .clickable { onOpen(project) }
                        .padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                        Text(
                            if (project.isGitRepo) "repositório git" else "pasta local",
                            style = MonoStyle,
                            color = colors.textTertiary,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall, color = colors.textTertiary)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.md),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = onOpenSettings) {
                Text("Configurações", color = colors.textSecondary)
            }
        }
    }
}
