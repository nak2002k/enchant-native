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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onNavigateToAccount: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToChats: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item { SettingsSectionHeader("Account") }
            item { SettingsRow(Icons.Default.Person, "Account", "Profile, devices, delete account", onNavigateToAccount) }
            item { SettingsRow(Icons.Default.Lock, "Security", "App lock, safety numbers, two-step PIN", onNavigateToSecurity) }
            item { SettingsRow(Icons.Default.Visibility, "Privacy", "Last seen, online, blocked users", onNavigateToPrivacy) }
            item { SettingsRow(Icons.Default.Notifications, "Notifications", "Message notifications, previews, DND", onNavigateToNotifications) }

            item { SettingsSectionHeader("Preferences") }
            item { SettingsRow(Icons.Default.Palette, "Appearance", "Theme, font size", onNavigateToAppearance) }
            item { SettingsRow(Icons.Default.Chat, "Chats", "Disappearing timer, backup, auto-download", onNavigateToChats) }
            item { SettingsRow(Icons.Default.Storage, "Storage", "Usage, cache, message trim", onNavigateToStorage) }

            item { SettingsSectionHeader("Info") }
            item { SettingsRow(Icons.Default.Info, "About", "Version, licenses", onNavigateToAbout) }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
