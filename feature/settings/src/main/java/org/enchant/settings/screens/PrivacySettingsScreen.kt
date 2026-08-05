package org.enchant.settings.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    veilSender: Boolean,
    onLastSeenChange: (String) -> Unit,
    onOnlineVisibilityChange: (Boolean) -> Unit,
    onAvatarVisibilityChange: (String) -> Unit,
    onAboutVisibilityChange: (String) -> Unit,
    onReadReceiptsChange: (Boolean) -> Unit,
    onVeilSenderChange: (Boolean) -> Unit,
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                VisibilityRow(
                    title = "Who can see my last seen?",
                    selected = lastSeenVisibility,
                    onSelected = onLastSeenChange
                )
            }
            item { SignalSettingsSwitchRow(
                title = "Show online",
                checked = onlineVisibility,
                onCheckedChange = onOnlineVisibilityChange
            ) }

            item { SignalSettingsDivider() }

            item {
                VisibilityRow(
                    title = "Profile photo",
                    selected = avatarVisibility,
                    onSelected = onAvatarVisibilityChange
                )
            }
            item {
                VisibilityRow(
                    title = "About",
                    selected = aboutVisibility,
                    onSelected = onAboutVisibilityChange
                )
            }

            item { SignalSettingsDivider() }

            item { SignalSettingsSwitchRow(
                title = "Read receipts",
                checked = readReceipts,
                onCheckedChange = onReadReceiptsChange
            ) }

            item { SignalSettingsDivider() }

            item { SignalSettingsSwitchRow(
                title = "Veil sender",
                label = "Hide your identity from the server when messaging",
                checked = veilSender,
                onCheckedChange = onVeilSenderChange
            ) }

            item { SignalSettingsDivider() }

            item { SignalSettingsRow(
                icon = Icons.Default.Block,
                title = "Blocked users",
                label = if (blockedUsers.isEmpty()) "No blocked users" else "${blockedUsers.size} blocked",
                onClick = onViewBlockedUsers
            ) }

            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun VisibilityRow(
    title: String,
    selected: String,
    onSelected: (String) -> Unit
) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf("everyone" to "Everyone", "contacts" to "Contacts", "nobody" to "Nobody").forEach { (value, label) ->
                androidx.compose.material3.FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}
