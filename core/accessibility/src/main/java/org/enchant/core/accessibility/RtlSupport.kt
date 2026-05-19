package org.enchant.core.accessibility

import android.content.Context
import android.view.View

/**
 * Returns true when the current layout direction is right-to-left.
 */
fun isRtl(context: Context): Boolean {
    return context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
}

/**
 * Returns the appropriate text alignment constant for the given layout direction.
 *
 * RTL → [View.TEXT_ALIGNMENT_VIEW_END]
 * LTR → [View.TEXT_ALIGNMENT_VIEW_START]
 */
fun getTextAlignment(isRtl: Boolean): Int {
    return if (isRtl) View.TEXT_ALIGNMENT_VIEW_END else View.TEXT_ALIGNMENT_VIEW_START
}
