package org.enchant.chat.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MessageProtobufHelper")
class MessageProtobufHelperTest {

    @Test
    @DisplayName("buildDataMessageContent produces parseable Content")
    fun `build data message content roundtrips`() {
        val body = "Hello, World!"
        val bytes = MessageProtobufHelper.buildDataMessageContent(body = body)
        assertTrue(bytes.isNotEmpty())

        val parsed = MessageProtobufHelper.parseContent(bytes)
        assertTrue(parsed is MessageProtobufHelper.ParsedContent.DataMessage)
        assertEquals(body, (parsed as MessageProtobufHelper.ParsedContent.DataMessage).body)
    }

    @Test
    @DisplayName("buildReceiptContent produces parseable receipt")
    fun `build receipt content`() {
        val bytes = MessageProtobufHelper.buildReceiptContent(
            timestamps = listOf(12345L),
            type = MessageProtobufHelper.ReceiptType.DELIVERY
        )
        assertTrue(bytes.isNotEmpty())

        val parsed = MessageProtobufHelper.parseContent(bytes)
        assertTrue(parsed is MessageProtobufHelper.ParsedContent.Receipt)
        assertEquals(MessageProtobufHelper.ReceiptType.DELIVERY,
            (parsed as MessageProtobufHelper.ParsedContent.Receipt).type)
    }

    @Test
    @DisplayName("buildTypingContent produces parseable typing indicator")
    fun `build typing content`() {
        val bytes = MessageProtobufHelper.buildTypingContent(isTyping = true)
        assertTrue(bytes.isNotEmpty())

        val parsed = MessageProtobufHelper.parseContent(bytes)
        assertTrue(parsed is MessageProtobufHelper.ParsedContent.Typing)
        assertTrue((parsed as MessageProtobufHelper.ParsedContent.Typing).isTyping)
    }

    @Test
    @DisplayName("buildDataMessageContent with forwardedFromUserId roundtrips the forward flag")
    fun `forwarded message roundtrips`() {
        val bytes = MessageProtobufHelper.buildDataMessageContent(
            body = "forward me",
            forwardedFromUserId = "original-sender-uuid"
        )
        val parsed = MessageProtobufHelper.parseContent(bytes)
        assertTrue(parsed is MessageProtobufHelper.ParsedContent.DataMessage)
        val dm = parsed as MessageProtobufHelper.ParsedContent.DataMessage
        assertEquals("forward me", dm.body)
        assertEquals("original-sender-uuid", dm.forwardedFromUserId)
    }

    @Test
    @DisplayName("non-forwarded message has null forwardedFromUserId")
    fun `plain message has no forward attribution`() {
        val bytes = MessageProtobufHelper.buildDataMessageContent(body = "plain")
        val parsed = MessageProtobufHelper.parseContent(bytes)
        assertTrue(parsed is MessageProtobufHelper.ParsedContent.DataMessage)
        assertNull((parsed as MessageProtobufHelper.ParsedContent.DataMessage).forwardedFromUserId)
    }

    @Test
    @DisplayName("buildDeleteContent produces parseable delete envelope")
    fun `build delete content`() {
        val targetTs = 1234567890L
        val bytes = MessageProtobufHelper.buildDeleteContent(targetTimestamp = targetTs)
        assertTrue(bytes.isNotEmpty())

        val parsed = MessageProtobufHelper.parseContent(bytes)
        assertTrue(parsed is MessageProtobufHelper.ParsedContent.Delete)
        assertEquals(targetTs, (parsed as MessageProtobufHelper.ParsedContent.Delete).targetTimestamp)
    }

    @Test
    @DisplayName("parseContent returns Unknown for invalid input")
    fun `invalid input returns Unknown`() {
        val result = MessageProtobufHelper.parseContent(ByteArray(0))
        assertTrue(result is MessageProtobufHelper.ParsedContent.Unknown)
    }

    @Test
    @DisplayName("parseContent returns Null for null message content")
    fun `null message content`() {
        val nullContent = org.enchant.protos.ContentProtos.Content.newBuilder()
            .setNullMessage(org.enchant.protos.ContentProtos.NullMessage.getDefaultInstance())
            .build()
            .toByteArray()

        val parsed = MessageProtobufHelper.parseContent(nullContent)
        assertTrue(parsed is MessageProtobufHelper.ParsedContent.Null)
    }
}
