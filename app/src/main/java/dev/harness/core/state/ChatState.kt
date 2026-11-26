package dev.harness.core.state

import dev.harness.core.rpc.ContentBlock
import dev.harness.core.rpc.PiMessage
import dev.harness.core.rpc.ToolCall
import androidx.compose.runtime.Immutable
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
@Immutable
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
        /** Wall-clock time the assistant turn started (epoch ms). 0 = unknown
         *  (hydrated history) — the UI hides time footers for unknown stamps. */
        val tsMs: Long = 0L,
        /** Token usage of the finished turn, as reported by the Pi events. */
        val usage: Usage? = null,
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
@Immutable
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
    /** Absolute guest path of the active session file (get_state). */
    val sessionFile: String? = null,
    /** Active session id (get_state). */
    val sessionId: String? = null,
    /** Message count reported by the Pi for the active session. */
    val messageCount: Int? = null,
) {
    val isBusy: Boolean
        get() = status != AgentStatus.Idle

    /** Tool groups are collapsed by default; the UI toggles expansion locally. */
    fun totalOpenTools(): Int = items.filterIsInstance<ChatItem.ToolGroup>().sumOf { it.tools.count { t -> t.status == ToolStatus.Running } }
}

internal object ToolLabels {
    /**
     * Language-neutral canonical names (localized at the UI layer via
     * tool_label_* resources; see ToolCallCard).
     */
    fun pretty(name: String): String = when (name.lowercase()) {
        "bash" -> "Shell"
        "read" -> "Read"
        "write" -> "Write"
        "edit" -> "Edit"
        "glob" -> "Search"
        "grep" -> "Search"
        "ls" -> "List"
        "webfetch" -> "Web"
        "fetch" -> "Web"
        "todo" -> "Tasks"
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

/**
 * Cheap diff descriptor for `edit` tool calls, shared by the live reducer and
 * the session hydrator so a restored card looks exactly like the live one.
 */
fun buildDiffSummary(toolName: String, args: kotlinx.serialization.json.JsonObject?): DiffSummary? {
    if (args == null || toolName.lowercase() != "edit") return null
    val path = (args["path"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        ?: (args["filePath"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        ?: return null
    val old = (args["oldString"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    val new = (args["newString"] as? kotlinx.serialization.json.JsonPrimitive)?.content
    if (old == null && new == null) {
        return DiffSummary(path = path, added = 0, removed = 0, previewLines = emptyList())
    }
    val oldLines = old?.lines() ?: emptyList()
    val newLines = new?.lines() ?: emptyList()
    val preview = mutableListOf<DiffLine>()
    preview += DiffLine(DiffLine.Kind.Header, "− antigo")
    oldLines.take(12).forEach { preview += DiffLine(DiffLine.Kind.Removed, it) }
    preview += DiffLine(DiffLine.Kind.Header, "+ novo")
    newLines.take(12).forEach { preview += DiffLine(DiffLine.Kind.Added, it) }
    return DiffSummary(path = path, added = newLines.size, removed = oldLines.size, previewLines = preview)
}

private fun jsonStr(el: kotlinx.serialization.json.JsonElement): String {
    val prim = el as? kotlinx.serialization.json.JsonPrimitive
    return prim?.content ?: el.toString()
}
