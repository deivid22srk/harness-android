package dev.harness.core.state

import dev.harness.core.rpc.ContentBlock
import dev.harness.core.rpc.PiMessage
import dev.harness.core.rpc.ToolCall

/**
 * Rebuilds the full chat transcript from the authoritative message list the
 * Pi returns (`get_messages`) — the restore path used when a session is
 * resumed (Frente A).
 *
 * Mirrors the live rendering of [ChatReducer]: user messages, assistant text
 * with thinking, tool-call cards grouped the way `tool_execution_*` events
 * would produce them, and bash-execution lines — so a resumed conversation
 * looks like the one the user left.
 *
 * Pure JVM code: unit-testable without Android.
 */
object SessionHydrator {

    /** Result of a `toolResult` message, keyed by toolCallId. */
    private data class ToolResult(val output: String, val isError: Boolean)

    fun toChatItems(messages: List<PiMessage>): List<ChatItem> {
        // toolResult messages follow their assistant message; index them first.
        val results = HashMap<String, ToolResult>()
        messages.forEach { msg ->
            if (msg.role == "toolResult" && msg.toolCallId != null) {
                results[msg.toolCallId!!] = ToolResult(msg.plainText, msg.isError)
            }
        }

        val items = ArrayList<ChatItem>(messages.size)
        messages.forEachIndexed { index, msg ->
            when (msg.role) {
                "user" -> {
                    val text = msg.plainText
                    if (text.isNotBlank()) {
                        items += ChatItem.UserMessage(
                            id = "h-u-$index",
                            text = text,
                            imageCount = msg.blocks.count { it is ContentBlock.Image },
                        )
                    }
                }

                "assistant" -> {
                    val text = msg.blocks.filterIsInstance<ContentBlock.Text>().joinToString("") { it.text }
                    val thinking = msg.blocks.filterIsInstance<ContentBlock.Thinking>().joinToString("") { it.thinking }
                    val hasText = text.isNotBlank() || thinking.isNotBlank() || msg.errorMessage != null
                    if (hasText) {
                        items += ChatItem.AssistantMessage(
                            id = "h-a-$index",
                            text = text,
                            thinking = thinking,
                            streaming = false,
                            stopReason = msg.stopReason,
                            error = msg.errorMessage,
                        )
                    }
                    val calls = msg.toolCalls
                    if (calls.isNotEmpty()) {
                        items += buildToolGroup(index, calls, results)
                    }
                }

                "bashExecution" -> {
                    val command = msg.command.orEmpty().ifBlank { msg.plainText }
                    if (command.isNotBlank()) {
                        val failed = (msg.exitCode ?: 0) != 0
                        val tail = msg.output.orEmpty().lines().takeLast(BASH_OUTPUT_TAIL).joinToString("\n")
                        items += ChatItem.Notice(
                            id = "h-b-$index",
                            text = if (failed) {
                                "Comando falhou (código ${msg.exitCode}): $command" +
                                    (if (tail.isNotBlank()) "\n$tail" else "")
                            } else {
                                "Executou: $command"
                            },
                            level = if (failed) NoticeLevel.Error else NoticeLevel.Info,
                        )
                    }
                }

                "custom" -> {
                    if (msg.plainText.isNotBlank()) {
                        items += ChatItem.Notice(id = "h-c-$index", text = msg.plainText, level = NoticeLevel.Info)
                    }
                }

                else -> Unit
                // toolResult (consumed above), system (prompt internals) and
                // unknown roles are not rendered.
            }
        }
        return items
    }

    private fun buildToolGroup(
        index: Int,
        calls: List<ToolCall>,
        results: Map<String, ToolResult>,
    ): ChatItem.ToolGroup {
        val tools = calls.mapIndexed { i, call ->
            val result = results[call.id]
            ToolItem(
                id = "h-t-$index-$i",
                toolCallId = call.id,
                name = call.name,
                summary = summarizeToolCall(call),
                // A hydrated session is finished: every call either has its
                // result (Ok/Error) or never completed (Cancelled — e.g. abort).
                status = when {
                    result == null -> ToolStatus.Cancelled
                    result.isError -> ToolStatus.Error
                    else -> ToolStatus.Ok
                },
                output = result?.output.orEmpty(),
                diff = buildDiffSummary(call.name, call.arguments),
            )
        }
        return ChatItem.ToolGroup(id = "h-g-$index", tools = tools)
    }

    private const val BASH_OUTPUT_TAIL = 8
}
