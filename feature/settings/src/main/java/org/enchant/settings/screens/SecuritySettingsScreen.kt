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
import androidx.compose.ui.unit.dp
import org.enchant.core.base.SecurePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onSetupTwoStep: () -> Unit,
    onBack: () -> Unit
) {
    var appLockEnabled by remember { mutableStateOf(SecurePreferences.getBoolean("applock.enabled", false)) }
    val biometricAvailable = SecurePreferences.getBoolean("applock.biometric", false)
    val safetyNumber = SecurePreferences.getString("safety_number", "UNVERIFIED") ?: "UNVERIFIED"
    val twoStepEnabled = SecurePreferences.getBoolean("twostep.enabled", false)

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("App Lock", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("PIN lock", style = MaterialTheme.typography.bodyMedium)
                            if (biometricAvailable) {
                                Text("Biometric unlock available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                appLockEnabled = enabled
                                SecurePreferences.putBoolean("applock.enabled", enabled)
                                if (!enabled) {
                                    SecurePreferences.remove("applock.pin_hash")
                                    SecurePreferences.remove("applock.biometric")
                                }
                            }
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Safety Number", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(safetyNumber, style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Text("Verify with contacts to ensure secure communication",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Two-Step Verification", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (twoStepEnabled) "Enabled" else "Not set up",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (twoStepEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onSetupTwoStep) {
                            Icon(Icons.Default.Edit, "Setup")
                        }
                    }
                }
            }
        }
    }
}
