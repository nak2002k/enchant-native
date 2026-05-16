package org.enchant.core.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityManager

object AccessibilityHelper {
    fun isScreenReaderEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
        return am.isEnabled && am.isTouchExplorationEnabled
    }

    fun getContentDescription(type: String, vararg args: String): String = when (type) {
        "send" -> "Send message"
        "attach" -> "Attach file"
        "emoji" -> "Emoji picker"
        "mic" -> "Voice message"
        "back" -> "Go back"
        "call" -> "Start call"
        "video_call" -> "Start video call"
        "mute" -> "Mute conversation"
        "archive" -> "Archive conversation"
        "delete" -> "Delete message"
        "reply" -> "Reply to message"
        "forward" -> "Forward message"
        "star" -> "Star message"
        "unread" -> "${args.getOrElse(0) { "0" }} unread messages"
        "typing" -> "${args.getOrElse(0) { "Someone" }} is typing"
        else -> type
    }
}
