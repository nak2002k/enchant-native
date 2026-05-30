# core:network Audit

## Security Issues

### CRITICAL: No TLS Enforcement on OkHttpClient
- **File**: `ApiClient.kt` (lines 28-33), `WebSocketManager.kt` (lines 74-77)
- **Issue**: `OkHttpClient.Builder()` is created without any TLS configuration. No `Tls12SocketFactory`, no TLS 1.2/1.3 enforcement.
- **Recommendation**: The reference app explicitly uses `Tls12SocketFactory` wrapping the SSL socket factory and sets `ConnectionSpec.RESTRICTED_TLS` to enforce strong TLS. Implement similar enforcement.
- **Risk**: Downgrade attacks, TLS protocol stripping attacks possible on compromised networks.

### CRITICAL: No Certificate Pinning
- **File**: `ApiClient.kt`, `WebSocketManager.kt`
- **Issue**: No `CertificatePinner` configured. No custom `TrustManager` with blacklisting capability.
- **Recommendation**: The reference implementation uses `BlacklistingTrustManager` for certificate blacklisting and `TrustStore` from configuration. Implement certificate pinning or custom trust management.
- **Risk**: Man-in-the-middle attacks without detection.

### HIGH: Sensitive Data Logging
- **File**: `AuthInterceptor.kt` (line 116), `WebSocketManager.kt` (line 350)
- **Issue**: `Log.w("AuthInterceptor", "Refresh failed: ${e.message}")` and `Log.e("Enchant", "handleFrame error: ${e.message}", e)` log errors that may contain sensitive token information or server responses.
- **Risk**: Secrets leaked to logcat on user devices.

### MEDIUM: No TLS for WebSocket
- **File**: `WebSocketManager.kt` (line 115)
- **Issue**: `ws://` URL used directly in `Request.Builder().url(AppConfig.wsUrl)`. No explicit WSS enforcement or TLS configuration on the `wsClient`.
- **Recommendation**: The reference app converts `https://` to `wss://` explicitly and uses TLS throughout. Enforce WSS and TLS configuration on WebSocket connections.
- **Risk**: WebSocket traffic transmitted in cleartext if URL is misconfigured.

## Bugs

### CRITICAL: Race Condition in AuthInterceptor Token Refresh
- **File**: `AuthInterceptor.kt` (lines 39-76)
- **Issue**: Between lines 37 and 39, the original response is not closed before proceeding. When `shouldRefresh` is false (another thread is refreshing), the code waits but then proceeds with the original `response` (line 76) which is already "used". Additionally, line 72 `response.close()` should be called before returning but after retry.
- **Risk**: Memory leak, socket leak, stuck threads.

### HIGH: WebSocket `sendMessage` Fire-and-Forget for Acknowledgment Messages
- **File**: `WebSocketManager.kt` (lines 384-411 `sendSignalMessage`, lines 221-237)
- **Issue**: `sendTypingStart`, `sendTypingStop`, `sendDeliveryReceipt`, `sendReadReceipt` just call `sendSignalMessage` which sends without waiting for response, no retry on failure, no confirmation.
- **Recommendation**: Implement confirmation and retry for delivery/read receipts.
- **Risk**: Messages appear sent but lost, no delivery confirmation.

### HIGH: OfflineQueue `drain()` Fails Silently on Repeated REST Failure
- **File**: `OfflineQueue.kt` (lines 90-127)
- **Issue**: If REST fallback fails 5 times (line 112), message is dropped with no notification, no callback, `_pendingCount` updates then message disappears.
- **Recommendation**: The reference app has more sophisticated queue management with explicit error propagation. Propagate errors to callers instead of silent drop.
- **Risk**: User loses messages silently after transient failures.

### MEDIUM: `ApiClient.request()` Retry Logic Bypasses Rate Limit Tracker
- **File**: `ApiClient.kt` (lines 172-180)
- **Issue**: Catch block retries with exponential backoff but does NOT call `RateLimitTracker.waitIfNeeded()` on retry, potentially hammering server during outages.
- **Risk**: Server load加剧 during outages, longer lockout from rate limiting.

### MEDIUM: JWT Expiry Check Uses System Time Only
- **File**: `WebSocketManager.kt` (line 437-448)
- **Issue**: `isJwtExpired` checks `System.currentTimeMillis() / 1000 >= exp`. If device clock is wrong, JWT may be considered valid when expired or vice versa.
- **Recommendation**: The reference app uses server time for synchronization. Use server time for auth decisions.
- **Risk**: Incorrect auth decisions based on clock skew.

### MEDIUM: `pendingRequests` Leak on WebSocket Failure
- **File**: `WebSocketManager.kt` (lines 138-141)
- **Issue**: `onFailure` triggers `scheduleReconnect` but any pending requests in `pendingRequests` are left orphaned with CompletableDeferreds that timeout after 10s in `sendMessage`.
- **Recommendation**: The reference app's `cleanupAfterShutdown()` properly cleans up all pending requests. Implement similar cleanup.
- **Risk**: Memory grows, goroutines accumulate.

### LOW: ConcurrentModificationException in `pendingRequests`
- **File**: `WebSocketManager.kt` (line 159)
- **Issue**: `pendingRequests.values.forEach { it.completeExceptionally(...) } ` during `disconnect()` while another thread may be adding via `pendingRequests[id] = deferred` in `sendMessage` (line 206).
- **Recommendation**: Use synchronized access with proper locking or `CopyOnWriteArrayMap`.
- **Risk**: Crash on disconnect during active sends.

### LOW: `scheduleReconnect` Has No Maximum Retry Cap
- **File**: `WebSocketManager.kt` (line 379)
- **Issue**: `retryCount++` grows unbounded. When `consecutive401s >= 5` (line 147) triggers AUTH_FAILED, but `scheduleReconnect` bypasses this via direct `connect()` call.
- **Recommendation**: The reference app has explicit connection attempt limits with backoff. Add maximum retry limit before giving up.
- **Risk**: Infinite retry loop under certain failure conditions.

## Completeness Gaps

### HIGH: No Connection Health Check / Liveness Probe
- **File**: `ApiClient.kt`, `WebSocketManager.kt`
- **Issue**: No periodic health check endpoint. `RateLimitTracker` updates from response headers but no active monitoring.
- **Recommendation**: Implement `HealthMonitor` style component that tracks message errors and keep-alive responses with explicit health state.
- **Gap**: No way to detect degraded connection proactively.

### MEDIUM: No WebSocket Batch Message Reading
- **File**: `WebSocketManager.kt`
- **Issue**: Handles messages one at a time via `handleFrame`. No batch reading mechanism.
- **Recommendation**: The reference app's `readMessageBatch()` reads multiple messages in a single call, batches ACKs. Implement batch reading for lower latency and more efficient network use.
- **Gap**: Higher latency for message delivery, less efficient network use.

### MEDIUM: No Exponential Backoff with Jitter Upper Bound
- **File**: `WebSocketManager.kt` (line 376-378)
- **Issue**: `baseDelay = minOf(1000L * (1 shl retryCount), 30000L)` doubles every retry but capped at 30s. Jitter is +/- 25% but negative jitter could make delay very small (100ms minimum after jitter subtraction).
- **Recommendation**: Use more sophisticated backoff with ceiling and minimum delays to avoid hammering server on repeated failures.
- **Gap**: Could hammer server on repeated failures.

### MEDIUM: No Request Cancellation
- **File**: `ApiClient.kt`, `WebSocketManager.kt`
- **Issue**: No cancellation token passed to OkHttp calls. If coroutine is cancelled, the request continues in flight.
- **Recommendation**: The reference app uses proper cancellation via `CompletableFuture` and `CancellationException`. Implement cancellation support.
- **Gap**: Wasted bandwidth, potential out-of-order delivery.

### LOW: No Explicit Disconnect on Network Loss
- **File**: `ConnectivityMonitor.kt`, `WebSocketManager.kt`
- **Issue**: `ConnectivityMonitor` tracks state but doesn't automatically disconnect WebSocket. Service continues with dead socket until next keepalive kills it.
- **Recommendation**: Properly manage WebSocket lifecycle with connection state flow.
- **Gap**: Slower reconnection after network loss.

### LOW: No Message Priority Queue
- **File**: `OfflineQueue.kt`
- **Issue**: Simple FIFO queue. Messages have no priority, no timestamp ordering after queue rebuild.
- **Recommendation**: Implement priority-based message handling with proper ordering.
- **Gap**: Important messages may be delayed behind less important ones.

## Code Quality Issues

### HIGH: Thread-Safety Issues with Singleton Pattern
- **File**: `ApiClient.kt` (lines 16-21)
- **Issue**: `getInstance()` is not synchronized, `_instance` can be set by `setInstance()` from any thread. The `initialized` field is checked before `client` is fully constructed in `init()`.
- **Risk**: Race condition could cause `client` to be accessed before initialization.

### MEDIUM: Using `Object()` as Lock is Fragile
- **File**: `AuthInterceptor.kt` (line 24)
- **Issue**: `private val lock = java.lang.Object()` with `synchronized(lock)` patterns. Multiple separate `lock` objects would cause issues if class is extended.
- **Risk**: Harder to reason about lock boundaries.

### MEDIUM: Memory Leak in CoroutineScope Without Cancellation
- **File**: `WebSocketService.kt` (lines 21, 75-77)
- **Issue**: `scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` created but `scope.cancel()` only called in `onDestroy()`. If service is destroyed without going through `onDestroy()`, scope leaks. Also `WebSocketManager.scope` (line 91) same issue.
- **Risk**: Orphaned coroutines, leaked references.

### MEDIUM: Error Swallowing in `handleFrame`
- **File**: `WebSocketManager.kt` (lines 333-344)
- **Issue**: `catch (e: Exception)` swallows all exceptions during envelope parsing. Sends NACK but errors are not propagated to caller, no retry scheduled.
- **Risk**: Silent message loss, harder to debug.

### LOW: Inconsistent Error Handling
- **File**: `ApiClient.kt` vs `WebSocketManager.kt`
- **Issue**: `ApiClient` catches all exceptions and wraps in `Result.failure(Exception(...))`. `WebSocketManager` uses `Result` only in `requestRESTFallback` but raw exceptions elsewhere.
- **Risk**: Inconsistent behavior, harder to debug.

### LOW: Unused Import in ApiModels.kt
- **File**: `ApiModels.kt` (line 4)
- **Issue**: `import kotlinx.serialization.SerialName` imported but not used.
- **Risk**: None functional but indicates incomplete housekeeping.

## Reference Implementation Comparison

| Aspect | Reference App | enchant-native | Gap |
|--------|---------------|---------------|-----|
| TLS Enforcement | `Tls12SocketFactory` + `ConnectionSpec.RESTRICTED_TLS` | None | **Critical** |
| Certificate Pinning | `TrustStore` + `BlacklistingTrustManager` | None | **Critical** |
| WebSocket Auth | Dedicated `AuthenticatorWebSocket` with proper state | Manual token handling in `WebSocketManager` | Medium |
| Message Batching | `readMessageBatch()` with batch ACK | Single-message handling | Medium |
| Health Monitoring | `HealthMonitor` tracks errors and keepalives | None | High |
| Request Cancellation | `CancellationException` propagation | None | Medium |
| Retry Logic | Sophisticated with jitter and limits | Basic exponential backoff | Medium |
| Connection State | `WebSocketConnectionState` enum with AUTH_FAILED | Custom `ConnectionState` enum | Low |
| Thread Safety | Proper synchronization via RxJava | Manual `synchronized` blocks | Medium |

## Recommendations (prioritized)

### P0 (Security-Critical)
1. **Add TLS 1.2/1.3 enforcement** to both `ApiClient` and `wsClient` OkHttp instances. Follow the reference app's `Tls12SocketFactory` pattern.
2. **Implement certificate pinning** via `CertificatePinner` or custom `TrustManager` with `TrustStore`.

### P1 (Critical Bugs)
1. **Fix response closing in AuthInterceptor**: Ensure `response.close()` is always called before returning response or proceeding to retry.
2. **Fix pending requests cleanup on WebSocket failure**: Follow the reference app's `cleanupAfterShutdown()` pattern - iterate and error-complete all pending requests.
3. **Fix concurrent modification in `pendingRequests`**: Use proper synchronization or `CopyOnWriteArrayMap`.

### P2 (High Priority)
1. **Add health monitoring**: Create a `HealthMonitor`-style component that tracks connection health, message errors, and keepalive responses.
2. **Implement message batching**: Add `readMessageBatch()` capability to collect multiple messages before processing.
3. **Improve OfflineQueue failure handling**: Propagate errors to callers; consider exponential backoff before dropping.
4. **Add request cancellation support**: Use `Job` cancellation or explicit cancellation tokens.

### P3 (Medium Priority)
1. **Cap retry count in scheduleReconnect**: Add maximum retry limit before giving up.
2. **Fix JWT expiry check**: Use server time synchronization or buffer the check.
3. **Improve retry logic in ApiClient**: Add rate limit awareness to retry logic.
4. **Add connection liveness probe**: Periodic health check independent of message flow.

### P4 (Code Quality)
1. **Avoid singleton pattern**: Consider dependency injection via `AppConfig` or manual DI.
2. **Unify error handling**: Use consistent `Result` type across all network operations.
3. **Remove sensitive data logging**: Ensure no tokens, secrets, or server responses in logs.
4. **Fix unused imports**: Clean up `ApiModels.kt`.