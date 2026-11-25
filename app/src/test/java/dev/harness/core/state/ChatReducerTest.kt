package dev.harness.core.state

import dev.harness.core.rpc.AssistantStreamEvent
import dev.harness.core.rpc.ContentBlock
import dev.harness.core.rpc.PiEvent
import dev.harness.core.rpc.PiMessage
import dev.harness.core.rpc.ToolCall
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {

    private val reducer = ChatReducer()

    private fun assistant(text: String) = PiMessage(
        role = "assistant",
        blocks = listOf(ContentBlock.Text(text)),
        plainText = text,
        stopReason = "stop",
        errorMessage = null,
        toolCallId = null,
        toolName = null,
        isError = false,
    )

    @Test
    fun `streams text deltas then replaces with authoritative content`() {
        var state = ChatState()
        state = reducer.reduce(state, PiEvent.AgentStart)
        state = reducer.reduce(state, PiEvent.MessageStart(null))
        state = reducer.reduce(state, PiEvent.MessageUpdate(null, AssistantStreamEvent.TextStart(0)))
        state = reducer.reduce(state, PiEvent.MessageUpdate(null, AssistantStreamEvent.TextDelta(0, "Ol")))
        state = reducer.reduce(state, PiEvent.MessageUpdate(null, AssistantStreamEvent.TextDelta(0, "á")))

        val partial = state.items.last() as ChatItem.AssistantMessage
        assertEquals("Olá", partial.text)
        assertTrue(partial.streaming)
        assertEquals(AgentStatus.Streaming, state.status)

        state = reducer.reduce(state, PiEvent.MessageUpdate(null, AssistantStreamEvent.TextEnd(0, "Olá, mundo")))
        assertEquals("Olá, mundo", (state.items.last() as ChatItem.AssistantMessage).text)

        state = reducer.reduce(state, PiEvent.MessageEnd(assistant("Olá, mundo!")))
        val final = state.items.last() as ChatItem.AssistantMessage
        assertEquals("Olá, mundo!", final.text)
        assertFalse(final.streaming)

        state = reducer.reduce(state, PiEvent.AgentSettled(aborted = false))
        assertEquals(AgentStatus.Idle, state.status)
    }

    @Test
    fun `groups tool executions and tracks state transitions`() {
        var state = ChatState()
        val args = buildJsonObject { put("command", "ls -la") }
        state = reducer.reduce(state, PiEvent.ToolExecutionStart("t1", "bash", args))
        state = reducer.reduce(state, PiEvent.ToolExecutionStart("t2", "read", buildJsonObject { put("path", "a.kt") }))

        val group = state.items.single() as ChatItem.ToolGroup
        assertEquals(2, group.tools.size)
        assertEquals("Shell", group.tools[0].label)
        assertEquals("ls -la", group.tools[0].summary)
        assertEquals(ToolStatus.Running, group.tools[0].status)

        state = reducer.reduce(
            state,
            PiEvent.ToolExecutionEnd(
                "t1", "bash",
                result = buildJsonObject {
                    put(
                        "content",
                        kotlinx.serialization.json.buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", "saída") })
                        },
                    )
                },
                isError = false,
                durationMs = 12,
            ),
        )
        val updated = (state.items.single() as ChatItem.ToolGroup).tools.first { it.toolCallId == "t1" }
        assertEquals(ToolStatus.Ok, updated.status)
        assertEquals("saída", updated.output)
        assertEquals(12L, updated.durationMs)
    }

    @Test
    fun `edit tool produces a diff summary`() {
        var state = ChatState()
        val args = buildJsonObject {
            put("path", "src/Main.kt")
            put("oldString", "val a = 1")
            put("newString", "val a = 2\nval b = 3")
        }
        state = reducer.reduce(state, PiEvent.ToolExecutionStart("e1", "edit", args))
        val tool = (state.items.single() as ChatItem.ToolGroup).tools.single()
        assertEquals("Edição", tool.label)
        val diff = tool.diff!!
        assertEquals("src/Main.kt", diff.path)
        assertEquals(2, diff.added)
        assertEquals(1, diff.removed)
    }

    @Test
    fun `settling marks dangling tools as cancelled`() {
        var state = ChatState()
        state = reducer.reduce(state, PiEvent.ToolExecutionStart("t1", "bash", buildJsonObject { put("command", "sleep 99") }))
        state = reducer.reduce(state, PiEvent.AgentSettled(aborted = true))
        val tool = (state.items.single() as ChatItem.ToolGroup).tools.single()
        assertEquals(ToolStatus.Cancelled, tool.status)
        assertEquals(AgentStatus.Idle, state.status)
    }

    @Test
    fun `queue updates and session name are tracked`() {
        var state = ChatState()
        state = reducer.reduce(state, PiEvent.QueueUpdate(listOf("stop"), listOf("depois")))
        assertEquals(listOf("stop"), state.steeringQueue)
        assertEquals(listOf("depois"), state.followUpQueue)

        state = reducer.reduce(state, PiEvent.SessionInfoChanged("gow-arm64"))
        assertEquals("gow-arm64", state.sessionName)

        state = reducer.reduce(state, PiEvent.ThinkingLevelChanged("high"))
        assertEquals("high", state.thinkingLevel)
    }

    @Test
    fun `empty assistant placeholder is dropped on message end`() {
        var state = ChatState()
        state = reducer.reduce(state, PiEvent.MessageStart(null))
        state = reducer.reduce(state, PiEvent.MessageEnd(assistant("")))
        assertTrue(state.items.isEmpty())
    }
}
