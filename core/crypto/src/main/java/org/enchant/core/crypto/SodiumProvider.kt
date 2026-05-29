package org.enchant.core.crypto

/**
 * libenchantcrypto / libsodium provider.
 *
 * Delegates to [EnchantCrypto] JNI bridge for all native operations.
 * [init] must be called once at app startup to initialize libsodium.
 */
object SodiumProvider {
    private var initialized = false

    suspend fun init() {
        if (initialized) return
        CryptoPrimitives.init()
        initialized = true
    }

    fun sodiumMemZero(bytes: ByteArray) {
        CryptoPrimitives.zeroBytes(bytes)
    }

    fun sodiumMlock(bytes: ByteArray) {
        // libsodium mlock is not exposed via JNI.
    }

    fun sodiumMunlock(bytes: ByteArray) {
        // libsodium munlock is not exposed via JNI.
    }

    val isInitialized: Boolean get() = initialized
}
