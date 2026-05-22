package org.enchant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
enum class MainNavigationListLocation : NavKey {
    CHATS,
    CALLS,
    STORIES,
    ARCHIVE
}