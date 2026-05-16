package org.enchant.chat.data

import org.enchant.protos.ContentProtos
import org.enchant.protos.DataMessageProtos

object MessageProtobufHelper {

    fun buildDataMessageContent(
        body: String,
        timestamp: Long = System.currentTimeMillis(),
        expireTimerSeconds: Int = 0
    ): ByteArray {
        val dataMessage = DataMessageProtos.DataMessage.newBuilder()
            .setBody(body)
            .setTimestamp(timestamp)
            .apply {
                if (expireTimerSeconds > 0) setExpireTimer(expireTimerSeconds)
            }
            .build()

        return ContentProtos.Content.newBuilder()
            .setDataMessage(dataMessage)
            .build()
            .toByteArray()
    }

    fun buildReceiptContent(
        envelopeIds: List<String>,
        type: ReceiptType
    ): ByteArray {
        val receiptType = when (type) {
            ReceiptType.DELIVERY -> org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.Type.DELIVERY
            ReceiptType.READ -> org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.Type.READ
        }
        val receiptMessage = org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.newBuilder()
            .setType(receiptType)
            .addAllTimestamp(envelopeIds.map { it.toLongOrNull() ?: 0L })
            .build()

        return ContentProtos.Content.newBuilder()
            .setReceiptMessage(receiptMessage)
            .build()
            .toByteArray()
    }

    fun buildTypingContent(isTyping: Boolean): ByteArray {
        val action = if (isTyping) {
            org.enchant.protos.TypingMessageProtos.TypingMessage.Action.STARTED
        } else {
            org.enchant.protos.TypingMessageProtos.TypingMessage.Action.STOPPED
        }
        val typingMessage = org.enchant.protos.TypingMessageProtos.TypingMessage.newBuilder()
            .setAction(action)
            .setTimestamp(System.currentTimeMillis())
            .build()

        return ContentProtos.Content.newBuilder()
            .setTypingMessage(typingMessage)
            .build()
            .toByteArray()
    }

    fun buildDeleteContent(targetTimestamp: Long): ByteArray {
        val delete = DataMessageProtos.DataMessage.Delete.newBuilder()
            .setTargetSentTimestamp(targetTimestamp)
            .build()
        val dataMessage = DataMessageProtos.DataMessage.newBuilder()
            .setDelete(delete)
            .setTimestamp(System.currentTimeMillis())
            .build()

        return ContentProtos.Content.newBuilder()
            .setDataMessage(dataMessage)
            .build()
            .toByteArray()
    }

    fun parseContent(plaintext: ByteArray): ParsedContent {
        return try {
            val content = ContentProtos.Content.parseFrom(plaintext)
            when {
                content.hasDataMessage() -> {
                    val dm = content.dataMessage
                    if (dm.hasDelete()) {
                        ParsedContent.Delete(
                            targetTimestamp = dm.delete.targetSentTimestamp,
                            body = dm.body
                        )
                    } else {
                        ParsedContent.DataMessage(
                            body = dm.body,
                            timestamp = dm.timestamp,
                            expireTimer = dm.expireTimer
                        )
                    }
                }
                content.hasReceiptMessage() -> {
                    val rm = content.receiptMessage
                    val receiptType = when (rm.type) {
                        org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.Type.DELIVERY -> ReceiptType.DELIVERY
                        org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.Type.READ -> ReceiptType.READ
                        else -> ReceiptType.DELIVERY
                    }
                    ParsedContent.Receipt(
                        type = receiptType,
                        timestamps = rm.timestampList
                    )
                }
                content.hasTypingMessage() -> {
                    ParsedContent.Typing(
                        isTyping = content.typingMessage.action ==
                            org.enchant.protos.TypingMessageProtos.TypingMessage.Action.STARTED
                    )
                }
                content.hasNullMessage() -> ParsedContent.Null
                else -> ParsedContent.Unknown
            }
        } catch (e: Exception) {
            ParsedContent.Unknown
        }
    }

    sealed class ParsedContent {
        data class DataMessage(
            val body: String,
            val timestamp: Long,
            val expireTimer: Int = 0
        ) : ParsedContent()

        data class Receipt(
            val type: ReceiptType,
            val timestamps: List<Long>
        ) : ParsedContent()

        data class Typing(val isTyping: Boolean) : ParsedContent()
        data class Delete(val targetTimestamp: Long, val body: String = "") : ParsedContent()
        data object Null : ParsedContent()
        data object Unknown : ParsedContent()
    }

    enum class ReceiptType { DELIVERY, READ }
}
