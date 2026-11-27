package com.example.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val PERMISSION_PREFS = "english_ai_permission_state"

@Composable
fun PermissionPromptDialog(
    title: String,
    message: String,
    icon: ImageVector,
    onAllow: () -> Unit,
    onCancel: () -> Unit,
    confirmText: String = "Open Settings"
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onAllow,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("permission_allow_button")
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("permission_cancel_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun hasRequestedPermissionBefore(context: Context, permission: String): Boolean {
    return context.applicationContext
        .getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .getBoolean("req_$permission", false)
}

fun markPermissionRequested(context: Context, permission: String) {
    context.applicationContext
        .getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("req_$permission", true)
        .apply()
}

fun shouldOpenAppSettings(context: Context, permission: String, deniedBefore: Boolean): Boolean {
    val requestedBefore = deniedBefore || hasRequestedPermissionBefore(context, permission)
    if (!requestedBefore) return false
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
        return false
    }
    val activity = context.findActivity() ?: return false
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

fun isPermissionPermanentlyDenied(context: Context, permission: String): Boolean {
    return shouldOpenAppSettings(context, permission, deniedBefore = false)
}

fun requestSystemPermission(
    context: Context,
    permission: String,
    launcher: ManagedActivityResultLauncher<String, Boolean>,
    onAlreadyGranted: () -> Unit,
    onPermanentlyDenied: () -> Unit
) {
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    if (granted) {
        onAlreadyGranted()
        return
    }
    if (isPermissionPermanentlyDenied(context, permission)) {
        onPermanentlyDenied()
        return
    }
    markPermissionRequested(context, permission)
    launcher.launch(permission)
}

fun openAppPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
