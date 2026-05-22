package org.enchant.core.ui

import android.content.res.Resources
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

enum class WindowBreakpoint {
    SMALL,
    MEDIUM,
    LARGE
}

fun Resources.getWindowBreakpoint(): WindowBreakpoint {
    val metrics = displayMetrics
    val widthDp = metrics.widthPixels / metrics.density

    return when {
        widthDp < 600f -> WindowBreakpoint.SMALL
        widthDp < 840f -> WindowBreakpoint.MEDIUM
        else -> WindowBreakpoint.LARGE
    }
}

fun Resources.isSplitPane(forceSplitPane: Boolean = false): Boolean {
    if (forceSplitPane) return true

    val breakpoint = getWindowBreakpoint()
    if (breakpoint == WindowBreakpoint.SMALL) return false

    val metrics = displayMetrics
    if (breakpoint == WindowBreakpoint.LARGE && metrics.widthPixels < metrics.heightPixels) {
        return false
    }

    return true
}

fun WindowSizeClass.Companion.horizontalPartitionDefaultSpacerSize(): androidx.compose.ui.unit.Dp {
    return when (widthSizeClass) {
        WindowWidthSizeClass.Compact -> 0.dp
        WindowWidthSizeClass.Medium -> 8.dp
        WindowWidthSizeClass.Expanded -> 16.dp
        else -> 8.dp
    }
}

fun WindowSizeClass.Companion.listPaneDefaultPreferredWidth(): androidx.compose.ui.unit.Dp {
    return 400.dp
}