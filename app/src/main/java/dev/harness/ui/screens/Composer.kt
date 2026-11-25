package dev.harness.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.harness.core.state.ChatState
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/**
 * Fixed bottom composer: multiline field, send↔stop button, and model/reasoning
 * chips that open bottom sheets (PROMPT §8).
 */
@Composable
fun Composer(
    state: ChatState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenThinkingPicker: () -> Unit,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    var text by remember { mutableStateOf("") }

    val busy = state.isBusy
    val canSend = text.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        // Chips row
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            ComposerChip(
                label = "Build",
                onClick = {},
            )
            ComposerChip(
                label = state.model?.name ?: "Modelo",
                onClick = onOpenModelPicker,
            )
            ComposerChip(
                label = thinkingLabel(state.thinkingLevel),
                onClick = onOpenThinkingPicker,
            )
            if (state.contextPercent != null) {
                Text("${state.contextPercent}%", style = MonoStyle, color = colors.textTertiary)
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(Radii.lg))
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAttach),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Anexar", tint = colors.textSecondary)
            }

            Box(Modifier.weight(1f).padding(vertical = Space.sm)) {
                if (text.isEmpty()) {
                    Text("Mensagem", style = MaterialTheme.typography.bodyLarge, color = colors.textTertiary)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.textPrimary),
                    maxLines = 6,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(if (canSend || busy) colors.accentMuted else colors.surfaceElevated)
                    .clickable(enabled = busy || canSend) {
                        if (busy) onStop() else {
                            val payload = text
                            text = ""
                            onSend(payload)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (busy) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (busy) "Parar" else "Enviar",
                    tint = if (canSend || busy) colors.textPrimary else colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ComposerChip(label: String, onClick: () -> Unit) {
    val colors = HarnessTheme
    Row(
        modifier = Modifier
            .clip(Radii.pill)
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(Radii.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, maxLines = 1)
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
    }
}

fun thinkingLabel(level: String): String = when (level) {
    "off" -> "Raciocínio off"
    "minimal" -> "Raciocínio mínimo"
    "low" -> "Raciocínio baixo"
    "medium" -> "Raciocínio médio"
    "high" -> "Raciocínio alto"
    "xhigh" -> "Raciocínio xalto"
    "max" -> "Raciocínio máx."
    else -> "Raciocínio"
}
