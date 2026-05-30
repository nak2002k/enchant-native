package org.enchant.window

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import org.enchant.core.ui.WindowBreakpoint
import org.enchant.core.ui.getWindowBreakpoint

enum class NavigationType {
    BAR,
    RAIL
}

@Composable
fun rememberNavigationType(): NavigationType {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val breakpoint = resources.getWindowBreakpoint()

    return when (breakpoint) {
        WindowBreakpoint.SMALL -> NavigationType.BAR
        WindowBreakpoint.MEDIUM -> NavigationType.BAR
        WindowBreakpoint.LARGE -> NavigationType.RAIL
    }
}

fun NavigationType.isRail(): Boolean = this == NavigationType.RAIL

val LocalNavigationType = staticCompositionLocalOf { NavigationType.BAR }