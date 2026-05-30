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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.enchant.core.network.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val client = remember { ApiClient.getInstance() }
    var lastBackup by remember { mutableStateOf<BackupInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isBackingUp by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backup Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        isLoading -> CircularProgressIndicator()
                        lastBackup != null -> {
                            Text("Last backup: ${lastBackup!!.completedTs ?: "Unknown"}")
                            Text("Version: ${lastBackup!!.version}")
                            Text("Size: ${lastBackup!!.totalSize / 1024} KB")
                        }
                        else -> Text("No backup found")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

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
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isBackingUp) "Backing up..." else "Create Backup")
            }

            Spacer(modifier = Modifier.height(8.dp))

            var showDeleteDialog by remember { mutableStateOf(false) }

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = lastBackup != null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Backup")
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
    }
}

internal data class BackupInfo(
    val backupId: String,
    val version: Int,
    val totalSize: Long,
    val completedTs: String?
)
