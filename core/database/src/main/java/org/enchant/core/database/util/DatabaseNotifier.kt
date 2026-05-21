package org.enchant.core.database.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DatabaseNotifier {
    private val _tableChanges = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)
    val tableChanges: SharedFlow<String> = _tableChanges.asSharedFlow()

    fun notify(table: String) {
        _tableChanges.tryEmit(table)
    }
}
