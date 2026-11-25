package dev.harness.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `paragraphs split on blank lines`() {
        val nodes = parseMarkdown("primeira\nsegunda\n\noutra linha")
        assertEquals(2, nodes.size)
        assertTrue(nodes[0] is MdNode.Paragraph)
        assertEquals("primeira\nsegunda", (nodes[0] as MdNode.Paragraph).text)
    }

    @Test
    fun `fenced code keeps language and body`() {
        val src = "```kotlin\nfun a() = 1\n```"
        val nodes = parseMarkdown(src)
        val code = nodes.single() as MdNode.Code
        assertEquals("kotlin", code.language)
        assertEquals("fun a() = 1", code.code)
    }

    @Test
    fun `unterminated fence during streaming still yields code`() {
        val src = "texto antes\n```python\nprint(1)"
        val nodes = parseMarkdown(src)
        assertEquals(2, nodes.size)
        val code = nodes[1] as MdNode.Code
        assertEquals("python", code.language)
        assertTrue(code.code.contains("print(1)"))
    }

    @Test
    fun `headings capture level`() {
        val nodes = parseMarkdown("# A\n## B\n### C")
        assertEquals(listOf(1, 2, 3), nodes.map { (it as MdNode.Heading).level })
    }

    @Test
    fun `ordered and unordered list items`() {
        val nodes = parseMarkdown("- um\n1. dois")
        assertTrue(nodes[0] is MdNode.ListItem && !nodes[0].ordered)
        assertTrue(nodes[1] is MdNode.ListItem && nodes[1].ordered)
        assertEquals("1.", (nodes[1] as MdNode.ListItem).marker)
    }

    @Test
    fun `quote merges consecutive lines`() {
        val nodes = parseMarkdown("> linha 1\n> linha 2")
        val quote = nodes.single() as MdNode.Quote
        assertEquals(listOf("linha 1", "linha 2"), quote.lines)
    }

    @Test
    fun `inline bold italic code link`() {
        val s = parseInline(
            "texto **negrito** e *italico* e `cod` e [link](https://x.y)",
            textColor = androidx.compose.ui.graphics.Color.Black,
            codeBackground = androidx.compose.ui.graphics.Color.Gray,
            codeColor = androidx.compose.ui.graphics.Color.White,
            linkColor = androidx.compose.ui.graphics.Color.Blue,
        )
        val annotations = s.getStringAnnotations(URL_TAG, 0, s.length)
        assertEquals(1, annotations.size)
        assertEquals("https://x.y", annotations[0].item)
        assertTrue(s.text.contains("negrito"))
    }

    @Test
    fun `unterminated bold renders literally`() {
        val s = parseInline(
            "abc **def",
            textColor = androidx.compose.ui.graphics.Color.Black,
            codeBackground = androidx.compose.ui.graphics.Color.Gray,
            codeColor = androidx.compose.ui.graphics.Color.White,
            linkColor = androidx.compose.ui.graphics.Color.Blue,
        )
        assertEquals("abc **def", s.text)
    }
}
