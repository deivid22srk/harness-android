package dev.harness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import dev.harness.core.settings.Provider
import dev.harness.core.settings.Providers
import dev.harness.core.state.ChatState
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

@Composable
fun SettingsScreen(
    selectedProvider: String,
    thinkingLevel: String,
    diagnostics: String,
    onSelectProvider: (String) -> Unit,
    onSaveKey: (String, String) -> Unit,
    onClearEnvironment: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    var expandedProvider by remember { mutableStateOf(selectedProvider) }
    var keyDraft by remember(selectedProvider) { mutableStateOf("") }
    var showDiagnostics by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
    ) {
        Spacer(Modifier.height(Space.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Configurações", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Voltar", color = colors.textSecondary) }
        }

        SectionTitle("Provedor de IA")
        Providers.ALL.forEach { provider ->
            ProviderRow(
                provider = provider,
                selected = provider.id == expandedProvider,
                onClick = { expandedProvider = provider.id; onSelectProvider(provider.id) },
            )
        }

        Spacer(Modifier.height(Space.md))
        SectionTitle("Chave de API — ${Providers.byId(expandedProvider)?.label ?: expandedProvider}")
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            placeholder = { Text(Providers.byId(expandedProvider)?.keyHint.orEmpty().ifBlank { "cole a chave" }) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            TextButton(onClick = {
                if (keyDraft.isNotBlank()) {
                    onSaveKey(expandedProvider, keyDraft)
                    keyDraft = ""
                }
            }) { Text("Salvar chave", color = colors.textPrimary) }
        }
        Text(
            "A chave é cifrada com a Keystore do aparelho e nunca sai do app.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(Space.xl))
        SectionTitle("Raciocínio padrão")
        Text(thinkingLevel, style = MonoStyle, color = colors.textSecondary)

        Spacer(Modifier.height(Space.xl))
        SectionTitle("Armazenamento")
        TextButton(onClick = onClearEnvironment) {
            Text("Reinstalar ambiente", color = colors.toolError)
        }
        Text(
            "Remove o Alpine e as ferramentas instaladas. As chaves são preservadas.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )

        Spacer(Modifier.height(Space.xl))
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

        Spacer(Modifier.height(Space.xl))
        SectionTitle("Licenças")
        Text(
            "Este app usa PRoot (GPL-2.0), Alpine Linux e Pi (MIT). Veja THIRD_PARTY_NOTICES.md no repositório.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(Space.xxl))
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
private fun ProviderRow(provider: Provider, selected: Boolean, onClick: () -> Unit) {
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
        Text(provider.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(provider.envVar, style = MonoStyle, color = colors.textTertiary)
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
            Text("Configure uma chave de API em Configurações.", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
        models.forEach { (provider, id) ->
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
