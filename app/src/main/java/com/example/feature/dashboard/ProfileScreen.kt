package com.example.feature.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ui.AvatarImageHelper
import com.example.core.ui.PermissionPromptDialog
import com.example.core.ui.StatusBanner
import com.example.core.ui.StudentAvatarImage
import com.example.core.ui.openAppPermissionSettings
import com.example.core.ui.requestSystemPermission
import com.example.domain.model.LearningLevel
import com.example.domain.model.UserProfile
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    actionState: DashboardActionState,
    avatarUrl: String? = actionState.avatarUrl,
    fallbackEmail: String = "",
    onUploadAvatar: (Uri) -> Unit = {},
    onRemoveAvatar: () -> Unit = {},
    onSave: (String, LearningLevel, Int, Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onClearBanner: () -> Unit
) {
    val context = LocalContext.current
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

    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var showCameraSettingsDialog by remember { mutableStateOf(false) }
    var showCameraDeniedMessage by remember { mutableStateOf(false) }
    var confirmRemoveAvatar by remember { mutableStateOf(false) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val capturedUri = pendingCameraUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        pendingCameraUriString = null
        if (saved && capturedUri != null) {
            showCameraDeniedMessage = false
            onUploadAvatar(capturedUri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            showCameraDeniedMessage = false
            onUploadAvatar(uri)
        }
    }

    val launchCameraCapture = remember(context) {
        {
            runCatching {
                val (_, uri) = AvatarImageHelper.createTempAvatarCameraUri(context)
                pendingCameraUriString = uri.toString()
                takePictureLauncher.launch(uri)
            }
            Unit
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCameraDeniedMessage = false
            launchCameraCapture()
        } else {
            showCameraDeniedMessage = true
        }
    }

    val handleTakePhotoClick = {
        showPhotoSourceDialog = false
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            launchCameraCapture()
        } else {
            requestSystemPermission(
                context = context,
                permission = Manifest.permission.CAMERA,
                launcher = cameraPermissionLauncher,
                onAlreadyGranted = launchCameraCapture,
                onPermanentlyDenied = { showCameraSettingsDialog = true }
            )
        }
    }

    val handlePickPhotoClick = {
        showPhotoSourceDialog = false
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val isBusyOrUploading = actionState.isBusy || actionState.isUploadingAvatar
    val resolvedAvatarUrl = avatarUrl?.takeIf { it.isNotBlank() && it != "null" }
    val headerEmail = profile?.email?.takeIf { it.isNotBlank() } ?: fallbackEmail

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

                // Profile Photo / Avatar Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .testTag("profile_avatar_preview"),
                            contentAlignment = Alignment.Center
                        ) {
                            StudentAvatarImage(
                                photoUrl = resolvedAvatarUrl,
                                contentDescription = "Student profile photo",
                                modifier = Modifier
                                    .size(104.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        shape = CircleShape
                                    )
                                    .clickable(enabled = !isBusyOrUploading) {
                                        showPhotoSourceDialog = true
                                    }
                            )

                            if (actionState.isUploadingAvatar) {
                                Box(
                                    modifier = Modifier
                                        .size(104.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(30.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 3.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(enabled = !isBusyOrUploading) {
                                        showPhotoSourceDialog = true
                                    }
                                    .testTag("profile_avatar_edit_badge")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = "Change profile photo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = displayName.ifBlank { profile?.displayName ?: "Learner" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (headerEmail.isNotBlank()) {
                                Text(
                                    text = headerEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (actionState.isUploadingAvatar) {
                                    "Uploading photo..."
                                } else {
                                    "Take a selfie or choose a photo for your student avatar"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showCameraDeniedMessage) {
                            Text(
                                text = "Camera permission is needed to take a photo. You can allow it when prompted or choose a photo from your library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = handleTakePhotoClick,
                                enabled = !isBusyOrUploading,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("profile_avatar_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Take Photo")
                            }

                            OutlinedButton(
                                onClick = handlePickPhotoClick,
                                enabled = !isBusyOrUploading,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("profile_avatar_gallery_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload Photo")
                            }

                            if (resolvedAvatarUrl != null) {
                                TextButton(
                                    onClick = { confirmRemoveAvatar = true },
                                    enabled = !isBusyOrUploading,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("profile_avatar_remove_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Remove", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Preferences Card
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
                            enabled = !isBusyOrUploading,
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
                                    enabled = !isBusyOrUploading
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
                                    enabled = !isBusyOrUploading
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
                                enabled = !isBusyOrUploading,
                                modifier = Modifier.testTag("profile_bangla_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                onSave(displayName, level, goalMinutes, banglaExplanations)
                            },
                            enabled = !isBusyOrUploading,
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

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Update Profile Photo") },
            text = { Text("Choose how you want to set your student profile avatar.") },
            confirmButton = {
                Button(
                    onClick = handleTakePhotoClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("profile_dialog_camera_button")
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Take Photo")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = handlePickPhotoClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("profile_dialog_gallery_button")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Choose Photo")
                }
            }
        )
    }

    if (showCameraSettingsDialog) {
        PermissionPromptDialog(
            title = "Camera Permission Needed",
            message = "Camera access was previously denied. Open Settings to enable camera permission, or upload a photo from your library instead.",
            icon = Icons.Filled.PhotoCamera,
            onAllow = {
                showCameraSettingsDialog = false
                openAppPermissionSettings(context)
            },
            onCancel = { showCameraSettingsDialog = false }
        )
    }

    if (confirmRemoveAvatar) {
        AlertDialog(
            onDismissRequest = { confirmRemoveAvatar = false },
            title = { Text("Remove Profile Photo?") },
            text = { Text("Your avatar will revert to the default student illustration.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveAvatar = false
                        onRemoveAvatar()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("profile_confirm_remove_avatar_button")
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmRemoveAvatar = false },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
