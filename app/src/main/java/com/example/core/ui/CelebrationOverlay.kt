package com.example.core.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class CelebrationStyle {
    CONFETTI,
    GOAL_BURST
}

data class CelebrationEvent(
    val id: Long = System.nanoTime(),
    val title: String,
    val subtitle: String = "",
    val style: CelebrationStyle = CelebrationStyle.CONFETTI
)

object CelebrationRules {
    fun shouldCelebrateWordMastered(
        knowsWord: Boolean,
        wasAlreadyMastered: Boolean
    ): Boolean = knowsWord && !wasAlreadyMastered

    fun shouldCelebrateVocabDailyGoal(
        newWordsDone: Int,
        dailyGoal: Int,
        isGoalCompleted: Boolean,
        wasGoalCompletedBefore: Boolean,
        alreadyCelebratedGoalForDate: Int?
    ): Boolean {
        if (dailyGoal <= 0 || !isGoalCompleted || newWordsDone < dailyGoal) return false
        if (wasGoalCompletedBefore) return false
        return alreadyCelebratedGoalForDate == null || alreadyCelebratedGoalForDate < dailyGoal
    }

    fun shouldCelebrateReadingDailyGoal(
        todayMinutes: Int,
        dailyGoalMinutes: Int,
        alreadyCelebratedGoalToday: Int?
    ): Boolean {
        if (dailyGoalMinutes <= 0 || todayMinutes < dailyGoalMinutes) return false
        return alreadyCelebratedGoalToday == null || alreadyCelebratedGoalToday < dailyGoalMinutes
    }

    fun resolvePatternCompletionCelebration(
        patternTitle: String,
        alreadyCelebratedDailyGoalToday: Boolean
    ): CelebrationEvent {
        return if (!alreadyCelebratedDailyGoalToday) {
            CelebrationEvent(
                title = "Daily Pattern Goal Completed!",
                subtitle = "Finished \"$patternTitle\" practice today!",
                style = CelebrationStyle.GOAL_BURST
            )
        } else {
            CelebrationEvent(
                title = "Pattern Completed!",
                subtitle = "Great practice on \"$patternTitle\"",
                style = CelebrationStyle.CONFETTI
            )
        }
    }
}

object DailyGoalCelebrationTracker {
    private const val PREFS_NAME = "engai_celebration_tracker"
    private const val KEY_VOCAB_DATE = "vocab_goal_date"
    private const val KEY_VOCAB_GOAL = "vocab_goal_count"
    private const val KEY_READING_DATE = "reading_goal_date"
    private const val KEY_READING_GOAL = "reading_goal_minutes"
    private const val KEY_PATTERN_DATE = "pattern_goal_date"

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun celebratedVocabGoalForDate(context: Context, dateKey: String): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_VOCAB_DATE, null)
        if (savedDate != dateKey) return null
        val goal = prefs.getInt(KEY_VOCAB_GOAL, -1)
        return goal.takeIf { it > 0 }
    }

    fun markVocabGoalCelebrated(context: Context, dateKey: String, dailyGoal: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VOCAB_DATE, dateKey)
            .putInt(KEY_VOCAB_GOAL, dailyGoal)
            .apply()
    }

    fun celebratedReadingGoalToday(context: Context): Int? {
        val today = todayKey()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_READING_DATE, null) != today) return null
        val goal = prefs.getInt(KEY_READING_GOAL, -1)
        return goal.takeIf { it > 0 }
    }

    fun markReadingGoalCelebratedToday(context: Context, dailyGoalMinutes: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_READING_DATE, todayKey())
            .putInt(KEY_READING_GOAL, dailyGoalMinutes)
            .apply()
    }

    fun hasCelebratedPatternGoalToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PATTERN_DATE, null) == todayKey()
    }

    fun markPatternGoalCelebratedToday(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PATTERN_DATE, todayKey())
            .apply()
    }
}

private enum class ParticleShape { RIBBON, CIRCLE, DIAMOND, STAR }

private data class ConfettiParticle(
    val originXRatio: Float,
    val originYRatio: Float,
    val velocityX: Float,
    val velocityY: Float,
    val gravity: Float,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val sizeDp: Float,
    val aspectRatio: Float,
    val initialRotation: Float,
    val rotationSpeed: Float,
    val flipSpeed: Float,
    val color: Color,
    val shape: ParticleShape
)

private val CelebrationColors = listOf(
    Color(0xFF10B981), // Emerald
    Color(0xFFF59E0B), // Amber Gold
    Color(0xFFF43F5E), // Rose Coral
    Color(0xFF38BDF8), // Sky Blue
    Color(0xFF8B5CF6), // Violet
    Color(0xFF84CC16), // Lime
    Color(0xFFFB923C)  // Orange
)

private fun generateConfettiParticles(seed: Long, count: Int): List<ConfettiParticle> {
    val rng = Random(seed)
    return List(count) { idx ->
        val fromLeftCannon = idx % 3 == 0
        val fromRightCannon = idx % 3 == 1
        val originX = when {
            fromLeftCannon -> 0.14f + rng.nextFloat() * 0.12f
            fromRightCannon -> 0.74f + rng.nextFloat() * 0.12f
            else -> 0.35f + rng.nextFloat() * 0.30f
        }
        val originY = if (!fromLeftCannon && !fromRightCannon) {
            0.10f + rng.nextFloat() * 0.12f
        } else {
            0.22f + rng.nextFloat() * 0.14f
        }
        val vx = when {
            fromLeftCannon -> 180f + rng.nextFloat() * 520f
            fromRightCannon -> -(180f + rng.nextFloat() * 520f)
            else -> (rng.nextFloat() - 0.5f) * 700f
        }
        val vy = -(420f + rng.nextFloat() * 680f)
        ConfettiParticle(
            originXRatio = originX,
            originYRatio = originY,
            velocityX = vx,
            velocityY = vy,
            gravity = 900f + rng.nextFloat() * 520f,
            swayAmplitude = 18f + rng.nextFloat() * 34f,
            swayFrequency = 3f + rng.nextFloat() * 5f,
            sizeDp = 6f + rng.nextFloat() * 6f,
            aspectRatio = 1.3f + rng.nextFloat() * 1.1f,
            initialRotation = rng.nextFloat() * 360f,
            rotationSpeed = (rng.nextFloat() - 0.5f) * 540f,
            flipSpeed = 4f + rng.nextFloat() * 7f,
            color = CelebrationColors[rng.nextInt(CelebrationColors.size)],
            shape = ParticleShape.entries[rng.nextInt(ParticleShape.entries.size)]
        )
    }
}

@Composable
fun CelebrationOverlay(
    event: CelebrationEvent?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember(event?.id) { Animatable(0f) }
    val ringProgress = remember(event?.id) { Animatable(0f) }

    LaunchedEffect(event?.id) {
        if (event != null) {
            progress.snapTo(0f)
            ringProgress.snapTo(0f)
            ringProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(event?.id) {
        if (event != null) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 3100, easing = LinearEasing)
            )
            delay(100)
            onDismiss()
        }
    }

    if (event != null) {
        val particles = remember(event.id) {
            val count = if (event.style == CelebrationStyle.GOAL_BURST) 90 else 65
            generateConfettiParticles(event.id, count)
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("celebration_overlay")
        ) {
            // Non-blocking Lottie-style particle & burst Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = progress.value * 3.1f
                val globalAlpha = when {
                    progress.value < 0.72f -> 1f
                    else -> ((1f - progress.value) / 0.28f).coerceIn(0f, 1f)
                }

                // Expanding radiant celebration rings for GOAL_BURST
                if (event.style == CelebrationStyle.GOAL_BURST && ringProgress.value < 1f) {
                    val center = Offset(size.width * 0.5f, size.height * 0.17f)
                    val radius = (size.minDimension * 0.42f) * ringProgress.value
                    val ringAlpha = (1f - ringProgress.value) * 0.45f
                    drawCircle(
                        color = Color(0xFFF59E0B).copy(alpha = ringAlpha),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 6.dp.toPx() * (1f - ringProgress.value * 0.6f))
                    )
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = ringAlpha * 0.85f),
                        radius = radius * 0.72f,
                        center = center,
                        style = Stroke(width = 4.dp.toPx() * (1f - ringProgress.value * 0.5f))
                    )
                }

                particles.forEach { p ->
                    val dragFactor = (1f - (t * 0.16f)).coerceAtLeast(0.32f)
                    val startX = size.width * p.originXRatio
                    val startY = size.height * p.originYRatio
                    val sway = sin(t * p.swayFrequency + p.initialRotation) * p.swayAmplitude
                    val px = startX + (p.velocityX * t * dragFactor) + sway
                    val py = startY + (p.velocityY * t) + (0.5f * p.gravity * t * t)

                    if (py <= size.height + 60f && px >= -60f && px <= size.width + 60f) {
                        val baseSize = p.sizeDp.dp.toPx()
                        val flipScale = cos(t * p.flipSpeed).let { c ->
                            if (c >= 0f) c.coerceAtLeast(0.22f) else c.coerceAtMost(-0.22f)
                        }
                        val rot = p.initialRotation + p.rotationSpeed * t
                        val colorWithAlpha = p.color.copy(alpha = globalAlpha)

                        rotate(degrees = rot, pivot = Offset(px, py)) {
                            when (p.shape) {
                                ParticleShape.RIBBON -> {
                                    val w = baseSize * kotlin.math.abs(flipScale)
                                    val h = baseSize * p.aspectRatio
                                    drawRect(
                                        color = colorWithAlpha,
                                        topLeft = Offset(px - w / 2f, py - h / 2f),
                                        size = Size(w, h)
                                    )
                                }

                                ParticleShape.CIRCLE -> {
                                    drawCircle(
                                        color = colorWithAlpha,
                                        radius = baseSize * 0.45f,
                                        center = Offset(px, py)
                                    )
                                }

                                ParticleShape.DIAMOND -> {
                                    val half = baseSize * 0.58f
                                    val path = Path().apply {
                                        moveTo(px, py - half)
                                        lineTo(px + half * kotlin.math.abs(flipScale), py)
                                        lineTo(px, py + half)
                                        lineTo(px - half * kotlin.math.abs(flipScale), py)
                                        close()
                                    }
                                    drawPath(path = path, color = colorWithAlpha)
                                }

                                ParticleShape.STAR -> {
                                    val outer = baseSize * 0.62f
                                    val inner = outer * 0.44f
                                    val path = Path()
                                    for (pt in 0 until 10) {
                                        val r = if (pt % 2 == 0) outer else inner
                                        val angle = (pt * 36.0 - 90.0) * PI / 180.0
                                        val sx = px + (cos(angle) * r).toFloat()
                                        val sy = py + (sin(angle) * r).toFloat()
                                        if (pt == 0) path.moveTo(sx, sy) else path.lineTo(sx, sy)
                                    }
                                    path.close()
                                    drawPath(path = path, color = colorWithAlpha)
                                }
                            }
                        }
                    }
                }
            }

            // Animated celebration badge banner at top-center
            val badgeScale = remember(event.id) { Animatable(0.7f) }
            LaunchedEffect(event.id) {
                badgeScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }

            val isGoalBurst = event.style == CelebrationStyle.GOAL_BURST
            val accentColor = if (isGoalBurst) Color(0xFFD97706) else Color(0xFF0E7A3E)
            val surfaceBg = if (isGoalBurst) Color(0xFFFFFBEB) else Color(0xFFECFDF5)
            val borderColor = if (isGoalBurst) Color(0xFFF59E0B) else Color(0xFF10B981)
            val interactionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedVisibility(
                    visible = progress.value < 0.94f,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f) + slideInVertically { -it / 2 },
                    exit = fadeOut(tween(240)) + scaleOut(targetScale = 0.92f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = surfaceBg,
                        border = BorderStroke(1.5.dp, borderColor),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .widthIn(max = 460.dp)
                            .graphicsLayer {
                                scaleX = badgeScale.value
                                scaleY = badgeScale.value
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onDismiss
                            )
                            .testTag("celebration_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accentColor,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isGoalBurst) Icons.Filled.EmojiEvents else Icons.Filled.AutoAwesome,
                                        contentDescription = event.title,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (event.subtitle.isNotBlank()) {
                                    Text(
                                        text = event.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF374151),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
