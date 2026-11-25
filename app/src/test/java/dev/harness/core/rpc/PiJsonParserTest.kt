package dev.harness.core.rpc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiJsonParserTest {

    @Test
    fun `parses a response record`() {
        val record = PiJson.parse(
            """{"id":"req-1","type":"response","command":"get_state","success":true,"data":{"thinkingLevel":"medium"}}""",
        )
        assertTrue(record is PiResponseRecord)
        val response = record as PiResponseRecord
        assertEquals("req-1", response.id)
        assertEquals("get_state", response.command)
        assertTrue(response.success)
        assertEquals("medium", response.dataString("thinkingLevel"))
    }

    @Test
    fun `parses text delta streaming event`() {
        val record = PiJson.parse(
            """{"type":"message_update","usage":{"input":10,"output":1,"totalTokens":11},"assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":"Olá"}}""",
        )
        val event = (record as PiEventRecord).event as PiEvent.MessageUpdate
        val delta = event.delta as AssistantStreamEvent.TextDelta
        assertEquals("Olá", delta.delta)
        assertEquals(0, delta.contentIndex)
        assertEquals(11L, event.usage?.totalTokens)
    }

    @Test
    fun `parses tool execution lifecycle`() {
        val start = PiJson.parse(
            """{"type":"tool_execution_start","toolCallId":"c1","toolName":"bash","args":{"command":"ls"}}""",
        )
        val startEvent = (start as PiEventRecord).event as PiEvent.ToolExecutionStart
        assertEquals("bash", startEvent.toolName)
        assertEquals("ls", (startEvent.args?.get("command") as JsonPrimitive).content)

        val end = PiJson.parse(
            """{"type":"tool_execution_end","toolCallId":"c1","toolName":"bash","result":{"content":[{"type":"text","text":"ok"}]},"isError":false,"durationMs":42}""",
        )
        val endEvent = (end as PiEventRecord).event as PiEvent.ToolExecutionEnd
        assertEquals(false, endEvent.isError)
        assertEquals(42L, endEvent.durationMs)
    }

    @Test
    fun `parses extension ui request`() {
        val record = PiJson.parse(
            """{"type":"extension_ui_request","id":"u1","method":"confirm","title":"Permitir?","message":"Rodar rm -rf?","timeout":10000}""",
        )
        val request = (record as PiExtensionUiRecord).request
        assertEquals("confirm", request.method)
        assertTrue(request.isDialog)
        assertEquals(10_000L, request.timeoutMs)
    }

    @Test
    fun `keeps unknown events instead of dropping them`() {
        val record = PiJson.parse("""{"type":"brand_new_event","payload":{"x":1}}""")
        val event = (record as PiEventRecord).event
        assertTrue(event is PiEvent.Unknown)
        assertEquals("brand_new_event", (event as PiEvent.Unknown).type)
    }

    @Test
    fun `reports malformed json without throwing`() {
        val record = PiJson.parse("{not json")
        assertTrue(record is PiMalformedRecord)
    }

    @Test
    fun `parses assistant message with mixed blocks`() {
        val record = PiJson.parse(
            """{"type":"message_end","message":{"role":"assistant","content":[{"type":"thinking","thinking":"hmm"},{"type":"text","text":"pronto"}],"stopReason":"stop","timestamp":1}}""",
        )
        val message = (record as PiEventRecord).event.let { (it as PiEvent.MessageEnd).message }
        assertEquals("assistant", message?.role)
        assertEquals("pronto", message?.plainText)
        assertEquals(1, message?.blocks?.count { it is ContentBlock.Thinking })
    }
}
