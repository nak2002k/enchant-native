package org.enchant.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = DisappearTimerPresets.SETTINGS_OPTIONS.find { it.seconds == defaultDisappearingTimer }?.label ?: "Off"

    SettingsScaffold(title = "Chats", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EnchantSpacing.lg,
                end = EnchantSpacing.lg,
                top = EnchantSpacing.sm,
                bottom = EnchantSpacing.xxxl,
            ),
        ) {
            item { EnchantSectionHeader("Default Timer") }
            item {
                EnchantGroupedCard {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { expanded = !expanded },
                                )
                                .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Default disappearing timer",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = selectedLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(EnchantSpacing.sm))
                            Icon(
                                Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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

            item { EnchantSectionHeader("Backup") }
            item {
                EnchantGroupedCard {
                    SettingsRow(
                        icon = Icons.Rounded.Backup,
                        iconBackground = SettingsIconTints.Brown,
                        title = "Backup",
                        subtitle = "Manage chat backups",
                        onClick = onBackupSettings,
                    )
                }
            }

            item { EnchantSectionHeader("Auto-Download") }
            item {
                EnchantGroupedCard {
                    SignalSettingsSwitchRow(
                        title = "Auto-download on Wi-Fi",
                        checked = autoDownloadWifi,
                        onCheckedChange = onAutoDownloadWifiChange,
                    )
                    EnchantDivider(inset = 56.dp)
                    SignalSettingsSwitchRow(
                        title = "Auto-download on cellular",
                        checked = autoDownloadCellular,
                        onCheckedChange = onAutoDownloadCellularChange,
                    )
                }
            }
        }
    }
}
