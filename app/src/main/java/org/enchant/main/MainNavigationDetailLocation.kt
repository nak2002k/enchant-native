package org.enchant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainNavigationDetailLocation : NavKey {
    @Serializable data object ConversationList : MainNavigationDetailLocation
    @Serializable data class Conversation(val threadId: Long) : MainNavigationDetailLocation
    @Serializable data object Chats : MainNavigationDetailLocation
    @Serializable data object Calls : MainNavigationDetailLocation
    @Serializable data object Settings : MainNavigationDetailLocation
    @Serializable data object Groups : MainNavigationDetailLocation
    @Serializable data object Status : MainNavigationDetailLocation
    @Serializable data object Archive : MainNavigationDetailLocation
}