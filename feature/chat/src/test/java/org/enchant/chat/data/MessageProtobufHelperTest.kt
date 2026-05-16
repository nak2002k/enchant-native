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
            envelopeIds = listOf("12345"),
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
    @DisplayName("buildDeleteContent produces parseable delete signal")
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
