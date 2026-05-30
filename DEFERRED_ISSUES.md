# Deferred Issues

Issues identified during audits that are deferred — either because they require
larger design decisions, are feature requests rather than bugs, or need cross-module
coordination. Each item includes the module, audit reference, and why it's deferred.

---

## core:jobmanager

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| S-1 | Job data (task names, params) stored as plaintext in SQLite | Requires Android Keystore integration — architectural change, not a bug fix |
| S-2 | JobLogger caller audit (no callers in this module) | Can't audit without touching every module that uses JobLogger |
| S-3 | Access control on `JobManager.initialize()` | Would break public API — needs design decision on init pattern |
| S-6 | `MinimalJobSpec` equality ignores `serializedData` | Intentional design for deduplication — changing would break existing behavior |
| C-1 | Missing job types (MessageSendWorker, AttachmentDownloadWorker, etc.) | Feature request — needs implementation of each worker |
| C-2 | WorkManager integration | Feature request — requires migration strategy from current JobManager |
| C-3 | Job deduplication | Feature request — needs dedup key design |
| C-4 | Cleanup of old job history | Feature request — needs retention policy decision |
| Q-1 | DI framework for JobManager | Refactor — needs framework choice and migration |
| Q-2 | Thread → Coroutine migration in JobRunner | Refactor — high risk, needs careful testing |
| Q-3 | DRY extraction of storage operations | Refactor — needs interface design |
| Q-7 | `transformJobs` edge case with empty list | Low priority — callers always pass non-empty lists |

## core:notifications

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Icons | Hardcoded system drawables (`android.R.drawable.ic_dialog_info`) | Needs custom icon assets designed — cosmetic, not functional |
| Test coverage | Only channel constants tested, not notification building | Needs Robolectric or instrumentation test setup |
| Group differentiation | Group/mention notifications not fully differentiated from DMs | Channel constants added; feature modules need to use them when calling `onMessageReceived` |

## core:auth

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| S-1/S-2 | JWT integrity verification (HMAC or JWKS) | Needs server JWKS endpoint support — can't implement client-side alone |
| C-1 | Missing `/v1/accounts/whoami` endpoint | Backend not implemented yet |
| C-2 | Username reservation/confirmation | Backend not implemented yet |
| C-3 | Registration lock (2FA/PIN) | Backend not implemented yet |
| C-4 | Phone number change flow | Backend not implemented yet |
| C-5 | FCM token management | Backend not implemented yet |
| C-6 | Concurrent device linking | Multi-device feature — needs design |
| C-7 | `checkRepeatedUseKeys()` for prekey reuse detection | Needs server-side key state tracking |
| C-8 | Kyber (post-quantum) prekey support | Needs crypto library integration |
| C-9 | SVR (Secure Value Recovery) integration | PIN-based backup — needs server support |

## core:base

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Bootstrap | No bootstrap coordinator for init order (SecurePreferences → Log → AppConfig → KeyStoreManager) | Design/feature request — init sequence is caller responsibility |

## core:database

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Passphrase | Database passphrase memory handling | Design decision requiring backend coordination |
| Cert pinning | Certificate pinning for external storage backup | Feature request for backup services |
| Schema | Missing tables/DAOs (reactions, mentions, drafts, attachments, pre-keys, sender-keys, etc.) | Feature requests requiring schema design |
| String literals | Hardcoded table/column name strings | Large refactoring with high risk of regressions |
| Return types | Inconsistent return types across DAOs | Cosmetic, low priority |

## core:push

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Validation | Payload validation | Needs backend-defined schema |
| Dedup | Duplicate message detection | Needs message ID cache/dedup service |
| Group/call | Call/group notification handling | Feature request, not a bug |
| Encryption | FCM token encryption at rest | Depends on `SecurePreferences` in core:base |
| Lifecycle | Lifecycle awareness | Needs `ProcessLifecycleOwner` integration |
| Architecture | Static singleton refactoring | Architecture change |

## core:store

All items were confirmed correct by the audit itself (not bugs). No deferred items.

## core:performance

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Cache encryption | ImagePipeline and MessageCache encryption at rest | Feature request, needs design decision |
| Disk cache bounds | No limits on Coil disk cache file count | Feature request for Coil configuration |
| Cache reporting | No cache size reporting | Feature request |
| MessageCache TTL | No time-based expiry for cached messages | Feature request |
| Watermark trimming | No high-watermark eviction for MessageCache | Feature request |
| Dry-run mode | No dry-run for MessageTrimmer | Feature request |
| Battery throttle | No throttle/battery awareness beyond battery-not-low | Feature request |

## core:network

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| TLS enforcement | No TLS 1.2/1.3 enforcement on OkHttpClient | Requires `Tls12SocketFactory`, `ConnectionSpec.RESTRICTED_TLS` — new dependency |
| Certificate pinning | No cert pinning on gateway | Requires `CertificatePinner` config — infrastructure setup |
| WS TLS | No explicit TLS check for WebSocket | `wsUrl` derived from `gatewayUrl` via `AppConfig.deriveWsUrl()` — partially handled |
| JWT system time | JWT expiry uses device system time | Needs server time endpoint — backend dependency |
| WS delivery confirm | sendMessage is fire-and-forget | Feature request for delivery/read receipt confirmation |
| OfflineQueue drop | OfflineQueue drain() silently drops messages | Feature request for error propagation |
| Connection health | No connection health monitoring | Feature request — needs new `HealthMonitor` component |
| WS batch reading | No WebSocket batch message reading | Feature request — new `readMessageBatch()` API |
| Request cancellation | No request cancellation support | Feature request — needs coroutine cancellation integration |
| Message priority | No message priority queue | Feature request — new queue architecture |

---

*Last updated: 2026-05-30*
