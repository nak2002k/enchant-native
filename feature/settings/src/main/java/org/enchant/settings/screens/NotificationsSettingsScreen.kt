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
        ) {
            SignalSettingsSwitchRow(
                title = "Notifications",
                label = "Master toggle for all notifications",
                checked = masterEnabled,
                onCheckedChange = onMasterToggle
            )

            if (masterEnabled) {
                SignalSettingsDivider()

                SignalSettingsSwitchRow(
                    title = "Message notifications",
                    checked = messageNotifications,
                    onCheckedChange = onMessageNotificationsChange
                )
                SignalSettingsSwitchRow(
                    title = "Show preview",
                    checked = showPreview,
                    onCheckedChange = onShowPreviewChange
                )

                SignalSettingsDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Start time", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = dndStartTime,
                        onValueChange = onDndStartTimeChange,
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        placeholder = { Text("HH:mm") }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("End time", style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = dndEndTime,
                        onValueChange = onDndEndTimeChange,
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        placeholder = { Text("HH:mm") }
                    )
                }

                Text(
                    "Repeat on",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
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

            val context = LocalContext.current
    if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
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
                                        BatteryOptimizationHelper.showAutoStartSettings(context)
                                    } else {
                                        BatteryOptimizationHelper.requestDisableBatteryOptimization(context)
                                    }
                                }
                            ) {
                                Text("Open Settings")
                            }
                            OutlinedButton(
                                onClick = { BatteryOptimizationHelper.requestDisableBatteryOptimization(context) }
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

}
