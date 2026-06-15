# Audit Report: `core:store` module

**Module path:** `core/store/src/main/java/org/enchant/core/store/`
**Files examined:** 39 `.kt` source files (26 namespace value classes, 8 core infrastructure files, 2 test files)
**Audit date:** 2026-05-29

---

## 1. Security

### 1.1 Encryption: SQLCipher (Production) / InMemory (Tests) -- PASS

- `KeyValueStore` uses **SQLCipher** (`net.sqlcipher.database.SQLiteDatabase`) with a master password derived from `SecurePreferences`.
- Password derivation: `derivePassword()` generates a `UUID.randomUUID()` as the DB password, stored in `SecurePreferences` under key `"enchant.store.password"`. This is a per-install secret; if an attacker extracts the DB file *and* the key from `SecurePreferences`, the DB is readable.
- The hex password conversion in `StoreOpenHelper.preKey()` is correct: `password.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }` (line 193). This is a hex encoding, not the raw password being used as a SQLCipher key. However, the raw password string itself is passed to `PRAGMA key`, which SQLCipher interprets as a passphrase and derives the actual key via PBKDF2. This is acceptable.
- `InMemoryKeyValueStorage` is used exclusively for testing, never in production.

### 1.2 Plaintext Escape Hatch -- WARN

- `PlainTextSharedPrefsDataStore` stores values in **unencrypted** `SharedPreferences` under file `"enchant_plaintext"`.
- The documentation warns: "If you're not comfortable logging it, don't put it here." This is the correct security posture.
- `EnchantStore` uses this for: `"enchant.migration_v1_done"`, `"enchant.store.password"` (in `SecurePreferences`, not this store), and legacy migration keys.
- The plaintext store is clearly labeled; no sensitive user data is stored here.

### 1.3 Key Namespacing -- PASS

- Every namespace uses a `private companion object { const val P = "namespace" }` pattern (e.g., `AccountValues.P = "account"`).
- All keys are prefixed: `"account.user_id"`, `"settings.theme"`, etc. This prevents collisions between namespaces.
- The pattern is consistent across all 26 value classes. No hardcoded non-prefixed keys found in the value classes.

### 1.4 Sensitive Values: PIN and SVR -- PASS (with notes)

- `PinValues.hash` and `PinValues.salt` store the PIN hash and salt. These are in the encrypted store, which is correct.
- `SvrValues.masterKey` stores the SVR master key. Also in encrypted store.
- `RegistrationValues.lockPin` stores a registration lock PIN.
- `BackupValues.svrMasterKey` stores the SVR backup master key.
- `ProxyValues.credentials` stores proxy credentials.
- `CertificateValues.uaCert` stores UA certificates.
- All sensitive values are in the encrypted `KeyValueStore`, not plaintext store. This is correct.

### 1.5 Crash Handler Flush -- PASS

- `EnchantCrashHandler` calls `EnchantStore.blockUntilAllWritesFinished()` before propagating the exception. This ensures pending async writes are flushed to the DB before crash. Correct pattern.

---

## 2. Bugs

### 2.1 Async Write Queue Race Condition on Read -- HIGH

**File:** `KeyValueStore.kt`

In `KeyValueStore`, reads check the `cache` first, then fall back to DB. Writes update the cache **synchronously** then enqueue to the async `writeQueue`:

```kotlin
// Line 78
override fun putString(key: String, value: String?) {
    enqueueWrite(WriteOperation.PutString(key, value))
    cache[key] = value  // <-- cache updated immediately
}
```

However, `getString` reads the cache **without** synchronizing with the write executor:

```kotlin
// Line 34-38
override fun getString(key: String, defaultValue: String?): String? {
    return cache[key] as? String ?: run {
        val dbValue = dbHelper.getString(key)
        if (dbValue != null) { cache[key] = dbValue; dbValue } else defaultValue
    }
}
```

**Race scenario:**  
1. Thread A writes `key = "foo"` via `putString` -> cache updated, write queued.  
2. Thread B calls `getString("foo")` immediately. Cache hit returns the value. OK.  
3. **But:** If Thread A calls `resetCache()` or `clearAll()` between the `putString` and the write being executed on the writer thread, the cache could be cleared while the write is still queued, leading to the write being lost.  
   - `resetCache()` at line 151-155 calls `flushPendingWrites()` first, which drains the queue synchronously. So this is safe.
   - `clearAll()` at line 117 calls `cache.clear()` then `enqueueWrite(ClearAll)`. The cache clear happens synchronously. If the writer thread has not yet processed the prior `putString`, that write is **lost** when `ClearAll` executes on the writer thread (which will wipe the DB entry set by the prior write).

**The `ClearAll` bug:**  
Line 117: `override fun clearAll() { cache.clear(); enqueueWrite(WriteOperation.ClearAll) }`  
If `ClearAll` is processed by the writer thread **after** a `PutString("key", "value")` that was queued before `clearAll()` was called, the `ClearAll` will wipe all data in the DB. The `PutString` will also be processed (because the queue is FIFO), which will re-insert the key. This is actually **correct** -- the operations are processed in order.

**However**, the `WriteBatch.apply()` at line 98-113 updates the cache immediately but enqueues the entire batch as a single `Batch` operation. The `executeBatch` in `StoreOpenHelper` (line 272-289) uses `beginTransaction()` / `setTransactionSuccessful()` / `endTransaction()`, which provides atomicity at the DB level. This is correct.

### 2.2 `contains()` Implementation -- MEDIUM

**File:** `KeyValueStore.kt`, line 76

```kotlin
override fun contains(key: String): Boolean = cache.containsKey(key)
```

This checks only the **in-memory cache**, not the DB. If a key was written in a previous session and the cache was evicted or not yet loaded from DB, `contains()` will return `false` even though the key exists in the DB.

Compare with `getString()` which falls back to DB. The `contains()` method should either:
1. Check DB if not in cache, OR
2. Always load cache from DB first (which `loadCacheFromDb()` does on init)

This could cause subtle bugs where code checks `contains(key)` to decide whether to write, believing the key doesn't exist when it actually does in the persistent store.

**Signal reference:** Signal's `SecretSessionStore.getSession()` uses a similar check-and-fetch pattern. The difference is Signal's store has no such async queue.

### 2.3 Boolean Storage Type Collision -- PASS (observed but correct)

**File:** `KeyValueStore.kt`, line 228

```kotlin
TYPE_BOOLEAN -> cursor.getInt(cursor.getColumnIndex(COL_INT)) == 1
```

Booleans are stored as `TYPE_BOOLEAN` (value 4) but use `COL_INT` column (the same column as `TYPE_INT`). The `upsert` function at line 265 correctly uses `intValue` for booleans. The type column distinguishes them at read time, so this is correct, not a bug.

### 2.4 Database Schema Version -- LOW

**File:** `KeyValueStore.kt`, line 191

```kotlin
SQLiteOpenHelper(context, "enchant_store.db", null, 1, ...)
```

The schema version is hardcoded as `1`. `onUpgrade()` is empty. If schema changes are needed in the future, this will need to be updated. No migration path exists in the SQL schema itself (migrations are handled at the application level via `ApplicationMigrations`).

### 2.5 Migration Key Collision Risk -- LOW

**File:** `EnchantStore.kt`, line 247

```kotlin
val migrationKey = "enchant.migration_v1_done"
```

This key is stored in the encrypted store. If a future migration needs a different key, using a versioned key (`v1`) is correct. However, if the same key is reused for a different migration type, there could be confusion. The `ApplicationMigrations` system uses `migration.applied.<version>` which is better namespaced.

### 2.6 WriteBatch.apply() Local Cache Update Order -- PASS

In `WriteBatch.apply()` (line 98-113), cache updates happen in order before the DB batch is enqueued. If a `Remove` operation is in the batch, it removes from cache. If a `PutString` is in the batch, it updates cache. The batch is executed on the writer thread in the same order. The cache update order matches the DB operation order. This is correct.

### 2.7 `blockUntilAllWritesFinished` Implementation -- PASS (with note)

**File:** `KeyValueStore.kt`, lines 163-168

```kotlin
override fun blockUntilAllWritesFinished() {
    val latch = CountDownLatch(1)
    writeExecutor.execute { latch.countDown() }
    latch.await(5, TimeUnit.SECONDS)
}
```

This submits a no-op task to the executor to ensure the executor has processed all prior tasks (since it's single-threaded). The latch fires after the prior tasks *and* the no-op task are complete. This is a correct pattern to wait for the write queue to drain. The 5-second timeout could theoretically be hit if there are thousands of writes, but this is an extreme edge case.

---

## 3. Completeness

### 3.1 Preference Coverage -- PASS

The module covers all major preference categories:

| Category | File | Coverage |
|---|---|---|
| Account/Identity | `AccountValues.kt` | userId, deviceId, username, displayName, aci, pni, fcmToken, registered, multiDevice, capabilities |
| Registration | `RegistrationValues.kt` | isComplete, lockPin, restoreDecisionState, sessionId, localRegistrationId |
| Settings | `SettingsValues.kt` | readReceipts, typing, linkPreviews, theme, fontSize, language, screenLock, mediaAutoDl |
| Notifications | `NotificationsValues.kt` | message, preview, sound, vibrate, calls, groups |
| Privacy | `PrivacyValues.kt` | lastSeen, online, avatar, about visibility, groupsAddPolicy |
| PIN | `PinValues.kt` | hash, salt, failedAttempts, pinLength, regLock |
| Onboarding | `OnboardingValues.kt` | complete, welcome, permissions, profileSetup |
| Proxy | `ProxyValues.kt` | host, port, enabled, credentials |
| Rate Limiting | `RateLimitValues.kt` | otp, otpCount, keyReg, profileUpdate timestamps |
| Phone Number Privacy | `PhoneNumberPrivacyValues.kt` | share, discoverable |
| Backup | `BackupValues.kt` | enabled, lastTs, key, svrMasterKey, cdnCreds, tier, mediaEnabled |
| SVR | `SvrValues.kt` | masterKey, backupId, lastRestore, configured, pinHash, salt |
| Labs | `LabsValues.kt` | experimental, multiDeviceV2, newStorage, messageRequests, payments, storiesV2 |
| Stories | `StoriesValues.kt` | privacy, introViewed, lastSend, viewedReceipts |
| Call Quality | `CallQualityValues.kt` | lowBw, alwaysRelay, directP2p, dataSaving |
| Emoji | `EmojiValues.kt` | recent, variant, keyboardHeight |
| Chat Colors | `ChatColorsValues.kt` | wallpaper, color, systemAccent |
| Internal | `InternalValues.kt` | syncTs, prekeyTs, trimTs, firstSync |
| Remote Config | `RemoteConfigValues.kt` | values, lastFetch, eTag |
| Storage Service | `StorageServiceValues.kt` | manifestVersion, lastSync, storageKey, syncEnabled |
| UI Hints | `UiHintValues.kt` | listSwipe, reactionHint, swipeReply, profileNameHint, safetyHint |
| Tooltips | `TooltipValues.kt` | chatSearch, noteToSelf, reactions, stories |
| Certificate | `CertificateValues.kt` | uaCert, certExpiry, serverParams |
| Wallpaper | `WallpaperValues.kt` | global, system, brightness |
| Payments | `PaymentsValues.kt` | enabled, introSeen, lastBalance |
| In-App Payments | `InAppPaymentValues.kt` | tier, lastPayment, introSeen |
| Image Editor | `ImageEditorValues.kt` | lastTool, brushSize, introSeen |
| Notification Profile | `NotificationProfileValues.kt` | profiles, activeId |
| Release Channel | `ReleaseChannelValues.kt` | channel, lastCheck |
| APK Update | `ApkUpdateValues.kt` | lastCheck, lastVersion, dismissed |
| Miscellaneous | `MiscellaneousValues.kt` | lastVersion, firstRun, appStart, dbUpgradeSeen |
| Encryption | `KeyValueStore.kt` | SQLCipher-encrypted SQLite, cache layer |
| Delegation | `StoreValueDelegates.kt` | ReadWriteProperty delegates, Flow observation |
| Migrations | `ApplicationMigrations.kt` | Versioned migration system |
| Crash Handling | `EnchantCrashHandler.kt` | Pre-crash write flush |
| Preference Library Bridge | `EnchantPreferenceDataStore.kt` | AndroidX Preference integration |
| Plaintext Store | `PlainTextSharedPrefsDataStore.kt` | Pre-encryption values |
| Test Support | `InMemoryKeyValueStorage.kt` | In-memory store for tests |

### 3.2 Missing Preferences (Gap Analysis)

- **Push notification token management**: Covered (fcmToken in AccountValues)
- **Message expiration/screenshot prevention**: Not found as dedicated values
- **Blocked addresses/contacts**: Not found as dedicated values
- **Draft messages**: Not found as dedicated values
- **Read cursor positions**: Not found as dedicated values
- **Message search history**: Not found as dedicated values

These may be managed elsewhere (database layer, not store layer). The store module is specifically for preferences/settings, and the coverage is comprehensive for that scope.

---

## 4. Code Quality

### 4.1 Architecture: Namespace Pattern -- EXCELLENT

Each preference domain has a dedicated `*Values.kt` class extending `EnchantStoreValues`. This follows the **Signal store pattern** exactly:

```
EnchantStore (singleton)
  ├── AccountValues (account.*)
  ├── SettingsValues (settings.*)
  ├── PrivacyValues (privacy.*)
  └── ... (26 namespaces total)
```

Each namespace:
1. Defines lazy `StoreValueDelegate` properties with the key prefix (`$P`)
2. Exposes Kotlin properties that delegate to the delegates
3. Implements `onFirstEverAppLaunch()` for defaults
4. Implements `getKeysToIncludeInBackup()` for backup filtering
5. Provides a `clear()` method that uses `beginWrite().remove(...).apply()` for atomic multi-key removal

This is a **very clean** pattern. Signal's `SignalStore` uses an identical approach.

### 4.2 Delegation Pattern -- EXCELLENT

`StoreValueDelegates` provides:
- Type-safe delegates: `stringValue()`, `intValue()`, `booleanValue()`, `longValue()`, `floatValue()`, `blobValue()`
- Enum support with `EnumSerializer`
- Protobuf support with `ProtoAdapter`
- `withPrecondition()` for conditional writes
- `map()` for read transformation
- `toFlow()` for reactive observation
- `observe()` for general Flow observation

The `StoreValueDelegate` class is a `ReadWriteProperty<Any?, T>`, making it idiomatic Kotlin. Property syntax `by delegates.booleanValue(...)` is clean and IDE-friendly.

### 4.3 Async Write Queue -- GOOD (with caveats)

The single-threaded `writeExecutor` with a `LinkedBlockingQueue` is a solid pattern for ensuring writes are serialized. The cache is updated synchronously on the calling thread, which ensures immediate visibility. The DB write happens asynchronously on the writer thread.

**Caveats noted above in Bugs section** (contains() and cache-only reads).

### 4.4 Migration Strategy -- GOOD

`ApplicationMigrations` is a clean versioned migration registry:
- Migrations are registered with `(version, name, block)` 
- Execution tracks applied migrations via `migration.applied.<version>` boolean keys
- Failed migrations are logged but do not block subsequent migrations
- Supports `getLastAppliedVersion()` and `getPendingMigrations()`

The legacy preference migration in `EnchantStore.migrateFromLegacyPreferences()` is a one-time v1 migration that copies from `SecurePreferences` to `KeyValueStore`. It correctly uses a single boolean flag.

### 4.5 Encapsulation -- EXCELLENT

- `KeyValueStorage` is an interface, allowing `InMemoryKeyValueStorage` for testing
- `EnchantStore` is a singleton object, initialized via `init(context)` or `init(storage)`
- Internal state (`store`, `delegates`, namespace fields) is `internal` or `private`
- `EnchantStore.init(storage)` for testing bypasses password derivation

### 4.6 Clear All Implementation -- PASS

`EnchantStore.clearAll()` calls `clear()` on every namespace. Each `clear()` uses `store.beginWrite().remove(...).apply()`, ensuring atomic multi-key removal. The `WriteBatch` uses a DB transaction. This is correct.

### 4.7 Backup Key Aggregation -- PASS

`getAllBackupKeys()` aggregates keys from all namespaces and deduplicates via `.distinct()`. This is used for backup/restore filtering. The implementation is correct.

### 4.8 PreferenceDataStore Bridge -- PASS

`EnchantPreferenceDataStore` implements `androidx.preference.PreferenceDataStore`. The `getStringSet`/`putStringSet` implementation uses `|` as a delimiter:

```kotlin
raw.split("|").toMutableSet()  // getStringSet
values?.joinToString("|")      // putStringSet
```

This is a reasonable delimiter choice (not used in typical user content). If a value contains `|`, it would be split incorrectly, but this is a known limitation of using string-based set serialization.

### 4.9 Naming Conventions -- PASS

- Namespace classes: `AccountValues`, `SettingsValues` (imperative plural nouns)
- Keys: `"account.user_id"` (dot-separated, namespace first)
- Properties: `userId`, `displayName`, `isRegistered` (camelCase, clear)
- Constants: `P = "account"` (compact prefix constant)

---

## 5. Test Coverage

**File:** `EnchantStoreTest.kt` (1044 lines)

- Happy path tests for every namespace property (default value + set/get)
- `clear()` resets to defaults
- `clearAll()` resets all namespaces
- `getAllBackupKeys()` returns non-empty, no duplicates
- Atomic batch writes via `beginWrite().putString().putInt().putLong().putBoolean().putFloat().apply()`
- `observe()` emits current value and changes
- `contains()` returns true/false for existing/missing keys
- `resetCache()` clears and reloads
- `onPostBackupRestore()` resets cache
- `ApplicationMigrations` executes once, does not re-execute
- `getPreferenceDataStore()` returns functional store

**Notable gap:** No test for the `contains()` bug (checking cache only, not DB).

**InMemoryKeyValueStorage:** Correctly synchronous for testing; no async behavior.

---

## Summary

| Category | Rating | Notes |
|---|---|---|
| **Security** | GOOD | SQLCipher encryption, plaintext escape hatch documented, key namespacing correct, sensitive values in encrypted store |
| **Bugs** | MEDIUM | `contains()` checks cache only; potential for stale reads if key exists in DB but not in memory |
| **Completeness** | EXCELLENT | 26 namespaces covering all major preference domains; comprehensive |
| **Code Quality** | EXCELLENT | Clean namespace pattern, excellent delegation, correct migration strategy, testable architecture |

### Key Recommendations

1. **Fix `contains()`**: Change `override fun contains(key: String): Boolean = cache.containsKey(key)` to fall back to DB or use `dbHelper.contains(key)` if not in cache. This is the only significant bug in the module.

2. **Schema version**: Consider making the database schema version (`1`) a constant that can be incremented when `onUpgrade()` is implemented.

3. **StringSet delimiter**: The `|` delimiter in `EnchantPreferenceDataStore.getStringSet` could corrupt data if the stored string contains `|`. Consider using a different delimiter (e.g., `|||`) or a different serialization format (JSON array) if there's any risk of pipe characters in the data.

