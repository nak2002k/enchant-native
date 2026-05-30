package org.enchant.core.ui

import android.content.res.Resources
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources

enum class WindowBreakpoint {
    SMALL,
    MEDIUM,
    LARGE
}

fun Resources.getWindowBreakpoint(): WindowBreakpoint {
    val widthDp = displayMetrics.widthPixels / displayMetrics.density

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

    if (breakpoint == WindowBreakpoint.LARGE && displayMetrics.widthPixels < displayMetrics.heightPixels) {
        return false
    }

    return true
}

@Composable
fun Resources.rememberIsSplitPane(forceSplitPane: Boolean = false): Boolean {
    return remember(this, forceSplitPane) {
        isSplitPane(forceSplitPane)
    }
}

fun horizontalPartitionDefaultSpacerSize(widthSizeClass: WindowBreakpoint): Int {
    return when (widthSizeClass) {
        WindowBreakpoint.SMALL -> 0
        WindowBreakpoint.MEDIUM -> 8
        WindowBreakpoint.LARGE -> 16
    }
}

fun listPaneDefaultPreferredWidth(): Int = 400