package org.enchant.core.accessibility

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(AndroidJUnit4::class)
class AccessibilityDelegateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `outgoing message formatted`() {
        val result = AccessibilityDelegate.getMessageDescription(
            context, "outgoing", "Hello", "Sent", "10:30"
        )
        assertTrue(result.contains("Outgoing"))
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains("Sent"))
        assertTrue(result.contains("10:30"))
    }

    @Test
    fun `incoming message formatted`() {
        val result = AccessibilityDelegate.getMessageDescription(
            context, "incoming", "Hi there", "Delivered", "11:00"
        )
        assertTrue(result.contains("Incoming"))
        assertTrue(result.contains("Hi there"))
        assertTrue(result.contains("Delivered"))
        assertTrue(result.contains("11:00"))
    }

    @Test
    fun `outgoing with media`() {
        val result = AccessibilityDelegate.getMessageDescription(
            context, "outgoing", "Check this", "Sent", "12:00", hasMedia = true
        )
        assertTrue(result.contains("media attachment"))
    }

    @Test
    fun `edited message includes suffix`() {
        val result = AccessibilityDelegate.getMessageDescription(
            context, "outgoing", "Fixed typo", "Sent", "12:30", isEdited = true
        )
        assertTrue(result.contains("Edited"))
    }

    @Test
    fun `not edited has no suffix`() {
        val result = AccessibilityDelegate.getMessageDescription(
            context, "outgoing", "Original", "Sent", "13:00", isEdited = false
        )
        assertTrue(result.endsWith("13:00."))
    }

    @Test
    fun `empty content uses fallback`() {
        val result = AccessibilityDelegate.getMessageDescription(
            context, "incoming", "", "Sent", "14:00"
        )
        assertTrue(result.contains("Empty message"))
    }

    @Test
    fun `incoming media edited`() {
        val result = AccessibilityDelegate.getMessageDescription(
            context, "incoming", "Photo", "Delivered", "15:00", hasMedia = true, isEdited = true
        )
        assertTrue(result.contains("Incoming"))
        assertTrue(result.contains("media attachment"))
        assertTrue(result.contains("Edited"))
    }

    @Test
    fun `online avatar`() {
        val result = AccessibilityDelegate.getAvatarDescription(context, "Alice", true)
        assertTrue(result.contains("Alice"))
        assertTrue(result.contains("Online"))
    }

    @Test
    fun `offline avatar`() {
        val result = AccessibilityDelegate.getAvatarDescription(context, "Bob", false)
        assertTrue(result.contains("Bob"))
        assertTrue(result.contains("Offline"))
    }

    @Test
    fun `empty name unknown`() {
        val result = AccessibilityDelegate.getAvatarDescription(context, "", true)
        assertTrue(result.contains("Unknown"))
    }

    @Test
    fun `blank name unknown`() {
        val result = AccessibilityDelegate.getAvatarDescription(context, "   ", false)
        assertTrue(result.contains("Unknown"))
    }

    @Test
    fun `group avatar`() {
        val result = AccessibilityDelegate.getGroupAvatarDescription(context, "Team Chat")
        assertTrue(result.contains("Team Chat"))
        assertTrue(result.contains("Group avatar"))
    }

    @Test
    fun `empty group name`() {
        val result = AccessibilityDelegate.getGroupAvatarDescription(context, "")
        assertTrue(result.contains("Unknown group"))
        assertTrue(result.contains("avatar"))
    }

    @Test
    fun `plain button`() {
        val result = AccessibilityDelegate.getButtonDescription(
            context, R.string.a11y_button_send
        )
        assertTrue(result.contains("Send message"))
        assertTrue(result.endsWith("button."))
    }

    @Test
    fun `button with state`() {
        val result = AccessibilityDelegate.getButtonDescription(
            context, R.string.a11y_button_mute, state = "Muted"
        )
        assertTrue(result.contains("Mute"))
        assertTrue(result.contains("Muted"))
    }

    @Test
    fun `button toggled on`() {
        val result = AccessibilityDelegate.getButtonDescription(
            context, R.string.a11y_button_mic, state = "on"
        )
        assertTrue(result.contains("Voice message"))
        assertTrue(result.contains("Enabled"))
    }

    @Test
    fun `button toggled off`() {
        val result = AccessibilityDelegate.getButtonDescription(
            context, R.string.a11y_button_mic, state = "off"
        )
        assertTrue(result.contains("Voice message"))
        assertTrue(result.contains("Disabled"))
    }

    @Test
    fun `send key`() {
        val result = AccessibilityDelegate.getButtonDescriptionByKey(context, "send")
        assertTrue(result.contains("Send message"))
    }

    @Test
    fun `call key`() {
        val result = AccessibilityDelegate.getButtonDescriptionByKey(context, "call")
        assertTrue(result.contains("Start call"))
    }

    @Test
    fun `video call with state`() {
        val result = AccessibilityDelegate.getButtonDescriptionByKey(context, "video_call", state = "on")
        assertTrue(result.contains("Start video call"))
        assertTrue(result.contains("Enabled"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown key throws`() {
        AccessibilityDelegate.getButtonDescriptionByKey(context, "nonexistent_action")
    }

    @Test
    fun `all standard keys resolve`() {
        val keys = listOf(
            "send", "attach", "emoji", "mic", "back", "call", "video_call",
            "mute", "archive", "delete", "reply", "forward", "star",
            "search", "more"
        )
        keys.forEach { key ->
            val result = AccessibilityDelegate.getButtonDescriptionByKey(context, key)
            assertTrue("Key '$key' returned blank", result.isNotBlank())
        }
    }

    @Test
    fun `heart reaction`() {
        val result = AccessibilityDelegate.getReactionDescription(context, "❤️", 3)
        assertTrue(result.contains("heart"))
        assertTrue(result.contains("3"))
    }

    @Test
    fun `thumbs up`() {
        val result = AccessibilityDelegate.getReactionDescription(context, "👍", 1)
        assertTrue(result.contains("thumbs up"))
        assertTrue(result.contains("1"))
    }

    @Test
    fun `zero count`() {
        val result = AccessibilityDelegate.getReactionDescription(context, "😂", 0)
        assertTrue(result.contains("No"))
    }

    @Test
    fun `negative count`() {
        val result = AccessibilityDelegate.getReactionDescription(context, "😢", -5)
        assertTrue(result.contains("No"))
    }

    @Test
    fun `unknown emoji`() {
        val result = AccessibilityDelegate.getReactionDescription(context, "🦄", 2)
        assertTrue(result.contains("emoji"))
        assertTrue(result.contains("2"))
    }

    @Test
    fun `heart unicode variant`() {
        val result = AccessibilityDelegate.getReactionDescription(context, "\u2764\uFE0F", 1)
        assertTrue(result.contains("heart"))
    }

    @Test
    fun `all known reactions`() {
        val reactions = mapOf(
            "❤️" to "heart",
            "😂" to "laughing",
            "😮" to "surprised",
            "😢" to "crying",
            "😡" to "angry",
            "👍" to "thumbs up",
            "👎" to "thumbs down",
            "👏" to "clapping"
        )
        reactions.forEach { (emoji, expectedName) ->
            val result = AccessibilityDelegate.getReactionDescription(context, emoji, 1)
            assertTrue("Reaction '$emoji' should contain '$expectedName'", result.contains(expectedName))
        }
    }

    @Test
    fun `none status`() {
        val result = AccessibilityDelegate.getDeliveryStatusDescription(context, DeliveryStatus.NONE)
        assertEquals("", result)
    }

    @Test
    fun `pending status`() {
        val result = AccessibilityDelegate.getDeliveryStatusDescription(context, DeliveryStatus.PENDING)
        assertTrue(result.contains("pending", ignoreCase = true))
    }

    @Test
    fun `sent status`() {
        val result = AccessibilityDelegate.getDeliveryStatusDescription(context, DeliveryStatus.SENT)
        assertTrue(result.contains("sent", ignoreCase = true))
    }

    @Test
    fun `delivered status`() {
        val result = AccessibilityDelegate.getDeliveryStatusDescription(context, DeliveryStatus.DELIVERED)
        assertTrue(result.contains("delivered", ignoreCase = true))
    }

    @Test
    fun `read status`() {
        val result = AccessibilityDelegate.getDeliveryStatusDescription(context, DeliveryStatus.READ)
        assertTrue(result.contains("read", ignoreCase = true))
    }

    @Test
    fun `edited now`() {
        val result = AccessibilityDelegate.getTimestampDescription(
            context, "10:00", "just now", isEdited = true, isNow = true
        )
        assertTrue(result.contains("Edited"))
    }

    @Test
    fun `plain now`() {
        val result = AccessibilityDelegate.getTimestampDescription(
            context, "10:00", "just now", isEdited = false, isNow = true
        )
        assertTrue(result.contains("Just now"))
        assertFalse(result.contains("Edited"))
    }

    @Test
    fun `edited relative`() {
        val result = AccessibilityDelegate.getTimestampDescription(
            context, "10:00", "5 minutes", isEdited = true, isNow = false
        )
        assertTrue(result.contains("Edited"))
        assertTrue(result.contains("5 minutes"))
    }

    @Test
    fun `plain relative`() {
        val result = AccessibilityDelegate.getTimestampDescription(
            context, "10:00", "2 hours", isEdited = false, isNow = false
        )
        assertTrue(result.contains("2 hours"))
        assertFalse(result.contains("Edited"))
    }

    @Test
    fun `basic item`() {
        val result = AccessibilityDelegate.getChatListItemDescription(
            context, "Alice", "Hey!", "10:30"
        )
        assertTrue(result.contains("Alice"))
        assertTrue(result.contains("Hey!"))
        assertTrue(result.contains("10:30"))
    }

    @Test
    fun `single unread`() {
        val result = AccessibilityDelegate.getChatListItemDescription(
            context, "Bob", "See you", "11:00", unreadCount = 1
        )
        assertTrue(result.contains("1 unread message"))
    }

    @Test
    fun `multiple unread`() {
        val result = AccessibilityDelegate.getChatListItemDescription(
            context, "Charlie", "Meeting?", "12:00", unreadCount = 5
        )
        assertTrue(result.contains("5 unread messages"))
    }

    @Test
    fun `muted conversation`() {
        val result = AccessibilityDelegate.getChatListItemDescription(
            context, "Dave", "OK", "13:00", isMuted = true
        )
        assertTrue(result.contains("Muted"))
    }

    @Test
    fun `pinned conversation`() {
        val result = AccessibilityDelegate.getChatListItemDescription(
            context, "Eve", "Later", "14:00", isPinned = true
        )
        assertTrue(result.contains("Pinned"))
    }

    @Test
    fun `draft conversation`() {
        val result = AccessibilityDelegate.getChatListItemDescription(
            context, "Frank", "Last msg", "15:00", hasDraft = "typing..."
        )
        assertTrue(result.contains("Draft"))
        assertTrue(result.contains("typing..."))
    }

    @Test
    fun `all indicators`() {
        val result = AccessibilityDelegate.getChatListItemDescription(
            context, "Grace", "Bye", "16:00",
            unreadCount = 3, isMuted = true, isPinned = true, hasDraft = "hello"
        )
        assertTrue(result.contains("3 unread messages"))
        assertTrue(result.contains("Muted"))
        assertTrue(result.contains("Pinned"))
        assertTrue(result.contains("Draft"))
    }

    @Test
    fun `photo`() {
        val result = AccessibilityDelegate.getMediaDescription(context, MediaType.PHOTO)
        assertTrue(result.contains("Photo"))
    }

    @Test
    fun `video`() {
        val result = AccessibilityDelegate.getMediaDescription(context, MediaType.VIDEO)
        assertTrue(result.contains("Video"))
    }

    @Test
    fun `voice note with duration`() {
        val result = AccessibilityDelegate.getMediaDescription(context, MediaType.VOICE_NOTE, "2:30")
        assertTrue(result.contains("Voice note"))
        assertTrue(result.contains("2:30"))
    }

    @Test
    fun `voice note no duration`() {
        val result = AccessibilityDelegate.getMediaDescription(context, MediaType.VOICE_NOTE)
        assertTrue(result.contains("Audio"))
    }

    @Test
    fun `video note with duration`() {
        val result = AccessibilityDelegate.getMediaDescription(context, MediaType.VIDEO_NOTE, "0:15")
        assertTrue(result.contains("Video note"))
        assertTrue(result.contains("0:15"))
    }

    @Test
    fun `all media types`() {
        MediaType.values().forEach { type ->
            val result = AccessibilityDelegate.getMediaDescription(context, type)
            assertTrue("MediaType.$type returned blank", result.isNotBlank())
        }
    }

    @Test
    fun `incoming call`() {
        val result = AccessibilityDelegate.getCallStateDescription(context, CallState.INCOMING, "Alice")
        assertTrue(result.contains("Incoming"))
        assertTrue(result.contains("Alice"))
    }

    @Test
    fun `ongoing call`() {
        val result = AccessibilityDelegate.getCallStateDescription(context, CallState.ONGOING, "Bob")
        assertTrue(result.contains("progress"))
        assertTrue(result.contains("Bob"))
    }

    @Test
    fun `ended call`() {
        val result = AccessibilityDelegate.getCallStateDescription(context, CallState.ENDED, "Charlie")
        assertTrue(result.contains("ended"))
        assertTrue(result.contains("Charlie"))
    }

    @Test
    fun `missed call`() {
        val result = AccessibilityDelegate.getCallStateDescription(context, CallState.MISSED, "Dave")
        assertTrue(result.contains("Missed"))
        assertTrue(result.contains("Dave"))
    }

    @Test
    fun `encrypted`() {
        val result = AccessibilityDelegate.getSecurityDescription(context, SecurityState.ENCRYPTED)
        assertTrue(result.contains("encrypted", ignoreCase = true))
    }

    @Test
    fun `verified`() {
        val result = AccessibilityDelegate.getSecurityDescription(context, SecurityState.VERIFIED)
        assertTrue(result.contains("Verified"))
    }

    @Test
    fun `unverified`() {
        val result = AccessibilityDelegate.getSecurityDescription(context, SecurityState.UNVERIFIED)
        assertTrue(result.contains("Unverified"))
    }

    @Test
    fun `blocked`() {
        val result = AccessibilityDelegate.getSecurityDescription(context, SecurityState.BLOCKED)
        assertTrue(result.contains("Blocked"))
    }

    @Test
    fun `safety number with name`() {
        val result = AccessibilityDelegate.getSecurityDescription(
            context, SecurityState.SAFETY_NUMBER_CHANGED, "Alice"
        )
        assertTrue(result.contains("Alice"))
    }

    @Test
    fun `safety number without name`() {
        val result = AccessibilityDelegate.getSecurityDescription(
            context, SecurityState.SAFETY_NUMBER_CHANGED
        )
        assertTrue(result.contains("Unverified"))
    }
}
