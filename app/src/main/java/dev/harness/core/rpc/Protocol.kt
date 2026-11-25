package dev.harness.core.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Typed model of the Pi RPC protocol records.
 *
 * Sources (official docs, see docs/pi/):
 *  - rpc.md / rpc-commands.md : stdin commands and responses
 *  - json.md                  : stdout session events
 *  - message-types.md         : shared message/content types
 *  - rpc-extension-ui.md      : extension UI sub-protocol
 *
 * Only records this version of Pi actually emits are modelled; unknown event
 * types are preserved as [PiEvent.Unknown] instead of being dropped.
 */
object PiJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    fun parse(line: String): PiRecord? {
        val element = try {
            json.parseToJsonElement(line)
        } catch (t: Throwable) {
            return PiMalformedRecord(line, t.message ?: "invalid JSON")
        }
        val obj = element as? JsonObject ?: return PiMalformedRecord(line, "not a JSON object")
        return when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "response" -> parseResponse(obj)
            "extension_ui_request" -> PiExtensionUiRecord(ExtensionUiRequest.from(obj))
            "extension_error" -> PiEventRecord(PiEvent.ExtensionError.from(obj))
            else -> PiEventRecord(parseEvent(obj))
        }
    }

    private fun parseResponse(obj: JsonObject): PiRecord = PiResponseRecord(
        id = obj["id"]?.str(),
        command = obj["command"]?.str() ?: "unknown",
        success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false,
        data = obj["data"] as? JsonObject,
        error = obj["error"]?.str(),
    )

    private fun parseEvent(obj: JsonObject): PiEvent {
        val type = obj["type"]?.str() ?: return PiEvent.Unknown("", obj)
        return when (type) {
            "agent_start" -> PiEvent.AgentStart
            "agent_end" -> PiEvent.AgentEnd(
                willRetry = obj["willRetry"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
            "agent_settled" -> PiEvent.AgentSettled(
                aborted = obj["aborted"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
            "turn_start" -> PiEvent.TurnStart
            "turn_end" -> PiEvent.TurnEnd(message = obj["message"]?.let(PiMessage::from))
            "message_start" -> PiEvent.MessageStart(obj["message"]?.let(PiMessage::from))
            "message_end" -> PiEvent.MessageEnd(obj["message"]?.let(PiMessage::from))
            "message_update" -> PiEvent.MessageUpdate(
                usage = (obj["usage"] as? JsonObject)?.let(Usage::from),
                delta = obj["assistantMessageEvent"]?.let(AssistantStreamEvent::from),
            )
            "tool_execution_start" -> PiEvent.ToolExecutionStart(
                toolCallId = obj["toolCallId"]?.str().orEmpty(),
                toolName = obj["toolName"]?.str().orEmpty(),
                args = obj["args"] as? JsonObject,
            )
            "tool_execution_update" -> PiEvent.ToolExecutionUpdate(
                toolCallId = obj["toolCallId"]?.str().orEmpty(),
                toolName = obj["toolName"]?.str().orEmpty(),
                partialResult = obj["partialResult"] as? JsonObject,
            )
            "tool_execution_end" -> PiEvent.ToolExecutionEnd(
                toolCallId = obj["toolCallId"]?.str().orEmpty(),
                toolName = obj["toolName"]?.str().orEmpty(),
                result = obj["result"] as? JsonObject,
                isError = obj["isError"]?.jsonPrimitive?.booleanOrNull ?: false,
                durationMs = obj["durationMs"]?.jsonPrimitive?.longOrNull,
            )
            "queue_update" -> PiEvent.QueueUpdate(
                steering = obj["steering"]?.stringList() ?: emptyList(),
                followUp = obj["followUp"]?.stringList() ?: emptyList(),
            )
            "session_info_changed" -> PiEvent.SessionInfoChanged(obj["name"]?.str())
            "thinking_level_changed" -> PiEvent.ThinkingLevelChanged(obj["level"]?.str().orEmpty())
            "compaction_start" -> PiEvent.CompactionStart(obj["reason"]?.str().orEmpty())
            "compaction_end" -> PiEvent.CompactionEnd(
                reason = obj["reason"]?.str().orEmpty(),
                aborted = obj["aborted"]?.jsonPrimitive?.booleanOrNull ?: false,
                willRetry = obj["willRetry"]?.jsonPrimitive?.booleanOrNull ?: false,
                errorMessage = obj["errorMessage"]?.str(),
            )
            "auto_retry_start" -> PiEvent.AutoRetryStart(
                attempt = obj["attempt"]?.jsonPrimitive?.intOrNull ?: 0,
                maxAttempts = obj["maxAttempts"]?.jsonPrimitive?.intOrNull ?: 0,
                errorMessage = obj["errorMessage"]?.str(),
            )
            "auto_retry_end" -> PiEvent.AutoRetryEnd(
                success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false,
                attempt = obj["attempt"]?.jsonPrimitive?.intOrNull ?: 0,
                finalError = obj["finalError"]?.str(),
            )
            "bash_execution_update" -> PiEvent.BashExecutionUpdate(
                id = obj["id"]?.str(),
                delta = obj["delta"]?.str().orEmpty(),
            )
            "entry_appended" -> PiEvent.EntryAppended(obj["entry"] as? JsonObject)
            else -> PiEvent.Unknown(type, obj)
        }
    }
}

sealed interface PiRecord

data class PiResponseRecord(
    val id: String?,
    val command: String,
    val success: Boolean,
    val data: JsonObject?,
    val error: String?,
) : PiRecord {
    fun dataString(key: String): String? = data?.get(key)?.str()
}

data class PiEventRecord(val event: PiEvent) : PiRecord

data class PiExtensionUiRecord(val request: ExtensionUiRequest) : PiRecord

data class PiMalformedRecord(val raw: String, val message: String) : PiRecord

// ---------------------------------------------------------------------------
// Session events (json.md)
// ---------------------------------------------------------------------------

sealed interface PiEvent {
    data object AgentStart : PiEvent
    data class AgentEnd(val willRetry: Boolean) : PiEvent
    data class AgentSettled(val aborted: Boolean) : PiEvent
    data object TurnStart : PiEvent
    data class TurnEnd(val message: PiMessage?) : PiEvent

    data class MessageStart(val message: PiMessage?) : PiEvent
    data class MessageEnd(val message: PiMessage?) : PiEvent
    data class MessageUpdate(val usage: Usage?, val delta: AssistantStreamEvent?) : PiEvent

    data class ToolExecutionStart(val toolCallId: String, val toolName: String, val args: JsonObject?) : PiEvent
    data class ToolExecutionUpdate(val toolCallId: String, val toolName: String, val partialResult: JsonObject?) : PiEvent
    data class ToolExecutionEnd(
        val toolCallId: String,
        val toolName: String,
        val result: JsonObject?,
        val isError: Boolean,
        val durationMs: Long?,
    ) : PiEvent

    data class QueueUpdate(val steering: List<String>, val followUp: List<String>) : PiEvent
    data class SessionInfoChanged(val name: String?) : PiEvent
    data class ThinkingLevelChanged(val level: String) : PiEvent
    data class CompactionStart(val reason: String) : PiEvent
    data class CompactionEnd(
        val reason: String,
        val aborted: Boolean,
        val willRetry: Boolean,
        val errorMessage: String?,
    ) : PiEvent

    data class AutoRetryStart(val attempt: Int, val maxAttempts: Int, val errorMessage: String?) : PiEvent
    data class AutoRetryEnd(val success: Boolean, val attempt: Int, val finalError: String?) : PiEvent
    data class BashExecutionUpdate(val id: String?, val delta: String) : PiEvent
    data class EntryAppended(val entry: JsonObject?) : PiEvent
    data class ExtensionError(val extensionPath: String?, val event: String?, val error: String?) : PiEvent {
        companion object {
            fun from(obj: JsonObject) = ExtensionError(
                extensionPath = obj["extensionPath"]?.str(),
                event = obj["event"]?.str(),
                error = obj["error"]?.str(),
            )
        }
    }

    data class Unknown(val type: String, val raw: JsonObject) : PiEvent
}

sealed interface AssistantStreamEvent {
    data object StreamStart : AssistantStreamEvent
    data class TextStart(val contentIndex: Int) : AssistantStreamEvent
    data class TextDelta(val contentIndex: Int, val delta: String) : AssistantStreamEvent
    data class TextEnd(val contentIndex: Int, val content: String) : AssistantStreamEvent
    data class ThinkingStart(val contentIndex: Int) : AssistantStreamEvent
    data class ThinkingDelta(val contentIndex: Int, val delta: String) : AssistantStreamEvent
    data class ThinkingEnd(val contentIndex: Int, val content: String) : AssistantStreamEvent
    data class ToolcallStart(val contentIndex: Int, val id: String, val toolName: String) : AssistantStreamEvent
    data class ToolcallDelta(val contentIndex: Int, val delta: String) : AssistantStreamEvent
    data class ToolcallEnd(val contentIndex: Int, val toolCall: ToolCall) : AssistantStreamEvent
    data class Done(val reason: String?) : AssistantStreamEvent
    data class StreamError(val reason: String?, val error: String?) : AssistantStreamEvent
    data class Unknown(val type: String, val raw: JsonObject) : AssistantStreamEvent

    companion object {
        fun from(el: JsonElement): AssistantStreamEvent {
            val obj = el as? JsonObject ?: return Unknown("", JsonObject(emptyMap()))
            val type = obj["type"]?.str() ?: return Unknown("", obj)
            return when (type) {
                "start" -> StreamStart
                "text_start" -> TextStart(obj["contentIndex"]?.jsonPrimitive?.intOrNull ?: 0)
                "text_delta" -> TextDelta(
                    obj["contentIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    obj["delta"]?.str().orEmpty(),
                )
                "text_end" -> TextEnd(
                    obj["contentIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    obj["content"]?.str().orEmpty(),
                )
                "thinking_start" -> ThinkingStart(obj["contentIndex"]?.jsonPrimitive?.intOrNull ?: 0)
                "thinking_delta" -> ThinkingDelta(
                    obj["contentIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    obj["delta"]?.str().orEmpty(),
                )
                "thinking_end" -> ThinkingEnd(
                    obj["contentIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    obj["content"]?.str().orEmpty(),
                )
                "toolcall_start" -> ToolcallStart(
                    obj["contentIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    obj["id"]?.str().orEmpty(),
                    obj["toolName"]?.str().orEmpty(),
                )
                "toolcall_delta" -> ToolcallDelta(
                    obj["contentIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    obj["delta"]?.str().orEmpty(),
                )
                "toolcall_end" -> ToolcallEnd(
                    obj["contentIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    (obj["toolCall"] as? JsonObject)?.let(ToolCall::from) ?: ToolCall("", "", null),
                )
                "done" -> Done(obj["reason"]?.str())
                "error" -> StreamError(obj["reason"]?.str(), obj["error"]?.str())
                else -> Unknown(type, obj)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Messages (message-types.md)
// ---------------------------------------------------------------------------

data class Usage(
    val input: Long,
    val output: Long,
    val cacheRead: Long,
    val cacheWrite: Long,
    val totalTokens: Long,
    val costTotal: Double,
) {
    companion object {
        fun from(obj: JsonObject): Usage {
            val cost = obj["cost"] as? JsonObject
            return Usage(
                input = obj["input"]?.jsonPrimitive?.longOrNull ?: 0,
                output = obj["output"]?.jsonPrimitive?.longOrNull ?: 0,
                cacheRead = obj["cacheRead"]?.jsonPrimitive?.longOrNull ?: 0,
                cacheWrite = obj["cacheWrite"]?.jsonPrimitive?.longOrNull ?: 0,
                totalTokens = obj["totalTokens"]?.jsonPrimitive?.longOrNull ?: 0,
                costTotal = cost?.get("total")?.str()?.toDoubleOrNull() ?: 0.0,
            )
        }
    }
}

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JsonObject?,
) {
    companion object {
        fun from(obj: JsonObject) = ToolCall(
            id = obj["id"]?.str().orEmpty(),
            name = obj["name"]?.str().orEmpty(),
            arguments = obj["arguments"] as? JsonObject,
        )
    }
}

sealed interface ContentBlock {
    data class Text(val text: String) : ContentBlock
    data class Thinking(val thinking: String, val redacted: Boolean) : ContentBlock
    data class ToolCallBlock(val call: ToolCall) : ContentBlock
    data class Image(val mimeType: String) : ContentBlock
    data class Unknown(val type: String) : ContentBlock
}

data class PiMessage(
    val role: String,
    val blocks: List<ContentBlock>,
    val plainText: String,
    val stopReason: String?,
    val errorMessage: String?,
    val toolCallId: String?,
    val toolName: String?,
    val isError: Boolean,
) {
    val toolCalls: List<ToolCall> get() = blocks.filterIsInstance<ContentBlock.ToolCallBlock>().map { it.call }

    companion object {
        fun from(el: JsonElement): PiMessage? {
            val obj = el as? JsonObject ?: return null
            val role = obj["role"]?.str() ?: return null
            val blocks = when (val content = obj["content"]) {
                is JsonArray -> content.map(::parseBlock)
                is JsonPrimitive -> listOf(ContentBlock.Text(content.contentOrNull.orEmpty()))
                else -> emptyList()
            }
            val plain = blocks.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
            return PiMessage(
                role = role,
                blocks = blocks,
                plainText = plain,
                stopReason = obj["stopReason"]?.str(),
                errorMessage = obj["errorMessage"]?.str(),
                toolCallId = obj["toolCallId"]?.str(),
                toolName = obj["toolName"]?.str(),
                isError = obj["isError"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }

        private fun parseBlock(el: JsonElement): ContentBlock {
            val obj = el as? JsonObject ?: return ContentBlock.Unknown("null")
            return when (obj["type"]?.str()) {
                "text" -> ContentBlock.Text(obj["text"]?.str().orEmpty())
                "thinking" -> ContentBlock.Thinking(
                    thinking = obj["thinking"]?.str().orEmpty(),
                    redacted = obj["redacted"]?.jsonPrimitive?.booleanOrNull ?: false,
                )
                "toolCall" -> ContentBlock.ToolCallBlock(ToolCall.from(obj))
                "image" -> ContentBlock.Image(obj["mimeType"]?.str().orEmpty())
                else -> ContentBlock.Unknown(obj["type"]?.str().orEmpty())
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Extension UI (rpc-extension-ui.md)
// ---------------------------------------------------------------------------

data class ExtensionUiRequest(
    val id: String,
    val method: String,
    val title: String?,
    val message: String?,
    val options: List<String>,
    val placeholder: String?,
    val prefill: String?,
    val notifyType: String?,
    val timeoutMs: Long?,
) {
    val isDialog: Boolean get() = method in setOf("select", "confirm", "input", "editor")

    companion object {
        fun from(obj: JsonObject) = ExtensionUiRequest(
            id = obj["id"]?.str().orEmpty(),
            method = obj["method"]?.str().orEmpty(),
            title = obj["title"]?.str(),
            message = obj["message"]?.str(),
            options = (obj["options"] as? JsonArray)?.mapNotNull { it.str() } ?: emptyList(),
            placeholder = obj["placeholder"]?.str(),
            prefill = obj["prefill"]?.str(),
            notifyType = obj["notifyType"]?.str(),
            timeoutMs = obj["timeout"]?.jsonPrimitive?.longOrNull,
        )
    }
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

internal fun JsonElement.str(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

internal fun JsonElement.stringList(): List<String> =
    (this as? JsonArray)?.mapNotNull { it.str() } ?: emptyList()
