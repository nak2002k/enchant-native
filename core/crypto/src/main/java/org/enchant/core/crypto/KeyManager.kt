package org.enchant.core.crypto

import org.enchant.core.base.KeyStoreManager
import org.enchant.core.base.SecurePreferences
import org.enchant.core.database.AppDatabase
import org.enchant.core.database.dao.SessionDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object KeyManager {
    private val mutex = Mutex()
    private var initialized = false

    suspend fun init() {
        if (initialized) return
        initialized = true
    }

    suspend fun generateAndUploadKeys(): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun getIdentityKeyPair(): CryptoHelper.KeyPair? {
        return null
    }

    suspend fun getIdentityPublicKeyBase64(): String? {
        return null
    }

    suspend fun topUpOpks() {
    }

    suspend fun rotateSignedPreKey(): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun hasKeys(): Boolean {
        return false
    }
}
