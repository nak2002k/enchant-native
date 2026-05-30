package org.enchant.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppPaneDragHandle(
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 48.dp)
                .background(
                    color = Color(0xFF605F5D),
                    shape = RoundedCornerShape(percent = 50)
                )
        )
    }
}