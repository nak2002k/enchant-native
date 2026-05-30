package org.enchant.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

object LocalResultEventBus {
    private val LocalResultEventBus: ProvidableCompositionLocal<ResultEventBus?> =
        compositionLocalOf { null }

    val current: ResultEventBus
        @Composable
        get() = LocalResultEventBus.current
            ?: error("No ResultEventBus has been provided")

    infix fun provides(bus: ResultEventBus): ProvidedValue<ResultEventBus?> {
        return LocalResultEventBus.provides(bus)
    }
}

class ResultEventBus {
    @PublishedApi
    internal val channelMap: MutableMap<String, Channel<Any?>> = mutableMapOf()

    fun hasChannel(resultKey: String): Boolean = channelMap.containsKey(resultKey)

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> getResultFlow(
        resultKey: String = T::class.toString()
    ): Flow<T>? = channelMap[resultKey]?.receiveAsFlow() as Flow<T>?

    inline fun <reified T> sendResult(
        resultKey: String = T::class.toString(),
        result: T
    ) {
        if (!channelMap.containsKey(resultKey)) {
            channelMap[resultKey] = Channel(
                capacity = BUFFERED,
                onBufferOverflow = BufferOverflow.SUSPEND
            )
        }
        channelMap[resultKey]?.trySend(result)
    }

    inline fun <reified T> removeResult(
        resultKey: String = T::class.toString()
    ) {
        channelMap.remove(resultKey)
    }
}