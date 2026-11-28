package com.example.feature.vocabulary

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.example.ui.theme.LocalIsDarkTheme

val BrandGreen = Color(0xFF0E6B3B)
val BrandGreenLight = Color(0xFFD1FAE5)
val BrandGold = Color(0xFFD97706)
val BrandGoldLight = Color(0xFFFEF3C7)
val BrandBackgroundLight = Color(0xFFF8FAF9)
val BrandBorderLight = Color(0xFFE5E7EB)

val LocalVocabDarkTheme = compositionLocalOf<Boolean?> { null }

@Immutable
data class VocabPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val gold: Color,
    val goldContainer: Color,
    val background: Color,
    val card: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val danger: Color,
    val dangerContainer: Color
)

@Composable
fun vocabPalette(dark: Boolean = LocalVocabDarkTheme.current ?: LocalIsDarkTheme.current): VocabPalette {
    return if (dark) {
        VocabPalette(
            primary = Color(0xFF52B788),
            onPrimary = Color(0xFF072214),
            primaryContainer = Color(0xFF163D28),
            gold = Color(0xFFF3B44B),
            goldContainer = Color(0xFF3E2B0D),
            background = Color(0xFF0D1510),
            card = Color(0xFF15221A),
            border = Color(0xFF263B2E),
            textPrimary = Color(0xFFF0F7F2),
            textSecondary = Color(0xFFA7BDB0),
            danger = Color(0xFFFCA5A5),
            dangerContainer = Color(0xFF4C1D1D)
        )
    } else {
        VocabPalette(
            primary = BrandGreen,
            onPrimary = Color.White,
            primaryContainer = BrandGreenLight,
            gold = BrandGold,
            goldContainer = BrandGoldLight,
            background = BrandBackgroundLight,
            card = Color.White,
            border = BrandBorderLight,
            textPrimary = Color(0xFF111827),
            textSecondary = Color(0xFF6B7280),
            danger = Color(0xFFDC2626),
            dangerContainer = Color(0xFFFEE2E2)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    val pal = vocabPalette()
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = pal.textPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = pal.textSecondary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("vocab_back_button")
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
fun VocabLoadingSkeleton(lines: Int = 4, modifier: Modifier = Modifier) {
    val pal = vocabPalette()
    val transition = rememberInfiniteTransition(label = "vocab_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(lines) { idx ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = pal.border.copy(alpha = alpha),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (idx == 0) 132.dp else 80.dp)
            ) {}
        }
    }
}

@Composable
fun VocabErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = vocabPalette()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(pal.dangerContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = pal.danger,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = pal.textPrimary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = pal.textSecondary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("vocab_retry_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

@Composable
fun VocabEmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.AutoMirrored.Filled.MenuBook,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val pal = vocabPalette()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(pal.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = pal.primary, modifier = Modifier.size(28.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = pal.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = pal.textSecondary,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun VocabFeedbackBanner(
    message: String?,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (message.isNullOrBlank()) return
    val pal = vocabPalette()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isError) pal.dangerContainer else pal.primaryContainer,
        border = BorderStroke(1.dp, if (isError) pal.danger.copy(alpha = 0.4f) else pal.primary.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (isError) pal.danger else pal.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) pal.danger else pal.textPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
