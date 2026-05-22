package org.enchant.chat

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

internal fun NavBackStack<NavKey>.goToConversation(threadId: Long) {
    val key = ChatNavKey.Conversation(threadId = threadId)
    if (contains(key)) {
        while (size > 1 && get(size - 1) != key) {
            removeAt(size - 1)
        }
    } else {
        add(key)
    }
}
