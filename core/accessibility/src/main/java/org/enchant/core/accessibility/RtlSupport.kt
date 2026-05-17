package org.enchant.core.accessibility

import android.content.Context
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

fun isRtl(context: Context): Boolean {
    return context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
}

fun Modifier.mirrorLayoutDirection(isRtl: Boolean): Modifier = this.then(
    if (isRtl) {
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, 0)
            }
        }
    } else Modifier
)

fun getTextAlignment(isRtl: Boolean): androidx.compose.ui.text.style.TextAlignment {
    return if (isRtl) androidx.compose.ui.text.style.TextAlignment.End
    else androidx.compose.ui.text.style.TextAlignment.Start
}
