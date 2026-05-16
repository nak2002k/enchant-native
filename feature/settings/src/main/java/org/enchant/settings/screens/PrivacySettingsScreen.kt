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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    lastSeenVisibility: String,
    onlineVisibility: Boolean,
    avatarVisibility: String,
    aboutVisibility: String,
    blockedUsers: List<String>,
    readReceipts: Boolean,
    onLastSeenChange: (String) -> Unit,
    onOnlineVisibilityChange: (Boolean) -> Unit,
    onAvatarVisibilityChange: (String) -> Unit,
    onAboutVisibilityChange: (String) -> Unit,
    onReadReceiptsChange: (Boolean) -> Unit,
    onViewBlockedUsers: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
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
                    Text("Last Seen & Online", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Who can see my last seen?",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    VisibilitySelector(
                        selected = lastSeenVisibility,
                        onSelected = onLastSeenChange
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show online", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = onlineVisibility,
                            onCheckedChange = onOnlineVisibilityChange
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Profile", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Profile photo", style = MaterialTheme.typography.bodyMedium)
                    VisibilitySelector(
                        selected = avatarVisibility,
                        onSelected = onAvatarVisibilityChange
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("About", style = MaterialTheme.typography.bodyMedium)
                    VisibilitySelector(
                        selected = aboutVisibility,
                        onSelected = onAboutVisibilityChange
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Read Receipts", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = readReceipts,
                            onCheckedChange = onReadReceiptsChange
                        )
                    }
                }
            }

            Card(onClick = onViewBlockedUsers, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Block, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Blocked Users", style = MaterialTheme.typography.bodyMedium)
                        Text("${blockedUsers.size} blocked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, "View",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VisibilitySelector(selected: String, onSelected: (String) -> Unit) {
    val options = listOf("everyone" to "Everyone", "contacts" to "Contacts", "nobody" to "Nobody")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label) }
            )
        }
    }
}
