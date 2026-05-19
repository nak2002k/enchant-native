package org.enchant.core.base

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration

fun <T> Flow<T>.throttleLatest(timeout: Duration, emitImmediately: (T) -> Boolean = { false }): Flow<T> {
    val rootFlow = this
    return channelFlow {
        rootFlow
            .onEach { if (emitImmediately(it)) send(it) }
            .filterNot { emitImmediately(it) }
            .conflate()
            .collect {
                send(it)
                delay(timeout)
            }
    }
}
