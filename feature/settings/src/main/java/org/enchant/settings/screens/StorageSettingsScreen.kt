package org.enchant.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.enchant.settings.StorageInfo

@Composable
fun StorageSettingsScreen(
    storageInfo: StorageInfo?,
    isProcessing: Boolean,
    messageRetentionDays: Int = 0,
    onClearCache: () -> Unit,
    onRetentionChange: (Int) -> Unit = {},
    onTrimMessages: () -> Unit = {},
    onBack: () -> Unit
) {
    val retentionOptions = listOf(
        0 to "Keep all",
        30 to "30 days",
        90 to "3 months",
        180 to "6 months",
        365 to "1 year"
    )
    var showTrimDialog by remember { mutableStateOf(false) }

    SettingsScaffold(title = "Storage", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EnchantSpacing.lg,
                end = EnchantSpacing.lg,
                top = EnchantSpacing.sm,
                bottom = EnchantSpacing.xxxl,
            ),
        ) {
            item { EnchantSectionHeader("Storage Usage") }
            item {
                EnchantGroupedCard {
                    if (storageInfo != null) {
                        val used = storageInfo.messagesBytes + storageInfo.mediaBytes + storageInfo.cacheBytes
                        val totalDisplay = if (storageInfo.totalBytes > 0)
                            " / ${formatBytes(storageInfo.totalBytes)}" else ""

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                        ) {
                            Text(
                                "${formatBytes(used)}$totalDisplay used",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(EnchantSpacing.md))
                            StorageBar(
                                label = "Messages",
                                bytes = storageInfo.messagesBytes,
                                total = used,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(EnchantSpacing.sm))
                            StorageBar(
                                label = "Media",
                                bytes = storageInfo.mediaBytes,
                                total = used,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.height(EnchantSpacing.sm))
                            StorageBar(
                                label = "Cache",
                                bytes = storageInfo.cacheBytes,
                                total = used,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    } else {
                        Text(
                            "Loading storage info...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                        )
                    }
                }
            }

            item { EnchantSectionHeader("Cache") }
            item {
                EnchantGroupedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Clear cache",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Remove temporary files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            FilledTonalButton(onClick = onClearCache) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            item { EnchantSectionHeader("Message Retention") }
            item {
                EnchantGroupedCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                    ) {
                        Text(
                            "Automatically delete messages older than the selected period.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(EnchantSpacing.sm))
                        retentionOptions.forEach { (days, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onRetentionChange(days) },
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = messageRetentionDays == days,
                                    onClick = { onRetentionChange(days) }
                                )
                                Spacer(Modifier.width(EnchantSpacing.sm))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            if (messageRetentionDays > 0) {
                item {
                    Spacer(Modifier.height(EnchantSpacing.md))
                    OutlinedButton(
                        onClick = { showTrimDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(EnchantSpacing.sm))
                        Text("Trim Now")
                    }
                }
            }
        }
    }

    if (showTrimDialog) {
        AlertDialog(
            onDismissRequest = { showTrimDialog = false },
            title = { Text("Trim Messages") },
            text = {
                Text("This will permanently delete all messages older than $messageRetentionDays days. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showTrimDialog = false
                    onTrimMessages()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrimDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StorageBar(
    label: String,
    bytes: Long,
    total: Long,
    color: Color
) {
    val fraction = if (total > 0) bytes.toFloat() / total else 0f
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                formatBytes(bytes), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { fraction },
            color = color,
            trackColor = color.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1073741824 -> "${"%.1f".format(bytes / 1073741824.0)} GB"
        bytes >= 1048576 -> "${"%.1f".format(bytes / 1048576.0)} MB"
        bytes >= 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}
