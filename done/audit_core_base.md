# Audit Report: `core:base` Module

**Module**: `org.enchant.core.base`
**Path**: `core/base/src/main/java/org/enchant/core/base/`
**Files Audited**: 28 Kotlin files
**Date**: 2026-05-29

---

## 1. Security

### 1.1 Logging of Sensitive Data

**Findings**:

- `Scrubber.kt`: PII scrubber uses a per-install salt stored in `SecurePreferences`. Salt is generated via `SecureRandom` on first use and persisted. This is a solid approach.
- Scrubber patterns: phone numbers (`+?[0-9]{7,15}`), emails, UUIDs, IPv4 addresses, URLs. Patterns are reasonable.
- `Scrubber.scrub()` is applied via `Log` abstraction. There is no evidence that raw PII leaks into log output in the logging subsystem itself.
- **Concern**: `Scrubber.kt` line 28 — the `SALT` lazy property calls `SecurePreferences.getString("scrubber_salt")` at any point it is accessed (not just at init). If `SecurePreferences` has not been initialized yet, this call will return `null`, causing a new salt to be generated and stored. This means log scrubbing before `SecurePreferences.init()` produces inconsistent tokens (new salt stored, next call uses it). This is a minor consistency issue rather than a PII leak.

- **No evidence of plaintext secrets in logs** across the files examined.

### 1.2 Secure Random

**Findings**:

- `ByteArrayExtensions.zero()` (line 31-35): Zeros out sensitive byte arrays in-place. Uses simple `for` loop — appropriate.
- `Scrubber.kt` (line 33): Uses `java.security.SecureRandom().nextBytes(bytes)` for salt generation. Good.
- `KeyStoreManager.kt` (line 219): Uses `java.security.SecureRandom().nextBytes(it)` for database passphrase generation. Good.
- No `java.util.Random` usage found in crypto-relevant paths.

### 1.3 AppConfig Values

**Findings**:

- `AppConfig.kt`: Stores gateway URL, WebSocket URL, TURN credentials, JWT public key, app version, user agent.
- All mutable fields (`_gatewayUrl`, `_turnPassword`, etc.) are private with `@Volatile initialized` guard.
- Double-checked locking pattern correctly implemented (lines 64-66).
- **Issue**: `userAgent` is derived from `_appVersion` but `appVersion` is resolved via `context.packageManager.getPackageInfo()` which can throw. On failure, defaults to "1.0.0". User agent format is `"Enchant-Android/1.0.0"`. No personally identifiable info in user agent string.
- TURN credentials (`_turnUsername`, `_turnPassword`) stored as nullable strings — not logged in any code path observed.
- **Concern**: `AppConfig.init()` stores TURN credentials into SharedPreferences (line 72-74). The source of these is SharedPreferences itself via `"turn_url"`, `"turn_username"`, `"turn_password"` keys. If these prefs are not encrypted, TURN credentials are at rest in plaintext. `SecurePreferences` exists but `AppConfig` uses regular `SharedPreferences`. Recommend reviewing whether TURN credentials should be stored in `SecurePreferences` instead.

### 1.4 Crash Handling

**Findings**:

- `AnrDetector.kt`: Monitors main thread responsiveness. On detection, calls `onAnrDetected(stackTrace)`. Stack trace captured via `Looper.getMainLooper().thread.stackTrace`. No PII in the captured stack trace itself.
- `DeadlockDetector.kt`: Checks for blocked threads every `intervalMs`. Logs warning with thread names. No PII.
- Both detectors use `Log.w()` for warnings — consistent with the logging infrastructure.

### 1.5 Keystore Operations

**Findings**:

- `KeyStoreManager.kt`: Wraps Android KeyStore with hardware backing detection. Keys include `enchant_identity_key` and `enchant_db_key`.
- `encrypt()`/`decrypt()` use AES/GCM/NoPadding with 128-bit GCM tag. IV is prepended to ciphertext. Correct.
- `getOrCreateDatabaseKey()`: Generates 32-byte random key, encrypts it with AndroidKeyStore, stores wrapped key in `SecurePreferences`. Good pattern.
- `deleteKey()` catches `KeyStoreException` and logs warning — does not propagate. Could silently fail if key deletion is important for security (e.g., key rotation).
- `keyInfo()` returns `KeyStoreEntryInfo` with creation date — no sensitive data exposed.

---

## 2. Bugs

### 2.1 Concurrency Issues

**Findings**:

- `AppConfig.kt`: `@Volatile private var initialized` combined with `synchronized(lock)` is correct double-checked locking. No issues.
- `SecurePreferences.kt`: `@Volatile private var prefs: SharedPreferences?` with `synchronized(this)` on init. Correct.
- `KeyStoreManager.kt`: `@Volatile private var initialized`, `@Volatile private var _isHardwareBacked`. `init()` is a suspend function using double-checked pattern (lines 30-46). Correct.
- `CoroutineDispatchers.kt`: `setProvider()` is not thread-safe — no synchronization. If called concurrently from multiple coroutines, `provider` could be set to a different provider mid-read. The default provider is stable, but if tests or app code call `setProvider()` concurrently, behavior is undefined. This is a **low-severity concurrency bug**.
- `LRUCache.kt`: All operations are `@Synchronized` on the instance. The `LinkedHashMap` subclass overrides `removeEldestEntry` without synchronization. Since all access goes through the synchronized methods, this is safe.
- `ResettableLazy.kt`: Uses `@Volatile` and `synchronized` for thread-safe double-checked locking. Correct.

**Potential Issue**: `KeyStoreManager.init()` is a suspend function, but `_isHardwareBacked` is accessed without synchronization in `isHardwareBacked()` (line 233: `fun isHardwareBacked(): Boolean = _isHardwareBacked`). While reads of `@Volatile` fields are atomic, the initialization state (`initialized`) is not checked before reading `_isHardwareBacked`. If `init()` has not run, `_isHardwareBacked` defaults to `false` — this is a silent incorrect value rather than a crash. **Low severity** since the default is a conservative false (not hardware-backed).

### 2.2 Null Safety

**Findings**:

- `ByteArrayExtensions.kt`: `xor()` (line 64-66) uses `require(this.size == other.size)` — throws `IllegalArgumentException` rather than returning null. Acceptable for an internal utility.
- `Base64.kt`: `decodeOrNull()` (line 86-93) correctly returns `null` on `IOException`. All decode methods handle `IllegalArgumentException` by wrapping in `IOException` — consistent.
- `E164Util.kt`: Uses `try/catch` on `NumberParseException` for all parsing operations, returning `null` on failure. Good null-safe behavior.
- `SecurePreferences.kt`: `getPrefs()` returns nullable `SharedPreferences?`. All consumers check for null before use. Correct.
- `SqlUtil.kt`: `getContentValuesKeys()` (line 214-229) uses reflection fallback with exception handling. Returns `emptyList()` on failure — safe.
- `KeyedSerialExecutor.kt`: `execute()` (line 44-51) uses `computeIfAbsent` which can cause issues if the key is concurrently removed between `computeIfAbsent` returning and `queue.put`. However, the worker removes the queue from `queues` map only when empty, and only after checking `queue.isEmpty()`. The logic is subtle but appears safe.

### 2.3 Initialization Order

**Findings**:

- `EnchantExecutors.kt`: Object fields `UNBOUNDED`, `BOUNDED`, `SERIAL`, `BOUNDED_IO` are initialized at object creation time. Thread pool creation in static/object initialization is generally safe but means these executors are created when the class is first accessed. No circular dependencies observed.
- `CoroutineDispatchers.kt`: `DefaultDispatcherProvider` is initialized lazily at first access. Safe.

**Issue**: `SecurePreferences.init()` must be called before `Scrubber.SALT` is accessed, otherwise the salt will be stored under a null key and subsequent calls will regenerate a new salt. No defensive initialization check exists. Callers must ensure `SecurePreferences.init()` happens before any scrubbing. **Medium concern** — this is a hidden initialization order dependency.

### 2.4 Memory Leaks

**Findings**:

- `KeyedSerialExecutor.kt`: The `queues` map stores `LinkedBlockingQueue` references for keys even after all tasks complete. The cleanup logic (lines 72-76) only removes the queue when the worker loop exits after detecting `queue.isEmpty()` and successfully removing the key from the map. If `poll()` returns null and the queue is not empty, the worker loop continues but the queue remains in the map. This is correct.
- `LRUCache.kt`: Uses `LinkedHashMap` with `removeEldestEntry` — properly evicts entries. No leaks.
- `AnrDetector.kt`: `handler.removeCallbacks(heartbeat)` in `stop()` prevents leak of pending callbacks. Correct.
- `DeadlockDetector.kt`: `executor.shutdown()` followed by `awaitTermination` in `stop()`. Properly cleaned up.

---

## 3. Completeness

### 3.1 App Initialization Coverage

**Findings**:

- `AppConfig.init(context)` — covers gateway URL, WebSocket URL, TURN credentials, JWT public key, app version, user agent. **Missing**: no build-specific configuration (BuildConfig fields), no environment indicator (dev/staging/prod), no feature flag loading.
- `SecurePreferences.init(context)` — encrypted preferences initialization. Covers secrets storage.
- `KeyStoreManager.init(context)` — hardware keystore detection and key setup. Covers crypto key management.
- `Log.initialize(logger)` — logging initialization. **Not covered by any init function** — caller must manually initialize. No `AppInitializer` or bootstrap mechanism in this module.
- `CoroutineDispatchers.setProvider()` — dispatcher customization. Optional, defaults are sane.

**Gap**: No single `BaseModule.init()` that coordinates all initialization steps in correct order. Callers must know the correct sequence:
1. `SecurePreferences.init(context)`
2. `Log.initialize(logger)` (requires `Scrubber` which needs `SecurePreferences`)
3. `AppConfig.init(context)`
4. `KeyStoreManager.init(context)` (suspend)

No bootstrap coordinator exists in this module.

### 3.2 Crypto Helpers

**Findings**:

- `Base64`: encode/decode, standard and URL-safe, with/without padding. **Complete**.
- `Hex`: encode/decode uppercase and lowercase, hexdump. **Complete**.
- `ByteArrayExtensions`: `toHexString()`, `toBase64()`, `sha256()`, `constantTimeEquals()`, `xor()`, `zero()`. **Complete** — includes constant-time comparison for timing attack prevention.
- `KeyStoreManager`: key generation, encryption, decryption, signing, verification. **Complete**.
- `SecureRandom` usage confirmed for salt and key generation.

### 3.3 Missing Considerations

- No `SecureRandom` wrapper for generating secure tokens — callers use `java.security.SecureRandom` directly.
- No HKDF or PBKDF2 utilities — `KeyStoreManager` handles key wrapping but no password-based key derivation.
- No constant-time byte comparison at the utility level beyond `MessageDigest.isEqual` (which is used in `ByteArrayExtensions.constantTimeEquals`).

---

## 4. Code Quality

### 4.1 Utility Classes Organization

**Findings**:

- `Base64`, `Hex`, `E164Util`, `UuidUtil`, `SqlUtil` — all as `object` singletons. Consistent.
- `ByteArrayExtensions`, `StringExtensions`, `FlowExtensions` — extension functions on specific types. Good separation.
- `Result` — sealed class with `Success`/`Failure`. Clean algebraic type.
- `LRUCache`, `ResettableLazy`, `Stopwatch` — concrete classes with single responsibility. Good.
- `Stopwatch` includes both a class and an inline function `logTime()` — good convenience wrapper.

### 4.2 Constants Organization

**Findings**:

- Constants embedded in classes (e.g., `MAX_QUERY_ARGS = 999` in `SqlUtil`). Acceptable.
- No central constants object. No `Constants.kt` file.
- Thread priorities defined as `private const` in `EnchantExecutors`. Good encapsulation.

### 4.3 Logging

**Findings**:

- `AndroidLogger` uses a single-threaded executor to serialize log writes. Good.
- `NoopLogger` as default before init — safe fallback.
- `CompoundLogger` for multi-destination logging — clean adapter pattern.
- `Scrubber` integrates at the `Log` level — not automatically applied to all messages; must be manually used.
- `Log.flush()` and `Log.blockUntilAllWritesFinished()` for crash handler integration — good.

### 4.4 Stream Utilities

**Findings**:

- `LimitedInputStream` — security-critical bounds checking on reads. Well-documented.
- `StreamUtil.readFully()` — exact-length reads, OOM prevention. Good.
- `NonClosingOutputStream` — prevents accidental data loss on close. Good pattern.
- No ZIP/JAR stream utilities — out of scope.

### 4.5 Concerns

- `SqlUtil.buildTrueUpdateQuery()` (line 176): The `contentValues.get(key)` returns `Any?`. The `when` clause checks `value is ByteArray` (line 192) and falls through to `toString()` otherwise. If `ContentValues` contains a non-String, non-ByteArray value (e.g., a `Long`), `toString()` is called which is safe but produces a decimal string representation. SQLite will bind this as a string parameter. Acceptable.
- `SqlUtil.getContentValuesKeys()` uses reflection on API < 28 to access `mValues` field. The field name `"mValues"` is a known internal name but could theoretically change in future Android versions. Low risk since it has a fallback and logs warning.
- `KeyedSerialExecutor.createWorker()` (line 60-82): The worker `Runnable` captures the `key` and `queue`. If the executor is shut down, the worker loop exits via `InterruptedException`. On interruption, `Thread.currentThread().interrupt()` is called — correct interrupt preservation.
- `EnchantExecutors.newCachedBoundedExecutor()` (line 60): The `LinkedBlockingQueue` subclass overrides `offer()` to return `false` when the queue is non-empty. This creates a "only offer if empty" behavior — effectively a stack for the first item and blocks subsequent offers when full. This is intentional (the rejection handler then `put()`s which blocks). Behavior is correct but non-obvious.

---

## Summary

| Category | Severity | Count |
|----------|----------|-------|
| Security: High | 0 | |
| Security: Medium | 1 (TURN credentials in regular SharedPreferences) | 1 |
| Security: Low | 1 (Scrubber initialization order) | 1 |
| Bug: High | 0 | |
| Bug: Medium | 1 (CoroutineDispatchers.setProvider not thread-safe) | 1 |
| Bug: Low | 2 (KeyStoreManager.isHardwareBacked race, Scrubber salt init order) | 2 |
| Completeness: Gap | 1 (No bootstrap coordinator) | 1 |
| Code Quality | 0 (minor notes only) | 0 |

**Critical Issues**: None.
**High Priority**: None.
**Medium Priority**: `CoroutineDispatchers.setProvider()` should be made thread-safe.
**Low Priority**: TURN credentials storage; Scrubber/SecurePreferences initialization order documentation.

