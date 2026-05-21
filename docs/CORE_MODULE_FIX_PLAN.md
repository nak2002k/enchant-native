# Core Module Bug Fix Plan

> **Goal**: Bring all core modules to Signal parity or better.
> **Status**: 64 bugs identified across 6 modules. 4 critical, 22 high, 18 medium, 12 low.
> **Date**: 2026-05-21

---

## Priority Order

Fix in this order: **CRITICAL → HIGH (security) → HIGH (functional) → MEDIUM → LOW**

Each fix is a separate commit per AGENTS.md.

---

## CRITICAL BUGS (Fix First)

### C1. SQL Injection in PRAGMA — core/store/KeyValueStore.kt:177

**Bug**: `db.execSQL("PRAGMA key = '$password')"` — direct string interpolation. Password with `'` breaks SQL or allows injection.

**Signal does**: Uses `SQLiteDatabaseHook.preKey()` with proper SQLCipher key-setting API.

**Fix**:
```kotlin
// KeyValueStore.kt — StoreOpenHelper
override fun preKey(db: net.sqlcipher.database.SQLiteDatabase) {
    // SQLCipher supports parameterized key setting via raw key bytes
    db.rawExecSQL("PRAGMA key = \"x'${password.toByteArray().toHex()}\"")
}
```
Use hex encoding of the password bytes instead of raw string interpolation. This eliminates any possibility of quote-based injection.

**File**: `core/store/src/main/java/org/enchant/core/store/KeyValueStore.kt`
**Test**: Add test with password containing `'`, `"`, `\`, `;`, `--`

---

### C2. SQL Injection in PRAGMA — core/database/AppDatabase.kt:17-21

**Bug**: Same pattern — `db.execSQL("PRAGMA key = '...')"` in the SQLiteDatabaseHook.

**Signal does**: Uses `SqlCipherDatabaseHook` with proper cipher API.

**Fix**: Same as C1 — use hex-encoded key via `rawExecSQL`.

**File**: `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt`
**Test**: Add test with passphrase containing special characters.

---

### C3. Unencrypted Fallback — core/base/SecurePreferences.kt:33-36

**Bug**: When `allowUnencryptedFallback = true` and EncryptedSharedPreferences fails, ALL secrets (DB keys, identity keys) are stored in plaintext SharedPreferences. Catastrophic security failure.

**Signal does**: NEVER falls back to unencrypted storage. If encryption fails, the app crashes.

**Fix**: Remove the unencrypted fallback entirely. The `allowUnencryptedFallback` parameter should be removed. If EncryptedSharedPreferences fails, throw `IllegalStateException` and let the app crash.

```kotlin
@Synchronized
fun init(context: Context) {
    if (prefs != null) return
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    prefs = EncryptedSharedPreferences.create(
        context, "enchant_secure_prefs", masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    _isEncrypted = true
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/SecurePreferences.kt`
**Test**: Verify that init throws when EncryptedSharedPreferences cannot be created.

---

### C4. UUID-Based DB Password — core/store/EnchantStore.kt:71-74

**Bug**: `val newPassword = java.util.UUID.randomUUID().toString()` — random UUID stored in SecurePreferences as DB password. If SecurePreferences data is lost (app data cleared, backup restore), the database becomes permanently unreadable.

**Signal does**: Derives database key from AndroidKeyStore via `KeyStoreManager`, which is hardware-backed and survives app data operations.

**Fix**: Derive the database password from AndroidKeyStore instead of a random UUID:

```kotlin
// EnchantStore.kt
private fun getDatabasePassword(): ByteArray {
    // Use KeyStoreManager's getOrCreateDatabaseKey which wraps via AndroidKeyStore
    return KeyStoreManager.getOrCreateDatabaseKey()
}
```

Remove the UUID-based password generation. Use the existing `KeyStoreManager.getOrCreateDatabaseKey()` which already wraps a 32-byte key via AndroidKeyStore AES-GCM.

**File**: `core/store/src/main/java/org/enchant/core/store/EnchantStore.kt`
**Test**: Verify database is recoverable after app restart (key persists in keystore).

---

## HIGH SEVERITY — Security

### H1. leftoverStream Bypasses Byte Limit — core/base/stream/LimitedInputStream.kt:105-111

**Bug**: Returns raw underlying `wrapped` stream, allowing caller to read unlimited bytes past the limit.

**Fix**: Remove `leftoverStream()` method. If the caller needs remaining data, return a new `LimitedInputStream` with the remaining byte budget.

**File**: `core/base/src/main/java/org/enchant/core/base/stream/LimitedInputStream.kt`

---

### H2. Hardcoded Scrubber Salt — core/base/logging/Scrubber.kt:27

**Bug**: `private const val SALT = "enchant-scrubber-v1"` hardcoded in source. Anyone with APK can build rainbow tables to de-anonymize hashed PII.

**Signal does**: Does not store PII in logs at all; uses structured logging with explicit field redaction.

**Fix**: Generate a random salt at first launch and store it in `SecurePreferences`.

```kotlin
private val SALT: String by lazy {
    SecurePreferences.getString("scrubber_salt") ?: run {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hex = salt.joinToString("") { "%02x".format(it) }
        SecurePreferences.putString("scrubber_salt", hex)
        hex
    }
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/logging/Scrubber.kt`

---

### H3. DB Key Stored as Comma-Separated String — core/base/KeyStoreManager.kt:218

**Bug**: `wrapped.joinToString(",") { it.toString() }` — bytes to signed decimal strings. Fragile, wastes space, and if logged, exposes raw key bytes.

**Signal does**: Stores wrapped keys as Base64-encoded strings.

**Fix**: Use Base64 encoding:

```kotlin
SecurePreferences.putString("db.passphrase", Base64.encodeToString(wrapped, Base64.NO_WRAP))
// Decode:
val bytes = Base64.decode(raw, Base64.NO_WRAP)
```

**File**: `core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt`

---

### H4. Hardcoded GCM 128-bit Tag — core/base/KeyStoreManager.kt:201

**Bug**: `GCMParameterSpec(128, iv)` — hardcoded tag length. Will fail if any ciphertext was encrypted with a different tag length (96 or 192 bits).

**Fix**: The encrypt method uses `cipher.iv` which is 12 bytes (96-bit IV) and the tag is appended by `cipher.doFinal()`. The GCM tag length is determined by the cipher, not the spec. Since we control both encrypt and decrypt, 128-bit is fine for now. But add a comment and make it configurable:

```kotlin
suspend fun decrypt(alias: String, ciphertext: ByteArray, tagLengthBits: Int = 128): ByteArray? {
    ...
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(tagLengthBits, iv))
    ...
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt`

---

### H5. TURN Credentials in Plain SharedPreferences — core/base/AppConfig.kt:72-74

**Bug**: TURN username/password read from plain SharedPreferences (`enchant_config`). Extractable from rooted device.

**Signal does**: Stores sensitive credentials in encrypted KeyValueStore.

**Fix**: Store TURN credentials in `EnchantStore` (SQLCipher-backed) instead of plain SharedPreferences.

**File**: `core/base/src/main/java/org/enchant/core/base/AppConfig.kt`

---

### H6. Default Gateway URL is HTTP — core/base/AppConfig.kt:100

**Bug**: `"http://localhost:8080"` — plaintext HTTP default.

**Fix**: Use `"https://localhost:8080"` and require explicit configuration for production.

**File**: `core/base/src/main/java/org/enchant/core/base/AppConfig.kt`

---

### H7. FTS Search Query Sanitization Incomplete — core/database/dao/MessageDao.kt:153-162

**Bug**: Sanitization removes `*`, `+`, `-`, `NEAR`, `AND`, `OR`, `NOT` via simple string replacement. Case-sensitive (`and` not removed). FTS5 also supports `COLUMN:` and phrase matching with `"`.

**Signal does**: Uses parameterized FTS queries with proper escaping.

**Fix**: Use FTS5's built-in escaping. Wrap the query in double quotes and escape internal quotes:

```kotlin
private fun sanitizeFtsQuery(query: String): String {
    // Escape double quotes and wrap in quotes for exact phrase matching
    val escaped = query.replace("\"", "\"\"")
    return "\"$escaped\""
}
```

**File**: `core/database/src/main/java/org/enchant/core/database/dao/MessageDao.kt`

---

## HIGH SEVERITY — Functional

### H8. Reader Pool Array Index Overflow — core/database/AppDatabase.kt:80

**Bug**: `readerIndex.getAndIncrement() % maxReaders` — `AtomicInteger` overflows to negative after 2^31 calls. `negative % 4` is negative → `ArrayIndexOutOfBoundsException`.

**Fix**: Use bitwise AND (works when maxReaders is power of 2):

```kotlin
val reader: SQLiteDatabase get() = readerPool[readerIndex.getAndIncrement() and (maxReaders - 1)]
```

**File**: `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt`

---

### H9. Reader Pool Never Closed — core/database/AppDatabase.kt:85-87

**Bug**: `close()` only closes `writer`. The 4 reader databases are never closed → resource leak (open file descriptors, memory).

**Fix**:
```kotlin
fun close() {
    readerPool.forEach { it.close() }
    writer.close()
}
```

**File**: `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt`

---

### H10. Migration v2 Not Wrapped in Transaction — core/database/AppDatabase.kt:40-43

**Bug**: FTS index creation and population not wrapped in transaction. If process killed during `INSERT INTO messages_fts...SELECT...`, FTS index left partially populated.

**Signal does**: Wraps each migration in `beginTransaction/setTransactionSuccessful/endTransaction`.

**Fix**:
```kotlin
2 -> {
    db.beginTransaction()
    try {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts5(...)")
        db.execSQL("INSERT INTO messages_fts(rowid, content, conversation_id) SELECT local_id, content, conversation_id FROM messages")
        db.execSQL("PRAGMA user_version = 2")
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
```

Apply same pattern to migrations 3 and 4.

**File**: `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt`

---

### H11. KeyedSerialExecutor Queue Removal Race — core/base/concurrent/KeyedSerialExecutor.kt:68-70

**Bug**: Between `queue.isEmpty()` check and `queues.remove(key, queue)`, a new task could be added → orphaned task (in queue but no worker processing it).

**Signal does**: Uses `AtomicReference` to track active worker and handles race properly.

**Fix**: Use atomic check-and-remove with re-check:

```kotlin
while (true) {
    val runnable = queue.poll()
    if (runnable != null) {
        runnable.run()
        continue
    }
    // Queue is empty — try to remove ourselves
    if (queues.remove(key, queue)) {
        // Successfully removed — check if new tasks arrived
        if (queue.isEmpty()) break
        // Race: new task arrived after we polled but before we removed
        // Re-add ourselves and continue
        queues[key] = queue
    }
    // If remove failed, another worker already took over
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/concurrent/KeyedSerialExecutor.kt`

---

### H12. KeyedSerialExecutor Exceptions Swallowed — core/base/concurrent/KeyedSerialExecutor.kt:65-67

**Bug**: `catch (e: Exception) {}` — exceptions completely swallowed with no logging.

**Fix**:
```kotlin
catch (e: Exception) {
    android.util.Log.e(TAG, "Task failed for key $key", e)
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/concurrent/KeyedSerialExecutor.kt`

---

### H13. Crash Handler blockUntilWritesFinish Doesn't Actually Wait — core/crash/CrashReporter.kt:44-53

**Bug**: `pool?.write { db -> db.execSQL("SELECT 1") }` submits a no-op query but doesn't wait for pending writes to complete. `EnchantStore.storage.flushPendingWrites()` drains the queue but doesn't guarantee writes are flushed to disk.

**Signal does**: Uses `CountDownLatch` — submits a task that counts down the latch, then awaits the latch.

**Fix**: Implement proper `blockUntilAllWritesFinished` in `KeyValueStore`:

```kotlin
// KeyValueStore.kt
override fun blockUntilAllWritesFinished() {
    val latch = CountDownLatch(1)
    writeExecutor.execute {
        latch.countDown()
    }
    latch.await(5, TimeUnit.SECONDS)
}
```

Then in CrashHandler:
```kotlin
private fun blockUntilWritesFinish() {
    try {
        EnchantStore.storage.blockUntilAllWritesFinished()
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Failed to block on writes", e)
    }
}
```

**Files**:
- `core/store/src/main/java/org/enchant/core/store/KeyValueStore.kt`
- `core/store/src/main/java/org/enchant/core/store/KeyValueStorage.kt` (add interface method)
- `core/crash/src/main/java/org/enchant/core/crash/CrashReporter.kt`

---

### H14. FTS Reset in Crash Handler May Cause Further Corruption — core/crash/CrashReporter.kt:56-84

**Bug**: Crash handler attempts to DROP and RECREATE FTS tables during an uncaught exception. Database may be in inconsistent state → DDL statements can cause further corruption or deadlock.

**Signal does**: Sets a flag and performs reset on next app startup through proper database machinery.

**Fix**: Set a flag in `SecurePreferences` indicating FTS needs reset. Perform the reset on next app startup:

```kotlin
// CrashReporter.kt
if (isFtsCorruption) {
    SecurePreferences.putBoolean("fts_needs_reset", true)
}

// App startup (e.g., in Application.onCreate or DatabasePool init):
if (SecurePreferences.getBoolean("fts_needs_reset", false)) {
    resetFtsIndex()
    SecurePreferences.remove("fts_needs_reset")
}
```

**Files**:
- `core/crash/src/main/java/org/enchant/core/crash/CrashReporter.kt`
- `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt`

---

### H15. runBlocking in LogDatabase Can Deadlock — core/database/LogDatabase.kt (all methods)

**Bug**: Every method wraps calls in `runBlocking { ... }`. If called from a coroutine that holds the database write lock, this will deadlock.

**Signal does**: LogDatabase is synchronous (no coroutines) — uses direct SQLite calls.

**Fix**: Make `LogDatabase` methods synchronous by calling DAO methods directly without `runBlocking`. The DAO methods should NOT be suspend functions since they use synchronous `pool.write`/`pool.readWith`:

```kotlin
// CrashLogDao.kt — remove suspend from all methods
class CrashLogDao(private val pool: DatabasePool) {
    fun insert(...): Long {
        return pool.write { db -> ... }
    }
    fun getAll(limit: Int = 100): List<CrashEntity> { ... }
    // etc.
}

// LogDatabase.kt — remove runBlocking
object LogDatabase {
    fun saveCrash(...) { crashes.insert(...) }
    fun getAllCrashes(limit: Int = 100) = crashes.getAll(limit)
    // etc.
}
```

**Files**:
- `core/database/src/main/java/org/enchant/core/database/dao/CrashLogDao.kt`
- `core/database/src/main/java/org/enchant/core/database/LogDatabase.kt`
- `core/database/src/test/java/org/enchant/core/database/dao/CrashLogDaoTest.kt` (update tests)

---

### H16. Crash Handler Name Collision with Logging Module — core/crash/CrashReporter.kt:130-164

**Bug**: File defines its own `object Log` which shadows `org.enchant.core.base.logging.Log`. The crash module's Log is a separate in-memory buffer with no persistence.

**Signal does**: Uses centralized `org.signal.core.util.logging.Log` everywhere, including crash handler.

**Fix**: Remove the local `Log` object. Use `android.util.Log` directly in CrashHandler (which it already does for the TAG-based logging). Remove the duplicate `Log` object entirely.

**File**: `core/crash/src/main/java/org/enchant/core/crash/CrashReporter.kt`

---

### H17. Crash Handler Stack Trace Limited to 30 Frames — core/crash/CrashReporter.kt:119

**Bug**: `throwable.stackTrace.take(30)` truncates stack trace. Deep call chains lose root cause.

**Signal does**: Uses `ExceptionUtil.convertThrowableToString(e)` which captures full trace.

**Fix**: Increase to 100 frames and handle suppressed exceptions:

```kotlin
private fun getStackTrace(throwable: Throwable): String {
    return buildString {
        append(throwable::class.java.name)
        throwable.message?.let { append(": $it") }
        append("\n")
        throwable.stackTrace.take(100).forEach { frame ->
            append("  at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})\n")
        }
        throwable.cause?.let { cause ->
            append("Caused by: ")
            append(getStackTrace(cause))
        }
        throwable.suppressedExceptions.forEach { suppressed ->
            append("Suppressed: ")
            append(getStackTrace(suppressed))
        }
    }
}
```

**File**: `core/crash/src/main/java/org/enchant/core/crash/CrashReporter.kt`

---

### H18. ConversationDao getAll Flow Loses Notifications — core/database/dao/ConversationDao.kt:35-50

**Bug**: `callbackFlow` collects from `DatabaseNotifier.tableChanges`, but `DatabaseNotifier` is `MutableSharedFlow` with no replay. If table change occurs before collector starts, notification is lost → Flow never re-queries.

**Fix**: Use `SharedFlow` with replay=1, or use Room's `@Query` with Flow return type:

```kotlin
// DatabaseNotifier.kt
private val _tableChanges = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 64)
```

**Files**:
- `core/database/src/main/java/org/enchant/core/database/util/DatabaseNotifier.kt`
- `core/database/src/main/java/org/enchant/core/database/dao/ConversationDao.kt`

---

### H19. RecipientDao Flows Are One-Shot — core/database/dao/RecipientDao.kt:56-75

**Bug**: `callbackFlow` implementations query database once, `trySend` result, and `awaitClose` without ever collecting from `DatabaseNotifier`. Effectively one-shot flows, not reactive streams.

**Fix**: Add `DatabaseNotifier.tableChanges.collect` like `ConversationDao.getAll()` does.

**File**: `core/database/src/main/java/org/enchant/core/database/dao/RecipientDao.kt`

---

### H20. UNBOUNDED Executor Can Cause OOM — core/base/EnchantExecutors.kt:15-16

**Bug**: `newCachedThreadPool` creates unlimited threads. Under load, spawns thousands of threads → OOM.

**Signal does**: Uses bounded executors with explicit thread limits.

**Fix**: Replace with bounded executor:

```kotlin
val UNBOUNDED: ExecutorService = ThreadPoolExecutor(
    2, 64, 60L, TimeUnit.SECONDS,
    LinkedBlockingQueue(1024),
    ThreadFactory { r -> Thread(r, "Enchant-Unbounded").apply { isDaemon = true } }
) { runnable, executor ->
    // Rejection policy: run in calling thread if queue full
    runnable.run()
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/EnchantExecutors.kt`

---

### H21. DeadlockDetector False Positives — core/base/concurrent/DeadlockDetector.kt:56-59

**Bug**: Flags ANY 2+ threads in BLOCKED or WAITING as "potential deadlock." WAITING is normal (e.g., waiting on queue). BLOCKED with 2+ threads is normal under lock contention.

**Signal does**: Does not implement simple thread-state-based deadlock detector; uses proper ANR detection via main-thread monitoring.

**Fix**: Remove WAITING from the condition. Only flag threads that have been BLOCKED for longer than a threshold (e.g., 10 seconds):

```kotlin
val blockedThreads = threads.filter { t ->
    t.state == Thread.State.BLOCKED &&
    (System.currentTimeMillis() - t.lastBlockedTime) > BLOCKED_THRESHOLD_MS
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/concurrent/DeadlockDetector.kt`

---

### H22. ANR Detector running Flag Not Volatile — core/base/concurrent/AnrDetector.kt:33

**Bug**: `running` is read from main thread and written from potentially any thread (`stop()`). Without `@Volatile`, write may not be visible to reading thread.

**Fix**: Add `@Volatile`:

```kotlin
@Volatile private var running = false
```

**File**: `core/base/src/main/java/org/enchant/core/base/concurrent/AnrDetector.kt`

---

### H23. throttleLatest Double Emission — core/base/FlowExtensions.kt:21-33

**Bug**: Values where `emitImmediately` returns true are sent TWICE — once in `onEach` and once in `collect`.

**Fix**: Use `channelFlow` with proper branching:

```kotlin
fun <T> Flow<T>.throttleLatest(timeout: Long, emitImmediately: (T) -> Boolean = { true }): Flow<T> = channelFlow {
    var lastEmitTime = 0L
    var pendingValue: T? = null
    collect { value ->
        val now = System.currentTimeMillis()
        if (now - lastEmitTime >= timeout || emitImmediately(value)) {
            send(value)
            lastEmitTime = now
            pendingValue = null
        } else {
            pendingValue = value
        }
    }
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/FlowExtensions.kt`

---

### H24. LRUCache ConcurrentModificationException — core/base/LRUCache.kt:39-41

**Bug**: `evict(count)` iterates over `map.keys.take(count)` while holding lock, but `take()` on live view of keys can cause `ConcurrentModificationException` if another thread modifies map during iteration.

**Fix**: Copy keys to list first:

```kotlin
fun evict(count: Int) {
    synchronized(this) {
        val keys = map.keys.toList().take(count)
        keys.forEach { map.remove(it) }
    }
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/LRUCache.kt`

---

### H25. ResettableLazy reset() Not Thread-Safe — core/base/ResettableLazy.kt:42-44

**Bug**: `value = UNINITIALIZED` is plain write without synchronization. If one thread is in `getValue` (inside synchronized block, about to read `value`) while another calls `reset()`, the write may not be visible.

**Fix**: Make `reset()` also use `synchronized(this)`:

```kotlin
fun reset() {
    synchronized(this) {
        value = UNINITIALIZED
    }
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/ResettableLazy.kt`

---

### H26. AndroidLogger blockUntilAllWritesFInished Can Deadlock — core/base/logging/AndroidLogger.kt:71-74

**Bug**: `logExecutor.execute { }` followed by `logExecutor.awaitTermination(5, TimeUnit.SECONDS)` — but `awaitTermination` only works after `shutdown()` is called. Since executor is never shut down, `awaitTermination` returns immediately without waiting.

**Signal does**: Uses `CountDownLatch` pattern.

**Fix**:
```kotlin
fun blockUntilAllWritesFinished() {
    val latch = CountDownLatch(1)
    logExecutor.execute { latch.countDown() }
    latch.await(5, TimeUnit.SECONDS)
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/logging/AndroidLogger.kt`

---

### H27. SecurePreferences getBoolean Overload Confusion — core/base/SecurePreferences.kt:101-108

**Bug**: Two `getBoolean` overloads with different nullability. Kotlin's overload resolution may pick wrong one. Nullable version has logic bug: `p.getBoolean(key, default ?: false)` uses `false` as SharedPreferences default even when `default` is `null`.

**Fix**: Rename nullable version to `getBooleanOrNull()`:

```kotlin
fun getBoolean(key: String, default: Boolean = false): Boolean {
    return getPrefs()?.getBoolean(key, default) ?: default
}

fun getBooleanOrNull(key: String, default: Boolean? = null): Boolean? {
    val p = getPrefs() ?: return default
    return if (p.contains(key)) p.getBoolean(key, default ?: false) else default
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/SecurePreferences.kt`

---

### H28. SecurePreferences init Not Thread-Safe — core/base/SecurePreferences.kt:18-41

**Bug**: `prefs != null` check at line 19 is outside synchronized block. Two threads could both see `prefs == null`, both enter init, one overwrites other's initialized prefs.

**Fix**: Use double-checked locking:

```kotlin
fun init(context: Context) {
    if (prefs != null) return
    synchronized(this) {
        if (prefs != null) return
        // ... init logic
    }
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/SecurePreferences.kt`

---

### H29. KeyStoreManager Test Key Leak — core/base/KeyStoreManager.kt:34-44

**Bug**: Hardware-backed check creates real EC keypair named `"__test__"` in AndroidKeyStore. If app killed between creation and deletion, test key leaks into keystore. Can accumulate and hit keystore limits.

**Signal does**: Uses `KeyInfo.isInsideSecureHardware` on existing key or checks `KeyProperties` for StrongBox support without creating a key.

**Fix**: Check StrongBox support without creating a key:

```kotlin
_isHardwareBacked = try {
    val keyInfo = KeyInfo::class.java
    val factory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
    // Check if StrongBox is supported by attempting to get KeyInfo for a hypothetical key
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        SecureEnclave.isSupported() // or use KeyProperties directly
} catch (_: Exception) {
    false
}
```

Or simpler: check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` and `KeyProperties` support.

**File**: `core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt`

---

### H30. getOrCreateDatabaseKey Infinite Recursion — core/base/KeyStoreManager.kt:221-226

**Bug**: If data is corrupted (NumberFormatException), removes and retries. If decryption keeps failing, no recursion limit → stack overflow.

**Fix**: Add max retry count:

```kotlin
suspend fun getOrCreateDatabaseKey(retryCount: Int = 0): ByteArray {
    if (retryCount > 2) {
        throw IllegalStateException("Failed to retrieve database key after 3 attempts")
    }
    // ... existing logic ...
    } catch (e: NumberFormatException) {
        SecurePreferences.remove("db.passphrase")
        return getOrCreateDatabaseKey(retryCount + 1)
    }
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt`

---

## MEDIUM SEVERITY

### M1. Cache-to-Database Inconsistency on Crash — core/store/KeyValueStore.kt:77-82

**Bug**: `cache[key] = value` set BEFORE `enqueueWrite()`. If app crashes before write processed, cache contains value never persisted. On restart, stale database value loaded.

**Signal does**: Write-through cache where writes are enqueued AND crash handler ensures all pending writes are flushed.

**Fix**: With H13 (blockUntilAllWritesFinished), the crash handler will flush pending writes. But for extra safety, change to write-through for critical keys:

```kotlin
override fun putString(key: String, value: String?) {
    enqueueWrite(WriteOperation.PutString(key, value))
    cache[key] = value  // Update cache AFTER enqueue (order matters for crash consistency)
}
```

Actually, the real fix is H13 — ensuring crash handler flushes writes. The cache-before-write pattern is fine as long as crash handler guarantees flush.

**File**: `core/store/src/main/java/org/enchant/core/store/KeyValueStore.kt`

---

### M2. contains() TOCTOU Race — core/store/KeyValueStore.kt:75

**Bug**: `cache.containsKey(key) || dbHelper.contains(key)` — if another thread removes key from DB between cache check and DB check, stale data may be returned.

**Fix**: Rely solely on cache (source of truth):

```kotlin
override fun contains(key: String): Boolean = cache.containsKey(key)
```

**File**: `core/store/src/main/java/org/enchant/core/store/KeyValueStore.kt`

---

### M3. Shutdown Hook Unreliable on Android — core/store/KeyValueStore.kt:148

**Bug**: `Runtime.getRuntime().addShutdownHook()` not reliable on Android. JVM shutdown hook may not run when app killed by system (force-stop, low memory killer).

**Signal does**: Relies on `SignalUncaughtExceptionHandler` to flush writes, not shutdown hooks.

**Fix**: Remove shutdown hook. Rely on crash handler's `blockUntilAllWritesFinished`.

**File**: `core/store/src/main/java/org/enchant/core/store/KeyValueStore.kt`

---

### M4. CursorMapper Reflection Slow and Fragile — core/database/util/CursorMapper.kt:27-58

**Bug**: Uses Kotlin reflection for every row mapping. Significantly slower than hand-written mappers. Breaks with obfuscation (R8/ProGuard).

**Signal does**: Hand-written cursor mapping with explicit column indices.

**Fix**: For now, keep reflection but add R8 keep rules:

```proguard
# Keep entity classes for reflection
-keep class org.enchant.core.database.entity.** { *; }
-keepclassmembers class org.enchant.core.database.entity.** { *; }
```

Long-term: Use KSP code generation to generate mappers at compile time.

**File**: `core/database/src/main/java/org/enchant/core/database/util/CursorMapper.kt`

---

### M5. Migration Framework Dead Code — core/database/AppDatabase.kt:34-69, 285-298

**Bug**: `DatabaseMigrator` class defined but never used. `onUpgrade` has hardcoded migration logic instead.

**Fix**: Either use `DatabaseMigrator` or remove it. For now, integrate it:

```kotlin
override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    android.util.Log.w("AppDatabase", "DB upgrade from v$oldVersion to v$newVersion")
    runBlocking {
        DatabaseMigrator(migrations).migrate(db, oldVersion, newVersion)
    }
}
```

Or remove `DatabaseMigrator` and keep hardcoded migrations (simpler for now).

**File**: `core/database/src/main/java/org/enchant/core/database/AppDatabase.kt`

---

### M6. ANR Detector False-Triggers on GC Pauses — core/base/concurrent/AnrDetector.kt:38-44

**Bug**: Heartbeat checks `now - lastAckTime > thresholdMs`, but `lastAckTime` only updated when heartbeat Runnable runs. Long GC pause causes false ANR detection.

**Fix**: Use monotonic clock and add GC-aware detection:

```kotlin
private fun checkForAnr() {
    val now = SystemClock.uptimeMillis()
    val elapsed = now - lastAckTime
    if (elapsed > thresholdMs) {
        // Check if this could be a GC pause
        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        if (totalMemory - freeMemory > GC_THRESHOLD) {
            // Likely GC pause, don't trigger ANR
            return
        }
        onAnrDetected()
    }
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/concurrent/AnrDetector.kt`

---

### M7. KeyedSerialExecutor shutdown() Blocks Calling Thread — core/base/concurrent/KeyedSerialExecutor.kt:84

**Bug**: `executor.awaitTermination(10, TimeUnit.SECONDS)` blocks calling thread. If called from main thread → ANR.

**Fix**: Use non-blocking shutdown or ensure shutdown called from background thread:

```kotlin
fun shutdown() {
    executor.shutdown()
    // Don't awaitTermination on main thread
    if (Looper.getMainLooper().isCurrentThread) {
        // Schedule shutdown check on background thread
        Executors.newSingleThreadExecutor().execute {
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    } else {
        executor.awaitTermination(10, TimeUnit.SECONDS)
    }
}
```

**File**: `core/base/src/main/java/org/enchant/core/base/concurrent/KeyedSerialExecutor.kt`

---

### M8. retry Function Retries Entire Flow — core/base/FlowExtensions.kt:45-58

**Bug**: On failure, entire flow re-collected from start. Already-emitted values re-emitted — almost certainly not desired behavior.

**Fix**: Use `retry` from kotlinx-coroutines-core which is designed for this purpose, or remove this function.

**File**: `core/base/src/main/java/org/enchant/core/base/FlowExtensions.kt`

---

### M9. DeadlockDetector Executor Never Shut Down Gracefully — core/base/concurrent/DeadlockDetector.kt:52

**Bug**: `executor.shutdown()` called but `awaitTermination` not called. Tasks in queue silently dropped.

**Fix**: Add `executor.awaitTermination(5, TimeUnit.SECONDS)` after `shutdown()`.

**File**: `core/base/src/main/java/org/enchant/core/base/concurrent/DeadlockDetector.kt`

---

### M10. Config Format Fragile — core/config/RemoteConfig.kt:53-55

**Bug**: `config.split(";")` and `it.startsWith("$key=")` fragile parsing. If value contains `;` or `=`, parsing breaks.

**Signal does**: Uses JSON (`JSONObject`) for robust parsing.

**Fix**: Switch to JSON format for config storage:

```kotlin
fun parseConfig(json: String): Map<String, String> {
    val obj = JSONObject(json)
    return obj.keys().asSequence().associateWith { obj.getString(it) }
}
```

**File**: `core/config/src/main/java/org/enchant/core/config/RemoteConfig.kt`

---

### M11. No Fetch Interval or Staleness Check — core/config/RemoteConfig.kt:18-24

**Bug**: `getString` always returns pending config without checking if stale.

**Signal does**: Has `FETCH_INTERVAL` (2 hours) and only refreshes if enough time has passed.

**Fix**: Add staleness checking:

```kotlin
private const val FETCH_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 hours

fun isStale(): Boolean {
    val lastFetch = EnchantStore.config.lastFetchTs
    return lastFetch == 0L || System.currentTimeMillis() - lastFetch > FETCH_INTERVAL_MS
}
```

**File**: `core/config/src/main/java/org/enchant/core/config/RemoteConfig.kt`

---

### M12. Accessibility LiveRegion Announcement Debounce Missing — core/accessibility/LiveRegionAnnouncer.kt:39-44

**Bug**: Rapid calls to `announce()` queue up many TalkBack announcements → poor UX.

**Signal does**: Throttles accessibility announcements.

**Fix**: Add debounce mechanism:

```kotlin
private var lastAnnounceTime = 0L
private const val DEBOUNCE_MS = 500L

fun announce(text: String) {
    val now = SystemClock.uptimeMillis()
    if (now - lastAnnounceTime < DEBOUNCE_MS) {
        pendingText = text // Replace pending announcement
        return
    }
    liveRegion?.let { view ->
        (view as? TextView)?.text = text
        view.contentDescription = text
    }
    lastAnnounceTime = now
    pendingText = null
}
```

**File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/LiveRegionAnnouncer.kt`

---

### M13. Accessibility isScreenReaderEnabled Conflates Touch Exploration — core/accessibility/AccessibilityHelper.kt:20-23

**Bug**: Returns `true` only when BOTH `isEnabled` AND `isTouchExplorationEnabled`. Some accessibility services (Switch Access) enable accessibility without touch exploration.

**Fix**: Split into two methods:

```kotlin
fun isScreenReaderActive(manager: AccessibilityManager): Boolean {
    return manager.isTouchExplorationEnabled
}

fun isAnyAccessibilityServiceEnabled(manager: AccessibilityManager): Boolean {
    return manager.isEnabled
}
```

**File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/AccessibilityHelper.kt`

---

### M14. FocusTraversalHelper Silently Skips Views Without IDs — core/accessibility/FocusTraversalHelper.kt:29-31

**Bug**: If `previousId == View.NO_ID`, traversal link not set, no warning logged. Caller has no way to know focus order is broken.

**Fix**: Log a warning:

```kotlin
if (previousId == View.NO_ID) {
    android.util.Log.w("FocusTraversal", "View ${child.id} has no ID, skipping traversal link")
} else {
    child.accessibilityTraversalAfter = previousId
}
```

**File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/FocusTraversalHelper.kt`

---

### M15. Reaction Emoji Matching Uses Exact Unicode — core/accessibility/AccessibilityDelegate.kt:297-307

**Bug**: Emoji comparison uses exact string matching. Fails for emoji with different normalization forms (NFC vs NFD), skin tone modifiers, or platform-specific renderings.

**Fix**: Use `androidx.emoji2.text.EmojiCompat` or normalize emoji before comparison:

```kotlin
private fun normalizeEmoji(emoji: String): String {
    return java.text.Normalizer.normalize(emoji, java.text.Normalizer.Form.NFC)
}
```

**File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/AccessibilityDelegate.kt`

---

### M16. DatabaseNotifier tryEmit Silently Drops Events — core/database/util/DatabaseNotifier.kt:11-13

**Bug**: `tryEmit` silently drops events when buffer full (64 capacity).

**Signal does**: Uses proper observer pattern with backpressure handling.

**Fix**: Increase buffer capacity and use `emit` with retry:

```kotlin
private val _tableChanges = MutableSharedFlow<String>(
    replay = 1,
    extraBufferCapacity = 256,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)

fun notify(table: String) {
    _tableChanges.tryEmit(table) // Now drops oldest instead of failing
}
```

**File**: `core/database/src/main/java/org/enchant/core/database/util/DatabaseNotifier.kt`

---

## LOW SEVERITY

### L1. AccessibilityDelegate String Comparison — core/accessibility/AccessibilityDelegate.kt:42

**Bug**: `direction` parameter is `String` compared with `== "outgoing"`. Error-prone.

**Fix**: Use `enum class MessageDirection { INCOMING, OUTGOING }`.

**File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/AccessibilityDelegate.kt`

---

### L2. AccessibilityDelegate resolveButtonActionKey Throws — core/accessibility/AccessibilityDelegate.kt:291

**Bug**: `throw IllegalArgumentException("Unknown button action key: $actionKey")` crashes app if new button added without updating mapping.

**Fix**: Return default string resource instead of throwing.

**File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/AccessibilityDelegate.kt`

---

### L3. AccessibilityExtensions Unnecessary API 34 Branching — core/accessibility/AccessibilityExtensions.kt:13-18

**Bug**: `ViewCompat.setAccessibilityLiveRegion()` works on all API levels. Direct setter on API 34+ unnecessary.

**Fix**: Use `ViewCompat.setAccessibilityLiveRegion(this, mode)` for all API levels.

**File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/AccessibilityExtensions.kt`

---

### L4. AccessibilityHelper Catches Only SettingNotFoundException — core/accessibility/AccessibilityHelper.kt:46-53

**Bug**: Only catches `Settings.SettingNotFoundException`. On some devices, reading `Settings.Global` may throw `SecurityException`.

**Fix**: Catch `Exception` broadly.

**File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/AccessibilityHelper.kt`

---

### L5. LiveRegionAnnouncer Casts to TextView Without Null Safety — core/accessibility/LiveRegionAnnouncer.kt:43

**Bug**: `(view as? TextView)?.text = text` silently does nothing if view not TextView, but still sets `contentDescription`. Inconsistency.

**Fix**: Require TextView at attach time.

**File**: `core/accessibility/src/main/java/org/enchant/core/accessibility/LiveRegionAnnouncer.kt`

---

### L6. Log Queue Can Overflow Silently — core/base/logging/AndroidLogger.kt:22-25

**Bug**: When queue full (1024 entries), rejection handler runs task in calling thread. Log writes can block calling thread (including main thread) under heavy load.

**Fix**: Drop oldest entries instead of blocking caller.

**File**: `core/base/src/main/java/org/enchant/core/base/logging/AndroidLogger.kt`

---

### L7. Phone Number Regex Too Greedy — core/base/logging/Scrubber.kt:30-31

**Bug**: `"\\+?[0-9]{7,15}"` matches any 7-15 digit sequence including timestamps, IDs.

**Fix**: Add word boundary assertions or use more specific pattern.

**File**: `core/base/src/main/java/org/enchant/core/base/logging/Scrubber.kt`

---

### L8. Sign/Verify Use Hardcoded Algorithm — core/base/KeyStoreManager.kt:151, 165

**Bug**: Both methods hardcode `"SHA256withECDSA"`. If key generated with different algorithm, signing/verification fails silently.

**Fix**: Derive algorithm from key's metadata.

**File**: `core/base/src/main/java/org/enchant/core/base/KeyStoreManager.kt`

---

### L9. Stack Trace Does Not Handle Suppressed Exceptions — core/crash/CrashReporter.kt:114-127

**Bug**: Only `throwable.cause` traversed. `throwable.suppressedExceptions` ignored.

**Fix**: Already addressed in H17.

**File**: `core/crash/src/main/java/org/enchant/core/crash/CrashReporter.kt`

---

### L10. CursorMapper Column Name Conversion May Not Match — core/database/util/CursorMapper.kt:33-34

**Bug**: `name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()` — if entity property name doesn't follow convention, mapping silently fails.

**Fix**: Add explicit column name annotations or validate at startup.

**File**: `core/database/src/main/java/org/enchant/core/database/util/CursorMapper.kt`

---

### L11. deleteByIds Executes Individual DELETEs — core/database/dao/CallLogDao.kt:41-43

**Bug**: `callIds.forEach { db.execSQL("DELETE ...") }` executes N individual DELETE statements.

**Fix**: Use single batched DELETE with IN clause.

**File**: `core/database/src/main/java/org/enchant/core/database/dao/CallLogDao.kt`

---

### L12. AppConfig Default Gateway HTTP — core/base/AppConfig.kt:100

**Bug**: `"http://localhost:8080"` uses plaintext HTTP.

**Fix**: Use `"https://localhost:8080"`.

**File**: `core/base/src/main/java/org/enchant/core/base/AppConfig.kt`

---

## Missing Features (Signal Parity Gaps)

These are not bugs but missing features that Signal has:

| Feature | Module | Signal Implementation | Our Status |
|---------|--------|----------------------|------------|
| Database corruption error handler | database | `SqlCipherErrorHandler` (203 lines) | Missing |
| Database backup/restore handling | database | `runPostBackupRestoreTasks` | Missing |
| FTS index rebuild capability | database | `messageSearch.rebuildIndex()` | Missing |
| Per-account session isolation | database | `SessionTable` with `account_id` | Missing |
| beginRead() snapshot isolation | store | `KeyValueDataSet.beginRead()` | Missing |
| Type validation with exceptions | store | `readValueAsType()` validates types | Missing |
| Synchronized getters | store | All getters `synchronized` | Missing |
| KeyValuePersistentStorage abstraction | store | Interface separating backend from store | Missing |
| Database upgrade/migration | store | `onUpgrade` handles schema evolution | Empty implementation |
| JobManager.flush() on crash | crash | Flushes job queue before dying | Missing |
| SSLException early return | crash | Detects and returns on SSLException | Missing |
| RxJava exception unwrapping | crash | Unwraps `OnErrorNotImplementedException` | Missing |
| Crash submission to remote server | crash | Syncs crashes to server | Missing |
| Call quality tracking | calls | `CallQuality.handleSummary()` | Missing |
| Multi-device call handling | calls | `ANSWERED_ELSEWHERE` handling | Incomplete |
| Notification profiles | calls | `NotificationProfiles.getActiveProfile()` | Missing |
| PSTN busy line check | calls | `TelephonyUtil.isAnyPstnLineBusy()` | Missing |
| Call link support | calls | `CallLinkRootKey`, `CallLinkSecretParams` | Missing |
| Screen share intent handling | calls | `handleSetLocalScreenShare` | Missing |
| Low bandwidth video handling | calls | `onLowBandwidthForVideo` callback | Missing |
| Camera error recovery | calls | `onCameraStopped()` disables video | Missing |
| Orientation change handling | calls | `handleOrientationChanged` | Missing |
| Group call SFU support | calls | ringrtc `GroupCall` integration | Missing |
| Safety number change notification | calls | `GroupCallSafetyNumberChangeNotificationUtil` | Missing |
| ringrtc native CallManager | calls | Native WebRTC wrapper | Missing (uses manual WebRTC) |
| KeyedSerialMonoLifoExecutor | calls | Per-recipient serialization | Missing (global serialization) |
| TURN server cache | calls | Thread-safe with TTL | Simple timestamp check |
| AccessibilityService connection tracking | accessibility | `removeAccessibilityStateChangeListener` | Missing |
| Content description change handling | accessibility | `CONTENT_CHANGE_TYPE_DESCRIPTION` | Missing |
| Font scale tiers | accessibility | Multiple tiers (1.2x, 1.3x, 1.4x+) | Single threshold |
| Haptic accessibility feedback | accessibility | Tied to accessibility settings | Missing |
| Node info pooling | accessibility | Reuses `AccessibilityNodeInfo` | Creates new per call |

---

## Execution Plan

### Phase 1: Critical Security (Day 1-2)
1. C1: SQL injection in KeyValueStore PRAGMA
2. C2: SQL injection in AppDatabase PRAGMA
3. C3: Remove unencrypted fallback in SecurePreferences
4. C4: Replace UUID-based DB password with KeyStoreManager

### Phase 2: High Security (Day 2-3)
5. H1: Remove leftoverStream bypass
6. H2: Generate random scrubber salt
7. H3: Base64 encode DB key
8. H4: Make GCM tag length configurable
9. H5: Move TURN credentials to EnchantStore
10. H6: Change default gateway to HTTPS
11. H7: Fix FTS search sanitization

### Phase 3: High Functional (Day 3-5)
12. H8: Fix reader pool array index overflow
13. H9: Close reader pool in close()
14. H10: Wrap migrations in transactions
15. H11: Fix KeyedSerialExecutor queue race
16. H12: Log swallowed exceptions in executor
17. H13: Implement blockUntilAllWritesFinished
18. H14: Defer FTS reset to app startup
19. H15: Remove runBlocking from LogDatabase
20. H16: Remove duplicate Log object in crash module
21. H17: Increase stack trace limit + suppressed exceptions
22. H18: Fix ConversationDao Flow notification loss
23. H19: Fix RecipientDao one-shot flows
24. H20: Bound UNBOUNDED executor
25. H21: Fix DeadlockDetector false positives
26. H22: Add @Volatile to ANR detector
27. H23: Fix throttleLatest double emission
28. H24: Fix LRUCache ConcurrentModificationException
29. H25: Make ResettableLazy reset() thread-safe
30. H26: Fix AndroidLogger blockUntilAllWritesFinished
31. H27: Rename getBooleanOrNull overload
32. H28: Fix SecurePreferences init thread-safety
33. H29: Fix KeyStoreManager test key leak
34. H30: Add retry limit to getOrCreateDatabaseKey

### Phase 4: Medium (Day 5-7)
35. M1: Cache consistency (depends on H13)
36. M2: Fix contains() TOCTOU race
37. M3: Remove unreliable shutdown hook
38. M4: Add R8 keep rules for CursorMapper
39. M5: Integrate or remove DatabaseMigrator
40. M6: Add GC-aware ANR detection
41. M7: Fix KeyedSerialExecutor shutdown blocking
42. M8: Fix or remove retry function
43. M9: Add awaitTermination to DeadlockDetector
44. M10: Switch config to JSON format
45. M11: Add fetch staleness check
46. M12: Add LiveRegion debounce
47. M13: Split accessibility methods
48. M14: Log FocusTraversal warnings
49. M15: Normalize emoji comparison
50. M16: Fix DatabaseNotifier buffer overflow

### Phase 5: Low + Missing Features (Day 7+)
51. L1-L12: All low severity fixes
52. Implement missing features from parity table above
