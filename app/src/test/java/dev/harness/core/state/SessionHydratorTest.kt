package dev.harness.core.state

import dev.harness.core.rpc.PiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies that a resumed session (get_messages payload) rebuilds the same
 * items the user saw live — including tool cards (PROMPT §4.1.3).
 */
class SessionHydratorTest {

    private fun parse(vararg lines: String): List<PiMessage> =
        lines.map { PiMessage.from(dev.harness.core.rpc.PiJson.json.parseToJsonElement(it))!! }

    @Test
    fun `user and assistant messages are restored`() {
        val items = SessionHydrator.toChatItems(
            parse(
                """{"role":"user","content":"olá","timestamp":1}""",
                """{"role":"assistant","content":[{"type":"text","text":"olá!"}],"stopReason":"stop","timestamp":2}""",
            ),
        )
        assertEquals(2, items.size)
        assertTrue(items[0] is ChatItem.UserMessage)
        assertEquals("olá", (items[0] as ChatItem.UserMessage).text)
        val assistant = items[1] as ChatItem.AssistantMessage
        assertEquals("olá!", assistant.text)
        assertEquals(false, assistant.streaming)
        assertEquals("stop", assistant.stopReason)
    }

    @Test
    fun `tool calls become grouped cards with results and error status`() {
        val items = SessionHydrator.toChatItems(
            parse(
                """{"role":"user","content":"leia o arquivo","timestamp":1}""",
                """{"role":"assistant","content":[{"type":"text","text":"lendo"},{"type":"toolCall","id":"c1","name":"read","arguments":{"path":"/a.py"}}],"stopReason":"toolUse","timestamp":2}""",
                """{"role":"toolResult","toolCallId":"c1","toolName":"read","content":[{"type":"text","text":"conteudo"}],"isError":false,"timestamp":3}""",
            ),
        )
        assertEquals(3, items.size) // user, assistant, tool group
        val group = items[2] as ChatItem.ToolGroup
        assertEquals(1, group.tools.size)
        val tool = group.tools[0]
        assertEquals("read", tool.name)
        assertEquals("/a.py", tool.summary)
        assertEquals(ToolStatus.Ok, tool.status)
        assertEquals("conteudo", tool.output)
    }

    @Test
    fun `failed tool result maps to Error status`() {
        val items = SessionHydrator.toChatItems(
            parse(
                """{"role":"assistant","content":[{"type":"toolCall","id":"c2","name":"bash","arguments":{"command":"ls"}}],"stopReason":"toolUse","timestamp":1}""",
                """{"role":"toolResult","toolCallId":"c2","toolName":"bash","content":[{"type":"text","text":"not found"}],"isError":true,"timestamp":2}""",
            ),
        )
        val group = items[0] as ChatItem.ToolGroup
        assertEquals(ToolStatus.Error, group.tools[0].status)
        assertEquals("ls", group.tools[0].summary)
    }

    @Test
    fun `tool call without result is Cancelled (aborted turn)`() {
        val items = SessionHydrator.toChatItems(
            parse(
                """{"role":"assistant","content":[{"type":"toolCall","id":"c3","name":"write","arguments":{"path":"/b.txt"}}],"stopReason":"aborted","timestamp":1}""",
            ),
        )
        val group = items[0] as ChatItem.ToolGroup
        assertEquals(ToolStatus.Cancelled, group.tools[0].status)
    }

    @Test
    fun `edit tool call carries the diff summary`() {
        val items = SessionHydrator.toChatItems(
            parse(
                """{"role":"assistant","content":[{"type":"toolCall","id":"c4","name":"edit","arguments":{"path":"/a.py","oldString":"x","newString":"y"}}],"stopReason":"toolUse","timestamp":1}""",
                """{"role":"toolResult","toolCallId":"c4","toolName":"edit","content":[{"type":"text","text":"ok"}],"isError":false,"timestamp":2}""",
            ),
        )
        val tool = (items[0] as ChatItem.ToolGroup).tools[0]
        assertEquals(1, tool.diff?.added)
        assertEquals(1, tool.diff?.removed)
        assertEquals("/a.py", tool.diff?.path)
    }

    @Test
    fun `bash execution becomes a notice and failures are errors`() {
        val items = SessionHydrator.toChatItems(
            parse(
                """{"role":"bashExecution","command":"echo oi","output":"oi","exitCode":0,"timestamp":1}""",
                """{"role":"bashExecution","command":"falso","output":"not found","exitCode":127,"timestamp":2}""",
            ),
        )
        assertTrue(items[0] is ChatItem.Notice)
        assertEquals(NoticeLevel.Info, (items[0] as ChatItem.Notice).level)
        assertEquals(NoticeLevel.Error, (items[1] as ChatItem.Notice).level)
        assertTrue((items[1] as ChatItem.Notice).text.contains("127"))
    }

    @Test
    fun `thinking content is restored on assistant messages`() {
        val items = SessionHydrator.toChatItems(
            parse(
                """{"role":"assistant","content":[{"type":"thinking","thinking":"vou analisar"},{"type":"text","text":"análise pronta"}],"stopReason":"stop","timestamp":1}""",
            ),
        )
        val assistant = items[0] as ChatItem.AssistantMessage
        assertEquals("vou analisar", assistant.thinking)
        assertEquals("análise pronta", assistant.text)
    }

    @Test
    fun `system toolResult and blank messages are not rendered`() {
        val items = SessionHydrator.toChatItems(
            parse(
                """{"role":"system","content":"","sections":{"cwd":"/x"},"timestamp":1}""",
                """{"role":"user","content":"","timestamp":2}""",
                """{"role":"toolResult","toolCallId":"c9","toolName":"read","content":[{"type":"text","text":"só resultado"}],"isError":false,"timestamp":3}""",
            ),
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `image messages keep the attachment count`() {
        val items = SessionHydrator.toChatItems(
            parse(
                """{"role":"user","content":[{"type":"text","text":"veja"},{"type":"image","mimeType":"image/png","data":"xx"}],"timestamp":1}""",
            ),
        )
        assertEquals(1, (items[0] as ChatItem.UserMessage).imageCount)
    }
}
