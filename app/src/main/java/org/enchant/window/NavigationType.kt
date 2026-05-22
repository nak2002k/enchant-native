package org.enchant.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

enum class NavigationType {
    BAR,
    RAIL
}

@Composable
fun rememberNavigationType(): NavigationType {
    return NavigationType.BAR
}

fun NavigationType.isRail(): Boolean = this == NavigationType.RAIL

val LocalNavigationType = staticCompositionLocalOf { NavigationType.BAR }