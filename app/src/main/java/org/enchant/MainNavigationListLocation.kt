package org.enchant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
enum class MainNavigationListLocation : NavKey {
    STATUS,
    CALLS,
    CHATS,
    SETTINGS,
    ARCHIVE
}