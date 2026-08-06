package org.enchant.settings.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.enchant.ui.icons.EnchantIcons

// ─── First-composition entrance: fade + 6dp rise, springy, 40ms stagger ───
@Composable
private fun SettingsEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(6f) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = with(density) { offsetY.value.dp.toPx() }
        },
    ) {
        content()
    }
}

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
    onBack: (() -> Unit)?
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
                SettingsEntrance(index = 0) {
                    SettingsProfileHeader(
                        displayName = displayName,
                        username = username,
                        about = about,
                        onEditClick = onOpenProfile,
                    )
                }
            }

            item {
                SettingsEntrance(index = 1) {
                    EnchantSectionHeader("Preferences")
                }
            }
            item {
                SettingsEntrance(index = 2) {
                    EnchantGroupedCard {
                        SettingsRow(
                            icon = EnchantIcons.user,
                            iconBackground = EnchantBrand.iOSBlue,
                            title = "Account",
                            subtitle = "Profile, devices, delete account",
                            onClick = onNavigateToAccount,
                        )
                        EnchantDivider(inset = 56.dp)
                        SettingsRow(
                            icon = EnchantIcons.lock,
                            iconBackground = SettingsIconTints.DarkGray,
                            title = "Security",
                            subtitle = "App lock, safety numbers, two-step PIN",
                            onClick = onNavigateToSecurity,
                        )
                        EnchantDivider(inset = 56.dp)
                        SettingsRow(
                            icon = EnchantIcons.shieldCheck,
                            iconBackground = SettingsIconTints.Teal,
                            title = "Privacy",
                            subtitle = "Last seen, online, blocked users",
                            onClick = onNavigateToPrivacy,
                        )
                        EnchantDivider(inset = 56.dp)
                        SettingsRow(
                            icon = EnchantIcons.bell,
                            iconBackground = EnchantBrand.Red,
                            title = "Notifications",
                            subtitle = "Message notifications, previews, DND",
                            onClick = onNavigateToNotifications,
                        )
                    }
                }
            }

            item {
                SettingsEntrance(index = 3) {
                    EnchantSectionHeader("Appearance & Data")
                }
            }
            item {
                SettingsEntrance(index = 4) {
                    EnchantGroupedCard {
                        SettingsRow(
                            icon = EnchantIcons.palette,
                            iconBackground = SettingsIconTints.Orange,
                            title = "Appearance",
                            subtitle = "Theme, font size",
                            onClick = onNavigateToAppearance,
                        )
                        EnchantDivider(inset = 56.dp)
                        SettingsRow(
                            icon = EnchantIcons.chatBubbleText,
                            iconBackground = EnchantBrand.GroupGreen,
                            title = "Chats",
                            subtitle = "Disappearing timer, backup, auto-download",
                            onClick = onNavigateToChats,
                        )
                        EnchantDivider(inset = 56.dp)
                        SettingsRow(
                            icon = EnchantIcons.database,
                            iconBackground = SettingsIconTints.Purple,
                            title = "Storage",
                            subtitle = "Usage, cache, message trim",
                            onClick = onNavigateToStorage,
                        )
                    }
                }
            }

            item {
                SettingsEntrance(index = 5) {
                    EnchantSectionHeader("About")
                }
            }
            item {
                SettingsEntrance(index = 6) {
                    EnchantGroupedCard {
                        SettingsRow(
                            icon = EnchantIcons.info,
                            iconBackground = SettingsIconTints.Gray,
                            title = "About",
                            subtitle = "Version, licenses",
                            onClick = onNavigateToAbout,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(EnchantSpacing.xxxl)) }
        }
    }
}
