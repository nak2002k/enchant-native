package org.enchant.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import org.enchant.core.base.SecurePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    twoStepEnabled: Boolean = false,
    onSetupTwoStep: (String) -> Unit = {},
    onDisableTwoStep: (String) -> Unit = {},
    onBack: () -> Unit
) {
    var appLockEnabled by remember { mutableStateOf(SecurePreferences.getBoolean("applock.enabled", false)) }
    val biometricAvailable = SecurePreferences.getBoolean("applock.biometric", false)
    var useBiometric by remember { mutableStateOf(SecurePreferences.getBoolean("applock.biometric", false)) }
    val context = LocalContext.current
    val canUseBiometric = remember {
        try {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) { false }
    }
    var showTwoStepDialog by remember { mutableStateOf(false) }
    var twoStepPin by remember { mutableStateOf("") }
    var twoStepMode by remember { mutableStateOf("setup") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SignalSettingsSwitchRow(
                title = "PIN lock",
                label = if (canUseBiometric && useBiometric) "Biometric unlock active" else "Lock the app with a PIN",
                checked = appLockEnabled,
                onCheckedChange = { enabled ->
                    appLockEnabled = enabled
                    SecurePreferences.putBoolean("applock.enabled", enabled)
                    if (!enabled) {
                        SecurePreferences.remove("applock.pin_hash")
                        SecurePreferences.putBoolean("applock.biometric", false)
                        useBiometric = false
                    }
                }
            )
            if (appLockEnabled && canUseBiometric) {
                SignalSettingsSwitchRow(
                    title = "Use biometric unlock",
                    label = "Unlock with fingerprint or face",
                    checked = useBiometric,
                    onCheckedChange = { enabled ->
                        useBiometric = enabled
                        SecurePreferences.putBoolean("applock.biometric", enabled)
                    }
                )
            }

            SignalSettingsDivider()

            SignalSettingsRow(
                icon = Icons.Default.Shield,
                title = "Safety number",
                label = SecurePreferences.getString("safety_number", "UNVERIFIED") ?: "UNVERIFIED",
                showChevron = false,
                onClick = null
            )
            Text(
                "Verify with contacts to ensure secure communication",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            SignalSettingsDivider()

            SignalSettingsRow(
                icon = Icons.Default.Lock,
                title = "Two-step verification",
                label = if (twoStepEnabled) "Enabled" else "Not set up",
                onClick = {
                    twoStepMode = if (twoStepEnabled) "disable" else "setup"
                    twoStepPin = ""
                    showTwoStepDialog = true
                }
            )
        }
    }

    if (showTwoStepDialog) {
        AlertDialog(
            onDismissRequest = { showTwoStepDialog = false },
            title = {
                Text(if (twoStepMode == "setup") "Setup Two-Step Verification" else "Disable Two-Step Verification")
            },
            text = {
                Column {
                    Text(
                        if (twoStepMode == "setup")
                            "Enter a 4-digit PIN to protect your account registration."
                        else
                            "Enter your PIN to disable two-step verification.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = twoStepPin,
                        onValueChange = { if (it.length <= 4) twoStepPin = it },
                        label = { Text("PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTwoStepDialog = false
                        if (twoStepMode == "setup") onSetupTwoStep(twoStepPin)
                        else onDisableTwoStep(twoStepPin)
                    },
                    enabled = twoStepPin.length == 4
                ) {
                    Text(if (twoStepMode == "setup") "Enable" else "Disable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTwoStepDialog = false }) { Text("Cancel") }
            }
        )
    }
}
