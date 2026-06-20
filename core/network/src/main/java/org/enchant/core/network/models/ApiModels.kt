package org.enchant.core.network.models

import kotlinx.serialization.Serializable

@Serializable
data class OtpRequest(val identifier: String)

@Serializable
data class OtpResponse(val challengeId: String, val expiresIn: Int)

@Serializable
data class VerifyOtpRequest(val challengeId: String, val otp: String, val deviceInfo: DeviceInfo? = null)

@Serializable
data class DeviceInfo(val deviceId: String? = null, val userAgent: String? = null)

@Serializable
data class AuthResponse(val userId: String, val accessToken: String, val refreshToken: String, val expiresIn: Int, val deviceId: String = "")

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class RefreshResponse(val accessToken: String, val refreshToken: String, val expiresIn: Int)

@Serializable
data class KeyRegisterRequest(
    val identityKey: String,
    val signedPrekey: SignedPrekeyData,
    val oneTimePrekeys: List<OneTimePrekeyData>
)

@Serializable
data class SignedPrekeyData(val publicKey: String, val signature: String)

@Serializable
data class OneTimePrekeyData(val publicKey: String)

@Serializable
data class KeyBundleResponse(val devices: List<KeyBundleDevice>)

@Serializable
data class KeyBundleDevice(
    val deviceId: String,
    val identityKey: String,
    val signedPrekey: SignedPrekeyData,
    val oneTimePrekey: String? = null
)

@Serializable
data class OpkCountResponse(val remaining: Int)

@Serializable
data class RotateSpkRequest(val publicKey: String, val signature: String)

@Serializable
data class UploadOpksRequest(val oneTimePrekeys: List<OneTimePrekeyData>)

@Serializable
data class SendMessageRequest(
    val recipientUserId: String,
    val recipientDeviceId: String? = null,
    val messageType: String,
    val payload: String,
    val senderTs: String? = null
)

@Serializable
data class SendMessageResponse(val envelope_ids: List<String>)

@Serializable
data class SealedSendRequest(
    val recipientUserId: String,
    val recipientDeviceId: String? = null,
    val messageType: String,
    val payload: String,
    val replyToken: String? = null
)

@Serializable
data class SealedSendResponse(val envelope_ids: List<String>, val sealed: Boolean)

@Serializable
data class MediaUploadResponse(val mediaId: String, val downloadUrl: String, val expiresTs: Long)

@Serializable
data class MediaDeleteResponse(val deleted: Boolean)

@Serializable
data class ProfileResponse(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val about: String? = null,
    val avatarMediaId: String? = null,
    val avatarKey: String? = null,
    val lastSeen: String? = null,
    val online: Boolean? = null
)

@Serializable
data class UpdateProfileRequest(
    val username: String? = null,
    val displayName: String? = null,
    val about: String? = null
)

@Serializable
data class UpdateProfileResponse(val updated: Boolean)

@Serializable
data class PrivacyRequest(
    val lastSeenVisibility: String? = null,
    val onlineVisibility: String? = null,
    val avatarVisibility: String? = null,
    val aboutVisibility: String? = null,
    val readReceiptsEnabled: Boolean? = null,
    val groupsAddPolicy: String? = null
)

@Serializable
data class UsernameSearchResponse(val results: List<UsernameSearchResult>)

@Serializable
data class UsernameSearchResult(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val avatarMediaId: String? = null
)

@Serializable
data class AddContactRequest(val contactUserId: String, val customName: String? = null)

@Serializable
data class AddContactResponse(val added: Boolean)

@Serializable
data class ContactListResponse(val contacts: List<ContactEntry>)

@Serializable
data class ContactEntry(val contactUserId: String, val customName: String? = null, val addedTs: String)

@Serializable
data class PhoneMatchRequest(val phoneHashes: List<String>)

@Serializable
data class PhoneMatchResponse(val matches: List<PhoneMatchResult>)

@Serializable
data class PhoneMatchResult(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val phoneHash: String
)

@Serializable
data class FriendRequest(val toUserId: String)

@Serializable
data class FriendRequestResponse(val id: String, val status: String)

@Serializable
data class CreateGroupRequest(
    val name: String,
    val description: String? = null,
    val initialMemberIds: List<String>? = null,
    val addMembersPolicy: String? = null,
    val joinType: String? = null
)

@Serializable
data class AddMemberRequest(val userIds: List<String>)

@Serializable
data class UpdateRoleRequest(val role: String)

@Serializable
data class InviteLinkRequest(val expiresTs: String? = null, val maxUses: Int = 0)

@Serializable
data class JoinRequestAction(val approve: Boolean = true)

@Serializable
data class ApiError(val error: String, val code: String? = null, val retryAfter: Int? = null)

@Serializable
data class BackupRestoreResponse(val success: Boolean, val restoredKeys: Int)
