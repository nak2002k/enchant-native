package org.enchant.registration.olddevice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class TransferState(
    val isTransferring: Boolean = false,
    val error: String? = null,
    val progress: Float = 0f
)

sealed interface TransferEvent {
    data object TransferClicked : TransferEvent
    data object BackUpNow : TransferEvent
    data object SkipAndContinue : TransferEvent
    data object NavigateBack : TransferEvent
    data object Retry : TransferEvent
}

class TransferViewModel : ViewModel() {

    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private val _backStack = MutableStateFlow(
        listOf<TransferNavKey>(TransferNavKey.Transfer)
    )
    val backStack: StateFlow<List<TransferNavKey>> = _backStack.asStateFlow()

    private val _finishRequests = MutableSharedFlow<Unit>()
    val finishRequests: SharedFlow<Unit> = _finishRequests.asSharedFlow()

    fun onEvent(event: TransferEvent) {
        when (event) {
            TransferEvent.TransferClicked -> {
                _backStack.value = _backStack.value + TransferNavKey.PrepareDevice
            }
            TransferEvent.BackUpNow -> {
                _state.value = _state.value.copy(isTransferring = true)
            }
            TransferEvent.SkipAndContinue -> {
                _backStack.value = _backStack.value + TransferNavKey.Done
            }
            TransferEvent.NavigateBack -> goBack()
            TransferEvent.Retry -> {
                _state.value = _state.value.copy(error = null, isTransferring = false)
            }
        }
    }

    fun goBack() {
        val current = _backStack.value.toMutableList()
        if (current.size > 1) {
            current.removeAt(current.size - 1)
            _backStack.value = current
        }
    }
}

@Composable
fun TransferScreen(
    state: TransferState,
    onEvent: (TransferEvent) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun PrepareDeviceScreen(
    state: TransferState,
    onEvent: (TransferEvent) -> Unit,
    modifier: Modifier = Modifier
) {
}
