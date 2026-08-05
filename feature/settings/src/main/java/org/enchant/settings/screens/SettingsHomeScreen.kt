package org.enchant.settings.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    displayName: String,
    username: String?,
    about: String?,
    onOpenProfile: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToChats: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                    onClick = onOpenProfile
                )
            }

            item { SignalSettingsDivider() }

            item {
                SignalSettingsRow(
                    icon = Icons.Default.Person,
                    title = "Account",
                    label = "Profile, devices, delete account",
                    onClick = onNavigateToAccount
                )
            }
            item { SignalSettingsRow(
                icon = Icons.Default.Lock,
                title = "Security",
                label = "App lock, safety numbers, two-step PIN",
                onClick = onNavigateToSecurity
            ) }
            item { SignalSettingsRow(
                icon = Icons.Default.Visibility,
                title = "Privacy",
                label = "Last seen, online, blocked users",
                onClick = onNavigateToPrivacy
            ) }
            item { SignalSettingsRow(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                label = "Message notifications, previews, DND",
                onClick = onNavigateToNotifications
            ) }

            item { SignalSettingsDivider() }

            item { SignalSettingsRow(
                icon = Icons.Default.Palette,
                title = "Appearance",
                label = "Theme, font size",
                onClick = onNavigateToAppearance
            ) }
            item { SignalSettingsRow(
                icon = Icons.Default.Chat,
                title = "Chats",
                label = "Disappearing timer, backup, auto-download",
                onClick = onNavigateToChats
            ) }
            item { SignalSettingsRow(
                icon = Icons.Default.Storage,
                title = "Storage",
                label = "Usage, cache, message trim",
                onClick = onNavigateToStorage
            ) }

            item { SignalSettingsDivider() }

            item { SignalSettingsRow(
                icon = Icons.Default.Info,
                title = "About",
                label = "Version, licenses",
                onClick = onNavigateToAbout
            ) }

            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }
}
