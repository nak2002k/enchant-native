package org.enchant.window

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

enum class NavigationType {
    BAR,
    RAIL
}

@Composable
fun rememberNavigationType(): NavigationType {
    val windowInfo = currentWindowAdaptiveInfo()
    val widthDp = windowInfo.windowSizeClass.windowBounds.width.value
    return remember(widthDp) {
        when {
            widthDp < 600f -> NavigationType.BAR
            else -> NavigationType.RAIL
        }
    }
}

fun NavigationType.isRail(): Boolean = this == NavigationType.RAIL