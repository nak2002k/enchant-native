# Audit: feature:calls UI Layer

**Module**: `feature/calls/src/main/java/org/enchant/calls/`
**Auditor**: Agent
**Files**: 14 Kotlin source files, 8 test files

---

## 1. SECURITY

### 1.1 SQL Injection in CallLogViewModel.deleteConfirmation

**File**: `CallLogViewModel.kt`, line 241

```kotlin
db.execSQL("DELETE FROM call_logs WHERE call_id NOT IN (${excludes.joinToString(",")}) AND filter = ?", arrayOf(filter.name))
```

**Issue**: String interpolation in SQL DELETE statement. Although `callId` values are internally generated, the `Excludes.ids` Set comes from UI state. If any caller ever passes malformed IDs, this could become exploitable. The fix is to use a loop-based parameterized delete:

```kotlin
excludes.forEach { id ->
    db.execSQL("DELETE FROM call_logs WHERE call_id = ?", arrayOf(id))
}
```

### 1.2 SafetyNumberHelper.verify — Timing Attack Bypass

**File**: `SafetyNumberDialog.kt`, lines 41-43

```kotlin
fun verify(remote: String, local: String): Boolean {
    return MessageDigest.isEqual(remote.encodeToByteArray(), local.encodeToByteArray())
}
```

**Issue**: The `verify` function uses `MessageDigest.isEqual` correctly for timing-attack prevention. However, it passes `encodeToByteArray()` results, which are already compared by `isEqual`. This is correct — the `isEqual` call IS the timing-safe comparison. The `encodeToByteArray()` happens before the comparison, so the timing attack protection applies to the decoded bytes. **No bug here.**

### 1.3 Hardcoded STUN Server Fallback

**File**: `CallLinkManager.kt`, lines 109, 294 (in core:calls)

Fallback `stun:stun.l.google.com:19302` is hardcoded. Acceptable for STUN, not a security issue.

### 1.4 Deep Link in CallLinkScreen

**File**: `CallLinkScreen.kt`, line 80

```kotlin
putExtra(Intent.EXTRA_TEXT, "enchant://call-link/${link.roomId}")
```

Room IDs are generated server-side; no sensitive data in the URL. **No issue.**

---

## 2. BUGS

### 2.1 OutgoingCallScreen Countdown Timer Never Cancelled

**File**: `OutgoingCallScreen.kt`, lines 27-36

```kotlin
LaunchedEffect(Unit) {
    while (timeLeft > 0) {
        delay(1000)
        timeLeft--
    }
    if (timeLeft == 0) onEndCall()
}
```

**Bug**: Timer runs indefinitely. If the call connects before timeout (e.g., in 5 seconds), the timer continues counting down to 0 and triggers `onEndCall()` — hanging up an active call. The `LaunchedEffect` should cancel when the composable leaves composition, but the call screen may remain mounted during the outgoing phase.

**Fix**: Cancel the timer when call state transitions to CONNECTED or beyond.

### 2.2 IncomingCallScreen Countdown Timer Never Cancelled

**File**: `IncomingCallScreen.kt`, lines 26-34 — same pattern as 2.1.

### 2.3 isMissedCall — Inconsistent with CallEndReason.TIMEOUT

**File**: `CallLogViewModel.kt`, lines 274-277

```kotlin
private fun isMissedCall(entry: CallLogEntry): Boolean {
    return entry.direction == CallDirection.INCOMING &&
           entry.status in listOf(CallEndReason.BUSY, CallEndReason.TIMEOUT)
}
```

**Bug**: The condition `status in listOf(BUSY, TIMEOUT)` correctly identifies missed calls. However, `applyFilter` at line 268 calls `isMissedCall` for `CallLogFilter.MISSED`, which is correct. But the display logic in `CallLogRow` at line 117 uses:

```kotlin
val isMissed = entry.status == CallEndReason.BUSY
```

This means missed calls with `TIMEOUT` status are not highlighted as missed in the UI. **Inconsistency**: `isMissedCall` treats TIMEOUT as missed, but the UI only treats BUSY as missed.

### 2.4 ActiveVideoCallScreen PiP Offset Has Incorrect Constants

**File**: `ActiveVideoCallScreen.kt`, line 79

```kotlin
.offset(x = (20 + pipOffsetX).dp, y = (100 + pipOffsetY).dp)
```

The `20` and `100` are hardcoded offsets added to the drag offset. These should be zero — the drag gesture should directly control position. The constants appear to be copy-paste error.

### 2.5 ActiveVideoCallScreen Shows Placeholder Instead of Real Video

**File**: `ActiveVideoCallScreen.kt`, line 47

```kotlin
Text("Remote Video", color = Color.White, style = MaterialTheme.typography.titleLarge)
```

**Bug**: The remote video area shows only a text placeholder "Remote Video". Actual WebRTC video rendering is not integrated. This is a missing feature, not a runtime bug, but worth documenting.

### 2.6 CallLogScreen Does Not Use clusteredEntries

**File**: `CallLogScreen.kt`, line 72

```kotlin
items(entries, key = { it.callId }) { entry ->
```

The screen renders `entries` directly, ignoring `clusteredEntries` computed in `CallLogViewModel.clusterCallLogs()`. The clustering logic (4-hour timeout grouping by peer/direction) exists but is never displayed. The `CallEventCluster` data and `CallLogSelectionState.Excludes` functionality are dead code from the UI perspective.

### 2.7 GroupCallScreen Receives Empty Participants List

**File**: `CallsNavDisplay.kt`, line 121

```kotlin
GroupCallScreen(
    participants = emptyList(),
    isAdmin = false,
    ...
)
```

Hardcoded `emptyList()`. No actual group call participant data is ever passed. The participant grid always shows nothing.

### 2.8 GroupCallScreen ParticipantTile Menu Never Opens

**File**: `GroupCallScreen.kt`, lines 98-104

```kotlin
if (isAdmin && showMenu) {
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
```

The menu is only shown when `isAdmin && showMenu`. `showMenu` is initialized to `false` and never toggled — no `showMenu = !showMenu` anywhere. Admin controls (mute/remove participant) are unreachable.

### 2.9 ParticipantTile showMenu State Never Toggled

**File**: `GroupCallScreen.kt`, line 78

```kotlin
var showMenu by remember { mutableStateOf(false) }
```

No code path sets `showMenu = true`. Long-press or tap listeners to toggle it are missing.

---

## 3. COMPLETENESS

### 3.1 CallLogScreen — Entry Click Does Nothing Useful

**File**: `CallLogScreen.kt`, line 44

```kotlin
onEntryClick = { if (backStack.size > 0) backStack.removeAt(backStack.size - 1) }
```

Clicking a call log entry navigates backward (pops backstack) instead of opening a conversation or call details. This is likely incorrect — should navigate to the conversation with that user.

### 3.2 CallLinkScreen — Edit Name Dialog Stub

**File**: `CallLinkScreen.kt`, line 96

```kotlin
OutlinedButton(onClick = { /* edit name dialog */ }, ...)
```

The "Edit Name" button is a stub with no dialog implementation.

### 3.3 ActiveVoiceCallScreen — onShowKeypad Empty

**File**: `ActiveVoiceCallScreen.kt`, line 111

```kotlin
onShowKeypad = { },
```

DTMF keypad functionality not implemented.

### 3.4 ActiveVoiceCallScreen — onShowSafetyNumber Empty

**File**: `ActiveVoiceCallScreen.kt`, line 113

```kotlin
onShowSafetyNumber = { }
```

Safety number dialog not implemented (though the `SafetyNumberDialog` composable exists and is usable).

### 3.5 GroupCallScreen — onSendReaction Empty

**File**: `CallsNavDisplay.kt`, line 127

```kotlin
onSendReaction = { },
```

Reactions not sent anywhere.

### 3.6 ActiveVideoCallScreen — Controls Auto-Hide Without User Interaction Check

**File**: `ActiveVideoCallScreen.kt`, lines 35-39

```kotlin
LaunchedEffect(Unit) {
    while (true) {
        delay(3000)
        if (showControls) showControls = false
    }
}
```

Controls hide after 3s regardless of user interaction. If a user is actively using controls, they disappear. Should reset timer on user interaction.

---

## 4. CODE QUALITY

### 4.1 ViewModels — Proper Architecture

`CallLogViewModel` and `CallViewModel` both:
- Use `viewModelScope.launch` for async work (correct coroutine scope)
- Expose `StateFlow` (read-only) with `MutableStateFlow` internally
- Handle loading/error states properly

**No issues.**

### 4.2 Navigation Inconsistency

**File**: `CallsNavBackStackExtensions.kt`

- `goToOutgoingCall` and `goToActiveCall` check `contains(key)` and pop to existing entry
- `goToIncomingCall` and `goToGroupCall` always `add()` unconditionally

```kotlin
// OutgoingCall: checks existing
if (contains(key)) { while (size > 1 && get(size - 1) != key) removeAt(size - 1) }
else { add(key) }

// IncomingCall: always adds
add(CallsNavKey.IncomingCall(callerId, callId))
```

This means repeated incoming calls stack multiple entries, while outgoing calls deduplicate.

### 4.3 CallsNavDisplay — viewModel() in Composable Parameters

**File**: `CallsNavDisplay.kt`, lines 22-24

```kotlin
callViewModel: CallViewModel = viewModel(),
callLogViewModel: CallLogViewModel = viewModel(),
```

Creating ViewModels inline in composable parameters makes dependency injection harder for testing. Should receive ViewModels via constructor or ` koinViewModel<CallViewModel>()` pattern.

### 4.4 DatabasePool Singleton Direct Usage

**File**: `CallLogViewModel.kt`, line 231

```kotlin
val pool = org.enchant.core.database.DatabasePool.instance
pool?.writer?.let { db ->
```

Direct singleton access couples to global state. Makes testing harder. Should be injected via constructor.

### 4.5 Inconsistent Error Handling in CallLinkManager

**File**: `CallLinkManager.kt`

- `createCallLink`, `getCallLink`, `joinCallLink`, `getCallLinkCredentials` return `Result<T>`
- `updateCallLinkName`, `updateCallLinkRestrictions`, `deleteCallLink` return `Unit` and swallow exceptions silently with `android.util.Log.w`

This asymmetry means callers cannot distinguish success from failure for write operations.

### 4.6 toggleVideo Uses Wrong Access Path

**File**: `CallViewModel.kt`, line 78

```kotlin
fun toggleVideo() {
    org.enchant.core.calls.CallsModule.getCallManager().toggleVideo()
}
```

**Bug**: `CallViewModel` already imports `CallManager` at line 9 (`import org.enchant.core.calls.CallManager`). It should call `CallManager.toggleVideo()` like all other methods, not bypass the alias and go directly to `CallsModule.getCallManager()`.

Compare:
```kotlin
fun toggleMute() { CallManager.toggleMute() }          // line 70 - correct
fun toggleSpeaker() { CallManager.toggleSpeaker() }   // line 73 - correct
fun toggleVideo() {
    org.enchant.core.calls.CallsModule.getCallManager().toggleVideo()  // line 78 - wrong
}
```

### 4.7 CallsNavKey Serialization — Long vs String for IDs

**File**: `CallsNavKey.kt`

```kotlin
data class OutgoingCall(val recipientId: Long) : CallsNavKey  // Long
data class IncomingCall(val callerId: Long, val callId: String) : CallsNavKey  // Long + String
data class ActiveCall(val callId: String) : CallsNavKey
data class GroupCall(val groupId: Long) : CallsNavKey
data class CallLink(val linkRoomId: String) : CallsNavKey
```

All Long IDs should arguably be String for consistency (room IDs are strings). But this is a design choice and not a bug.

### 4.8 SignalStrength to UI Int Conversion — Arbitrary Transform

**File**: `CallsNavDisplay.kt`, line 104

```kotlin
signalStrength = state.callState.signalStrength?.ordinal?.let { 3 - it } ?: 0,
```

Maps `SignalStrength.NONE.ordinal(3) -> 0`, `SignalStrength.POOR.ordinal(2) -> 1`, etc. The `3 - it` transform is arbitrary — no documented rationale. A `SignalStrength` enum with 4 values has a natural ordinal ordering; the transform inverts it.

---

## 5. TEST COVERAGE

Tests are comprehensive for the surface area they cover:
- `CallLogViewModelTest`: 395 lines, covers load/paging/filter/search/selection/deletion
- `CallViewModelTest`: 234 lines, covers all call control operations
- `CallsNavKeyTest`: 139 lines, covers serialization round-trips for all nav key variants
- `CallsNavBackStackExtensionsTest`: 165 lines, covers navigation logic
- `CallLinkManagerTest`: 164 lines, covers API operations

**Notable gap**: No test verifies that `OutgoingCallScreen` or `IncomingCallScreen` timers are cancelled when call connects. The countdown timer bug (section 2.1, 2.2) is not tested.

**Mock overuse**: Tests mock `CallsModule.getCallManager()` and `DatabasePool.instance` directly rather than using interface abstractions, which matches the production code's own patterns.

---

## SUMMARY TABLE

| Category | Severity | Count |
|---|---|---|
| Security — SQL injection risk | Medium | 1 |
| Bug — timer not cancelled | High | 2 |
| Bug — isMissedCall UI inconsistency | Low | 1 |
| Bug — PiP offset constants | Low | 1 |
| Completeness — placeholder video | Medium | 1 |
| Completeness — clustering unused | Low | 1 |
| Completeness — empty groups | Medium | 1 |
| Completeness — stub dialogs/handlers | Low | 5 |
| Code Quality — toggleVideo path | Low | 1 |
| Code Quality — navigation inconsistency | Low | 1 |
| Code Quality — singleton coupling | Low | 1 |
| Code Quality — error handling asymmetry | Low | 1 |

**Critical bugs**: None.
**High priority**: Timer cancellation on outgoing/incoming screens.
**Medium priority**: SQL injection fix, placeholder video, empty group call data.
**Low priority**: Remaining code quality items.
