package dev.harness.core.state

import dev.harness.core.rpc.AssistantStreamEvent
import dev.harness.core.rpc.ContentBlock
import dev.harness.core.rpc.PiEvent
import dev.harness.core.rpc.PiMessage
import dev.harness.core.rpc.ToolCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure, deterministic projection of Pi session events onto [ChatState].
 *
 * Streaming is reconstructed exactly as docs/json.md prescribes: buffer deltas
 * for a live view, then replace with the authoritative content in
 * `text_end` / `thinking_end` / `toolcall_end`, and finally with the whole
 * message from `message_end`.
 *
 * No Android dependencies: unit-testable on the JVM.
 */
class ChatReducer {

    fun reduce(state: ChatState, event: PiEvent): ChatState = when (event) {
        is PiEvent.AgentStart -> state.copy(status = AgentStatus.WaitingModel, error = null)

        is PiEvent.TurnStart -> state.copy(status = AgentStatus.WaitingModel)

        is PiEvent.MessageStart -> {
            val msg = event.message
            if (msg?.role == "assistant") {
                val item = ChatItem.AssistantMessage(id = nextId(items(state)), streaming = true)
                state.copy(items = state.items + item, status = AgentStatus.Streaming)
            } else {
                state
            }
        }

        is PiEvent.MessageUpdate -> applyDelta(state, event)

        is PiEvent.MessageEnd -> applyMessageEnd(state, event.message)

        is PiEvent.ToolExecutionStart -> upsertTool(state, event.toolCallId, event.toolName, event.args)

        is PiEvent.ToolExecutionUpdate -> updateToolOutput(state, event.toolCallId) { prev ->
            val partial = extractText(event.partialResult)
            if (partial != null) partial else prev
        }

        is PiEvent.ToolExecutionEnd -> finishTool(state, event)

        is PiEvent.TurnEnd -> state.copy(status = AgentStatus.WaitingModel)

        is PiEvent.AgentEnd -> state.copy(
            status = if (event.willRetry) AgentStatus.Retrying else AgentStatus.Idle,
        )

        is PiEvent.AgentSettled -> finalizeStreaming(state)

        is PiEvent.QueueUpdate -> state.copy(
            steeringQueue = event.steering,
            followUpQueue = event.followUp,
        )

        is PiEvent.SessionInfoChanged -> state.copy(sessionName = event.name)

        is PiEvent.ThinkingLevelChanged -> state.copy(thinkingLevel = event.level)

        is PiEvent.CompactionStart -> appendNotice(state, "Compactando contexto…") {
            it.copy(status = AgentStatus.Compacting)
        }

        is PiEvent.CompactionEnd -> {
            val text = when {
                event.aborted -> "Compactação cancelada."
                event.errorMessage != null -> "Falha na compactação: ${event.errorMessage}"
                else -> "Contexto compactado."
            }
            appendNotice(state, text) {
                it.copy(status = if (event.willRetry) AgentStatus.Retrying else it.status)
            }
        }

        is PiEvent.AutoRetryStart -> appendNotice(
            state,
            "Nova tentativa ${event.attempt}/${event.maxAttempts}${event.errorMessage?.let { e -> " — $e" } ?: ""}",
        ) { it.copy(status = AgentStatus.Retrying) }

        is PiEvent.AutoRetryEnd -> if (!event.success && event.finalError != null) {
            appendNotice(state, "Falha após novas tentativas: ${event.finalError}", NoticeLevel.Error) {
                it.copy(status = AgentStatus.Idle)
            }
        } else {
            state.copy(status = if (event.success) AgentStatus.WaitingModel else state.status)
        }

        is PiEvent.BashExecutionUpdate -> state

        is PiEvent.ExtensionError -> appendNotice(
            state,
            "Erro em extensão${event.event?.let { " ($it)" } ?: ""}: ${event.error}",
            NoticeLevel.Error,
        )

        is PiEvent.EntryAppended, is PiEvent.Unknown -> state
    }

    // -----------------------------------------------------------------------

    private fun applyDelta(state: ChatState, event: PiEvent.MessageUpdate): ChatState {
        val delta = event.delta ?: return state.copy(lastUsage = event.usage ?: state.lastUsage)
        var next = state.copy(lastUsage = event.usage ?: state.lastUsage)
        var idx = next.items.indexOfLast { it is ChatItem.AssistantMessage }
        if (idx < 0) {
            next = next.copy(items = next.items + ChatItem.AssistantMessage(id = nextId(next.items), streaming = true))
            idx = next.items.lastIndex
        }
        val current = next.items[idx] as ChatItem.AssistantMessage
        val updated = when (delta) {
            is AssistantStreamEvent.TextDelta -> current.copy(text = current.text + delta.delta)
            is AssistantStreamEvent.TextEnd -> current.copy(text = delta.content)
            is AssistantStreamEvent.ThinkingDelta -> current.copy(thinking = current.thinking + delta.delta)
            is AssistantStreamEvent.ThinkingEnd -> current.copy(thinking = delta.content)
            is AssistantStreamEvent.ToolcallEnd -> {
                // Tool calls surface via tool_execution_* events; keep the
                // assistant message text-only, but stop showing the caret.
                current.copy(streaming = true)
            }
            is AssistantStreamEvent.Done -> current.copy(streaming = false, stopReason = delta.reason)
            is AssistantStreamEvent.StreamError -> current.copy(
                streaming = false,
                error = delta.error ?: delta.reason,
                stopReason = "error",
            )
            else -> current
        }
        val items = next.items.toMutableList().also { it[idx] = updated }
        return next.copy(items = items, status = AgentStatus.Streaming)
    }

    private fun applyMessageEnd(state: ChatState, message: PiMessage?): ChatState {
        if (message == null || message.role != "assistant") return state
        val text = message.blocks.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
        val thinking = message.blocks.filterIsInstance<ContentBlock.Thinking>().joinToString("") { it.thinking }
        val idx = state.items.indexOfLast { it is ChatItem.AssistantMessage }
        val authoritative = ChatItem.AssistantMessage(
            id = if (idx >= 0) state.items[idx].id else nextId(state.items),
            text = text,
            thinking = thinking,
            streaming = false,
            stopReason = message.stopReason,
            error = message.errorMessage,
        )
        val items = state.items.toMutableList()
        if (idx >= 0) {
            items[idx] = authoritative
        } else if (authoritative.hasText || authoritative.thinking.isNotBlank()) {
            items += authoritative
        }
        // Drop empty assistant placeholders (e.g. pure tool-call turns).
        return state.copy(
            items = items.filterNot {
                it is ChatItem.AssistantMessage && !it.streaming && !it.hasText && it.thinking.isBlank() && it.error == null
            },
        )
    }

    private fun finalizeStreaming(state: ChatState): ChatState {
        val items = state.items.map { item ->
            if (item is ChatItem.AssistantMessage && item.streaming) item.copy(streaming = false) else item
        }
        val tools = items.map { item ->
            if (item is ChatItem.ToolGroup) {
                item.copy(tools = item.tools.map { t ->
                    if (t.status == ToolStatus.Running) t.copy(status = ToolStatus.Cancelled) else t
                })
            } else item
        }
        return state.copy(items = tools, status = AgentStatus.Idle)
    }

    private fun upsertTool(state: ChatState, toolCallId: String, toolName: String, args: JsonObject?): ChatState {
        val summary = summarizeArgs(toolName, args)
        val diff = buildDiff(toolName, args)
        val existing = findTool(state, toolCallId)
        return if (existing != null) {
            replaceTool(state, toolCallId) { it.copy(status = ToolStatus.Running) }
        } else {
            val item = ToolItem(
                id = toolCallId.ifBlank { nextId(state.items) },
                toolCallId = toolCallId,
                name = toolName,
                summary = summary,
                status = ToolStatus.Running,
                diff = diff,
            )
            val items = state.items.toMutableList()
            val lastGroupIdx = items.indexOfLast { it is ChatItem.ToolGroup }
            if (lastGroupIdx >= 0) {
                val group = items[lastGroupIdx] as ChatItem.ToolGroup
                items[lastGroupIdx] = group.copy(tools = group.tools + item)
            } else {
                items += ChatItem.ToolGroup(id = "tg-$toolCallId", tools = listOf(item))
            }
            state.copy(items = items, status = AgentStatus.RunningTools)
        }
    }

    private fun updateToolOutput(state: ChatState, toolCallId: String, transform: (String) -> String): ChatState =
        replaceTool(state, toolCallId) { it.copy(output = transform(it.output)) }

    private fun finishTool(state: ChatState, event: PiEvent.ToolExecutionEnd): ChatState {
        val output = extractText(event.result) ?: ""
        val status = if (event.isError) ToolStatus.Error else ToolStatus.Ok
        val withTool = replaceTool(state, event.toolCallId) {
            it.copy(status = status, output = output.ifBlank { it.output }, durationMs = event.durationMs)
        }
        return withTool.copy(status = AgentStatus.WaitingModel)
    }

    private fun replaceTool(state: ChatState, toolCallId: String, transform: (ToolItem) -> ToolItem): ChatState {
        if (toolCallId.isBlank()) return state
        val items = state.items.map { item ->
            if (item is ChatItem.ToolGroup && item.tools.any { it.toolCallId == toolCallId }) {
                item.copy(tools = item.tools.map { if (it.toolCallId == toolCallId) transform(it) else it })
            } else {
                item
            }
        }
        return state.copy(items = items)
    }

    private fun findTool(state: ChatState, toolCallId: String): ToolItem? =
        state.items.filterIsInstance<ChatItem.ToolGroup>()
            .flatMap { it.tools }
            .firstOrNull { it.toolCallId == toolCallId }

    private fun appendNotice(
        state: ChatState,
        text: String,
        level: NoticeLevel = NoticeLevel.Info,
        transform: (ChatState) -> ChatState = { it },
    ): ChatState {
        val next = transform(state)
        return next.copy(items = next.items + ChatItem.Notice(id = nextId(next.items), text = text, level = level))
    }

    private fun items(state: ChatState) = state.items
}

private fun nextId(items: List<ChatItem>): String = "i-${items.size}-${System.nanoTime() % 100000}"

private fun summarizeArgs(toolName: String, args: JsonObject?): String {
    if (args == null) return ""
    fun pick(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k ->
            (args[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }
    return when (toolName.lowercase()) {
        "bash" -> pick("command", "cmd").orEmpty()
        "read", "edit", "write" -> pick("path", "filePath", "file_path", "filename").orEmpty()
        "glob", "grep" -> pick("pattern", "query", "glob").orEmpty()
        else -> pick("path", "command", "pattern", "query", "name", "url").orEmpty()
    }
}

private fun buildDiff(toolName: String, args: JsonObject?): DiffSummary? =
    buildDiffSummary(toolName, args)

/** Tool results are `{content: [{type:"text", text:"..."}], details: {...}}`. */
private fun extractText(result: JsonObject?): String? {
    if (result == null) return null
    val content = result["content"] ?: return null
    val array = content as? kotlinx.serialization.json.JsonArray ?: return null
    val text = array.mapNotNull { block ->
        (block as? JsonObject)?.let { b ->
            (b["text"] as? JsonPrimitive)?.content?.takeIf { b["type"]?.jsonPrimitive?.content == "text" }
        }
    }.joinToString("\n")
    return text.ifBlank { null }
}
