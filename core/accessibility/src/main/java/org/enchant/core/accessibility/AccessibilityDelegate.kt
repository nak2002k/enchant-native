package org.enchant.core.accessibility

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AccessibilityDelegate {
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun getContentDescriptionForMessage(direction: String, content: String, status: String, timestamp: Long, hasMedia: Boolean = false, isEdited: Boolean = false): String {
        val dir = if (direction == "outgoing") "Outgoing" else "Incoming"
        val media = if (hasMedia) " with media attachment" else ""
        val time = timeFormatter.format(Date(timestamp))
        val edit = if (isEdited) " Edited." else "."
        return "$dir text message: $content$media. $status at $time$edit"
    }

    fun getContentDescriptionForAvatar(userName: String, isOnline: Boolean): String {
        val name = userName.ifBlank { "Unknown user" }
        val status = if (isOnline) "Online" else "Offline"
        return "$name's avatar. $status."
    }

    fun getContentDescriptionForButton(action: String, state: String? = null): String {
        val buttonName = when (action) {
            "send" -> "Send message"
            "attach" -> "Attach file"
            "emoji" -> "Emoji picker"
            "mic" -> "Voice message"
            "back" -> "Go back"
            "call" -> "Start call"
            "video_call" -> "Start video call"
            "mute" -> "Mute"
            "archive" -> "Archive conversation"
            "delete" -> "Delete message"
            "reply" -> "Reply to message"
            "forward" -> "Forward message"
            "star" -> "Star message"
            "search" -> "Search"
            "more" -> "More options"
            else -> action.replaceFirstChar { it.uppercase() }
        }
        return if (state != null) "$buttonName button. Current state: $state." else "$buttonName button."
    }

    fun getContentDescriptionForReaction(emoji: String, count: Int): String {
        val c = if (count <= 0) "No" else if (count == 1) "1" else "$count"
        val name = when (emoji) {
            "❤️", "\u2764\uFE0F" -> "heart"
            "😂", "\uD83D\uDE02" -> "laughing face"
            "😮", "\uD83D\uDE2E" -> "surprised face"
            "😢", "\uD83D\uDE22" -> "crying face"
            "😡", "\uD83D\uDE21" -> "angry face"
            "👍", "\uD83D\uDC4D" -> "thumbs up"
            "👎", "\uD83D\uDC4E" -> "thumbs down"
            "👏", "\uD83D\uDC4F" -> "clapping"
            else -> "emoji"
        }
        return "$c $name reactions. Tap to view."
    }
}
