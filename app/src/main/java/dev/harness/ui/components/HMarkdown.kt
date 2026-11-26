package dev.harness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/*
 * Markdown renderer for agent messages — dependency-free, streaming-friendly.
 *
 * Blocks: fenced code (with language), headings (#..######), ordered/unordered
 * lists (nested, task lists), quotes, pipe tables, horizontal rules,
 * paragraphs. Inline: **bold**, *italic*, ~~strike~~, `code`, [text](url).
 * An unterminated code fence during streaming still renders as a code block
 * (the common agent case); an unterminated table renders as a table.
 */

internal sealed interface MdNode {
    data class Heading(val level: Int, val text: String) : MdNode
    data class Paragraph(val text: String) : MdNode
    data class Code(val language: String?, val code: String) : MdNode
    data class ListItem(
        val ordered: Boolean,
        val marker: String,
        val text: String,
        /** Nesting depth derived from leading indentation (2 spaces per level). */
        val depth: Int = 0,
        /** null = normal item; true/false = task list checkbox. */
        val taskDone: Boolean? = null,
    ) : MdNode
    data class Quote(val lines: List<String>) : MdNode
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdNode
    data object HorizontalRule : MdNode
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val UNORDERED = Regex("""^[-*+]\s+(.*)$""")
private val ORDERED = Regex("""^(\d{1,3})[.)]\s+(.*)$""")
private val QUOTE = Regex("""^>\s?(.*)$""")
private val HRULE = Regex("""^(\*{3,}|-{3,}|_{3,})\s*$""")
private val TASK = Regex("""^\[([ xX])]\s+(.*)$""")
/** GFM separator row: `| --- | :---: |` or `---|---`. */
private val TABLE_SEPARATOR = Regex("""^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)*\|?\s*$""")
private val LEADING_SPACES = Regex("""^( +)\S.*$""")

private fun isTableSeparator(line: String): Boolean =
    TABLE_SEPARATOR.matches(line.trimEnd()) && line.contains('-')

private fun looksLikeTableRow(line: String): Boolean =
    line.trim().startsWith("|") || (line.contains(" | ") || line.contains("| "))

/** Splits a pipe row into cells, honoring escaped `\|` and dropping edge pipes. */
internal fun splitTableRow(line: String): List<String> {
    var t = line.trim()
    if (t.startsWith("|")) t = t.removePrefix("|")
    if (t.endsWith("|") && !t.endsWith("\\|")) t = t.removeSuffix("|")
    return t.split(Regex("""(?<!\\)\|""")).map { it.trim().replace("\\|", "|") }
}

/** Pure, JVM-testable markdown block parser. */
internal fun parseMarkdown(source: String): List<MdNode> {
    val out = ArrayList<MdNode>()
    val lines = source.lines()
    var i = 0
    val para = StringBuilder()
    val quoteLines = ArrayList<String>()

    fun flushParagraph() {
        val t = para.toString().trim()
        if (t.isNotEmpty()) out += MdNode.Paragraph(t)
        para.clear()
    }

    fun flushQuote() {
        if (quoteLines.isNotEmpty()) {
            out += MdNode.Quote(quoteLines.toList())
            quoteLines.clear()
        }
    }

    fun flushAll() {
        flushParagraph()
        flushQuote()
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                flushAll()
                val lang = trimmed.removePrefix("```").trim().ifBlank { null }
                val code = StringBuilder()
                i++
                var closed = false
                while (i < lines.size) {
                    if (lines[i].trimStart().startsWith("```")) {
                        closed = true
                        break
                    }
                    code.append(lines[i]).append('\n')
                    i++
                }
                out += MdNode.Code(lang, code.toString().trimEnd('\n'))
                if (!closed) break // streaming: rest is code
            }
            // Pipe table: candidate row + GFM separator right below it. Checked
            // BEFORE the heading/rule branches so `|---|` never leaks raw.
            looksLikeTableRow(trimmed) &&
                i + 1 < lines.size &&
                isTableSeparator(lines[i + 1]) -> {
                flushAll()
                val header = splitTableRow(trimmed)
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < lines.size && looksLikeTableRow(lines[i]) && !lines[i].isBlank()) {
                    rows += splitTableRow(lines[i])
                    i++
                }
                val width = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
                val h = header + List(width - header.size) { "" }
                val normalized = rows.map { it + List(width - it.size) { "" } }
                out += MdNode.Table(h, normalized)
                continue // already advanced past the block
            }
            HRULE.matches(trimmed) -> {
                flushAll()
                out += MdNode.HorizontalRule
            }
            trimmed.startsWith("#") && HEADING.matches(trimmed) -> {
                flushAll()
                val m = HEADING.matchEntire(trimmed)!!
                out += MdNode.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            }
            UNORDERED.matches(trimmed) || ORDERED.matches(trimmed) -> {
                flushAll()
                val spaces = LEADING_SPACES.matchEntire(line)?.groupValues?.get(1)?.length ?: 0
                val depth = (spaces / 2).coerceIn(0, 3)
                val m = if (UNORDERED.matches(trimmed)) UNORDERED.matchEntire(trimmed)!! else null
                if (m != null) {
                    val raw = m.groupValues[1]
                    val task = TASK.matchEntire(raw)
                    if (task != null) {
                        out += MdNode.ListItem(
                            ordered = false,
                            marker = "•",
                            text = task.groupValues[2],
                            depth = depth,
                            taskDone = task.groupValues[1] != " ",
                        )
                    } else {
                        out += MdNode.ListItem(ordered = false, marker = "•", text = raw, depth = depth)
                    }
                } else {
                    val om = ORDERED.matchEntire(trimmed)!!
                    out += MdNode.ListItem(
                        ordered = true,
                        marker = om.groupValues[1] + ".",
                        text = om.groupValues[2],
                        depth = depth,
                    )
                }
            }
            QUOTE.matches(trimmed) -> {
                flushParagraph()
                val m = QUOTE.matchEntire(trimmed)!!
                quoteLines += m.groupValues[1]
            }
            trimmed.isBlank() -> flushAll()
            else -> {
                flushQuote()
                if (para.isNotEmpty()) para.append('\n')
                para.append(line)
            }
        }
        i++
    }
    flushAll()
    return out
}

/** Inline spans: bold, italic, strike, inline code, links (URL annotations). */
internal const val URL_TAG = "URL"

internal fun parseInline(
    text: String,
    textColor: Color,
    codeBackground: Color,
    codeColor: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    var idx = 0
    val n = text.length
    while (idx < n) {
        when {
            text.startsWith("**", idx) -> {
                val end = text.indexOf("**", idx + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = textColor)) {
                        append(text.substring(idx + 2, end))
                    }
                    idx = end + 2
                } else {
                    append(text[idx]); idx++
                }
            }
            text.startsWith("~~", idx) -> {
                val end = text.indexOf("~~", idx + 2)
                if (end > 0) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = textColor)) {
                        append(text.substring(idx + 2, end))
                    }
                    idx = end + 2
                } else {
                    append(text[idx]); idx++
                }
            }
            text[idx] == '`' -> {
                val end = text.indexOf('`', idx + 1)
                if (end > 0) {
                    withStyle(
                        SpanStyle(fontFamily = dev.harness.ui.theme.JetBrainsMono, background = codeBackground, color = codeColor),
                    ) { append(text.substring(idx + 1, end)) }
                    idx = end + 1
                } else {
                    append(text[idx]); idx++
                }
            }
            text[idx] == '[' -> {
                val closeBracket = text.indexOf("](", idx + 1)
                val closeParen = if (closeBracket > 0) text.indexOf(')', closeBracket + 2) else -1
                if (closeBracket > 0 && closeParen > closeBracket) {
                    val label = text.substring(idx + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    pushStringAnnotation(URL_TAG, url)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(label.ifBlank { url })
                    }
                    pop()
                    idx = closeParen + 1
                } else {
                    append(text[idx]); idx++
                }
            }
            text[idx] == '*' && idx + 1 < n && text[idx + 1] != '*' -> {
                val end = text.indexOf('*', idx + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                        append(text.substring(idx + 1, end))
                    }
                    idx = end + 1
                } else {
                    append(text[idx]); idx++
                }
            }
            text[idx] == '_' && (idx == 0 || !text[idx - 1].isLetterOrDigit()) -> {
                val end = text.indexOf('_', idx + 1)
                if (end > 0) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                        append(text.substring(idx + 1, end))
                    }
                    idx = end + 1
                } else {
                    append(text[idx]); idx++
                }
            }
            else -> {
                append(text[idx]); idx++
            }
        }
    }
}

/**
 * Paragraph text with tappable links. One Text per paragraph keeps streaming
 * recomposition cheap (a single text node).
 */
@Composable
internal fun InlineMarkdownText(text: String, modifier: Modifier = Modifier) {
    val colors = HarnessTheme
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, colors.isDark) {
        parseInline(
            text = text,
            textColor = colors.textPrimary,
            codeBackground = colors.codeBackground,
            codeColor = colors.textPrimary,
            linkColor = colors.accentText,
        )
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        color = colors.textPrimary,
        modifier = modifier
            .pointerInput(annotated) {
                detectTapGestures { position ->
                    val l = layout ?: return@detectTapGestures
                    val offset = l.getOffsetForPosition(position)
                    annotated.getStringAnnotations(URL_TAG, offset, offset)
                        .firstOrNull()
                        ?.let { runCatching { uriHandler.openUri(it.item) } }
                }
            },
        onTextLayout = { layout = it },
    )
}

/** Pipe table with header, horizontal scroll for wide tables (PROMPT §5.2). */
@Composable
private fun MdTableBlock(header: List<String>, rows: List<List<String>>) {
    val colors = HarnessTheme
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Radii.md)
    val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    Column(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(shape)
            .background(colors.surfaceElevated, shape)
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
            header.forEach { cell ->
                Text(
                    text = parseInline(cell, colors.textPrimary, colors.codeBackground, colors.textPrimary, colors.accentText),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.widthIn(min = 96.dp),
                )
            }
        }
        HDivider(modifier = Modifier.padding(vertical = Space.xs))
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                row.forEach { cell ->
                    Text(
                        text = parseInline(cell, colors.textSecondary, colors.codeBackground, colors.textSecondary, colors.accentText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.widthIn(min = 96.dp),
                    )
                }
            }
        }
    }
}

private val ListBulletWidth = 28.dp
private val DepthIndent = 18.dp

/** Full markdown renderer for a chat message. */
@Composable
fun HMarkdown(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    val nodes = remember(text) { parseMarkdown(text) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        nodes.forEachIndexed { idx, node ->
            // Stable per-block identity: during streaming only the tail block's
            // content changes, so keyed blocks skip redundant work (PROMPT §5.3).
            key(idx) {
                when (node) {
                    is MdNode.Heading -> Text(
                        text = node.text,
                        style = when (node.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        color = colors.textPrimary,
                    )
                    is MdNode.Paragraph -> InlineMarkdownText(node.text)
                    is MdNode.Code -> HCodeBlock(code = node.code, language = node.language)
                    is MdNode.Table -> MdTableBlock(node.header, node.rows)
                    is MdNode.HorizontalRule -> HDivider(modifier = Modifier.padding(vertical = Space.xs))
                    is MdNode.ListItem -> Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = DepthIndent * node.depth),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        val markerText = when {
                            node.taskDone == true -> "✓"
                            node.taskDone == false -> "○"
                            else -> node.marker
                        }
                        Text(
                            markerText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = when {
                                node.taskDone == true -> colors.toolOk
                                node.ordered -> colors.accentText
                                else -> colors.textTertiary
                            },
                            modifier = Modifier.width(ListBulletWidth),
                        )
                        InlineMarkdownText(node.text, Modifier.weight(1f))
                    }
                    is MdNode.Quote -> Row(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .padding(top = 4.dp, bottom = 4.dp)
                                .width(2.dp)
                                .background(colors.accent.copy(alpha = 0.6f)),
                        )
                        Column(
                            Modifier
                                .padding(start = Space.md)
                                .background(
                                    colors.surfaceElevated,
                                    androidx.compose.foundation.shape.RoundedCornerShape(Radii.sm),
                                )
                                .padding(horizontal = Space.md, vertical = Space.sm),
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            node.lines.forEach { line ->
                                Text(
                                    parseInline(line, colors.textSecondary, colors.codeBackground, colors.textSecondary, colors.accentText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
