package org.enchant.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Tracks what screen/flow the user (or agent) is on — updated by UI and navigation commands.
 */
object AgentUiTracker {
    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    data class AgentUiState(
        val phase: String = "unknown",
        val authRoute: String? = null,
        val mainTab: String? = null,
        val mainDetail: String? = null,
        val mainDetailParams: JsonObject? = null,
        val lastAction: String? = null
    )

    fun setPhase(phase: String) {
        _state.value = _state.value.copy(phase = phase)
    }

    fun setAuthRoute(route: String) {
        _state.value = _state.value.copy(phase = "auth", authRoute = route)
        AgentEventLog.emit("ui_auth_route", data = buildJsonObject { put("route", route) })
    }

    fun setMainNavigation(tab: String, detail: String? = null, params: JsonObject? = null) {
        _state.value = _state.value.copy(
            phase = "main",
            mainTab = tab,
            mainDetail = detail,
            mainDetailParams = params
        )
        AgentEventLog.emit(
            "ui_main_nav",
            data = buildJsonObject {
                put("tab", tab)
                if (detail != null) put("detail", detail)
            }
        )
    }

    fun recordAction(action: String) {
        _state.value = _state.value.copy(lastAction = action)
    }

    fun toJson(): JsonObject = buildJsonObject {
        val s = _state.value
        put("phase", s.phase)
        s.authRoute?.let { put("auth_route", it) }
        s.mainTab?.let { put("main_tab", it) }
        s.mainDetail?.let { put("main_detail", it) }
        s.lastAction?.let { put("last_action", it) }
    }
}
