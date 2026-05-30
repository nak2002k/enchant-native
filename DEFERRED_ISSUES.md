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

## core:navigation

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Deep links | Deep link action verification | Architectural limitation, requires navigation component changes |
| Bundle safety | Bundle type safety (no compile-time checks) | Needs Safe Args plugin — feature request |
| Safe Args | Safe Args integration | Feature request |
| Nested nav | Nested navigation graph support | Feature request |
| Back stack | Back stack management utilities | Feature request |
| Arg validation | Argument validation helpers | Feature request |
| Deep link utils | Deep link handling utilities | Feature request |

## core:ui

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| RTL | RTL support in TransitionSpecs | Feature enhancement, not a bug |
| Reduced motion | Reduced motion accessibility support | Feature enhancement |
| Components | Missing common UI components (buttons, loaders, etc.) | Feature requests |
| Split pane | `isSplitPane` density concern | Not a bug — pixel comparison valid for orientation detection |
| A11y semantics | Missing accessibility semantics | Feature enhancement |

## feature:channels

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Admin checks | Admin/permission checks, role validation | Needs backend |
| Rate limiting | Rate limiting on subscribe/unsubscribe | Needs backend |
| Creator validation | Channel creator validation | Needs backend |
| Post management | Create/edit/delete/pin posts | Feature requests, no backend APIs |
| Admin tools | Admin management, block/report | Feature requests |
| Media display | Channel avatar display, `mediaIds` display | Feature requests |
| hasMore | `hasMore` flag for pagination | Backend needs to provide it |
| Error sealed class | Sealed class for UI errors | Architecture change |

## feature:chat

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Message types | Structured message types (Location/Sticker/ContactCard) | Needs backend schema |
| Reactions | Reaction toggle UI | Feature request |
| Reply chain | Reply chain display | Feature request |
| Edit | Edit isolation | Feature request |
| SecurePreferences DI | DI for SecurePreferences | Significant refactor |
| Clipboard | Clipboard auto-clear | Feature request |
| Circuit breaker | Circuit breaker pattern | Feature request |

## feature:contacts

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Phone hash salt | No phone hash salt/HKDF | Requires backend API change for salt protocol |
| Delta sync | No contact discovery interval/delta sync | Feature request, needs backend delta endpoint |
| Reverse search | No reverse contact search | Feature request, needs new endpoint |
| Room caching | No Room for caching | Larger refactor beyond audit scope |
| SavedStateHandle | No SavedStateHandle | UI architecture change |
| Test coverage | Comprehensive test coverage | Feature request |

## feature:groups

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Encryption | Group message encryption | Backend responsibility |
| Role enforcement | Server-side role enforcement | Backend responsibility |
| Ban checks | Ban permission checks | Backend responsibility |
| Rate limiting | Rate limiting on approve/deny | Backend rate limiting needed |
| Revision conflicts | Optimistic locking on reads | Backend revision protocol |
| Concurrent modification | Race in GroupStateProcessor | Backend revision protocol |
| Preview link | Preview link leaks metadata | Backend endpoint behavior |
| QR code | QR code for group invite | Feature request |
| Avatar upload | Group avatar upload | Feature request |
| Search | Group search | Feature request |
| Revoke link | Revoke invite link | Feature request |
| Block+remove | Block and remove member | Feature request |
| God object | ViewModel too large | Large refactor |
| Pagination | No pagination | Backend/infrastructure |
| WebSocket | No WebSocket updates | Backend/infrastructure |
| Raw SQL | Raw SQL queries | Large refactor |
| Naming | Inconsistent naming conventions | Large refactor |

## feature:chatlist

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| Encryption indicator | Encryption indicator for offline queue | Feature request |
| Pin sync | Pin not synced to server | No backend API endpoint found |
| Pagination | No pagination | Needs backend support |
| Swipe gestures | No swipe gestures | Feature request |
| Search names | Search doesn't cover participant names | Needs DAO changes |
| Empty state | No search-specific empty state | UI feature request |

## feature:profile

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| E2E encryption | E2E encryption of profile fields | Requires SealedSender integration + backend profile key exchange |
| Search debounce | Search debounce | Feature request (UX optimization) |
| Avatar download | Avatar download | Requires MediaService.getBinary() from chat module (cross-module dependency) |
| Edit form UI | Edit form UI | Feature request (no EditProfileScreen exists) |
| Architecture | ViewModel architecture / state management refactor | Code quality improvements requiring broader changes |
| DI refactor | Remove ApiClient.getInstance() | Requires Hilt/Koin setup |

## feature:polls

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| 1.2 | Results visibility (no access control on poll results) | Backend-dependent — needs server-side authorization check |
| 3.6 | No "Close Poll" UI | Feature incomplete — requires UI composable to trigger closePoll() |
| 3.7 | No voters list endpoint | No GET /v1/polls/{id}/voters/{option_id} client implementation |
| 3.3 | loadPoll test missing | Test coverage gap — needs full state transition test |
| 2.7 | Duplicate option text allowed | UX issue — no deduplication warning |
| 2.8 | Auto-close timer invalid input crashes | UX edge case — invalid seconds silently defaults to null |
| 2.5 | Option count validation not enforced on backend path | Client-side validation not a security boundary — backend enforces |
| 4.4 | Hardcoded magic numbers (3600, 60, 604800) | Code quality — needs named constants |
| 4.7 | No loading/error/empty states for PollBubble | UI state not handled — assumes poll always complete |
| 4.8 | Inconsistent naming (closes_in_hours vs closeInSeconds) | Naming inconsistency — backend uses hours, client uses seconds |

## feature:backup

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| S-1 | AEADBadTagException suppressed - returns false silently | Backend needed to surface tampering to user; current behavior is intentional security design |
| S-2 | No memory zeroing in BackupArchive | BackupArchive is a utility object with no lifecycle - zeroing would require callers to manage |
| B-1 | downloadBackup discards downloaded bytes | Backend doesn't provide getBinary endpoint for backup download |
| B-2 | pollExportStatus infinite polling with no timeout/max retries | Backend poll endpoint behavior unknown; needs design decision |
| B-3 | backups list never populated (no server-side listing API) | Backend doesn't provide list backups endpoint |
| B-4 | downloadProgress never updated | Backend doesn't support progress callbacks for backup download |
| B-5 | uploadProgress stale after first chunk failure | Queue already reset on failure, but progress bar shows partially completed |
| C-1 | restoreBackup all-or-nothing (no selective restore from server) | Backend restore API is all-or-nothing |
| C-2 | No media data backup | Media not stored locally - requires backend media backup service |
| C-3 | existingIds in-memory Set pattern for all exporters | Large DB memory usage concern - INSERT OR IGNORE would require DB schema changes |
| C-4 | No DB transaction timeout on import | beginTransaction has no timeout - would require DB pool configuration |
| T-1 | No tests for BackupExporter | Test infrastructure not set up for instrumented tests |
| T-2 | No tests for BackupArchive | Test infrastructure not set up for instrumented tests |
| T-3 | No chunk upload/download ordering tests | Requires MockWebServer setup for byte-level verification |
| T-4 | No integration test (export -> import round-trip) | Requires file system and DB integration test setup |

---

## feature:location

| ID | Issue | Reason Deferred |
|----|-------|-----------------|
| C-1 | Message integration (LocationMessage data class, ChatService wiring, message envelope with location payload) | Requires cross-module coordination with :core:network and existing chat infrastructure — large feature, not a bug fix |
| C-2 | Location message UI in conversation (render location in message bubble) | Feature request — no existing UI component to extend |
| C-3 | Location persistence/history | Feature request — would need local storage design |
| C-4 | Unsend/delete location message | Feature request — needs revocation capability design |
| Q-1 | ViewModel extraction | Refactor — single 298-line composable with inline logic should be separated into ViewModel for testability |
| Q-2 | GlobalScope replacement in reverseGeocodeAddress | Should use structured concurrency with proper lifecycle scope — currently uses GlobalScope as workaround |

*Last updated: 2026-05-30*
