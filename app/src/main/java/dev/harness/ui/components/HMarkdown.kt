package dev.harness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import dev.harness.ui.theme.Space

/*
 * Markdown renderer for agent messages — dependency-free, streaming-friendly.
 *
 * Blocks: fenced code (with language), headings (#..###), ordered/unordered
 * lists (one nesting level), quotes, paragraphs. Inline: **bold**, *italic*,
 * ~~strike~~, `code`, [text](url). A unterminated code fence during streaming
 * still renders as a code block (the common agent case).
 */

internal sealed interface MdNode {
    data class Heading(val level: Int, val text: String) : MdNode
    data class Paragraph(val text: String) : MdNode
    data class Code(val language: String?, val code: String) : MdNode
    data class ListItem(val ordered: Boolean, val marker: String, val text: String) : MdNode
    data class Quote(val lines: List<String>) : MdNode
}

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val UNORDERED = Regex("""^[-*+]\s+(.*)$""")
private val ORDERED = Regex("""^(\d{1,3})[.)]\s+(.*)$""")
private val QUOTE = Regex("""^>\s?(.*)$""")

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
            trimmed.startsWith("#") && HEADING.matches(trimmed) -> {
                flushAll()
                val m = HEADING.matchEntire(trimmed)!!
                out += MdNode.Heading(m.groupValues[1].length, m.groupValues[2].trim())
            }
            UNORDERED.matches(trimmed) -> {
                flushAll()
                val m = UNORDERED.matchEntire(trimmed)!!
                out += MdNode.ListItem(ordered = false, marker = "•", text = m.groupValues[1])
            }
            ORDERED.matches(trimmed) -> {
                flushAll()
                val m = ORDERED.matchEntire(trimmed)!!
                out += MdNode.ListItem(ordered = true, marker = m.groupValues[1] + ".", text = m.groupValues[2])
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

/** Full markdown renderer for a chat message. */
@Composable
fun HMarkdown(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = HarnessTheme
    val nodes = remember(text) { parseMarkdown(text) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        nodes.forEach { node ->
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
                is MdNode.ListItem -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Text(
                        node.marker,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (node.ordered) colors.accentText else colors.textTertiary,
                        modifier = Modifier.width(28.dp),
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
                                androidx.compose.foundation.shape.RoundedCornerShape(dev.harness.ui.theme.Radii.sm),
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
