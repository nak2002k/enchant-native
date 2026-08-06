package org.enchant.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.enchant.core.push.BatteryOptimizationHelper

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
    SettingsScaffold(title = "Notifications", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EnchantSpacing.lg,
                end = EnchantSpacing.lg,
                top = EnchantSpacing.sm,
                bottom = EnchantSpacing.xxxl,
            ),
        ) {
            item {
                EnchantGroupedCard {
                    SignalSettingsSwitchRow(
                        title = "Notifications",
                        label = "Master toggle for all notifications",
                        checked = masterEnabled,
                        onCheckedChange = onMasterToggle,
                    )
                }
            }

            if (masterEnabled) {
                item { EnchantSectionHeader("Messages") }
                item {
                    EnchantGroupedCard {
                        SignalSettingsSwitchRow(
                            title = "Message notifications",
                            checked = messageNotifications,
                            onCheckedChange = onMessageNotificationsChange,
                        )
                        EnchantDivider(inset = 56.dp)
                        SignalSettingsSwitchRow(
                            title = "Show preview",
                            checked = showPreview,
                            onCheckedChange = onShowPreviewChange,
                        )
                    }
                }

                item { EnchantSectionHeader("Do Not Disturb") }
                item {
                    EnchantGroupedCard {
                        TimeFieldRow(
                            label = "Start time",
                            value = dndStartTime,
                            onValueChange = onDndStartTimeChange,
                        )
                        EnchantDivider(inset = 56.dp)
                        TimeFieldRow(
                            label = "End time",
                            value = dndEndTime,
                            onValueChange = onDndEndTimeChange,
                        )
                        EnchantDivider(inset = 56.dp)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                        ) {
                            Text(
                                text = "Repeat on",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(EnchantSpacing.sm))
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

                item {
                    val context = LocalContext.current
                    if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = EnchantSpacing.lg)
                                .clip(RoundedCornerShape(EnchantRadii.medium)),
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
}

@Composable
private fun TimeFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(100.dp),
            singleLine = true,
            placeholder = { Text("HH:mm") }
        )
    }
}
