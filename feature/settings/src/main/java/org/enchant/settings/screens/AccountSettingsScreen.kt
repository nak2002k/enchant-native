package org.enchant.settings.screens

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.enchant.settings.DeviceInfo
import org.enchant.ui.icons.EnchantIcons

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

    SettingsScaffold(title = "Account", onBack = onBack) {
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
                SettingsProfileHeader(
                    displayName = displayName,
                    username = username,
                    about = about,
                    onEditClick = { showEditProfile = true },
                )
            }

            item { EnchantSectionHeader("Profile") }
            item {
                EnchantGroupedCard {
                    SettingsRow(
                        icon = EnchantIcons.pencil,
                        iconBackground = EnchantBrand.iOSBlue,
                        title = "Edit profile",
                        onClick = { showEditProfile = true },
                    )
                }
            }

            item { EnchantSectionHeader("Devices") }
            item {
                EnchantGroupedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Linked devices",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                    devices.forEachIndexed { index, device ->
                        if (index > 0) EnchantDivider(inset = 56.dp)
                        DeviceRow(device = device, onRevoke = onRevokeDevice)
                    }
                }
            }

            item { EnchantSectionHeader("Danger Zone") }
            item {
                EnchantGroupedCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showDeleteConfirm = true },
                            )
                            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(EnchantBrand.Red),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                EnchantIcons.trash2,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Spacer(Modifier.width(EnchantSpacing.lg))
                        Text(
                            text = "Delete account",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showEditProfile) {
        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
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

@Composable
private fun DeviceRow(
    device: DeviceInfo,
    onRevoke: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(SettingsIconTints.Gray),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Devices,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(EnchantSpacing.lg))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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
            TextButton(onClick = { onRevoke(device.deviceId) }) {
                Text("Revoke", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
