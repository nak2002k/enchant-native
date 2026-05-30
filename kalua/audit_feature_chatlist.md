# Audit Report: `feature:chat-list` Module

**Module path:** `/home/nsk/project/personal/enchant-native/feature/chat-list/src/main/java/org/enchant/chatlist/`

---

## CATEGORY 1: SECURITY

### Finding 1.1 — User ID leaked in snackbar notification (HIGH)

**File:** `ConversationListScreen.kt`, line 66

```kotlin
snackbarHostState.showSnackbar("New message from ${senderId.take(8)}...")
```

**Problem:** When a new WebSocket message arrives, the sender user ID (first 8 chars) is revealed
in a plain-text snackbar. This is personal identifying information.
**Recommendation:** Use the sender display name or "Someone" / "New message" without any user ID portion.

### Finding 1.2 — Silent exception swallowing hides security-relevant errors (MEDIUM)
**File:** `ConversationListViewModel.kt`, lines 90, 99, 120, 137, 157

```kotlin
catch (e: Exception) { android.util.Log.w("Enchant", "silent: ${e.message}") }
```

**Problem:** All API failures (archive, unarchive, mute, mark-read, refresh) are silently swallowed.
`markRead` failing silently = server never learns read status (privacy issue).
Archive/unarchive failing silently = local/server state divergence with no user feedback.
**Recommendation:** Surface non-transient failures (401/403) to the UI or at minimum log full exception.

### Finding 1.3 — No encryption indicator for offline queue (LOW)

**Files:** `ConversationListScreen.kt` line 51, referenced `OfflineQueue`
Pending queue counter shown with no indication of encryption at rest.

---

## CATEGORY 2: BUGS

### Finding 2.1 — Archive action always archives, never toggles (BUG)
**File:** `ConversationListScreen.kt`, line 188

```kotlin
onArchive = { viewModel.archiveConversation(conversation.id) },
```

Menu text shows "Unarchive" correctly when `conversation.isArchived` is true,
but the action is hardcoded to `archiveConversation` (sets archived=true only).
The user cannot unarchive from the UI.

**Also:** `ChatListNavBackStackExtensions.goToArchive()` adds `ArchiveList` nav key
but no handler in `ChatListNavDisplay` actually filters to archived conversations.

### Finding 2.2 — Mute toggle missing from UI (BUG)
**File:** `ConversationListScreen.kt` lines 342-348

`ConversationTile` shows "Muted" label when `conversation.isMuted` but the dropdown menu
has **no mute/unmute action**. The ViewModel has `muteConversation(id, until)` but it is never called.

### Finding 2.3 — Unread badge overflow for large counts (EDGE CASE BUG)
**File:** `ConversationListScreen.kt`, line 333

```kotlin
text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString()
```

The cap is "> 99" but a large count (e.g., 1000+) renders as "1000" which overflows the badge.

### Finding 2.4 — `ChatListNavDisplay` references non-existent `viewModel.state` (CRASH)
**File:** `ChatListNavDisplay.kt`, lines 30, 41

```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()
```

`ConversationListViewModel` has no `state` property. It only exposes individual
_conversations, _filter, _searchQuery, _unreadCount, _isRefreshing, _navigationEvent StateFlows.
This will not compile or will fail at runtime.

### Finding 2.5 — Test references non-existent `viewModel.currentFilter` (TEST BUG)
**File:** `ConversationListViewModelTest.kt`, line 66

```kotlin
assertEquals(UNREAD, viewModel.currentFilter.value)
```

The ViewModel exposes `filter` not `currentFilter`. Test does not compile.

### Finding 2.6 — Pin and mute operations not synced to server (BUG)
**File:** `ConversationListViewModel.kt` lines 103-122

`pinConversation` and `muteConversation` only call local repo methods.
No API call is made to sync pin/mute state to the server.

---

## CATEGORY 3: COMPLETENESS

### Finding 3.1 — No load-more / pagination in conversation list (MISSING)
**File:** `ConversationListScreen.kt`, line 176

`LazyColumn` renders all conversations from flow with no pagination.
Thousands of conversations cause performance issues.

### Finding 3.2 — No swipe gestures for archive/pin (MISSING)
Only long-press menu provides archive/pin/mute/delete actions.
Swipe gestures are standard in conversation list UIs.

### Finding 3.3 — Search does not cover participant names (INCOMPLETE)
**File:** `ConversationRepository.kt` line 238 - `searchConversations` searches
the conversations table without joining recipients/messages tables.
Searching a participant name will not return their conversation.

### Finding 3.4 — Archive list screen has no separate filtering (LOGIC BUG)
**File:** `ChatListNavDisplay.kt` lines 39-49

`ArchiveList` entry uses the same ViewModel instance and same `_conversations` state.
Navigating to archive shows whatever was loaded previously, not filtered archive list.

### Finding 3.5 — No search-specific empty state (MISSING UI)
When `searchQuery` is non-blank and results are empty,
only generic "No conversations yet" is shown — no "No results for X" variant.

---

## CATEGORY 4: CODE QUALITY

### Finding 4.1 — Hardcoded 300ms search debounce (MAGIC NUMBER)
**File:** `ConversationListViewModel.kt` line 78: `delay(300)`

### Finding 4.2 — Same-day timestamps show hours instead of time (SUBTLE BUG)
**File:** `ConversationListScreen.kt` line 415: `diff < 86400_000 -> "${diff / 3600_000}h"`

For messages received today, showing "3h" loses the actual time-of-day.
"14:30" would be more useful than "3h" for same-day messages.

### Finding 4.3 — Avatar initials from conversation ID not display name (POOR)
**File:** `ConversationListScreen.kt` line 272: `conversation.id.take(2).uppercase()`

UUID-style IDs produce non-meaningful initials. Should derive from display name.

### Finding 4.4 — Missing accessibility content descriptions (ACCESSIBILITY)
Avatar Text and unread badge Text lack contentDescription.

### Finding 4.5 — ViewModel exposes no error state for async operations (DESIGN)
**File:** `ConversationListViewModel.kt`
No isError / errorMessage StateFlow. User gets no feedback on failed operations.

### Finding 4.6 — `refresh()` re-launches flow collection for no reason (INEFFICIENCY)
**File:** `ConversationListViewModel.kt` lines 148-153

`refresh()` cancel+re-launches the conversations flow but ignores the API response.
The flow auto-re-emits anyway from the same ConversationDao query.

### Finding 4.7 — `refresh()` is never called; `_isRefreshing` is dead state (DEAD CODE)
**File:** `ConversationListScreen.kt`

`_isRefreshing` is displayed but `viewModel.refresh()` is not invoked anywhere in the UI.
No pull-to-refresh or button triggers it.
