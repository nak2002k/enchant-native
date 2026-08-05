package org.enchant.agent

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject

/** Navigation commands emitted by the debug API and consumed by MainActivity / Nav displays. */
sealed class AgentNavCommand {
    data object ShowMainApp : AgentNavCommand()
    data object ShowAuthFlow : AgentNavCommand()
    data class OpenMainTab(val tab: String) : AgentNavCommand()
    data class OpenMainDetail(val detail: String, val params: JsonObject = JsonObject(emptyMap())) : AgentNavCommand()
    data class OpenAuthScreen(val route: String) : AgentNavCommand()
}

/**
 * Hooks registered by the app module so agent commands can drive real navigation.
 */
object AgentNavigationHooks {
    var onShowMainApp: (() -> Unit)? = null
    var onShowAuthFlow: (() -> Unit)? = null
    var onOpenMainTab: ((String) -> Unit)? = null
    var onOpenMainDetail: ((String, JsonObject) -> Unit)? = null

    private val _commands = MutableSharedFlow<AgentNavCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<AgentNavCommand> = _commands.asSharedFlow()

    suspend fun emit(command: AgentNavCommand) {
        _commands.emit(command)
        when (command) {
            is AgentNavCommand.ShowMainApp -> onShowMainApp?.invoke()
            is AgentNavCommand.ShowAuthFlow -> onShowAuthFlow?.invoke()
            is AgentNavCommand.OpenMainTab -> onOpenMainTab?.invoke(command.tab)
            is AgentNavCommand.OpenMainDetail -> onOpenMainDetail?.invoke(command.detail, command.params)
            is AgentNavCommand.OpenAuthScreen -> AgentUiTracker.setAuthRoute(command.route)
        }
    }
}
