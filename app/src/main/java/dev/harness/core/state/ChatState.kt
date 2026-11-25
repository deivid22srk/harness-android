package dev.harness.core.state

import dev.harness.core.rpc.ContentBlock
import dev.harness.core.rpc.PiMessage
import dev.harness.core.rpc.ToolCall
import dev.harness.core.rpc.Usage

/** Model metadata as reported by `get_state` / `get_available_models`. */
data class ModelInfo(
    val provider: String,
    val id: String,
    val name: String,
    val reasoning: Boolean,
)

enum class ToolStatus { Running, Ok, Error, Cancelled }

/** One line in the chat transcript. */
sealed interface ChatItem {
    val id: String

    data class UserMessage(
        override val id: String,
        val text: String,
        val imageCount: Int = 0,
    ) : ChatItem

    data class AssistantMessage(
        override val id: String,
        val text: String = "",
        val thinking: String = "",
        val streaming: Boolean = false,
        val stopReason: String? = null,
        val error: String? = null,
    ) : ChatItem {
        val hasText: Boolean get() = text.isNotBlank()
        val isThinkingOnly: Boolean get() = text.isBlank() && thinking.isNotBlank()
    }

    data class ToolGroup(
        override val id: String,
        val tools: List<ToolItem>,
    ) : ChatItem

    data class Notice(
        override val id: String,
        val text: String,
        val level: NoticeLevel = NoticeLevel.Info,
    ) : ChatItem
}

/** Severity of a [ChatItem.Notice]. */
enum class NoticeLevel { Info, Warning, Error }

/** A single tool invocation, rendered as a compact expandable row. */
data class ToolItem(
    val id: String,
    val toolCallId: String,
    val name: String,
    val summary: String,
    val status: ToolStatus = ToolStatus.Running,
    val output: String = "",
    val diff: DiffSummary? = null,
    val durationMs: Long? = null,
) {
    val label: String get() = ToolLabels.pretty(name)
}

/** Cheap, allocation-light diff descriptor (added/removed line counts + preview). */
data class DiffSummary(
    val path: String,
    val added: Int,
    val removed: Int,
    val previewLines: List<DiffLine>,
)

data class DiffLine(val kind: Kind, val text: String) {
    enum class Kind { Context, Added, Removed, Header }
}

enum class AgentStatus { Idle, WaitingModel, Streaming, RunningTools, Compacting, Retrying }

/** The complete, immutable UI state derived purely from Pi events. */
data class ChatState(
    val items: List<ChatItem> = emptyList(),
    val status: AgentStatus = AgentStatus.Idle,
    val model: ModelInfo? = null,
    val thinkingLevel: String = "off",
    val availableThinkingLevels: List<String> = emptyList(),
    val sessionName: String? = null,
    val steeringQueue: List<String> = emptyList(),
    val followUpQueue: List<String> = emptyList(),
    val lastUsage: Usage? = null,
    val contextPercent: Int? = null,
    val error: String? = null,
) {
    val isBusy: Boolean
        get() = status != AgentStatus.Idle

    /** Tool groups are collapsed by default; the UI toggles expansion locally. */
    fun totalOpenTools(): Int = items.filterIsInstance<ChatItem.ToolGroup>().sumOf { it.tools.count { t -> t.status == ToolStatus.Running } }
}

internal object ToolLabels {
    fun pretty(name: String): String = when (name.lowercase()) {
        "bash" -> "Shell"
        "read" -> "Leitura"
        "write" -> "Escrita"
        "edit" -> "Edição"
        "glob" -> "Busca"
        "grep" -> "Busca"
        "ls" -> "Listar"
        "webfetch" -> "Web"
        "fetch" -> "Web"
        "todo" -> "Tarefas"
        else -> name.replaceFirstChar { it.uppercase() }
    }
}

/** Maps a Pi assistant message to a UI item (used when hydrating from get_messages). */
fun PiMessage.toAssistantItem(id: String): ChatItem.AssistantMessage {
    val text = blocks.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
    val thinking = blocks.filterIsInstance<ContentBlock.Thinking>().joinToString("") { it.thinking }
    return ChatItem.AssistantMessage(id = id, text = text, thinking = thinking, streaming = false)
}

fun PiMessage.toUserItem(id: String): ChatItem.UserMessage {
    val text = blocks.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
    val images = blocks.count { it is ContentBlock.Image }
    return ChatItem.UserMessage(id = id, text = text, imageCount = images)
}

/** Best-effort summary line for a tool call, matching the OpenCode reference. */
fun summarizeToolCall(call: ToolCall): String {
    val args = call.arguments ?: return ""
    fun pick(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> args[k]?.let { jsonStr(it) } }

    return when (call.name.lowercase()) {
        "bash" -> pick("command", "cmd").orEmpty()
        "read", "write" -> pick("path", "filePath", "file_path", "filename").orEmpty()
        "edit" -> pick("path", "filePath", "file_path").orEmpty()
        "glob", "grep" -> pick("pattern", "query", "glob").orEmpty()
        else -> pick("path", "command", "pattern", "query", "name", "url").orEmpty()
    }
}

private fun jsonStr(el: kotlinx.serialization.json.JsonElement): String {
    val prim = el as? kotlinx.serialization.json.JsonPrimitive
    return prim?.content ?: el.toString()
}
