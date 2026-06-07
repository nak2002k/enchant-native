package org.enchant.core.model

import org.enchant.core.database.entity.ConversationEntity
import org.enchant.core.database.entity.MessageEntity

data class Conversation(
    val id: String,
    val type: ConversationType,
    val lastMessage: String? = null,
    val lastMessageTimestamp: Long? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val muteUntil: Long? = null,
    val disappearTimerSeconds: Int = 0
) {
    companion object {
        fun fromEntity(e: ConversationEntity): Conversation = Conversation(
            id = e.conversationId,
            type = ConversationType.safeValueOf(e.type),
            lastMessage = e.lastMessage,
            lastMessageTimestamp = e.lastMessageTimestamp,
            unreadCount = e.unreadCount,
            isPinned = e.isPinned,
            isArchived = e.isArchived,
            isMuted = e.isMuted,
            muteUntil = e.muteUntil,
            disappearTimerSeconds = e.disappearTimerSeconds
        )
    }
}

enum class ConversationType { DIRECT, GROUP, CHANNEL;
    companion object {
        fun safeValueOf(value: String): ConversationType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: DIRECT
    }
}

data class Message(
    val localId: Long = 0,
    val envelopeId: String? = null,
    val conversationId: String,
    val senderId: String,
    val content: String,
    val status: MessageStatus,
    val timestamp: Long,
    val isEdited: Boolean = false,
    val isStarred: Boolean = false,
    val isDeleted: Boolean = false,
    val mediaKey: String? = null,
    val mediaMimeType: String? = null,
    val mediaSize: Long? = null,
    val replyToEnvelopeId: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val disappearAt: Long? = null,
    val isViewOnce: Boolean = false,
    val editedAt: Long? = null
) {
    companion object {
        fun fromEntity(e: MessageEntity): Message = Message(
            localId = e.localId,
            envelopeId = e.envelopeId,
            conversationId = e.conversationId,
            senderId = e.senderId,
            content = e.content,
            status = MessageStatus.safeValueOf(e.status),
            timestamp = e.timestamp,
            isEdited = e.isEdited,
            isStarred = e.isStarred,
            isDeleted = e.isDeleted,
            mediaKey = e.mediaKey,
            mediaMimeType = e.mediaMimeType,
            mediaSize = e.mediaSize,
            replyToEnvelopeId = e.replyToEnvelopeId,
            disappearAt = e.disappearAt,
            isViewOnce = e.isViewOnce,
            editedAt = e.editedAt
        )
    }
}

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED, PENDING;
    companion object {
        fun safeValueOf(value: String): MessageStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

data class Reaction(val messageId: String, val emoji: String, val userId: String)
data class Mention(val userId: String, val start: Int, val length: Int)
data class User(val userId: String, val username: String, val displayName: String? = null, val avatarMediaId: String? = null)
data class BodyRange(val start: Int, val length: Int, val type: BodyRangeType, val value: String? = null)
enum class BodyRangeType { BOLD, ITALIC, CODE, MENTION, LINK, SPOILER }
data class LinkPreview(val url: String, val title: String?, val description: String?, val imageUrl: String?)

@JvmInline
value class AccountEntropyPool(val value: String)
