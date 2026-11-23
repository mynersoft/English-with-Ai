package com.example.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.core.ui.PasswordStrengthIndicator
import com.example.core.ui.StatusBanner
import com.example.core.ui.launchGoogleCredentialFlow
import com.example.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    email: String,
    isEmailVerified: Boolean,
    hasPasswordProvider: Boolean,
    actionState: DashboardActionState,
    onChangePassword: (String, String, String) -> Unit,
    onSendResetEmail: () -> Unit,
    onDeactivateAccount: () -> Unit,
    onDeleteWithPassword: (String) -> Unit,
    onDeleteWithGoogleToken: (String) -> Unit,
    onAuthError: (String) -> Unit,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit,
    onClearBanner: () -> Unit
) {
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmNewPassword by rememberSaveable { mutableStateOf("") }
    var showPasswords by rememberSaveable { mutableStateOf(false) }

    var showDeactivateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmPassword by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Security & Account") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("security_back_button")
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

                // Identity & Verification Status Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Account Identity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = email.ifBlank { "Signed-in account" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = if (isEmailVerified) SuccessGreen else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isEmailVerified) "Email verified" else "Email pending verification",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isEmailVerified) SuccessGreen else MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                // Password Management Card (for Email/Password accounts)
                if (hasPasswordProvider) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Change Password",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Requires re-entering your current password for verification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = currentPassword,
                                onValueChange = {
                                    currentPassword = it
                                    if (actionState.bannerMessage != null) onClearBanner()
                                },
                                label = { Text("Current Password") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { showPasswords = !showPasswords },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = if (showPasswords) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (showPasswords) "Hide passwords" else "Show passwords"
                                        )
                                    }
                                },
                                visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !actionState.isBusy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("security_current_password_input")
                            )

                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = {
                                    newPassword = it
                                    if (actionState.bannerMessage != null) onClearBanner()
                                },
                                label = { Text("New Password") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !actionState.isBusy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("security_new_password_input")
                            )

                            if (newPassword.isNotEmpty()) {
                                PasswordStrengthIndicator(password = newPassword)
                            }

                            OutlinedTextField(
                                value = confirmNewPassword,
                                onValueChange = {
                                    confirmNewPassword = it
                                    if (actionState.bannerMessage != null) onClearBanner()
                                },
                                label = { Text("Confirm New Password") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                enabled = !actionState.isBusy,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("security_confirm_password_input")
                            )

                            Button(
                                onClick = {
                                    onChangePassword(currentPassword, newPassword, confirmNewPassword)
                                },
                                enabled = !actionState.isBusy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .height(48.dp)
                                    .testTag("security_update_password_button")
                            ) {
                                if (actionState.isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Update Password")
                                }
                            }

                            OutlinedButton(
                                onClick = onSendResetEmail,
                                enabled = !actionState.isBusy,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .height(48.dp)
                                    .testTag("security_send_reset_button")
                            ) {
                                Text("Send Password Recovery Email Instead")
                            }
                        }
                    }
                }

                // Account Lifecycle Actions
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Account Lifecycle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedButton(
                            onClick = onSignOut,
                            enabled = !actionState.isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .height(48.dp)
                                .testTag("security_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Sign Out")
                        }

                        OutlinedButton(
                            onClick = { showDeactivateDialog = true },
                            enabled = !actionState.isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .height(48.dp)
                                .testTag("security_deactivate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PauseCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Deactivate Account")
                        }

                        Button(
                            onClick = { showDeleteDialog = true },
                            enabled = !actionState.isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .height(48.dp)
                                .testTag("security_delete_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Permanently Delete Account")
                        }
                    }
                }
            }
        }
    }

    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = { Text("Deactivate Account?") },
            text = {
                Text("Your learning progress and profile are preserved safely in the cloud, and you can reactivate anytime by signing back in.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeactivateDialog = false
                        onDeactivateAccount()
                    }
                ) {
                    Text("Deactivate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deleteConfirmPassword = ""
            },
            title = { Text("Delete Account Permanently") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (hasPasswordProvider) {
                            "This permanently removes your profile, session records, and Firebase login. Enter your password to confirm."
                        } else {
                            "This permanently removes your profile, session records, and Firebase login. You will be asked to re-verify with Google."
                        }
                    )
                    if (hasPasswordProvider) {
                        OutlinedTextField(
                            value = deleteConfirmPassword,
                            onValueChange = { deleteConfirmPassword = it },
                            label = { Text("Confirm Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    onClick = {
                        showDeleteDialog = false
                        if (hasPasswordProvider) {
                            val pw = deleteConfirmPassword
                            deleteConfirmPassword = ""
                            onDeleteWithPassword(pw)
                        } else {
                            launchGoogleCredentialFlow(
                                context = context,
                                credentialManager = credentialManager,
                                scope = scope,
                                onTokenReceived = { token -> onDeleteWithGoogleToken(token) },
                                onError = { msg -> onAuthError(msg) },
                                onCancelled = { /* User cancelled re-auth */ }
                            )
                        }
                    }
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        deleteConfirmPassword = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
