# Audit: `feature:polls` Module

**Module path:** `/home/nsk/project/personal/Enchant/frontend/feature/polls/src/main/java/org/enchant/polls/`
**Files examined:**
- `PollBubble.kt` (166 lines)
- `PollViewModel.kt` (151 lines)
- `screens/PollCreateSheet.kt` (139 lines)
- `PollViewModelTest.kt` (108 lines)

---

## 1. Security

### 1.1 Vote Privacy

**Issue — Vote option IDs are sent as plain text strings, not option indices.**

`PollViewModel.vote()` (line 84) builds the request body as:
```kotlin
put("option_ids", JsonArray(selectedOptions.map { JsonPrimitive(it) }))
```

`selectedOptions` is `List<String>` where each String is the **display text** of the option (e.g. `"Red"`), as shown in `PollBubble` line 46:
```kotlin
val isSelected = option in selectedOptions
```

The backend API (section 9.2) expects `"option_ids":["1"]` — numeric identifiers. Sending option text strings instead:
1. Leaks the full question and all option texts to the network layer for every vote.
2. Will produce a validation error from the backend since it expects integer IDs, not arbitrary strings.

**Severity:** High — API mismatch, vote submission will always fail.

---

### 1.2 Results Visibility

**No access control on poll results.**

`loadPoll()` fetches `GET /v1/polls/{id}` with no authorization check. The `PollData` model exposes `results: Map<String, Int>` and `yourVote: List<String>` to anyone who can guess or obtain a poll ID. There is no verification that the requester is a participant in the conversation.

**Severity:** Medium — Results should only be visible to conversation members.

---

### 1.3 Anonymous Polls

**The `anonymous` flag is completely absent.**

The backend API (section 9.2) accepts `"anonymous":false` in the poll creation body. `PollCreateSheet` has no anonymous option UI. `PollViewModel.createPoll()` does not send the `anonymous` field. `PollData` has no `anonymous` Boolean. This means all polls are created as non-anonymous by default with no UI to change this.

**Severity:** Medium — Users cannot create anonymous polls, defeating a core privacy feature.

---

## 2. Bugs

### 2.1 Vote Submission Race Condition

`PollBubble.onVote` is a `Unit` callback passed in from the parent composable. The parent can call `onVote` while `loadPoll` from a previous vote is still in flight, since `loadPoll` is triggered inside `vote()` success callback (line 90). No optimistic locking or request deduplication exists.

**Severity:** Medium — A second vote can be submitted before the first resolves.

---

### 2.2 Poll Closing Without Reload Suppresses Error

`PollViewModel.closePoll()` (line 129-146) calls `loadPoll(pollId)` after a successful close. However, `_uiState` is set to success *before* `loadPoll` completes. If `loadPoll` fails, the success message is already set and the error is lost (the `fold` overwrites `_uiState` with the load error, losing the "Poll closed" message).

**Severity:** Low — Error from reload silently overwrites success confirmation.

---

### 2.3 UI State Mutation on Background Thread

`createPoll`, `vote`, `loadPoll`, and `closePoll` all use `viewModelScope.launch` without any `Dispatchers.Main` context. The `_uiState` `MutableStateFlow` is mutated from within `withContext(Dispatchers.Default)` blocks:

```kotlin
val result = withContext(Dispatchers.Default) {
    apiClient.post("/v1/polls", body)
}
result.fold(
    onSuccess = { ... _uiState.value = ... },  // <- mutating StateFlow off main thread
```

**Severity:** High — `MutableStateFlow` is not thread-safe for multi-threaded writes. This can cause state corruption, dropped updates, or crashes.

---

### 2.4 Poll Creation Ignores Backend Response for Options

In `createPoll` success handler (lines 57-71), `PollData` is constructed with:
```kotlin
pollId = json["poll_id"]?.jsonPrimitive?.content ?: "",
options = options,  // <- local parameter, not from response
```

The backend returns the poll with server-assigned option IDs and texts. The client ignores the server's options and uses the locally-supplied `options` list. This means:
1. The client does not know the server-assigned option IDs (needed for voting).
2. Option IDs are never synchronized with what the server created.

**Severity:** High — Voting with option text strings (bug 1.1) compounds with this; the server never gets correct option identifiers.

---

### 2.5 Option Count Validation Not Enforced on Backend Path

`PollCreateSheet` enforces min 2, max 12 options in the UI (lines 86, 76-77). `PollViewModel.createPoll()` does **not** perform any validation before sending to the backend. If the `PollCreateSheet` button is rapidly clicked, or if the API is called directly, a poll with 0, 1, or more than 12 options can reach the backend.

**Severity:** Medium — Client-side validation is not a security boundary.

---

### 2.6 Empty Question Not Validated

`PollViewModel.createPoll()` sends `"question": ""` to the backend if called with an empty string. No validation exists in the ViewModel.

**Severity:** Low — Backend should reject, but client should not send.

---

### 2.7 `PollCreateSheet` — Duplicate Option Text Allowed

The sheet allows adding multiple options with identical text. No deduplication or warning is shown. This makes option IDs ambiguous (which "Red" was selected?).

**Severity:** Low — UX issue.

---

### 2.8 Auto-Close Timer Input — Invalid Input Crashes

`PollCreateSheet` line 121:
```kotlin
val closeSecs = if (enableCloseTimer) closeInSeconds.toIntOrNull()
    ?.coerceIn(60, 604800) else null
```

If `closeInSeconds` is not a valid integer (e.g., empty string after filtering digits → becomes `""`), `toIntOrNull()` returns `null`, then `?.coerceIn(60, 604800)` returns `null`, and `null` is passed to `onCreate`. The caller passes this to the API which may accept `null` as "no auto-close". This is handled but not clearly.

However, if `closeInSeconds` is set to something like `"9999999"` (7 digits), `take(7)` passes `9999999`, `toIntOrNull()` gives `9999999`, and `coerceIn(60, 604800)` clamps to `604800`. This is fine.

**Severity:** Low — Works but the UX of entering invalid seconds silently defaulting to null is poor.

---

## 3. Completeness

### 3.1 `deletePoll` — Referenced in Tests But Absent from ViewModel

`PollViewModelTest` (line 93-96) calls `viewModel.deletePoll("poll-1")`, but `PollViewModel` has no `deletePoll` function. This means the test compiles only because Kotlin allows calling an undefined function on a `ViewModel` — or the test would fail to compile if actually run.

**Severity:** High — Broken test or broken feature. The test body is also empty (rule violation from AGENT_QUALITY_RULES.md Rule 4).

### 3.2 `createPoll` Signature Mismatch

`PollViewModel.createPoll()` signature (line 44) is:
```kotlin
fun createPoll(question: String, options: List<String>, allowMultiple: Boolean, closeInSeconds: Int?)
```

The test (lines 28-35) calls it with additional parameters `conversationId`, `anonymous`, and `closesInSeconds`:
```kotlin
viewModel.createPoll(
    conversationId = "conv-1",
    question = "Favorite color?",
    options = listOf("Red", "Blue", "Green"),
    allowMultiple = false,
    anonymous = false,
    closesInSeconds = 3600
)
```

This test would not compile — extra named parameters that don't exist in the function signature. This indicates the test was written against a different version of the API.

**Severity:** High — Test is incompatible with implementation.

### 3.3 `loadPoll` — Missing from Test

No test exists for `loadPoll()`. This is a core function for displaying poll results and should have coverage.

### 3.4 `vote` — Missing Assertions in Test

The vote test (lines 77-80) has an empty body:
```kotlin
@Test @DisplayName("vote casts a vote on a poll")
fun `vote`() = runTest {
    viewModel.vote("poll-1", listOf("option-1"))
}
```

No assertions. This violates AGENT_QUALITY_RULES.md Rule 4 (No Cheating on Tests) and Rule 7 (State Transitions Must Be Tested).

### 3.5 `PollData` — Missing Fields

The backend API returns:
- `poll_id`, `question`, `options`, `results`, `your_vote`, `total_votes`, `is_closed`, `allow_multiple` — **covered**
- `anonymous` — **missing** from `PollData`
- `closes_in_hours` / `closes_at` — **missing** from `PollData` (auto-close timer)
- `conversation_id` — **missing** from `PollData` and `createPoll()`

### 3.6 No "Close Poll" UI

`PollCreateSheet` has no UI to close a poll. The "close" functionality exists in `PollViewModel.closePoll()` but there is no composable or screen to trigger it.

**Severity:** Medium — Feature is incomplete; no user-facing way to close a poll.

### 3.7 No Voters List

Backend supports `GET /v1/polls/{id}/voters/{option_id}` — no client implementation exists for this endpoint.

---

## 4. Code Quality

### 4.1 ViewModel Initializer — `apiClient` Parameter Required but Test Uses No-Arg Constructor

`PollViewModel` constructor requires `apiClient: ApiClient` (line 39). The test's `@BeforeEach`:
```kotlin
@BeforeEach
fun setUp() {
    viewModel = PollViewModel()  // <- no apiClient passed
}
```

This would not compile. The test is not runnable as written.

### 4.2 All State Transitions Not Tested

`PollUiState` has 4 fields: `currentPoll`, `isSubmitting`, `error`, `successMessage`. No test verifies the state transitions for any of the 4 operations (`createPoll`, `vote`, `loadPoll`, `closePoll`). Only UI state default is tested.

### 4.3 Missing Doc Comments

`PollViewModel`, `PollData`, `PollUiState`, `PollBubble`, `PollCreateSheet`, and all public functions lack doc comments explaining behavior, contract, and failure modes. This violates AGENT_QUALITY_RULES.md Rule 11.

### 4.4 Hardcoded Magic Numbers

`PollCreateSheet` line 26: `closeInSeconds by remember { mutableStateOf("3600") }` — default 1 hour, no explanation. `coerceIn(60, 604800)` (line 122) uses magic numbers for min/max seconds without constants.

### 4.5 Error Messages Are Raw Exceptions

All error handling in `PollViewModel` passes `it.message` directly to `_uiState.value.copy(error = it.message)`. This leaks internal details (file paths, SQL errors, network stack traces) to the UI. For a private messenger, this is a security concern.

### 4.6 `PollBubble` — `remember(poll.pollId)` for Option Selection State

`PollBubble` line 23:
```kotlin
var selectedOptions by remember(poll.pollId) { mutableStateOf(poll.yourVote) }
```

If the same poll is loaded twice with different `yourVote` values, the `remember` key (`poll.pollId`) won't change, so the local selection state won't update to reflect the new `yourVote`. This can cause the UI to show stale selections after reloading a poll.

**Severity:** Medium — On poll reload, selected options may not sync with server's `yourVote`.

### 4.7 No Loading / Error / Empty States for Poll Results

`PollBubble` assumes `poll` is always in a complete state. There is no handling for a `null` or partially-loaded `PollData`. This can cause null pointer exceptions or unexpected UI rendering.

### 4.8 Inconsistent Naming

- Backend uses `option_ids` (plural), client sends `option_ids` but with string values instead of integers.
- Backend uses `closes_in_hours`, client uses `closeInSeconds`.
- Backend field `anonymous`, client has no equivalent.

---

## Summary Table

| Category | Issue | Severity |
|----------|-------|----------|
| Security | Vote sends display text instead of option IDs | High |
| Security | No anonymous poll support (flag missing) | Medium |
| Security | Results visible to anyone with poll ID | Medium |
| Bug | StateFlow mutated on background thread | High |
| Bug | Test references non-existent `deletePoll` | High |
| Bug | Test calls `createPoll` with wrong signature | High |
| Bug | Vote test has no assertions | High |
| Bug | Option text used instead of server IDs for voting | High |
| Bug | "Poll closed" success can be overwritten by reload error | Low |
| Completeness | No `loadPoll` test | Medium |
| Completeness | No close poll UI | Medium |
| Completeness | No voters list endpoint | Low |
| Code Quality | No doc comments on any public API | Medium |
| Code Quality | Raw exception messages exposed to UI | Medium |
| Code Quality | `remember(poll.pollId)` can cause stale selection state | Medium |

---

## Recommendations (Priority Order)

1. **Fix vote submission** — Send option indices/IDs matching server's `options` array, not display text.
2. **Move StateFlow mutations to `Dispatchers.Main`** — All `_uiState.value = ...` must run on the main thread.
3. **Remove or implement `deletePoll`** — It is referenced in tests but does not exist.
4. **Fix test signatures** — `createPoll` test uses non-existent parameters.
5. **Add `anonymous` field** to `PollData`, `createPoll()`, and `PollCreateSheet`.
6. **Add assertions to vote test** — Verify state transitions.
7. **Add `loadPoll` test** — Verify results loading.
8. **Wrap error messages** — Don't expose raw exception strings to UI.
9. **Add doc comments** — Document all public declarations.
10. **Add close poll UI** — Allow users to close their own polls.
