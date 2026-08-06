package org.enchant.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.MainNavigationListLocation
import org.enchant.MainNavigationViewModel.MainNavigationState

private val JewelPurpleLight = Color(0xFF3A0D6E)
private val JewelPurpleDark = Color(0xFFB388E3)

@Composable
private fun brandPurple(): Color =
    if (isSystemInDarkTheme()) JewelPurpleDark else JewelPurpleLight

private fun destinationIcons(destination: MainNavigationListLocation): Pair<ImageVector, ImageVector> =
    when (destination) {
        MainNavigationListLocation.CHATS -> Icons.Filled.Chat to Icons.Outlined.ChatBubbleOutline
        MainNavigationListLocation.CALLS -> Icons.Filled.Call to Icons.Outlined.Call
        MainNavigationListLocation.STATUS -> Icons.Filled.PhotoCamera to Icons.Outlined.PhotoCamera
        MainNavigationListLocation.SETTINGS -> Icons.Filled.Settings to Icons.Outlined.Settings
        MainNavigationListLocation.ARCHIVE -> Icons.Filled.Chat to Icons.Outlined.ChatBubbleOutline
    }

@Composable
fun MainNavigationBar(
    state: MainNavigationState,
    onDestinationSelected: (MainNavigationListLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (state.compact) 48.dp else 80.dp)
        ) {
            val entries = remember(state.isStoriesFeatureEnabled) {
                listOf(
                    MainNavigationListLocation.STATUS,
                    MainNavigationListLocation.CALLS,
                    MainNavigationListLocation.CHATS,
                    MainNavigationListLocation.SETTINGS,
                )
            }

            entries.forEach { destination ->
                val badgeCount = when (destination) {
                    MainNavigationListLocation.CHATS -> state.chatsCount
                    MainNavigationListLocation.CALLS -> state.callsCount
                    MainNavigationListLocation.STATUS -> state.storiesCount
                    MainNavigationListLocation.SETTINGS -> 0
                    MainNavigationListLocation.ARCHIVE -> 0
                }
                val selected = state.currentListLocation == destination
                val (filledIcon, outlinedIcon) = destinationIcons(destination)
                val selectedTint = brandPurple()

                NavigationBarItem(
                    selected = selected,
                    icon = {
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(90))
                            },
                            label = "tabIcon"
                        ) { isSelected ->
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSelected) filledIcon else outlinedIcon,
                                    contentDescription = destination.name,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) selectedTint
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (badgeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 11.dp, y = (-8).dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(selectedTint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    },
                    label = if (state.compact) null else {
                        {
                            val label = when (destination) {
                                MainNavigationListLocation.STATUS -> "Status"
                                else -> destination.name.lowercase().replaceFirstChar { it.uppercase() }
                            }
                            Text(
                                label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) selectedTint
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = selectedTint,
                        selectedTextColor = selectedTint,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = Color.Transparent
                    ),
                    onClick = { onDestinationSelected(destination) }
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
