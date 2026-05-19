# core:base — Pending Items

## Tests Requiring `sqlite-framework` Dependency

The following SqlUtil tests require a real `SupportSQLiteDatabase` instance via `androidx.sqlite:sqlite-framework`. Add the dependency to `core/base/build.gradle.kts` and uncomment the tests in `SqlUtilTest.kt`.

**File:** `core/base/src/test/java/org/enchant/core/base/SqlUtilTest.kt`

Test methods to enable:
- `tableExists returns true for existing table`
- `tableExists returns false for non-existing table`
- `columnExists returns true for existing column`
- `columnExists returns false for missing column`
- `isEmpty returns true for empty table`
- `getAllTables returns created tables`

## `buildTrueUpdateQuery` — Pending Fix

**File:** `core/base/src/main/java/org/enchant/core/base/SqlUtil.kt`

The method `buildTrueUpdateQuery` uses `ContentValues.valueSet()` to iterate over column keys. Robolectric returns `null` for `valueSet()` on some API levels (added in API 28). The current fallback returns a plain query without the true-update comparison.

Fix: enumerate ContentValues keys differently or accept a `Map<String, Any?>` parameter.

## `buildBulkInsert` ByteArray Test — Pending

**File:** `core/base/src/test/java/org/enchant/core/base/SqlUtilTest.kt`

The test `buildBulkInsert handles ByteArray values inline` is commented out because Robolectric's `ContentValues.put(String, ByteArray)` doesn't return the array as `ByteArray` in Kotlin's `is` check correctly. Uncomment when Robolectric handles this.

---

## OPK Bug Analysis

**Files checked:**
- `core/crypto/src/main/java/org/enchant/core/crypto/KeyManager.kt`
- `core/crypto/src/test/java/org/enchant/core/crypto/KeyManagerTest.kt`

### Bug #1: SPK/OPK Encoding — FIXED

Tests `C02/C03/L17` verify that SPK and OPK private keys are stored as base64, not comma-separated. The implementation at `KeyManager.kt:295-297` uses `CryptoHelper.base64UrlEncode()` for all key storage. This is correct.

### Bug #2: OPK Replace in `topUpOpks` — STILL OPEN

**Location:** `KeyManager.kt:262`

```kotlin
suspend fun topUpOpks() {
    ...
    if (remaining < 10) {
        val opks = generateOpks(100)
        val uploadResult = uploadOpks(client, opks)
        if (uploadResult.isSuccess) {
            storeOpksLocally(opks)  // <-- REPLACES all local OPKs
        }
    }
}
```

**Problem:** When `topUpOpks()` uploads 100 new OPKs, `storeOpksLocally()` writes all 100 starting at index 0, overwriting any unconsumed OPKs from the previous batch. The server still has the old unconsumed OPKs registered. If the server distributes one of those old OPKs in a message, the client can't decrypt it because the private key was overwritten.

**Scenario:**
1. Client uploads 100 OPKs (indexes 0-99), server stores them
2. Server distributes 95 OPKs, 5 remain unconsumed on server
3. Client calls `topUpOpks()` because server reports `remaining < 10`
4. Client uploads 100 new OPKs, calls `storeOpksLocally(100)` → overwrites indexes 0-99
5. Server now has 105 OPKs (5 old + 100 new)
6. Server distributes one of the 5 old OPKs — client can't decrypt it

**Fix needed:** `storeOpksLocally` should either:
- Append new OPKs at the end of existing ones (e.g., start at index `currentCount`), or
- Have the server clear old OPKs before uploading new ones (add a `PUT`/`DELETE` endpoint call)
