package org.enchant.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.enchant.MainNavigationDetailLocation
import org.enchant.MainNavigationViewModel
import org.enchant.isContentRoot
import org.enchant.ui.icons.EnchantIcons

@Composable
fun EmptyDetailScreen() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))
    )

    Box(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            Icon(
                imageVector = EnchantIcons.chatBubble,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Select a conversation",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose from the list on the left to start messaging",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MainNavigationDetailLocationEffect(
    mainNavigationViewModel: MainNavigationViewModel,
    onWillFocusPrimary: suspend () -> Unit = {}
) {
    var state by remember {
        mutableStateOf(
            mainNavigationViewModel.earlyNavigationDetailLocationRequested
                ?: MainNavigationDetailLocation.Empty
        )
    }

    LaunchedEffect(Unit) {
        mainNavigationViewModel.detailLocation.collect { location ->
            // Always (re)focus on every emission: after a system back the
            // pane state is out of sync with the detail stack, and a repeated
            // goTo for the same conversation must still surface the primary
            // pane on phones.
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
            state = location
        }
    }
}

data class MainContentLayoutData(
    val detailPaddingEnd: Int = 0,
    val shape: RoundedCornerShape = RoundedCornerShape(0.dp)
)