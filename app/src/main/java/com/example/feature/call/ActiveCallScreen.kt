package com.example.feature.call

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CallMode
import com.example.domain.model.CallParticipantSummary
import com.example.domain.model.CallRoomSummary
import com.example.feature.call.engine.CallConnectionPhase
import com.example.feature.call.engine.CallEngine
import com.example.ui.theme.LocalIsDarkTheme

@Composable
fun ActiveCallScreen(
    state: ActiveCallUiState,
    engine: CallEngine?,
    onToggleMic: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onLeaveCall: (endRoomForAll: Boolean) -> Unit
) {
    val room = state.room ?: return
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val stageBg = if (isDark) Color(0xFF070C14) else Color(0xFF101826)
    val tileBg = if (isDark) Color(0xFF111C2D) else Color(0xFF1A2436)
    val emerald = Color(0xFF10B981)

    BackHandler {
        onLeaveCall(false)
    }

    val selfId = room.selfUserId
    val remoteParticipants = room.participants.filter { it.userId != selfId }
    val engState = state.engineState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(stageBg)
    ) {
        // Video Stage
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 86.dp, bottom = 104.dp, start = 12.dp, end = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                remoteParticipants.isEmpty() -> {
                    WaitingStageCard(
                        room = room,
                        phase = engState.phase,
                        tileBg = tileBg,
                        emerald = emerald,
                        onCopyCode = { code -> copyRoomCode(context, code) }
                    )
                }

                remoteParticipants.size == 1 -> {
                    val peer = remoteParticipants.first()
                    RemoteParticipantTile(
                        participant = peer,
                        hasVideoStream = engState.remoteVideoAvailable.contains(peer.userId) && peer.videoEnabled,
                        streamVersion = engState.remoteStreamsVersion,
                        engine = engine,
                        tileBg = tileBg,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns = if (maxWidth >= 640.dp || remoteParticipants.size >= 5) 3 else 2
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(remoteParticipants, key = { it.userId }) { peer ->
                                RemoteParticipantTile(
                                    participant = peer,
                                    hasVideoStream = engState.remoteVideoAvailable.contains(peer.userId) && peer.videoEnabled,
                                    streamVersion = engState.remoteStreamsVersion,
                                    engine = engine,
                                    tileBg = tileBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (columns == 3) 200.dp else 235.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Local Picture-in-Picture preview
            if (engState.localVideoEnabled && engState.hasCameraCapture && engine != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tileBg,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.3f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(width = 104.dp, height = 148.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSwitchCamera() }
                        .testTag("active_call_local_pip")
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        engine.LocalVideoSurface(modifier = Modifier.fillMaxSize())
                        Surface(
                            color = Color.Black.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "You",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Top Call Header
        ActiveCallTopHeader(
            room = room,
            elapsedSeconds = state.elapsedSeconds,
            phase = engState.phase,
            providerLabel = state.transport?.providerLabel ?: engState.providerLabel,
            onCopyCode = { code -> copyRoomCode(context, code) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        // Bottom Controls Bar
        ActiveCallControlsBar(
            audioMuted = engState.localAudioMuted,
            videoEnabled = engState.localVideoEnabled,
            speakerOn = engState.isSpeakerphone,
            canSwitchCamera = engState.hasCameraCapture && engState.localVideoEnabled,
            isHostGroup = room.isHost && room.mode != CallMode.DIRECT,
            onToggleMic = onToggleMic,
            onToggleVideo = onToggleVideo,
            onSwitchCamera = onSwitchCamera,
            onToggleSpeaker = onToggleSpeaker,
            onEndCall = { onLeaveCall(room.isHost && room.mode != CallMode.DIRECT && room.participantCount <= 1) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun ActiveCallTopHeader(
    room: CallRoomSummary,
    elapsedSeconds: Int,
    phase: CallConnectionPhase,
    providerLabel: String,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.48f),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when (room.mode) {
                            CallMode.DIRECT -> Icons.Filled.PhoneInTalk
                            CallMode.GROUP_PUBLIC -> Icons.Filled.Public
                            CallMode.GROUP_PRIVATE -> Icons.Filled.Lock
                        },
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = room.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusLabel = when {
                        phase == CallConnectionPhase.RINGING -> "Ringing..."
                        phase == CallConnectionPhase.CONNECTING -> "Connecting..."
                        else -> formatElapsed(elapsedSeconds)
                    }
                    Text(
                        text = statusLabel,
                        color = Color(0xFF34D399),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("·", color = Color.White.copy(alpha = 0.55f))
                    Text(
                        text = "${room.participantCount}/${room.maxParticipants}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text("·", color = Color.White.copy(alpha = 0.55f))
                    Text(
                        text = providerLabel,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (room.mode == CallMode.GROUP_PRIVATE && room.roomCode.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCopyCode(room.roomCode) }
                        .testTag("active_call_copy_code_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = room.roomCode,
                            color = Color(0xFF6EE7B7),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy private room code",
                            tint = Color(0xFF6EE7B7),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingStageCard(
    room: CallRoomSummary,
    phase: CallConnectionPhase,
    tileBg: Color,
    emerald: Color,
    onCopyCode: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = tileBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(emerald.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                if (phase == CallConnectionPhase.RINGING || phase == CallConnectionPhase.CONNECTING) {
                    CircularProgressIndicator(
                        color = emerald,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Icon(
                    imageVector = if (room.mode == CallMode.DIRECT) Icons.Filled.PhoneInTalk else Icons.Filled.Groups,
                    contentDescription = null,
                    tint = emerald,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = when (room.mode) {
                    CallMode.DIRECT -> "Calling ${room.targetName.ifBlank { room.title }}..."
                    CallMode.GROUP_PRIVATE -> "Private Room Ready"
                    CallMode.GROUP_PUBLIC -> "Waiting for Learners to Join"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when (room.mode) {
                    CallMode.DIRECT -> "Waiting for your classmate to accept the 1-on-1 call."
                    CallMode.GROUP_PRIVATE -> "Share the private room code below so friends can join your English video session."
                    CallMode.GROUP_PUBLIC -> "Your public room is visible in the lobby. Anyone practicing English can join."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f)
            )

            if (room.mode == CallMode.GROUP_PRIVATE && room.roomCode.isNotBlank()) {
                Button(
                    onClick = { onCopyCode(room.roomCode) },
                    colors = ButtonDefaults.buttonColors(containerColor = emerald, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("waiting_copy_private_code_button")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Room Code: ${room.roomCode}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RemoteParticipantTile(
    participant: CallParticipantSummary,
    hasVideoStream: Boolean,
    streamVersion: Int,
    engine: CallEngine?,
    tileBg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tileBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier.clip(RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasVideoStream && engine != null && streamVersion >= 0) {
                engine.RemoteVideoSurface(
                    userId = participant.userId,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = participant.name.take(2).uppercase(),
                            color = Color(0xFF6EE7B7),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (participant.videoEnabled) "Connecting video..." else "Camera off",
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.56f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = participant.name,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (participant.audioMuted) {
                        Icon(
                            imageVector = Icons.Filled.MicOff,
                            contentDescription = "${participant.name} muted",
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveCallControlsBar(
    audioMuted: Boolean,
    videoEnabled: Boolean,
    speakerOn: Boolean,
    canSwitchCamera: Boolean,
    isHostGroup: Boolean,
    onToggleMic: () -> Unit,
    onToggleVideo: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF111827).copy(alpha = 0.92f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallControlRoundButton(
                icon = if (audioMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (audioMuted) "Unmute" else "Mute",
                active = !audioMuted,
                testTag = "call_toggle_mic_button",
                onClick = onToggleMic
            )
            CallControlRoundButton(
                icon = if (videoEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                label = if (videoEnabled) "Video On" else "Video Off",
                active = videoEnabled,
                testTag = "call_toggle_video_button",
                onClick = onToggleVideo
            )
            CallControlRoundButton(
                icon = Icons.Filled.Cameraswitch,
                label = "Flip",
                active = canSwitchCamera,
                enabled = canSwitchCamera,
                testTag = "call_switch_camera_button",
                onClick = onSwitchCamera
            )
            CallControlRoundButton(
                icon = if (speakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                label = if (speakerOn) "Speaker" else "Earpiece",
                active = speakerOn,
                testTag = "call_toggle_speaker_button",
                onClick = onToggleSpeaker
            )

            Surface(
                shape = CircleShape,
                color = Color(0xFFDC2626),
                modifier = Modifier
                    .size(56.dp)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(onClick = onEndCall)
                    .testTag("call_end_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = if (isHostGroup) "Leave or end call" else "End call",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControlRoundButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (active) Color.White.copy(alpha = 0.16f) else Color(0xFF334155),
            modifier = Modifier
                .size(48.dp)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
        ) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.testTag(testTag)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp
        )
    }
}

private fun formatElapsed(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}

private fun copyRoomCode(context: Context, code: String) {
    if (code.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("Private Video Room Code", code))
    Toast.makeText(context, "Copied room code: $code", Toast.LENGTH_SHORT).show()
}
