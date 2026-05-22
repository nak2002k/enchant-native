package org.enchant.main

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.MainNavigationListLocation
import org.enchant.MainNavigationViewModel.MainNavigationState

@Composable
fun MainNavigationBar(
    state: MainNavigationState,
    onDestinationSelected: (MainNavigationListLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(if (state.compact) 48.dp else 80.dp)
    ) {
        val entries = remember(state.isStoriesFeatureEnabled) {
            if (state.isStoriesFeatureEnabled) {
                MainNavigationListLocation.entries.toList()
            } else {
                MainNavigationListLocation.entries.filterNot {
                    it == MainNavigationListLocation.STORIES
                }
            }
        }

        entries.forEach { destination ->
            val badgeCount = when (destination) {
                MainNavigationListLocation.CHATS -> state.chatsCount
                MainNavigationListLocation.CALLS -> state.callsCount
                MainNavigationListLocation.STORIES -> state.storiesCount
                MainNavigationListLocation.ARCHIVE -> 0
            }
            val selected = state.currentListLocation == destination

            NavigationBarItem(
                selected = selected,
                icon = {},
                label = if (state.compact) null else {
                    { Text(destination.name) }
                },
                onClick = { onDestinationSelected(destination) }
            )
        }
    }
}