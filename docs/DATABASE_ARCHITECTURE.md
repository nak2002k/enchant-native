# Database Architecture — Pure SQLCipher with Typed DAOs

> Design document for Enchant's encrypted database layer.
> Inspired by Signal's approach (raw SQLCipher) but with a typed, reactive abstraction on top.

---

## 1. Philosophy: Signal's Approach, Made Better

### Signal's Database Layer
| Characteristic | Signal Android |
|---------------|---------------|
| Engine | SQLCipher (full-database AES-256-GCM encryption) |
| Access pattern | Raw `SQLiteDatabase` queries everywhere |
| Model mapping | Manual Cursor → POJO mapping in every DAO |
| Reactivity | `ContentObserver` + manual notification broadcast |
| Migrations | Manual `onUpgrade()` with raw SQL |
| Connection | Single `SQLiteOpenHelper` (no pooling) |
| Thread safety | `ReentrantLock` per store |

### Enchant's Improvements

| Feature | Signal | Enchant |
|---------|--------|---------|
| **Type safety** | Raw Cursors | Typed DAOs returning `MessageEntity` |
| **Reactivity** | ContentObserver (manual) | `Flow<List<T>>` (automatic) |
| **Connection pooling** | Single connection | 1 writer + 4-8 readers (WAL) |
| **Cursor mapping** | Manual per-query | Auto-mapping via reflection-free codegen |
| **Pagination** | OFFSET/LIMIT (slow) | Cursor-based (O(log n)) |
| **Migration testing** | Manual | `MigrationTestHelper`-style framework |
| **Transaction API** | `beginTransaction()` / `endTransaction()` | `runInTransaction<T> { }` (auto rollback) |
| **Error handling** | Inline try/catch | `Result<T>` wrapping all DAO calls |

---

## 2. Architecture

```
┌────────────────────────────────────────────────────────┐
│                     AppDatabase                         │
│  (singleton — holds all DAOs, connection pool, migrator)│
├──────────────┬──────────────┬──────────────┬────────────┤
│  MessageDao  │  SessionDao  │ IdentityDao  │   ...Dao   │
│  (messages)  │  (sessions)  │ (identities) │ (14 total) │
├──────────────┴──────────────┴──────────────┴────────────┤
│                  DatabasePool                             │
│  (1 writer + 4-8 reader connections, WAL mode)          │
├──────────────────────────────────────────────────────────┤
│              SQLCipher (SupportFactory)                   │
│  (AES-256-GCM encrypted, PBKDF2 key derivation)         │
├──────────────────────────────────────────────────────────┤
│                   Android KeyStore                        │
│  (DB passphrase encrypted at rest)                      │
└──────────────────────────────────────────────────────────┘
```

---

## 3. SQLCipher Configuration

### 3.1 Key Derivation

The database encryption key is derived from user-specific and device-specific entropy:

```kotlin
object DatabaseKeyManager {
    private const val KEY_ALIAS = "enchant_db_key"

    suspend fun getOrCreateKey(context: Context): ByteArray {
        val keyStore = KeyStoreManager.getInstance()
        return if (keyStore.keyExists(KEY_ALIAS)) {
            keyStore.decrypt(KEY_ALIAS, loadEncryptedDbKey())
        } else {
            val newKey = CryptoHelper.generateRandomKey(32)
            val encrypted = keyStore.encrypt(KEY_ALIAS, newKey)
            saveEncryptedDbKey(encrypted)
            newKey
        }
    }
}
```

Key hierarchy:
```
Android KeyStore (hardware-backed)
  └── AES-256 key (enchant_db_key)
       └── Encrypt/decrypt DB passphrase (32 random bytes)
            └── SQLCipher database (enchant.db)
```

### 3.2 SQLCipher PRAGMAs

```sql
PRAGMA key = '<derived_passphrase>';
PRAGMA cipher_page_size = 1024;
PRAGMA kdf_iter = 256000;
PRAGMA cipher_default_kdf_iter = 256000;
PRAGMA cipher_hmac_algorithm = HMAC_SHA512;
PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA512;
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;
PRAGMA cipher_memory_security = ON;
```

### 3.3 Database Connection Pool

```kotlin
class DatabasePool(context: Context, passphrase: ByteArray) {
    private val factory = SupportFactory(passphrase)

    // Single writer
    private val writer: SupportSQLiteDatabase by lazy {
        createOpenHelper(context).writableDatabase
    }

    // Thread-local readers for concurrent access without contention
    private val threadLocalReader = ThreadLocal.withInitial {
        createOpenHelper(context).readableDatabase
    }

    private fun createOpenHelper(context: Context): SupportSQLiteOpenHelper {
        return factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("enchant.db")
                .callback(MigrationCallback(DB_VERSION))
                .build()
        )
    }

    suspend fun <T> read(block: (SupportSQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) { threadLocalReader.get().let(block) }

    suspend fun <T> write(block: (SupportSQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) { synchronized(writer) { block(writer) } }
}
```

---

## 4. Typed DAOs (Our Key Improvement Over Signal)

### 4.1 Pure Data Classes (No Room Annotations)

```kotlin
// core/database/src/main/java/org/enchant/core/database/entity/MessageEntity.kt
data class MessageEntity(
    val localId: Long = 0,
    val conversationId: String,
    val senderId: String,
    val senderDeviceId: String?,
    val envelopeId: String?,
    val messageType: String,
    val content: String,
    val mediaKey: String?,
    val mediaIv: String?,
    val mediaMimeType: String?,
    val mediaSize: Long?,
    val mediaThumbnailPath: String?,
    val replyToEnvelopeId: String?,
    val forwardedFromUserId: String?,
    val status: String,
    val timestamp: Long,
    val serverTs: Long?,
    val isEdited: Boolean = false,
    val editEnvelopeId: String?,
    val isStarred: Boolean = false,
    val isDeleted: Boolean = false,
    val disappearAt: Long?,
    val gifUrl: String?
)
```

### 4.2 Auto-Mapping via Extension Functions

```kotlin
// core/database/src/main/java/org/enchant/core/database/util/CursorExtensions.kt
object CursorMapper {
    inline fun <reified T : Any> mapTo(cursor: Cursor): T {
        val columns = cursor.columnNames
        val constructor = T::class.primaryConstructor!!
        val args = mutableMapOf<KParameter, Any?>()

        constructor.parameters.forEach { param ->
            val colIndex = columns.indexOf(param.name?.toSnakeCase())
            if (colIndex >= 0) {
                args[param] = when (param.type.classifier) {
                    String::class -> cursor.getString(colIndex)
                    Long::class -> cursor.getLong(colIndex)
                    Int::class -> cursor.getInt(colIndex)
                    Boolean::class -> cursor.getInt(colIndex) == 1
                    else -> cursor.getString(colIndex)
                }
            }
        }
        return constructor.callBy(args)
    }

    private fun String.toSnakeCase(): String {
        return replace(Regex("([a-z])([A-Z])")) { "${it.group(1)}_${it.group(2)}" }.lowercase()
    }
}
```

### 4.3 Typed DAO Interface

```kotlin
// core/database/src/main/java/org/enchant/core/database/dao/MessageDao.kt
class MessageDao(private val pool: DatabasePool) {

    suspend fun insert(message: MessageEntity): Long = pool.write { db ->
        val stmt = db.compileStatement("""
            INSERT OR IGNORE INTO messages
                (conversation_id, sender_id, sender_device_id, envelope_id, message_type,
                 content, media_key, media_iv, media_mime_type, media_size,
                 media_thumbnail_path, reply_to_envelope_id, forwarded_from_user_id,
                 status, timestamp, server_ts, is_edited, edit_envelope_id,
                 is_starred, is_deleted, disappear_at, gif_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent())
        // ... bind parameters
        stmt.executeInsert()
    }

    suspend fun getByEnvelopeId(envelopeId: String): MessageEntity? = pool.read { db ->
        db.query("SELECT * FROM messages WHERE envelope_id = ?", arrayOf(envelopeId))
            .use { cursor ->
                if (cursor.moveToFirst()) CursorMapper.mapTo<MessageEntity>(cursor) else null
            }
    }

    fun getConversationMessages(
        conversationId: String, limit: Int = 50, beforeId: Long? = null
    ): Flow<List<MessageEntity>> = callbackFlow {
        // Initial load
        val cursor = db.query("""
            SELECT * FROM messages
            WHERE conversation_id = ? AND is_deleted = 0
            ${if (beforeId != null) "AND local_id < ?" else ""}
            ORDER BY timestamp DESC
            LIMIT ?
        """.trimIndent(), args)
        val messages = cursor.use { it.mapToList<MessageEntity>() }
        trySend(messages)

        // Observe via polling or trigger-based notification
        // (implementation depends on reactivity mechanism)
    }
}
```

### 4.4 Reactive Flows (Two Approaches)

**Approach A: Flow with DB Trigger (Recommended)**

```kotlin
class TableNotifier {
    private val triggers = ConcurrentHashMap<String, MutableSharedFlow<Unit>>()

    fun notify(table: String) {
        triggers.getOrPut(table) { MutableSharedFlow() }.tryEmit(Unit)
    }

    fun observe(table: String): SharedFlow<Unit> {
        return triggers.getOrPut(table) { MutableSharedFlow() }
    }
}
```

DAOs observe changes and re-query:

```kotlin
fun getConversationMessages(conversationId: String): Flow<List<MessageEntity>> {
    return tableNotifier.observe("messages").flatMapLatest {
        flow { emit(queryMessages(conversationId)) }
    }.flowOn(Dispatchers.IO)
}
```

**Approach B: Polling (Simpler, Less Reactive)**

```kotlin
fun getConversationMessages(conversationId: String): Flow<List<MessageEntity>> = flow {
    while (true) {
        emit(queryMessages(conversationId))
        delay(100)  // Poll every 100ms
    }
}.flowOn(Dispatchers.IO)
```

**Recommendation:** Use Approach A with trigger-based notifications. The `TableNotifier` is called by DAOs after write operations. This gives us sub-100ms reactivity without polling overhead.

---

## 5. Cursor-Based Pagination

```kotlin
data class Page<T>(
    val items: List<T>,
    val nextCursor: Long?,   // localId of last item for "load more"
    val hasMore: Boolean
)

suspend fun getMessagesPaged(
    conversationId: String,
    cursor: Long? = null,     // exclusive cursor (local_id of last loaded message)
    limit: Int = 50
): Page<MessageEntity> = db.read { db ->
    val cursorClause = if (cursor != null) "AND local_id < ?" else ""
    val args = mutableListOf(conversationId).apply {
        cursor?.let { add(it.toString()) }
        add(limit.toString())
    }
    db.rawQuery("""
        SELECT * FROM messages
        WHERE conversation_id = ? AND is_deleted = 0 $cursorClause
        ORDER BY local_id DESC
        LIMIT ?
    """, args.toTypedArray()).use { c ->
        val items = c.mapToList<MessageEntity>()
        Page(
            items = items,
            nextCursor = items.lastOrNull()?.localId,
            hasMore = items.size == limit
        )
    }
}
```

**Why cursor-based?** `OFFSET` forces a full scan of skipped rows. Cursor pagination uses the index on `(conversation_id, local_id)` — O(log n) lookup.

---

## 6. Migration Framework

### 6.1 Versioned Migrations

```kotlin
interface Migration {
    val version: Int
    suspend fun migrate(db: SupportSQLiteDatabase)
}
```

### 6.2 Migration Runner

```kotlin
class DatabaseMigrator(private val migrations: List<Migration>) {

    suspend fun migrate(db: SupportSQLiteDatabase, currentVersion: Int, targetVersion: Int) {
        for (version in (currentVersion + 1)..targetVersion) {
            val migration = migrations.find { it.version == version }
                ?: throw IllegalStateException("No migration from v${version - 1} to v$version")
            db.beginTransaction()
            try {
                migration.migrate(db)
                db.execSQL("PRAGMA user_version = $version")
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}
```

### 6.3 Migration Example

```kotlin
object Migration2 : Migration {
    override val version = 2

    override suspend fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS call_logs (
                call_id TEXT PRIMARY KEY,
                remote_user_id TEXT NOT NULL,
                type TEXT NOT NULL,
                direction TEXT NOT NULL,
                duration_seconds INTEGER DEFAULT 0,
                status TEXT NOT NULL,
                ended_at INTEGER NOT NULL
            )
        """)
        db.execSQL("CREATE INDEX idx_call_logs_remote_user ON call_logs(remote_user_id)")
    }
}
```

### 6.4 Migration Testing

```kotlin
class MigrationTest {
    @Test
    fun `migrate from v1 to v2 preserves existing data`() = runTest {
        // Create v1 schema
        val v1Db = createDatabase(version = 1)
        v1Db.execSQL("INSERT INTO messages ...")

        // Close and reopen with migration
        v1Db.close()
        val v2Db = migrateDatabase(1, 2)

        // Verify data preserved
        val cursor = v2Db.rawQuery("SELECT count(*) FROM messages", null)
        // ...
    }
}
```

---

## 7. Database Schema (14 Tables)

### 7.1 `messages` — Core Message Storage

| Column | Type | Constraints | Index |
|--------|------|-------------|-------|
| local_id | INTEGER | PRIMARY KEY AUTOINCREMENT | |
| conversation_id | TEXT | NOT NULL | INDEX |
| sender_id | TEXT | NOT NULL | |
| sender_device_id | TEXT | | |
| envelope_id | TEXT | UNIQUE | INDEX |
| message_type | TEXT | NOT NULL | |
| content | TEXT | NOT NULL | FTS5 |
| media_key | TEXT | | |
| media_iv | TEXT | | |
| media_mime_type | TEXT | | |
| media_size | INTEGER | | |
| media_thumbnail_path | TEXT | | |
| reply_to_envelope_id | TEXT | | |
| forwarded_from_user_id | TEXT | | |
| status | TEXT | NOT NULL | INDEX |
| timestamp | INTEGER | NOT NULL | INDEX |
| server_ts | INTEGER | | |
| is_edited | INTEGER | DEFAULT 0 | |
| edit_envelope_id | TEXT | | |
| is_starred | INTEGER | DEFAULT 0 | |
| is_deleted | INTEGER | DEFAULT 0 | |
| disappear_at | INTEGER | | INDEX |
| gif_url | TEXT | | |

**Indexes:**
```sql
CREATE INDEX idx_messages_conversation_ts ON messages(conversation_id, timestamp DESC);
CREATE UNIQUE INDEX idx_messages_envelope ON messages(envelope_id);
CREATE INDEX idx_messages_status ON messages(status);
CREATE INDEX idx_messages_disappear ON messages(disappear_at)
    WHERE disappear_at IS NOT NULL AND is_deleted = 0;
```

### 7.2 `conversations` — Thread/Conversation Metadata

| Column | Type | Constraints |
|--------|------|-------------|
| conversation_id | TEXT | PRIMARY KEY |
| type | TEXT | NOT NULL ('individual', 'group') |
| last_message | TEXT | |
| last_message_envelope_id | TEXT | |
| last_message_timestamp | INTEGER | |
| unread_count | INTEGER | DEFAULT 0 |
| is_pinned | INTEGER | DEFAULT 0 |
| is_archived | INTEGER | DEFAULT 0 |
| is_muted | INTEGER | DEFAULT 0 |
| mute_until | INTEGER | |
| disappear_timer_seconds | INTEGER | DEFAULT 0 |

**Indexes:**
```sql
CREATE INDEX idx_conversations_pinned ON conversations(is_pinned DESC, last_message_timestamp DESC);
CREATE INDEX idx_conversations_archived ON conversations(is_archived, last_message_timestamp DESC);
```

### 7.3 `signal_sessions` — Signal Protocol Sessions

| Column | Type | Constraints |
|--------|------|-------------|
| user_id | TEXT | NOT NULL |
| device_id | TEXT | NOT NULL |
| serialized_session | BLOB | NOT NULL |
| created_at | INTEGER | |
| last_used_at | INTEGER | |
| archived | INTEGER | DEFAULT 0 |
| PRIMARY KEY | | (user_id, device_id) |

### 7.4 `identities` — Identity Keys

| Column | Type | Constraints |
|--------|------|-------------|
| address_name | TEXT | PRIMARY KEY |
| recipient_id | TEXT | |
| identity_key | BLOB | NOT NULL |
| verified_status | INTEGER | DEFAULT 0 |
| first_use | INTEGER | DEFAULT 1 |
| timestamp | INTEGER | NOT NULL |
| non_blocking_approval | INTEGER | DEFAULT 0 |

### 7.5 `key_material` — Pre-Key Storage

| Column | Type | Constraints |
|--------|------|-------------|
| key_type | TEXT | NOT NULL ('signed_prekey', 'one_time_prekey') |
| key_id | INTEGER | NOT NULL |
| public_key | BLOB | NOT NULL |
| private_key | BLOB | NOT NULL |
| signature | BLOB | (for signed prekeys) |
| created_at | INTEGER | NOT NULL |
| is_active | INTEGER | DEFAULT 1 |
| PRIMARY KEY | | (key_type, key_id) |

### 7.6 Remaining Tables

Seven more tables follow the same pattern: `recipients`, `groups`, `group_members`, `media_cache`, `profile_cache`, `call_logs`, `status_cache`, `sticker_packs`, `installed_stickers`. Full DDL in `CREATE_TABLES.md`.

---

## 8. Full Schema DDL (V1)

```sql
-- messages
CREATE TABLE IF NOT EXISTS messages (
    local_id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    sender_device_id TEXT,
    envelope_id TEXT UNIQUE,
    message_type TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    media_key TEXT, media_iv TEXT, media_mime_type TEXT, media_size INTEGER,
    media_thumbnail_path TEXT, reply_to_envelope_id TEXT, forwarded_from_user_id TEXT,
    status TEXT NOT NULL DEFAULT 'sending',
    timestamp INTEGER NOT NULL, server_ts INTEGER,
    is_edited INTEGER DEFAULT 0, edit_envelope_id TEXT,
    is_starred INTEGER DEFAULT 0, is_deleted INTEGER DEFAULT 0,
    disappear_at INTEGER, gif_url TEXT
);
CREATE INDEX idx_messages_conversation_ts ON messages(conversation_id, timestamp DESC);
CREATE UNIQUE INDEX idx_messages_envelope ON messages(envelope_id);

-- conversations
CREATE TABLE IF NOT EXISTS conversations (
    conversation_id TEXT PRIMARY KEY, type TEXT NOT NULL,
    last_message TEXT, last_message_envelope_id TEXT, last_message_timestamp INTEGER,
    unread_count INTEGER DEFAULT 0, is_pinned INTEGER DEFAULT 0,
    is_archived INTEGER DEFAULT 0, is_muted INTEGER DEFAULT 0,
    mute_until INTEGER, disappear_timer_seconds INTEGER DEFAULT 0
);
CREATE INDEX idx_conversations_pinned ON conversations(is_pinned DESC, last_message_timestamp DESC);

-- signal_sessions
CREATE TABLE IF NOT EXISTS signal_sessions (
    user_id TEXT NOT NULL, device_id TEXT NOT NULL,
    serialized_session BLOB NOT NULL, created_at INTEGER, last_used_at INTEGER,
    archived INTEGER DEFAULT 0, PRIMARY KEY(user_id, device_id)
);

-- identities
CREATE TABLE IF NOT EXISTS identities (
    address_name TEXT PRIMARY KEY, recipient_id TEXT,
    identity_key BLOB NOT NULL, verified_status INTEGER DEFAULT 0,
    first_use INTEGER DEFAULT 1, timestamp INTEGER NOT NULL,
    non_blocking_approval INTEGER DEFAULT 0
);

-- key_material
CREATE TABLE IF NOT EXISTS key_material (
    key_type TEXT NOT NULL, key_id INTEGER NOT NULL,
    public_key BLOB NOT NULL, private_key BLOB NOT NULL,
    signature BLOB, created_at INTEGER NOT NULL, is_active INTEGER DEFAULT 1,
    PRIMARY KEY(key_type, key_id)
);

-- recipients
CREATE TABLE IF NOT EXISTS recipients (
    recipient_id TEXT PRIMARY KEY, username TEXT, display_name TEXT,
    phone_number TEXT, avatar_media_id TEXT, avatar_local_path TEXT,
    is_blocked INTEGER DEFAULT 0
);
CREATE INDEX idx_recipients_username ON recipients(username);

-- groups
CREATE TABLE IF NOT EXISTS groups (
    group_id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT,
    avatar_media_id TEXT, my_role TEXT NOT NULL DEFAULT 'member',
    member_count INTEGER DEFAULT 0
);

-- group_members
CREATE TABLE IF NOT EXISTS group_members (
    group_id TEXT NOT NULL, user_id TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'member', joined_at INTEGER,
    PRIMARY KEY(group_id, user_id)
);
CREATE INDEX idx_group_members_group ON group_members(group_id);

-- media_cache
CREATE TABLE IF NOT EXISTS media_cache (
    media_id TEXT PRIMARY KEY, local_path TEXT, file_size INTEGER, last_accessed_at INTEGER
);

-- profile_cache
CREATE TABLE IF NOT EXISTS profile_cache (
    user_id TEXT PRIMARY KEY, display_name TEXT, username TEXT,
    about TEXT, avatar_media_id TEXT, profile_json TEXT
);

-- call_logs
CREATE TABLE IF NOT EXISTS call_logs (
    call_id TEXT PRIMARY KEY, remote_user_id TEXT NOT NULL,
    type TEXT NOT NULL, direction TEXT NOT NULL,
    duration_seconds INTEGER DEFAULT 0, status TEXT NOT NULL,
    ended_at INTEGER NOT NULL
);
CREATE INDEX idx_call_logs_remote ON call_logs(remote_user_id);

-- status_cache
CREATE TABLE IF NOT EXISTS status_cache (
    status_id TEXT PRIMARY KEY, author_id TEXT NOT NULL,
    status_type TEXT NOT NULL, text_content TEXT, media_id TEXT,
    background_color TEXT, timestamp INTEGER, viewed INTEGER DEFAULT 0
);
CREATE INDEX idx_status_cache_author ON status_cache(author_id);

-- sticker_packs
CREATE TABLE IF NOT EXISTS sticker_packs (
    pack_id TEXT PRIMARY KEY, title TEXT, cover TEXT,
    author TEXT, installed_at INTEGER
);

-- installed_stickers
CREATE TABLE IF NOT EXISTS installed_stickers (
    pack_id TEXT NOT NULL, sticker_id TEXT NOT NULL,
    emoji TEXT, position INTEGER, PRIMARY KEY(pack_id, sticker_id)
);
CREATE INDEX idx_stickers_pack ON installed_stickers(pack_id);
```

---

## 9. AppDatabase.kt API

```kotlin
class AppDatabase private constructor(
    val pool: DatabasePool,
    val migrator: DatabaseMigrator,
    val notifier: TableNotifier
) {
    // DAOs
    val messages: MessageDao = MessageDao(pool, notifier)
    val conversations: ConversationDao = ConversationDao(pool, notifier)
    val sessions: SessionDao = SessionDao(pool)
    val identities: IdentityDao = IdentityDao(pool)
    val keyMaterial: KeyMaterialDao = KeyMaterialDao(pool)
    val recipients: RecipientDao = RecipientDao(pool, notifier)
    val groups: GroupDao = GroupDao(pool)
    val groupMembers: GroupMemberDao = GroupMemberDao(pool)
    val mediaCache: MediaCacheDao = MediaCacheDao(pool)
    val profileCache: ProfileCacheDao = ProfileCache(pool)
    val callLogs: CallLogDao = CallLogDao(pool, notifier)
    val statusCache: StatusCacheDao = StatusCacheDao(pool)
    val stickerPacks: StickerPackDao = StickerPackDao(pool)
    val installedStickers: InstalledStickerDao = InstalledStickerDao(pool)

    companion object {
        private const val DB_VERSION = 1
        private var instance: AppDatabase? = null

        suspend fun init(context: Context): AppDatabase {
            instance?.let { return it }

            val passphrase = DatabaseKeyManager.getOrCreateKey(context)
            val pool = DatabasePool(context, passphrase)
            val notifier = TableNotifier()
            val migrator = DatabaseMigrator(listOf(/* Migration2, Migration3, ... */))

            // Run migrations if needed
            pool.write { db ->
                val currentVersion = db.version
                if (currentVersion < DB_VERSION) {
                    migrator.migrate(db, currentVersion, DB_VERSION)
                    db.version = DB_VERSION
                }
            }

            return AppDatabase(pool, migrator, notifier).also { instance = it }
        }

        fun getInstance(): AppDatabase =
            instance ?: throw IllegalStateException("AppDatabase not initialized")

        fun close() {
            instance?.pool?.close()
            instance = null
        }
    }
}
```

---

## 10. Performance Targets

| Operation | Target | Notes |
|-----------|--------|-------|
| Message insert | < 5ms | Prepared statement, WAL mode |
| Message fetch (50 items) | < 10ms | Index-only scan |
| Message fetch (paged, cursor) | < 5ms | O(log n) via index |
| Batch insert (200 items) | < 50ms | Single transaction |
| Session load | < 2ms | PK lookup |
| Migration (v1 → v2) | < 100ms | Schema-only |
| Full DB open + migrate | < 500ms | Includes key derivation |
| Concurrent reads | < 15ms | 4 simultaneous, WAL mode |

---

## 11. Testing Strategy

| Test Type | Tool | What |
|-----------|------|------|
| Unit (DAO) | In-memory SQLCipher | Each DAO operation with clean DB per test |
| Migration | Versioned SQL | Create v1 schema, run migration, verify v2 schema |
| Concurrency | Coroutines test | 10 concurrent readers + 1 writer |
| Reactive Flow | Turbine | Verify Flow emits on insert/update/delete |
| Performance | Benchmark | Measure insert/fetch/migration times |
| Encryption | Verify on disk | DB file is binary gibberish without key |

**Example DAO test:**
```kotlin
class MessageDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MessageDao

    @BeforeEach
    fun setUp() = runTest {
        db = createInMemoryDatabase()
        dao = db.messages
    }

    @Test
    fun `insert and retrieve message by envelope id`() = runTest {
        val message = MessageEntity(
            conversationId = "conv1", senderId = "user1", messageType = "text",
            content = "Hello", status = "sending", timestamp = System.currentTimeMillis(),
            envelopeId = "env1"
        )
        val id = dao.insert(message)
        val retrieved = dao.getByEnvelopeId("env1")
        assertNotNull(retrieved)
        assertEquals("Hello", retrieved!!.content)
    }
}
```

---

## Appendix: Comparison with Room

| Feature | Room | Pure SQLCipher (Enchant) |
|---------|------|-------------------------|
| Compile-time query verification | ✅ SQL syntax checked | ❌ (manual review) |
| Auto-generated DAOs | ✅ | ❌ (manual) |
| Full control over SQL | ❌ (abstraction leaks) | ✅ |
| WAL + connection pooling | ❌ (single connection) | ✅ (1 writer + N readers) |
| Encryption | ❌ (need SQLCipher plugin) | ✅ (native) |
| Custom pragmas | ❌ (hard to set) | ✅ (full control) |
| Binary size overhead | ~500KB | ~0 (uses SQLCipher directly) |

**Enchant's advantage:** Full control over SQL + connection pooling + native SQLCipher encryption. The trade-off is writing DAOs manually, which we offset with the `CursorMapper` auto-mapping and clean interface design.
