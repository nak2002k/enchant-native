package org.enchant.core.base

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DI {
    private val mutex = Mutex()
    private var _initialized = false
    private var _securePreferences: SecurePreferences? = null
    private var _keyStoreManager: KeyStoreManager? = null
    private var _appConfig: AppConfig? = null
    private var _database: Any? = null
    private var _apiClient: Any? = null
    private var _webSocketManager: Any? = null
    private var _sessionManager: Any? = null

    val securePreferences: SecurePreferences
        get() = checkInitialized().let { _securePreferences!! }
    val keyStoreManager: KeyStoreManager
        get() = checkInitialized().let { _keyStoreManager!! }
    val appConfig: AppConfig
        get() = checkInitialized().let { _appConfig!! }
    val database: Any
        get() = checkInitialized().let { _database!! }
    val apiClient: Any
        get() = checkInitialized().let { _apiClient!! }
    val webSocketManager: Any
        get() = checkInitialized().let { _webSocketManager!! }
    val sessionManager: Any
        get() = checkInitialized().let { _sessionManager!! }
    val isInitialized: Boolean get() = _initialized

    @Synchronized
    suspend fun init(context: Context) {
        if (_initialized) return
        mutex.withLock {
            if (_initialized) return@withLock
            try {
                AppConfig.init(context)
                _appConfig = AppConfig
                KeyStoreManager.init(context)
                _keyStoreManager = KeyStoreManager
                SecurePreferences.init(context)
                _securePreferences = SecurePreferences
                _initialized = true
            } catch (e: Exception) {
                reset()
                throw IllegalStateException("DI init failed at step: ${e.message}", e)
            }
        }
    }

    @Synchronized
    fun reset() {
        _database = null
        _apiClient = null
        _webSocketManager = null
        _sessionManager = null
        _appConfig = null
        _keyStoreManager = null
        _securePreferences = null
        _initialized = false
    }

    private fun checkInitialized(): Unit {
        if (!_initialized) throw IllegalStateException("DI not initialized. Call DI.init() first.")
    }
}
