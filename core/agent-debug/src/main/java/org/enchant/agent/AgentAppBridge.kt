package org.enchant.agent

import kotlinx.serialization.json.JsonObject

/**
 * App-provided implementation that executes real app logic (auth, messaging, contacts, etc.).
 * Registered once from the debug source set after DI init.
 */
interface AgentAppBridge {
    suspend fun getState(): JsonObject
    suspend fun getHelp(): JsonObject

    // Auth / registration
    suspend fun requestOtp(identifier: String): JsonObject
    suspend fun verifyOtp(otp: String): JsonObject
    suspend fun registerKeys(): JsonObject
    suspend fun setProfile(username: String, displayName: String, about: String?): JsonObject
    suspend fun completeRegistration(): JsonObject
    suspend fun skipToMainIfRegistered(): JsonObject
    suspend fun logout(): JsonObject
    suspend fun acceptTerms(): JsonObject
    suspend fun skipPermissions(): JsonObject
    suspend fun skipPin(): JsonObject
    suspend fun skipAppLock(): JsonObject
    suspend fun runRegistrationFlow(
        identifier: String,
        otp: String,
        username: String,
        displayName: String,
        about: String? = null
    ): JsonObject

    suspend fun navigate(body: JsonObject): JsonObject
    suspend fun performUiAction(action: String): JsonObject
    suspend fun getUiCurrent(): JsonObject
    suspend fun submitPhone(phone: String): JsonObject
    suspend fun submitOtp(otp: String): JsonObject
    suspend fun completeAppLock(pin: String?): JsonObject

    // Messaging
    suspend fun listConversations(): JsonObject
    suspend fun openConversation(conversationId: String): JsonObject
    suspend fun listMessages(conversationId: String, limit: Int): JsonObject
    suspend fun sendMessage(recipientUserId: String, text: String, sealed: Boolean): JsonObject
    suspend fun sendMediaMessage(
        recipientUserId: String,
        conversationId: String,
        filePath: String,
        mimeType: String,
        fileName: String?,
        isViewOnce: Boolean
    ): JsonObject
    suspend fun sendReaction(
        conversationId: String,
        emoji: String,
        envelopeId: String?,
        messageLocalId: Long?
    ): JsonObject
    suspend fun sendSticker(
        recipientUserId: String,
        conversationId: String,
        packId: String,
        stickerId: String
    ): JsonObject

    // Contacts
    suspend fun listContacts(): JsonObject
    suspend fun addContact(userId: String, customName: String?): JsonObject
    suspend fun removeContact(userId: String): JsonObject
    suspend fun listBlockedUsers(): JsonObject

    // Groups
    suspend fun listGroups(): JsonObject
    suspend fun createGroup(
        name: String,
        description: String?,
        initialMemberIds: List<String>?,
        addMembersPolicy: String,
        joinType: String
    ): JsonObject
    suspend fun addGroupMembers(groupId: String, userIds: List<String>): JsonObject
    suspend fun joinGroupViaLink(linkCode: String): JsonObject

    // Calls
    suspend fun startCall(remoteUserId: String, isVideo: Boolean): JsonObject
    suspend fun listCallLog(limit: Int): JsonObject

    // Status (stories)
    suspend fun listStatusFeed(): JsonObject
    suspend fun createTextStatus(
        text: String,
        backgroundColor: String,
        privacy: String,
        selectedContacts: List<String>?
    ): JsonObject
    suspend fun createMediaStatus(
        mediaId: String,
        privacy: String,
        selectedContacts: List<String>?
    ): JsonObject
    suspend fun viewStatus(statusId: String): JsonObject

    // Stickers
    suspend fun listStickerLibrary(): JsonObject
    suspend fun listFeaturedStickers(): JsonObject
    suspend fun installStickerPack(packId: String): JsonObject

    // Backup (cloud + local encrypted)
    suspend fun backupCloudInitiate(): JsonObject
    suspend fun backupCloudLatest(): JsonObject
    suspend fun backupCloudRestore(backupId: String): JsonObject
    suspend fun backupLocalExport(outputPath: String, backupKeyB64: String): JsonObject
    suspend fun backupLocalImport(inputPath: String, backupKeyB64: String, sections: List<String>): JsonObject

    // App lock PIN
    suspend fun appLockSetPin(pin: String): JsonObject
    suspend fun appLockVerifyPin(pin: String): JsonObject
    suspend fun appLockDisable(): JsonObject

    // Network / crypto
    suspend fun getNetworkStatus(): JsonObject
    suspend fun connectWebSocket(): JsonObject
    suspend fun disconnectWebSocket(): JsonObject
    suspend fun getCryptoStatus(): JsonObject
    suspend fun testJniSequence(): JsonObject
    suspend fun resetSession(userId: String): JsonObject
}
