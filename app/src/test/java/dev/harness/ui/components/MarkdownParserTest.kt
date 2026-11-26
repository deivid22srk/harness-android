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
        val first = nodes[0]
        assertTrue(first is MdNode.ListItem && !first.ordered)
        val second = nodes[1] as MdNode.ListItem
        assertTrue(second.ordered)
        assertEquals("1.", second.marker)
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

    // -----------------------------------------------------------------
    // Rodada 2 (M1): tables, nesting, task lists, horizontal rules
    // -----------------------------------------------------------------

    @Test
    fun `pipe table with separator becomes table`() {
        val src = "| Job | Duração |\n|---|---|\n| build | ~2s |\n| game | ~3s |"
        val nodes = parseMarkdown(src)
        val table = nodes.single() as MdNode.Table
        assertEquals(listOf("Job", "Duração"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("build", "~2s"), table.rows[0])
    }

    @Test
    fun `table rows are normalized to column count`() {
        val src = "| a | b | c |\n|---|---|---|\n| 1 | 2 |"
        val table = parseMarkdown(src).single() as MdNode.Table
        assertEquals(3, table.rows[0].size)
        assertEquals("", table.rows[0][2])
    }

    @Test
    fun `escaped pipe stays inside a cell`() {
        val src = "| expr | out |\n|---|---|\n| a \\| b | c |"
        val table = parseMarkdown(src).single() as MdNode.Table
        assertEquals("a | b", table.rows[0][0])
    }

    @Test
    fun `table separator is not a horizontal rule`() {
        val src = "| a | b |\n|---|---|\n| 1 | 2 |\n\ntexto\n\n---\n\nfim"
        val nodes = parseMarkdown(src)
        assertEquals(4, nodes.size)
        assertTrue(nodes[0] is MdNode.Table)
        assertTrue(nodes[1] is MdNode.Paragraph)
        assertEquals(MdNode.HorizontalRule, nodes[2])
        assertTrue(nodes[3] is MdNode.Paragraph)
    }

    @Test
    fun `unterminated table still renders during streaming`() {
        val src = "| a | b |\n|---|---|\n| 1 | 2"
        val nodes = parseMarkdown(src)
        val table = nodes.single() as MdNode.Table
        assertEquals(1, table.rows.size)
    }

    @Test
    fun `nested list indentation maps to depth`() {
        val nodes = parseMarkdown("- raiz\n  - filho\n    - neto")
        val depths = nodes.map { (it as MdNode.ListItem).depth }
        assertEquals(listOf(0, 1, 2), depths)
    }

    @Test
    fun `task list captures checkbox state`() {
        val nodes = parseMarkdown("- [x] feito\n- [ ] pendente")
        val done = nodes[0] as MdNode.ListItem
        val pending = nodes[1] as MdNode.ListItem
        assertEquals(true, done.taskDone)
        assertEquals(false, pending.taskDone)
        assertEquals("feito", done.text)
    }

    @Test
    fun `horizontal rule variants`() {
        val nodes = parseMarkdown("a\n\n---\n\n***\n\n___\n\nb")
        assertEquals(listOf(3), nodes.filterIsInstance<MdNode.HorizontalRule>().size.let { listOf(it) })
    }

    @Test
    fun `resource fixture of a typical agent answer parses`() {
        val text = javaClass.getResourceAsStream("/markdown/agent-typical.md")!!
            .readBytes().decodeToString()
        val nodes = parseMarkdown(text)
        assertTrue(nodes.any { it is MdNode.Table })
        assertTrue(nodes.any { it is MdNode.Code })
        assertTrue(nodes.any { it is MdNode.ListItem && it.taskDone != null })
        assertTrue(nodes.any { it == MdNode.HorizontalRule })
    }
}
