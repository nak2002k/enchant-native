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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default Disappearing Timer",
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    var expanded by remember { mutableStateOf(false) }
                    val selectedLabel = DisappearTimerPresets.SETTINGS_OPTIONS.find { it.seconds == defaultDisappearingTimer }?.label ?: "Off"
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
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
                }
            }

            Card(onClick = onBackupSettings, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Backup, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Backup", style = MaterialTheme.typography.bodyMedium)
                        Text("Manage chat backups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Media Auto-Download",
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = autoDownloadWifi,
                            onCheckedChange = onAutoDownloadWifiChange)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cellular", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = autoDownloadCellular,
                            onCheckedChange = onAutoDownloadCellularChange)
                    }
                }
            }
        }
    }
}
