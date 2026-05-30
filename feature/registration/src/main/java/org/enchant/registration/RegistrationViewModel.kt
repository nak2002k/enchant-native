package org.enchant.registration

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation3.runtime.NavBackStack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.enchant.core.ui.navigation.ResultEventBus

class RegistrationViewModel(
    private val repository: Any
) : EventDrivenViewModel<RegistrationFlowEvent>("RegistrationViewModel") {

    private val _state = MutableStateFlow(RegistrationFlowState())
    val state: StateFlow<RegistrationFlowState> = _state.asStateFlow()

    private val _finishRequests = MutableSharedFlow<Unit>()
    val finishRequests: SharedFlow<Unit> = _finishRequests.asSharedFlow()

    val resultBus: ResultEventBus = ResultEventBus()

    override suspend fun processEvent(event: RegistrationFlowEvent) {
        val newState = applyEvent(_state.value, event)
        _state.value = newState
    }

    suspend fun applyEvent(
        state: RegistrationFlowState,
        event: RegistrationFlowEvent
    ): RegistrationFlowState = when (event) {
        is RegistrationFlowEvent.ResetState -> RegistrationFlowState(isRestoringNavigationState = false)
        is RegistrationFlowEvent.NavigateToScreen -> applyNavigationToScreenEvent(state, event)
        is RegistrationFlowEvent.NavigateBack -> {
            val newBackStack = state.backStack.toMutableList()
            if (newBackStack.size > 1) newBackStack.removeAt(newBackStack.size - 1)
            state.copy(backStack = NavBackStack(*newBackStack.toTypedArray()))
        }
        is RegistrationFlowEvent.SessionUpdated -> state.copy(sessionMetadata = event.session)
        is RegistrationFlowEvent.E164Chosen -> state.copy(sessionE164 = event.e164)
        is RegistrationFlowEvent.Registered -> state.copy(accountEntropyPool = event.accountEntropyPool)
        is RegistrationFlowEvent.MasterKeyRestoredFromSvr -> state.copy(temporaryMasterKey = event.masterKey)
        is RegistrationFlowEvent.RegistrationComplete -> {
            if (state.backStack.lastOrNull() != RegistrationNavKey.FullyComplete) {
                _finishRequests.tryEmit(Unit)
                applyNavigationToScreenEvent(state, RegistrationFlowEvent.NavigateToScreen(RegistrationNavKey.FullyComplete))
            } else {
                state
            }
        }
        is RegistrationFlowEvent.PendingRestoreOptionSelected -> state
        is RegistrationFlowEvent.UserSuppliedAepSubmitted -> state
        is RegistrationFlowEvent.UserSuppliedAepVerified -> state
        is RegistrationFlowEvent.RecoveryPasswordInvalid -> state
        else -> state
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyNavigationToScreenEvent(
        state: RegistrationFlowState,
        event: RegistrationFlowEvent.NavigateToScreen
    ): RegistrationFlowState {
        val newBackStack = state.backStack.toMutableList()
        newBackStack.add(event.route as RegistrationNavKey)
        return state.copy(backStack = NavBackStack(*newBackStack.toTypedArray()))
    }

    class Factory(private val repository: Any) : ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras
        ): T {
            if (modelClass.isAssignableFrom(RegistrationViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return RegistrationViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
