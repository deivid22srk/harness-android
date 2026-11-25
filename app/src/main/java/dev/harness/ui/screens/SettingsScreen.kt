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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.harness.ModelOption
import dev.harness.core.settings.Provider
import dev.harness.core.settings.Providers
import dev.harness.core.state.ChatState
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

@Composable
fun SettingsScreen(
    providers: List<Provider>,
    selectedProvider: String,
    thinkingLevel: String,
    diagnostics: String,
    onSelectProvider: (String) -> Unit,
    onSaveKey: (String, String) -> Boolean,
    onAddCustomProvider: (String, String, String, String) -> Unit,
    onRemoveCustomProvider: (String) -> Unit,
    onOpenModels: (String) -> Unit,
    onClearEnvironment: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    var activeProvider by remember(selectedProvider) { mutableStateOf(selectedProvider) }
    var keyDraft by remember(activeProvider) { mutableStateOf("") }
    var keySaved by remember(activeProvider) { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showAddCustom by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
    ) {
        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Configurações",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text("Voltar", color = colors.textSecondary) }
        }

        SectionTitle("Provedor de IA")
        providers.forEach { provider ->
            ProviderRow(
                provider = provider,
                selected = provider.id == activeProvider,
                isCustom = provider.api != null,
                onClick = {
                    activeProvider = provider.id
                    keyDraft = ""
                    keySaved = false
                    onSelectProvider(provider.id)
                },
                onRemove = { onRemoveCustomProvider(provider.id) },
            )
        }
        Spacer(Modifier.height(Space.xs))
        TextButton(onClick = { showAddCustom = true }) {
            Text("+ Adicionar provedor personalizado", color = colors.textPrimary)
        }

        Spacer(Modifier.height(Space.md))
        SectionTitle("Chave de API — ${providers.firstOrNull { it.id == activeProvider }?.label ?: activeProvider}")
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it; keySaved = false },
            placeholder = { Text(providers.firstOrNull { it.id == activeProvider }?.keyHint.orEmpty().ifBlank { "cole a chave" }) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = keyDraft.isNotBlank(),
                onClick = {
                    if (onSaveKey(activeProvider, keyDraft)) {
                        keyDraft = ""
                        keySaved = true
                    }
                },
            ) { Text("Salvar chave", color = if (keyDraft.isNotBlank()) colors.textPrimary else colors.textTertiary) }
            if (keySaved) {
                Text("Chave salva", style = MaterialTheme.typography.bodySmall, color = colors.toolOk)
            }
        }
        Text(
            "A chave é cifrada com a Keystore do aparelho e nunca sai do app.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(Space.md))
        SectionTitle("Modelos")
        TextButton(onClick = { onOpenModels(activeProvider) }) {
            Text("Obter modelos", color = colors.textPrimary)
        }
        Text(
            "Escolha quais modelos deste provedor ficam disponíveis para uso.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(Space.lg))
        SectionTitle("Raciocínio padrão")
        Text(thinkingLevel, style = MonoStyle, color = colors.textSecondary)

        Spacer(Modifier.height(Space.lg))
        SectionTitle("Armazenamento")
        TextButton(onClick = onClearEnvironment) {
            Text("Reinstalar ambiente", color = colors.toolError)
        }
        Text(
            "Remove o Alpine e as ferramentas instaladas. As chaves são preservadas.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Diagnóstico", Modifier.weight(1f))
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp).clickable { showDiagnostics = !showDiagnostics },
            )
        }
        if (showDiagnostics) {
            Text(diagnostics.ifBlank { "(sem logs)" }, style = MonoStyle, color = colors.textSecondary)
        }

        Spacer(Modifier.height(Space.lg))
        SectionTitle("Licenças")
        Text(
            "Este app usa PRoot (GPL-2.0), Alpine Linux e Pi (MIT). Veja THIRD_PARTY_NOTICES.md no repositório.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(Space.xxl))
    }

    if (showAddCustom) {
        AddCustomProviderDialog(
            onDismiss = { showAddCustom = false },
            onAdd = { id, label, baseUrl, api ->
                onAddCustomProvider(id, label, baseUrl, api)
                showAddCustom = false
            },
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = HarnessTheme.textTertiary,
        modifier = modifier.padding(vertical = Space.sm),
    )
}

@Composable
private fun ProviderRow(
    provider: Provider,
    selected: Boolean,
    isCustom: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = HarnessTheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(Radii.sm))
            .background(if (selected) colors.accentMuted else colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(Radii.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(provider.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            if (isCustom) {
                Text(provider.baseUrl.orEmpty(), style = MonoStyle, color = colors.textTertiary, maxLines = 1)
            }
        }
        if (isCustom) {
            Text(
                "remover",
                style = MaterialTheme.typography.bodySmall,
                color = colors.toolError,
                modifier = Modifier.clickable(onClick = onRemove).padding(Space.xs),
            )
        }
        Text(provider.envVar, style = MonoStyle, color = colors.textTertiary)
    }
}

@Composable
private fun AddCustomProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
) {
    val colors = HarnessTheme
    var id by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var api by remember { mutableStateOf(Providers.APIS.first()) }
    var apiExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        title = { Text("Provedor personalizado", color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(
                    value = id, onValueChange = { id = it },
                    label = { Text("identificador") },
                    placeholder = { Text("meu-provedor") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("nome") },
                    placeholder = { Text("Meu Provedor") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("base URL") },
                    placeholder = { Text("https://api.exemplo.com/v1") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text("API", style = MaterialTheme.typography.labelLarge, color = colors.textTertiary)
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.sm))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(Radii.sm))
                            .clickable { apiExpanded = !apiExpanded }
                            .padding(horizontal = Space.md, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(api, style = MonoStyle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = colors.textTertiary)
                    }
                    if (apiExpanded) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 44.dp)
                                .clip(RoundedCornerShape(Radii.sm))
                                .background(colors.surface)
                                .border(1.dp, colors.border, RoundedCornerShape(Radii.sm)),
                        ) {
                            Providers.APIS.forEach { option ->
                                Text(
                                    option,
                                    style = MonoStyle,
                                    color = colors.textPrimary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { api = option; apiExpanded = false }
                                        .padding(horizontal = Space.md, vertical = Space.sm),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = id.isNotBlank() && baseUrl.isNotBlank(),
                onClick = { onAdd(id, label, baseUrl, api) },
            ) { Text("Adicionar", color = if (id.isNotBlank() && baseUrl.isNotBlank()) colors.textPrimary else colors.textTertiary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = colors.textSecondary) } },
    )
}

/**
 * "Obter modelos" dialog: lists the models reported by Pi and lets the user
 * select/deselect them, including select-all / clear-all.
 */
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        title = {
            Column {
                Text("Modelos — $providerLabel", color = colors.textPrimary)
                Text(
                    "$selectedCount de ${models.size} selecionados",
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
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accent)
                        Spacer(Modifier.size(Space.md))
                        Text("Obtendo modelos…", color = colors.textSecondary)
                    }
                    error != null -> Text(error, color = colors.toolError, style = MaterialTheme.typography.bodySmall)
                    models.isEmpty() -> Text(
                        "Nenhum modelo encontrado. Verifique a chave de API do provedor.",
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            TextButton(onClick = { onSelectAll(true) }) { Text("Selecionar tudo", color = colors.textPrimary) }
                            TextButton(onClick = { onSelectAll(false) }) { Text("Limpar", color = colors.textSecondary) }
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
            TextButton(onClick = onSave, enabled = !loading && models.isNotEmpty()) {
                Text("Salvar", color = colors.textPrimary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = colors.textSecondary) } },
    )
}

@Composable
private fun ModelRow(option: ModelOption, onToggle: () -> Unit) {
    val colors = HarnessTheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.sm))
            .clickable(onClick = onToggle)
            .padding(vertical = Space.sm, horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (option.selected) colors.toolOk else colors.surface)
                .border(1.dp, if (option.selected) colors.toolOk else colors.border, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (option.selected) {
                Text("✓", color = colors.background, style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(option.id, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1)
            Text(option.provider, style = MonoStyle, color = colors.textTertiary, maxLines = 1)
        }
    }
}

/** Bottom-sheet style model picker. */
@Composable
fun ModelPickerSheet(
    state: ChatState,
    models: List<Pair<String, String>>,
    onPick: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = Radii.lg, topEnd = Radii.lg))
            .background(colors.surfaceElevated)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text("Modelo", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(
            state.model?.let { "${it.name} · ${it.provider}" } ?: "Nenhum modelo selecionado",
            style = MonoStyle,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(Space.sm))
        if (models.isEmpty()) {
            Text(
                "Nenhum modelo selecionado. Use Configurações → Obter modelos.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(models, key = { "${it.first}/${it.second}" }) { (provider, id) ->
                Text(
                    "$provider/$id",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.sm))
                        .clickable { onPick(provider, id) }
                        .padding(vertical = Space.sm, horizontal = Space.sm),
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Fechar", color = colors.textSecondary)
        }
    }
}

/** Bottom-sheet style reasoning picker. */
@Composable
fun ThinkingPickerSheet(
    levels: List<String>,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = Radii.lg, topEnd = Radii.lg))
            .background(colors.surfaceElevated)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text("Raciocínio", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Spacer(Modifier.height(Space.xs))
        levels.forEach { level ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.sm))
                    .background(if (level == current) colors.accentMuted else colors.surfaceElevated)
                    .clickable { onPick(level) }
                    .padding(vertical = Space.sm, horizontal = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(thinkingLabel(level), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                if (level == current) Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = colors.textTertiary)
            }
        }
        Spacer(Modifier.height(Space.sm))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Fechar", color = colors.textSecondary)
        }
    }
}
