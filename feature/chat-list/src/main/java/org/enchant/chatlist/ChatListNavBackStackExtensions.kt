package org.enchant.chatlist

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

internal fun NavBackStack<NavKey>.goToArchive() {
    if (contains(ChatListNavKey.ArchiveList)) {
        while (size > 1 && get(size - 1) !is ChatListNavKey.ArchiveList) {
            removeAt(size - 1)
        }
    } else {
        add(ChatListNavKey.ArchiveList())
    }
}
