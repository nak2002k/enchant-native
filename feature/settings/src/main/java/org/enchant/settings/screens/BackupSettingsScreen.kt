package org.enchant.settings.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.enchant.core.network.ApiClient

@Composable
fun BackupSettingsScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val client = remember { ApiClient.getInstance() }
    var lastBackup by remember { mutableStateOf<BackupInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isBackingUp by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val response = client.get("/v1/backup/latest")
        response.fold(
            onSuccess = { json ->
                lastBackup = BackupInfo(
                    backupId = json["backup_id"]?.jsonPrimitive?.content ?: "",
                    version = json["version"]?.jsonPrimitive?.int ?: 0,
                    totalSize = json["total_size"]?.jsonPrimitive?.long ?: 0L,
                    completedTs = json["completed_ts"]?.jsonPrimitive?.content
                )
                isLoading = false
            },
            onFailure = { isLoading = false }
        )
    }

    SettingsScaffold(title = "Backup", onBack = onNavigateBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EnchantSpacing.lg,
                end = EnchantSpacing.lg,
                top = EnchantSpacing.sm,
                bottom = EnchantSpacing.xxxl,
            ),
        ) {
            item { EnchantSectionHeader("Backup Status") }
            item {
                EnchantGroupedCard {
                    when {
                        isLoading -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(EnchantSpacing.md))
                                Text(
                                    text = "Checking for backups...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        lastBackup != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                            ) {
                                Text(
                                    text = "Last backup: ${lastBackup!!.completedTs ?: "Unknown"}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Version: ${lastBackup!!.version}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Size: ${lastBackup!!.totalSize / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = "No backup found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.lg),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(EnchantSpacing.lg))
                Button(
                    onClick = {
                        scope.launch {
                            isBackingUp = true
                            client.post("/v1/backup/initiate", kotlinx.serialization.json.buildJsonObject {
                                put("version", kotlinx.serialization.json.JsonPrimitive(1))
                                put("total_chunks", kotlinx.serialization.json.JsonPrimitive(1))
                                put("total_size", kotlinx.serialization.json.JsonPrimitive(0))
                            })
                            isBackingUp = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBackingUp
                ) {
                    if (isBackingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(EnchantSpacing.sm))
                    }
                    Text(if (isBackingUp) "Backing up..." else "Create Backup")
                }
            }

            item {
                Spacer(Modifier.height(EnchantSpacing.sm))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = lastBackup != null,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Backup")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Backup") },
            text = { Text("Are you sure you want to delete your backup? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        client.del("/v1/backup")
                        lastBackup = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

internal data class BackupInfo(
    val backupId: String,
    val version: Int,
    val totalSize: Long,
    val completedTs: String?
)
