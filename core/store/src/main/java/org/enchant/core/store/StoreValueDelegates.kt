package org.enchant.core.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Kotlin property delegates for [KeyValueStorage] with reactive [Flow] support.
 *
 * Supports:
 * - Primitive types: String, Int, Long, Boolean, Float, ByteArray
 * - Enum types with serializers
 * - Protobuf messages with adapters
 * - Precondition gating (only store if predicate passes)
 * - Value mapping (transform on read)
 *
 * Usage:
 * ```
 * val userId by delegates.stringValue("account.user_id")
 * val theme by delegates.enumValue("settings.theme", Theme.SYSTEM, Theme.serializer)
 * val metadata by delegates.protoValue("settings.meta", Metadata.ADAPTER)
 *
 * // With precondition:
 * var experimental by delegates.booleanValue("labs.exp", false).withPrecondition { Environment.IS_STAGING }
 *
 * // With mapping:
 * var feature by delegates.booleanValue("labs.feature", false).map { it && RemoteConfig.isEnabled }
 *
 * // Reactive:
 * delegates.observe("account.user_id").collect { ... }
 * themeDelegate.toFlow().collect { ... }
 * ```
 */
class StoreValueDelegates(
    private val store: KeyValueStorage
) {
    private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()

    fun stringValue(key: String, defaultValue: String? = null): StoreValueDelegate<String?> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getString(key, defaultValue) },
            setter = { v -> store.putString(key, v) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    fun intValue(key: String, defaultValue: Int = 0): StoreValueDelegate<Int> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getInt(key, defaultValue) },
            setter = { store.putInt(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    fun longValue(key: String, defaultValue: Long = 0L): StoreValueDelegate<Long> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getLong(key, defaultValue) },
            setter = { store.putLong(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    fun booleanValue(key: String, defaultValue: Boolean = false): StoreValueDelegate<Boolean> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getBoolean(key, defaultValue) },
            setter = { store.putBoolean(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    fun floatValue(key: String, defaultValue: Float = 0f): StoreValueDelegate<Float> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getFloat(key, defaultValue) },
            setter = { store.putFloat(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    fun blobValue(key: String, defaultValue: ByteArray? = null): StoreValueDelegate<ByteArray?> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = { store.getBlob(key, defaultValue) },
            setter = { store.putBlob(key, it) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    fun <T : Enum<T>> enumValue(
        key: String,
        defaultValue: T,
        serializer: EnumSerializer<T>
    ): StoreValueDelegate<T> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = {
                val raw = store.getString(key)
                if (raw != null) serializer.deserialize(raw) else defaultValue
            },
            setter = { store.putString(key, serializer.serialize(it)) },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    fun <T> protoValue(
        key: String,
        defaultValue: T? = null,
        adapter: ProtoAdapter<T>
    ): StoreValueDelegate<T?> {
        return StoreValueDelegate(
            key = key,
            default = defaultValue,
            getter = {
                val raw = store.getBlob(key)
                if (raw != null) adapter.decode(raw) else defaultValue
            },
            setter = { v ->
                if (v != null) store.putBlob(key, adapter.encode(v))
                else store.remove(key)
            },
            flow = getOrCreateFlow(key, defaultValue)
        )
    }

    fun <T : Any?> observe(key: String): Flow<T?> {
        val flow = getOrCreateFlow(key, null) as MutableStateFlow<T?>
        return flow.asStateFlow()
    }

    fun <T : Any> observe(key: String, default: T): Flow<T> {
        return observe<T>(key).map { it ?: default }
    }

    internal fun emitValue(key: String, value: Any?) {
        flows[key]?.value = value
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any?> getOrCreateFlow(key: String, default: T?): MutableStateFlow<T?> {
        return flows.getOrPut(key) {
            val currentValue = store.getAll()[key] as? T?
            MutableStateFlow(currentValue ?: default)
        } as MutableStateFlow<T?>
    }

    interface EnumSerializer<T : Enum<T>> {
        fun serialize(value: T): String
        fun deserialize(raw: String): T
    }

    interface ProtoAdapter<T> {
        fun encode(value: T): ByteArray
        fun decode(data: ByteArray): T
    }

    class StoreValueDelegate<T : Any?> internal constructor(
        private val key: String,
        private val default: T,
        private val getter: () -> T,
        private val setter: (T) -> Unit,
        private val flow: MutableStateFlow<T?>
    ) : ReadWriteProperty<Any?, T> {

        private var precondition: (() -> Boolean)? = null
        private var mapper: ((T) -> T)? = null

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            val raw = getter()
            return mapper?.invoke(raw) ?: raw
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            val shouldWrite = precondition?.invoke() ?: true
            if (shouldWrite) {
                setter(value)
                flow.value = value
            }
        }

        fun toFlow(): Flow<T> = flow.asStateFlow().map { it ?: default }

        fun withPrecondition(predicate: () -> Boolean): StoreValueDelegate<T> {
            this.precondition = predicate
            return this
        }

        fun map(transform: (T) -> T): StoreValueDelegate<T> {
            val existing = this.mapper
            this.mapper = if (existing != null) {
                { transform(existing(it)) }
            } else {
                transform
            }
            return this
        }

        fun withFlowPrecondition(predicate: (T) -> Boolean): Flow<T> {
            return toFlow().filter { predicate(it) }
        }

        fun <R> mapFlow(transform: (T) -> R): Flow<R> {
            return toFlow().map(transform)
        }
    }
}
