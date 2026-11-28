package com.example.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.StatusBanner
import com.example.domain.model.LearningLevel
import com.example.domain.model.UserProfile
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    actionState: DashboardActionState,
    onSave: (String, LearningLevel, Int, Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onClearBanner: () -> Unit
) {
    var displayName by rememberSaveable(profile?.displayName) {
        mutableStateOf(profile?.displayName.orEmpty())
    }
    var level by rememberSaveable(profile?.learningLevel) {
        mutableStateOf(profile?.safeLevel ?: LearningLevel.Beginner)
    }
    var goalMinutes by rememberSaveable(profile?.dailyGoalMinutes) {
        mutableIntStateOf((profile?.dailyGoalMinutes ?: 20L).toInt())
    }
    var banglaExplanations by rememberSaveable(profile?.banglaExplanations) {
        mutableStateOf(profile?.banglaExplanations ?: true)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Profile & Preferences") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusBanner(
                    message = actionState.bannerMessage,
                    tone = actionState.bannerTone
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = {
                                displayName = it
                                if (actionState.bannerMessage != null) onClearBanner()
                            },
                            label = { Text("Display Name") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            enabled = !actionState.isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input")
                        )

                        Text(
                            text = "Learning Level",
                            style = MaterialTheme.typography.titleMedium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LearningLevel.entries.forEach { option ->
                                FilterChip(
                                    selected = level == option,
                                    onClick = { level = option },
                                    label = { Text(option.displayName) },
                                    enabled = !actionState.isBusy
                                )
                            }
                        }

                        Text(
                            text = "Daily Study Target",
                            style = MaterialTheme.typography.titleMedium
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15, 20, 30, 45, 60).forEach { mins ->
                                FilterChip(
                                    selected = goalMinutes == mins,
                                    onClick = { goalMinutes = mins },
                                    label = { Text("$mins min") },
                                    enabled = !actionState.isBusy
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bangla Explanations",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Show Bangla grammar and mistake explanations alongside English",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = banglaExplanations,
                                onCheckedChange = { banglaExplanations = it },
                                enabled = !actionState.isBusy,
                                modifier = Modifier.testTag("profile_bangla_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                onSave(displayName, level, goalMinutes, banglaExplanations)
                            },
                            enabled = !actionState.isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .height(50.dp)
                                .testTag("profile_save_button")
                        ) {
                            if (actionState.isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Save Preferences")
                            }
                        }
                    }
                }

                val context = LocalContext.current
                val themeMode by AppThemeManager.observeThemeMode(context).collectAsStateWithLifecycle()

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_theme_mode_selector")
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Choose how the app looks across student screens. Admin tools stay in dark mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                val modeIcon = when (mode) {
                                    AppThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                    AppThemeMode.LIGHT -> Icons.Filled.LightMode
                                    AppThemeMode.DARK -> Icons.Filled.DarkMode
                                }
                                FilterChip(
                                    selected = themeMode == mode,
                                    onClick = { AppThemeManager.setThemeMode(context, mode) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.testTag("theme_chip_${mode.storageKey}")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
