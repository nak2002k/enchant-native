package org.enchant

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import org.enchant.core.calls.CallManager
import org.enchant.core.crypto.KeyManager
import org.enchant.core.crypto.PreKeyWorker
import org.enchant.core.crypto.SessionManager
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.IdentityDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.dao.SessionDao
import org.enchant.core.jobmanager.DisappearingMessagesWorker
import org.enchant.core.network.ApiClient
import org.enchant.core.network.ConnectivityMonitor
import org.enchant.core.network.OfflineQueue
import org.enchant.core.network.WebSocketManager

object DI {
    private val mutex = Mutex()
    private var _initialized = false
    private var _workerScope: CoroutineScope? = null

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

    val securePreferences: SecurePreferences get() = checkNotNull(_securePreferences) { "DI not initialized" }
    val keyStoreManager: KeyStoreManager get() = checkNotNull(_keyStoreManager) { "DI not initialized" }
    val appConfig: AppConfig get() = checkNotNull(_appConfig) { "DI not initialized" }
    val databasePool: DatabasePool get() = checkNotNull(_databasePool) { "DI not initialized" }
    val apiClient: ApiClient get() = checkNotNull(_apiClient) { "DI not initialized" }
    val webSocketManager: WebSocketManager get() = checkNotNull(_webSocketManager) { "DI not initialized" }
    val connectivityMonitor: ConnectivityMonitor get() = checkNotNull(_connectivityMonitor) { "DI not initialized" }
    val offlineQueue: OfflineQueue get() = checkNotNull(_offlineQueue) { "DI not initialized" }
    val messageDao: MessageDao get() = checkNotNull(_messageDao) { "DI not initialized" }
    val conversationDao: ConversationDao get() = checkNotNull(_conversationDao) { "DI not initialized" }
    val sessionDao: SessionDao get() = checkNotNull(_sessionDao) { "DI not initialized" }
    val identityDao: IdentityDao get() = checkNotNull(_identityDao) { "DI not initialized" }
    val recipientDao: RecipientDao get() = checkNotNull(_recipientDao) { "DI not initialized" }
    val conversationRepository: ConversationRepository get() = checkNotNull(_conversationRepository) { "DI not initialized" }
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

                val client = ApiClient()
                client.init()
                ApiClient.setInstance(client)
                _apiClient = client

                AuthManager.setApiClient(client)
                AuthManager.init()

                val dbPassphrase = try {
                    KeyStoreManager.getOrCreateDatabaseKey()
                } catch (e: Exception) {
                    android.util.Log.w("DI", "DB key init failed: ${e.message}")
                    ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                }
                val pool = try {
                    DatabasePool(context, dbPassphrase, emptyList()).also {
                        _databasePool = it
                        DatabasePool.instance = it
                    }
                } catch (e: Exception) {
                    android.util.Log.w("DI", "DatabasePool init failed: ${e.message}")
                    null
                }
                if (pool != null) {
                    _messageDao = MessageDao(pool)
                    _conversationDao = ConversationDao(pool)
                    _sessionDao = SessionDao(pool)
                    _identityDao = IdentityDao(pool)
                    _recipientDao = RecipientDao(pool)

                    _conversationRepository = ConversationRepository(
                        messageDao = _messageDao!!,
                        conversationDao = _conversationDao!!,
                        recipientDao = _recipientDao!!,
                        pool = pool
                    )
                }

                ConnectivityMonitor.init(context)
                _connectivityMonitor = ConnectivityMonitor
                _offlineQueue = OfflineQueue

                KeyManager.init(client)
                SessionManager.init()
                PreKeyWorker.schedule(context)

                WebSocketManager.init()
                _webSocketManager = WebSocketManager

                CallManager.init()
                CallManager.setApiClient(client)

                if (_conversationRepository != null) {
                    MessageSendPipeline.init(_apiClient!!, _conversationRepository!!)
                    IncomingMessageProcessor.init(_conversationRepository!!, _recipientDao!!, client, _conversationDao!!, _messageDao!!)
                    MediaService.init(_apiClient!!)
                    ContentPreProcessor.init(_apiClient!!)

                    _workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    _workerScope?.launch {
                        while (true) {
                            delay(60_000L)
                            DisappearingMessagesWorker.tick()
                        }
                    }
                }

                _initialized = true
            } catch (e: Exception) {
                reset()
                throw IllegalStateException("DI init failed: ${e.message}", e)
            }
        }
    }

    fun reset() {
        _workerScope?.cancel()
        _workerScope = null
        MessageSendPipeline.shutdown()
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
}
