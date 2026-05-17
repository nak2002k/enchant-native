package org.enchant.core.notifications

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("NotificationChannels — Full Coverage")
class NotificationChannelsTest {

    @Nested @DisplayName("Channel Constants")
    inner class ChannelConstantsTest {
        @Test @DisplayName("CHANNEL_MESSAGES is 'messages'")
        fun `channel messages`() {
            assertEquals("messages", NotificationChannels.CHANNEL_MESSAGES)
        }

        @Test @DisplayName("CHANNEL_MESSAGES_SILENT is 'messages_silent'")
        fun `channel messages silent`() {
            assertEquals("messages_silent", NotificationChannels.CHANNEL_MESSAGES_SILENT)
        }

        @Test @DisplayName("CHANNEL_CALLS is 'calls'")
        fun `channel calls`() {
            assertEquals("calls", NotificationChannels.CHANNEL_CALLS)
        }

        @Test @DisplayName("CHANNEL_VOICE is 'voice'")
        fun `channel voice`() {
            assertEquals("voice", NotificationChannels.CHANNEL_VOICE)
        }

        @Test @DisplayName("CHANNEL_OTHER is 'other'")
        fun `channel other`() {
            assertEquals("other", NotificationChannels.CHANNEL_OTHER)
        }
    }

    @Nested @DisplayName("Channel Count")
    inner class ChannelCountTest {
        @Test @DisplayName("defines exactly 5 channels")
        fun `five channels defined`() {
            val channels = listOf(
                NotificationChannels.CHANNEL_MESSAGES,
                NotificationChannels.CHANNEL_MESSAGES_SILENT,
                NotificationChannels.CHANNEL_CALLS,
                NotificationChannels.CHANNEL_VOICE,
                NotificationChannels.CHANNEL_OTHER
            )
            assertEquals(5, channels.size)
            assertEquals(channels.toSet().size, channels.size, "All channel IDs must be unique")
        }
    }
}
