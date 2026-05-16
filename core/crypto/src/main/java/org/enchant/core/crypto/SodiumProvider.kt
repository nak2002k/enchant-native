package org.enchant.core.crypto

object SodiumProvider {
    private var initialized = false

    suspend fun init() {
        if (initialized) return
        try {
            System.loadLibrary("sodium")
            initialized = true
        } catch (_: UnsatisfiedLinkError) {
            initialized = false
        }
    }

    fun sodiumMemZero(bytes: ByteArray) {
        if (initialized) {
            bytes.fill(0)
        } else {
            CryptoHelper.zeroBytes(bytes)
        }
    }

    fun sodiumMlock(bytes: ByteArray) {
    }

    fun sodiumMunlock(bytes: ByteArray) {
    }

    val isInitialized: Boolean get() = initialized
}
