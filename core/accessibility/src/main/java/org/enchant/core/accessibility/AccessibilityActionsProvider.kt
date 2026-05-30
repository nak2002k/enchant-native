package org.enchant.core.accessibility

import android.content.Context
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.ViewCompat

/**
 * Provides custom accessibility actions for long-press / context-menu behaviors
 * on chat messages and list items.
 *
 * Each action is registered via [ViewCompat.addAccessibilityAction] and
 * fires a callback when activated by a screen reader.
 *
 * NOTE: Requires feature:chat:ConversationItem and feature:chat-list:ConversationListItem
 *       as integration points where these actions are attached to views.
 */
object AccessibilityActionsProvider {

    /**
     * Attaches standard message actions to a [View].
     */
    fun attachMessageActions(
        view: View,
        onReply: (() -> Unit)? = null,
        onCopy: (() -> Unit)? = null,
        onForward: (() -> Unit)? = null,
        onDelete: (() -> Unit)? = null,
        onStar: (() -> Unit)? = null,
        onSelectText: (() -> Unit)? = null
    ) {
        val context = view.context

        onReply?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_reply)) { _, _ ->
                it()
                true
            }
        }
        onCopy?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_copy)) { _, _ ->
                it()
                true
            }
        }
        onForward?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_forward)) { _, _ ->
                it()
                true
            }
        }
        onDelete?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_delete)) { _, _ ->
                it()
                true
            }
        }
        onStar?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_star)) { _, _ ->
                it()
                true
            }
        }
        onSelectText?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_select)) { _, _ ->
                it()
                true
            }
        }
    }

    /**
     * Attaches call-control actions to a [View].
     */
    fun attachCallActions(
        view: View,
        onToggleMic: (() -> Unit)? = null,
        onToggleVideo: (() -> Unit)? = null,
        onToggleSpeaker: (() -> Unit)? = null,
        onEndCall: (() -> Unit)? = null
    ) {
        val context = view.context

        onToggleMic?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_toggle_mic)) { _, _ ->
                it()
                true
            }
        }
        onToggleVideo?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_toggle_video)) { _, _ ->
                it()
                true
            }
        }
        onToggleSpeaker?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_toggle_speaker)) { _, _ ->
                it()
                true
            }
        }
        onEndCall?.let {
            ViewCompat.addAccessibilityAction(view, context.getString(R.string.a11y_button_end_call)) { _, _ ->
                it()
                true
            }
        }
    }

    /**
     * Adds a custom accessibility action with a label and callback.
     */
    fun addCustomAction(
        view: View,
        label: String,
        action: (View) -> Boolean
    ) {
        ViewCompat.addAccessibilityAction(view, label) { v, _ ->
            action(v)
        }
    }

    /**
     * No-op. Once custom accessibility actions are added via ViewCompat.addAccessibilityAction,
     * they cannot be safely removed without replacing the view or its delegate entirely.
     * Setting the delegate to null does NOT remove actions and may break the view's
     * existing accessibility behavior permanently. Callers should recreate the view
     * or reattach a new delegate if actions need to change.
     */
    @Suppress("UNUSED_PARAMETER")
    fun clearActions(view: View) {
    }
}
