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
import org.enchant.core.model.DisappearTimerPresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsSettingsScreen(
    defaultDisappearingTimer: Int,
    autoDownloadWifi: Boolean,
    autoDownloadCellular: Boolean,
    onDisappearingTimerChange: (Int) -> Unit,
    onAutoDownloadWifiChange: (Boolean) -> Unit,
    onAutoDownloadCellularChange: (Boolean) -> Unit,
    onBackupSettings: () -> Unit,
    onBack: () -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
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
            Text(
                "Default disappearing timer",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
            var expanded by remember { mutableStateOf(false) }
            val selectedLabel = DisappearTimerPresets.SETTINGS_OPTIONS.find { it.seconds == defaultDisappearingTimer }?.label ?: "Off"
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DisappearTimerPresets.SETTINGS_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onDisappearingTimerChange(option.seconds)
                                expanded = false
                            }
                        )
                    }
                }
            }

            SignalSettingsDivider()

            SignalSettingsRow(
                icon = Icons.Default.Backup,
                title = "Backup",
                label = "Manage chat backups",
                onClick = onBackupSettings
            )

            SignalSettingsDivider()

            SignalSettingsSwitchRow(
                title = "Auto-download on Wi-Fi",
                checked = autoDownloadWifi,
                onCheckedChange = onAutoDownloadWifiChange
            )
            SignalSettingsSwitchRow(
                title = "Auto-download on cellular",
                checked = autoDownloadCellular,
                onCheckedChange = onAutoDownloadCellularChange
            )
        }
    }
}
