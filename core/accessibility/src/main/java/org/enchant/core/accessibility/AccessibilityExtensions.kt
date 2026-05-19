package org.enchant.core.accessibility

import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat

/**
 * Kotlin extension functions for quick accessibility setup on Views.
 *
 * These wrap the verbose ViewCompat / AccessibilityNodeInfo APIs into
 * concise, chainable calls.
 */

/**
 * Sets a content description from a string resource.
 */
fun View.withContentDescription(@StringRes resId: Int, vararg args: Any): View {
    contentDescription = if (args.isEmpty()) {
        context.getString(resId)
    } else {
        context.getString(resId, *args)
    }
    return this
}

/**
 * Sets a literal content description string.
 */
fun View.withContentDescription(text: String): View {
    contentDescription = text
    return this
}

/**
 * Marks this view as a polite live region so TalkBack announces
 * text changes automatically.
 */
fun View.asLiveRegion(): View {
    ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
    return this
}

/**
 * Marks this view as an assertive live region (interrupts current speech).
 */
fun View.asAssertiveLiveRegion(): View {
    ViewCompat.setAccessibilityLiveRegion(this, ViewCompat.ACCESSIBILITY_LIVE_REGION_ASSERTIVE)
    return this
}

/**
 * Adds a custom accessibility action with a label and callback.
 * Delegates to [AccessibilityActionsProvider.addCustomAction].
 */
fun View.withAccessibilityAction(
    label: String,
    action: (View) -> Boolean
): View {
    AccessibilityActionsProvider.addCustomAction(this, label, action)
    return this
}

/**
 * Conditionally sets whether this view is important for accessibility.
 */
fun View.setImportantForAccessibilityIf(condition: Boolean): View {
    importantForAccessibility = if (condition) {
        View.IMPORTANT_FOR_ACCESSIBILITY_YES
    } else {
        View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    return this
}

/**
 * Marks this view as decorative (skipped by screen readers).
 */
fun View.asDecorative(): View {
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    return this
}

/**
 * Marks this view as an accessibility heading.
 */
fun View.asHeading(isHeading: Boolean = true): View {
    ViewCompat.setAccessibilityHeading(this, isHeading)
    return this
}

/**
 * Sets the traversal order of this ViewGroup's children by view IDs.
 */
fun ViewGroup.setDescendantOrder(vararg childIds: Int): ViewGroup {
    val ordered = childIds.filter { it != View.NO_ID }.map { findViewById<View>(it) }.filterNotNull()
    FocusTraversalHelper.setTraversalOrder(this, *ordered.toTypedArray())
    return this
}

/**
 * Sets the traversal order of this ViewGroup's children by view references.
 */
fun ViewGroup.setDescendantOrder(vararg children: View): ViewGroup {
    FocusTraversalHelper.setTraversalOrder(this, *children)
    return this
}

/**
 * Attaches standard message accessibility actions.
 * Delegates to [AccessibilityActionsProvider.attachMessageActions].
 */
fun View.attachMessageActions(
    onReply: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onStar: (() -> Unit)? = null,
    onSelectText: (() -> Unit)? = null
): View {
    AccessibilityActionsProvider.attachMessageActions(
        this, onReply, onCopy, onForward, onDelete, onStar, onSelectText
    )
    return this
}

/**
 * Attaches call-control accessibility actions.
 * Delegates to [AccessibilityActionsProvider.attachCallActions].
 */
fun View.attachCallActions(
    onToggleMic: (() -> Unit)? = null,
    onToggleVideo: (() -> Unit)? = null,
    onToggleSpeaker: (() -> Unit)? = null,
    onEndCall: (() -> Unit)? = null
): View {
    AccessibilityActionsProvider.attachCallActions(
        this, onToggleMic, onToggleVideo, onToggleSpeaker, onEndCall
    )
    return this
}
