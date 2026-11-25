package dev.harness.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.harness.HarnessViewModel
import dev.harness.PendingImage
import dev.harness.R
import dev.harness.core.state.ChatState
import dev.harness.ui.components.HChip
import dev.harness.ui.components.HIconButton
import dev.harness.ui.components.touchTargetClickable
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/**
 * Fixed bottom composer: multiline field with persisted draft (per project,
 * survives process death), model/reasoning chips, queued-message indicator,
 * pending image attachments and a send button that turns into Stop.
 */
@Composable
fun Composer(
    state: ChatState,
    pendingImages: List<PendingImage>,
    draftKey: String,
    queueSize: Int,
    onSend: (String) -> Unit,
    onQueue: (String) -> Unit,
    prefill: String? = null,
    prefillToken: Int = 0,
    onStop: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenThinkingPicker: () -> Unit,
    onAttach: () -> Unit,
    onRemoveImage: (PendingImage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    // Draft is scoped per project by the SaveableStateProvider around this
    // composable (restores correctly per project, survives process death).
    var text by rememberSaveable { mutableStateOf("") }

    // "Editar e reenviar" pushes the past message into the field.
    LaunchedEffect(prefillToken) {
        prefill?.let { text = it }
    }

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
        // Chips row: model, reasoning, context usage, queued messages
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HChip(
                text = state.model?.name ?: stringResource(R.string.composer_model_chip),
                onClick = onOpenModelPicker,
                trailing = {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
            HChip(
                text = thinkingLabel(state.thinkingLevel),
                onClick = onOpenThinkingPicker,
                trailing = {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
            if (queueSize > 0) {
                Text(
                    stringResource(R.string.queue_steering_label, queueSize),
                    style = MonoStyle,
                    color = colors.toolRunning,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.contextPercent != null) {
                Text(
                    "${state.contextPercent}%",
                    style = MonoStyle,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = Space.xs),
                )
            }
        }

        // Pending attachments
        if (pendingImages.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                pendingImages.take(4).forEach { image ->
                    Row(
                        Modifier
                            .clip(Radii.shapeSm)
                            .background(colors.surfaceElevated)
                            .padding(horizontal = Space.sm, vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Text(image.label, style = MonoStyle, color = colors.textSecondary)
                        HIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = stringResource(R.string.attach_remove),
                            onClick = { onRemoveImage(image) },
                            size = 32.dp,
                        )
                    }
                }
                if (pendingImages.size > 4) {
                    Text("+${pendingImages.size - 4}", style = MonoStyle, color = colors.textTertiary)
                }
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radii.shapeLg)
                .background(colors.surface)
                .border(1.dp, colors.border, Radii.shapeLg)
                .padding(horizontal = Space.sm, vertical = Space.xs),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            HIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.composer_attach),
                onClick = onAttach,
                tint = colors.textSecondary,
            )

            Box(Modifier.weight(1f).padding(vertical = Space.sm).heightIn(min = 32.dp)) {
                if (text.isEmpty()) {
                    Text(
                        stringResource(R.string.chat_input_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textTertiary,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // While busy: queue the message (steer) AND keep Stop reachable.
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.Bottom) {
                if (busy) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .touchTargetClickable(
                                enabled = canSend,
                                role = androidx.compose.ui.semantics.Role.Button,
                                shape = Radii.shapeMd,
                            ) {
                                val payload = text
                                text = ""
                                onQueue(payload)
                            }
                            .background(
                                if (canSend) colors.accentButton else colors.surfaceElevated,
                                Radii.shapeMd,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.composer_queue),
                            tint = if (canSend) colors.onAccent else colors.textTertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .touchTargetClickable(
                            enabled = busy || canSend,
                            role = androidx.compose.ui.semantics.Role.Button,
                            shape = Radii.shapeMd,
                        ) {
                            if (busy) {
                                onStop()
                            } else {
                                val payload = text
                                text = ""
                                onSend(payload)
                            }
                        }
                        .background(
                            when {
                                busy -> colors.toolErrorContainer
                                canSend -> colors.accentButton
                                else -> colors.surfaceElevated
                            },
                            Radii.shapeMd,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (busy) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(if (busy) R.string.chat_stop else R.string.chat_send),
                        tint = when {
                            busy -> colors.toolErrorText
                            canSend -> colors.onAccent
                            else -> colors.textTertiary
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** Photo picker sheet. */
@Composable
fun AttachSheet(
    onPickImage: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickImage(uri)
        onDismiss()
    }
    dev.harness.ui.components.HBottomSheet(
        title = stringResource(R.string.attach_sheet_title),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            dev.harness.ui.components.HButton(
                text = stringResource(R.string.attach_sheet_pick),
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                variant = dev.harness.ui.components.HButtonDefaults.Variant.Secondary,
            )
            Text(
                stringResource(R.string.attach_sheet_note),
                style = MaterialTheme.typography.bodySmall,
                color = HarnessTheme.textSecondary,
            )
        }
    }
}
