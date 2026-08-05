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
    val currentDetail by navigator.currentDetail.collectAsState()
    val isTablet = navigationType.isRail() || navigator.scaffoldDirective.maxHorizontalPartitions >= 2

    BackHandler(enabled = canGoBack) {
        scope.launch { navigator.navigateBack() }
    }

    Row(modifier = modifier.fillMaxSize()) {
        if (navigationType.isRail()) {
            navRailContent()
        }

        if (isTablet) {
            // Split-pane (tablet/landscape): list + detail side by side.
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
        } else {
            // Phone: single pane. The list is shown until a detail is
            // opened (mirrors Signal's phone navigation), then the detail
            // takes the full screen.
            Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                if (currentDetail == null) {
                    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        Box(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                            secondaryContent()
                        }
                        bottomNavContent()
                    }
                } else {
                    primaryContent()
                }
            }
        }
    }
}
