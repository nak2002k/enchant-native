package org.enchant.core.database.entity

data class MessageEntity(
    val localId: Long = 0,
    val conversationId: String,
    val senderId: String,
    val senderDeviceId: String? = null,
    val envelopeId: String? = null,
    val messageType: String,
    val content: String,
    val mediaKey: String? = null,
    val mediaIv: String? = null,
    val mediaMimeType: String? = null,
    val mediaSize: Long? = null,
    val mediaThumbnailPath: String? = null,
    val replyToEnvelopeId: String? = null,
    val forwardedFromUserId: String? = null,
    val status: String = "sending",
    val timestamp: Long,
    val serverTs: Long? = null,
    val isEdited: Boolean = false,
    val editEnvelopeId: String? = null,
    val isStarred: Boolean = false,
    val isDeleted: Boolean = false,
    val disappearAt: Long? = null,
    val gifUrl: String? = null,
    val isViewOnce: Boolean = false,
    val editedAt: Long? = null
)

data class ConversationEntity(
    val conversationId: String,
    val type: String,
    val lastMessage: String? = null,
    val lastMessageEnvelopeId: String? = null,
    val lastMessageTimestamp: Long? = null,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val muteUntil: Long? = null,
    val disappearTimerSeconds: Int = 0
)

data class EnchantSessionEntity(
    val userId: String,
    val deviceId: String,
    val serializedSession: ByteArray,
    val createdAt: Long? = null,
    val lastUsedAt: Long? = null,
    val archived: Boolean = false
)

data class IdentityEntity(
    val addressName: String,
    val recipientId: String? = null,
    val identityKey: ByteArray,
    val verifiedStatus: Int = 0,
    val firstUse: Boolean = true,
    val timestamp: Long,
    val nonBlockingApproval: Boolean = false
)

data class KeyMaterialEntity(
    val keyType: String,
    val keyId: Int,
    val publicKey: ByteArray,
    val privateKey: ByteArray,
    val signature: ByteArray? = null,
    val createdAt: Long,
    val isActive: Boolean = true
)

data class RecipientEntity(
    val recipientId: String,
    val username: String? = null,
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val avatarMediaId: String? = null,
    val avatarLocalPath: String? = null,
    val isBlocked: Boolean = false
)

data class GroupEntity(
    val groupId: String,
    val name: String,
    val description: String? = null,
    val avatarMediaId: String? = null,
    val myRole: String = "member",
    val memberCount: Int = 0,
    val revision: String = "0"
)

data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val role: String = "member",
    val joinedAt: Long? = null
)

data class MediaCacheEntity(
    val mediaId: String,
    val localPath: String? = null,
    val fileSize: Long? = null,
    val lastAccessedAt: Long? = null
)

data class ProfileCacheEntity(
    val userId: String,
    val displayName: String? = null,
    val username: String? = null,
    val about: String? = null,
    val avatarMediaId: String? = null,
    val profileJson: String? = null
)

data class CallLogEntity(
    val callId: String,
    val remoteUserId: String,
    val type: String,
    val direction: String,
    val durationSeconds: Int = 0,
    val status: String,
    val endedAt: Long
)

data class StatusCacheEntity(
    val statusId: String,
    val authorId: String,
    val statusType: String,
    val textContent: String? = null,
    val mediaId: String? = null,
    val backgroundColor: String? = null,
    val timestamp: Long? = null,
    val viewed: Boolean = false
)

data class StickerPackEntity(
    val packId: String,
    val title: String? = null,
    val cover: String? = null,
    val author: String? = null,
    val installedAt: Long? = null
)

data class InstalledStickerEntity(
    val packId: String,
    val stickerId: String,
    val emoji: String? = null,
    val position: Int? = null
)

data class DraftEntity(
    val conversationId: String,
    val content: String,
    val timestamp: Long
)

data class ScheduledMessageEntity(
    val id: Long = 0,
    val conversationId: String,
    val content: String,
    val scheduledAt: Long,
    val isSent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatFolderEntity(
    val folderId: String,
    val name: String,
    val position: Int = 0,
    val conversationIds: String = "[]"
)
