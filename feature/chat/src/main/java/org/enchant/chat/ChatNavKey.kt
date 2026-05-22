package org.enchant.chat

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface ChatNavKey : NavKey {

    @Serializable
    data class Conversation(
        val threadId: Long
    ) : ChatNavKey

    @Serializable
    data class MediaViewer(
        val messageId: Long,
        val attachmentId: Long
    ) : ChatNavKey
}
