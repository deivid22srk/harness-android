package dev.harness.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.harness.core.state.AgentStatus
import dev.harness.core.state.ChatItem
import dev.harness.core.state.ChatState
import dev.harness.core.state.NoticeLevel
import dev.harness.core.state.ToolStatus
import dev.harness.ui.components.MarkdownLite
import dev.harness.ui.components.ToolRow
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

@Composable
fun ChatList(
    state: ChatState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { shouldStickToBottom(listState) }.collect { autoScroll = it }
    }
    LaunchedEffect(state.items.size, state.status) {
        if (autoScroll && state.items.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(state.items.lastIndex) }
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.lg, end = Space.lg, top = Space.md, bottom = Space.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            items(state.items, key = { it.id }) { item ->
                when (item) {
                    is ChatItem.UserMessage -> UserBubble(item)
                    is ChatItem.AssistantMessage -> AssistantBlock(item)
                    is ChatItem.ToolGroup -> ToolGroupBlock(item)
                    is ChatItem.Notice -> NoticeRow(item)
                }
            }
            if (state.isBusy) {
                item(key = "busy") { BusyIndicator(state.status) }
            }
        }

        if (!autoScroll) {
            Box(Modifier.align(Alignment.BottomEnd).padding(Space.lg)) {
                JumpToBottom {
                    autoScroll = true
                }
            }
        }
    }
}

private fun shouldStickToBottom(listState: LazyListState): Boolean {
    val info = listState.layoutInfo
    if (info.totalItemsCount == 0) return true
    val last = info.visibleItemsInfo.lastOrNull() ?: return true
    return last.index >= info.totalItemsCount - 2
}

@Composable
private fun UserBubble(item: ChatItem.UserMessage) {
    val colors = HarnessTheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(colors.userBubble)
                .border(1.dp, colors.border, RoundedCornerShape(Radii.lg))
                .padding(horizontal = Space.md, vertical = Space.sm),
        ) {
            Text(item.text, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            if (item.imageCount > 0) {
                Text("${item.imageCount} imagem(ns)", style = MonoStyle, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun AssistantBlock(item: ChatItem.AssistantMessage) {
    val colors = HarnessTheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (item.thinking.isNotBlank()) {
            var open by remember(item.id) { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text("Raciocínio", style = MaterialTheme.typography.labelLarge, color = colors.textTertiary)
                Icon(
                    if (open) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
            AnimatedVisibility(open) {
                Text(item.thinking, style = MonoStyle, color = colors.textSecondary, fontStyle = FontStyle.Italic)
            }
        }
        if (item.text.isNotBlank()) {
            MarkdownLite(item.text)
        }
        item.error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.toolError)
        }
    }
}

@Composable
private fun ToolGroupBlock(group: ChatItem.ToolGroup) {
    val colors = HarnessTheme
    val running = group.tools.count { it.status == ToolStatus.Running }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (running > 0) "Executando ${group.tools.size} ferramenta(s)…"
            else "Usou ${group.tools.size} ferramenta(s)",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(Space.xs))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .border(1.dp, colors.border, RoundedCornerShape(Radii.md))
                .background(colors.surface)
                .padding(Space.sm),
        ) {
            group.tools.forEach { ToolRow(it) }
        }
    }
}

@Composable
private fun NoticeRow(item: ChatItem.Notice) {
    val colors = HarnessTheme
    val tint = when (item.level) {
        NoticeLevel.Info -> colors.textTertiary
        NoticeLevel.Warning -> colors.toolRunning
        NoticeLevel.Error -> colors.toolError
    }
    Text(
        text = item.text,
        style = MaterialTheme.typography.bodySmall,
        color = tint,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.sm),
    )
}

@Composable
private fun BusyIndicator(status: AgentStatus) {
    val colors = HarnessTheme
    val label = when (status) {
        AgentStatus.WaitingModel -> "Pensando…"
        AgentStatus.Streaming -> "Escrevendo…"
        AgentStatus.RunningTools -> "Executando…"
        AgentStatus.Compacting -> "Compactando contexto…"
        AgentStatus.Retrying -> "Tentando novamente…"
        AgentStatus.Idle -> ""
    }
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(colors.textSecondary.copy(alpha = alpha)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@Composable
private fun JumpToBottom(onClick: () -> Unit) {
    val colors = HarnessTheme
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.border, RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Ir para o fim", tint = colors.textPrimary)
    }
}
