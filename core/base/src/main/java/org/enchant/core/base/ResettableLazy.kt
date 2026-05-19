package org.enchant.core.base

import kotlin.reflect.KProperty

fun <T> resettableLazy(initializer: () -> T): ResettableLazy<T> {
    return ResettableLazy(initializer)
}

class ResettableLazy<T>(
    val initializer: () -> T
) {
    @Volatile
    private var value: Any? = UNINITIALIZED

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (value === UNINITIALIZED) {
            synchronized(this) {
                if (value === UNINITIALIZED) {
                    value = initializer()
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    fun reset() {
        value = UNINITIALIZED
    }

    fun isInitialized(): Boolean {
        return value !== UNINITIALIZED
    }

    companion object {
        private val UNINITIALIZED = Any()
    }
}
