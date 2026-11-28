package com.example.feature.reading.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.feature.reading.audio.AudioStatus

@Immutable
data class ReadingPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val amber: Color,
    val amberContainer: Color,
    val background: Color,
    val card: Color,
    val paperHighlight: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val matched: Color,
    val matchedBg: Color,
    val missing: Color,
    val missingBg: Color,
    val mismatched: Color,
    val mismatchedBg: Color,
    val extra: Color,
    val extraBg: Color
)

@Composable
fun readingPalette(dark: Boolean = com.example.ui.theme.LocalIsDarkTheme.current): ReadingPalette {
    val vp = com.example.feature.vocabulary.vocabPalette(dark)
    return ReadingPalette(
        primary = vp.primary,
        onPrimary = vp.onPrimary,
        primaryContainer = vp.primaryContainer,
        amber = vp.gold,
        amberContainer = vp.goldContainer,
        background = vp.background,
        card = vp.card,
        paperHighlight = vp.primaryContainer,
        border = vp.border,
        textPrimary = vp.textPrimary,
        textSecondary = vp.textSecondary,
        matched = vp.primary,
        matchedBg = vp.primaryContainer,
        missing = vp.danger,
        missingBg = vp.dangerContainer,
        mismatched = vp.gold,
        mismatchedBg = vp.goldContainer,
        extra = vp.primary,
        extraBg = vp.primaryContainer
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    val pal = readingPalette()
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = pal.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = pal.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reading_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = pal.textPrimary
                )
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = pal.background)
    )
}

@Composable
fun CefrBadge(level: String, modifier: Modifier = Modifier) {
    val pal = readingPalette()
    val isDark = com.example.ui.theme.LocalIsDarkTheme.current
    val (bg, fg) = when (level.uppercase()) {
        "A1", "A2" -> pal.primaryContainer to pal.primary
        "B1", "B2" -> pal.amberContainer to pal.amber
        else -> if (isDark) Color(0xFF3B1F6E) to Color(0xFFDDD6FE) else Color(0xFFEDE9FE) to Color(0xFF6D28D9)
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = level.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun AudioStatusChip(status: AudioStatus, modifier: Modifier = Modifier) {
    val pal = readingPalette()
    val (bg, fg, icon) = when (status) {
        AudioStatus.READY -> Triple(pal.primaryContainer, pal.primary, Icons.Filled.PlayCircle)
        AudioStatus.LOADING -> Triple(pal.amberContainer, pal.amber, Icons.Filled.GraphicEq)
        AudioStatus.PLAYING -> Triple(pal.primary, pal.onPrimary, Icons.Filled.GraphicEq)
        AudioStatus.PAUSED -> Triple(pal.amberContainer, pal.amber, Icons.Filled.PauseCircle)
        AudioStatus.COMPLETED -> Triple(pal.matchedBg, pal.matched, Icons.Filled.CheckCircle)
        AudioStatus.ERROR -> Triple(pal.missingBg, pal.missing, Icons.Filled.ErrorOutline)
        AudioStatus.UNSUPPORTED -> Triple(pal.missingBg, pal.missing, Icons.Filled.ErrorOutline)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.2f)),
        modifier = modifier.testTag("audio_status_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
        }
    }
}

/**
 * Renders an English sentence where tapping any word invokes [onWordTap] with that word,
 * while highlighting words known in the passage vocabulary list.
 */
@Composable
fun ClickableWordSentenceText(
    text: String,
    highlightedVocabWords: Set<String>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onWordTap: (String) -> Unit
) {
    val pal = readingPalette()
    val annotated = remember(text, highlightedVocabWords, pal.primary) {
        buildAnnotatedString {
            val wordRegex = Regex("[A-Za-z][A-Za-z'’-]*")
            var cursor = 0
            for (match in wordRegex.findAll(text)) {
                if (match.range.first > cursor) {
                    append(text.substring(cursor, match.range.first))
                }
                val token = match.value
                val norm = token.lowercase().replace(Regex("[^a-z0-9']"), "").trim('\'')
                val isKeyVocab = highlightedVocabWords.contains(norm)
                pushStringAnnotation(tag = "WORD", annotation = token)
                if (isKeyVocab) {
                    pushStyle(
                        SpanStyle(
                            color = pal.primary,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(token)
                    pop()
                } else {
                    append(token)
                }
                pop()
                cursor = match.range.last + 1
            }
            if (cursor < text.length) {
                append(text.substring(cursor))
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = modifier) {
        Text(
            text = annotated,
            style = style,
            onTextLayout = { layoutResult = it },
            modifier = Modifier.pointerInput(annotated) {
                detectTapGestures { offset ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val pos = layout.getOffsetForPosition(offset)
                    val hit = annotated.getStringAnnotations(tag = "WORD", start = pos, end = pos)
                        .firstOrNull()
                    if (hit != null) {
                        onWordTap(hit.item)
                    }
                }
            }
        )
    }
}
