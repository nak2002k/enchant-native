package org.enchant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainNavigationDetailLocation : NavKey {
    @Serializable data object Empty : MainNavigationDetailLocation
    @Serializable data object ConversationList : MainNavigationDetailLocation
    @Serializable data class Conversation(val threadId: Long) : MainNavigationDetailLocation
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
        @Serializable sealed class CallLinks : Calls {
            @Serializable data class EditCallLinkName(val callLinkRoomId: String) : CallLinks()
        }
    }
}