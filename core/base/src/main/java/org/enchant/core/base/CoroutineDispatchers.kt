package org.enchant.core.base

import android.content.Context
import kotlinx.coroutines.Dispatchers

object CoroutineDispatchers {
    val io = Dispatchers.IO
    val network = Dispatchers.IO.limitedParallelism(4)
    val crypto = Dispatchers.Default.limitedParallelism(1)
    val computation = Dispatchers.Default
    val main = Dispatchers.Main
}
