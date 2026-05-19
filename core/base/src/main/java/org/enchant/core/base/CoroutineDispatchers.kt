package org.enchant.core.base

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

object CoroutineDispatchers {

    private var provider: DispatcherProvider = DefaultDispatcherProvider

    fun setProvider(provider: DispatcherProvider = DefaultDispatcherProvider) {
        this.provider = provider
    }

    val io: CoroutineDispatcher get() = provider.io
    val network: CoroutineDispatcher get() = provider.network
    val crypto: CoroutineDispatcher get() = provider.crypto
    val computation: CoroutineDispatcher get() = provider.computation
    val main: CoroutineDispatcher get() = provider.main

    interface DispatcherProvider {
        val io: CoroutineDispatcher
        val network: CoroutineDispatcher
        val crypto: CoroutineDispatcher
        val computation: CoroutineDispatcher
        val main: CoroutineDispatcher
    }

    private object DefaultDispatcherProvider : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val network: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)
        override val crypto: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
        override val computation: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Main
    }
}
