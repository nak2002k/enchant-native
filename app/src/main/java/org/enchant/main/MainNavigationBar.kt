package org.enchant.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.MainNavigationListLocation
import org.enchant.MainNavigationViewModel.MainNavigationState
import org.enchant.ui.icons.EnchantIcons

private val JewelPurpleLight = Color(0xFF3A0D6E)
private val JewelPurpleDark = Color(0xFFB388E3)

@Composable
private fun brandPurple(): Color =
    if (isSystemInDarkTheme()) JewelPurpleDark else JewelPurpleLight

private fun destinationIcons(destination: MainNavigationListLocation): Pair<ImageVector, ImageVector> =
    when (destination) {
        MainNavigationListLocation.CHATS -> EnchantIcons.messageCircle to EnchantIcons.messageCircle
        MainNavigationListLocation.CALLS -> EnchantIcons.phone to EnchantIcons.phone
        MainNavigationListLocation.STATUS -> EnchantIcons.camera to EnchantIcons.camera
        MainNavigationListLocation.SETTINGS -> EnchantIcons.settings to EnchantIcons.settings
        MainNavigationListLocation.ARCHIVE -> EnchantIcons.messageCircle to EnchantIcons.messageCircle
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
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.06f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "tabIconScale",
                )

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
                            Box(
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = (-6).dp)
                                            .width(28.dp)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(selectedTint)
                                    )
                                }
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
