package org.enchant

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.enchant.core.calls.CallsModule
import org.enchant.core.calls.WebSocketSignalingClient
import org.enchant.core.crypto.KeyManager
import org.enchant.core.crypto.PreKeyStore
import org.enchant.core.crypto.PreKeyWorker
import org.enchant.core.crypto.NativeSessionManager
import org.enchant.core.database.DatabasePool
import org.enchant.core.database.dao.ConversationDao
import org.enchant.core.database.dao.IdentityDao
import org.enchant.core.database.dao.MessageDao
import org.enchant.core.database.dao.PreKeyDaoImpl
import org.enchant.core.database.dao.RecipientDao
import org.enchant.core.database.dao.SessionDao
import org.enchant.core.jobmanager.DisappearingMessagesWorker
import org.enchant.core.network.ApiClient
import org.enchant.core.network.ConnectivityMonitor
import org.enchant.core.network.OfflineQueue
import org.enchant.core.network.WebSocketManager
import org.enchant.core.performance.MessageTrimmer
import org.enchant.core.store.EnchantStore
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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
    private var _preKeyStore: PreKeyStore? = null
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
    val preKeyStore: PreKeyStore get() = checkNotNull(_preKeyStore) { "DI not initialized" }
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

                EnchantStore.init(context)

                val client = ApiClient()
                if (BuildConfig.DEBUG && AppConfig.gatewayUrl.startsWith("http://")) {
                    client.init(
                        OkHttpClient.Builder()
                            .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS))
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(60, TimeUnit.SECONDS)
                            .build()
                    )
                } else {
                    client.init()
                }
                ApiClient.setInstance(client)
                _apiClient = client

                AuthManager.setApiClient(client)
                AuthManager.init()

                val dbPassphrase = try {
                    KeyStoreManager.getOrCreateDatabaseKey()
                } catch (e: Exception) {
                    android.util.Log.w("DI", "DB key init failed: ${e.message}")
                    org.enchant.core.crypto.CryptoPrimitives.generateRandomKey(32)
                }
                var pool: org.enchant.core.database.DatabasePool? = null
                try {
                    pool = org.enchant.core.database.DatabasePool(context, dbPassphrase, emptyList()).also {
                        _databasePool = it
                        org.enchant.core.database.DatabasePool.instance = it
                        if (SecurePreferences.getBoolean("fts_needs_reset", false)) {
                            resetFtsIndex(it)
                            SecurePreferences.remove("fts_needs_reset")
                        }
                    }
                } catch (e: Exception) {
                    // F-C3: NEVER auto-delete the encrypted DB on a Keystore /
                    // passphrase failure. Doing so destroyed every message and
                    // session irrecoverably on transient Keystore issues and
                    // device restores. Instead, surface the failure so the user
                    // can recover (re-import a backup, etc.). We deliberately do
                    // not catch-and-continue: the app must not silently start
                    // with an empty database.
                    android.util.Log.e("DI", "DatabasePool init failed; NOT deleting DB", e)
                    throw e
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

                    val preKeyDao = PreKeyDaoImpl(pool)
                    val preKeyStore = PreKeyStore()
                    preKeyStore.setDao(preKeyDao)
                    _preKeyStore = preKeyStore
                    KeyManager.setPreKeyStore(preKeyStore)
                }

                ConnectivityMonitor.init(context)
                _connectivityMonitor = ConnectivityMonitor
                _offlineQueue = OfflineQueue

                KeyManager.init(object : KeyManager.ApiClientLike {
                    override suspend fun get(path: String): Result<kotlinx.serialization.json.JsonObject> =
                        client.get(path)
                    override suspend fun post(path: String, body: kotlinx.serialization.json.JsonObject): Result<kotlinx.serialization.json.JsonObject> =
                        client.post(path, body)
                    override suspend fun put(path: String, body: kotlinx.serialization.json.JsonObject): Result<kotlinx.serialization.json.JsonObject> =
                        client.put(path, body)
                })
                _preKeyStore?.let { KeyManager.setPreKeyStore(it) }
                NativeSessionManager.init(
                    selfUserId = SecurePreferences.getString("auth.user_id") ?: "self",
                    // Persist native ratchet sessions to the encrypted app
                    // database dir so they survive app restarts.
                    sessionDbPath = java.io.File(
                        context.getDatabasePath("enchant.db").parentFile ?: context.filesDir,
                        "native_sessions.db"
                    ).absolutePath
                )
                KeyManager.syncNativeIdentity()
                PreKeyWorker.schedule(context)
                org.enchant.core.crypto.KeyTransparencyMonitorWorker.schedule(context)
                MessageTrimmer.scheduleTrimming(context, EnchantStore.settings.messageTrimLength.takeIf { it > 0 }?.toLong() ?: 365)

                WebSocketManager.init(context)
                _webSocketManager = WebSocketManager

                if (_conversationRepository != null) {
                    MessageSendPipeline.init(_apiClient!!, _conversationRepository!!)
                    IncomingMessageProcessor.init(_conversationRepository!!, _recipientDao!!, client, _conversationDao!!, _messageDao!!)
                    WebSocketManager.incomingHandler = { envelope ->
                        val result = runCatching { IncomingMessageProcessor.processIncoming(envelope) }
                        val outcome = result.getOrElse {
                            android.util.Log.e("IncomingMsg", "processIncoming THREW: ${it.message}", it)
                            null
                        }?.let { it !is org.enchant.chat.data.ProcessResult.Error } ?: false
                        if (!outcome && result.getOrNull() is org.enchant.chat.data.ProcessResult.Error) {
                            android.util.Log.e("IncomingMsg", "processIncoming ERROR: ${(result.getOrNull() as org.enchant.chat.data.ProcessResult.Error).reason}")
                        }
                        outcome
                    }
                    MediaService.init(_apiClient!!)
                    ContentPreProcessor.init(_apiClient!!)

                    CallsModule.initialize(context)
                    val signalingClient = WebSocketSignalingClient(client)
                    // Never rebuild the call manager: the WebRTC engine is a
                    // one-shot singleton on this device and a second EGL init
                    // can fail, wedging calls after an auth re-init.
                    if (runCatching { org.enchant.core.calls.CallsModule.getCallManager() }.isFailure) {
                        android.util.Log.w("DI", "DI: building CallManager...")
                        CallsModule.setCallManager(
                            CallsModule.provideCallManager(signalingClient, pool!!)
                        )
                        android.util.Log.w("DI", "DI: CallManager set OK")
                    } else {
                        android.util.Log.w("DI", "DI: CallManager already present, skipping rebuild")
                    }

                    _workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    _workerScope?.launch {
                        while (true) {
                            delay(60_000L)
                            try {
                                _conversationRepository?.deleteExpiredMessages()
                            } catch (e: Exception) {
                                android.util.Log.w("DI", "Disappearing messages cleanup failed: ${e.message}")
                            }
                        }
                    }
                    // Periodic pending-fetch: recovers frames that a silently
                    // dead WS socket never delivered (REST path is independent).
                    _workerScope?.launch {
                        while (true) {
                            delay(30_000L)
                            try {
                                org.enchant.core.network.PendingMessageFetcher.fetchAndProcess()
                            } catch (e: Exception) {
                                android.util.Log.w("DI", "Periodic pending fetch failed: ${e.message}")
                            }
                        }
                    }
                    // Register the FCM push token with the backend so closed apps
                    // get woken by a push and pull their pending messages.
                    org.enchant.core.push.FcmFetchManager.init {
                        org.enchant.core.network.PendingMessageFetcher.fetchAndProcess()
                    }
                    _workerScope?.launch {
                        try {
                            val token = org.enchant.core.push.PushTokenRegistrar.getFcmToken()
                            if (!token.isNullOrBlank()) {
                                org.enchant.core.push.PushTokenRegistrar.registerWithBackend(token)
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("DI", "Push token registration failed: ${e.message}")
                        }
                    }
                }

                _initialized = true
            } catch (e: Throwable) {
                android.util.Log.e("DI", "DI init FAILED at: ${e.message}", e)
                reset()
                throw IllegalStateException("DI init failed: ${e.message}", e as? Exception ?: Exception(e))
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
        _preKeyStore = null
        _initialized = false
    }

    private fun resetFtsIndex(pool: DatabasePool) {
        try {
            pool.write { db ->
                db.execSQL("DROP TABLE IF EXISTS messages_fts")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(content, conversation_id UNINDEXED, tokenize='unicode61')")
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS messages_ai AFTER INSERT ON messages BEGIN
                        INSERT INTO messages_fts(rowid, content, conversation_id)
                        VALUES (new.local_id, new.content, new.conversation_id);
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS messages_ad AFTER DELETE ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, content, conversation_id)
                        VALUES ('delete', old.local_id, old.content, old.conversation_id);
                    END
                """)
                db.execSQL("""
                    CREATE TRIGGER IF NOT EXISTS messages_au AFTER UPDATE OF content ON messages BEGIN
                        INSERT INTO messages_fts(messages_fts, rowid, content, conversation_id)
                        VALUES ('delete', old.local_id, old.content, old.conversation_id);
                        INSERT INTO messages_fts(rowid, content, conversation_id)
                        VALUES (new.local_id, new.content, new.conversation_id);
                    END
                """)
                db.execSQL("INSERT INTO messages_fts(rowid, content, conversation_id) SELECT local_id, content, conversation_id FROM messages")
            }
            android.util.Log.w("DI", "FTS index reset completed")
        } catch (e: Exception) {
            android.util.Log.e("DI", "FTS index reset failed", e)
        }
    }
}
