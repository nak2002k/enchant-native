package org.enchant.agent

import android.content.Context
import kotlinx.serialization.json.jsonPrimitive
import org.enchant.MainNavigationDetailLocation
import org.enchant.MainNavigationListLocation
import org.enchant.MainNavigationViewModel

/**
 * Runtime hooks shared between debug bridge and the Compose UI (debug builds only).
 */
object AgentRuntime {
    var onSetAuthFlowComplete: ((Boolean) -> Unit)? = null
    var mainNavigationViewModel: MainNavigationViewModel? = null
}

object AgentDebugSetup {

    fun init(context: Context) {
        AgentNavigationHooks.onShowMainApp = {
            AgentRuntime.onSetAuthFlowComplete?.invoke(true)
            AgentUiTracker.setPhase("main")
        }
        AgentNavigationHooks.onShowAuthFlow = {
            AgentRuntime.onSetAuthFlowComplete?.invoke(false)
            AgentUiTracker.setPhase("auth")
        }
        AgentNavigationHooks.onOpenMainTab = { tab ->
            AgentRuntime.onSetAuthFlowComplete?.invoke(true)
            val location = when (tab.uppercase()) {
                "CALLS" -> MainNavigationListLocation.CALLS
                "STORIES" -> MainNavigationListLocation.STORIES
                "ARCHIVE" -> MainNavigationListLocation.ARCHIVE
                else -> MainNavigationListLocation.CHATS
            }
            AgentRuntime.mainNavigationViewModel?.goTo(location)
            AgentUiTracker.setMainNavigation(tab.lowercase())
        }
        AgentNavigationHooks.onOpenMainDetail = mainDetail@{ detail, params ->
            AgentRuntime.onSetAuthFlowComplete?.invoke(true)
            val vm = AgentRuntime.mainNavigationViewModel ?: return@mainDetail
            when (detail.lowercase()) {
                "conversation", "chat" -> {
                    val id = params["conversation_id"]?.jsonPrimitive?.content
                        ?: return@mainDetail
                    vm.goTo(MainNavigationListLocation.CHATS)
                    vm.goTo(MainNavigationDetailLocation.Conversation(id))
                    AgentUiTracker.setMainNavigation("chats", "conversation", params)
                }
                "settings" -> {
                    vm.goTo(MainNavigationDetailLocation.Settings)
                    AgentUiTracker.setMainNavigation("chats", "settings")
                }
                "contacts" -> {
                    vm.goTo(MainNavigationDetailLocation.Contacts)
                    AgentUiTracker.setMainNavigation("chats", "contacts")
                }
                "security_settings" -> {
                    vm.goTo(MainNavigationDetailLocation.SecuritySettings)
                    AgentUiTracker.setMainNavigation("chats", "security_settings")
                }
                "profile" -> {
                    val userId = params["user_id"]?.jsonPrimitive?.content ?: return@mainDetail
                    vm.goTo(MainNavigationDetailLocation.Profile(userId))
                    AgentUiTracker.setMainNavigation("chats", "profile", params)
                }
                "groups" -> {
                    vm.goTo(MainNavigationDetailLocation.Groups)
                    AgentUiTracker.setMainNavigation("chats", "groups")
                }
                "create_group" -> {
                    vm.goTo(MainNavigationDetailLocation.CreateGroup)
                    AgentUiTracker.setMainNavigation("chats", "create_group")
                }
                "group_info" -> {
                    val groupId = params["group_id"]?.jsonPrimitive?.content ?: return@mainDetail
                    vm.goTo(MainNavigationDetailLocation.GroupInfo(groupId))
                    AgentUiTracker.setMainNavigation("chats", "group_info", params)
                }
                "status_feed" -> {
                    vm.goTo(MainNavigationListLocation.STORIES)
                    vm.goTo(MainNavigationDetailLocation.StatusFeed)
                    AgentUiTracker.setMainNavigation("stories", "status_feed")
                }
                "status_create" -> {
                    vm.goTo(MainNavigationListLocation.STORIES)
                    vm.goTo(MainNavigationDetailLocation.StatusCreate)
                    AgentUiTracker.setMainNavigation("stories", "status_create")
                }
                "stickers" -> {
                    vm.goTo(MainNavigationDetailLocation.Stickers)
                    AgentUiTracker.setMainNavigation("chats", "stickers")
                }
                "backup_settings" -> {
                    vm.goTo(MainNavigationDetailLocation.BackupSettings)
                    AgentUiTracker.setMainNavigation("chats", "backup_settings")
                }
                else -> AgentUiTracker.setMainNavigation("chats", detail, params)
            }
        }
        AgentDebug.start(
            context.applicationContext,
            EnchantAgentBridge(),
            org.enchant.BuildConfig.AGENT_PORT
        )
    }
}
