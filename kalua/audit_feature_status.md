# Audit Report: `feature:status`

**Module**: `/home/nsk/project/personal/Enchant/frontend/feature/status/src/main/java/org/enchant/status/`
**Files examined**:
- `StatusViewModel.kt`
- `screens/StatusCreateScreen.kt`
- `screens/StatusViewerScreen.kt`
- `screens/StatusFeedScreen.kt`
- Test files: `StatusViewModelTest.kt`, `screens/StatusFeedScreenTest.kt`

---

## 1. Security

### Findings

| Issue | Location | Severity |
|-------|----------|----------|
| Privacy setting not sent with selected contact IDs | `createTextStatus()` L102, `createMediaStatus()` L132 | **Critical** |
| `StatusPrivacy.Selected` cannot carry `userIds` payload | `StatusPrivacy` sealed class L17 | **Critical** |
| No client-side privacy enforcement on feed | `loadFeed()` L65 | **High** |
| No expiration enforcement | All functions | **High** |
| Privacy dropdown allows selecting "Selected Contacts" with no way to pick contacts | `StatusCreateScreen` L157 | **Medium** |
| No validation that viewer can see restricted status | `viewStatus()` L157 | **Medium** |
| Media status generates random UUID instead of using real media ID | `StatusCreateScreen` L135, L143 | **High** |

**Details**:

1. **`StatusPrivacy.Selected` is a data object with no payload** (L19). The API spec (`05_social.md` L55) expects `selected_contacts: ["uuid"]` alongside privacy `"SELECTED"`. The `createTextStatus` and `createMediaStatus` builds the JSON via `privacyToStr(privacy)` which returns only the string `"SELECTED"` - it never includes the actual contact ID list. When privacy is `StatusPrivacy.Selected`, the server receives no contact list and falls back to default visibility. The privacy choice is虚假 (fake) — the user picks "Selected Contacts" but the app does not surface a contact picker and does not send the list.

2. **No client-side feed privacy filtering**: `loadFeed()` parses all statuses from `json["statuses"]` without filtering based on the current user's privacy relationship to each status author. The server is trusted to return only appropriate statuses, but no client-side assertion exists. If a misconfigured or malicious server returned statuses the user should not see (e.g., "Close Friends" status from a non-close-friend), the client would display them.

3. **No expiration tracking**: The backend returns `expires_ts` on `POST /v1/status` (per `05_social.md` L55). The response is discarded — only `successMessage` is set. There is no storage of `expires_ts` and no local timer or cleanup. The `StatusFeedEntry` data class has no `expiresAt` or `expiresTs` field. After 24 hours the server presumably hides expired statuses, but:
   - The client does not actively remove expired statuses from its own feed display.
   - There is no background job or alarm to clean up expired statuses locally.
   - If the user is offline for >24 hours and then opens the app, stale statuses may be shown momentarily before the feed refreshes.

4. **"Add Image" / "Add GIF" use random UUID**: `StatusCreateScreen` lines 135 and 143 pass `UUID.randomUUID().toString()` as the `mediaId`. This is a stub — real media would first be uploaded via `POST /media` to get a real media ID, then passed to `createMediaStatus`. The current code creates statuses that reference nonexistent media IDs.

5. **`viewedBy` is parsed from feed but never used in UI**: `StatusFeedEntry.viewedBy` exists but the `StatusFeedScreen` and `StatusViewerScreen` never render view counts or viewer lists. The field is populated (L38, L172) but inert.

---

## 2. Bugs

### Findings

| Issue | Location | Severity |
|-------|----------|----------|
| Feed API response field mismatch — `statuses` vs `feed` | `loadFeed()` L65 | **High** |
| `myStatus` identification relies on magical `"me"` string value | `loadFeed()` L79 | **High** |
| `viewedBy` never transferred to UI state | `loadFeed()` L67, `getViewers()` L172 | **Medium** |
| `viewStatus()` error swallowed silently with only a log | `viewStatus()` L161 | **Medium** |
| No loading guard on `viewStatus` | `viewStatus()` L157 | **Low** |
| Unhandled malformed JSON on parse failure | `loadFeed()` L64, `getViewers()` L171 | **Low** |
| `feed` parameter in `StatusFeedScreen` is typed as `Map<String, List<StatusFeedEntry>>` but passed feed is a flat list | `StatusFeedScreen` L27 vs `StatusViewModel` L81 | **High** |

**Details**:

1. **Wrong response field**: `loadFeed()` reads `json["statuses"]` (L65), but the backend API (`05_social.md` L56, `android-backend-reference.md` L800) returns `{"feed": [...]}`. The code asks for `"statuses"` which likely does not exist in the response, so the map returns `null` and falls back to `emptyList()`. The entire feed would be invisible without any error. This needs verification against the live server, but the spec says `"feed"` not `"statuses"`.

2. **`"me"` string matching for my own status** (L79): `entries.find { it.userId == "me" }` assumes the server marks the current user's status with the literal string `"me"` as the `userId`. The spec (`android-backend-reference.md` L801) uses `author_user_id: "uuid"`, not the string `"me"`. If the server uses the actual user UUID, `myStatus` will always be `null` and the "My Status" section in `StatusFeedScreen` will never populate. This is fragile and likely wrong.

3. **`feed` vs `Map<String, List<StatusFeedEntry>>` type mismatch**: `StatusFeedScreen` declares `feed: Map<String, List<StatusFeedEntry>>` (L27), intended to be grouped by user. However, `StatusViewModel.loadFeed()` stores a flat `List<StatusFeedEntry>` (L81: `_uiState.value = _uiState.value.copy(feed = entries)`). This is a type mismatch — the screen's `remember(feed)` and `sortedUsers` logic (L31-35) expects a `Map`, but it receives whatever type the ViewModel passes. The compiler would not accept this unless there is a conversion somewhere not observed, or the code will fail at runtime with a `ClassCastException`.

4. **`viewStatus()` swallows all errors**: Line 161 logs with `Log.w` and returns. The `viewStatus` function returns `Unit` — callers have no idea if the view was recorded. If the server is down or the status ID is invalid, the failure is invisible. This means the server's view count may perpetually show 0 for statuses the user actually viewed.

5. **`getViewers()` reads `json["viewers"]`**: The backend spec (`05_social.md` L58) returns `{"views": [...]}`, not `{"viewers": [...]}`. Again a field name mismatch. If the server uses `"views"` the viewer list will always be empty.

---

## 3. Completeness

### Findings

| Missing Feature | Impact |
|-----------------|--------|
| **Expiration / TTL enforcement** — no `expires_ts` storage or cleanup | **High** |
| **Selected contacts privacy picker** — UI allows "Selected Contacts" but has no contact list UI | **Critical** |
| **Status viewer list display** — `getViewers()` exists but no screen shows viewer list | **Medium** |
| **Edit status** — no edit function | **Medium** |
| **Single status fetch** — no `GET /v1/status/{status_id}` wrapper | **Medium** |
| **Media upload before status** — stub UUID used instead of real media ID | **High** |
| **Close Friends data model** — `StatusPrivacy.CloseFriends` exists but no close friends list exists in contacts module | **Medium** |
| **Background status cleanup job** — no scheduled work to remove expired statuses | **Medium** |
| **View count display** — no UI shows how many people have viewed a status | **Low** |
| **Reply to status** — `onReply` callback exists in `StatusViewerScreen` but no implementation | **Low** |

**Details**:

1. The status module is **half-implemented**. It can post a text status and load a feed, but critical paths for privacy-selected contacts, media, expiration, and viewers are missing or stubbed.

2. **Text length enforcement in createTextStatus**: The `StatusCreateScreen` UI enforces 700 chars at the TextField level (L91: `if (it.length <= 700)`), which is good. However, `createTextStatus` itself does not validate length server-side and passes `text` directly without a length check in the ViewModel.

3. **No `selectedContacts` parameter in ViewModel API**: According to `BUILD_PHASES/05_social.md` L171, `createTextStatus` should accept `selectedContacts: List<String>?`. The ViewModel function signatures at L98 and L128 only take `(text, backgroundColor, privacy)` and `(mediaId, privacy)` — no contact list.

---

## 4. Code Quality

### Findings

| Issue | Location | Notes |
|-------|----------|-------|
| Feed parsing logic duplicated in `loadFeed()` and `getViewers()` | `StatusViewModel` L65, L172 | Identical JSON array parsing pattern, should be shared |
| `StatusPrivacy.Selected` cannot hold data | L19 | Design flaw — enum-like sealed class cannot carry payload |
| Hardcoded `android.util.Log` usage | `viewStatus()` L161 | Ties ViewModel to Android platform |
| ViewModel uses parameterless constructor that delegates to singleton | `StatusViewModel` L54 | Makes testing harder — cannot inject mock API client cleanly |
| NoJob cancellation on in-flight requests | All `viewModelScope.launch` blocks | Race condition if calls overlap |
| `feed` state is a flat list but screen expects `Map` | `StatusFeedScreen` L27 | Type contract violation |
| Null handling via elvis operator silently returns empty string | `loadFeed()` L68-77 | No error surfaced when JSON structure is unexpected |
| `StatusPrivacy.AllContacts` maps to `"ALL_CONTACTS"` but API spec uses `"ALL_CONTACTS"` | L221 | Appears correct; `SELECTED` and `CLOSE_FRIENDS` mapping may be wrong per spec |
| Loading state not set for `viewStatus` | L157 | Inconsistency with other operations |
| `"me"` userId heuristic | L79 | Fragile hardcoded string comparison |

**Details**:

1. **Duplicated parsing**: Lines 65-78 in `loadFeed()` and lines 172-179 in `getViewers()` use the same pattern: `jsonArray?.map { item -> val obj = item.jsonObject; StatusViewer(... = obj["field"]?.jsonPrimitive?.content ?: "") }`. This is a candidate for a private helper like `parseStatusEntries(jsonArray)` and `parseStatusViewers(jsonArray)`.

2. **`StatusPrivacy.Selected` as data object**: Kotlin sealed classes with `data object` entries cannot hold constructor parameters. Using `StatusPrivacy.Selected` requires changing to `data class Selected(val userIds: List<String>) : StatusPrivacy()`. This is a design bug — the privacy model cannot represent its own data.

3. **Android logging in ViewModel**: `Log.w("Status", "Load failed: ${e.message}")` at L161 directly uses `android.util.Log`, which prevents this ViewModel from being unit-tested in a JVM environment without Robolectric. A logging abstraction (e.g., `EnchantLog`) should be used.

4. **Parameterless constructor using singleton**: `constructor() : this(ApiClient.getInstance())` means `StatusViewModel()` always uses the real singleton. In tests, the singleton must be stubbed or mocked, making tests fragile. Standard practice is to use a factory or Hilt injection.

5. **No coroutine cancellation**: Each function (`loadFeed`, `createTextStatus`, `getViewers`, `deleteStatus`) launches a `viewModelScope.launch` without storing the `Job`. Rapid sequential calls (e.g., user taps rapidly) result in overlapping requests whose responses can arrive out of order.

6. **Type mismatch `feed` / `Map`**: As noted in Bugs, `StatusFeedScreen` expects `Map<String, List<StatusFeedEntry>>` for grouping, but `StatusViewModel` stores a flat list. The compiler should catch this unless the ViewModel is converted to `StateFlow<Map<...>>` somewhere. If this compiles, there is a silent type coercion happening that needs investigation.

---

## Summary

| Category | Status |
|----------|--------|
| Security | **Critical gaps** — privacy selection is fake (no contact list transmission), media status uses random UUIDs, no expiration tracking |
| Bugs | **High severity** — wrong response field names (`statuses` vs `feed`, `viewers` vs `views`), `"me"` userId heuristic, type mismatch feed vs Map |
| Completeness | **Half-implemented** — no selected-contact picker, no media upload, no expiration cleanup, no viewer list UI, no single-status fetch |
| Code Quality | **Needs work** — duplicated parsing, data model design flaw (`StatusPrivacy.Selected`), Android logging, no request cancellation, singleton constructor |

### Priority Fixes (in order)

1. **Change `StatusPrivacy.Selected` to `data class Selected(val userIds: List<String>)` and add a contact picker UI in `StatusCreateScreen`.** Send `selected_contacts` in the POST body.
2. **Fix feed response field**: `json["statuses"]` should be `json["feed"]`. Verify against live server.
3. **Fix my status detection**: Do not rely on `userId == "me"`. Use a dedicated endpoint or include `is_mine: true` flag from server.
4. **Add media upload flow**: Do not use `UUID.randomUUID()`. Upload first via `POST /media`, get real media ID, then call `createMediaStatus`.
5. **Add `expiresAt` field to `StatusFeedEntry`**. Store it from POST response. Add local expiration cleanup via `WorkManager`.
6. **Fix viewer list field**: `json["viewers"]` should be `json["views"]` (verify against server).
7. **Add viewer list screen**: `getViewers()` exists but no route displays it. Wire `onViewInfo` in `StatusViewerScreen`.
8. **Replace `android.util.Log` with logging abstraction**.
9. **Add request cancellation**: Store `Job` for each in-flight request and cancel on new call.
10. **Add single status fetch**: `GET /v1/status/{status_id}`.
11. **Tests**: Current tests are stubs — `StatusViewModelTest` calls functions but asserts nothing. Add assertions for success and error paths, null inputs, missing fields.
