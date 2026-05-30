package org.enchant.window

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppScaffold(
    navigator: AppScaffoldNavigator<Any>,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    primaryContent: @Composable () -> Unit = {},
    secondaryContent: @Composable () -> Unit = {},
    navRailContent: @Composable () -> Unit = {},
    bottomNavContent: @Composable () -> Unit = {}
) {
    val navigationType = rememberNavigationType()
    val scope = rememberCoroutineScope()
    val canGoBack by navigator.canNavigateBack.collectAsState()

    BackHandler(enabled = canGoBack) {
        scope.launch { navigator.navigateBack() }
    }

    Row(modifier = modifier.fillMaxSize()) {
        if (navigationType.isRail()) {
            navRailContent()
        }

        Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
            Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                secondaryContent()
            }

            if (!navigationType.isRail()) {
                bottomNavContent()
            }
        }

        Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
            primaryContent()
        }
    }
}