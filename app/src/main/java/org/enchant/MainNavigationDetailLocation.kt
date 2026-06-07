package org.enchant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainNavigationDetailLocation : NavKey {
    @Serializable data object Empty : MainNavigationDetailLocation
    @Serializable data object ConversationList : MainNavigationDetailLocation
    @Serializable data class Conversation(val conversationId: String) : MainNavigationDetailLocation
    @Serializable data object Groups : MainNavigationDetailLocation
    @Serializable data object Settings : MainNavigationDetailLocation
    @Serializable data object Status : MainNavigationDetailLocation
    @Serializable data object Archive : MainNavigationDetailLocation

    @Serializable
    sealed interface Chats : MainNavigationDetailLocation {
        @Serializable data class MessageDetails(val recipientId: String, val messageId: Long) : Chats
        @Serializable data class ConversationSettings(val recipientId: String) : Chats
    }

    @Serializable
    sealed interface Calls : MainNavigationDetailLocation {
        @Serializable data class EditCallLinkName(val callLinkRoomId: String) : Calls
        @Serializable data class CallDetail(val callId: String) : Calls
    }

    // Settings sub-routes
    @Serializable data object AccountSettings : MainNavigationDetailLocation
    @Serializable data object SecuritySettings : MainNavigationDetailLocation
    @Serializable data object PrivacySettings : MainNavigationDetailLocation
    @Serializable data object NotificationSettings : MainNavigationDetailLocation
    @Serializable data object AppearanceSettings : MainNavigationDetailLocation
    @Serializable data object ChatsSettings : MainNavigationDetailLocation
    @Serializable data object StorageSettings : MainNavigationDetailLocation
    @Serializable data object About : MainNavigationDetailLocation
    @Serializable data object BackupSettings : MainNavigationDetailLocation
    @Serializable data object BlockedUsers : MainNavigationDetailLocation

    // Other routes
    @Serializable data object Contacts : MainNavigationDetailLocation
    @Serializable data object CreateGroup : MainNavigationDetailLocation
    @Serializable data class GroupInfo(val groupId: String) : MainNavigationDetailLocation
    @Serializable data object Channels : MainNavigationDetailLocation
    @Serializable data object Stickers : MainNavigationDetailLocation
    @Serializable data class Profile(val userId: String) : MainNavigationDetailLocation
    @Serializable data object StatusFeed : MainNavigationDetailLocation
    @Serializable data object StatusCreate : MainNavigationDetailLocation
    @Serializable data class StatusViewer(val statusId: String) : MainNavigationDetailLocation
}
