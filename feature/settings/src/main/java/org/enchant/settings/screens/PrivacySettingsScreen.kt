package org.enchant.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.ui.icons.EnchantIcons

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
    SettingsScaffold(title = "Privacy", onBack = onBack) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = EnchantSpacing.lg,
                end = EnchantSpacing.lg,
                top = EnchantSpacing.sm,
                bottom = EnchantSpacing.xxxl,
            ),
        ) {
            item { EnchantSectionHeader("Presence") }
            item {
                EnchantGroupedCard {
                    VisibilityRow(
                        title = "Who can see my last seen?",
                        selected = lastSeenVisibility,
                        onSelected = onLastSeenChange,
                    )
                    EnchantDivider(inset = 56.dp)
                    SignalSettingsSwitchRow(
                        title = "Show online",
                        checked = onlineVisibility,
                        onCheckedChange = onOnlineVisibilityChange,
                    )
                }
            }

            item { EnchantSectionHeader("Profile") }
            item {
                EnchantGroupedCard {
                    VisibilityRow(
                        title = "Profile photo",
                        selected = avatarVisibility,
                        onSelected = onAvatarVisibilityChange,
                    )
                    EnchantDivider(inset = 56.dp)
                    VisibilityRow(
                        title = "About",
                        selected = aboutVisibility,
                        onSelected = onAboutVisibilityChange,
                    )
                }
            }

            item { EnchantSectionHeader("Messaging") }
            item {
                EnchantGroupedCard {
                    SignalSettingsSwitchRow(
                        title = "Read receipts",
                        checked = readReceipts,
                        onCheckedChange = onReadReceiptsChange,
                    )
                    EnchantDivider(inset = 56.dp)
                    SignalSettingsSwitchRow(
                        title = "Veil sender",
                        label = "Hide your identity from the server when messaging",
                        checked = veilSender,
                        onCheckedChange = onVeilSenderChange,
                    )
                }
            }

            item { EnchantSectionHeader("Blocking") }
            item {
                EnchantGroupedCard {
                    SettingsRow(
                        icon = EnchantIcons.ban,
                        iconBackground = EnchantBrand.Red,
                        title = "Blocked users",
                        subtitle = if (blockedUsers.isEmpty()) "No blocked users" else "${blockedUsers.size} blocked",
                        onClick = onViewBlockedUsers,
                    )
                }
            }

            item { Spacer(Modifier.height(EnchantSpacing.xxxl)) }
        }
    }
}

@Composable
private fun VisibilityRow(
    title: String,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EnchantSpacing.lg, vertical = EnchantSpacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(EnchantSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(EnchantSpacing.sm)) {
            listOf("everyone" to "Everyone", "contacts" to "Contacts", "nobody" to "Nobody").forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}
