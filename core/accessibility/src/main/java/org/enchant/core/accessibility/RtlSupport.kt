package org.enchant.core.accessibility

import android.content.Context
import android.view.View

fun isRtl(context: Context): Boolean {
    return context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
}

fun getTextAlignment(isRtl: Boolean): Int {
    return if (isRtl) View.TEXT_ALIGNMENT_VIEW_END else View.TEXT_ALIGNMENT_VIEW_START
}
