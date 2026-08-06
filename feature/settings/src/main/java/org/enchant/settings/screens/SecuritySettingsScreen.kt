package org.enchant.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import org.enchant.core.base.SecurePreferences

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

    SettingsScaffold(title = "Security", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EnchantSpacing.lg,
                end = EnchantSpacing.lg,
                top = EnchantSpacing.sm,
                bottom = EnchantSpacing.xxxl,
            ),
        ) {
            item { EnchantSectionHeader("App Lock") }
            item {
                EnchantGroupedCard {
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
                        EnchantDivider(inset = 56.dp)
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
                }
            }

            item { EnchantSectionHeader("Verification") }
            item {
                EnchantGroupedCard {
                    Column {
                        SettingsRow(
                            icon = Icons.Rounded.Shield,
                            iconBackground = SettingsIconTints.Teal,
                            title = "Safety number",
                            subtitle = SecurePreferences.getString("safety_number", "UNVERIFIED") ?: "UNVERIFIED",
                        )
                        Text(
                            "Verify with contacts to ensure secure communication",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.xs)
                        )
                        EnchantDivider(inset = 56.dp)
                        SettingsRow(
                            icon = Icons.Rounded.Lock,
                            iconBackground = SettingsIconTints.DarkGray,
                            title = "Two-step verification",
                            subtitle = if (twoStepEnabled) "Enabled" else "Not set up",
                            onClick = {
                                twoStepMode = if (twoStepEnabled) "disable" else "setup"
                                twoStepPin = ""
                                showTwoStepDialog = true
                            }
                        )
                    }
                }
            }
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
