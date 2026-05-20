package org.enchant.core.base

import kotlin.reflect.KProperty

/**
 * Creates a resettable lazy delegate.
 *
 * Unlike Kotlin's built-in `lazy`, this delegate can be reset to its
 * uninitialized state, causing the initializer to run again on next access.
 */
fun <T> resettableLazy(initializer: () -> T): ResettableLazy<T> {
    return ResettableLazy(initializer)
}

/**
 * A lazy delegate that can be reset to re-run the initializer on next access.
 *
 * Thread-safe with double-checked locking.
 */
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

    /**
     * Resets the delegate to its uninitialized state.
     * The next access will re-run the initializer.
     */
    fun reset() {
        value = UNINITIALIZED
    }

    /**
     * Returns true if the value has been initialized.
     */
    fun isInitialized(): Boolean {
        return value !== UNINITIALIZED
    }

    override fun toString(): String {
        return if (isInitialized()) value.toString() else "Lazy value not initialized yet."
    }

    companion object {
        private val UNINITIALIZED = Any()
    }
}
