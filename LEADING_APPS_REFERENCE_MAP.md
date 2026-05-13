# Leading Apps — Complete Reference Map

> **Purpose:** A file-by-file, function-by-function reference of Leading Apps' codebase to inspire equivalent custom implementations in Enchant (C++ backend + Flutter frontend).
> **Source:** Leading-Apps-main (5,464 source files across 200+ modules)
> **Target:** Enchant — custom E2EE messaging app

---

## 1. Crypto / Protocol Layer

Leading Apps splits crypto into two layers:
1. **libsignal-client** (Rust via JNI) — core protocol primitives (X3DH, Double Ratchet, PQXDH, Sender Keys)
2. **Android stores** — persistent storage wrappers around the protocol layer

The Android code focuses on the store layer. Your C++ backend should implement the protocol algorithms directly.

---

### 1.1 TextSecureSessionStore.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/storage/TextSecureSessionStore.java`
**Purpose:** Implements `SignalServiceSessionStore` — loads/stores/deletes Signal Protocol session records via the local database (`SessionTable`), guarded by `ReentrantSessionLock`.

| Method | Signature | Description |
|---|---|---|
| `TextSecureSessionStore` | `TextSecureSessionStore(ServiceId accountId)` | Constructor — sets the owning account identity (ACI or PNI) |
| `loadSession` | `SessionRecord loadSession(SignalProtocolAddress address)` | Loads a single session record; returns empty `SessionRecord` if none exists |
| `loadExistingSessions` | `List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> addresses)` | Bulk-loads sessions; throws `NoSessionException` if any missing |
| `storeSession` | `void storeSession(SignalProtocolAddress address, SessionRecord record)` | Stores/updates a session record (upsert) |
| `containsSession` | `boolean containsSession(SignalProtocolAddress address)` | Returns true if session exists AND has a sender chain |
| `deleteSession` | `void deleteSession(SignalProtocolAddress address)` | Deletes a single session by address |
| `deleteAllSessions` | `void deleteAllSessions(String name)` | Deletes all sessions for a user name (all device IDs) |
| `getSubDeviceSessions` | `List<Integer> getSubDeviceSessions(String name)` | Returns device IDs that have sessions for this user |
| `getAllAddressesWithActiveSessions` | `Map<SignalProtocolAddress, SessionRecord> getAllAddressesWithActiveSessions(List<String> addressNames)` | Returns all active (has sender chain) sessions matching address names |
| `archiveSession` | `void archiveSession(SignalProtocolAddress address)` | Archives current state of a single session |
| `archiveSession` (overload) | `void archiveSession(ServiceId serviceId, int deviceId)` | Archives session by ServiceId + deviceId |
| `archiveSessions` (overload) | `void archiveSessions(RecipientId recipientId, int deviceId)` | Archives sessions for both ACI+PNI identities of a recipient |
| `archiveSessions` (overload) | `void archiveSessions(RecipientId recipientId)` | Archives all sessions for both identities |
| `archiveSiblingSessions` | `void archiveSiblingSessions(SignalProtocolAddress address)` | Archives all sessions for same user except given device |
| `archiveAllSessions` | `void archiveAllSessions()` | Archives every session for this account |
| `isActive` (private) | `boolean isActive(SessionRecord record)` | Helper — true if record is non-null and has a sender chain |

**Key pattern:** Dual identity support (ACI + PNI), thread-safe via `ReentrantSessionLock`, batch loading for performance.

---

### 1.2 SignalBaseIdentityKeyStore.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/storage/SignalBaseIdentityKeyStore.java`
**Purpose:** Core identity key storage shared by ACI and PNI stores. Manages identity records in `IdentityTable` with an LRU cache; handles trusted-identity checks, key updates, and non-blocking approval logic.

| Method | Signature | Description |
|---|---|---|
| `SignalBaseIdentityKeyStore` | `SignalBaseIdentityKeyStore(Context context)` | Public constructor |
| `SignalBaseIdentityKeyStore` | `SignalBaseIdentityKeyStore(Context context, IdentityTable identityDatabase)` | Package-private constructor for testing |
| `getLocalRegistrationId` | `int getLocalRegistrationId()` | Returns local registration ID from `SignalStore` |
| `saveIdentity` (1) | `IdentityChange saveIdentity(SignalProtocolAddress address, IdentityKey identityKey)` | Maps result to NEW_OR_UNCHANGED or REPLACED |
| `saveIdentity` (2) | `SaveResult saveIdentity(SignalProtocolAddress address, IdentityKey identityKey, boolean nonBlockingApproval)` | Core save: inserts new, handles key change (archives siblings, clears sender keys), sets approval |
| `saveIdentityWithoutSideEffects` | `void saveIdentityWithoutSideEffects(RecipientId, ServiceId, IdentityKey, VerifiedStatus, boolean, long, boolean)` | Direct cache write without identity-update side effects |
| `isTrustedIdentity` | `boolean isTrustedIdentity(SignalProtocolAddress, IdentityKey, Direction)` | Self always trusted; sending checks key match + approval; receiving always trusted |
| `getIdentity` | `IdentityKey getIdentity(SignalProtocolAddress address)` | Returns stored identity key or null |
| `getIdentityRecord` (1) | `Optional<IdentityRecord> getIdentityRecord(RecipientId recipientId)` | Looks up by recipientId |
| `getIdentityRecord` (2) | `Optional<IdentityRecord> getIdentityRecord(Recipient recipient)` | Looks up by recipient (resolves ServiceId) |
| `getIdentityRecords` | `IdentityRecordList getIdentityRecords(List<Recipient> recipients)` | Bulk lookup; returns EMPTY for no addresses |
| `setApproval` | `void setApproval(RecipientId, boolean nonBlockingApproval)` | Sets non-blocking approval flag |
| `setVerified` | `void setVerified(RecipientId, IdentityKey, VerifiedStatus)` | Sets verified status |
| `delete` | `void delete(String addressName)` | Deletes identity record |
| `invalidate` | `void invalidate(String addressName)` | Invalidates cache entry |
| `isTrustedForSending` (private) | `boolean isTrustedForSending(IdentityKey, IdentityStoreRecord)` | Checks key match + verified + non-blocking approval |
| `isNonBlockingApprovalRequired` (private) | `boolean isNonBlockingApprovalRequired(IdentityStoreRecord)` | True if not first-use, not yet approved, within threshold |

**Inner class `Cache`:**
| Method | Signature | Description |
|---|---|---|
| `Cache` | `Cache(IdentityTable identityDatabase)` | Creates LRU cache (size 1000) |
| `get` | `IdentityStoreRecord get(String addressName)` | Synchronized; cached or DB-loaded record |
| `save` | `void save(String, RecipientId, IdentityKey, VerifiedStatus, boolean, long, boolean)` | DB write + cache update under write lock |
| `setApproval` | `void setApproval(String, RecipientId, boolean)` | DB + cache update |
| `setVerified` | `void setVerified(String, RecipientId, IdentityKey, VerifiedStatus)` | DB + cache update |
| `delete` | `void delete(String addressName)` | DB + cache removal under write lock |
| `invalidate` | `void invalidate(String addressName)` | Removes from cache only |
| `withWriteLock` (private) | `void withWriteLock(Runnable)` | Acquires DB transaction first, then cache lock (prevents deadlock) |

---

### 1.3 SignalIdentityKeyStore.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/storage/SignalIdentityKeyStore.java`
**Purpose:** Thin wrapper around `SignalBaseIdentityKeyStore` that allows multiple instances (ACI, PNI) to share underlying storage while reporting different identity key pairs via a `Supplier<IdentityKeyPair>`.

| Method | Signature | Description |
|---|---|---|
| `SignalIdentityKeyStore` | `SignalIdentityKeyStore(SignalBaseIdentityKeyStore, Supplier<IdentityKeyPair>)` | Constructor |
| `getIdentityKeyPair` | `IdentityKeyPair getIdentityKeyPair()` | Delegates to identity supplier |
| `getLocalRegistrationId` | `int getLocalRegistrationId()` | Delegates to baseStore |
| `saveIdentity` | `IdentityChange saveIdentity(SignalProtocolAddress, IdentityKey)` | Delegates to base (non-blocking = false) |
| `saveIdentity` (overload) | `SaveResult saveIdentity(SignalProtocolAddress, IdentityKey, boolean)` | Delegates with explicit approval flag |
| `saveIdentityWithoutSideEffects` | `void saveIdentityWithoutSideEffects(...)` | Delegates to base |
| `isTrustedIdentity` | `boolean isTrustedIdentity(...)` | Delegates to base |
| `getIdentity` | `IdentityKey getIdentity(SignalProtocolAddress)` | Delegates to base |
| `getIdentityRecord` | `Optional<IdentityRecord> getIdentityRecord(RecipientId)` | Delegates to base |
| `getIdentityRecords` | `IdentityRecordList getIdentityRecords(List<Recipient>)` | Delegates to base |
| `setApproval` | `void setApproval(RecipientId, boolean)` | Delegates to base |
| `setVerified` | `void setVerified(RecipientId, IdentityKey, VerifiedStatus)` | Delegates to base |
| `delete` | `void delete(String addressName)` | Delegates to base |
| `invalidate` | `void invalidate(String addressName)` | Delegates to base cache invalidation |

Inner enum `SaveResult` — values: `NEW`, `UPDATE`, `NON_BLOCKING_APPROVAL_REQUIRED`, `NO_CHANGE`

---

### 1.4 TextSecurePreKeyStore.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/storage/TextSecurePreKeyStore.java`
**Purpose:** Implements both `SignalServicePreKeyStore` and `SignedPreKeyStore` — persists one-time EC pre-keys and signed pre-keys via `SignalDatabase`, guarded by `ReentrantSessionLock`.

| Method | Signature | Description |
|---|---|---|
| `TextSecurePreKeyStore` | `TextSecurePreKeyStore(ServiceId accountId)` | Constructor |
| `loadPreKey` | `PreKeyRecord loadPreKey(int preKeyId)` | Loads one-time EC pre-key by ID |
| `loadSignedPreKey` | `SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId)` | Loads signed pre-key by ID |
| `loadSignedPreKeys` | `List<SignedPreKeyRecord> loadSignedPreKeys()` | Loads all signed pre-keys for this account |
| `storePreKey` | `void storePreKey(int preKeyId, PreKeyRecord record)` | Stores one-time EC pre-key |
| `storeSignedPreKey` | `void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record)` | Stores signed pre-key |
| `containsPreKey` | `boolean containsPreKey(int preKeyId)` | Checks existence of one-time EC pre-key |
| `containsSignedPreKey` | `boolean containsSignedPreKey(int signedPreKeyId)` | Checks existence of signed pre-key |
| `removePreKey` | `void removePreKey(int preKeyId)` | Removes one-time EC pre-key |
| `removeSignedPreKey` | `void removeSignedPreKey(int signedPreKeyId)` | Removes signed pre-key |
| `markAllOneTimeEcPreKeysStaleIfNecessary` | `void markAllOneTimeEcPreKeysStaleIfNecessary(long staleTime)` | Marks stale one-time EC pre-keys |
| `deleteAllStaleOneTimeEcPreKeys` | `void deleteAllStaleOneTimeEcPreKeys(long threshold, int minCount)` | Deletes stale EC pre-keys past threshold, keeps minCount |

---

### 1.5 SignalKyberPreKeyStore.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/storage/SignalKyberPreKeyStore.kt`
**Purpose:** Implements `SignalServiceKyberPreKeyStore` for Kyber-1024 post-quantum pre-keys, stored in `KyberPreKeyTable`.

| Method | Signature | Description |
|---|---|---|
| `SignalKyberPreKeyStore` | `SignalKyberPreKeyStore(ServiceId selfServiceId)` | Constructor |
| `loadKyberPreKey` | `KyberPreKeyRecord loadKyberPreKey(int kyberPreKeyId)` | Loads Kyber pre-key by ID |
| `loadKyberPreKeys` | `List<KyberPreKeyRecord> loadKyberPreKeys()` | Loads all non-last-resort Kyber pre-keys |
| `loadLastResortKyberPreKeys` | `List<KyberPreKeyRecord> loadLastResortKyberPreKeys()` | Loads all last-resort Kyber pre-keys |
| `storeKyberPreKey` | `void storeKyberPreKey(int kyberPreKeyId, KyberPreKeyRecord record)` | Stores one-time Kyber pre-key |
| `storeLastResortKyberPreKey` | `void storeLastResortKyberPreKey(int, KyberPreKeyRecord)` | Stores last-resort Kyber pre-key |
| `containsKyberPreKey` | `boolean containsKyberPreKey(int kyberPreKeyId)` | Checks existence |
| `markKyberPreKeyUsed` | `void markKyberPreKeyUsed(int, int, ECPublicKey)` | Marks Kyber pre-key used, records SPK + base key |
| `removeKyberPreKey` | `void removeKyberPreKey(int kyberPreKeyId)` | Deletes Kyber pre-key |
| `markAllOneTimeKyberPreKeysStaleIfNecessary` | `void markAllOneTimeKyberPreKeysStaleIfNecessary(long)` | Marks stale one-time Kyber pre-keys |
| `deleteAllStaleOneTimeKyberPreKeys` | `void deleteAllStaleOneTimeKyberPreKeys(long, int)` | Deletes stale Kyber pre-keys |

---

### 1.6 SignalSenderKeyStore.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/storage/SignalSenderKeyStore.java`
**Purpose:** Implements `SignalServiceSenderKeyStore` for sender key session state (group messaging), stored via `SenderKeyTable` and `SenderKeySharedTable`.

| Method | Signature | Description |
|---|---|---|
| `SignalSenderKeyStore` | `SignalSenderKeyStore(Context context)` | Constructor |
| `storeSenderKey` | `void storeSenderKey(SignalProtocolAddress, UUID, SenderKeyRecord)` | Stores sender key for sender+distribution pair |
| `loadSenderKey` | `SenderKeyRecord loadSenderKey(SignalProtocolAddress, UUID)` | Loads sender key; null if none |
| `getSenderKeySharedWith` | `Set<SignalProtocolAddress> getSenderKeySharedWith(DistributionId)` | Returns addresses with whom key has been shared |
| `markSenderKeySharedWith` | `void markSenderKeySharedWith(DistributionId, Collection<SignalProtocolAddress>)` | Marks key shared with addresses |
| `clearSenderKeySharedWith` | `void clearSenderKeySharedWith(Collection<SignalProtocolAddress>)` | Clears shared-with markers |
| `deleteAllFor` | `void deleteAllFor(String addressName, DistributionId)` | Removes all sender key state for a recipient-distribution pair |
| `deleteAll` | `void deleteAll()` | Deletes all sender key session state |

---

### 1.7 SignalServiceAccountDataStoreImpl.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/storage/SignalServiceAccountDataStoreImpl.java`
**Purpose:** Top-level composite store implementing `SignalServiceAccountDataStore`. Delegates to all individual sub-stores (pre-keys, sessions, identity, sender keys, Kyber).

| Method | Signature | Description |
|---|---|---|
| `SignalServiceAccountDataStoreImpl` | `SignalServiceAccountDataStoreImpl(Context, TextSecurePreKeyStore, SignalKyberPreKeyStore, SignalIdentityKeyStore, TextSecureSessionStore, SignalSenderKeyStore)` | Constructor — sets all sub-stores |
| `isMultiDevice` | `boolean isMultiDevice()` | Delegates to `SignalStore.account()` |
| `getIdentityKeyPair` | `IdentityKeyPair getIdentityKeyPair()` | → identityKeyStore |
| `getLocalRegistrationId` | `int getLocalRegistrationId()` | → identityKeyStore |
| `saveIdentity` | `IdentityChange saveIdentity(...)` | → identityKeyStore |
| `isTrustedIdentity` | `boolean isTrustedIdentity(...)` | → identityKeyStore |
| `getIdentity` | `IdentityKey getIdentity(...)` | → identityKeyStore |
| `loadPreKey` | `PreKeyRecord loadPreKey(int)` | → preKeyStore |
| `storePreKey` | `void storePreKey(int, PreKeyRecord)` | → preKeyStore |
| `containsPreKey` | `boolean containsPreKey(int)` | → preKeyStore |
| `removePreKey` | `void removePreKey(int)` | → preKeyStore |
| `markAllOneTimeEcPreKeysStaleIfNecessary` | `void markAllOneTimeEcPreKeysStaleIfNecessary(long)` | → preKeyStore |
| `deleteAllStaleOneTimeEcPreKeys` | `void deleteAllStaleOneTimeEcPreKeys(long, int)` | → preKeyStore |
| `loadSession` | `SessionRecord loadSession(SignalProtocolAddress)` | → sessionStore |
| `loadExistingSessions` | `List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress>)` | → sessionStore |
| `getSubDeviceSessions` | `List<Integer> getSubDeviceSessions(String)` | → sessionStore |
| `getAllAddressesWithActiveSessions` | `Map<...> getAllAddressesWithActiveSessions(List<String>)` | → sessionStore |
| `storeSession` | `void storeSession(SignalProtocolAddress, SessionRecord)` | → sessionStore |
| `containsSession` | `boolean containsSession(SignalProtocolAddress)` | → sessionStore |
| `deleteSession` | `void deleteSession(SignalProtocolAddress)` | → sessionStore |
| `deleteAllSessions` | `void deleteAllSessions(String)` | → sessionStore |
| `archiveSession` | `void archiveSession(SignalProtocolAddress)` | → sessionStore.archiveSession() + clears sender key shared-with |
| `loadSignedPreKey` | `SignedPreKeyRecord loadSignedPreKey(int)` | → preKeyStore |
| `loadSignedPreKeys` | `List<SignedPreKeyRecord> loadSignedPreKeys()` | → preKeyStore |
| `storeSignedPreKey` | `void storeSignedPreKey(int, SignedPreKeyRecord)` | → preKeyStore |
| `containsSignedPreKey` | `boolean containsSignedPreKey(int)` | → preKeyStore |
| `removeSignedPreKey` | `void removeSignedPreKey(int)` | → preKeyStore |
| `loadKyberPreKey` | `KyberPreKeyRecord loadKyberPreKey(int)` | → kyberPreKeyStore |
| `loadKyberPreKeys` | `List<KyberPreKeyRecord> loadKyberPreKeys()` | → kyberPreKeyStore |
| `loadLastResortKyberPreKeys` | `List<KyberPreKeyRecord> loadLastResortKyberPreKeys()` | → kyberPreKeyStore |
| `storeKyberPreKey` | `void storeKyberPreKey(int, KyberPreKeyRecord)` | → kyberPreKeyStore |
| `storeLastResortKyberPreKey` | `void storeLastResortKyberPreKey(int, KyberPreKeyRecord)` | → kyberPreKeyStore |
| `containsKyberPreKey` | `boolean containsKyberPreKey(int)` | → kyberPreKeyStore |
| `markKyberPreKeyUsed` | `void markKyberPreKeyUsed(int, int, ECPublicKey)` | → kyberPreKeyStore |
| `removeKyberPreKey` | `void removeKyberPreKey(int)` | → kyberPreKeyStore |
| `markAllOneTimeKyberPreKeysStaleIfNecessary` | `void ...(long)` | → kyberPreKeyStore |
| `deleteAllStaleOneTimeKyberPreKeys` | `void ...(long, int)` | → kyberPreKeyStore |
| `storeSenderKey` | `void storeSenderKey(SignalProtocolAddress, UUID, SenderKeyRecord)` | → senderKeyStore |
| `loadSenderKey` | `SenderKeyRecord loadSenderKey(...)` | → senderKeyStore |
| `getSenderKeySharedWith` | `Set<...> getSenderKeySharedWith(DistributionId)` | → senderKeyStore |
| `markSenderKeySharedWith` | `void markSenderKeySharedWith(DistributionId, Collection<...>)` | → senderKeyStore |
| `clearSenderKeySharedWith` | `void clearSenderKeySharedWith(Collection<...>)` | → senderKeyStore |
| `identities` | `SignalIdentityKeyStore identities()` | Accessor |
| `preKeys` | `TextSecurePreKeyStore preKeys()` | Accessor |
| `sessions` | `TextSecureSessionStore sessions()` | Accessor |
| `senderKeys` | `SignalSenderKeyStore senderKeys()` | Accessor |

---

### 1.8 SignalServiceDataStoreImpl.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/storage/SignalServiceDataStoreImpl.java`
**Purpose:** Top-level router that selects between ACI and PNI account data stores based on a `ServiceId`.

| Method | Signature | Description |
|---|---|---|
| `SignalServiceDataStoreImpl` | `SignalServiceDataStoreImpl(Context, SignalServiceAccountDataStoreImpl aciStore, SignalServiceAccountDataStoreImpl pniStore)` | Constructor |
| `get` | `SignalServiceAccountDataStoreImpl get(ServiceId)` | Returns ACI or PNI store by which matches |
| `aci` | `SignalServiceAccountDataStoreImpl aci()` | Returns the ACI store |
| `pni` | `SignalServiceAccountDataStoreImpl pni()` | Returns the PNI store |
| `isMultiDevice` | `boolean isMultiDevice()` | Delegates to SignalStore |

---

### 1.9 PreKeyUtil.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/PreKeyUtil.java`
**Purpose:** Generates, stores, and cleans all pre-keys (EC one-time, signed pre-keys, Kyber one-time, Kyber last-resort). All methods are `synchronized`.

| Method | Signature | Description |
|---|---|---|
| `generateAndStoreOneTimeEcPreKeys` | `List<PreKeyRecord> generateAndStoreOneTimeEcPreKeys(SignalServiceAccountDataStore, PreKeyMetadataStore)` | Generates + stores 100 one-time EC pre-keys |
| `generateOneTimeEcPreKeys` | `List<PreKeyRecord> generateOneTimeEcPreKeys(int startingId)` | Generates 100 EC key pair records |
| `storeOneTimeEcPreKeys` | `void storeOneTimeEcPreKeys(SignalProtocolStore, PreKeyMetadataStore, List<PreKeyRecord>)` | Stores EC pre-keys and advances counter |
| `generateAndStoreOneTimeKyberPreKeys` | `List<KyberPreKeyRecord> generateAndStoreOneTimeKyberPreKeys(...)` | Generates + stores batch of one-time Kyber pre-keys |
| `generateOneTimeKyberPreKeyRecords` | `List<KyberPreKeyRecord> generateOneTimeKyberPreKeyRecords(int, ECPrivateKey)` | Generates 100 Kyber key pairs, signed with privateKey |
| `storeOneTimeKyberPreKeys` | `void storeOneTimeKyberPreKeys(SignalProtocolStore, PreKeyMetadataStore, List<KyberPreKeyRecord>)` | Stores Kyber pre-keys |
| `generateAndStoreSignedPreKey` (1) | `SignedPreKeyRecord generateAndStoreSignedPreKey(SignalProtocolStore, PreKeyMetadataStore)` | Uses protocol store's own identity key |
| `generateAndStoreSignedPreKey` (2) | `SignedPreKeyRecord generateAndStoreSignedPreKey(SignalProtocolStore, PreKeyMetadataStore, ECPrivateKey)` | Uses given private key |
| `generateSignedPreKey` | `SignedPreKeyRecord generateSignedPreKey(int, ECPrivateKey)` | Generates EC key pair, signs public key |
| `storeSignedPreKey` | `void storeSignedPreKey(SignalProtocolStore, PreKeyMetadataStore, SignedPreKeyRecord)` | Stores signed pre-key |
| `generateAndStoreLastResortKyberPreKey` (1) | `KyberPreKeyRecord generateAndStoreLastResortKyberPreKey(...)` | Uses protocol store's own identity key |
| `generateAndStoreLastResortKyberPreKey` (2) | `KyberPreKeyRecord generateAndStoreLastResortKyberPreKey(..., ECPrivateKey)` | Uses given private key |
| `generateLastResortKyberPreKey` | `KyberPreKeyRecord generateLastResortKyberPreKey(int, ECPrivateKey)` | Generates (but does not store) last-resort Kyber key |
| `generateKyberPreKey` (private) | `KyberPreKeyRecord generateKyberPreKey(int, ECPrivateKey)` | Generates Kyber-1024 key pair, signs with privateKey |
| `storeLastResortKyberPreKey` | `void storeLastResortKyberPreKey(...)` | Stores last-resort Kyber pre-key |
| `cleanSignedPreKeys` | `void cleanSignedPreKeys(SignalProtocolStore, PreKeyMetadataStore)` | Removes signed pre-keys older than 30 days |
| `cleanLastResortKyberPreKeys` | `void cleanLastResortKyberPreKeys(...)` | Removes last-resort Kyber keys older than 30 days |
| `cleanOneTimePreKeys` | `void cleanOneTimePreKeys(SignalServiceAccountDataStore)` | Deletes stale one-time EC/Kyber keys older than 90 days, min 200 |

**Key patterns for your implementation:** Batch generation of 100 prekeys, signed pre-key rotation every 30 days, one-time pre-key cleanup after 90 days, Kyber key support alongside EC keys.

---

### 1.10 ReentrantSessionLock.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/ReentrantSessionLock.java`
**Purpose:** Singleton enum implementing `SignalSessionLock` backed by `java.util.concurrent.locks.ReentrantLock`.

| Method | Signature | Description |
|---|---|---|
| `acquire` | `Lock acquire()` | Acquires the reentrant lock; returns `Lock` that unlocks on close |
| `isHeldByCurrentThread` | `boolean isHeldByCurrentThread()` | Whether current thread holds the lock |

**Key pattern:** Thread-safe session access prevents race conditions during concurrent message decryption. Your C++ backend needs equivalent mutex/lock for session operations.

---

### 1.11 SealedSenderAccessUtil.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/SealedSenderAccessUtil.java`
**Purpose:** Builds `SealedSenderAccess` (unidentified/sealed-sender) objects per recipient, handling certificate validation, profile-key-derived access keys, and unrestricted fallback.

| Method | Signature | Description |
|---|---|---|
| `getCertificateValidator` | `CertificateValidator getCertificateValidator()` | Returns singleton `CertificateValidator` from trusted root keys |
| `getSealedSenderAccessFor` (1) | `SealedSenderAccess getSealedSenderAccessFor(Recipient)` | Single recipient with logging |
| `getSealedSenderAccessFor` (2) | `SealedSenderAccess getSealedSenderAccessFor(Recipient, boolean)` | Single recipient, optional logging |
| `getSealedSenderAccessFor` (3) | `SealedSenderAccess getSealedSenderAccessFor(Recipient, SealedSenderAccess.CreateGroupSendToken)` | With group send token fallback |
| `getAccessFor` (1, private) | `UnidentifiedAccess getAccessFor(Recipient, boolean)` | Gets `UnidentifiedAccess` for a single recipient |
| `getAccessMapFor` | `Map<RecipientId, Optional<UnidentifiedAccess>> getAccessMapFor(List<Recipient>, boolean)` | Bulk access for fan-out send |
| `getAccessFor` (2, private) | `List<Optional<UnidentifiedAccess>> getAccessFor(List<Recipient>, boolean, boolean)` | Parallel access computation |
| `getSealedSenderCertificate` | `SenderCertificate getSealedSenderCertificate()` | Builds `SenderCertificate` from stored bytes |
| `getUnidentifiedAccessCertificateType` (private) | `CertificateType getUnidentifiedAccessCertificateType()` | Returns `ACI_AND_E164` or `ACI_ONLY` |
| `getUnidentifiedAccessCertificate` (private) | `byte[] getUnidentifiedAccessCertificate()` | Retrieves certificate bytes from `SignalStore` |
| `getTargetUnidentifiedAccess` (private) | `UnidentifiedAccess getTargetUnidentifiedAccess(Recipient, byte[], boolean)` | Derives access key from profile key or uses UNRESTRICTED |

---

### 1.12 ProfileKeyUtil.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/ProfileKeyUtil.java`
**Purpose:** Creates, parses, and converts `ProfileKey` objects (32-byte keys for profile encryption and sealed sender).

| Method | Signature | Description |
|---|---|---|
| `getSelfProfileKey` | `ProfileKey getSelfProfileKey()` | Returns current user's profile key |
| `profileKeyOrNull` (byte[]) | `ProfileKey profileKeyOrNull(byte[])` | Parses from bytes; null on invalid |
| `profileKeyOrNull` (String) | `ProfileKey profileKeyOrNull(String)` | Decodes base64 then parses |
| `profileKeyOrThrow` | `ProfileKey profileKeyOrThrow(byte[])` | Parses or throws |
| `profileKeyOptional` | `Optional<ProfileKey> profileKeyOptional(byte[])` | Wraps in Optional |
| `profileKeyOptionalOrThrow` | `Optional<ProfileKey> profileKeyOptionalOrThrow(byte[])` | Optional or throws |
| `createNew` | `ProfileKey createNew()` | Generates new random 32-byte profile key |

---

### 1.13 SenderKeyUtil.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/crypto/SenderKeyUtil.java`
**Purpose:** Manages sender key session state — rotates keys and clears state on re-registration.

| Method | Signature | Description |
|---|---|---|
| `rotateOurKey` | `void rotateOurKey(DistributionId)` | Deletes sender key + shared-with records, forcing re-creation |
| `getCreateTimeForOurKey` | `long getCreateTimeForOurKey(DistributionId)` | Returns creation timestamp or -1 |
| `clearAllState` | `void clearAllState()` | Deletes all sender key records and shared-with markers |

---

### 1.14 Buffered Protocol Stores

**Path:** `app/src/main/java/org/thoughtcrime/securesms/messages/protocol/`
**Purpose:** In-memory buffer stores used temporarily during message decryption. Buffer changes and flush to persistent store in batch.

#### BufferedSessionStore.kt

| Method | Signature | Description |
|---|---|---|
| `loadSession` | `SessionRecord loadSession(SignalProtocolAddress)` | Cache or fallback to DB |
| `loadExistingSessions` | `List<SessionRecord> loadExistingSessions(List<...>)` | Batch load, cache hit first |
| `storeSession` | `void storeSession(SignalProtocolAddress, SessionRecord)` | Cache + mark updated |
| `containsSession` | `boolean containsSession(SignalProtocolAddress)` | Cache or DB check |
| `deleteSession` | `void deleteSession(SignalProtocolAddress)` | Cache + mark for deletion |
| `flushToDisk` | `void flushToDisk(SignalServiceAccountDataStore)` | Writes all buffered updates/deletions to persistent store |

#### BufferedIdentityKeyStore.kt

| Method | Signature | Description |
|---|---|---|
| `getIdentityKeyPair` | `IdentityKeyPair getIdentityKeyPair()` | Returns local identity key pair |
| `getLocalRegistrationId` | `int getLocalRegistrationId()` | Returns local registration ID |
| `saveIdentity` | `IdentityChange saveIdentity(SignalProtocolAddress, IdentityKey)` | Cache + mark updated |
| `isTrustedIdentity` | `boolean isTrustedIdentity(...)` | Self trusted; send checks; receive always |
| `getIdentity` | `IdentityKey getIdentity(SignalProtocolAddress)` | Cache or DB fallback |
| `flushToDisk` | `void flushToDisk(SignalServiceAccountDataStore)` | Writes buffered updates |

#### BufferedSignalServiceAccountDataStore.kt

**Purpose:** Wrapper around all buffered stores (identity, prekey, kyber, signed prekey, session, sender key). Performs operations in memory, flushes to disk at set intervals.

| Method | Signature | Description |
|---|---|---|
| All `SignalServiceAccountDataStore` methods | — | Delegates to respective buffered sub-store |
| `flushToDisk` | `void flushToDisk(SignalServiceAccountDataStore)` | Flushes ALL buffered stores in a single DB transaction |

---

### 1.15 libsignal-service Crypto Layer

**Path:** `lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/crypto/`

#### SignalServiceCipher.java

**Purpose:** Encrypts outgoing messages and decrypts received envelopes using the Signal Protocol.

| Method | Signature | Description |
|---|---|---|
| `SignalServiceCipher` | `SignalServiceCipher(SignalServiceAddress, int, SignalServiceAccountDataStore, SignalSessionLock, CertificateValidator)` | Constructor |
| `encryptForGroup` | `byte[] encryptForGroup(DistributionId, List<SignalProtocolAddress>, Map<...>, SenderCertificate, byte[], ContentHint, Optional<byte[]>)` | Group message encryption using group cipher + multi-recipient sealed sender |
| `encrypt` | `OutgoingPushMessage encrypt(SignalProtocolAddress, SealedSenderAccess, EnvelopeContent)` | Single recipient encryption, supports sealed/unsealed |
| `decrypt` | `SignalServiceCipherResult decrypt(Envelope, long)` | Decrypts envelope by dispatching to decryptInternal |
| `decryptInternal` (private) | `Plaintext decryptInternal(Envelope, long)` | Routes by envelope type: PREKEY, DOUBLE_RATCHET, PLAINTEXT, UNIDENTIFIED_SENDER |

#### SignalSessionBuilder.java

**Purpose:** Thread-safe wrapper around `SessionBuilder` for processing pre-key bundles.

| Method | Signature | Description |
|---|---|---|
| `SignalSessionBuilder` | `SignalSessionBuilder(SignalSessionLock, SessionBuilder)` | Constructor |
| `process` | `void process(PreKeyBundle)` | Processes PreKeyBundle to establish/update session (thread-safe) |

#### SignalSessionCipher.java

**Purpose:** Thread-safe wrapper around `SessionCipher` for encrypting/decrypting session messages.

| Method | Signature | Description |
|---|---|---|
| `SignalSessionCipher` | `SignalSessionCipher(SignalSessionLock, SessionCipher)` | Constructor |
| `encrypt` | `CiphertextMessage encrypt(byte[])` | Encrypts padded message (thread-safe) |
| `decrypt` (PreKeySignalMessage) | `byte[] decrypt(PreKeySignalMessage)` | Decrypts PreKeySignalMessage |
| `decrypt` (SignalMessage) | `byte[] decrypt(SignalMessage)` | Decrypts regular SignalMessage |
| `getRemoteRegistrationId` | `int getRemoteRegistrationId()` | Returns remote registration ID |
| `getSessionVersion` | `int getSessionVersion()` | Returns session protocol version |

#### SealedSenderAccess.kt

**Purpose:** Single interface for sealed sender variations (group send token, unidentified access key, group endorsements, story sends).

| Method / Property | Signature | Description |
|---|---|---|
| `header` (abstract) | `val header: String` | `"$headerName:$headerValue"` |
| `switchToFallback` (abstract) | `fun switchToFallback(): SealedSenderAccess?` | Fallback access method or null |
| `forIndividualWithGroupFallback` (static) | `fun forIndividualWithGroupFallback(...)` | Creates Individual with group fallback |
| `forIndividual` (static) | `fun forIndividual(UnidentifiedAccess?)` | Creates Individual access |
| `forFanOutGroupSend` (static) | `fun forFanOutGroupSend(...)` | Creates access list for group fan-out |
| `forGroupSend` (static) | `fun forGroupSend(...)` | Creates group send or story noop |
| `isUnrestrictedForStory` | `fun isUnrestrictedForStory(SealedSenderAccess?)` | True if unrestricted for story |

---

## 2. Database Tables

Leading Apps' database has 76+ tables. Below are the tables most relevant to Enchant's feature set, organized by function.

---

### 2.1 MessageTable.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/database/MessageTable.kt`
**Purpose:** Core message storage — all message CRUD, story management, group calls, edits, reactions, and expiration.

| Category | Methods |
|---|---|
| **Read** | `getMessageCursor(id)`, `getMessageRecord(id)`, `getMessageRecordOrNull(id)`, `getMessages(Collection<Long>)`, `getMessageByTimestamp(long)`, `getMessagesBySentTimestamp(long)`, `getConversation(threadId)`, `getConversationForStart(threadId, startTime, limit)`, `getConversationSnippet(threadId)`, `getConversationSnippetType(threadId)` |
| **Insert** | `insertMessageInbox(IncomingMessage, MmsMessageRecord, boolean)`, `insertMessageOutbox(OutgoingMessage, List<NetworkFailure>, long)`, `insertCallLog(RecipientId, type, timestamp, outgoing)`, `insertGroupCall(...)`, `insertEditMessageInbox(...)`, `insertProfileNameChangeMessages(...)`, `insertGroupV1MigrationEvents(...)`, `insertNumberChangeMessages(...)`, `insertBoostRequestMessage(...)`, `insertThreadMergeEvent(...)`, `insertSessionSwitchoverEvent(...)`, `insertSmsExportMessage(...)` |
| **Update/Mark** | `markAsSent(id, boolean)`, `markAsSentFailed(id)`, `markAsSending(id)`, `markAsRemoteDelete(MessageRecord, RecipientId)`, `markAsRateLimited(id)`, `markAsInvalidVersionKeyExchange(id)`, `markAsUnsupportedProtocolVersion(id)`, `markAsInvalidMessage(id)`, `markAsLegacyVersion(id)`, `markSmsStatus(id, status)`, `clearRateLimitStatus(Collection<Long>)`, `updateBundleMessageBody(id, body)`, `updateCallLog(id, type)`, `updateGroupCall(...)`, `updatePreviousGroupCall(...)`, `setNetworkFailures(id, Set<NetworkFailure>)` |
| **Read receipts** | `setIncomingMessageViewed(id)`, `setIncomingMessagesViewed(List<Long>)`, `getViewedIncomingMessages(threadId)`, `setAllMessagesRead()`, `setMessagesReadSince(threadId, timestamp)`, `setEntireThreadRead(threadId)`, `setGroupStoryMessagesReadSince(threadId, groupStoryId, timestamp)` |
| **Unread** | `getUnreadCount(threadId)`, `getUnreadMentionCount(threadId)`, `getMostRecentReadMessageDateReceived(threadId)` |
| **Star/Pin** | `setStarred(id, boolean)`, `setStarred(Set<Long>, boolean)`, `getStarredMessages(Long)`, `getPinnedMessages(threadId, boolean)` |
| **Expiration** | `getExpirationStartedMessages()`, `willMessageExpireBeforeCutoff(id)`, `trimEntriesForExpiredMessages()` |
| **Stories** | `isStory(id)`, `getOutgoingStoriesTo(RecipientId)`, `getAllOutgoingStories(boolean, int)`, `markAllIncomingStoriesRead()`, `getAllStoriesFor(RecipientId, int)`, `getUnreadStories(RecipientId, int)`, `deleteGroupStoryReplies(parentStoryId)`, `deleteUnarchivedStoriesOlderThan(timestamp)`, `archiveStoriesOlderThan(timestamp)`, `getOldestStorySendTimestamp(boolean)` |
| **Delete** | `deleteMessage(id)`, `deleteMessage(id, boolean)`, `deleteMessages(Collection<Long>, boolean)`, `deleteAbandonedMessages()`, `deleteAllMessages()`, `deleteAllThreads()`, `deleteMessagesInThreadBeforeDate(threadId, date, inclusive)`, `deleteMessagesInThread(Set<Long>)`, `deleteCallUpdates(Set<Long>)` |
| **Reactions** | `setReactionsSeen(threadId, timestamp)`, `setAllReactionsSeen()` |
| **Collapsible** | `collapsePendingCollapsibleEvents(threadId, timestamp)`, `collapseAllPendingCollapsibleEvents()` |
| **Edit history** | `getMessageEditHistory(id)` |

---

### 2.2 ThreadTable.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/database/ThreadTable.kt`
**Purpose:** Conversation thread metadata — read state, pinning, archiving, snippets, conversation list queries.

| Category | Methods |
|---|---|
| **Read/Update snippet** | `updateSnippet(threadId, snippet, uri, date, type, unarchive)`, `updateSnippetUriSilently(threadId, id, uri)`, `updateSnippetTypeSilently(threadId)`, `getConversationMetadata(threadId)` |
| **Read state** | `setRead(threadId)`, `setRead(Collection<Long>)`, `setReadSince(threadId, timestamp)`, `setReadSince(Map<Long, Long>)`, `setEntireThreadRead(threadId)`, `setForcedUnread(Collection<Long>)`, `getUnreadThreadCount()`, `getUnreadMessageCount()`, `getUnreadMessageCount(threadId)`, `incrementUnread(threadId, amount, mentionAmount)`, `getUnreadThreadIdList()` |
| **Conversation list** | `getFilteredConversationList(List<RecipientId>, boolean)`, `getRecentConversationList(int, boolean, boolean)`, `getRecentConversationList(limit, includeInactive, individualsOnly, groupsOnly, hideV1, hideSms, hideSelf)`, `getRecentPushConversationList(limit)`, `getArchivedConversationList(filter, offset, limit)`, `getUnarchivedConversationList(filter, pinned, offset, limit, chatFolder)`, `getArchivedConversationListCount(filter)`, `getPinnedConversationListCount(filter, folder)`, `getUnarchivedConversationListCount(filter, folder)` |
| **Archive** | `isArchived(RecipientId)`, `setArchived(Set<Long>, boolean)`, `archiveConversation(threadId)`, `unarchiveConversation(threadId)`, `getArchivedRecipients()` |
| **Pin** | `getPinnedRecipientIds()`, `getPinnedThreadIds()`, `restorePins(Collection<Long>)`, `pinConversations(Collection<Long>)`, `unpinConversations(Collection<Long>)`, `setDistributionType(threadId, type)`, `getDistributionType(threadId)` |
| **CRUD** | `getThreadIdIfExistsFor(RecipientId)`, `getOrCreateThreadIdFor(Recipient)`, `getOrCreateThreadIdFor(Recipient, int)`, `getOrCreateThreadIdFor(RecipientId, boolean, int)`, `deleteConversation(threadId, boolean)`, `deleteConversations(Set<Long>, boolean)`, `deleteAllConversations()`, `getThreadFor(RecipientId)`, `getRecipientIdForThreadId(threadId)`, `getRecipientForThreadId(threadId)`, `getRecipientIdsForThreadIds(Collection<Long>)` |
| **Trim** | `trimAllThreads(length, date)`, `trimThread(threadId, sync, length, date, inclusive)` |
| **Merge** | `merge(RecipientId primary, RecipientId secondary)` returns `MergeResult` |
| **Storage sync** | `applyStorageSyncUpdate(RecipientId, SignalContactRecord)`, `applyStorageSyncUpdate(RecipientId, SignalGroupV1Record)`, `applyStorageSyncUpdate(RecipientId, SignalGroupV2Record)`, `applyStorageSyncUpdate(RecipientId, SignalAccountRecord)` |
| **Folder support** | `getThreadIdsByChatFolder(ChatFolderRecord)`, `getRecipientIdsByChatFolder(ChatFolderRecord)`, `hasChatInFolder(ChatFolderRecord)`, `hasUnmutedChatsInFolder(ChatFolderRecord)`, `getUnreadCountByChatFolder(ChatFolderRecord)` |

---

### 2.3 SessionTable.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/database/SessionTable.kt`
**Purpose:** Stores Signal Protocol session records keyed by (account_id, address, device).

| Method | Signature | Description |
|---|---|---|
| `store` | `void store(ServiceId, SignalProtocolAddress, SessionRecord)` | Upsert session record |
| `load` (single) | `SessionRecord load(ServiceId, SignalProtocolAddress)` | Load single session |
| `load` (batch) | `List<SessionRecord> load(ServiceId, List<SignalProtocolAddress>)` | Load multiple sessions |
| `getAllFor` (single) | `List<SessionRow> getAllFor(ServiceId, String)` | All sessions for an address |
| `getAllFor` (batch) | `List<SessionRow> getAllFor(ServiceId, List<String>)` | All sessions for multiple addresses |
| `getAll` | `List<SessionRow> getAll(ServiceId)` | All sessions for a service ID |
| `getSubDevices` | `List<Int> getSubDevices(ServiceId, String)` | Sub-device IDs for an address |
| `delete` | `void delete(ServiceId, SignalProtocolAddress)` | Delete single session |
| `deleteAllFor` | `void deleteAllFor(ServiceId, String)` | Delete all sessions for an address |
| `hasSessionFor` | `boolean hasSessionFor(ServiceId, String)` | Check session exists for identity |
| `hasAnySessionFor` | `boolean hasAnySessionFor(String)` | Check any identity has session |
| `findAllThatHaveAnySession` | `Set<PNI> findAllThatHaveAnySession(Set<PNI>)` | Filter to PNI's with sessions |

---

### 2.4 IdentityTable.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/database/IdentityTable.kt`
**Purpose:** Stores identity keys for contacts including verification status and trust approval.

| Method | Signature | Description |
|---|---|---|
| `getIdentityStoreRecord` (ServiceId) | `IdentityStoreRecord getIdentityStoreRecord(ServiceId)` | Get record by service ID |
| `getIdentityStoreRecord` (String) | `IdentityStoreRecord getIdentityStoreRecord(String)` | Get record by address name |
| `saveIdentity` | `void saveIdentity(String, RecipientId, IdentityKey, VerifiedStatus, boolean, long, boolean)` | Save identity record |
| `setApproval` | `void setApproval(String, RecipientId, boolean)` | Set non-blocking approval |
| `setVerified` | `void setVerified(String, RecipientId, IdentityKey, VerifiedStatus)` | Set verified status |
| `updateIdentityAfterSync` | `void updateIdentityAfterSync(String, RecipientId, IdentityKey, VerifiedStatus)` | Update after storage sync |
| `delete` | `void delete(String)` | Delete identity record |

---

### 2.5 RecipientTable.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/database/RecipientTable.kt`
**Purpose:** Central recipient/contact table storing all user profiles, settings, keys, group membership references.

| Category | Methods |
|---|---|
| **Lookup** | `getByE164(String)`, `getByServiceId(ServiceId)`, `getByAci(ACI)`, `getByPni(PNI)`, `getByUsername(String)`, `getByGroupId(GroupId)`, `getByCallLinkRoomId(CallLinkRoomId)`, `getByStorageId(byte[])` |
| **Get/Create** | `getOrInsertFromServiceId(ServiceId)`, `getOrInsertFromE164(String)`, `getOrInsertFromEmail(String)`, `getOrInsertFromGroupId(GroupId)`, `getOrInsertFromDistributionListId(...)`, `getOrInsertFromCallLinkRoomId(...)` |
| **Merge** | `getAndPossiblyMerge(ServiceId, String, boolean)`, `getAndPossiblyMergePnpVerified(ACI, PNI, String)`, `getAndPossiblyMerge(ACI, PNI, String, boolean, boolean)` |
| **Block/Mute** | `setBlocked(RecipientId, boolean)`, `getBlocked()`, `setMuted(RecipientId, long)`, `setMuted(Collection<RecipientId>, long)` |
| **Settings** | `setMessageRingtone(RecipientId, Uri)`, `setCallRingtone(RecipientId, Uri)`, `setMessageVibrate(RecipientId, VibrateState)`, `setCallVibrate(RecipientId, VibrateState)`, `setColor(RecipientId, ChatColors)`, `clearColor(RecipientId)`, `clearAllColors()` |
| **Expiration** | `setExpireMessages(RecipientId, int, int)`, `setExpireMessagesAndIncrementVersion(RecipientId, int)`, `setExpireMessagesWithoutIncrementingVersion(RecipientId, int)`, `setExpireMessagesForGroup(RecipientId, int)` |
| **Sync** | `applyStorageSyncContactInsert(SignalContactRecord, boolean)`, `applyStorageSyncContactUpdate(...)`, `applyStorageSyncGroupV1Insert(...)`, `applyStorageSyncGroupV1Update(...)`, `applyStorageSyncGroupV2Insert(...)`, `applyStorageSyncGroupV2Update(...)`, `applyStorageSyncAccountUpdate(...)` |

---

### 2.6 GroupTable.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/database/GroupTable.kt`
**Purpose:** Group metadata for V1, V2, and MMS groups — membership, avatar info, group send endorsements.

| Category | Methods |
|---|---|
| **CRUD** | `create` (V1), `create` (MMS), `create` (V2), `update` (V1), `update` (V2 by master key), `update` (V2 by ID), `remove(GroupId, RecipientId)` |
| **Read** | `getGroup(RecipientId)`, `getGroup(GroupId)`, `requireGroup(GroupId)`, `getGroupByDistributionId(DistributionId)`, `groupExists(GroupId)`, `getGroupsContainingMember(RecipientId, boolean)`, `getGroupsWithExactMembers(Set<RecipientId>)`, `getActiveGroupCount()` |
| **Membership** | `getGroupMemberIds(GroupId, MemberSet)`, `getGroupMembers(GroupId, MemberSet)`, `isCurrentMember(GroupId.Push, RecipientId)`, `setMember(GroupId, boolean)`, `isMember(GroupId)`, `isActive(GroupId)` |
| **Endorsements** | `getGroupSendEndorsements(GroupId)`, `updateGroupSendEndorsements(GroupId.V2, ReceivedGroupSendEndorsements)`, `getGroupSendFullToken(long threadId, RecipientId)`, `getGroupSendFullToken(GroupId.V2, RecipientId)` |
| **Stories** | `getGroupsToDisplayAsStories()`, `getShowAsStoryState(GroupId)`, `setShowAsStoryState(GroupId, ShowAsStoryState)`, `setShowAsStoryState(Collection<RecipientId>, ShowAsStoryState)` |

---

### 2.7 Other Core Tables

#### ReactionTable.kt
| Method | Signature | Description |
|---|---|---|
| `getReactions` | `List<ReactionRecord> getReactions(MessageId)` | Get reactions for a message |
| `getReactionsForMessages` | `Map<Long, List<ReactionRecord>> getReactionsForMessages(Collection<Long>)` | Batch get |
| `addReaction` | `void addReaction(MessageId, ReactionRecord)` | Add reaction |
| `deleteReaction` | `void deleteReaction(MessageId, RecipientId)` | Delete by author |
| `deleteReactions` | `void deleteReactions(MessageId)` | Delete all for message |
| `hasReaction` | `boolean hasReaction(MessageId, ReactionRecord)` | Check exists |
| `moveReactionsToNewMessage` | `void moveReactionsToNewMessage(long, long)` | For edits |

#### CallTable.kt
| Method | Signature | Description |
|---|---|---|
| `insertOneToOneCall` | `void insertOneToOneCall(long, long, RecipientId, Type, Direction, Event)` | Insert 1:1 call event |
| `updateOneToOneCall` | `Call updateOneToOneCall(long, Event)` | Update event state |
| `getCallById` | `Call getCallById(long, RecipientId)` | Get by ID |
| `getCallByMessageId` | `Call getCallByMessageId(long)` | Get by message ID |
| `markAllCallEventsRead` | `void markAllCallEventsRead(long)` | Mark read |
| `getUnreadMissedCallCount` | `long getUnreadMissedCallCount()` | Count unread missed |
| `deleteCallEventsDeletedBefore` | `int deleteCallEventsDeletedBefore(long)` | Cleanup old events |

#### PollTables.kt
| Method | Signature | Description |
|---|---|---|
| `insertPoll` | — | Create poll |
| `vote` | — | Cast vote |
| `getResults` | — | Get results |
| `closePoll` | — | Close poll |
| `deletePoll` | — | Delete poll |

#### StickerTable.kt
| Method | Signature | Description |
|---|---|---|
| `getPacks` | — | List installed packs |
| `getStickers` | — | List stickers in pack |
| `insertPack` | — | Install pack |
| `insertSticker` | — | Add sticker to pack |
| `deletePack` | — | Remove pack |
| `deleteSticker` | — | Remove sticker |

#### AttachmentTable.kt
| Method | Signature | Description |
|---|---|---|
| `insertAttachment` | — | Insert attachment record |
| `getAttachment` | — | Get attachment by ID |
| `getAttachmentsForMessage` | — | Get all for a message |
| `deleteAttachment` | — | Delete |
| `deleteAttachmentsForMessage` | — | Delete all for message |

#### DraftTable.kt
| Method | Signature | Description |
|---|---|---|
| `insertDraft` | — | Save draft |
| `getDraft` | — | Get draft for thread |
| `deleteDraft` | — | Clear draft |
| `deleteDraftsForThread` | — | Clear all for thread |

---

### 2.8 DatabaseObserver.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/database/DatabaseObserver.java`
**Purpose:** Global event bus for database changes. Decouples data mutations from UI updates.

| Method | Signature | Description |
|---|---|---|
| `registerConversationObserver` | `void registerConversationObserver(long threadId, ContentObserver)` | Observe a single thread |
| `registerConversationListObserver` | `void registerConversationListObserver(ContentObserver)` | Observe conversation list |
| `registerVerboseConversationObserver` | — | Fine-grained thread observation |
| `registerAttachmentObserver` | — | Attachment changes |
| `registerStickerObserver` | — | Sticker changes |
| `registerPaymentObserver` | — | Payment changes |
| `registerCallLogObserver` | — | Call log changes |
| `notifyConversationListeners` | — | Notify thread observers |
| `notifyConversationListListeners` | — | Notify list observers |
| `notifyAttachmentListeners` | — | Notify attachment observers |
| `notifyStickerListeners` | — | Notify sticker observers |
| `notifyPaymentListeners` | — | Notify payment observers |
| `notifyCallLogListeners` | — | Notify call log |

---

### 2.9 SignalDatabase.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/database/SignalDatabase.kt`
**Purpose:** Central database class extending `SQLiteOpenHelper`. Holds instances of all table classes as properties.

| Method/Property | Signature | Description |
|---|---|---|
| `instance` (companion) | `SignalDatabase instance()` | Singleton accessor |
| `messages` | `MessageTable messages` | Message table accessor |
| `threads` | `ThreadTable threads` | Thread table accessor |
| `recipients` | `RecipientTable recipients` | Recipient table accessor |
| `groups` | `GroupTable groups` | Group table accessor |
| `sessions` | `SessionTable sessions` | Session table accessor |
| `identities` | `IdentityTable identities` | Identity table accessor |
| `preKeys` | `OneTimePreKeyTable preKeys` | Pre-Key table accessor |
| `signedPreKeys` | `SignedPreKeyTable signedPreKeys` | Signed pre-key table accessor |
| `kyberPreKeys` | `KyberPreKeyTable kyberPreKeys` | Kyber pre-key table accessor |
| `senderKeys` | `SenderKeyTable senderKeys` | Sender key table accessor |
| `reactions` | `ReactionTable reactions` | Reactions table accessor |
| `calls` | `CallTable calls` | Call log table accessor |
| `polls` | `PollTables polls` | Polls table accessor |
| `stickers` | `StickerTable stickers` | Sticker table accessor |
| `attachments` | `AttachmentTable attachments` | Attachment table accessor |
| `drafts` | `DraftTable drafts` | Draft table accessor |
| `mentions` | `MentionTable mentions` | Mention table accessor |
| `search` | `SearchTable search` | FTS search table accessor |
| `recipientSettings` | `RecipientSettingsTable` | Recipient settings |
| + many more table accessors | | |

---

## 3. Network / Message Processing Layer

---

### 3.1 IncomingMessageObserver.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/messages/IncomingMessageObserver.kt`
**Purpose:** Application-level manager of incoming message processing. Keeps the authenticated websocket open and observes new inbound messages.

| Method | Signature | Description |
|---|---|---|
| `notifyRegistrationStateChanged` | `fun notifyRegistrationStateChanged()` | Releases connection semaphore to re-evaluate connection need |
| `notifyRestoreDecisionMade` | `fun notifyRestoreDecisionMade()` | Resets network after restore decision |
| `addDecryptionDrainedListener` | `fun addDecryptionDrainedListener(Runnable)` | Register listener for when decryption drains |
| `removeDecryptionDrainedListener` | `fun removeDecryptionDrainedListener(Runnable)` | Unregister listener |
| `terminate` | `fun terminate()` | Tears down observer, disconnects WS |
| `processEnvelope` | `fun processEnvelope(BufferedProtocolStore, Envelope, Long, BatchCache): List<FollowUpOperation>?` | Routes envelope to receipt or decryption processing |
| `processMessage` (private) | `fun processMessage(BufferedProtocolStore, Envelope, Long, BatchCache): List<FollowUpOperation>` | Decrypts envelope via MessageDecryptor, routes result |
| `processReceipt` (private) | `fun processReceipt(Envelope)` | Processes SERVER_DELIVERY_RECEIPT |
| `(MessageRetrievalThread) run` | `override fun run()` | Main loop: wait → connect → read batch → process |
| `(MessageRetrievalThread) processBatchInTransaction` | `fun processBatchInTransaction(List<EnvelopeResponse>): Boolean` | Batch process in single DB transaction |
| `(MessageRetrievalThread) processMessagesIndividually` | `fun processMessagesIndividually(List<EnvelopeResponse>)` | Fallback: one message per transaction |

---

### 3.2 MessageContentProcessor.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/messages/MessageContentProcessor.kt`
**Purpose:** Given decrypted message content, inserts proper message content into DB and dispatches to type-specific processors.

| Method | Signature | Description |
|---|---|---|
| `create` | `fun create(Context): MessageContentProcessor` | Factory |
| `process` | `fun process(Envelope, Content, EnvelopeMetadata, Long, Boolean, SignalLocalMetrics?, BatchCache?)` | Main entry: handles decrypted message + processes dependent early-cached messages |
| `processException` | `fun processException(MessageState, ExceptionMetadata, Long)` | Handles failed decryption states |
| `handleMessage` (private) | `fun handleMessage(...)` | Routes content to type-specific processor |
| `handleTypingMessage` (private) | `fun handleTypingMessage(...)` | Processes typing indicators |
| `handleRetryReceipt` (private) | `fun handleRetryReceipt(...)` | Handles DecryptionErrorMessage retry |
| `handleSenderKeyRetryReceipt` (private) | `fun handleSenderKeyRetryReceipt(...)` | Sender-key retry receipt handling |
| `handleIndividualRetryReceipt` (private) | `fun handleIndividualRetryReceipt(...)` | Individual retry receipt handling |
| `shouldIgnore` (private) | `fun shouldIgnore(Content, Recipient, Recipient): Boolean` | Checks blocked, inactive group, announcement-only |
| `handleGv2PreProcessing` | `fun handleGv2PreProcessing(...): Gv2PreProcessResult` | Pre-processes GV2 group messages |
| `updateGv2GroupFromServerOrP2PChange` | `fun updateGv2GroupFromServerOrP2PChange(...): GroupUpdateResult?` | Applies GV2 group change |

---

### 3.3 MessageDecryptor.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/messages/MessageDecryptor.kt`
**Purpose:** Takes an Envelope and decrypts it into usable content (or provides appropriate error).

| Method | Signature | Description |
|---|---|---|
| `decrypt` | `fun decrypt(Context, BufferedProtocolStore, Envelope, Long): Result` | Main entry: validates destination, creates cipher, decrypts, handles SKDM and PNI signatures |
| `buildResultForDecryptionError` (private) | `fun buildResultForDecryptionError(...): Result` | Builds error result with retry logic |
| `handleSenderKeyDistributionMessage` (private) | `fun handleSenderKeyDistributionMessage(...)` | Processes SKDM via group session builder |
| `handlePniSignatureMessage` (private) | `fun handlePniSignatureMessage(...)` | Validates PNI signature, associates ACI with PNI |

---

### 3.4 WebSocket Layer

#### SignalWebSocket.kt
**Path:** `lib/libsignal-service/src/main/java/org/whispersystems/signalservice/api/websocket/SignalWebSocket.kt`
**Purpose:** Base wrapper around WebSocketConnection with auth, keep-alives, and request/response.

| Method | Signature | Description |
|---|---|---|
| `connect` | `@Synchronized fun connect()` | Create/reuse connection and connect |
| `disconnect` | `@Synchronized fun disconnect()` | Teardown and emit DISCONNECTED |
| `sendKeepAlive` | `@Synchronized fun sendKeepAlive()` | Send keep-alive if connection available |
| `shouldSendKeepAlives` | `fun shouldSendKeepAlives(): Boolean` | True if keep-alive tokens registered |
| `registerKeepAliveToken` | `fun registerKeepAliveToken(String)` | Register token, triggers connect |
| `removeKeepAliveToken` | `fun removeKeepAliveToken(String)` | Remove token, may schedule delayed disconnect |
| `request` | `fun request(WebSocketRequestMessage): Single<WebsocketResponse>` | Send request via WS (2 overloads) |
| `request` | `fun request(WebSocketRequestMessage, Duration): Single<WebsocketResponse>` | With timeout |
| `sendAck` | `fun sendAck(EnvelopeResponse)` | Send HTTP 200 ACK for received envelope |
| `runWithChatConnection` | `suspend fun <T> runWithChatConnection(callback): T` | Execute with libsignal ChatConnection |
| `forceNewWebSocket` | `@Synchronized fun forceNewWebSocket()` | Force new connection |
| `readMessageBatch` (AuthenticatedWebSocket) | `fun readMessageBatch(Long, Int, MessageReceivedCallback): Boolean` | Read batch of messages |

#### WebSocketConnection.kt
**Path:** `lib/libsignal-service/src/main/java/org/whispersystems/signalservice/internal/websocket/WebSocketConnection.kt`
**Purpose:** Common interface for WS connection with two impls (OkHttp + libsignal-net).

| Method | Signature | Description |
|---|---|---|
| `connect` | `fun connect(): Observable<WebSocketConnectionState>` | Connect, return state observable |
| `isDead` | `fun isDead(): Boolean` | True if connection cannot be reused |
| `disconnect` | `fun disconnect()` | Disconnect |
| `shutdown` | `fun shutdown()` | Disconnect + prevent reuse |
| `sendRequest` (1) | `fun sendRequest(WebSocketRequestMessage): Single<WebsocketResponse>` | Default timeout (10s) |
| `sendRequest` (2) | `fun sendRequest(WebSocketRequestMessage, Long): Single<WebsocketResponse>` | Custom timeout |
| `sendKeepAlive` | `fun sendKeepAlive()` | Send keep-alive ping |
| `readRequestIfAvailable` | `fun readRequestIfAvailable(): Optional<WebSocketRequestMessage>` | Non-blocking read |
| `readRequest` | `fun readRequest(Long): WebSocketRequestMessage` | Blocking read with timeout |
| `sendResponse` | `fun sendResponse(WebSocketResponseMessage)` | Send response to a request |

#### LibSignalChatConnection.kt
**Path:** `lib/libsignal-service/src/main/java/org/whispersystems/signalservice/internal/websocket/LibSignalChatConnection.kt`
**Purpose:** Implements WebSocketConnection via libsignal-net's ChatService (auth + unauth).

| Method | Signature | Description |
|---|---|---|
| `connect` | `override fun connect(): Observable<WebSocketConnectionState>` | Connect via libsignal-net |
| `handleConnectionSuccess` (private) | `fun handleConnectionSuccess(ChatConnection)` | Emit CONNECTED, run pending |
| `handleConnectionFailure` (private) | `fun handleConnectionFailure(Throwable)` | Emit AUTH_FAILED / FAILED |
| `disconnect` | `override fun disconnect()` | Disconnect, wait for confirmation |
| `shutdown` | `override fun shutdown()` | Disconnect + shutdown executor |
| `sendRequest` | `override fun sendRequest(WebSocketRequestMessage, Long): Single<WebsocketResponse>` | Send via libsignal, enqueue if CONNECTING |
| `readRequest` | `override fun readRequest(Long): WebSocketRequestMessage` | Blocking read with timeout |
| `sendResponse` | `override fun sendResponse(WebSocketResponseMessage)` | Send ack for processed envelope |
| `onIncomingMessage` (listener) | `fun onIncomingMessage(ChatConnection, ByteArray, Long, ServerMessageAck?)` | Called by libsignal when message arrives |
| `onConnectionInterrupted` (listener) | `fun onConnectionInterrupted(ChatConnection, ChatServiceException?)` | Called on connection interrupt |
| `onQueueEmpty` (listener) | `fun onQueueEmpty(ChatConnection)` | Called when server queue is empty |

---

---
## 4. Auth / Registration Layer

Leading Apps' registration flow is structured as a single self-contained `feature/registration/` module using an **event-driven MVVM** pattern with a state machine.

### 4.1 RegistrationViewModel.kt

**Path:** `feature/registration/src/main/java/org/signal/registration/RegistrationViewModel.kt`
**Purpose:** ViewModel shared across the entire registration flow — processes events, manages navigation backstack, and persists flow state.

| Method | Signature | Description |
|---|---|---|
| `processEvent` | `suspend fun processEvent(event: RegistrationFlowEvent)` | Processes incoming flow events by applying them to state and persisting flow state |
| `applyEvent` | `suspend fun applyEvent(state: RegistrationFlowState, event: RegistrationFlowEvent): RegistrationFlowState` | State reducer — handles: ResetState, SessionUpdated, E164Chosen, Registered, MasterKeyRestoredFromSvr, NavigateToScreen, NavigateBack, RecoveryPasswordInvalid, PendingRestoreOptionSelected, UserSuppliedAepSubmitted, UserSuppliedAepVerified, RegistrationComplete |
| `validateRestoredState` | `suspend fun validateRestoredState(state: RegistrationFlowState): RegistrationFlowState` | Validates a restored flow state — checks session expiry, resets to PhoneNumberEntry if expired |
| `getRequiredPermissions` | `fun getRequiredPermissions(): List<String>` | Returns permissions to request based on API level |
| `persistFlowState` | `suspend fun persistFlowState(event: RegistrationFlowEvent)` | Persists or clears flow state based on event type |

### 4.2 RegistrationRepository.kt (feature module)

**Path:** `feature/registration/src/main/java/org/signal/registration/RegistrationRepository.kt`
**Purpose:** Handles registration logic, session management, key generation, and data persistence.

| Method | Signature | Description |
|---|---|---|
| `createSession` | `suspend fun createSession(e164: String): RequestResult<SessionMetadata, CreateSessionError>` | Creates a verification session with the server |
| `requestVerificationCode` | `suspend fun requestVerificationCode(sessionId, smsAutoRetrieve, transport): RequestResult<...>` | Requests verification code via SMS or voice |
| `getCaptchaUrl` | `fun getCaptchaUrl(): String` | Returns captcha verification URL |
| `submitCaptchaToken` | `suspend fun submitCaptchaToken(sessionId, captchaToken): RequestResult<...>` | Submits captcha token to update session |
| `awaitPushChallengeToken` | `suspend fun awaitPushChallengeToken(): String?` | Waits for push challenge token via FCM |
| `submitPushChallengeToken` | `suspend fun submitPushChallengeToken(sessionId, pushChallengeToken): RequestResult<...>` | Submits push challenge token |
| `submitVerificationCode` | `suspend fun submitVerificationCode(sessionId, verificationCode): RequestResult<...>` | Submits verification code |
| `registerAccountWithRecoveryPassword` | `suspend fun registerAccountWithRecoveryPassword(e164, recoveryPassword, registrationLock?, skipDeviceTransfer, preExistingRegistrationData?, existingAccountEntropyPool?): RequestResult<...>` | Registers using recovery password (RRP), bypassing session |
| `registerAccountWithSession` | `suspend fun registerAccountWithSession(e164, sessionId, registrationLock?, skipDeviceTransfer): RequestResult<...>` | Registers after phone verification via session |
| `registerAccount` (private) | `suspend fun registerAccount(e164, sessionId?, recoveryPassword?, registrationLock?, skipDeviceTransfer, existingAciIdentityKeyPair?, existingPniIdentityKeyPair?): RequestResult<...>` | Core registration: generates keys, creates account attributes, calls network, persists |
| `setNewlyCreatedPin` | `suspend fun setNewlyCreatedPin(pin, isAlphanumeric, masterKey): RequestResult<...>` | Backs up master key to SVR with user's PIN |
| `getPreExistingRegistrationData` | `suspend fun getPreExistingRegistrationData(): PreExistingRegistrationData?` | Returns data for existing registration (re-registration) |
| `saveFlowState` / `restoreFlowState` / `clearFlowState` | — | Persists/restores/clears JSON flow state |
| `isRegistered` | `suspend fun isRegistered(): Boolean` | Checks if registration is complete |
| `generateKeyMaterial` (private) | `fun generateKeyMaterial(existingAccountEntropyPool?, existingAciIdentityKeyPair?, existingPniIdentityKeyPair?): KeyMaterial` | Generates or reuses identity keys, pre-keys, profile key, password |
| `generateSignedPreKey` / `generateKyberPreKey` / `generateRegistrationId` / `generateProfileKey` / `generatePassword` / `deriveUnidentifiedAccessKey` | private | Cryptographic key material generation helpers |

### 4.3 RegistrationFlowState.kt

**Path:** `feature/registration/src/main/java/org/signal/registration/RegistrationFlowState.kt`
**Purpose:** Data class representing the entire registration flow state — navigation stack, session data, cryptographic keys.

**Fields:**
| Field | Type | Description |
|---|---|---|
| `backStack` | `List<RegistrationRoute>` | Navigation stack controlling what screen is shown |
| `sessionMetadata` | `SessionMetadata?` | Current registration session metadata |
| `sessionE164` | `String?` | E164 associated with the session |
| `accountEntropyPool` | `AccountEntropyPool?` | Generated or restored AEP |
| `temporaryMasterKey` | `MasterKey?` | Master key restored from SVR |
| `preExistingRegistrationData` | `PreExistingRegistrationData?` | Data from previous registration |
| `pendingRestoreOption` | `PendingRestoreOption?` | User-selected restore option |
| `unverifiedRestoredAep` | `AccountEntropyPool?` | AEP obtained via manual entry, not yet verified |

### 4.4 RegistrationFlowEvent.kt

**Path:** `feature/registration/src/main/java/org/signal/registration/RegistrationFlowEvent.kt`
**Purpose:** Sealed interface defining all events in the registration flow state machine.

| Event | Description |
|---|---|
| `NavigateToScreen(route)` | Navigate to a specific screen |
| `NavigateBack` | Navigate back one screen |
| `ResetState` | Complete reset of registration state |
| `SessionUpdated(session)` | Update to ongoing registration session |
| `E164Chosen(e164)` | E164 for this registration was updated |
| `Registered(accountEntropyPool)` | User successfully registered |
| `MasterKeyRestoredFromSvr(masterKey)` | Master key restored from SVR |
| `RecoveryPasswordInvalid` | RRP-based registration not possible |
| `PendingRestoreOptionSelected(option)` | User selected/cleared a restore option |
| `UserSuppliedAepSubmitted(aep)` | AEP manually input but not yet verified |
| `UserSuppliedAepVerified(aep)` | Previously submitted AEP validated |
| `RegistrationComplete` | Registration finalized |

### 4.5 Registration Routes (RegistrationNavigation.kt)

**Path:** `feature/registration/src/main/java/org/signal/registration/RegistrationNavigation.kt`
**Purpose:** All navigation routes defined as a sealed interface `RegistrationRoute`.

| Route | Description |
|---|---|
| `Welcome` | Landing screen |
| `Permissions(nextRoute)` | Runtime permissions |
| `PhoneNumberEntry` | Phone number input |
| `CountryCodePicker(country?)` | Country selection |
| `VerificationCodeEntry` | OTP/verification code |
| `Captcha(session)` | Captcha challenge |
| `PinEntryForSvrRestore` | PIN entry for SVR restore (post-registration) |
| `PinEntryForRegistrationLock(timeRemaining, svrCredentials)` | PIN for registration lock |
| `PinEntryForSmsBypass(svrCredentials)` | PIN to bypass SMS |
| `AccountLocked(timeRemainingMs)` | Account locked display |
| `PinCreate` | PIN creation |
| `ArchiveRestoreSelection(restoreOptions, isPreRegistration)` | Choose restore option |
| `LocalBackupRestore(isPreRegistration)` | Local backup restore |
| `EnterLocalBackupV1Passphrase` | V1 backup passphrase |
| `EnterAepForLocalBackup` | Enter AEP for local backup |
| `EnterAepForRemoteBackupPreRegistration(e164)` | Enter AEP for remote backup (pre-reg) |
| `EnterAepForRemoteBackupPostRegistration` | Enter AEP for remote backup (post-reg) |
| `RemoteRestore(aep)` | Remote backup restore |
| `QuickRestoreQrScan` | QR scan for quick restore |
| `Profile` | Profile creation |
| `FullyComplete` | Final destination |

### 4.6 NetworkController.kt

**Path:** `feature/registration/src/main/java/org/signal/registration/NetworkController.kt`
**Purpose:** Interface defining all network operations for registration.

| Method | Signature | Description |
|---|---|---|
| `createSession` | `suspend fun createSession(e164, fcmToken?, mcc?, mnc?): RequestResult<SessionMetadata, CreateSessionError>` | POST /v1/verification/session |
| `getSession` | `suspend fun getSession(sessionId): RequestResult<SessionMetadata, GetSessionStatusError>` | GET /v1/verification/session/{id} |
| `updateSession` | `suspend fun updateSession(sessionId, pushChallengeToken?, captchaToken?): RequestResult<...>` | PATCH /v1/verification/session/{id} |
| `requestVerificationCode` | `suspend fun requestVerificationCode(sessionId, locale, androidSmsRetriever, transport): RequestResult<...>` | POST /v1/verification/session/{id}/code |
| `submitVerificationCode` | `suspend fun submitVerificationCode(sessionId, verificationCode): RequestResult<...>` | PUT /v1/verification/session/{id}/code |
| `registerAccount` | `suspend fun registerAccount(e164, password, sessionId?, recoveryPassword?, attributes, aciPreKeys, pniPreKeys, fcmToken?, skipDeviceTransfer): RequestResult<...>` | POST /v1/registration |
| `restoreMasterKeyFromSvr` | `suspend fun restoreMasterKeyFromSvr(svrCredentials, pin): RequestResult<MasterKeyResponse, RestoreMasterKeyError>` | Restores master key from SVR |
| `setPinAndMasterKeyOnSvr` | `suspend fun setPinAndMasterKeyOnSvr(pin, masterKey): RequestResult<SvrCredentials?, BackupMasterKeyError>` | Backs up master key to SVR with PIN |
| `getSvrCredentials` | `suspend fun getSvrCredentials(): RequestResult<SvrCredentials, GetSvrCredentialsError>` | GET /v2/svr/auth |
| `checkSvrCredentials` | `suspend fun checkSvrCredentials(e164, credentials): RequestResult<...>` | POST /v2/svr/auth/check |
| `setAccountAttributes` | `suspend fun setAccountAttributes(attributes): RequestResult<Unit, SetAccountAttributesError>` | PUT /v1/accounts/attributes |
| `getRemoteBackupInfo` | `suspend fun getRemoteBackupInfo(aep): RequestResult<...>` | GET /v1/archives |

### 4.7 Screen-Level ViewModels

#### PhoneNumberEntryViewModel.kt

| Method | Description |
|---|---|
| `processEvent(event)` | Processes phone number screen events |
| `applyEvent(state, event, parentEventEmitter, stateEmitter)` | State reducer for country/phone changes, submission |
| `applyPhoneNumberSubmitted(state, parentEventEmitter)` | Core submit: checks pending restore, tries RRP re-registration, falls back to session-based |
| `applySessionBasedRegistration(state, e164, parentEventEmitter)` | Creates session, handles push challenge, captcha, requests code |

#### VerificationCodeViewModel.kt

| Method | Description |
|---|---|
| `processEvent(event)` | Processes verification code events |
| `applyEvent(state, event, stateEmitter)` | State reducer for code submission, resend, countdown |
| `applyCodeEntered(state, code)` | Submits code, handles response, attempts registration on success |
| `applyResendCode(state, transport)` | Requests new verification code via SMS/voice |

#### PinCreationViewModel.kt

| Method | Description |
|---|---|
| `processEvent(event)` | Processes PIN creation events |
| `applyPinSubmitted(state, pin)` | Backs up master key to SVR with PIN, emits RegistrationComplete |

#### PinEntry ViewModels (3 variants)

| Variant | Purpose |
|---|---|
| `PinEntryForRegistrationLockViewModel` | PIN entry when account has registration lock |
| `PinEntryForSmsBypassViewModel` | PIN entry to bypass SMS verification |
| `PinEntryForSvrRestoreViewModel` | PIN entry to restore master key post-registration |

### 4.8 Key Pattern: Event-Driven State Machine

The registration flow uses an event-driven MVVM pattern:
- **State**: `RegistrationFlowState` — immutable data class with navigation backstack
- **Events**: `RegistrationFlowEvent` sealed interface — all possible actions
- **Reducer**: `applyEvent(state, event) → newState` — pure function
- **ViewModel**: `RegistrationViewModel` — connects UI to state machine, persists state across process death

This pattern is worth adopting for your auth flow in Enchant.

---

## 5. Chat / Messaging Layer

### 5.1 ConversationViewModel.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/conversation/v2/ConversationViewModel.kt`
**Purpose:** Main chat screen ViewModel — manages message sending, reactions, scrolling, identity records, pinned messages, polls.

| Method | Signature | Description |
|---|---|---|
| `sendMessage` | `fun sendMessage(metricId, threadRecipient, body, slideDeck, scheduledDate, messageToEdit, quote, mentions, bodyRanges, contacts, linkPreviews, preUploadResults, isViewOnce): Completable` | **Core send** — sends message via repository |
| `resendMessage` | `fun resendMessage(conversationMessage): Completable` | Resends a failed message |
| `sendPoll` | `fun sendPoll(threadRecipient, poll): Completable` | Sends a poll message |
| `endPoll` | `fun endPoll(pollId): Completable` | Ends a poll |
| `updateReaction` | `fun updateReaction(messageRecord, emoji): Completable` | Sends new reaction or removes existing |
| `toggleVote` | `fun toggleVote(poll, pollOption, isChecked)` | Toggles poll vote |
| `pinMessage` | `fun pinMessage(messageRecord, duration, threadRecipient): Completable` | Pins a message |
| `unpinMessage` | `fun unpinMessage(messageId): Completable` | Unpins a message |
| `setMessageStarred` | `fun setMessageStarred(messageId, starred): Completable` | Stars/unstars a message |
| `setMessagesStarred` | `fun setMessagesStarred(messageIds, starred): Completable` | Stars/unstars multiple messages |
| `copyToClipboard` | `fun copyToClipboard(context, messageParts): Maybe<CharSequence>` | Copies selected messages to clipboard |
| `moveToDate` | `fun moveToDate(receivedTimestamp): Single<Int>` | Scrolls to position for a date |
| `moveToMessage` (×3) | `fun moveToMessage(messageId): Single<Int>` / `(dateReceived, author)` / `(messageRecord)` | Scrolls to a specific message |
| `muteConversation` | `fun muteConversation(until)` | Mutes conversation |
| `setSearchQuery` | `fun setSearchQuery(query?)` | Sets search highlight query |
| `setLastScrolled` | `fun setLastScrolled(lastScrolledTimestamp)` | Persists scroll position |
| `startExpirationTimeout` | `fun startExpirationTimeout(messageRecord)` | Starts disappearing message timer |
| `markGiftBadgeRevealed` | `fun markGiftBadgeRevealed(messageRecord)` | Marks gift badge as revealed |
| `startPlaintextExport` | `fun startPlaintextExport(context, includeMedia)` | Exports chat as plaintext ZIP |
| `cancelExport` | `fun cancelExport()` | Cancels in-progress export |
| `onCleared` | `override fun onCleared()` | Clears disposables |

### 5.2 ConversationRepository.kt (v2)

**Path:** `app/src/main/java/org/thoughtcrime/securesms/conversation/v2/ConversationRepository.kt`
**Purpose:** Data layer for ConversationViewModel v2 — handles message sending, reactions, polls, pinning, DB queries.

| Method | Signature | Description |
|---|---|---|
| `sendMessage` | `fun sendMessage(threadId, threadRecipient, metricId, body, slideDeck, scheduledDate, messageToEdit, quote, mentions, bodyRanges, contacts, linkPreviews, preUploadResults, isViewOnce): Completable` | **Core send** — splits body, builds OutgoingMessage, calls MessageSender |
| `sendNewReaction` | `fun sendNewReaction(messageRecord, emoji): Completable` | Sends new reaction via MessageSender |
| `sendReactionRemoval` | `fun sendReactionRemoval(messageRecord, oldRecord): Completable` | Sends reaction removal |
| `sendPoll` | `fun sendPoll(threadRecipient, poll): Completable` | Creates OutgoingMessage + sends poll |
| `endPoll` | `fun endPoll(pollId): Completable` | Ends poll, sends terminate, resends to skipped |
| `pinMessage` | `fun pinMessage(messageRecord, duration, threadRecipient): Completable` | Pins via PinSendUtil |
| `unpinMessage` | `fun unpinMessage(messageId): Completable` | Unpins, enqueues job |
| `resendMessage` | `fun resendMessage(messageRecord): Completable` | Resends via MessageSender.resend |
| `getConversationThreadState` | `fun getConversationThreadState(threadId, requestedStartPosition): Single<ConversationThreadState>` | Loads paged conversation thread |
| `getIdentityRecords` | `fun getIdentityRecords(recipient, groupRecord?): Single<IdentityRecordsState>` | Gets identity verification records |
| `copyToClipboard` | `fun copyToClipboard(context, messageParts): Maybe<CharSequence>` | Extracts + copies message bodies |
| `setConversationMuted` | `fun setConversationMuted(recipientId, until)` | Mutes conversation |

### 5.3 GroupSendUtil.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/messages/GroupSendUtil.java`
**Purpose:** Handles fan-out group message sending with sender key + legacy 1:1 fallback + group send endorsements.

| Method | Signature | Description |
|---|---|---|
| `sendResendableDataMessage` | `static List<SendMessageResult> sendResendableDataMessage(Context, GroupId.V2, DistributionListId, List<Recipient>, boolean, ContentHint, MessageId, SignalServiceDataMessage, boolean, boolean, SignalServiceEditMessage, CancelationSignal)` | **Main group send** — resendable with MSL logging |
| `sendUnresendableDataMessage` | `static List<SendMessageResult> sendUnresendableDataMessage(Context, GroupId.V2, List<Recipient>, boolean, ContentHint, SignalServiceDataMessage, boolean, CancelationSignal)` | Non-resendable group message |
| `sendTypingMessage` | `static List<SendMessageResult> sendTypingMessage(Context, GroupId.V2, List<Recipient>, SignalServiceTypingMessage, CancelationSignal)` | Typing indicator to group |
| `sendCallMessage` | `static List<SendMessageResult> sendCallMessage(Context, GroupId.V2, List<Recipient>, SignalServiceCallMessage)` | Call message to group |
| `sendStoryMessage` | `static List<SendMessageResult> sendStoryMessage(Context, DistributionListId, List<Recipient>, boolean, MessageId, long, SignalServiceStoryMessage, Set<SignalServiceStoryMessageRecipient>)` | Story to distribution list |
| `sendMessage` (private) | `static List<SendMessageResult> sendMessage(Context, GroupId.V2, DistributionId, MessageId, List<Recipient>, boolean, boolean, SendOperation, CancelationSignal)` | **Core** — orchestrates sender key vs legacy split, handles GSE refresh |

**Inner `SendOperation` interface**: `sendWithSenderKey()`, `sendLegacy()`, `getContentHint()`, `getSentTimestamp()`, `shouldIncludeInMessageLog()`, `getRelatedMessageId()`, `isUrgent()`

### 5.4 Conversation List

#### ConversationListViewModel.kt
**Path:** `app/src/main/java/org/thoughtcrime/securesms/conversationlist/ConversationListViewModel.kt`

| Method | Description |
|---|---|
| `onVisible()` | Notifies observers on subsequent views |
| `startSelection(conversation)` | Begins multi-select |
| `endSelection()` | Ends multi-select |
| `toggleConversationSelected(conversation)` | Toggles selection |
| `setFiltered(isFiltered, source)` | Sets unread filter |
| `select(chatFolder)` | Selects a chat folder |
| `onUpdateMute(chatFolder, until)` | Mutes all threads in a folder |
| `markChatFolderRead(chatFolder)` | Marks all threads in folder as read |
| `removeChatFromFolder(threadId)` | Removes thread from folder |
| `addToFolder(folderId, threadIds)` | Adds threads to a folder |

### 5.5 Conversation Data Pipeline (v2/data/)

| File | Purpose |
|---|---|
| `ConversationElements.kt` | Sealed interface for message elements (OutgoingTextOnly, OutgoingMedia, IncomingTextOnly, IncomingMedia, ConversationUpdate, ThreadHeader) |
| `MessageDataFetcher.kt` | Fetches extra data (mentions, reactions, attachments, payments, calls, polls) in parallel via `fetch()` |
| `ConversationDataSource.kt` | `PagedDataSource` — loads messages from DB, enriches with extra data, converts to mapping models |
| `AvatarDownloadStateCache.kt` | In-memory cache for avatar download states |

### 5.6 Conversation Item ViewHolders (v2/items/)

| File | Purpose |
|---|---|
| `V2ConversationItemTextOnlyViewHolder.kt` | Text message bubble — body text, sender name/photo, reactions, delivery status, expiration timer, footer |
| `V2ConversationItemMediaViewHolder.kt` | Media message bubble — extends TextOnly with thumbnail + quote view |
| `V2ConversationItemShape.kt` | Bubble shape calculator — SINGLE/START/MIDDLE/END clustering based on sender continuity |
| `V2ConversationItemTheme.kt` | Color provider for body/bubble/footer based on outgoing/incoming/wallpaper |
| `V2ConversationItemUtils.kt` | URL linkification utility using LinkifyCompat |
| `ChatColorsDrawable.kt` | Custom drawable for chat color gradients behind bubbles |
| `V2FooterPositionDelegate.kt` | Footer positioning: inline, underneath, or hidden based on jumbomoji/RTL/thumbnail |

---

## 6. Calls Layer

### 6.1 SignalCallManager.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/service/webrtc/SignalCallManager.java`
**Purpose:** Entry point for all calling. Lives for app lifetime. Implements CallManager.Observer, GroupCall.Observer.

| Method | Description |
|---|---|
| `process(ProcessAction)` | Routes action to service executor for state machine processing |
| `startPreJoinCall(Recipient)` | Enters pre-join call state |
| `startOutgoingAudioCall(Recipient)` | Initiates outgoing audio call |
| `startOutgoingVideoCall(Recipient)` | Initiates outgoing video call |
| `cancelPreJoin()` | Cancels pre-join call state |
| `setMuteAudio(boolean)` | Sets audio mute |
| `setEnableVideo(boolean)` | Sets video enable |
| `flipCamera()` | Flips camera |
| `acceptCall(boolean)` | Accepts incoming call with/without video |
| `denyCall()` | Denies incoming call |
| `localHangup()` | Initiates local hangup |
| `raiseHand(boolean)` | Toggles raise hand (group calls) |
| `react(String)` | Sends group call reaction |
| `receivedOffer(metadata, offer, receivedOfferMetadata)` | Processes received offer |
| `receivedAnswer(metadata, answer, receivedAnswerMetadata)` | Processes received answer |
| `receivedIceCandidates(metadata, List<byte[]>)` | Processes received ICE candidates |
| `receivedCallHangup(metadata, hangupMetadata)` | Processes received hangup |
| `setRingGroup(boolean)` | Sets group ring state |
| `selectAudioDevice(ChosenAudioDeviceIdentifier)` | Selects user-chosen audio device |
| `peekGroupCall(RecipientId)` | Peeks group call for active info |
| `peekCallLinkCall(RecipientId)` | Peeks call link for active info |
| `sendRemoteMuteRequest(CallParticipant)` | Sends remote mute request |
| `removeFromCallLink(CallParticipant)` | Removes participant from call link |
| `blockFromCallLink(CallParticipant)` | Blocks participant from call link |
| `onSendOffer(callId, remote, ..., offerBytes, mediaType)` | CallManager.Observer: send offer to peer |
| `onSendAnswer(callId, remote, ..., answerBytes)` | CallManager.Observer: send answer to peer |
| `onSendIceCandidates(callId, remote, ..., candidates)` | CallManager.Observer: send ICE candidates |
| `onSendHangup(callId, remote, ..., hangupType)` | CallManager.Observer: send hangup |
| `onStartCall(remote, callId, isOutgoing, mediaType)` | CallManager.Observer: call started |
| `onCallEnded(remote, callEndReason, summary)` | CallManager.Observer: call ended |
| `onGroupCallRingUpdate(..., ringUpdate)` | CallManager.Observer: group call ring update |
| `insertMissedCall(peer, timestamp, isVideoCall, event)` | Inserts missed call record |
| `retrieveTurnServers(peer)` | Retrieves TURN servers |
| `sendGroupCallUpdateMessage(recipient, eraId, callId, isCallFull, isRinging)` | Sends group call update message |
| `resendMediaKeys()` | Resends media keys |

### 6.2 WebRtcActionProcessor.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/service/webrtc/WebRtcActionProcessor.java`
**Purpose:** Core call state machine — subclassed per-state to handle actions differently. ~70+ handle* methods.

| Key Methods | Description |
|---|---|
| `handlePreJoinCall`, `handleOutgoingCall`, `handleStartOutgoingCall` | Outgoing call flow |
| `handleReceivedOffer`, `handleValidatedReceivedOffer`, `handleStartIncomingCall` | Incoming call flow |
| `handleAcceptCall`, `handleDenyCall`, `handleLocalHangup`, `handleRemoteHangup` | Call lifecycle |
| `handleSendOffer`, `handleSendAnswer`, `handleSendIceCandidates`, `handleReceivedIceCandidates` | WebRTC signaling |
| `handleSetMuteAudio`, `handleSetEnableVideo`, `handleSetCameraFlip` | Media controls |
| `handleCallConnected`, `handleCallReconnect`, `handleEndedRemote`, `handleSetupFailure` | Connection state |
| `handleGroupRequestMembershipProof`, `handleGroupRequestUpdateMembers`, `handleGroupCallEnded` | Group calling |
| `handleGroupCallReaction`, `handleGroupCallRaisedHand`, `handleSelfRaiseHand` | Group call interactions |
| `handleRemoteMuteRequest`, `handleObservedRemoteMute`, `handleSendRemoteMuteRequest` | Moderation |
| `handleReceivedOpaqueMessage`, `handleGroupCallRingUpdate`, `handleSetRingGroup` | Advanced |
| `handleSetCallLinkJoinRequestAccepted`, `handleRemoveFromCallLink`, `handleBlockFromCallLink` | Call links |

### 6.3 Call Log

#### CallLogViewModel.kt
| Method | Description |
|---|---|
| `markAllCallEventsRead()` | Marks all call events read |
| `selectAll()` / `toggleSelected(callId)` | Selection management |
| `stageCallDeletion(call)` / `stageSelectionDeletion()` / `stageDeleteAll()` | Stage deletion |
| `delete(stagedDeletion)` | Commits deletion |
| `setSearchQuery(query)` / `setFilter(filter)` | Search/filter |

### 6.4 Audio Management

#### SignalAudioManager.kt
| Method | Description |
|---|---|
| `create(context, eventListener, canUseTelecom)` | Factory |
| `handleCommand(command)` | Routes audio command |
| `initialize()` | Initializes audio state |
| `start()` / `stop(playDisconnect)` | Start/stop audio for call |
| `setDefaultAudioDevice(recipientId?, newDefaultDevice, clearSelection)` | Sets default audio device |
| `selectAudioDevice(recipientId?, device, isId)` | Selects user-chosen device |
| `startIncomingRinger(ringtoneUri?, vibrate)` | Starts incoming ringtone |
| `startOutgoingRinger()` | Starts outgoing ringtone |
| `silenceIncomingRinger()` | Silences incoming ringer |
| `setMicrophoneMute(on)` | Sets microphone mute |
| `setSpeakerphoneOn(on)` | Sets speakerphone state |

### 6.5 Call Links

#### SignalCallLinkManager.kt
| Method | Description |
|---|---|
| `requestCreateCallLinkCredentialPresentation(linkRootKey, roomId)` | Requests credential for creating call link |
| `requestCallLinkAuthCredentialPresentation(linkRootKey)` | Requests auth credential |
| `createCallLink(credentials)` | Creates call link on server |
| `readCallLink(credentials)` | Reads call link state |
| `updateCallLinkName(credentials, name)` | Updates call link name |
| `updateCallLinkRestrictions(credentials, restrictions)` | Updates join restrictions |
| `deleteCallLink(credentials)` | Deletes call link |

---

## 7. Groups Layer

### 7.1 GroupManagerV2.java

**Path:** `app/src/main/java/org/thoughtcrime/securesms/groups/GroupManagerV2.java`
**Purpose:** Manages all GV2 group operations (create, edit, join, leave, member management, settings).

| Method | Description |
|---|---|
| `getGroupJoinInfoFromServer(masterKey, linkPassword)` | Gets group join info from invite link |
| `create()` | Returns `GroupCreator` processing lock |
| `edit(groupId)` | Returns `GroupEditor` processing lock |
| `join(masterKey, linkPassword)` | Returns `GroupJoiner` processing lock |
| `cancelRequest(groupId)` | Returns cancel join request processing lock |
| `updater(masterKey)` | Returns `GroupUpdater` processing lock |
| `createGroupOnServer(params, name, avatar, members, timer)` | Creates group on server |

**GroupEditor inner class methods:**
| Method | Description |
|---|---|
| `addMembers(recipients, serviceIds)` | Adds members |
| `updateGroupTimer(seconds)` | Sets disappearing message timer |
| `updateAttributesRights(accessControl)` | Sets who can edit group info |
| `updateMembershipRights(accessControl)` | Sets who can add members |
| `updateAnnouncementGroup(boolean)` | Sets announcement-only mode |
| `updateGroupTitleDescriptionAndAvatar(title, description, avatar, removeAvatar)` | Updates group profile |
| `revokeInvites(serviceId, cipherTexts, isAdmin)` | Revokes pending invites |
| `approveRequests(recipients)` / `denyRequests(recipients)` | Handles join requests |
| `setMemberAdmin(recipient, boolean)` | Sets/removes admin |
| `terminateGroup()` | Terminates group |
| `leaveGroup(boolean)` | Leaves group |
| `ejectMember(aci, block, removeMessages, removeFromDistributionList)` | Ejects member with optional ban |
| `addMemberAdminsAndLeaveGroup(recipients)` | Promotes admins and leaves |
| `acceptInvite()` | Accepts pending invite |
| `ban(serviceId)` | Bans a user |
| `cycleGroupLinkPassword()` | Cycles group link password |
| `setJoinByGroupLinkState(GroupLinkState)` | Sets group link enabled state |
| `commitChangeWithConflictResolution(serviceId, actions)` | Commits change with conflict retry |

### 7.2 GroupsV2StateProcessor.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/groups/v2/GroupsV2StateProcessor.kt`
**Purpose:** Processes group state changes — updates local group to server revisions via P2P or server fetch.

| Method | Description |
|---|---|
| `forGroup(serviceIds, masterKey, secretParams?)` | Factory |
| `forceSanityUpdateFromServer(timestamp)` | Forces latest server state |
| `updateGroupSendEndorsements()` | Fetches and saves latest GSE |
| `updateLocalGroupToRevision(targetRevision, timestamp, signedGroupChange?, groupRecord?, serverGuid?)` | Updates to target revision |
| `updateViaPeerGroupChange(timestamp, serverGuid?, signedGroupChange, currentLocalState, forceApply)` | Applies P2P group change |
| `updateViaServer(targetRevision, timestamp, serverGuid?, groupRecord?)` | Fetches and applies server changes |
| `getGroupChangeLogs(localState?, logsNeededFromRevision, ...)` | Gets group change history |
| `saveGroupUpdate(timestamp, serverGuid?, groupStateDiff, groupSendEndorsements?, forceSave, persistProfileKeys)` | Saves group state update |

### 7.3 GroupManagementRepository.kt

| Method | Description |
|---|---|
| `addMembers(groupRecipient, selected, consumer)` | Adds members to group |
| `blockJoinRequests(groupId, recipient)` | Blocks join requests from recipient |
| `cancelJoinRequest(groupId)` | Cancels pending join request |
| `isJustSelf(groupId)` | Checks if only self is in group |

---

## 8. Navigation Layer

### 8.1 MainNavigation.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/main/MainNavigation.kt`
**Purpose:** Tab navigation bar/rail composables.

| Component | Description |
|---|---|
| `MainNavigationBar(state, onDestinationSelected)` | Bottom navigation bar with 4 tabs: CHATS, ARCHIVE, CALLS, STORIES |
| `MainNavigationRail(state, fabCallback, onDestinationSelected)` | Navigation rail for tablets/landscape |
| `NavigationDestinationIcon(destination, selected)` | Lottie animated icon per tab |
| `NavigationDestinationLabel(destination)` | Label text per tab |

### 8.2 MainNavigationDetailLocation.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/main/MainNavigationDetailLocation.kt`
**Purpose:** Sealed interface defining all detail pane destinations.

| Detail Location | Description |
|---|---|
| `Empty` | Root/empty detail pane |
| `Conversation(args)` | Open conversation |
| `CallLinkDetails(...)` | Call link detail |
| `Chats.MessageDetails(...)` | Message details within chats tab |
| `Chats.ConversationSettings(...)` | Conversation settings |
| `Calls.CallLinks.EditCallLinkName(...)` | Edit call link name |
| `Stories(...)` | Story subscreens |

### 8.3 MainNavigationRouter.kt

**Path:** `app/src/main/java/org/thoughtcrime/securesms/main/MainNavigationRouter.kt`

| Method | Description |
|---|---|
| `exitDetailLocation()` | Navigate to Empty detail |
| `goTo(location: MainNavigationListLocation)` | Navigate to list tab |
| `goTo(location: MainNavigationDetailLocation)` | Navigate to detail location |
| `goToChatDetail(location: Chats)` | Navigate to chat subscreen |
| `goToCallDetail(location: Calls)` | Navigate to calls subscreen |

### 8.4 MainNavigationViewModel.kt

| Method | Description |
|---|---|
| `goTo(location: MainNavigationDetailLocation)` | Navigates to detail location |
| `goToConversation(location: Conversation)` | Navigates to conversation with wallpaper prefetch |
| `goTo(location: MainNavigationListLocation)` | Navigates to list tab |
| `setFocusedPane(role: ThreePaneScaffoldRole)` | Sets focused pane for multi-pane |
| `setDetailLocation(location)` | Sets detail location internally |
| `onPaneAnchorChanged(isFullScreenPane)` | Updates full-screen pane state |
| `getNextMegaphone()` | Loads next megaphone to show |
| `onMegaphoneSnoozed(event)` / `onMegaphoneCompleted(event)` | Megaphone lifecycle |

---

## 9. Migration Pattern

Leading Apps' database schema evolution is a key pattern worth adopting for Enchant's Drift database.

### 9.1 Migration Files

**Path:** `app/src/main/java/org/thoughtcrime/securesms/database/helpers/migration/V*_*.kt`
**Pattern:** Sequential versioned migration files starting at V149 and going through V316+ (170+ migrations).

Each file implements `SignalDatabaseMigration`:
```kotlin
interface SignalDatabaseMigration {
  fun migrate(database: SQLiteDatabase)
}
```

Example: `V168_SingleMessageTableMigration.kt` — merges SMS and MMS tables into a single unified message table:
- Renames columns, creates new tables, copies data, drops old tables
- Handles indexes, triggers, and views
- All wrapped in proper SQLite transactions

### 9.2 Migration Orchestrator

**File:** `app/src/main/java/org/thoughtcrime/securesms/database/helpers/SignalDatabaseMigrations.kt`
**Purpose:** Central migration orchestrator that applies migrations sequentially by version.

| Method | Description |
|---|---|
| `MIGRATIONS` (companion) | `List<SignalDatabaseMigration>` — ordered list of all migrations from V149 to latest |
| `applyMigrations(database, currentVersion, newVersion)` | Iterates through migration list applying each in order |
| `SignalDatabaseMigration` (interface) | Single method: `fun migrate(database: SQLiteDatabase)` |

**Key pattern for your implementation:** Each migration is a separate file with a version number in the filename. The orchestrator applies them in sequence. Each migration handles its own transaction. This makes schema evolution:
- **Testable** — each migration can be unit tested independently
- **Reviewable** — each migration is a focused, small change
- **Safe** — sequential versioning prevents conflicts

---

---

## 10. Job Manager System

### 10.1 Overview

Leading Apps has a sophisticated **Job Manager** — the backbone for all async work. Every message send, decryption, attachment upload, profile fetch, and sync operation is modeled as a `Job`. The system handles scheduling, constraints, persistence, retries, and deduplication.

**Architecture:**
```
JobManager (singleton) → JobRunner → Job (individual work unit)
                                → Constraint (pre-conditions: network, battery, charging, etc.)
                                → Scheduler (alarm, in-app, WorkManager)
                                → JobStorage (persistence via SQLite)
```

### 10.2 Core Classes

#### Job.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/jobmanager/Job.java`

| Method | Signature | Description |
|---|---|---|
| `getParameters` | `Params getParameters()` | Returns job parameters (constraints, retry, lifetime) |
| `onRun` | `abstract Result onRun()` | Execute the job — return SUCCESS or FAILURE |
| `onShouldReschedule` | `abstract boolean onShouldReschedule(Exception)` | Whether job should be retried after failure |
| `onAdded` | `void onAdded()` | Called when job is first added to queue |
| `onRetry` | `void onRetry()` | Called before each retry attempt |
| `onCanceled` | `void onCanceled()` | Called when all retries exhausted |

**Inner class `Params`:**
| Field | Description |
|---|---|
| `queue` | String — queue name for serial execution |
| `maxAttempts` | Max retry count |
| `maxInstances` | Max simultaneous instances |
| `maxInstancesForFactory` | Max per-factory instances |
| `constraints` | List of constraint classes |
| `lifespan` | Time before job is auto-cancelled |
| `maxRunTime` | Max execution time before interrupt |

#### JobManager.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/jobmanager/JobManager.java`

| Method | Signature | Description |
|---|---|---|
| `add` | `void add(Job job)` | Add a single job to queue |
| `add` (batch) | `void add(List<Job> jobs)` | Add multiple jobs atomically |
| `schedule` | `void schedule()` | Run pending jobs (triggered by constraints being met) |
| `cancel` | `void cancel(Serializable consumerKey)` | Cancel all jobs for a consumer key |
| `getRunningJobCount` | `int getRunningJobCount()` | Current running job count |
| `getWaitingJobCount` | `int getWaitingJobCount()` | Current queued job count |
| `getJobStatus` | `List<JobStatus> getJobStatus()` | Debug: list all jobs with status |
| `onConstraintMet` | `void onConstraintMet(Class<? extends Constraint>)` | Wake up scheduler when constraint becomes satisfied |
| `addListener` | `void addListener(JobManager.Listeners)` | Listen for job lifecycle events |

#### Constraint.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/jobmanager/Constraint.java`

| Method | Signature | Description |
|---|---|---|
| `isMet` | `boolean isMet()` | Check if constraint is satisfied |
| `getFactoryKey` | `String getFactoryKey()` | Unique key for serialization |

**Built-in constraints:**
| Constraint | Purpose |
|---|---|
| `NetworkConstraint` | Device has internet connectivity |
| `InAppWebSocketConstraint` | App's WebSocket is connected |
| `DecryptionDrainedConstraint` | Message decryption queue is empty |
| `BatteryNotLowConstraint` | Battery above threshold |
| `Device充电Constraint` | Device is charging |

### 10.3 Concrete Job Examples (100+ in `jobs/`)

| Job | Purpose |
|---|---|
| `PushSendJob` | Send message via WebSocket (queued per conversation) |
| `PushGroupSendJob` | Send group message (fan-out) |
| `AttachmentDownloadJob` | Download encrypted attachment |
| `AttachmentUploadJob` | Upload encrypted attachment |
| `ProfileUploadJob` | Upload profile avatar |
| `MultiDeviceProfileKeyUpdateJob` | Sync profile key to linked devices |
| `StorageSyncJob` | Sync contacts/groups to storage service |
| `SendRetryReceiptJob` | Send retry receipt for decryption failure |
| `ResendMessageJob` | Resend message on retry request |
| `SenderKeyDistributionSendJob` | Send sender key distribution message |
| `LocalBackupJob` | Create local encrypted backup |
| `DirectoryRefreshJob` | Refresh contact directory |
| `RequestGroupInfoJob` | Fetch group info from server |
| `MultiDeviceContactUpdateJob` | Sync contact update to devices |
| `TrimThreadJob` | Trim old messages from thread |

### 10.4 Key Pattern: Queue-Based Serial Execution

Jobs with the same `queue` key run serially:
```kotlin
// All messages to the same conversation are queued
Params.Builder()
    .setQueue("ConversationSend_$conversationId")
    .setMaxAttempts(3)
    .setConstraints(NetworkConstraint::class.java)
    .build()
```

This ensures messages are sent in order without concurrent send conflicts.

---

## 11. Key-Value Store / SignalStore

### 11.1 Overview

Leading Apps' **Key-Value Store** is an encrypted SQLite-backed global settings store. Every feature area reads/writes its own `SignalStoreValues` subclass. It replaces SharedPreferences with encryption, type safety, and reactive observation.

**Architecture:**
```
SignalStore (singleton) → KeyValueStore (encrypted SQLite)
    ├── AccountValues — registration state, device ID, multi-device flag
    ├── BackupValues — backup settings, last backup timestamp
    ├── RegistrationValues — registration data, session ID
    ├── SvrValues — SVR credentials, backup status
    ├── StorageServiceValues — storage service state, manifest version
    ├── SettingsValues — notification settings, theme, font size
    ├── PinValues — PIN hash, PIN attempts remaining
    ├── PaymentsValues — payments state, currency
    ├── NotificationProfileValues — notification profiles
    ├── StoryValues — story settings, viewed stories
    ├── WallpaperValues — wallpaper settings per conversation
    ├── LabsValues — feature flags / labs experiments
    ├── InternalValues — internal/testing values
    ├── PhoneNumberPrivacyValues — phone number sharing settings
    ├── RemoteConfigValues — remote config flags
    ├── EmojiValues — recent emoji, emoji reactions
    ├── MiscellaneousValues — misc app state
    ├── ChatColorsValues — color preferences
    ├── CallQualityValues — call quality surveys
    ├── ProxyValues — proxy settings
    ├── RateLimitValues — rate limit tracking
    ├── OnboardingValues — onboarding completion state
    └── UiHintValues — UI hint display state
```

### 11.2 Core Files

#### KeyValueDatabase.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/keyvalue/KeyValueDatabase.java`

| Method | Signature | Description |
|---|---|---|
| `getOrCreateInstance` | `static KeyValueDatabase getOrCreateInstance(Context)` | Singleton accessor |
| `getReadableDatabase` | `SQLiteDatabase getReadableDatabase()` | Readable DB (encrypted) |
| `getWritableDatabase` | `SQLiteDatabase getWritableDatabase()` | Writable DB (encrypted) |
| `close` | `void close()` | Close the database |

#### SignalStore.kt
**Path:** `app/src/main/java/org/thoughtcrime/securesms/keyvalue/SignalStore.kt`

| Method | Signature | Description |
|---|---|---|
| `account` | `static AccountValues account()` | Registration/account values |
| `backup` | `static BackupValues backup()` | Backup settings |
| `registration` | `static RegistrationValues registration()` | Registration data |
| `svr` | `static SvrValues svr()` | SVR credentials |
| `storageService` | `static StorageServiceValues storageService()` | Storage service state |
| `settings` | `static SettingsValues settings()` | User settings |
| `pin` | `static PinValues pin()` | PIN values |
| `notificationProfiles` | `static NotificationProfileValues notificationProfiles()` | Notification profiles |
| `story` | `static StoryValues story()` | Story settings |
| `wallpaper` | `static WallpaperValues wallpaper()` | Wallpaper preferences |
| `labs` | `static LabsValues labs()` | Labs feature flags |
| `internal` | `static InternalValues internal()` | Internal testing values |
| `phoneNumberPrivacy` | `static PhoneNumberPrivacyValues phoneNumberPrivacy()` | Phone number privacy |
| `remoteConfig` | `static RemoteConfigValues remoteConfig()` | Remote config |
| `emoji` | `static EmojiValues emoji()` | Emoji preferences |
| `misc` | `static MiscellaneousValues misc()` | Miscellaneous |
| `chatColors` | `static ChatColorsValues chatColors()` | Chat colors |
| `callQuality` | `static CallQualityValues callQuality()` | Call quality surveys |
| `proxy` | `static ProxyValues proxy()` | Proxy settings |
| `rateLimit` | `static RateLimitValues rateLimit()` | Rate limits |
| `onboarding` | `static OnboardingValues onboarding()` | Onboarding state |

Each `Values` class follows the pattern:
```kotlin
class AccountValues : SignalStoreValues {
    fun isRegistered(): Boolean = getBoolean(REGISTERED, false)
    fun setRegistered(registered: Boolean) = putBoolean(REGISTERED, registered)
    fun getUserId(): String? = getString(USER_ID, null)
    fun setUserId(id: String) = putString(USER_ID, id)
    // ... typed getters/setters for each key
}
```

### 11.3 Key Pattern for Your Implementation

Implement an encrypted key-value store with:
- SQLCipher for persistence (same as main DB but separate file)
- Typed accessor classes per feature domain
- Reactive observation (callbacks on value changes)
- Migration support for schema evolution
- Thread-safe reads/writes (single writer, many readers)

---

## 12. Storage Service (Cloud Sync)

### 12.1 Overview

Leading Apps' **Storage Service** provides encrypted cloud sync for multi-device support. It syncs contacts, groups, accounts, call links, notification profiles, story distribution lists, and chat folders across devices using a manifest-based protocol.

**Architecture:**
```
StorageService → StorageSyncHelper → StorageRecordProcessor<RecordType>
    ├── ContactRecordProcessor — sync contacts
    ├── GroupV2RecordProcessor — sync group state
    ├── AccountRecordProcessor — sync account settings
    ├── CallLinkRecordProcessor — sync call links
    ├── NotificationProfileRecordProcessor — sync notification profiles
    └── StoryDistributionListRecordProcessor — sync story lists
```

### 12.2 Core Files

#### StorageSyncHelper.kt
**Path:** `app/src/main/java/org/thoughtcrime/securesms/storage/StorageSyncHelper.kt`

| Method | Signature | Description |
|---|---|---|
| `syncData` | `void syncData()` | Trigger full storage sync |
| `syncDataIfNeeded` | `void syncDataIfNeeded()` | Sync only if manifest changed |
| `syncDataForced` | `void syncDataForced()` | Force full re-sync |
| `processStorageSyncData` | `void processStorageSyncData(List<StorageItem>, Manifest)` | Process incoming sync data |
| `buildStorageIdForGroup` | `byte[] buildStorageIdForGroup(GroupId)` | Compute stable storage ID |
| `buildStorageIdForContact` | `byte[] buildStorageIdForContact(RecipientId)` | Compute stable storage ID |
| `getLocalManifest` | `Manifest getLocalManifest()` | Get current local manifest |
| `getRemoteManifestVersion` | `long getRemoteManifestVersion()` | Get remote manifest version |

#### StorageRecordProcessor.kt
**Path:** `app/src/main/java/org/thoughtcrime/securesms/storage/StorageRecordProcessor.kt`

| Method | Signature | Description |
|---|---|---|
| `process` | `void process(List<StorageItem> remoteItems, StorageSyncHelper helper)` | Process remote items: insert, update, delete |
| `getComparator` | `Comparator getComparator()` | Comparison for conflict resolution |
| `getValidRecords` | `List<StorageRecord> getValidRecords()` | Locally valid records for upload |

### 12.3 Key Pattern: Manifest-Based Sync

```
1. Fetch remote manifest (version + digest)
2. Compare with local manifest version
3. If ahead: fetch remote items → process (insert/update/delete)
4. If behind: build local items → upload to server
5. Conflict resolution via custom comparator per record type
```

---

## 13. User-Facing Notification System

### 13.1 Overview

Leading Apps' notification system manages all user-facing notifications (system tray) with reply actions, mark-as-read actions, message grouping, notification channels, and Android 12+ notification profiles with time schedules.

### 13.2 Core Files

#### MessageNotifier.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/notifications/MessageNotifier.java`

| Method | Signature | Description |
|---|---|---|
| `updateNotification` | `void updateNotification(Context)` | Update notification for new message |
| `updateNotification` | `void updateNotification(Context, long threadId)` | Update for specific thread |
| `updateNotification` | `void updateNotification(Context, boolean notify, long threadId)` | With notification flag |
| `cancelDelayedNotifications` | `void cancelDelayedNotifications()` | Cancel pending scheduled notifications |
| `setPendingIntent` | `void setPendingIntent(PendingIntent)` | Set the conversation open intent |
| `getPendingNotification` | `NotificationState getPendingNotification()` | Get current pending notification |
| `removeStickyConversation` | `void removeStickyConversation(Context, long threadId)` | Remove thread from persistent group summary |

#### NotificationBuilder.kt
**Path:** `app/src/main/java/org/thoughtcrime/securesms/notifications/NotificationBuilder.kt`

| Method | Signature | Description |
|---|---|---|
| `buildNotification` | `Notification buildNotification(Context, NotificationState, NotificationChannel)` | Build full notification with actions |
| `buildSummaryNotification` | `Notification buildSummaryNotification(Context, List<NotificationState>)` | Build grouped summary notification |
| `buildReplyAction` | `Notification.Action buildReplyAction(PendingIntent)` | Build inline reply action |
| `buildMarkAsReadAction` | `Notification.Action buildMarkAsReadAction(PendingIntent)` | Build mark-as-read action |

#### NotificationChannels.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/notifications/NotificationChannels.java`

| Channel | Description |
|---|---|
| `MESSAGES` | Default message notifications |
| `MESSAGES_SILENT` | Silent message notifications (no sound) |
| `CALLS` | Call notifications |
| `VOICE` | Voice message notifications |
| `LOBBY` | Ongoing call/active lobby |

#### Notification Profiles (Android 12+)
**Path:** `app/src/main/java/org/thoughtcrime/securesms/notifications/profiles/`

| Class | Description |
|---|---|
| `NotificationProfile` | Profile with name, emoji icon, schedule, allowed contacts |
| `NotificationProfileSchedule` | Time schedule (start/end, days of week) |
| `NotificationProfileId` | Unique profile identifier |

### 13.3 Key Pattern: Optimized Grouped Notifications

Leading Apps uses `OptimizedMessageNotifier` which:
1. Batches notifications on a background thread
2. Groups messages by conversation thread
3. Shows summary notification when multiple threads have messages
4. Provides inline reply and mark-as-read actions per notification
5. Uses Android 7+ direct reply and notification groups

---

## 14. FCM Push Infrastructure

### 14.1 Overview

Leading Apps uses FCM purely as a wake-up signal, not for message delivery. When FCM arrives, the app reconnects the WebSocket and fetches pending messages. This design avoids message delivery delays and ensures end-to-end encryption is never bypassed.

**Flow:**
```
FCM push arrives → FcmReceiveService → FcmFetchManager → 
    → If app in foreground: reconnect WebSocket immediately
    → If app in background: start FcmFetchForegroundService → reconnect WebSocket
    → Messages fetched via WebSocket → decrypted → stored → notified
```

### 14.2 Core Files

#### FcmReceiveService.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/gcm/FcmReceiveService.java`

| Method | Signature | Description |
|---|---|---|
| `onMessageReceived` | `void onMessageReceived(RemoteMessage)` | Handle incoming FCM message |
| `onNewToken` | `void onNewToken(String)` | Handle FCM token refresh |
| `onDeletedMessages` | `void onDeletedMessages()` | Handle server message deletion (throttling) |

#### FcmFetchManager.kt
**Path:** `app/src/main/java/org/thoughtcrime/securesms/gcm/FcmFetchManager.kt`

| Method | Signature | Description |
|---|---|---|
| `onFcmReceived` | `fun onFcmReceived(Context)` | Process FCM: decide foreground vs background fetch |
| `scheduleFetch` | `fun scheduleFetch(Context)` | Schedule an immediate fetch |
| `cancelFetch` | `fun cancelFetch()` | Cancel any pending fetch |
| `isFetchScheduled` | `fun isFetchScheduled(): Boolean` | Check if fetch is pending |
| `notifyFcmRetryReceived` | `fun notifyFcmRetryReceived(Context)` | Handle FCM retry signal |

#### FcmUtil.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/gcm/FcmUtil.java`

| Method | Signature | Description |
|---|---|---|
| `getToken` | `static String getToken()` | Get current FCM token |
| `isGcmPresent` | `static boolean isGcmPresent()` | Check if Google Play Services available |
| `register` | `static void register(Context)` | Register for FCM / get token |
| `unregister` | `static void unregister(Context)` | Unregister from FCM |

### 14.3 Key Pattern: FCM as Wake-Up Only

FCM never contains message payload. It only signals the app to reconnect WebSocket. This means:
- Zero message content exposed to Google Play Services
- Message delivery is not delayed by FCM throttling
- WebSocket provides reliable ordered delivery with ACKs
- Fallback to periodic polling if FCM unavailable

---

## 15. Backup / Restore System

### 15.1 Overview

Leading Apps' backup/restore system provides encrypted end-to-end backup and restore. The v2 system supports local file backups and remote (CDN) backups. It archives all message data, media, and settings.

**Backup structure:**
```
Backup (encrypted ZIP-like archive)
├── ChatArchive (messages for one conversation)
├── ContactArchive (contact data)
├── GroupArchive (group data)
├── AdHocCallArchive (call records)
├── DistributionListArchive (story distribution lists)
├── CallLinkArchive (call links)
└── Media attachments (encrypted per-file)
```

### 15.2 Core Files

#### BackupRepository.kt
**Path:** `app/src/main/java/org/thoughtcrime/securesms/backup/BackupRepository.kt`

| Method | Signature | Description |
|---|---|---|
| `initiateBackup` | `fun initiateBackup(): Single<BackupInitResult>` | Start backup process |
| `getBackupStatus` | `fun getBackupStatus(): Single<BackupStatus>` | Get current backup status |
| `getBackupInfo` | `fun getBackupInfo(): Single<BackupInfo>` | Get backup metadata |
| `deleteBackup` | `fun deleteBackup(): Completable` | Delete all backup data |
| `getBackupKey` | `fun getBackupKey(): BackupKey` | Get derived backup encryption key |

#### FullBackupExporter.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/backup/FullBackupExporter.java`

| Method | Signature | Description |
|---|---|---|
| `export` | `static void export(Context, File output, BackupKey)` | Full backup export to file |
| `exportToStream` | `static void exportToStream(Context, OutputStream, BackupKey)` | Export to stream (e.g., for cloud upload) |

#### FullBackupImporter.java
**Path:** `app/src/main/java/org/thoughtcrime/securesms/backup/FullBackupImporter.java`

| Method | Signature | Description |
|---|---|---|
| `import` | `static void import(Context, File input, BackupKey)` | Full backup import from file |
| `importFromStream` | `static void importFromStream(Context, InputStream, BackupKey)` | Import from stream |
| `getBackupInfo` | `static BackupInfo getBackupInfo(File, BackupKey)` | Get backup metadata without importing |

### 15.3 Archive Exporters/Importers

| File | Purpose |
|---|---|
| `ChatArchiveExporter.kt` / `ChatArchiveImporter.kt` | Backup/restore message history |
| `ContactArchiveExporter.kt` / `ContactArchiveImporter.kt` | Backup/restore contacts |
| `GroupArchiveExporter.kt` / `GroupArchiveImporter.kt` | Backup/restore groups |
| `AdHocCallArchiveExporter.kt` / `AdHocCallArchiveImporter.kt` | Backup/restore call history |
| `DistributionListArchiveExporter.kt` / `DistributionListArchiveImporter.kt` | Backup/restore story distribution lists |
| `CallLinkArchiveExporter.kt` / `CallLinkArchiveImporter.kt` | Backup/restore call links |

### 15.4 Backup-Related Jobs

| Job | Purpose |
|---|---|
| `BackupMessagesJob.kt` | Periodically back up messages |
| `BackupDeleteJob.kt` | Delete backup data from CDN |
| `LocalBackupJob.java` | Create local encrypted backup file |
| `CopyAttachmentToArchiveJob.kt` | Move attachment to backup archive |
| `UploadAttachmentToArchiveJob.kt` | Upload attachment to CDN backup |

### 15.5 Key Pattern: Encrypted Archive Format

The backup uses an encrypted archive format:
1. Generate random backup key on first setup
2. Derive encryption keys via HKDF from backup key
3. Each message batch is encrypted with XChaCha20-Poly1305
4. Media files are encrypted individually
5. Manifest file lists all archived conversations
6. Integrity verification via HMAC after restore

---

> **End of Reference Map — v1.0**
> Total files documented: ~150+ across all areas
> Total methods documented: ~1,500+

