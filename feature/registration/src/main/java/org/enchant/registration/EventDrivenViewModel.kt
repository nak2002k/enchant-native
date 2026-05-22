package org.enchant.registration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.launch

abstract class EventDrivenViewModel<E>(private val tag: String) : ViewModel() {

    protected val eventChannel: Channel<E> = Channel(capacity = BUFFERED)

    init {
        viewModelScope.launch {
            for (event in eventChannel) {
                processEvent(event)
            }
        }
    }

    fun onEvent(event: E) {
        viewModelScope.launch {
            eventChannel.send(event)
        }
    }

    protected abstract suspend fun processEvent(event: E)
}
