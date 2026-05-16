package org.enchant

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.enchant.chat.data.ContentPreProcessor
import org.enchant.chat.data.ConversationRepository
import org.enchant.chat.data.IncomingMessageProcessor
import org.enchant.chat.data.MediaService
import org.enchant.chat.data.MessageSendPipeline
import org.enchant.core.auth.AuthManager
import org.enchant.core.base.AppConfig
import org.enchant.core.base.KeyStoreManager
import org.enchant.core.base.SecurePreferences
import org.enchant.core.crypto.KeyManager
import org.enchant.core.crypto.SessionManager
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.IdentityDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.dao.SessionDao
import org.enchant.core.network.ApiClient
import org.enchant.core.network.ConnectivityMonitor
import org.enchant.core.network.OfflineQueue
import org.enchant.core.network.WebSocketManager

object DI {
    private val mutex = Mutex()
    private var _initialized = false

    private var _securePreferences: SecurePreferences? = null
    private var _keyStoreManager: KeyStoreManager? = null
    private var _appConfig: AppConfig? = null
    private var _databasePool: DatabasePool? = null
    private var _apiClient: ApiClient? = null
    private var _webSocketManager: WebSocketManager? = null
    private var _connectivityMonitor: ConnectivityMonitor? = null
    private var _offlineQueue: OfflineQueue? = null
    private var _messageDao: MessageDao? = null
    private var _conversationDao: ConversationDao? = null
    private var _sessionDao: SessionDao? = null
    private var _identityDao: IdentityDao? = null
    private var _recipientDao: RecipientDao? = null
    private var _conversationRepository: ConversationRepository? = null

    val securePreferences: SecurePreferences get() = checkInit().let { _securePreferences!! }
    val keyStoreManager: KeyStoreManager get() = checkInit().let { _keyStoreManager!! }
    val appConfig: AppConfig get() = checkInit().let { _appConfig!! }
    val databasePool: DatabasePool get() = checkInit().let { _databasePool!! }
    val apiClient: ApiClient get() = checkInit().let { _apiClient!! }
    val webSocketManager: WebSocketManager get() = checkInit().let { _webSocketManager!! }
    val connectivityMonitor: ConnectivityMonitor get() = checkInit().let { _connectivityMonitor!! }
    val offlineQueue: OfflineQueue get() = checkInit().let { _offlineQueue!! }
    val messageDao: MessageDao get() = checkInit().let { _messageDao!! }
    val conversationDao: ConversationDao get() = checkInit().let { _conversationDao!! }
    val sessionDao: SessionDao get() = checkInit().let { _sessionDao!! }
    val identityDao: IdentityDao get() = checkInit().let { _identityDao!! }
    val recipientDao: RecipientDao get() = checkInit().let { _recipientDao!! }
    val conversationRepository: ConversationRepository get() = checkInit().let { _conversationRepository!! }
    val isInitialized: Boolean get() = _initialized

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

                val dbPassphrase = KeyStoreManager.getOrCreateDatabaseKey()
                _databasePool = DatabasePool(context, dbPassphrase, emptyList())

                val pool = _databasePool!!
                _messageDao = MessageDao(pool)
                _conversationDao = ConversationDao(pool)
                _sessionDao = SessionDao(pool)
                _identityDao = IdentityDao(pool)
                _recipientDao = RecipientDao(pool)

                val client = ApiClient()
                client.init()
                _apiClient = client

                _connectivityMonitor = ConnectivityMonitor(context)
                _offlineQueue = OfflineQueue()

                _conversationRepository = ConversationRepository(
                    messageDao = _messageDao!!,
                    conversationDao = _conversationDao!!,
                    recipientDao = _recipientDao!!,
                    pool = pool
                )

                KeyManager.init()
                SessionManager.init()

                AuthManager.init()

                WebSocketManager.init()
                _webSocketManager = WebSocketManager

                MessageSendPipeline.init(_apiClient!!, _conversationRepository!!)
                IncomingMessageProcessor.init(_conversationRepository!!)
                MediaService.init(_apiClient!!)
                ContentPreProcessor.init(_apiClient!!)

                _initialized = true
            } catch (e: Exception) {
                reset()
                throw IllegalStateException("DI init failed: ${e.message}", e)
            }
        }
    }

    fun getWebSocketManager(): WebSocketManager {
        checkInit()
        return _webSocketManager!!
    }

    fun reset() {
        _webSocketManager = null
        _apiClient = null
        _conversationRepository = null
        _messageDao = null
        _conversationDao = null
        _sessionDao = null
        _identityDao = null
        _recipientDao = null
        _databasePool = null
        _appConfig = null
        _keyStoreManager = null
        _securePreferences = null
        _connectivityMonitor = null
        _offlineQueue = null
        _initialized = false
    }

    private fun checkInit(): Unit {
        if (!_initialized) throw IllegalStateException("DI not initialized. Call DI.init() first.")
    }
}
