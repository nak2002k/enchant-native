package org.enchant.core.accessibility

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat

/**
 * Controls screen-reader focus traversal order for Views and ViewGroups.
 *
 * Android's default traversal follows the view tree order, which is often wrong
 * for chat UIs (e.g. timestamp before message body, or avatar after name).
 * This helper lets you define explicit traversal sequences.
 *
 * NOTE: Requires feature:chat:ConversationItem and feature:chat-list:ConversationListItem
 *       as integration points.
 */
object FocusTraversalHelper {

    /**
     * Sets an explicit focus traversal order for a [ViewGroup]'s children.
     * Uses [View.setAccessibilityTraversalAfter] to chain the order.
     *
     * @param parent the container whose children will be ordered
     * @param orderedChildren the child views in the desired traversal order
     */
    fun setTraversalOrder(parent: ViewGroup, vararg orderedChildren: View) {
        orderedChildren.forEachIndexed { index, child ->
            if (index > 0) {
                val previousId = orderedChildren[index - 1].id
                if (previousId != View.NO_ID) {
                    child.accessibilityTraversalAfter = previousId
                }
            }
            if (index < orderedChildren.size - 1) {
                val nextId = orderedChildren[index + 1].id
                if (nextId != View.NO_ID) {
                    child.accessibilityTraversalBefore = nextId
                }
            }
        }
    }

    /**
     * Returns the recommended traversal order for a conversation list item.
     *
     * Order: avatar → name → lastMessage → timestamp → unreadBadge
     */
    fun getConversationItemTraversal(
        avatar: View,
        name: View,
        lastMessage: View,
        timestamp: View,
        unreadBadge: View
    ): List<View> {
        return listOf(avatar, name, lastMessage, timestamp, unreadBadge)
    }

    /**
     * Returns the recommended traversal order for a chat message bubble.
     *
     * Order: avatar (incoming only) → senderName (incoming only) → messageBody →
     *        mediaAttachment → timestamp → deliveryStatus → reactions
     */
    fun getMessageBubbleTraversal(
        isIncoming: Boolean,
        avatar: View?,
        senderName: View?,
        messageBody: View,
        mediaAttachment: View?,
        timestamp: View,
        deliveryStatus: View?,
        reactions: View?
    ): List<View> {
        return buildList {
            if (isIncoming) {
                avatar?.let { add(it) }
                senderName?.let { add(it) }
            }
            add(messageBody)
            mediaAttachment?.let { add(it) }
            add(timestamp)
            deliveryStatus?.let { add(it) }
            reactions?.let { add(it) }
        }
    }

    /**
     * Returns the recommended traversal order for a call control bar.
     *
     * Order: muteToggle → videoToggle → speakerToggle → endCall
     */
    fun getCallControlsTraversal(
        muteToggle: View,
        videoToggle: View,
        speakerToggle: View,
        endCall: View
    ): List<View> {
        return listOf(muteToggle, videoToggle, speakerToggle, endCall)
    }

    /**
     * Marks a view as decorative so screen readers skip it entirely.
     */
    fun skipForAccessibility(view: View) {
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * Marks a view as important only when a condition is met.
     * When the condition is false the view is skipped by screen readers.
     */
    fun setImportantForAccessibilityIf(view: View, condition: Boolean) {
        view.importantForAccessibility = if (condition) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    /**
     * Marks a view as an accessibility heading so TalkBack users can
     * jump between sections quickly.
     */
    fun setAsHeading(view: View, isHeading: Boolean = true) {
        ViewCompat.setAccessibilityHeading(view, isHeading)
    }
}
