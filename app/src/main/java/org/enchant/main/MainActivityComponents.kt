package org.enchant.main

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.enchant.MainNavigationDetailLocation
import org.enchant.MainNavigationViewModel

@Composable
fun EmptyDetailScreen() {
    Box(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
    }
}

@Composable
fun MainNavigationDetailLocationEffect(
    mainNavigationViewModel: MainNavigationViewModel,
    onWillFocusPrimary: suspend () -> Unit = {}
) {
    var state by rememberSaveable {
        mutableStateOf(
            mainNavigationViewModel.earlyNavigationDetailLocationRequested
                ?: MainNavigationDetailLocation.Empty
        )
    }

    LaunchedEffect(Unit) {
        mainNavigationViewModel.detailLocation.collect { location ->
            if (state == location) {
                mainNavigationViewModel.setFocusedPane(
                    if (location == MainNavigationDetailLocation.Empty) {
                        "Secondary"
                    } else {
                        if (location.isContentRoot) {
                            onWillFocusPrimary()
                        }
                        "Primary"
                    }
                )
            }
            state = location
        }
    }
}

data class MainContentLayoutData(
    val detailPaddingEnd: Int = 0,
    val shape: RoundedCornerShape = RoundedCornerShape(0.dp)
)