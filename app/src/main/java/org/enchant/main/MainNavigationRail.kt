package org.enchant.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.MainNavigationListLocation

@Composable
fun MainNavigationRail(
    state: MainNavigationState,
    mainFloatingActionButtons: @Composable () -> Unit,
    onDestinationSelected: (MainNavigationListLocation) -> Unit,
    modifier: Modifier = Modifier
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
    val selectedDestination = if (state.currentListLocation == MainNavigationListLocation.ARCHIVE) {
        MainNavigationListLocation.CHATS
    } else {
        state.currentListLocation
    }

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        mainFloatingActionButtons()
        Spacer(modifier = Modifier.height(40.dp))

        entries.forEachIndexed { idx, destination ->
            val selected = selectedDestination == destination
            NavigationRailItem(
                modifier = Modifier.padding(
                    bottom = if (idx == entries.lastIndex) 0.dp else 16.dp
                ),
                icon = {},
                label = { Text(destination.name) },
                selected = selected,
                onClick = { onDestinationSelected(destination) }
            )
        }
    }
}