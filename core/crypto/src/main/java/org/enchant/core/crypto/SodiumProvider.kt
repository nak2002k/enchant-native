package org.enchant.core.crypto

object SodiumProvider {
    private var initialized = false

    suspend fun init() {
        if (initialized) return
        initialized = true
    }

    fun sodiumMemZero(bytes: ByteArray) {
        CryptoHelper.zeroBytes(bytes)
    }

    fun sodiumMlock(bytes: ByteArray) {
        // No-op: libsodium JNI not bundled. Memory stays on Java heap.
    }

    fun sodiumMunlock(bytes: ByteArray) {
        // No-op: libsodium JNI not bundled.
    }

    val isInitialized: Boolean get() = initialized
}