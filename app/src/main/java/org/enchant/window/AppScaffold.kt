package org.enchant.window

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppScaffold(
    navigator: AppScaffoldNavigator<Any>,
    modifier: Modifier = Modifier,
    topBarContent: @Composable () -> Unit = {},
    primaryContent: @Composable () -> Unit = {},
    secondaryContent: @Composable () -> Unit = {},
    navRailContent: @Composable () -> Unit = {},
    bottomNavContent: @Composable () -> Unit = {},
    paneExpansionState: PaneExpansionState = rememberPaneExpansionState(),
    paneExpansionDragHandle: @Composable (PaneExpansionState) -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.systemBars
) {
    val isForceSinglePane = false
    val useSimpleScaffold = isForceSinglePane ||
        (navigator.scaffoldDirective.maxHorizontalPartitions == 1 && Build.VERSION.SDK_INT < 33)

    if (useSimpleScaffold) {
        SinglePaneAppScaffold(
            navigator = navigator,
            modifier = modifier,
            topBarContent = topBarContent,
            primaryContent = primaryContent,
            secondaryContent = secondaryContent,
            bottomNavContent = bottomNavContent,
            snackbarHost = snackbarHost,
            contentWindowInsets = contentWindowInsets
        )
    } else {
        AdaptiveAppScaffold(
            navigator = navigator,
            modifier = modifier,
            topBarContent = topBarContent,
            primaryContent = primaryContent,
            secondaryContent = secondaryContent,
            navRailContent = navRailContent,
            bottomNavContent = bottomNavContent,
            paneExpansionState = paneExpansionState,
            paneExpansionDragHandle = paneExpansionDragHandle,
            snackbarHost = snackbarHost,
            contentWindowInsets = contentWindowInsets
        )
    }
}

@Composable
fun ListAndNavigation(
    listContent: @Composable () -> Unit,
    navRailContent: @Composable () -> Unit,
    bottomNavContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationType = rememberNavigationType()

    Row(modifier = modifier.fillMaxSize()) {
        if (navigationType.isRail()) {
            navRailContent()
        }

        Column(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.weight(1f)) {
                listContent()
            }

            if (!navigationType.isRail()) {
                bottomNavContent()
            }
        }
    }
}