package com.example.feature.reading.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feature.reading.audio.AudioManagerState
import com.example.feature.reading.audio.AudioStatus
import com.example.feature.reading.audio.TtsVoiceOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerBar(
    audioState: AudioManagerState,
    sentenceCount: Int,
    hasAudioUrl: Boolean,
    onPlayPassage: () -> Unit,
    onPlayMp3: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onReplay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectVoice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pal = readingPalette()
    var showVoicePicker by remember { mutableStateOf(false) }
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioStatusChip(status = audioState.status)
                Text(
                    text = if (sentenceCount > 0) {
                        "Sentence ${audioState.activeSentenceIndex + 1} / $sentenceCount"
                    } else "No sentences",
                    style = MaterialTheme.typography.labelMedium,
                    color = pal.textSecondary
                )
                OutlinedButton(
                    onClick = { showVoicePicker = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("audio_voice_picker_button")
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Voice", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (!audioState.errorMessage.isNullOrBlank()) {
                Text(
                    text = audioState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = pal.missing
                )
            }

            // Primary playback transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = sentenceCount > 0 && audioState.activeSentenceIndex > 0,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audio_prev_button")
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous sentence")
                }

                when (audioState.status) {
                    AudioStatus.PLAYING, AudioStatus.LOADING -> {
                        Button(
                            onClick = onPause,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = pal.amber,
                                contentColor = pal.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("audio_pause_button")
                        ) {
                            Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                    }

                    AudioStatus.PAUSED -> {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = pal.primary,
                                contentColor = pal.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("audio_resume_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                    }

                    else -> {
                        Button(
                            onClick = onPlayPassage,
                            enabled = sentenceCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = pal.primary,
                                contentColor = pal.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("audio_play_all_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play All")
                        }
                    }
                }

                IconButton(
                    onClick = onNext,
                    enabled = sentenceCount > 0 && audioState.activeSentenceIndex < sentenceCount - 1,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audio_next_button")
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next sentence")
                }

                IconButton(
                    onClick = onReplay,
                    enabled = sentenceCount > 0,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audio_replay_button")
                ) {
                    Icon(Icons.Filled.Replay, contentDescription = "Replay")
                }

                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("audio_stop_button")
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                }
            }

            // Speed & optional MP3 selector row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                speeds.forEach { spd ->
                    val selected = kotlin.math.abs(audioState.speed - spd) < 0.05f
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSpeed(spd) },
                        label = { Text("${spd}x") },
                        modifier = Modifier.testTag("audio_speed_${spd}")
                    )
                }

                if (hasAudioUrl) {
                    OutlinedButton(
                        onClick = onPlayMp3,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("audio_play_mp3_button")
                    ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play MP3", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (showVoicePicker) {
        VoiceSelectorSheet(
            voices = audioState.availableVoices,
            selectedVoiceId = audioState.selectedVoiceId,
            accent = audioState.accent,
            onSelectVoice = {
                onSelectVoice(it)
                showVoicePicker = false
            },
            onDismiss = { showVoicePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSelectorSheet(
    voices: List<TtsVoiceOption>,
    selectedVoiceId: String,
    accent: String,
    onSelectVoice: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val pal = readingPalette()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pal.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Select Reader Voice ($accent)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = pal.textPrimary
            )
            if (voices.isEmpty()) {
                Text(
                    text = "Using default system voice for $accent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = pal.textSecondary
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(voices, key = { it.id }) { voice ->
                        val isSelected = voice.id == selectedVoiceId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) pal.primaryContainer else pal.background,
                            border = BorderStroke(1.dp, if (isSelected) pal.primary else pal.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectVoice(voice.id) }
                                .minimumInteractiveComponentSize()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = voice.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = pal.textPrimary
                                    )
                                    Text(
                                        text = "${voice.localeTag}${if (voice.isNetworkRequired) " · Online" else " · Offline"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = pal.textSecondary
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = pal.primary)
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = pal.border)
        }
    }
}
