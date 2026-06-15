# enchant-native `core:notifications` Module Audit

**Module path:** `core/notifications/src/main/java/org/enchant/core/notifications/`
**Files examined:** 7 `.kt` source files, 1 `.kt` test file
**Date:** 2026-05-29

---

## 1. Security

### 1.1 Notification Content Privacy

**Issue: Message preview content included in notification at `PRIORITY_HIGH`**

In `MessageNotifier.kt` lines 54–63 and `NotificationBuilder.kt` lines 56–70, the notification content is set with `setContentText("$senderName: $messagePreview")` — a plaintext preview of the message body. The notification channel uses `IMPORTANCE_HIGH`, which means the content appears on the lock screen and in the status bar even when the device is locked.

- **Risk:** Any user who picks up the device sees message previews without authentication.
- **Signal reference:** Signal Android uses `IMPORTANCE_DEFAULT` or `IMPORTANCE_LOW` and relies on `setHideMainNotificationContent(false)` with a custom "main" notification vs. a decoy notification pattern to keep sensitive content off the lock screen.
- **No `NotificationCompat.Builder.setVisibility()` restriction** is applied. Without `setVisibility(NotificationCompat.VISIBILITY_PRIVATE)`, the notification shows content even on the lock screen. This is a critical gap.

**Fix required:** Set `.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)` on all sensitive notifications and provide a separate public-facing notification variant.

---

### 1.2 ReplyReceiver Security

**`NotificationReplyReceiver.kt` (lines 13–74):**

1. **PendingResult lifecycle issue** (lines 16, 53, 66): `goAsync()` is used to allow async work, but the `CoroutineScope` created at line 17 has an unbounded lifetime. The coroutine may still be running after `pendingResult.finish()` is called, which is fine in principle, but there is no structured concurrency that ties the scope to the BroadcastReceiver lifecycle. If the process is killed before the coroutine completes, the work may be silently dropped.

2. **Error suppression** (lines 53, 65): Errors are swallowed with only a `Log.w`. This means failed reply sends or mark-read operations are silently lost with no user feedback. A user could tap "Reply" and never see confirmation of failure.

3. **Security of reply path** (lines 42–52): The reply is encrypted via `SessionManager.encryptMessage()` before sending, which is correct. The `selfId` is retrieved from `SecurePreferences`, which is appropriate. The payload encoding uses `Base64.getUrlEncoder().withoutPadding()`, which appears correct for URL-safe transmission.

4. **`PendingIntent.FLAG_IMMUTABLE`** is used in `NotificationBuilder.kt` lines 114, 132, 172, 179 — this is correct and follows best practices.

5. **Broadcast receiver action strings** are defined as companion object constants in `NotificationReplyReceiver.kt` lines 71–72 (`ACTION_REPLY`, `ACTION_MARK_READ`) and referenced in `NotificationBuilder.kt` lines 109, 127. This is correct.

---

### 1.3 Summary

| Issue | Severity | Location |
|---|---|---|
| No `setVisibility(PRIVATE)` on lock screen | **HIGH** | `NotificationBuilder.kt` lines 56–70, 94–105 |
| Silent error swallowing in reply/mark-read | **MEDIUM** | `NotificationReplyReceiver.kt` lines 53, 65 |
| Unbounded CoroutineScope in BroadcastReceiver | **LOW** | `NotificationReplyReceiver.kt` line 17 |

---

## 2. Bugs

### 2.1 Notification Channel Setup

**`NotificationChannels.kt` lines 15–43:**

- All 5 channels are correctly guarded with `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return` — no crash on pre-Oreo.
- `CHANNEL_MESSAGES` uses `IMPORTANCE_HIGH` which is correct for messages.
- `CHANNEL_MESSAGES_SILENT` uses `IMPORTANCE_LOW` which is correct for muted conversations.
- `CHANNEL_CALLS` uses `IMPORTANCE_HIGH` which is correct for calls.
- `CHANNEL_VOICE` uses `IMPORTANCE_DEFAULT` — reasonable for voice message playback.
- `CHANNEL_OTHER` uses `IMPORTANCE_LOW` — reasonable for background service notifications.

**No bugs detected.** Channel setup is correct.

---

### 2.2 Summary Notification Bug

**`MessageNotifier.kt` lines 86–109 (`updateSummaryNotification`):**

The summary notification logic has a **critical inconsistency** in how it groups messages:

1. Line 67 in `buildMessageNotification`: `.setGroupSummary(messageCount > 1)` — per-conversation summary is shown only when `messageCount > 1`. But this is set on the individual conversation notification, not on the summary notification.

2. The summary notification at lines 101–105 uses `setGroupSummary(true)` which is correct.

3. **Bug — Group key mismatch**: In `NotificationBuilder.kt` line 66, each conversation notification sets `.setGroup(conversationId)` — so each conversation is its own group. But the summary at line 103 uses `.setGroup(SUMMARY_GROUP)` = `"enchant_summary"` — a different group. This means the summary notification does **not** serve as the group summary for the individual conversation notifications, because they have different group keys. Android's grouping logic requires the summary's group to match the individual notifications' group to collapse them.

**Effect:** On Android 7+ (where notification grouping is supported), individual conversation notifications will NOT collapse into the summary. Instead, all conversation notifications will appear separately. The summary notification will appear as a separate unrelated notification.

**Fix:** Either (a) remove per-conversation `.setGroup()` and rely on the summary, or (b) change the summary to use the same group key as the conversations it summarizes.

---

### 2.3 Quick Reply Handling

**`NotificationBuilder.kt` lines 107–123 and `NotificationReplyReceiver.kt` lines 31–56:**

1. **Reply action creation** is correct: `RemoteInput.Builder(REPLY_KEY)` with label "Reply" is standard.
2. **Reply text extraction** at line 158 (`NotificationBuilder.getReplyText`) correctly uses `RemoteInput.getResultsFromIntent`.
3. **Reply path** encrypts the message before sending — correct.
4. **Empty reply handling** at `NotificationReplyReceiver.kt` line 33: checks `replyText.isNullOrBlank()` and finishes early — correct.

**No bugs detected in quick reply logic.** It is correctly implemented.

---

### 2.4 Summary Notification Count Bug

**`NotificationBuilder.kt` lines 85–92:**

```kotlin
conversationList.take(10).forEach { conv ->
    val line = if (conv.displayName.length > 20) {
        "${conv.displayName.take(20)}… ${conv.snippet.take(40)}"
    } else {
        "${conv.displayName} ${conv.snippet.take(50)}"
    }
    inboxStyle.addLine(line)
}
```

The `InboxStyle` is limited to displaying at most 7 lines in the expanded view (Android documentation). The code calls `addLine` up to 10 times, which means the `InboxStyle` will silently truncate lines beyond the 7-line limit. This is a known Android limitation, not a code error per se, but the code should limit to 7 lines to avoid confusion.

**Minor issue:** The truncated display may be confusing to users — consider also showing a "+N more" indicator when `conversationList.size > 7`.

---

## 3. Completeness

### 3.1 Notification Types Covered

| Notification Type | Supported | File |
|---|---|---|
| Direct Messages | Yes | `MessageNotifier.kt` |
| Muted Messages | Yes (uses `CHANNEL_MESSAGES_SILENT`) | `MessageNotifier.kt` line 51 |
| Call Notifications | Yes | `NotificationBuilder.kt` lines 139–155 |
| Voice Messages | Yes (channel created) | `NotificationChannels.kt` line 33–36 |
| Group Messages | Partially — same as direct | `MessageNotifier.kt` — no group-specific handling |
| Mentions | API support only (`mentionNotificationsOn` flag) | `NotificationPreferencesManager.kt` — no distinct mention notification type |
| Status Updates | API support only | `NotificationPreferencesManager.kt` — no distinct status notification builder |
| Summary/Bundled | Yes (but buggy — see 2.2) | `NotificationBuilder.kt` lines 73–105 |

**Coverage assessment:** Message and call notifications are well covered. However, **group conversations and mentions do not have dedicated notification handling** — they fall back to the same `MessageNotifier` path with no special grouping or mention-highlighting behavior.

---

## 4. Code Quality

### 4.1 Object-based Singleton Pattern

All classes use the Kotlin `object` declaration pattern for singletons:
- `MessageNotifier` (lines 8–112)
- `NotificationBuilder` (lines 28–179)
- `NotificationProfileHelper` (lines 20–97)
- `OptimizedMessageNotifier` (lines 23–74)
- `NotificationChannels` (lines 8–44)

This is an anti-pattern for Android components that need `Context`. It forces callers to pass `Context` as a parameter on every call, making it harder to manage lifecycle-aware resources. Signal Android uses lifecycle-aware components with proper dependency injection.

**Issue:** `MessageNotifier`, `OptimizedMessageNotifier`, `NotificationBuilder`, and `NotificationProfileHelper` all require `Context` on every method call, yet they hold no strong references to it. This is fine for now but breaks down if the module grows.

### 4.2 `requestCodeCounter` Race Condition

**`NotificationBuilder.kt` lines 31–35:**

```kotlin
private val requestCodeCounter = java.util.concurrent.atomic.AtomicInteger(0)

private fun uniqueRequestCode(base: Int): Int {
    return base xor requestCodeCounter.incrementAndGet()
}
```

The XOR-based approach is intended to generate unique request codes, but XOR has a significant problem: if `base` is the same for two different `PendingIntent` operations (e.g., same `conversationId.hashCode()`), and the counter wraps or reaches the same value, collisions can occur. Additionally, XOR of large numbers can produce values outside the expected 32-bit range that Android expects for request codes.

**Recommendation:** Use a simpler `base + counter.incrementAndGet()` or a `ConcurrentHashMap<conversationId, Int>` to track per-conversation counters.

### 4.3 Hardcoded Drawables

`NotificationBuilder.kt` lines 57, 95, 121, 135, 146 use Android system drawables:
- `android.R.drawable.ic_dialog_info` (twice)
- `android.R.drawable.ic_menu_send` (twice)
- `android.R.drawable.ic_menu_call`

These are fine for development but should be replaced with actual app drawables for production.

### 4.4 NotificationPreferencesManager API coupling

`NotificationPreferencesManager.kt` is tightly coupled to the REST API via `ApiClient`. All methods are suspend functions that call the network. There is no local caching of preferences — every read goes to the network. If the device is offline, `getGlobalPreferences()` returns a default-constructed `NotificationPreferences()` (line 60), meaning all notifications would be shown (or hidden) depending on the default, not the user's actual stored preference.

**This is a significant offline-first gap:** preferences should be cached in `SharedPreferences` and synchronized in the background.

### 4.5 OptimizedMessageNotifier context leak

**`OptimizedMessageNotifier.kt` lines 38–73:**

The `lastContext` field (line 28) holds a reference to the last context passed to `flush()`. If `scheduleFlush()` fires (line 66–73) but the context has become invalid (activity destroyed, process killed), this could cause issues. The pattern relies on the 50ms delay being short enough that the context is still valid, but this is fragile.

---

## 5. Test Coverage

**Current state:** Only 1 test file exists — `NotificationChannelsTest.kt` — which tests only that channel constants are correctly defined and unique. It does not test:
- Notification building with various inputs
- Reply action creation
- Mark-read action creation
- Summary notification building
- ReplyReceiver behavior
- Profile helper schedule logic
- Preferences manager network calls

**Coverage gap:** The test suite is extremely thin. According to the AGENTS.md testing requirements, every class must have tests covering happy path, error/edge cases, boundary conditions, state transitions, and security invariants.

---

## 6. Summary of Findings

### Critical (must fix before production)

1. **No `setVisibility(PRIVATE)` on notifications** — lock screen exposes message previews (HIGH)
2. **Group key mismatch in summary notification** — summary and conversation notifications use different groups, preventing collapse (HIGH)

### Medium (should fix)

3. **Silent error swallowing** in `NotificationReplyReceiver` — user gets no feedback on failed replies (MEDIUM)
4. **`requestCodeCounter` XOR collision risk** — request codes may collide for same conversationId (MEDIUM)
5. **No offline caching of preferences** — `NotificationPreferencesManager` always hits network, defaults to all-notifications-on on failure (MEDIUM)

### Low (nice to have)

6. **Unbounded CoroutineScope** in `NotificationReplyReceiver` — may outlive the component (LOW)
7. **`InboxStyle` truncates at 7 lines** — no "+N more" indicator when conversations exceed 7 (LOW)
8. **Hardcoded system drawables** — should use app-specific icons (LOW)
9. **No test coverage** for any non-constant behavior (LOW)
10. **Group/mention notifications** not differentiated from direct messages (LOW)

---

## Files Reviewed

- `MessageNotifier.kt` — 112 lines
- `NotificationBuilder.kt` — 179 lines
- `NotificationProfileHelper.kt` — 97 lines
- `NotificationPreferencesManager.kt` — 131 lines
- `NotificationReplyReceiver.kt` — 74 lines
- `OptimizedMessageNotifier.kt` — 75 lines
- `NotificationChannels.kt` — 44 lines
- `NotificationChannelsTest.kt` — 54 lines
