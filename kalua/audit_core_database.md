# core:database Audit

## Security Issues

### 1. SQLCipher Configuration is Non-Standard (Medium)
**File:** `AppDatabase.kt` lines 15-23
```kotlin
db.execSQL("PRAGMA kdf_iter = 256000")
db.execSQL("PRAGMA cipher_hmac_algorithm = HMAC_SHA512")
db.execSQL("PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA512")
```
**Issue:** Uses custom KDF iterations (256000) that differ from the reference app's standard `cipher_default_kdf_iter = 1`. This creates interop issues and increases startup cost.

**Reference:** The reference implementation sets:
```java
connection.execute("PRAGMA cipher_default_kdf_iter = 1;", null, null);
connection.execute("PRAGMA cipher_compatibility = 3;", null, null);
connection.execute("PRAGMA kdf_iter = '1';", null, null);
```

### 2. Database Passphrase Memory Handling (Low)
**File:** `AppDatabase.kt` line 76
```kotlin
val writer: SQLiteDatabase by lazy { openHelper.getWritableDatabase(passphrase) }
```
**Issue:** The `passphrase` ByteArray is held in memory as a class property for the lifetime of `DatabasePool`. Should use `SQLiteDatabase.loadLatest()` pattern or clear after use.

### 3. Log Output Contains Potentially Sensitive Data (Medium)
**File:** `AppDatabase.kt` line 35
```kotlin
android.util.Log.w("AppDatabase", "DB upgrade from v$oldVersion to v$newVersion - applying migrations")
```
**Issue:** Log message reveals database upgrade events which could aid attackers in fingerprinting the application version.

### 4. No Certificate Pinning for External Storage Sync
**Observation:** The reference app has `StorageKeyGenerator` and remote storage APIs for secure storage synchronization with backup services. This module has no equivalent mechanism.

---

## Bugs

### 1. Transaction Pattern Missing in Most DAOs (High)
**Files:** `SessionDao.kt`, `IdentityDao.kt`, `KeyMaterialDao.kt`, `GroupDao.kt`, `GroupMemberDao.kt`, `ProfileCacheDao.kt`, `MediaCacheDao.kt`, `CallLogDao.kt`, `StatusCacheDao.kt`, `StickerPackDao.kt`, `InstalledStickerDao.kt`, `CrashLogDao.kt`

**Issue:** Most DAOs use raw `db.execSQL()` without explicit transactions. The reference app wraps all write operations in `withinTransaction { }` for every write operation.

**Reference implementation pattern:**
```kotlin
fun runInTransaction(block: (SQLiteDatabase) -> T): T {
    return instance!!.writableDatabase.withinTransaction {
        block(it)
    }
}
```

**Impact:** If a batch operation fails mid-way, partial state may be committed. Specifically:
- `SessionDao.store()` (line 6-10): Could write corrupted session data
- `KeyMaterialDao.store()` (line 6-10): Could leave inconsistent key state

### 2. insertBatch Does Not Use Parameterized Statements (High)
**File:** `MessageDao.kt` lines 50-91
```kotlin
db.execSQL("""... VALUES (?, ?, ?, ?, ?, ...)""", arrayOf(
    msg.mediaSize?.toString(),  // WRONG: String conversion
    msg.timestamp.toString(),   // WRONG: String conversion
    ...
))
```
**Issue:** `db.execSQL()` with arrayOf Objects does NOT properly bind parameters for SQLCipher. Must use `compileStatement()` with `bindString()` / `bindLong()` like the single `insert()` method does.

**Consequence:** Binary data (mediaKey, identityKey, etc.) will be corrupted or cause SQL errors. Long values may be truncated/incorrectly stored.

### 3. CursorMapper Column Name Mapping is Fragile (Medium)
**File:** `CursorMapper.kt` lines 32-36
```kotlin
val columnName = param.name
    ?.let { name ->
        name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }
```
**Issue:** Uses reflection-based snake_case conversion which is error-prone. Does not match actual DB column names exactly.

**Reference implementation uses:** Explicit column name constants and direct cursor access with proper getX() calls.

### 4. DatabaseNotifier Uses SharedFlow with Replay=1 (Low)
**File:** `DatabaseNotifier.kt` line 8
```kotlin
private val _tableChanges = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)
```
**Issue:** Replay=1 means new subscribers get the last emitted value. If notification happens before subscriber collects, it may miss updates. Should be `replay = 0` for real-time accuracy.

### 5. Search Messages FTS Query Building is Fragile (Medium)
**File:** `MessageDao.kt` lines 151-165
```kotlin
val ftsQuery = words.joinToString(" ") { "\"$it\"" }
```
**Issue:** Does not properly escape FTS5 special characters. The reference implementation uses `SqlUtil.buildFtsQuery()` helper with proper escaping.

---

## Completeness Gaps

### 1. Missing Foreign Key Constraints Enforcement (Medium)
**File:** `AppDatabase.kt` line 31
```kotlin
db.execSQL("PRAGMA foreign_keys = ON")
```
**Issue:** FK constraints declared but not verified. The reference implementation has explicit FK validation and `RemappedRecordTables` for handling deleted referents. Tables like `group_members` should have FK to `groups_table` and `recipients`.

**Missing Indexes:**
- `idx_messages_conversation_id` on `messages(conversation_id)` - only composite `idx_messages_conversation_ts` exists
- `idx_conversations_last_message_ts` - used in search

### 2. Missing Tables from Reference Implementation (High)
**Reference implementation has that enchant-native lacks:**
- `reaction_table` - message reactions (exists in DB schema but no DAO)
- `mentions` - @mentions in messages (exists in DB schema but no DAO)
- `search_table` - full-text search index
- `drafts` - message drafts
- `attachments` - media attachments (separate from message)
- `group_receipts` - group message delivery receipts
- `pending_retry_receipts` - delivery receipt retry queue
- `sender_keys` - sender key distribution
- `one_time_pre_keys` / `signed_pre_keys` - pre-key storage
- `kyber_pre_keys` - post-quantum keys
- `call_links` - call link join info
- `chat_folders` - folder organization
- `distribution_lists` - story distribution
- `notification_profiles` - notification settings
- `payments` - payment metadata
- `remapped_records` - ID remapping for backup restore

### 3. Missing Entity Definitions (Medium)
**File:** `Entities.kt` - has basic entities but lacks:
- `ReactionEntity` - reactions on messages
- `MentionEntity` - @mentions
- `DraftEntity` - unsent message drafts
- `AttachmentEntity` - media attachments
- `SenderKeyEntity` - sender key records
- `PreKeyEntity` - pre-key records

### 4. Missing Proper Index Definitions (Medium)
**AppDatabase.kt missing indexes that reference implementation has:**
```sql
CREATE INDEX IF NOT EXISTS idx_messages_thread_date ON messages(thread_id, date_received);
CREATE INDEX IF NOT EXISTS idx_messages_attachments ON messages(attachment_id);
CREATE INDEX IF NOT EXISTS idx_recipients_normalized ON recipients(e164, uuid);
CREATE INDEX IF NOT EXISTS idx_groups_group_id ON groups(group_id);
```

### 5. No Migration Version Tracking (High)
**File:** `AppDatabase.kt` lines 34-68

**Issue:** `onUpgrade` manually handles versions 2-4 with hardcoded `when` statements. If a migration fails, there's no rollback mechanism. Reference implementation uses `DatabaseMigrations` class with proper version tracking and rollback support.

---

## Code Quality Issues

### 1. Inconsistent Parameter Binding Patterns (High)
**File:** Multiple DAOs

| Method | Pattern Used |
|--------|-------------|
| `MessageDao.insert()` | `compileStatement()` + `bindString()` (CORRECT) |
| `MessageDao.insertBatch()` | `execSQL()` + `arrayOf()` (WRONG) |
| `SessionDao.store()` | `execSQL()` + `arrayOf()` (WRONG) |
| `KeyMaterialDao.store()` | `execSQL()` + `arrayOf()` (WRONG) |

### 2. Inconsistent Return Type Annotations (Low)
- `LogDatabase.saveCrash()` returns `Unit` but `CrashLogDao.insert()` returns `Long`
- `CallLogDao.insert()` returns `Unit` but other DAOs use various patterns

### 3. No Close Method on DAOs (Medium)
**File:** `AppDatabase.kt` - `DatabasePool` has `close()` method but DAOs hold reference to pool. No mechanism to close cursors if DAO operations fail.

### 4. Duplicate Entity Definitions (Medium)
**Issue:** `GroupEntity` defined in both:
- `/entity/Entities.kt` (line 82-90)
- `/dao/GroupDao.kt` (line 5-9)

Same for `GroupMemberEntity`, `CallLogEntity`, `StickerPackEntity`, `InstalledStickerEntity`, `StatusCacheEntity`, `ProfileCacheEntity`, `MediaCacheEntity`.

### 5. Hardcoded String Literals for Table/Column Names (Medium)
**Issue:** No constants for table names like the reference implementation uses:
```kotlin
// Reference implementation pattern
object MessageTable {
    const val TABLE_NAME = "messages"
    const val ID = "_id"
    const val BODY = "body"
    ...
}
```

### 6. Missing Flow Cleanup in Some DAOs (Medium)
**File:** `ConversationDao.kt` lines 35-50
```kotlin
fun getAll(): Flow<List<ConversationEntity>> = callbackFlow {
    fun queryDb(): List<ConversationEntity> = pool.readWith { db ->
```
**Issue:** Flow collection may not properly cancel the `DatabaseNotifier.tableChanges` subscription if `queryDb()` throws. Should use `awaitClose { job.cancel() }` pattern correctly.

---

## Reference Implementation Comparison

### Encryption at Rest
| Aspect | enchant-native | Reference App |
|--------|---------------|---------------|
| SQLCipher | Yes | Yes |
| Custom KDF | 256000 iter | 1 iter (standard) |
| Key derivation | Custom PBKDF2 | Standard |
| Memory security | `cipher_memory_security = ON` | Not explicitly set |

### Database Schema Comparison
**Reference implementation has 40+ tables vs enchant-native's ~15 tables.**

Missing in enchant-native:
- Complete message attachments handling (separate table)
- Pre-key management (2 tables)
- Sender key management
- Reaction storage
- Draft storage
- Chat folder organization
- Payment records
- CDS (contact discovery) table
- Emoji search table
- Remapped records handling

### Transaction Handling
**Reference app:** All writes wrapped in `withinTransaction { }`
**enchant-native:** Only batch operations use transactions, single writes do not

### Migration Strategy
**Reference app:** `DatabaseMigrations` class with version tracking and post-transaction hooks
**enchant-native:** Inline `onUpgrade` with hardcoded version checks

---

## Recommendations (prioritized)

### P0 - Critical
1. **Fix `insertBatch` parameter binding** - Use `compileStatement()` with proper `bindString()`/`bindLong()` calls like `insert()` does. Binary data is being corrupted.

2. **Add transactions to all write operations** - Wrap `SessionDao.store()`, `KeyMaterialDao.store()`, and all other DAOs in explicit transactions. Use `db.beginTransaction()/setTransactionSuccessful()/endTransaction()` pattern.

3. **Add missing tables/DAOs** - Implement `ReactionDao`, `MentionDao`, `DraftDao`, `AttachmentDao`, `PreKeyDao`, `SenderKeyDao` or document why they're not needed.

### P1 - High
4. **Standardize SQLCipher settings** - Change `kdf_iter` from 256000 to 1, remove custom `cipher_hmac_algorithm`/`cipher_kdf_algorithm` to match reference implementation for interop.

5. **Add FK constraints** - Add foreign keys between `group_members` and `groups_table`/`recipients`. Verify constraints are checked.

6. **Create migration class** - Extract migrations to `DatabaseMigrations` class with proper version tracking.

7. **Remove duplicate entity definitions** - Consolidate all entities in `entity/Entities.kt` and remove duplicates from DAOs.

### P2 - Medium
8. **Add missing indexes** - Add `idx_messages_conversation_id`, `idx_conversations_last_message_ts`.

9. **Replace `execSQL` with `compileStatement`** for all write operations to ensure proper parameter binding.

10. **Add constants for table/column names** - Create object with `const val TABLE_NAME = "..."` for each table.

11. **Fix CursorMapper** - Use explicit column name mapping instead of snake_case conversion reflection.

### P3 - Low
12. **Remove debug logging** - Replace `android.util.Log.w` with proper logging framework.

13. **Add `replay = 0`** to `DatabaseNotifier._tableChanges`.

14. **Document encryption key lifecycle** - How passphrase is generated, stored, and cleared.

---

## Summary

The `core:database` module has a solid foundation using SQLCipher for encryption, but suffers from inconsistent parameter binding patterns, missing transaction handling, and incomplete schema coverage compared to the reference implementation. The most critical bugs are in `MessageDao.insertBatch()` which corrupts binary data, and the lack of transactions which can lead to partial state on failures.

The reference implementation's database is significantly more mature with ~40 tables, proper migration handling, and consistent transaction patterns. enchant-native should align its SQLCipher configuration and migration strategy with the reference implementation for compatibility and security.