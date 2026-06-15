# Audit Report: `core:performance` Module

## Files Audited

| File | Lines | Purpose |
|------|-------|---------|
| `ImagePipeline.kt` | 82 | Image loading and caching via Coil |
| `MessageCache.kt` | 45 | In-memory message cache per conversation |
| `MessageTrimmer.kt` | 78 | Periodic WorkManager job to trim old DB messages |
| `PerformanceTracker.kt` | 34 | In-memory metrics collection |

---

## 1. Security

### Findings

**ImagePipeline — Cache Directory**

The disk cache is placed in `context.cacheDir.resolve("image_cache")`.  
`context.cacheDir` is world-readable on Android (permissions 771), meaning any app with root or `READ_EXTERNAL_STORAGE` permission can read cached images.

- **Issue**: No encryption on disk cache. Sensitive images if any are cached unencrypted.
- **No bounds on raw file count**: `DiskCache` has a 50 MB size limit, but no limit on number of files. A malicious URL pattern could exhaust `inodes` before hitting the byte limit.

**MessageTrimmer — SQL Injection in Worker Parameters**

```kotlin
val retentionDays = inputData.getLong("retentionDays", 365L)
```

`retentionDays` is passed directly into SQL without validation. While WorkManager enforces type-safety, a compromised `inputData` could pass unexpected values (e.g., negative values) leading to unintended DELETE behavior or integer overflow in `TimeUnit.DAYS.toMillis(retentionDays)` if negative (causing a huge cutoff, deleting almost everything).

**MessageCache — No Encryption**

In-memory cache holds message objects. If the app process is scraped, these are exposed. No wipe on app backgrounding.

---

## 2. Bugs

### ImagePipeline

**Double initialization race condition**  
`initialized` is `@Volatile`, but `init()` has no double-checked locking. Two concurrent calls can both see `initialized = false` and execute full initialization, though the second will re-set the `ImageLoader` to the same instance. Minor, but not idempotent-safe in the strictest sense.

**`loadImage` success handler is empty**  
```kotlin
onSuccess = { /* default target handles this */ }
```
The comment suggests the default `ImageView` target is used, but `onSuccess` overrides this with a no-op. This means Coil's default target-setting behavior is bypassed and the ImageView is never set on success. **This is a functional bug**: images loaded via `loadImage()` will never actually appear in the target `ImageView`.

**`prefetchImage` size mismatch**  
```kotlin
.size(Size.ORIGINAL)
```
Uses `Size.ORIGINAL` for prefetch but Coil's default strategy is `Size.ORIGINAL`. However `prefetchImage` disables memory cache but keeps disk cache enabled. If the same URL is prefetched repeatedly, disk cache will be hit every time (no ETag/304 handling visible). Could cause redundant I/O.

### MessageCache

**Race condition in `cacheMessages`**  
```kotlin
val conversationCache = cache.getOrPut(conversationId) { ... }
synchronized(conversationCache) {
    messages.forEach { message ->
        conversationCache[idExtractor(message)] = message
    }
}
```

`cache.getOrPut` is called outside the `synchronized` block, so two concurrent calls for the same `conversationId` could both create a new `LinkedHashMap` (because `getOrPut` is not atomic with respect to the outer cache map). The outer cache is a `Collections.synchronizedMap` but `getOrPut` still has a TOCTOU window. Only one will win in the map, but the other created map becomes eligible for GC. The inner `synchronized(conversationCache)` protects mutations but not the initial creation.

**`getCachedMessages` returns a copy, but only of the VALUES**  
```kotlin
return cache[conversationId]?.values?.toList()
```
The returned `List<T>` is a new list, but the `T` objects themselves are the same references stored in the map. Mutations to message objects after caching will be visible via `getCachedMessages`. This is likely unintended.

**LRU eviction not triggered on `getCachedMessages` order access**  
The outer `LinkedHashMap` uses access-order (`true` in constructor). `getCachedMessages` reads the inner map's values, but does not touch the outer map entry, so no access-order update occurs on the outer map. This means LRU eviction of conversation entries may not correctly reflect read access, only write access.

### MessageTrimmer

**Periodic work with `ExistingPeriodicWorkPolicy.KEEP` — no way to update schedule**  
If retention days need to change (e.g., user preference update), the work request won't be updated. Must use `REPLACE` policy or cancel+reschedule.

**No cleanup on app uninstall**  
The WorkManager periodic work continues indefinitely. If the app is uninstalled and reinstalled, the old work continues. No UNINSTALL receiver to cancel it.

---

## 3. Completeness

### ImagePipeline

**Missing `clearMemoryCache` context**: `Coil.imageLoader(context).memoryCache?.clear()` — the `memoryCache` property may be null if Coil is not fully initialized. No null safety check.

**Missing `clearDiskCache` null check**: same issue — `diskCache` can be null.

**No cache size reporting**: No API to query current memory/disk cache sizes for UI or diagnostics.

**No cache key customization**: Coil's default cache key is URL-based. No mention of custom keys for transformed/converted images.

### MessageCache

**No TTL/expiry**: Messages never expire based on time, only based on count. Long-idle conversations stay in memory indefinitely.

**No watermark-based trimming**: Only eviction on insert. No proactive trimming when memory pressure is high (no `trimToSize` or similar).

### MessageTrimmer

**No dry-run mode**: Cannot preview what would be deleted without actually deleting.

**No logging of deleted count**: Only logs errors, not the number of rows affected. Cannot audit cleanup effectiveness.

**No throttle/battery awareness beyond battery-not-low**: `Constraints` only checks battery not low. Does not check charging state or device idle for larger deletions.

---

## 4. Code Quality

### ImagePipeline

- Uses `object` singleton — fine for a pipeline, but no DI-friendly interface
- `Log.w` for image load failures — should be `Log.d` (verbose) as 404s on images are normal
- Hardcoded constants (50 MB, 25%) — should be configurable via `AppConfig`
- No tests for actual image loading/caching behavior (only constant validation)

### MessageCache

- Re-implements LRU logic with `LinkedHashMap` — already available in `core.base.LRUCache` which is a tested, thread-safe implementation. `MessageCache` should delegate to `LRUCache<String, LRUCache<String, T>>` rather than re-implementing.
- `synchronized(conversationCache)` — this lock is held for the entire batch insert; a large message list could cause lock contention for concurrent reads on the same conversation.

### MessageTrimmer

- Two identical DELETE SQL blocks — one in `MessageTrimmerWorker.doWork()` and one in `MessageTrimmer.trimOldMessages()`. Code duplication risk.
- Worker uses `db.execSQL` with string array but `arrayOf(cutoff.toString())` is a single-element array; works but fragile.
- `Result.retry()` on exception will reschedule work indefinitely if the DB is persistently unavailable — no backoff.

### PerformanceTracker

- Stores raw timestamps as `Long` in memory, unbounded (only trims to 1000 entries per metric). Could cause GC pressure if many metrics are tracked.
- `getAverage` uses `ConcurrentLinkedQueue.average()` which iterates all entries — O(n) for every call. Called frequently this is inefficient.
- No metric unregistration — metrics accumulate even if the trace name is never used again. `metrics` map grows unbounded.

---

## Summary Table

| Category | Severity | Count |
|----------|----------|-------|
| Security | HIGH | 2 |
| Bugs | HIGH | 4 |
| Completeness | MEDIUM | 5 |
| Code Quality | MEDIUM | 4 |

### Critical Issues

1. **`ImagePipeline.loadImage` success handler is a no-op** — images never display
2. **`MessageTrimmer` SQL parameters not validated** — potential for malformed DELETE
3. **`MessageCache` outer LRU eviction ignores reads** — conversation eviction order incorrect

### Recommended Actions

1. Fix `ImagePipeline.loadImage` onSuccess handler to actually set the target
2. Replace `inputData.getLong` with validated bounds check in `MessageTrimmerWorker`
3. Refactor `MessageCache` to extend `LRUCache` from `core.base`
4. Add null guards to `clearMemoryCache` and `clearDiskCache`
5. Replace `ExistingPeriodicWorkPolicy.KEEP` with `REPLACE` to allow schedule updates
6. Log deleted row count in `MessageTrimmer`
7. Add cache size reporting APIs to `ImagePipeline`
