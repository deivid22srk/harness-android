package dev.harness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.harness.core.state.DiffLine
import dev.harness.core.state.DiffSummary
import dev.harness.core.state.ToolItem
import dev.harness.core.state.ToolStatus
import dev.harness.ui.theme.HarnessTheme
import dev.harness.ui.theme.MonoStyle
import dev.harness.ui.theme.Radii
import dev.harness.ui.theme.Space

/** Compact pill used by the composer and headers. */
@Composable
fun HarnessPill(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = HarnessTheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(colors.accentMuted)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
        trailing?.invoke()
    }
}

/** Monospace block with a copy affordance and horizontal scroll. */
@Composable
fun CodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String? = null,
) {
    val colors = HarnessTheme
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(colors.codeBackground)
            .border(1.dp, colors.border, RoundedCornerShape(Radii.md)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language ?: "código",
                style = MonoStyle,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Copiar",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radii.sm))
                    .clickable { clipboard.setText(AnnotatedString(code)) }
                    .padding(horizontal = Space.sm, vertical = Space.xs),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.md)
                .padding(bottom = Space.md),
        ) {
            Text(text = code, style = MonoStyle, color = colors.textPrimary)
        }
    }
}

/** One compact tool row; expands to reveal output/diff. */
@Composable
fun ToolRow(tool: ToolItem, modifier: Modifier = Modifier) {
    val colors = HarnessTheme
    var expanded by remember(tool.id) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.sm))
                .clickable { expanded = !expanded }
                .padding(vertical = Space.sm, horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
            StatusDot(tool.status)
            Text(
                text = tool.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
            )
            Text(
                text = tool.summary.lineSequence().firstOrNull().orEmpty(),
                style = MonoStyle,
                color = colors.textSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            tool.diff?.let {
                Text(text = "+${it.added}", style = MonoStyle, color = colors.toolOk)
                Text(text = "−${it.removed}", style = MonoStyle, color = colors.toolError)
            }
            tool.durationMs?.let {
                Text(text = formatDuration(it), style = MonoStyle, color = colors.textTertiary)
            }
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.lg, bottom = Space.sm, end = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                tool.diff?.let { DiffView(it) }
                if (tool.output.isNotBlank()) {
                    CodeBlock(code = tool.output.trim(), language = tool.name.lowercase())
                }
            }
        }
    }
}

@Composable
fun DiffView(diff: DiffSummary) {
    val colors = HarnessTheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(colors.codeBackground)
            .border(1.dp, colors.border, RoundedCornerShape(Radii.md))
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(diff.path, style = MonoStyle, color = colors.textSecondary)
        diff.previewLines.forEach { line ->
            val (prefix, tint) = when (line.kind) {
                DiffLine.Kind.Added -> "+ " to colors.toolOk
                DiffLine.Kind.Removed -> "− " to colors.toolError
                DiffLine.Kind.Header -> "" to colors.textTertiary
                DiffLine.Kind.Context -> "  " to colors.textPrimary
            }
            Text(text = "$prefix${line.text}", style = MonoStyle, color = tint, maxLines = 4)
        }
    }
}

@Composable
fun StatusDot(status: ToolStatus) {
    val colors = HarnessTheme
    val color = when (status) {
        ToolStatus.Running -> colors.toolRunning
        ToolStatus.Ok -> colors.toolOk
        ToolStatus.Error -> colors.toolError
        ToolStatus.Cancelled -> colors.textTertiary
    }
    Box(
        Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

@Composable
fun HarnessDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm)
            .size(1.dp)
            .background(HarnessTheme.border),
    )
}

fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> "%.1fs".format(ms / 1000.0)
    else -> "${ms / 60_000}m"
}

/**
 * Deliberately tiny Markdown subset: fenced code blocks, inline code, bold,
 * and headings. Enough for agent answers without pulling a full parser.
 */
@Composable
fun MarkdownLite(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdownLite(text) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(code = block.code, language = block.language)
                is MdBlock.Heading -> Text(
                    text = renderInline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                is MdBlock.Paragraph -> Text(
                    text = renderInline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val text: String) : MdBlock
    data class Code(val code: String, val language: String?) : MdBlock
}

private fun parseMarkdownLite(source: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = source.lines()
    var i = 0
    val para = StringBuilder()

    fun flush() {
        val t = para.toString().trim()
        if (t.isNotEmpty()) out += MdBlock.Paragraph(t)
        para.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flush()
                val lang = line.trim().removePrefix("```").trim().ifBlank { null }
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.appendLine(lines[i]); i++
                }
                out += MdBlock.Code(code.toString().trimEnd('\n'), lang)
            }
            line.trimStart().startsWith("#") -> {
                flush()
                out += MdBlock.Heading(line.trimStart('#').trim())
            }
            line.isBlank() -> flush()
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(line)
            }
        }
        i++
    }
    flush()
    return out
}

/** Renders `inline code` and **bold** spans. */
@Composable
private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    val colors = HarnessTheme
    var idx = 0
    while (idx < text.length) {
        when {
            text.startsWith("**", idx) -> {
                val end = text.indexOf("**", idx + 2)
                if (end > 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = colors.textPrimary)) {
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
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = colors.codeBackground,
                            color = colors.textPrimary,
                        ),
                    ) { append(text.substring(idx + 1, end)) }
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
