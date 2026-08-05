package org.enchant.settings.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.settings.DeviceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    displayName: String,
    username: String?,
    about: String?,
    devices: List<DeviceInfo>,
    isLoading: Boolean,
    onProfileUpdate: (displayName: String?, username: String?, about: String?) -> Unit = { _, _, _ -> },
    onRevokeDevice: (String) -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var editDisplayName by remember(displayName) { mutableStateOf(displayName) }
    var editUsername by remember(username) { mutableStateOf(username ?: "") }
    var editAbout by remember(about) { mutableStateOf(about ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                SignalSettingsBioRow(
                    initial = displayName.take(2).uppercase().ifBlank { "?" },
                    displayName = displayName.ifBlank { "User" },
                    username = username,
                    about = about,
                    onClick = { showEditProfile = true }
                )
            }

            item { SignalSettingsDivider() }

            item {
                SignalSettingsRow(
                    icon = Icons.Default.Edit,
                    title = "Edit profile",
                    onClick = { showEditProfile = true }
                )
            }

            item { SignalSettingsDivider() }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Devices", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            items(devices, key = { it.deviceId }) { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Devices, null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge)
                        if (device.lastSeen != null) {
                            Text(
                                "Last seen: ${device.lastSeen}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (device.isCurrent) {
                            Text(
                                "Current device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (!device.isCurrent) {
                        TextButton(onClick = { onRevokeDevice(device.deviceId) }) {
                            Text("Revoke", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 64.dp)
                )
            }

            item { SignalSettingsDivider() }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteConfirm = true }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DeleteForever, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(24.dp))
                    Text("Delete account", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it.lowercase().replace("[^a-z0-9_]".toRegex(), "") },
                        label = { Text("Username") },
                        prefix = { Text("@") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editAbout,
                        onValueChange = { if (it.length <= 139) editAbout = it },
                        label = { Text("About") },
                        supportingText = { Text("${editAbout.length}/139") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showEditProfile = false
                    onProfileUpdate(
                        editDisplayName.ifBlank { null },
                        editUsername.ifBlank { null }.let { if (it == username) null else it },
                        editAbout.ifBlank { null }.let { if (it == about) null else it }
                    )
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account") },
            text = { Text("Are you sure? This action cannot be undone. All your data will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteAccount()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
