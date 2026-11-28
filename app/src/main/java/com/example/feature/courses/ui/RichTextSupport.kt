package com.example.feature.courses.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface RichTextBlock {
    data class Heading(val level: Int, val runs: List<RichInlineRun>) : RichTextBlock
    data class BulletItem(val runs: List<RichInlineRun>) : RichTextBlock
    data class NumberedItem(val number: Int, val runs: List<RichInlineRun>) : RichTextBlock
    data class Paragraph(val runs: List<RichInlineRun>) : RichTextBlock
}

data class RichInlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val linkUrl: String? = null
)

object RichTextParser {
    private val htmlBreakRegex = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val htmlParagraphCloseRegex = Regex("</\\s*(p|div|h1|h2|h3|li|ul|ol)\\s*>", RegexOption.IGNORE_CASE)

    fun parse(rawInput: String): List<RichTextBlock> {
        if (rawInput.isBlank()) return emptyList()

        // Normalize basic HTML formatting into canonical markdown-like lines while preserving formatting
        var text = rawInput
            .replace("\r\n", "\n")
            .replace(htmlBreakRegex, "\n")
            .replace(Regex("<\\s*h1[^>]*>", RegexOption.IGNORE_CASE), "\n# ")
            .replace(Regex("<\\s*h2[^>]*>", RegexOption.IGNORE_CASE), "\n## ")
            .replace(Regex("<\\s*h3[^>]*>", RegexOption.IGNORE_CASE), "\n### ")
            .replace(Regex("<\\s*li[^>]*>", RegexOption.IGNORE_CASE), "\n- ")
            .replace(htmlParagraphCloseRegex, "\n")
            .replace(Regex("<\\s*(p|div|ul|ol)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<\\s*(strong|b)\\s*>", RegexOption.IGNORE_CASE), "**")
            .replace(Regex("</\\s*(strong|b)\\s*>", RegexOption.IGNORE_CASE), "**")
            .replace(Regex("<\\s*(em|i)\\s*>", RegexOption.IGNORE_CASE), "*")
            .replace(Regex("</\\s*(em|i)\\s*>", RegexOption.IGNORE_CASE), "*")

        text = Regex(
            """<\s*a\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</\s*a\s*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).replace(text) { m ->
            val url = m.groupValues[1].trim()
            val label = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim().ifBlank { url }
            "[$label]($url)"
        }

        // Strip any remaining residual HTML tags safely
        text = text.replace(Regex("<[^>]+>"), "")

        val blocks = mutableListOf<RichTextBlock>()
        val lines = text.lines()
        val paragraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                val joined = paragraphBuffer.joinToString("\n").trim()
                if (joined.isNotBlank()) {
                    blocks.add(RichTextBlock.Paragraph(parseInlineRuns(joined)))
                }
                paragraphBuffer.clear()
            }
        }

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> flushParagraph()
                trimmed.startsWith("### ") -> {
                    flushParagraph()
                    blocks.add(RichTextBlock.Heading(3, parseInlineRuns(trimmed.removePrefix("### ").trim())))
                }
                trimmed.startsWith("## ") -> {
                    flushParagraph()
                    blocks.add(RichTextBlock.Heading(2, parseInlineRuns(trimmed.removePrefix("## ").trim())))
                }
                trimmed.startsWith("# ") -> {
                    flushParagraph()
                    blocks.add(RichTextBlock.Heading(1, parseInlineRuns(trimmed.removePrefix("# ").trim())))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    flushParagraph()
                    blocks.add(RichTextBlock.BulletItem(parseInlineRuns(trimmed.drop(2).trim())))
                }
                Regex("""^(\d+)\.\s+(.+)$""").matches(trimmed) -> {
                    flushParagraph()
                    val m = Regex("""^(\d+)\.\s+(.+)$""").find(trimmed)!!
                    val num = m.groupValues[1].toIntOrNull() ?: 1
                    blocks.add(RichTextBlock.NumberedItem(num, parseInlineRuns(m.groupValues[2].trim())))
                }
                else -> paragraphBuffer.add(trimmed)
            }
        }
        flushParagraph()
        return blocks
    }

    private val tokenRegex = Regex(
        """(\[([^\]]+)\]\((https?://[^\s)]+)\))|(\*\*(.+?)\*\*)|(__(.+?)__)|(\*(.+?)\*)|(_([^_\n]+?)_)|(https?://[^\s<>"']+)"""
    )

    fun parseInlineRuns(input: String): List<RichInlineRun> {
        if (input.isEmpty()) return emptyList()
        val runs = mutableListOf<RichInlineRun>()
        var cursor = 0
        for (match in tokenRegex.findAll(input)) {
            if (match.range.first > cursor) {
                runs.add(RichInlineRun(text = input.substring(cursor, match.range.first)))
            }
            when {
                match.groups[1] != null -> {
                    runs.add(
                        RichInlineRun(
                            text = match.groupValues[2],
                            linkUrl = match.groupValues[3]
                        )
                    )
                }
                match.groups[4] != null -> {
                    runs.add(RichInlineRun(text = match.groupValues[5], bold = true))
                }
                match.groups[6] != null -> {
                    runs.add(RichInlineRun(text = match.groupValues[7], bold = true))
                }
                match.groups[8] != null -> {
                    runs.add(RichInlineRun(text = match.groupValues[9], italic = true))
                }
                match.groups[10] != null -> {
                    runs.add(RichInlineRun(text = match.groupValues[11], italic = true))
                }
                match.groups[12] != null -> {
                    val url = match.groupValues[12]
                    runs.add(RichInlineRun(text = url, linkUrl = url))
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < input.length) {
            runs.add(RichInlineRun(text = input.substring(cursor)))
        }
        return runs
    }
}

@Composable
fun RichTextContentView(
    rawText: String,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val blocks = remember(rawText) { RichTextParser.parse(rawText) }
    val linkColor = MaterialTheme.colorScheme.primary
    val bodyColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is RichTextBlock.Heading -> {
                    val annotated = remember(block, linkColor) { buildRunsAnnotatedString(block.runs, linkColor) }
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    }
                    ClickableRichText(
                        text = annotated,
                        style = style.copy(color = bodyColor),
                        onOpenLink = { url -> runCatching { uriHandler.openUri(url) } }
                    )
                }

                is RichTextBlock.BulletItem -> {
                    val annotated = remember(block, linkColor) { buildRunsAnnotatedString(block.runs, linkColor) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ClickableRichText(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(color = bodyColor, lineHeight = 24.sp),
                            modifier = Modifier.weight(1f),
                            onOpenLink = { url -> runCatching { uriHandler.openUri(url) } }
                        )
                    }
                }

                is RichTextBlock.NumberedItem -> {
                    val annotated = remember(block, linkColor) { buildRunsAnnotatedString(block.runs, linkColor) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        ClickableRichText(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(color = bodyColor, lineHeight = 24.sp),
                            modifier = Modifier.weight(1f),
                            onOpenLink = { url -> runCatching { uriHandler.openUri(url) } }
                        )
                    }
                }

                is RichTextBlock.Paragraph -> {
                    val annotated = remember(block, linkColor) { buildRunsAnnotatedString(block.runs, linkColor) }
                    ClickableRichText(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(color = bodyColor, lineHeight = 24.sp),
                        onOpenLink = { url -> runCatching { uriHandler.openUri(url) } }
                    )
                }
            }
        }
    }
}

private fun buildRunsAnnotatedString(
    runs: List<RichInlineRun>,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        runs.forEach { run ->
            val start = length
            append(run.text)
            val end = length
            if (run.bold) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            }
            if (run.italic) {
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            }
            if (!run.linkUrl.isNullOrBlank()) {
                addStyle(
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    end
                )
                addStringAnnotation(
                    tag = "URL",
                    annotation = run.linkUrl,
                    start = start,
                    end = end
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ClickableRichText(
    text: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    onOpenLink: (String) -> Unit
) {
    ClickableText(
        text = text,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { ann -> onOpenLink(ann.item) }
        }
    )
}

@Composable
fun RichTextToolbarEditor(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Rich Text Content",
    modifier: Modifier = Modifier
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    if (fieldValue.text != value) {
        fieldValue = TextFieldValue(
            text = value,
            selection = TextRange(value.length.coerceAtMost(fieldValue.selection.end))
        )
    }

    fun applyWrap(prefix: String, suffix: String, placeholder: String) {
        val current = fieldValue.text
        val start = fieldValue.selection.min.coerceIn(0, current.length)
        val end = fieldValue.selection.max.coerceIn(0, current.length)
        val selected = current.substring(start, end).ifEmpty { placeholder }
        val inserted = "$prefix$selected$suffix"
        val updated = current.substring(0, start) + inserted + current.substring(end)
        val cursorEnd = start + inserted.length
        fieldValue = TextFieldValue(updated, TextRange(cursorEnd))
        onValueChange(updated)
    }

    fun applyLinePrefix(prefix: String) {
        val current = fieldValue.text
        val pos = fieldValue.selection.start.coerceIn(0, current.length)
        val lineStart = current.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let {
            if (it == -1) 0 else it + 1
        }
        val updated = current.substring(0, lineStart) + prefix + current.substring(lineStart)
        val newCursor = (pos + prefix.length).coerceAtMost(updated.length)
        fieldValue = TextFieldValue(updated, TextRange(newCursor))
        onValueChange(updated)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormatChipButton("H1", "rich_btn_h1") { applyLinePrefix("# ") }
                FormatChipButton("H2", "rich_btn_h2") { applyLinePrefix("## ") }
                FormatChipButton("B", "rich_btn_bold") { applyWrap("**", "**", "bold text") }
                FormatChipButton("I", "rich_btn_italic") { applyWrap("*", "*", "italic text") }
                FormatChipButton("• List", "rich_btn_bullet") { applyLinePrefix("- ") }
                FormatChipButton("1. List", "rich_btn_numbered") { applyLinePrefix("1. ") }
                FormatChipButton("Link", "rich_btn_link") {
                    applyWrap("[", "](https://example.com)", "Link text")
                }
            }
        }

        OutlinedTextField(
            value = fieldValue,
            onValueChange = { next ->
                fieldValue = next
                onValueChange(next.text)
            },
            label = { Text(label) },
            placeholder = {
                Text("# Heading\nWrite paragraphs with **bold**, *italic*, - bullet lists, and [links](https://...)")
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .testTag("rich_text_editor_field"),
            minLines = 5,
            maxLines = 12
        )
    }
}

@Composable
private fun FormatChipButton(
    text: String,
    tag: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag(tag)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
