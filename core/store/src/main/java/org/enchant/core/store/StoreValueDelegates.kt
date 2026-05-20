package org.enchant.core.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Kotlin property delegates for [KeyValueStore] with reactive [Flow] support.
 *
 * Usage inside a category object:
 * ```
 * val userId by store.stringValue("account.user_id")
 * val readReceipts by store.booleanValue("settings.read_receipts", true)
 * ```
 *
 * Each delegate maintains a [MutableStateFlow] so UI can observe changes:
 * ```
 * store.observe("account.user_id").collect { value -> ... }
 * ```
 */
class StoreValueDelegates(
    private val store: KeyValueStorage
) {
    private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

    // -- String delegate ------------------------------------------------------------------

    fun stringValue(key: String, defaultValue: String? = null): StoreValueDelegate<String?> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getString(key, defaultValue) },
            setter = { store.putString(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    // -- Int delegate ---------------------------------------------------------------------

    fun intValue(key: String, defaultValue: Int = 0): StoreValueDelegate<Int> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getInt(key, defaultValue) },
            setter = { store.putInt(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    // -- Long delegate --------------------------------------------------------------------

    fun longValue(key: String, defaultValue: Long = 0L): StoreValueDelegate<Long> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getLong(key, defaultValue) },
            setter = { store.putLong(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    // -- Boolean delegate -----------------------------------------------------------------

    fun booleanValue(key: String, defaultValue: Boolean = false): StoreValueDelegate<Boolean> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getBoolean(key, defaultValue) },
            setter = { store.putBoolean(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    // -- Float delegate -------------------------------------------------------------------

    fun floatValue(key: String, defaultValue: Float = 0f): StoreValueDelegate<Float> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getFloat(key, defaultValue) },
            setter = { store.putFloat(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    // -- Blob delegate --------------------------------------------------------------------

    fun blobValue(key: String, defaultValue: ByteArray? = null): StoreValueDelegate<ByteArray?> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getBlob(key, defaultValue) },
            setter = { store.putBlob(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    // -- Flow observation -----------------------------------------------------------------

    /**
     * Returns a [Flow] that emits the current value and every subsequent change for [key].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any?> observe(key: String): Flow<T?> {
        val flow = getOrCreateFlow(key, null) as MutableStateFlow<T?>
        return flow.asStateFlow()
    }

    /**
     * Returns a typed [Flow] with a default value for null-safety.
     */
    fun <T : Any> observe(key: String, default: T): Flow<T> {
        return observe<T>(key).map { it ?: default }
    }

    /**
     * Internal: emit a new value to the flow for [key].
     * Called by delegates after each write.
     */
    internal fun emitValue(key: String, value: Any?) {
        flows[key]?.value = value
    }

    // -- Internal -------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any?> getOrCreateFlow(key: String, default: T?): MutableStateFlow<T?> {
        return flows.getOrPut(key) {
            val currentValue = store.getAll()[key] as? T?
            MutableStateFlow(currentValue ?: default)
        } as MutableStateFlow<T?>
    }

    /**
     * A [ReadWriteProperty] backed by [KeyValueStore] that emits to a [Flow] on every write.
     */
    class StoreValueDelegate<T : Any?> internal constructor(
        private val key: String,
        private val default: T,
        private val getter: () -> T,
        private val setter: (T) -> Unit,
        private val flow: MutableStateFlow<T?>
    ) : ReadWriteProperty<Any?, T> {

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return getter()
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            setter(value)
            flow.value = value
        }

        /**
         * Returns the underlying [Flow] for reactive observation.
         */
        fun toFlow(): Flow<T> = flow.asStateFlow().map { it ?: default }

        /**
         * Returns a flow that only emits when a precondition is met.
         */
        fun withPrecondition(predicate: () -> Boolean): Flow<T> {
            return toFlow().filter { predicate() }
        }

        /**
         * Returns a flow that maps the value through a transformation.
         */
        fun <R> map(transform: (T) -> R): Flow<R> {
            return toFlow().map(transform)
        }
    }
}
