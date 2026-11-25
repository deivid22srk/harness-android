package dev.harness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/*
 * Lightweight syntax highlighter (regex tokenizer). No dependency: covers the
 * languages agents answer in most (Kotlin, Java, Python, JS/TS, C, Go, Shell,
 * JSON, XML/YAML-ish). Unknown languages render plain mono.
 */

/** Highlight color roles, resolved from the current theme at render time. */
internal data class CodeColors(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val type: Color,
    val annotation: Color,
    val plain: Color,
)

@Composable
internal fun currentCodeColors(): CodeColors {
    val c = HarnessTheme
    return if (c.isDark) {
        CodeColors(
            keyword = Color(0xFFB79CFF),
            string = Color(0xFF8BE9AF),
            number = Color(0xFFF9C74F),
            comment = Color(0xFF7A8399),
            type = Color(0xFF7CDCFE),
            annotation = Color(0xFFFAB383),
            plain = c.textPrimary,
        )
    } else {
        CodeColors(
            keyword = Color(0xFF7C3AED),
            string = Color(0xFF047857),
            number = Color(0xFFB45309),
            comment = Color(0xFF6B7280),
            type = Color(0xFF0369A1),
            annotation = Color(0xFFC2410C),
            plain = c.textPrimary,
        )
    }
}

private val KEYWORDS = setOf(
    "abstract", "as", "break", "case", "catch", "class", "const", "continue", "data", "def", "default",
    "do", "elif", "else", "enum", "export", "extends", "false", "final", "finally", "for", "fun", "func",
    "function", "if", "impl", "implements", "import", "in", "instanceof", "interface", "internal",
    "is", "lambda", "let", "match", "namespace", "new", "null", "object", "operator", "override",
    "package", "pass", "private", "protected", "public", "raise", "return", "sealed", "self", "static",
    "struct", "super", "suspend", "switch", "this", "throw", "trait", "true", "try", "type", "typealias",
    "val", "var", "void", "when", "where", "while", "with", "yield", "nil", "nil?", "None", "end", "then", "fi", "esac", "echo",
)

private val TYPE_PATTERN = Regex("""\b[A-Z][A-Za-z0-9_]*\b""")
private val KEYWORD_PATTERN = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\b""")
private val STRING_PATTERN = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\\\r\\n]|\\\\.)*\"|'(?:[^'\\\\\\r\\n]|\\\\.)*'")
private val COMMENT_PATTERN = Regex("//[^\\r\\n]*|#[^\\r\\n]*")
private val NUMBER_PATTERN = Regex("""\b(?:0[xX][0-9a-fA-F_]+|\d[\d_]*(?:\.\d+)?[fFL]?)\b""")
private val ANNOTATION_PATTERN = Regex("""@[A-Za-z_][A-Za-z0-9_.]*""")

/**
 * Tokenizes [code] into an [AnnotatedString] with code colors. Cheap enough to
 * run per frame during streaming (bounded by message length).
 *
 * Priority: strings -> comments -> annotations -> numbers -> keywords/types.
 */
fun highlightCode(code: String, colors: CodeColors): AnnotatedString = buildAnnotatedString {
    var index = 0
    val length = code.length
    while (index < length) {
        val rest = code.substring(index)
        val stringMatch = STRING_PATTERN.matchAt(rest, 0)
        val commentMatch = COMMENT_PATTERN.matchAt(rest, 0)
        val annotationMatch = ANNOTATION_PATTERN.matchAt(rest, 0)
        val numberMatch = NUMBER_PATTERN.matchAt(rest, 0)
        val wordMatch = KEYWORD_PATTERN.matchAt(rest, 0)
        val token: String = when {
            stringMatch != null -> stringMatch.value
            commentMatch != null -> commentMatch.value
            annotationMatch != null -> annotationMatch.value
            numberMatch != null -> numberMatch.value
            wordMatch != null -> wordMatch.value
            else -> ""
        }
        if (token.isEmpty()) {
            append(rest[0])
            index++
            continue
        }
        when {
            stringMatch != null -> withStyle(SpanStyle(color = colors.string)) { append(token) }
            commentMatch != null -> withStyle(
                SpanStyle(color = colors.comment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
            ) { append(token) }
            annotationMatch != null -> withStyle(SpanStyle(color = colors.annotation, fontWeight = FontWeight.Medium)) { append(token) }
            numberMatch != null -> withStyle(SpanStyle(color = colors.number)) { append(token) }
            token in KEYWORDS -> withStyle(SpanStyle(color = colors.keyword, fontWeight = FontWeight.Medium)) { append(token) }
            TYPE_PATTERN.matches(token) -> withStyle(SpanStyle(color = colors.type, fontWeight = FontWeight.Medium)) { append(token) }
            else -> withStyle(SpanStyle(color = colors.plain)) { append(token) }
        }
        index += token.length
    }
}

/**
 * Code block: language label, copy action, horizontal scroll, selection
 * support and syntax highlighting.
 */
@Composable
fun HCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String? = null,
) {
    val colors = HarnessTheme
    val clipboard = LocalClipboardManager.current
    val shape = RoundedCornerShape(Radii.md)
    val codeColors = currentCodeColors()
    val annotated = remember(code, codeColors) { highlightCode(code, codeColors) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.codeBackground, shape)
            .border(1.dp, colors.border, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = language?.lowercase()?.ifBlank { null }
                    ?: androidx.compose.ui.res.stringResource(dev.harness.R.string.code_language_fallback),
                style = dev.harness.ui.theme.MonoStyle,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = androidx.compose.ui.res.stringResource(dev.harness.R.string.action_copy),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier
                    .touchTargetClickable(role = androidx.compose.ui.semantics.Role.Button) {
                        clipboard.setText(AnnotatedString(code))
                    }
                    .padding(horizontal = Space.sm, vertical = Space.xs),
            )
        }
        HDivider()
        SelectionContainer(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(Space.md),
        ) {
            Text(text = annotated, style = dev.harness.ui.theme.CodeStyle, color = colors.textPrimary)
        }
    }
}

/** Read-only output block (tool results) without copy affordance. */
@Composable
fun HOutputBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int? = null,
) {
    val colors = HarnessTheme
    val shape = RoundedCornerShape(Radii.sm)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.codeBackground, shape)
            .border(1.dp, colors.border, shape)
            .padding(Space.md),
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = dev.harness.ui.theme.MonoStyle,
                color = colors.textSecondary,
                maxLines = maxLines,
                modifier = Modifier.widthIn(max = 700.dp),
            )
        }
    }
}
