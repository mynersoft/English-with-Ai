package com.example.feature.reading.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.PermissionPromptDialog
import com.example.core.ui.openAppPermissionSettings
import com.example.core.ui.shouldOpenAppSettings
import com.example.domain.model.ReadingAccent
import com.example.domain.model.ReadingCefrLevel
import com.example.feature.reading.ReadingSettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingSettingsScreen(
    viewModel: ReadingSettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pal = readingPalette()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showNotifPermissionDialog by remember { mutableStateOf(false) }
    var notifDeniedOnce by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateDraft { it.copy(reminderEnabled = granted) }
        if (granted) {
            notifDeniedOnce = false
        } else {
            notifDeniedOnce = true
            showNotifPermissionDialog = true
        }
    }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = pal.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ReadingTopBar(
                title = "Reading Settings",
                subtitle = "Audio voice, accent, CEFR level & reminder",
                onBack = onBack
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (uiState.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = pal.primary)
                }
            } else {
                val s = uiState.settings
                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)
                val goals = listOf(5, 10, 15, 20, 30, 45, 60)
                val times = listOf("07:00", "09:00", "13:00", "18:00", "20:00", "21:30")

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (!uiState.error.isNullOrBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = pal.missingBg,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(uiState.error.orEmpty(), color = pal.missing, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }

                    // Preferred CEFR Level
                    item {
                        SettingsSectionCard(title = "Preferred CEFR Level") {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReadingCefrLevel.entries.forEach { lvl ->
                                    FilterChip(
                                        selected = s.preferredLevel == lvl.code,
                                        onClick = { viewModel.updateDraft { it.copy(preferredLevel = lvl.code) } },
                                        label = { Text(lvl.code) }
                                    )
                                }
                            }
                        }
                    }

                    // Accent & Voice
                    item {
                        SettingsSectionCard(title = "Accent & Voice") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReadingAccent.entries.forEach { opt ->
                                    FilterChip(
                                        selected = s.accent == opt.code,
                                        onClick = { viewModel.updateDraft { it.copy(accent = opt.code, voiceName = "") } },
                                        label = { Text(opt.label) },
                                        modifier = Modifier.testTag("settings_accent_${opt.code}")
                                    )
                                }
                            }

                            if (!uiState.accentStatusNote.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = pal.primaryContainer.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = uiState.accentStatusNote.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = pal.textPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = pal.border, modifier = Modifier.padding(vertical = 6.dp))
                            Text("Available Voices (${uiState.availableVoices.size})", style = MaterialTheme.typography.labelMedium, color = pal.textSecondary)

                            if (uiState.availableVoices.isEmpty()) {
                                Text(
                                    "Using default system voice for ${s.accent}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = pal.textSecondary
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    uiState.availableVoices.take(8).forEach { voice ->
                                        val selected = s.voiceName == voice.id
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (selected) pal.primaryContainer else pal.background,
                                            border = BorderStroke(1.dp, if (selected) pal.primary else pal.border),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.updateDraft { it.copy(voiceName = voice.id) } }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(voice.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = pal.textPrimary)
                                                    Text(voice.localeTag, style = MaterialTheme.typography.labelSmall, color = pal.textSecondary)
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(onClick = { viewModel.previewVoice(voice.id) }) {
                                                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Preview voice")
                                                    }
                                                    if (selected) {
                                                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = pal.primary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Default Speed & Bengali Translation
                    item {
                        SettingsSectionCard(title = "Playback & Translation") {
                            Text("Default Reading Speed", style = MaterialTheme.typography.labelMedium, color = pal.textSecondary)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                speeds.forEach { spd ->
                                    FilterChip(
                                        selected = kotlin.math.abs(s.defaultSpeed - spd) < 0.05f,
                                        onClick = { viewModel.updateDraft { it.copy(defaultSpeed = spd) } },
                                        label = { Text("${spd}x") }
                                    )
                                }
                            }

                            HorizontalDivider(color = pal.border, modifier = Modifier.padding(vertical = 6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Show Bengali Translation", fontWeight = FontWeight.SemiBold, color = pal.textPrimary)
                                    Text("Displays sentence & quiz Bengali explanations by default", style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                                }
                                Switch(
                                    checked = s.showBengali,
                                    onCheckedChange = { checked -> viewModel.updateDraft { it.copy(showBengali = checked) } },
                                    modifier = Modifier.testTag("settings_show_bengali_switch")
                                )
                            }
                        }
                    }

                    // Daily Goal & Reminder
                    item {
                        SettingsSectionCard(title = "Daily Goal & Reminder") {
                            Text("Daily Reading Goal (minutes)", style = MaterialTheme.typography.labelMedium, color = pal.textSecondary)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                goals.forEach { min ->
                                    FilterChip(
                                        selected = s.dailyGoalMinutes == min,
                                        onClick = { viewModel.updateDraft { it.copy(dailyGoalMinutes = min) } },
                                        label = { Text("${min}m") }
                                    )
                                }
                            }

                            HorizontalDivider(color = pal.border, modifier = Modifier.padding(vertical = 6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.NotificationsActive, null, tint = pal.primary)
                                    Column {
                                        Text("Daily Reading Reminder", fontWeight = FontWeight.SemiBold, color = pal.textPrimary)
                                        Text("Local daily notification at selected time", style = MaterialTheme.typography.bodySmall, color = pal.textSecondary)
                                    }
                                }
                                Switch(
                                    checked = s.reminderEnabled,
                                    onCheckedChange = { enable ->
                                        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (!granted) {
                                                showNotifPermissionDialog = true
                                            } else {
                                                viewModel.updateDraft { it.copy(reminderEnabled = true) }
                                            }
                                        } else {
                                            viewModel.updateDraft { it.copy(reminderEnabled = enable) }
                                        }
                                    },
                                    modifier = Modifier.testTag("settings_reminder_switch")
                                )
                            }

                            if (s.reminderEnabled) {
                                Text("Reminder Time", style = MaterialTheme.typography.labelMedium, color = pal.textSecondary)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    times.forEach { t ->
                                        FilterChip(
                                            selected = s.reminderTime == t,
                                            onClick = { viewModel.updateDraft { it.copy(reminderTime = t) } },
                                            label = { Text(t) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = viewModel::saveSettings,
                            enabled = !uiState.saving,
                            colors = ButtonDefaults.buttonColors(containerColor = pal.primary, contentColor = pal.onPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("save_reading_settings_button")
                        ) {
                            if (uiState.saving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = pal.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Save Reading Settings")
                        }
                    }
                }
            }
        }
    }

    if (showNotifPermissionDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionPromptDialog(
            title = "Notification Permission Required",
            message = "Allow notifications so we can send your daily reading reminder.",
            icon = Icons.Filled.NotificationsActive,
            onAllow = {
                showNotifPermissionDialog = false
                if (shouldOpenAppSettings(context, Manifest.permission.POST_NOTIFICATIONS, notifDeniedOnce)) {
                    openAppPermissionSettings(context)
                } else {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onCancel = { showNotifPermissionDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val pal = readingPalette()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = pal.card,
        border = BorderStroke(1.dp, pal.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = pal.textPrimary)
            content()
        }
    }
}
