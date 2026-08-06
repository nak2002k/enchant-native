package org.enchant.main

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import org.enchant.ui.icons.EnchantIcons

private val JewelPurpleLight = Color(0xFF3A0D6E)
private val JewelPurpleDark = Color(0xFFB388E3)

@Composable
private fun brandPurple(): Color = if (isSystemInDarkTheme()) JewelPurpleDark else JewelPurpleLight

private fun destinationIcons(destination: MainNavigationListLocation): Pair<ImageVector, ImageVector> =
    when (destination) {
        MainNavigationListLocation.CHATS -> EnchantIcons.messageCircle to EnchantIcons.messageCircle
        MainNavigationListLocation.CALLS -> EnchantIcons.phone to EnchantIcons.phone
        MainNavigationListLocation.STATUS -> EnchantIcons.camera to EnchantIcons.camera
        MainNavigationListLocation.SETTINGS -> EnchantIcons.settings to EnchantIcons.settings
        MainNavigationListLocation.ARCHIVE -> EnchantIcons.messageCircle to EnchantIcons.messageCircle
    }

private fun badgeCountFor(
    state: MainNavigationState,
    destination: MainNavigationListLocation
): Int = when (destination) {
    MainNavigationListLocation.CHATS -> state.chatsCount
    MainNavigationListLocation.CALLS -> state.callsCount
    MainNavigationListLocation.STATUS -> state.storiesCount
    MainNavigationListLocation.SETTINGS -> 0
    MainNavigationListLocation.ARCHIVE -> 0
}

@Composable
fun MainNavigationRail(
    state: MainNavigationState,
    mainFloatingActionButtons: @Composable () -> Unit,
    onDestinationSelected: (MainNavigationListLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = remember(state.isStoriesFeatureEnabled) {
        if (state.isStoriesFeatureEnabled) {
            MainNavigationListLocation.entries.filterNot {
                it == MainNavigationListLocation.ARCHIVE
            }
        } else {
            MainNavigationListLocation.entries.filterNot {
                it == MainNavigationListLocation.STATUS || it == MainNavigationListLocation.ARCHIVE
            }
        }
    }
    val selectedDestination = if (state.currentListLocation == MainNavigationListLocation.ARCHIVE) {
        MainNavigationListLocation.CHATS
    } else {
        state.currentListLocation
    }

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.width(80.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(brandPurple()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "E",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        mainFloatingActionButtons()
        Spacer(modifier = Modifier.height(32.dp))

        entries.forEachIndexed { idx, destination ->
            val selected = selectedDestination == destination
            NavigationRailItem(
                modifier = Modifier.padding(
                    bottom = if (idx == entries.lastIndex) 0.dp else 12.dp
                ),
                icon = {
                    RailIcon(
                        selected = selected,
                        destination = destination,
                        badgeCount = badgeCountFor(state, destination)
                    )
                },
                label = null,
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Color.Transparent,
                    unselectedIconColor = Color.Transparent,
                    indicatorColor = Color.Transparent
                )
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RailIcon(
    selected: Boolean,
    destination: MainNavigationListLocation,
    badgeCount: Int
) {
    val tint = if (selected) brandPurple() else MaterialTheme.colorScheme.onSurfaceVariant
    val (filledIcon, outlinedIcon) = destinationIcons(destination)
    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brandPurple().copy(alpha = 0.12f))
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (selected) filledIcon else outlinedIcon,
                contentDescription = destination.name,
                modifier = Modifier.size(28.dp),
                tint = tint
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                destination.name,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = tint
            )
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = 1.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(brandPurple()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
