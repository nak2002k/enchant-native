package org.enchant.chat.data

import org.enchant.protos.ContentProtos
import org.enchant.protos.DataMessageProtos

object MessageProtobufHelper {

    fun buildDataMessageContent(
        body: String,
        timestamp: Long = System.currentTimeMillis(),
        expireTimerSeconds: Int = 0,
        groupMasterKey: ByteArray? = null,
        attachment: org.enchant.protos.AttachmentPointerProtos.AttachmentPointer? = null,
        replyToTimestamp: Long? = null,
        replyToAuthor: String? = null,
        replyToText: String? = null,
        replyToEnvelopeId: String? = null
    ): ByteArray {
        val dataMessage = DataMessageProtos.DataMessage.newBuilder()
            .setBody(body)
            .setTimestamp(timestamp)
            .apply {
                if (expireTimerSeconds > 0) setExpireTimer(expireTimerSeconds)
                if (groupMasterKey != null) {
                    setGroupV2(
                        org.enchant.protos.GroupContextProtos.GroupContextV2.newBuilder()
                            .setMasterKey(com.google.protobuf.ByteString.copyFrom(groupMasterKey))
                            .setRevision(0)
                            .build()
                    )
                }
                if (attachment != null) {
                    addAttachments(attachment)
                }
                if (replyToTimestamp != null) {
                    val quote = org.enchant.protos.DataMessageProtos.DataMessage.Quote.newBuilder()
                        .setId(replyToTimestamp)
                        .setText(replyToText ?: "")
                    if (!replyToAuthor.isNullOrBlank()) quote.setAuthorAci(replyToAuthor)
                    if (!replyToEnvelopeId.isNullOrBlank()) quote.setEnvelopeId(replyToEnvelopeId)
                    setQuote(quote.build())
                }
            }
            .build()

        return ContentProtos.Content.newBuilder()
            .setDataMessage(dataMessage)
            .build()
            .toByteArray()
    }

    fun buildReceiptContent(
        timestamps: List<Long>,
        type: ReceiptType,
        envelopeId: String? = null
    ): ByteArray {
        val receiptType = when (type) {
            ReceiptType.DELIVERY -> org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.Type.DELIVERY
            ReceiptType.READ -> org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.Type.READ
        }
        val builder = org.enchant.protos.ReceiptMessageProtos.ReceiptMessage.newBuilder()
            .setType(receiptType)
            .addAllTimestamp(timestamps)
        if (!envelopeId.isNullOrBlank()) builder.setEnvelopeId(envelopeId)
        val receiptMessage = builder.build()

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
            val content = try {
                val ssc = org.enchant.protos.SignalServiceContentProto.parseFrom(plaintext)
                if (ssc.hasLocalAddress() && ssc.hasContent()) ssc.content
                else if (ssc.hasLocalAddress() && ssc.hasLegacyDataMessage()) ssc.legacyDataMessage
                else throw IllegalArgumentException("not a content wrapper")
            } catch (_: Exception) {
                ContentProtos.Content.parseFrom(plaintext)
            }
            when {
                content.hasDataMessage() -> {
                    val dm = content.dataMessage
                    if (dm.hasDelete()) {
                        ParsedContent.Delete(
                            targetTimestamp = dm.delete.targetSentTimestamp,
                            body = dm.body
                        )
                    } else {
                        val att = dm.attachmentsList.firstOrNull { it.hasCdnKey() && it.hasKey() }
                        ParsedContent.DataMessage(
                            body = dm.body,
                            timestamp = dm.timestamp,
                            expireTimer = dm.expireTimer,
                            groupMasterKey = if (dm.hasGroupV2() && dm.groupV2.masterKey.size() > 0)
                                dm.groupV2.masterKey.toByteArray() else null,
                            mediaId = att?.cdnKey,
                            mediaKey = att?.key?.toByteArray(),
                            mediaMime = att?.contentType,
                            replyToTimestamp = if (dm.hasQuote()) dm.quote.id else null,
                            replyToAuthor = if (dm.hasQuote() && dm.quote.hasAuthorAci()) dm.quote.authorAci else null,
                            replyToText = if (dm.hasQuote()) dm.quote.text else null,
                            replyToEnvelopeId = if (dm.hasQuote() && dm.quote.hasEnvelopeId()) dm.quote.envelopeId else null
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
                        timestamps = rm.timestampList,
                        envelopeId = if (rm.hasEnvelopeId()) rm.envelopeId else null
                    )
                }
                content.hasTypingMessage() -> {
                    ParsedContent.Typing(
                        isTyping = content.typingMessage.action ==
                            org.enchant.protos.TypingMessageProtos.TypingMessage.Action.STARTED
                    )
                }
                content.senderKeyDistributionMessage.size() > 0 -> {
                    val raw = content.senderKeyDistributionMessage.toByteArray()
                    // groupId(36) || signingPub(32) || distribution
                    if (raw.size < 68) {
                        ParsedContent.Unknown
                    } else {
                        val gid = String(raw.copyOfRange(0, 36).let { b ->
                            b.takeWhile { it != 0.toByte() }.toByteArray()
                        }, Charsets.UTF_8).takeIf { it.isNotBlank() }
                        if (gid == null) ParsedContent.Unknown
                        else ParsedContent.SenderKeyDistribution(gid, raw.copyOfRange(36, raw.size))
                    }
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
            val expireTimer: Int = 0,
            val groupMasterKey: ByteArray? = null,
            val mediaId: String? = null,
            val mediaKey: ByteArray? = null,
            val mediaMime: String? = null,
            val replyToTimestamp: Long? = null,
            val replyToAuthor: String? = null,
            val replyToText: String? = null,
            val replyToEnvelopeId: String? = null
        ) : ParsedContent()

        data class Receipt(
            val type: ReceiptType,
            val timestamps: List<Long>,
            val envelopeId: String? = null
        ) : ParsedContent()

        data class Typing(val isTyping: Boolean) : ParsedContent()
        data class SenderKeyDistribution(val groupId: String, val distribution: ByteArray) : ParsedContent()
        data class Delete(val targetTimestamp: Long, val body: String = "") : ParsedContent()
        data object Null : ParsedContent()
        data object Unknown : ParsedContent()
    }

    enum class ReceiptType { DELIVERY, READ }
}
