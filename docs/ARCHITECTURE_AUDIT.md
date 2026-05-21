# Enchant Native — Complete Architecture Audit & Gap Analysis

## Date: 2026-05-21
## Scope: All 17 core modules compared against Signal Android

---

## 1. Module Status Overview

| Module | Status | Source Files | Tests | Architecture Pattern |
|--------|--------|-------------|-------|---------------------|
| **store/** | Rewritten | 40 | 2 | Singleton + Namespaces + Delegates |
| **accessibility/** | Rewritten | 7 | 7 | Singleton + Extensions |
| **auth/** | Rewritten | 3 | 5 | Repository + State Machine |
| **base/** | Rewritten | 28 | 20 | Mixed (Singletons, Utilities, Providers) |
| **calls/** | Rewritten | 39 | 4 | DI Factory + State Machine + Action Processor |
| **config/** | Rewritten | 1 | 1 | Singleton |
| **crash/** | Rewritten | 1 | 0 | Singleton |
| **crypto/** | Rewritten | 15 | 15 | Singleton + Store Abstraction |
| **database/** | Rewritten | 20 | 5 | Connection Pool + DAO |
| **jobmanager/** | Rewritten | 2 | 2 | Singleton + Handler Registry |
| **model/** | Rewritten | 1 | 0 | Pure Data Classes |
| **navigation/** | Rewritten | 2 | 1 | Sealed Class Hierarchy |
| **network/** | Rewritten | 8 | 4 | Singleton + Companion Factory |
| **notifications/** | Rewritten | 6 | 1 | Singleton + Queue/Debounce |
| **performance/** | Rewritten | 4 | 2 | Singleton + Worker |
| **protos/** | Rewritten | 15 (.proto) | 0 | Protobuf Generation |
| **push/** | Rewritten | 5 | 0 | Firebase Service |

**All 17 modules are freshly rewritten.** Zero legacy code remains. The dead `signalstore/` folder was deleted and replaced by the new `store/` module.

---

## 2. Where We Match Signal (Equivalent or Better)

### 2.1 Store/KeyValue — EQUIVALENT

| Aspect | Signal | Enchant | Notes |
|--------|--------|---------|-------|
| Central singleton | `SignalStore` | `EnchantStore` | Same pattern |
| Namespace classes | 28 | 30 | We have 2 more (Miscellaneous, ApkUpdate) |
| Encrypted SQLite | SQLCipher (separate DB) | SQLCipher (same DB) | Equivalent |
| In-memory cache | ConcurrentHashMap write-through | ConcurrentHashMap write-through | Same |
| Kotlin delegates | `SignalStoreValueDelegate` | `StoreValueDelegate` | Same pattern |
| Flow reactivity | `MutableStateFlow` per delegate | `MutableStateFlow` per key | Same |
| Proto/enum delegates | `protoValue()`, `enumValue()` | `protoValue()`, `enumValue()` | Matched |
| Precondition delegates | `.withPrecondition { }` | `.withPrecondition { }` | Matched |
| Mapping delegates | `.map { }` | `.map { }` | Matched |
| Plaintext escape hatch | `PlainTextSharedPrefsDataStore` | `PlainTextSharedPrefsDataStore` | Matched |
| Crash-safe flush | `blockUntilAllWritesFinished()` | `blockUntilAllWritesFinished()` + `EnchantCrashHandler` | **Better** — we have crash handler installed |
| Post-backup cache reset | `onPostBackupRestore()` | `onPostBackupRestore()` | Matched |
| PreferenceDataStore bridge | `SignalPreferenceDataStore` | `EnchantPreferenceDataStore` | Matched |
| Versioned migrations | `ApplicationMigrations.java` (v157) | `ApplicationMigrations` | Matched |

### 2.2 Accessibility — BETTER

| Aspect | Signal | Enchant | Notes |
|--------|--------|---------|-------|
| Files | 1 (`AccessibilityUtil.java`) | 7 | We have 6x more coverage |
| Features | Animation check only | Delegate, focus traversal, live region, RTL, actions provider | **Better** — comprehensive |
| Architecture | Static utility | Singleton + Extensions + Delegate | **Better** — more composable |

### 2.3 Notifications — EQUIVALENT

| Aspect | Signal | Enchant | Notes |
|--------|--------|---------|-------|
| Interface | `MessageNotifier` | `MessageNotifier` | Same pattern |
| Optimized notifier | `OptimizedMessageNotifier` | `OptimizedMessageNotifier` | Same |
| Batching | Debounce-based | Debounce-based (50ms) | Same |
| Channels | `NotificationChannels` | `NotificationChannels` | Same |

### 2.4 Push/FCM — EQUIVALENT

| Aspect | Signal | Enchant | Notes |
|--------|--------|---------|-------|
| FCM manager | `FcmFetchManager` | `FcmFetchManager` | Same |
| Foreground service | `FcmFetchForegroundService` | `FcmFetchForegroundService` | Same |
| Huawei fallback | Yes | Yes | Same |
| Token registrar | `FcmUtil` | `PushTokenRegistrar` | Same |

### 2.5 Base Utilities — EQUIVALENT SCOPE

| Aspect | Signal | Enchant | Notes |
|--------|--------|---------|-------|
| Files | 172 in `util/` | 28 in `base/` | Signal has more files |
| Security hardening | Standard | 30+ audited fixes (H1-H30, C1-C3) | **Better** — deeper security |
| Concurrency | Basic | ANR detector, deadlock detector, keyed serial executor | **Better** — more sophisticated |
| Logging | Basic | Compound logger hierarchy with scrubber | **Better** — more structured |

---

## 3. Where Signal Is More Sophisticated

### 3.1 Network — GAP: HIGH

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| Domain fronting | Country-specific CDN routing | None | Censorship circumvention |
| Custom DNS fallback | System → Cloudflare → Static IPs | System DNS only | Resilience |
| TLS spec per host | Custom connection specs | Default OkHttp | Security |
| Remote deprecation detector | Server-forced client updates | None | Security |
| Per-country proxy configs | Egypt, Iran, Cuba, etc. | None | Censorship circumvention |
| Device transfer blocking | Interceptor during migration | None | Data integrity |

### 3.2 JobManager — GAP: CRITICAL

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| Job chains | `.then()` with parallel/sequential segments | None | Ordered operations |
| Constraints | 18 built-in (Network, Charging, WiFi, etc.) | None | Conditional execution |
| Multi-scheduler | InApp + JobScheduler + AlarmManager | Single coroutine loop | Persistence across app death |
| Job implementations | 184 specialized jobs | 1 (DisappearingMessagesWorker) | Feature completeness |
| SQLite persistence | Full DB with LRU cache, eligibility sorting | SecurePreferences (key-value) | Scalability, reliability |
| Reserved runners | Dedicated threads per job type | Single thread | Performance isolation |
| Cascading failure | BFS through dependency graph | None | Error propagation |
| Dual priority | Global + Queue priority | None | Execution ordering |
| Dynamic runner scaling | 4-16 runners based on load | Fixed single runner | Throughput |
| Job migration | Versioned (v1-v12) with sequential migrations | None | Schema evolution |
| runSynchronously | Blocking with timeout + listener | None | Synchronous operations |
| EmptyQueueListener | Debounced callback for queue drained | None | Lifecycle management |
| JsonJobData | Type-safe serialization (12 types) | Pipe-delimited strings | Reliability |
| WakeLock | PARTIAL_WAKE_LOCK per job (10min timeout) | None | Prevents sleep during work |

### 3.3 Calls — GAP: HIGH

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| WebRTC engine | RingRTC (custom fork) | Standard WebRTC | Call quality |
| Call quality survey | Built-in survey system | None | UX |
| Call links | Join-by-link without contact | None | Feature |
| Group call ringing | Size-limited ringing | None | UX |
| Telecom API integration | System-level call handling | None | OS integration |
| Media encryption | DTLS/SRTP only | Triple Ratchet (planned) | **We will be better** |

### 3.4 Auth/Registration — GAP: HIGH

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| Session-based flow | Create session → verify → register | Direct OTP → JWT | Security |
| SVR backup/restore | Secure Value Recovery for PIN | None | Account recovery |
| Device transfer QR | ProvisioningSocket for linked devices | None | Multi-device |
| Push challenge | Bot prevention during registration | None | Security |
| Captcha | reCAPTCHA integration | None | Bot prevention |
| Registration lock | PIN-based account protection | None | Account security |
| Typed error handling | `RequestResult<T, E>` | Basic error types | Developer experience |

### 3.5 Crypto — GAP: MEDIUM

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| ACI/PNI dual identity | Separate identity key stores | Single identity | Privacy |
| Kyber pre-keys | Post-quantum KEM support | None (planned) | Future-proofing |
| ReentrantSessionLock | Thread-safe session operations | Mutex-protected | Thread safety |
| Sealed sender access | `SealedSenderAccessUtil` | Basic sealed sender | Privacy |
| Key rotation jobs | `RotateCertificateJob`, `CleanPreKeysJob` | None | Key hygiene |

### 3.6 Navigation — GAP: MEDIUM

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| Three-pane adaptive | Material 3 Adaptive (list/detail/settings) | Standard Compose Nav | Tablet/foldable UX |
| DB observer navigation | `MainNavigationRepository` with reactive DB | Static routes | Real-time updates |
| Pane focus management | `ThreePaneScaffoldRole` | None | UX polish |
| Early navigation | Handle nav before navigator ready | None | Performance |
| Wallpaper prefetch | Prefetch during navigation | None | Performance |

### 3.7 Remote Config — GAP: MEDIUM

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| Hot-swap flags | Runtime updates without restart | None | Flexibility |
| Sticky flags | Once-true-always-true | None | Feature rollout |
| Active flags | Server value ignored, default only | None | Testing |
| Change listeners | React to config changes | None | Reactivity |
| Type-safe delegates | `remoteBoolean`, `remoteInt`, etc. | Basic string parsing | Type safety |
| Fetch throttling | 2-hour interval | None | Server load |

### 3.8 Crash — GAP: MEDIUM

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| Remote-config crash patterns | Server decides which crashes to prompt | Basic handler | UX |
| Crash database | Local crash storage/querying | None | Debugging |
| Shake-to-report | Shake gesture to submit report | None | User feedback |
| Percentile rollout | Per-pattern rollout percentage | None | Gradual rollout |

### 3.9 Database — GAP: MEDIUM

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| DatabaseObserver | Granular listeners per table/type | Manual Flow emission | Reactivity |
| Post-commit hooks | `runPostSuccessfulTransaction` | None | Cascading updates |
| Cross-table references | `RecipientIdDatabaseReference` for ID remapping | None | Data integrity |
| FTS5 auto-sync | Triggers for automatic FTS sync | Manual FTS updates | Search accuracy |

### 3.10 Models — GAP: MEDIUM

| Feature | Signal | Enchant | Impact |
|---------|--------|---------|--------|
| Three-layer model | DB record → domain model → live wrapper | Flat data classes | Reactivity |
| Type-safe IDs | `RecipientId`, `ThreadId`, `MessageId` | Raw Long/String | Compile-time safety |
| Computed properties | `isOutgoing`, `isFailed`, `isPending` | Manual checks | Developer experience |
| Live recipient cache | `Recipient` wrapper with lazy resolution | None | Performance |

---

## 4. Where We're Ahead of Signal

| Area | Enchant | Signal | Advantage |
|------|---------|--------|-----------|
| **Accessibility** | 7 dedicated files with delegate pattern | 1 file (animation check) | 7x more coverage |
| **Security Hardening** | 30+ audited fixes (H1-H30, C1-C3) | Standard Android security | Deeper security |
| **libenchantcrypto** | Cross-platform C++ library (6 platforms) | Java-only libsignal | Broader platform support |
| **AI Agent E2EE** | First-class agent identity keys, sessions | No agent support | Unique feature |
| **Hash-chained conversations** | Tamper-evident logs per conversation | No hash chaining | Audit capability |
| **Post-Quantum Roadmap** | Designed for Triple Ratchet migration | Has Kyber but not Triple Ratchet | Future-proof design |
| **libenchantcall** | Triple Ratchet media encryption (planned) | DTLS/SRTP only | Better call encryption |
| **Agent payments** | Structured payment intents with policy engine | No agent payments | Unique feature |

---

## 5. Priority Gap Remediation Plan

| Priority | Module | Gap | Effort | Impact |
|----------|--------|-----|--------|--------|
| **P0** | jobmanager/ | Job chains, constraints, multi-scheduler, SQLite persistence | High | Critical — ordered operations |
| **P1** | network/ | Remote deprecation detector, DNS fallback | Medium | High — security |
| **P1** | crypto/ | ACI/PNI dual identity, key rotation jobs | Medium | High — privacy |
| **P2** | store/ | Remote config delegates (hot-swap, sticky, active) | Medium | Medium — flexibility |
| **P2** | database/ | DatabaseObserver system | Medium | Medium — reactivity |
| **P2** | model/ | Type-safe IDs, three-layer models | Medium | Medium — safety |
| **P3** | navigation/ | Three-pane adaptive, DB observer nav | High | Medium — tablet UX |
| **P3** | crash/ | Crash database, shake-to-report | Low | Low — debugging |
| **P3** | auth/ | SVR integration, device transfer | High | Medium — account recovery |

---

## 6. Test Coverage Analysis

| Module | Source Files | Test Files | Ratio | Assessment |
|--------|-------------|-----------|-------|------------|
| accessibility/ | 7 | 7 | 1:1 | Excellent |
| crypto/ | 15 | 15 | 1:1 | Excellent |
| base/ | 28 | 20 | 1:1.4 | Good |
| auth/ | 3 | 5 | 1.7:1 | Good |
| network/ | 8 | 4 | 2:1 | Adequate |
| database/ | 20 | 5 | 4:1 | Thin |
| calls/ | 39 | 4 | 10:1 | Thin |
| store/ | 40 | 2 | 20:1 | Thin |
| notifications/ | 6 | 1 | 6:1 | Thin |
| performance/ | 4 | 2 | 2:1 | Adequate |
| jobmanager/ | 2 | 2 | 1:1 | Good (but module is too small) |
| navigation/ | 2 | 1 | 2:1 | Adequate |
| config/ | 1 | 1 | 1:1 | Adequate |
| model/ | 1 | 0 | - | Missing |
| crash/ | 1 | 0 | - | Missing |
| push/ | 5 | 0 | - | Missing |
| protos/ | 15 (.proto) | 0 | - | N/A (generated) |

**Modules needing more tests (priority order):** store (40 files, 2 tests), calls (39 files, 4 tests), push (5 files, 0 tests), crash (1 file, 0 tests), model (1 file, 0 tests), notifications (6 files, 1 test).
