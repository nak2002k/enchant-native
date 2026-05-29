# feature:contacts Audit

## Security Issues

1. **Plaintext phone number in sync response** (`ContactSyncService.kt` line 53)
   - `MatchedContact` contains `phoneNumber` field populated from server response JSON
   - Server returns `"phone_number"` directly, exposing raw phone number in client memory
   - This violates phone number confidentiality even if transmission is hashed
   - Fix: Remove `phoneNumber` from `MatchedContact` or only store the hash

2. **No contact deduplication on sync** (`ContactSyncService.kt` line 100)
   - `contacts.distinctBy { it.normalizedE164 }` runs AFTER all contacts are loaded into memory
   - If duplicate numbers exist, all duplicates are temporarily held in memory before deduplication
   - Fix: Use SQL DISTINCT or deduplicate at cursor level during query

3. **Hardcoded singleton in ViewModel** (`ContactsViewModel.kt` line 28)
   - `ContactsViewModel() : this(ContactsRepository(...getInstance()..., ...instance!!...))`
   - Uses double-bang operator `!!` which will crash if pool is uninitialized
   - No lazy initialization or null-safety
   - Fix: Inject via Hilt/Koin or use lazy initialization with proper null handling

## Bugs

1. **Inconsistent JSON key names between repository and sync service**
   - `ContactsRepository.matchPhoneContacts()` (line 233) sends `"phone_hashes"`
   - `ContactSyncService.syncContacts()` (line 41) sends `"hashes"`
   - Server API expects one format; sync will fail silently
   - Fix: Normalize to single key name matching backend API spec

2. **Duplicate deletion on every getContacts()** (`ContactsRepository.kt` line 84)
   - `db.execSQL("DELETE FROM recipients")` wipes ALL recipients before inserting
   - If network fails after DELETE but before insert, contacts are lost with no recovery
   - Fix: Use transaction or upsert pattern (INSERT OR REPLACE with proper WHERE)

3. **Block/unblock returns `ContactResult.Added`/`Removed` instead of `Blocked`/`Unblocked`** (`ContactsRepository.kt` lines 181, 200)
   - `blockUser` returns `ContactResult.Added(true)` but the action is "block"
   - This misleads callers about what happened
   - Fix: Use distinct sealed class members for block/unblock

4. **Silent SecurityException swallow** (`ContactSyncService.kt` line 96)
   - `catch (_: SecurityException)` hides permission denial from caller
   - No indication to user that contacts cannot be read
   - Fix: Return specific error result or log with visibility control

5. **No pagination on device contact read** (`ContactSyncService.kt` lines 73-101)
   - All contacts loaded into memory at once
   - Devices with thousands of contacts will OOM or block UI
   - Fix: Use LIMIT/OFFSET cursor pagination

## Completeness Gaps

1. **No phone hash salt/processing** (`ContactSyncService.kt` line 68-71)
   - Uses raw SHA-256 of E.164 number
   - Lacks client-side salt or key derivation (e.g., HKDF)
   - Weak against rainbow table attacks on phone numbers
   - Fix: Use salted hash or Argon2id as per Signal approach

2. **Phone hash matching limited to 1000 hashes** (`ContactsRepository.kt` line 230)
   - `if (phoneHashes.isEmpty() || phoneHashes.size > 1000) return@withContext emptyList()`
   - No batch splitting for large contact lists
   - Fix: Chunk into batches of 1000 and aggregate results

3. **No contact discovery interval/delta sync**
   - Every sync fetches all contacts from server
   - Fix: Track `lastSyncTs` and use delta sync if supported by server

4. **No permission request handling in UI layer**
   - `ContactSyncService` catches `SecurityException` but caller `syncContacts()` cannot distinguish "permission denied" from "network error"
   - Fix: Return typed error or use sealed class for sync results

5. **Contact blocking not reflected in local cache immediately** (`ContactsRepository.kt` lines 172-189)
   - `blockUser` updates local DB but `blockedUsers` list is only reloaded via `loadBlockedUsers()`
   - ViewModel does not update `contacts` list with `isBlocked` status
   - Fix: Update in-memory state or invalidate contact cache on block

6. **No reverse contact search (finding who has me by phone hash)**
   - Only checks contacts user has added
   - No "discover contacts who have me" endpoint consumed

## Code Quality Issues

1. **Repository pattern broken: direct DB writes in getContacts** (`ContactsRepository.kt` line 83-91)
   - `getContacts()` has side effect of deleting all recipients (write) then reading server
   - Should be separate: sync (write) vs get (read from cache or server)
   - Fix: Split into `syncContacts()` and `getContacts()` with clear cache vs network semantics

2. **Inefficient callbackFlow in getCachedContacts** (`ContactsRepository.kt` line 111-121)
   - Creates new database query on every collection
   - No caching layer between DB reads
   - Fix: Use Room with Flow for reactive queries

3. **ViewModel has no SavedStateHandle**
   - Rotation or process death will lose `searchQuery` and other UI state
   - Fix: Add SavedStateHandle with rememberSaveable

4. **No error differentiation in sync result**
   - `Result.failure(e)` loses error type (network vs permission vs server)
   - UI cannot show appropriate message
   - Fix: Use sealed class like `SyncResult` with specific error types

5. **FriendRequestsScreen creates ApiClient in composable** (line 30)
   - `remember { ApiClient() }` creates new instance each recomposition
   - Should use dependency injection
   - Fix: Pass ApiClient as parameter or use Hilt injection

6. **Test coverage gaps**
   - `ContactsRepository` has no unit tests (only ViewModel tested)
   - `ContactSyncService` has no tests
   - No edge case tests: null phone numbers, empty lists, malformed JSON
   - Fix: Add repository and service tests

## Recommendations (prioritized)

1. **Critical: Fix JSON key mismatch** (`ContactSyncService.kt` line 41)
   - Change `"hashes"` to `"phone_hashes"` to match `ContactsRepository`
   - This will make phone contact discovery actually work

2. **Critical: Remove phone number from MatchedContact** (`ContactSyncService.kt` line 53)
   - Never store plaintext phone in memory after sync
   - Exposing via data class even if not persisted

3. **High: Fix getContacts() to use transactions**
   - Wrap DELETE + INSERT in single transaction
   - Prevents data loss on network failure

4. **High: Add proper permission flow**
   - Distinguish READ_CONTACTS permission denied from other errors
   - Show user-friendly prompt to grant permission

5. **High: Add phone hash salt**
   - Use HKDF-SHA256 with app-specific salt before sending hashes
   - Prevents correlation attacks on phone number hashes

6. **Medium: Add pagination to readDeviceContacts**
   - Use cursor with LIMIT for large contact lists
   - Prevents OOM on high-contact-count devices

7. **Medium: Split getContacts into sync vs getCached**
   - Clearer separation of concerns
   - Cached query should not trigger network

8. **Medium: Add SavedStateHandle to ViewModel**
   - Survives process death
   - Remember search query across rotation

9. **Low: Add comprehensive unit tests**
   - Test repository with mock pool
   - Test sync service with mock content resolver
   - Edge cases: empty contacts, null fields, large lists

10. **Low: Use Room for contact caching**
    - Reactive Flow queries replace callbackFlow hack
    - Proper migration support
