# Core Push Module Audit

**Module**: `core/push/src/main/java/org/enchant/core/push/`  
**Files Audited**: 5 Kotlin files  
- `FcmReceiveService.kt`
- `PushTokenRegistrar.kt`
- `FcmFetchManager.kt`
- `FcmFetchForegroundService.kt`
- `HuaweiPushFallback.kt`

---

## 1. Security

### Issues Found

| Severity | Issue | File(s) | Line(s) |
|----------|-------|---------|---------|
| **HIGH** | No payload validation before processing | `FcmReceiveService.kt` | 16-31 |
| **HIGH** | FCM token stored via `SecurePreferences` - implementation unverified | `PushTokenRegistrar.kt` | 28, 52, 36 |
| **MEDIUM** | Silent exception swallowing in service startup | `FcmReceiveService.kt` | 26 |
| **MEDIUM** | Hardcoded notification channel with generic icon | `FcmFetchForegroundService.kt` | 31 |
| **LOW** | Polling reveals message existence pattern | `HuaweiPushFallback.kt` | 34 |

### Details

**1. No Payload Validation (HIGH)**
`FcmReceiveService.onMessageReceived()` processes `RemoteMessage` without validating:
- No check for expected payload structure
- No `message_id` extraction for deduplication
- No `from` field validation
- No `message_type` inspection (for calls vs messages vs data-only)

```kotlin
// Line 16-19 - No validation performed
override fun onMessageReceived(message: RemoteMessage) {
    scope.launch {
        if (isAppInForeground()) {
            FcmFetchManager.scheduleFetch()  // Assumes valid message
```

**2. Token Storage Security (HIGH)**
`SecurePreferences` is used for FCM token storage but its implementation is not visible in this module. FCM tokens are sensitive credentials that:
- Grant push access to the user's account
- Should be encrypted at rest
- Should not be logged or exposed

**3. Silent Exception Swallowing (MEDIUM)**
```kotlin
// Line 26 - Catches all exceptions silently
try { startService(intent) } catch (_: Exception) {}
```
This masks all errors including security-relevant ones (e.g., security policy violations).

---

## 2. Bugs

### Issues Found

| Severity | Issue | File(s) | Line(s) |
|----------|-------|---------|---------|
| **HIGH** | No duplicate message detection | All | N/A |
| **HIGH** | Token refresh: local storage updated before backend confirms | `PushTokenRegistrar.kt` | 17-18, 28 |
| **HIGH** | Foreground service timeout is fixed (2s), not tied to actual fetch | `FcmFetchForegroundService.kt` | 39 |
| **MEDIUM** | `onNewToken` saves locally before backend registration | `PushTokenRegistrar.kt` | 36-37 |
| **MEDIUM** | Race condition in token comparison | `PushTokenRegistrar.kt` | 18 |
| **MEDIUM** | `notifyFcmRetryReceived()` never called | `FcmFetchManager.kt` | 55-57 |

### Details

**1. No Duplicate Detection (HIGH)**
FCM guarantees "at least once" delivery. Without `message_id` tracking:
- Messages may be processed multiple times
- No `seen_sentinel` or processed message cache exists

**2. Token Refresh State Inconsistency (HIGH)**
```kotlin
// PushTokenRegistrar.kt lines 17-28
suspend fun registerWithBackend(token: String) {
    if (token == SecurePreferences.getString(PUSH_TOKEN_KEY)) return  // Check old value
    withContext(Dispatchers.IO) {
        try {
            apiClient.post("/v1/push/register", body)
            SecurePreferences.putString(PUSH_TOKEN_KEY, token)  // Save AFTER server call
        }
    }
}
```
Problem: `onNewToken` in `FcmReceiveService` does:
```kotlin
// Line 36-37
SecurePreferences.putString("push.fcm_token", token)  // Save BEFORE registration
PushTokenRegistrar.registerWithBackend(token)
```
If registration fails, local state shows new token but backend has old token.

**3. Foreground Service Fixed Timeout (HIGH)**
```kotlin
// FcmFetchForegroundService.kt line 39
delay(2000)  // Always exactly 2 seconds
stopForeground(STOP_FOREGROUND_REMOVE)
```
The service stops after 2 seconds regardless of whether the actual fetch completed. This could cause:
- Fetch started but service killed before completion
- Unnecessary battery drain if fetch completes quickly
- No feedback if fetch takes longer

**4. `notifyFcmRetryReceived()` Never Called**
```kotlin
// FcmFetchManager.kt lines 55-57
fun notifyFcmRetryReceived() {
    _backoffCounter.set(0)
}
```
This method exists to reset backoff but nothing invokes it. The backoff only resets on successful `onFetchTriggered` invocation, not on FCM retry messages.

---

## 3. Completeness

### Missing Scenarios

| Scenario | Handled? | Notes |
|----------|----------|-------|
| Data-only messages | Partially | Schedules fetch, but no local handling |
| Notification-payload messages | **NO** | `RemoteMessage.notification` not handled - bypasses fetch |
| Call notifications | **NO** | No `message_type=call` handling |
| Group notifications | **NO** | No group-specific logic |
| Data messages while in foreground | Yes | Schedules fetch |
| Data messages while backgrounded | Yes | Starts foreground service |
| FCM `onDeletedMessages` | Yes | Schedules fetch |
| Token refresh | Yes | But with state inconsistency bug |

### Critical Gap: Notification-Payload Messages

FCM supports two message types:
1. **Data messages** - `RemoteMessage.data` only, app handles everything
2. **Notification messages** - `RemoteMessage.notification` with optional `data`, FCM displays notification automatically if app is backgrounded

Current code only handles data messages via `FcmFetchManager.scheduleFetch()`. Notification-payload messages:
- When app is foreground: `onMessageReceived` is called, but `message.notification` is ignored
- When app is background: FCM displays notification automatically (good), but no fetch triggered

---

## 4. Code Quality

### Issues Found

| Severity | Issue | File(s) |
|----------|-------|---------|
| **MEDIUM** | `FcmReceiveService` coroutine scope never cancelled | `FcmReceiveService.kt` |
| **MEDIUM** | No lifecycle awareness - service may run when app shouldn't | All |
| **MEDIUM** | `FcmFetchManager` is static singleton, hard to test | `FcmFetchManager.kt` |
| **LOW** | Generic exception catching masks errors | Multiple |
| **LOW** | No structured concurrency - fire-and-forget coroutines | All |
| **LOW** | No error recovery for Huawei polling | `HuaweiPushFallback.kt` |

### Details

**1. Service Lifecycle Cleanup**
```kotlin
// FcmReceiveService.kt
class FcmReceiveService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // scope never cancelled - will leak if service destroyed
```
Should override `onDestroy()` to cancel scope.

**2. No Lifecycle Awareness**
The module has no awareness of app lifecycle:
- No `ProcessLifecycleOwner` observer
- No check for `ActivityManager` with `IMPORTANCE_FOREGROUND_SERVICE` (only checks `IMPORTANCE_FOREGROUND`)
- Could start foreground service when app is intentionally backgrounded

**3. Static Singleton State**
```kotlin
// FcmFetchManager.kt
object FcmFetchManager {
    private var scope: CoroutineScope? = null
    private var fetchJob: Job? = null
    private val _backoffCounter = AtomicInteger(0)
```
Static singleton with mutable state makes unit testing difficult and global state management problematic.

**4. Huawei Polling No Backoff**
```kotlin
// HuaweiPushFallback.kt line 34
delay(30000)  // Fixed 30 second interval
```
No exponential backoff on failures. During backend outages, this could:
- Hammer the server with requests
- Drain battery with unnecessary polling

---

## Summary

| Category | Issues | Critical |
|----------|--------|----------|
| Security | 5 | 2 |
| Bugs | 6 | 3 |
| Completeness | 6 | 1 |
| Code Quality | 6 | 2 |
| **TOTAL** | **23** | **8** |

### Priority Fixes

1. **Add payload validation** - Extract and validate `message_id`, `message_type`, `from` fields
2. **Fix token refresh consistency** - Save token locally only after backend confirmation
3. **Add duplicate message detection** - Track processed `message_id`s with TTL
4. **Handle notification-payload messages** - Either process `message.notification` or document the limitation
5. **Cancel coroutine scope in `FcmReceiveService.onDestroy()`**
6. **Tie foreground service lifetime to actual fetch completion** - Use WorkManager or proper binding
7. **Add Huawei polling backoff** - Exponential backoff on failures
8. **Add `notifyFcmRetryReceived()` caller** - Or inline the reset logic
