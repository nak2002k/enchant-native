package org.enchant.main

import androidx.lifecycle.ViewModel
import org.enchant.MainNavigationDetailLocation
import org.enchant.MainNavigationListLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MainNavigationState(
    val currentListLocation: MainNavigationListLocation = MainNavigationListLocation.CHATS,
    val currentDetailLocation: MainNavigationDetailLocation = MainNavigationDetailLocation.ConversationList,
    val chatsCount: Int = 0,
    val callsCount: Int = 0,
    val storiesCount: Int = 0,
    val compact: Boolean = false,
    val isStoriesFeatureEnabled: Boolean = false
)

class MainNavigationViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainNavigationState())
    val state: StateFlow<MainNavigationState> = _state.asStateFlow()

    fun goToList(location: MainNavigationListLocation) {
        _state.value = _state.value.copy(currentListLocation = location)
    }

    fun goToDetail(detail: MainNavigationDetailLocation) {
        _state.value = _state.value.copy(currentDetailLocation = detail)
    }

    fun toggleCompact() {
        _state.value = _state.value.copy(compact = !_state.value.compact)
    }

    fun updateChatsCount(count: Int) {
        _state.value = _state.value.copy(chatsCount = count)
    }

    fun updateCallsCount(count: Int) {
        _state.value = _state.value.copy(callsCount = count)
    }

    fun updateStoriesCount(count: Int) {
        _state.value = _state.value.copy(storiesCount = count)
    }

    fun toggleStoriesFeature() {
        _state.value = _state.value.copy(isStoriesFeatureEnabled = !_state.value.isStoriesFeatureEnabled)
    }
}