package org.enchant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainNavKey : NavKey {

    @Serializable data object Contacts : MainNavKey
    @Serializable data object CreateGroup : MainNavKey
    @Serializable data object Groups : MainNavKey
    @Serializable data class GroupInfo(val groupId: String) : MainNavKey
    @Serializable data object JoinRequests : MainNavKey
    @Serializable data object Settings : MainNavKey
    @Serializable data object AccountSettings : MainNavKey
    @Serializable data object SecuritySettings : MainNavKey
    @Serializable data object PrivacySettings : MainNavKey
    @Serializable data object NotificationSettings : MainNavKey
    @Serializable data object AppearanceSettings : MainNavKey
    @Serializable data object ChatsSettings : MainNavKey
    @Serializable data object StorageSettings : MainNavKey
    @Serializable data object About : MainNavKey
    @Serializable data object BackupSettings : MainNavKey
    @Serializable data object BlockedUsers : MainNavKey
    @Serializable data object StatusFeed : MainNavKey
    @Serializable data object StatusCreate : MainNavKey
    @Serializable data class StatusViewer(val statusId: String) : MainNavKey
    @Serializable data object ChannelsFeed : MainNavKey
    @Serializable data object Stickers : MainNavKey
    @Serializable data object Search : MainNavKey
    @Serializable data object QRCode : MainNavKey
    @Serializable data object QRScanner : MainNavKey
    @Serializable data class Profile(val userId: String) : MainNavKey
    @Serializable data object LocationPicker : MainNavKey
    @Serializable data class PollCreate(val conversationId: String) : MainNavKey
    @Serializable data object ShareTarget : MainNavKey
    @Serializable data class MediaViewer(val messageId: Long, val attachmentId: Long) : MainNavKey
    @Serializable data object AppLock : MainNavKey
    @Serializable data object PinCreation : MainNavKey
    @Serializable data object RestorePrompt : MainNavKey
}
