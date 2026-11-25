package dev.harness.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.HarnessViewModel
import dev.harness.HarnessUiState
import dev.harness.R
import dev.harness.core.pi.ConnectionState
import dev.harness.core.state.AgentStatus
import dev.harness.core.state.ChatItem
import dev.harness.core.state.ChatState
import dev.harness.core.state.NoticeLevel
import dev.harness.core.state.ToolStatus
import dev.harness.ui.components.HBadge
import dev.harness.ui.components.HBottomSheet
import dev.harness.ui.components.HButton
import dev.harness.ui.components.HButtonDefaults
import dev.harness.ui.components.HChip
import dev.harness.ui.components.HEmptyState
import dev.harness.ui.components.HIconButton
import dev.harness.ui.components.HMarkdown
import dev.harness.ui.components.ToolCallCard
import dev.harness.ui.components.touchTargetClickable
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space
import kotlinx.coroutines.launch

private val MaxContentWidth = 720.dp
private val MaxMessageWidth = 640.dp

/** Chat screen: branded top bar, message list, error banner and composer. */
@Composable
fun ChatScreen(
    uiState: HarnessUiState,
    viewModel: HarnessViewModel,
) {
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding(),
    ) {
        ChatTopBar(
            title = uiState.activeProject?.name ?: stringResource(R.string.app_name),
            subtitle = uiState.chat.sessionName,
            connection = uiState.connection,
            modelName = uiState.chat.model?.name,
            onNewSession = viewModel::newSession,
            onSettings = viewModel::openSettings,
            onBack = viewModel::backToProjects,
        )

        val sessionError = uiState.chat.error
        if (sessionError != null || uiState.connection == ConnectionState.Failed) {
            ConnectionErrorBanner(
                message = sessionError ?: stringResource(R.string.chat_session_error_title),
                onRetry = viewModel::restartSession,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            ChatList(
                state = uiState.chat,
                isBusy = uiState.chat.isBusy,
                lastPrompt = viewModel.lastPromptForRegeneration(),
                onRegenerate = viewModel::regenerate,
                onEditResend = viewModel::requestEditResend,
                onSuggestion = viewModel::send,
            )
        }

        // Draft state is scoped per project: key() disposes on switch and the
        // holder restores the exact draft when returning to a project.
        val draftKey = uiState.activeProject?.hostPath ?: "draft"
        val draftHolder = rememberSaveableStateHolder()
        key(draftKey) {
            draftHolder.SaveableStateProvider(draftKey) {
                Composer(
                    state = uiState.chat,
                    pendingImages = uiState.pendingImages,
                    draftKey = draftKey,
                    queueSize = uiState.chat.steeringQueue.size + uiState.chat.followUpQueue.size,
                    onSend = viewModel::send,
                    onQueue = viewModel::steer,
                    prefill = uiState.composerPrefill,
                    prefillToken = uiState.composerPrefillToken,
                    onStop = viewModel::stopAgent,
                    onOpenModelPicker = { viewModel.setPicker(HarnessViewModel.Pickers.Model, true) },
                    onOpenThinkingPicker = { viewModel.setPicker(HarnessViewModel.Pickers.Thinking, true) },
                    onAttach = { viewModel.setPicker(HarnessViewModel.Pickers.Attach, true) },
                    onRemoveImage = viewModel::removePendingImage,
                )
            }
        }
    }

    if (uiState.showModelPicker) {
        HBottomSheet(
            title = stringResource(R.string.model_picker_title),
            onDismiss = { viewModel.setPicker(HarnessViewModel.Pickers.Model, false) },
        ) {
            ModelPickerSheet(
                current = uiState.chat.model?.let { "${it.provider}/${it.id}" },
                models = uiState.chatModels,
                onPick = viewModel::pickModel,
            )
        }
    }

    if (uiState.showThinkingPicker) {
        HBottomSheet(
            title = stringResource(R.string.thinking_picker_title),
            onDismiss = { viewModel.setPicker(HarnessViewModel.Pickers.Thinking, false) },
        ) {
            ThinkingPickerSheet(
                levels = uiState.chat.availableThinkingLevels.ifEmpty {
                    listOf("off", "minimal", "low", "medium", "high")
                },
                current = uiState.thinkingLevel,
                onPick = viewModel::pickThinking,
            )
        }
    }

    if (uiState.showAttachSheet) {
        AttachSheet(
            onPickImage = { viewModel.addImageAttachment(it) },
            onDismiss = { viewModel.setPicker(HarnessViewModel.Pickers.Attach, false) },
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    subtitle: String?,
    connection: ConnectionState,
    modelName: String?,
    onNewSession: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = Space.xs, vertical = Space.xs),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            HIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.a11y_back),
                onClick = onBack,
                tint = colors.textPrimary,
            )
            Column(Modifier.weight(1f).padding(horizontal = Space.xs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    HBadge(text = connectionLabel(connection), color = connectionColor(connection))
                    Text(
                        subtitle ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HIconButton(
                icon = Icons.Default.Add,
                contentDescription = stringResource(R.string.chat_new_session),
                onClick = onNewSession,
            )
            HIconButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.a11y_open_settings),
                onClick = onSettings,
            )
        }
        modelName?.let {
            Text(
                it,
                style = MonoStyle,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.xxl + Space.lg, bottom = Space.xs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun connectionLabel(state: ConnectionState): String = stringResource(
    when (state) {
        ConnectionState.Connected -> R.string.conn_connected
        ConnectionState.Starting -> R.string.conn_starting
        ConnectionState.Restarting -> R.string.conn_restarting
        ConnectionState.Stopped -> R.string.conn_stopped
        ConnectionState.Failed -> R.string.conn_failed
    },
)

@Composable
private fun connectionColor(state: ConnectionState) = when (state) {
    ConnectionState.Connected -> HarnessTheme.toolOk
    ConnectionState.Starting, ConnectionState.Restarting -> HarnessTheme.toolRunning
    ConnectionState.Stopped -> HarnessTheme.textTertiary
    ConnectionState.Failed -> HarnessTheme.toolError
}

@Composable
private fun ConnectionErrorBanner(message: String, onRetry: () -> Unit) {
    val colors = HarnessTheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.xs)
            .clip(Radii.shapeMd)
            .background(colors.toolErrorContainer)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = colors.toolErrorText,
            modifier = Modifier.size(18.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.toolErrorText,
            modifier = Modifier.weight(1f),
        )
        HButton(
            stringResource(R.string.chat_connection_retry),
            onRetry,
            variant = HButtonDefaults.Variant.Ghost,
        )
    }
}

/** Message list with streaming-aware autoscroll and jump-to-bottom. */
@Composable
fun ChatList(
    state: ChatState,
    isBusy: Boolean,
    lastPrompt: String?,
    onRegenerate: () -> Unit,
    onEditResend: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var stuckToBottom by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val colors = HarnessTheme

    // Track "stuck to bottom" continuously (scroll position changes).
    LaunchedEffect(listState) {
        snapshotFlow { isStuckToBottom(listState) }.collect { stuckToBottom = it }
    }

    // Re-scroll when items are added OR when the streaming tail grows.
    val tailLength = (state.items.lastOrNull() as? ChatItem.AssistantMessage)?.text?.length ?: 0
    LaunchedEffect(state.items.size, tailLength, isBusy) {
        if (stuckToBottom && state.items.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(state.items.lastIndex) }
        }
    }

    val isEmpty = state.items.isEmpty()

    Box(modifier.fillMaxSize()) {
        if (isEmpty) {
            Column(
                Modifier.fillMaxSize().padding(Space.lg),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HEmptyState(
                    title = stringResource(R.string.chat_empty_title),
                    description = stringResource(R.string.chat_empty_desc),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = MaxMessageWidth),
                ) {
                    listOf(
                        stringResource(R.string.chat_suggestion_1),
                        stringResource(R.string.chat_suggestion_2),
                        stringResource(R.string.chat_suggestion_3),
                    ).forEach { suggestion ->
                        HChip(text = suggestion, onClick = { onSuggestion(suggestion) })
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.lg, end = Space.lg, top = Space.md, bottom = Space.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(state.items, key = { it.id }, contentType = { it::class.simpleName }) { item ->
                    Column(Modifier.widthIn(max = MaxMessageWidth).fillMaxWidth()) {
                        when (item) {
                            is ChatItem.UserMessage -> UserMessage(item, onEditResend)
                            is ChatItem.AssistantMessage -> AssistantMessage(
                                item,
                                canRegenerate = lastPrompt != null && !isBusy && item == state.items.lastOrNull(),
                                onRegenerate = onRegenerate,
                            )
                            is ChatItem.ToolGroup -> ToolGroupBlock(item)
                            is ChatItem.Notice -> NoticeRow(item)
                        }
                    }
                }
                if (isBusy) {
                    item(key = "busy") { BusyIndicator(state.status) }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = !stuckToBottom,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.lg),
            ) {
                JumpToBottom(
                    onClick = {
                        stuckToBottom = true
                        scope.launch { runCatching { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) } }
                    },
                )
            }
        }
    }
}

private fun isStuckToBottom(listState: LazyListState): Boolean {
    val info = listState.layoutInfo
    if (info.totalItemsCount == 0) return true
    val last = info.visibleItemsInfo.lastOrNull() ?: return true
    return last.index >= info.totalItemsCount - 2 &&
        last.offset + last.size <= info.viewportEndOffset + 80
}

@Composable
private fun UserMessage(
    item: ChatItem.UserMessage,
    onEditResend: (String) -> Unit,
) {
    val colors = HarnessTheme
    val clipboard = LocalClipboardManager.current
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                Modifier
                    .widthIn(max = MaxMessageWidth * 0.82f)
                    .clip(Radii.shapeLg)
                    .background(colors.userBubble)
                    .padding(horizontal = Space.md, vertical = Space.sm),
            ) {
                Text(item.text, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                if (item.imageCount > 0) {
                    Text(
                        stringResource(R.string.chat_user_images, item.imageCount),
                        style = MonoStyle,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        MessageActions(
            listOf(
                MessageAction(stringResource(R.string.msg_edit_resend)) { onEditResend(item.text) },
                MessageAction(stringResource(R.string.msg_copy)) {
                    clipboard.setText(AnnotatedString(item.text))
                },
            ),
        )
    }
}

@Composable
private fun AssistantMessage(
    item: ChatItem.AssistantMessage,
    canRegenerate: Boolean,
    onRegenerate: () -> Unit,
) {
    val colors = HarnessTheme
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth()) {
        if (item.thinking.isNotBlank()) {
            dev.harness.ui.components.CollapsibleSection(title = stringResource(R.string.reasoning_label)) {
                Text(
                    item.thinking,
                    style = MonoStyle,
                    color = colors.textSecondary,
                    fontStyle = FontStyle.Italic,
                )
            }
            Spacer(Modifier.height(Space.xs))
        }
        if (item.text.isNotBlank()) {
            SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                HMarkdown(item.text)
            }
        }
        item.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.toolErrorText,
            )
        }
        if (!item.streaming && (item.text.isNotBlank() || item.error != null)) {
            val actions = buildList {
                add(
                    MessageAction(stringResource(R.string.msg_copy)) {
                        clipboard.setText(AnnotatedString(item.text.ifBlank { item.error ?: "" }))
                    },
                )
                if (canRegenerate) {
                    add(MessageAction(stringResource(R.string.msg_regenerate)) { onRegenerate() })
                }
            }
            MessageActions(actions)
        }
    }
}

private data class MessageAction(val label: String, val onClick: () -> Unit)

@Composable
private fun MessageActions(actions: List<MessageAction>) {
    val colors = HarnessTheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        modifier = Modifier.padding(top = Space.xs),
    ) {
        actions.forEach { action ->
            Text(
                action.label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
                modifier = Modifier
                    .touchTargetClickable { action.onClick() }
                    .padding(horizontal = Space.sm, vertical = Space.xs),
            )
        }
    }
}

@Composable
private fun ToolGroupBlock(group: ChatItem.ToolGroup) {
    val colors = HarnessTheme
    val running = group.tools.count { it.status == ToolStatus.Running }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (running > 0) {
                stringResource(R.string.tool_group_running, group.tools.size)
            } else {
                stringResource(R.string.tool_group_done, group.tools.size)
            },
            style = MaterialTheme.typography.labelLarge,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(Space.xs))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radii.shapeMd)
                .border(1.dp, colors.border, Radii.shapeMd)
                .background(colors.surface)
                .padding(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            group.tools.forEach { ToolCallCard(it) }
        }
    }
}

@Composable
private fun NoticeRow(item: ChatItem.Notice) {
    val colors = HarnessTheme
    val tint = when (item.level) {
        NoticeLevel.Info -> colors.textTertiary
        NoticeLevel.Warning -> colors.toolRunning
        NoticeLevel.Error -> colors.toolErrorText
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radii.shapeSm)
            .background(colors.surfaceElevated)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(tint))
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}

@Composable
private fun BusyIndicator(status: AgentStatus) {
    val colors = HarnessTheme
    val label = stringResource(
        when (status) {
            AgentStatus.WaitingModel -> R.string.busy_waiting_model
            AgentStatus.Streaming -> R.string.busy_streaming
            AgentStatus.RunningTools -> R.string.busy_running_tools
            AgentStatus.Compacting -> R.string.busy_compacting
            AgentStatus.Retrying -> R.string.busy_retrying
            AgentStatus.Idle -> R.string.busy_streaming
        },
    )
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900),
            RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.accent.copy(alpha = alpha)),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun JumpToBottom(onClick: () -> Unit) {
    val colors = HarnessTheme
    HIconButton(
        icon = Icons.Default.KeyboardArrowDown,
        contentDescription = stringResource(R.string.chat_jump_to_bottom),
        onClick = onClick,
        tint = colors.textPrimary,
        filled = true,
    )
}

@Composable
private fun ModelPickerSheet(
    current: String?,
    models: List<Pair<String, String>>,
    onPick: (String, String) -> Unit,
) {
    val colors = HarnessTheme
    if (models.isEmpty()) {
        Text(
            stringResource(R.string.model_picker_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        items(models, key = { "${it.first}/${it.second}" }) { (provider, id) ->
            val qualified = "$provider/$id"
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radii.shapeSm)
                    .background(if (qualified == current) colors.accentContainer else colors.surfaceElevated)
                    .touchTargetClickable { onPick(provider, id) }
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (qualified == current) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.onAccentContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    qualified,
                    style = MonoStyle,
                    color = if (qualified == current) colors.onAccentContainer else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ThinkingPickerSheet(
    levels: List<String>,
    current: String,
    onPick: (String) -> Unit,
) {
    val colors = HarnessTheme
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        items(levels, key = { it }) { level ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radii.shapeSm)
                    .background(if (level == current) colors.accentContainer else colors.surfaceElevated)
                    .touchTargetClickable { onPick(level) }
                    .padding(horizontal = Space.md, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    thinkingLabel(level),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (level == current) colors.onAccentContainer else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (level == current) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.onAccentContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun thinkingLabel(level: String): String = stringResource(
    when (level) {
        "off" -> R.string.thinking_chip_off
        "minimal" -> R.string.thinking_chip_minimal
        "low" -> R.string.thinking_chip_low
        "medium" -> R.string.thinking_chip_medium
        "high" -> R.string.thinking_chip_high
        "xhigh" -> R.string.thinking_chip_xhigh
        "max" -> R.string.thinking_chip_max
        else -> R.string.thinking_chip_fallback
    },
)
