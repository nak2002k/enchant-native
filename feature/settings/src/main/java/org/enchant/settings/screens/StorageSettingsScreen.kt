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
import org.enchant.settings.StorageInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    storageInfo: StorageInfo?,
    isProcessing: Boolean,
    onClearCache: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage") },
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
                    Text("Storage Usage", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (storageInfo != null) {
                        val used = storageInfo.messagesBytes + storageInfo.mediaBytes + storageInfo.cacheBytes
                        val totalDisplay = if (storageInfo.totalBytes > 0)
                            " / ${formatBytes(storageInfo.totalBytes)}" else ""

                        Text("${formatBytes(used)}$totalDisplay used",
                            style = MaterialTheme.typography.bodyLarge)

                        Spacer(Modifier.height(12.dp))

                        StorageBar(
                            label = "Messages",
                            bytes = storageInfo.messagesBytes,
                            total = used,
                            color = MaterialTheme.colorScheme.primary
                        )
                        StorageBar(
                            label = "Media",
                            bytes = storageInfo.mediaBytes,
                            total = used,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        StorageBar(
                            label = "Cache",
                            bytes = storageInfo.cacheBytes,
                            total = used,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text("Loading storage info...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Clear Cache", style = MaterialTheme.typography.bodyMedium)
                        Text("Remove temporary files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp)
                    } else {
                        FilledTonalButton(onClick = onClearCache) {
                            Text("Clear")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Message Trim Settings",
                        style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Keep messages for the last N months. Older messages will be removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Coming soon", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StorageBar(
    label: String,
    bytes: Long,
    total: Long,
    color: androidx.compose.ui.graphics.Color
) {
    val fraction = if (total > 0) bytes.toFloat() / total else 0f
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(formatBytes(bytes), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
