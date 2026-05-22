package org.enchant.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.MainNavigationListLocation

interface MainFloatingActionButtonsCallback {
    fun onNewChatClick()
    fun onNewCallClick()
    fun onCameraClick(destination: MainNavigationListLocation)

    object Empty : MainFloatingActionButtonsCallback {
        override fun onNewChatClick() = Unit
        override fun onNewCallClick() = Unit
        override fun onCameraClick(destination: MainNavigationListLocation) = Unit
    }
}

@Composable
fun MainFloatingActionButtons(
    destination: MainNavigationListLocation,
    callback: MainFloatingActionButtonsCallback,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            slideInVertically { it } togetherWith slideOutVertically { -it }
        },
        modifier = modifier,
        label = "FABContent"
    ) { tab ->
        when (tab) {
            MainNavigationListLocation.CHATS,
            MainNavigationListLocation.ARCHIVE -> NewChatFab(onClick = callback::onNewChatClick)
            MainNavigationListLocation.CALLS -> NewCallFab(onClick = callback::onNewCallClick)
            MainNavigationListLocation.STORIES -> CameraFab(onClick = { callback.onCameraClick(tab) })
        }
    }
}

@Composable
private fun NewChatFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(56.dp)
    ) {
    }
}

@Composable
private fun NewCallFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(56.dp)
    ) {
    }
}

@Composable
private fun CameraFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(56.dp)
    ) {
    }
}