package org.enchant.core.crypto

object SodiumProvider {
    private var initialized = false

    suspend fun init() {
        if (initialized) return
        initialized = true
    }

    val isInitialized: Boolean get() = initialized
}
