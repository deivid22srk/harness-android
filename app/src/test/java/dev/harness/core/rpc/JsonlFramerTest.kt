package dev.harness.core.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The RPC protocol splits records on LF only. U+2028/U+2029 inside JSON strings
 * must NOT be treated as boundaries (docs/rpc.md "Framing").
 */
class JsonlFramerTest {

    private fun frames(input: String, chunk: Int = 7): List<String> {
        val framer = JsonlFramer()
        val bytes = input.toByteArray(Charsets.UTF_8)
        val out = ArrayList<String>()
        var i = 0
        while (i < bytes.size) {
            val end = minOf(i + chunk, bytes.size)
            out += framer.feed(bytes.copyOfRange(i, end))
            i = end
        }
        return out
    }

    @Test
    fun `splits simple records`() {
        val records = frames("{\"a\":1}\n{\"b\":2}\n")
        assertEquals(listOf("{\"a\":1}", "{\"b\":2}"), records)
    }

    @Test
    fun `tolerates CRLF`() {
        val records = frames("{\"a\":1}\r\n{\"b\":2}\r\n")
        assertEquals(listOf("{\"a\":1}", "{\"b\":2}"), records)
    }

    @Test
    fun `does not split on unicode line separators`() {
        val payload = "{\"text\":\"a\u2028b\u2029c\"}"
        val records = frames("$payload\n", chunk = 3)
        assertEquals(listOf(payload), records)
    }

    @Test
    fun `handles chunk boundary inside multibyte char`() {
        val payload = "{\"text\":\"café—☕\"}"
        // chunk=1 forces splits in the middle of UTF-8 sequences.
        val records = frames("$payload\n", chunk = 1)
        assertEquals(listOf(payload), records)
    }

    @Test
    fun `reports pending bytes without newline`() {
        val framer = JsonlFramer()
        framer.feed("{\"partial\":".toByteArray())
        assertTrue(framer.pending() > 0)
        val out = framer.feed("1}\n".toByteArray())
        assertEquals(listOf("{\"partial\":1}"), out)
        assertEquals(0, framer.pending())
    }

    @Test
    fun `keeps empty trailing line out of results only when absent`() {
        val records = frames("{\"a\":1}\n\n{\"b\":2}\n")
        // The middle blank line is produced as an empty string frame; callers skip blanks.
        assertEquals(listOf("{\"a\":1}", "", "{\"b\":2}"), records)
    }
}
