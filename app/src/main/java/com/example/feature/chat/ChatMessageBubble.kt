package com.example.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ChatAiCorrection
import com.example.domain.model.ChatMessageItem
import com.example.domain.model.ChatReplyQuote
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val OutgoingGreen = Color(0xFF0E7A3E)
private val CreamBg = Color(0xFFFFF8EC)
private val DashedAmber = Color(0xFFE5A446)
private val HighlightOrange = Color(0xFFD97706)
private val SparkleOrange = Color(0xFFEA8C1E)

private val AvatarPalette = listOf(
    Color(0xFF0E5A3E),
    Color(0xFF0F766E),
    Color(0xFF6D28D9),
    Color(0xFF0369A1),
    Color(0xFFB45309)
)

@Composable
fun ChatDateDivider(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFE7EAE7)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4B5563),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
    }
}

fun ChatMessageItem.displayTextForViewer(): String {
    if (!isMine && aiCorrection.hasMistake && aiCorrection.correctedText.isNotBlank()) {
        return aiCorrection.correctedText
    }
    return text
}

@Composable
fun ChatMessageBubbleRow(
    msg: ChatMessageItem,
    isCorrecting: Boolean,
    isHighlighted: Boolean = false,
    onReply: () -> Unit,
    onRequestAiCorrection: () -> Unit
) {
    val timeLabel = remember(msg.createdAt) { formatMessageTime(msg.createdAt) }
    val rowModifier = if (isHighlighted) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFDDF7E6))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    Box(modifier = rowModifier) {
        if (msg.isMine) {
            OutgoingBubble(
                msg = msg,
                timeLabel = timeLabel,
                isCorrecting = isCorrecting,
                onReply = onReply,
                onRequestAiCorrection = onRequestAiCorrection
            )
        } else {
            IncomingBubble(
                msg = msg,
                timeLabel = timeLabel,
                onReply = onReply
            )
        }
    }
}

@Composable
private fun IncomingBubble(
    msg: ChatMessageItem,
    timeLabel: String,
    onReply: () -> Unit
) {
    val accent = remember(msg.senderId) {
        AvatarPalette[abs(msg.senderId.hashCode()) % AvatarPalette.size]
    }
    val initials = remember(msg.senderName) {
        msg.senderName
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifBlank { "S" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = msg.senderName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                if (msg.senderBadge.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (msg.senderBadge == "Admin") Color(0xFFF6C644) else Color(0xFFE8F3EC)
                    ) {
                        Text(
                            text = msg.senderBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (msg.senderBadge == "Admin") Color(0xFF451A03) else OutgoingGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomEnd = 18.dp,
                        bottomStart = 6.dp
                    ),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE6EBE6)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier
                        .widthIn(max = 285.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(onClick = onReply)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        msg.replyTo?.let { quote ->
                            ReplyQuoteCard(quote = quote, isOutgoingBubble = false)
                        }
                        Text(
                            text = msg.displayTextForViewer(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF111827)
                        )
                    }
                }
                IconButton(
                    onClick = onReply,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply to ${msg.senderName}",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (timeLabel.isNotBlank()) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun OutgoingBubble(
    msg: ChatMessageItem,
    timeLabel: String,
    isCorrecting: Boolean,
    onReply: () -> Unit,
    onRequestAiCorrection: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 6.dp
            ),
            color = OutgoingGreen,
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onReply)
                .testTag("chat_outgoing_bubble_${msg.id}")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                msg.replyTo?.let { quote ->
                    ReplyQuoteCard(quote = quote, isOutgoingBubble = true)
                }
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Normal
                )

                OutgoingAiSection(
                    ai = msg.aiCorrection,
                    isCorrecting = isCorrecting,
                    onRequestAiCorrection = onRequestAiCorrection
                )
            }
        }

        if (timeLabel.isNotBlank()) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(top = 3.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun OutgoingAiSection(
    ai: ChatAiCorrection,
    isCorrecting: Boolean,
    onRequestAiCorrection: () -> Unit
) {
    var showExplanation by remember(ai.correctedText) { mutableStateOf(false) }

    when {
        isCorrecting -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFFDE68A)
                )
                Text(
                    text = "Checking grammar...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        ai.status == "corrected" && ai.hasMistake && ai.correctedText.isNotBlank() -> {
            val annotated = remember(ai.tokens, ai.correctedText) {
                buildAnnotatedString {
                    if (ai.tokens.isEmpty()) {
                        withStyle(SpanStyle(color = HighlightOrange, fontWeight = FontWeight.Bold)) {
                            append(ai.correctedText)
                        }
                    } else {
                        ai.tokens.forEachIndexed { idx, token ->
                            if (idx > 0) append(" ")
                            if (token.changed) {
                                withStyle(SpanStyle(color = HighlightOrange, fontWeight = FontWeight.Bold)) {
                                    append(token.text)
                                }
                            } else {
                                withStyle(SpanStyle(color = Color(0xFF1F2937), fontWeight = FontWeight.Medium)) {
                                    append(token.text)
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CreamBg)
                        .drawBehind {
                            drawRoundRect(
                                color = DashedAmber,
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                                style = Stroke(
                                    width = 1.4.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                )
                            )
                        }
                        .clickable(enabled = ai.explanationBn.isNotBlank()) {
                            showExplanation = !showExplanation
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("chat_ai_correction_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "AI grammar correction",
                            tint = SparkleOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                    }
                }

                AnimatedVisibility(visible = showExplanation && ai.explanationBn.isNotBlank()) {
                    Text(
                        text = ai.explanationBn,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        ai.status == "correct" -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircleOutline,
                    contentDescription = null,
                    tint = Color(0xFFBBF7D0),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Natural English",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBBF7D0)
                )
            }
        }

        ai.status == "unavailable" -> {
            Text(
                text = "AI correction not configured on server",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f)
            )
        }

        ai.status == "none" -> {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White.copy(alpha = 0.16f),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRequestAiCorrection)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFDE68A),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Check AI grammar",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyQuoteCard(
    quote: ChatReplyQuote,
    isOutgoingBubble: Boolean
) {
    val bg = if (isOutgoingBubble) Color.White.copy(alpha = 0.16f) else Color(0xFFF2F4F2)
    val barColor = if (isOutgoingBubble) Color(0xFFA7F3D0) else OutgoingGreen
    val nameColor = if (isOutgoingBubble) Color(0xFFD1FAE5) else OutgoingGreen
    val previewColor = if (isOutgoingBubble) Color.White.copy(alpha = 0.9f) else Color(0xFF374151)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight()
                    .background(barColor)
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = quote.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = nameColor
                )
                Text(
                    text = quote.textPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = previewColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatMessageTime(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val zdt = Instant.parse(iso).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(zdt)
    }.getOrDefault("")
}

fun formatDateDivider(iso: String): String {
    if (iso.isBlank()) return "Today"
    return runCatching {
        val date = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now(ZoneId.systemDefault())
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(date)
        }
    }.getOrDefault("Today")
}
