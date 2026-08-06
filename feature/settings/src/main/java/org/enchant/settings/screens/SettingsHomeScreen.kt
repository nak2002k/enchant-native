package org.enchant.settings.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    SettingsScaffold(title = "Settings", onBack = onBack) {
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
                    onEditClick = onOpenProfile,
                )
            }

            item { EnchantSectionHeader("Preferences") }
            item {
                EnchantGroupedCard {
                    SettingsRow(
                        icon = Icons.Rounded.Person,
                        iconBackground = EnchantBrand.iOSBlue,
                        title = "Account",
                        subtitle = "Profile, devices, delete account",
                        onClick = onNavigateToAccount,
                    )
                    EnchantDivider(inset = 56.dp)
                    SettingsRow(
                        icon = Icons.Rounded.Lock,
                        iconBackground = SettingsIconTints.DarkGray,
                        title = "Security",
                        subtitle = "App lock, safety numbers, two-step PIN",
                        onClick = onNavigateToSecurity,
                    )
                    EnchantDivider(inset = 56.dp)
                    SettingsRow(
                        icon = Icons.Rounded.Shield,
                        iconBackground = SettingsIconTints.Teal,
                        title = "Privacy",
                        subtitle = "Last seen, online, blocked users",
                        onClick = onNavigateToPrivacy,
                    )
                    EnchantDivider(inset = 56.dp)
                    SettingsRow(
                        icon = Icons.Rounded.Notifications,
                        iconBackground = EnchantBrand.Red,
                        title = "Notifications",
                        subtitle = "Message notifications, previews, DND",
                        onClick = onNavigateToNotifications,
                    )
                }
            }

            item { EnchantSectionHeader("Appearance & Data") }
            item {
                EnchantGroupedCard {
                    SettingsRow(
                        icon = Icons.Rounded.Palette,
                        iconBackground = SettingsIconTints.Orange,
                        title = "Appearance",
                        subtitle = "Theme, font size",
                        onClick = onNavigateToAppearance,
                    )
                    EnchantDivider(inset = 56.dp)
                    SettingsRow(
                        icon = Icons.Rounded.ChatBubble,
                        iconBackground = EnchantBrand.GroupGreen,
                        title = "Chats",
                        subtitle = "Disappearing timer, backup, auto-download",
                        onClick = onNavigateToChats,
                    )
                    EnchantDivider(inset = 56.dp)
                    SettingsRow(
                        icon = Icons.Rounded.Storage,
                        iconBackground = SettingsIconTints.Purple,
                        title = "Storage",
                        subtitle = "Usage, cache, message trim",
                        onClick = onNavigateToStorage,
                    )
                }
            }

            item { EnchantSectionHeader("About") }
            item {
                EnchantGroupedCard {
                    SettingsRow(
                        icon = Icons.Rounded.Info,
                        iconBackground = SettingsIconTints.Gray,
                        title = "About",
                        subtitle = "Version, licenses",
                        onClick = onNavigateToAbout,
                    )
                }
            }

            item { Spacer(Modifier.height(EnchantSpacing.xxxl)) }
        }
    }
}
