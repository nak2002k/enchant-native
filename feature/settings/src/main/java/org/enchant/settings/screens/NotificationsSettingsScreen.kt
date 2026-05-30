package org.enchant.settings.screens

import android.content.Context
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
import org.enchant.core.push.BatteryOptimizationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(
    masterEnabled: Boolean,
    messageNotifications: Boolean,
    showPreview: Boolean,
    dndStartTime: String,
    dndEndTime: String,
    dndDaysOfWeek: Set<Int>,
    onMasterToggle: (Boolean) -> Unit,
    onMessageNotificationsChange: (Boolean) -> Unit,
    onShowPreviewChange: (Boolean) -> Unit,
    onDndStartTimeChange: (String) -> Unit,
    onDndEndTimeChange: (String) -> Unit,
    onDndDaysChange: (Set<Int>) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
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
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Notifications", style = MaterialTheme.typography.titleSmall)
                        Text("Master toggle for all notifications",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = masterEnabled, onCheckedChange = onMasterToggle)
                }
            }

            if (masterEnabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Message Notifications",
                            style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Message notifications",
                                style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = messageNotifications,
                                onCheckedChange = onMessageNotificationsChange
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Show preview", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = showPreview,
                                onCheckedChange = onShowPreviewChange
                            )
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Do Not Disturb Schedule",
                            style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Start time", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                value = dndStartTime,
                                onValueChange = onDndStartTimeChange,
                                modifier = Modifier.width(100.dp),
                                singleLine = true,
                                placeholder = { Text("HH:mm") }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("End time", style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                value = dndEndTime,
                                onValueChange = onDndEndTimeChange,
                                modifier = Modifier.width(100.dp),
                                singleLine = true,
                                placeholder = { Text("HH:mm") }
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Repeat on", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            dayNames.forEachIndexed { index, name ->
                                FilterChip(
                                    selected = index in dndDaysOfWeek,
                                    onClick = {
                                        val newDays = if (index in dndDaysOfWeek) {
                                            dndDaysOfWeek - index
                                        } else {
                                            dndDaysOfWeek + index
                                        }
                                        onDndDaysChange(newDays)
                                    },
                                    label = { Text(name) },
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(LocalContext.current)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Battery Optimization",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Notifications may be delayed. Disable battery optimization for reliable message delivery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (BatteryOptimizationHelper.isXiaomi() ||
                                        BatteryOptimizationHelper.isHuawei() ||
                                        BatteryOptimizationHelper.isOnePlus()) {
                                        BatteryOptimizationHelper.showAutoStartSettings(LocalContext.current)
                                    } else {
                                        BatteryOptimizationHelper.requestDisableBatteryOptimization(LocalContext.current)
                                    }
                                }
                            ) {
                                Text("Open Settings")
                            }
                            OutlinedButton(
                                onClick = { BatteryOptimizationHelper.requestDisableBatteryOptimization(LocalContext.current) }
                            ) {
                                Text("Disable Optimization")
                            }
                        }
                    }
                }
            }
        }
    }
}
