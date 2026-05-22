package org.enchant.chatlist

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface ChatListNavKey : NavKey {

    @Serializable
    data object ConversationList : ChatListNavKey

    @Serializable
    data class ArchiveList(
        val includeArchived: Boolean = true
    ) : ChatListNavKey
}
