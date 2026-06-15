# Audit Report: `feature:channels`

**Module**: `/home/nsk/project/personal/Enchant/frontend/feature/channels/`
**Files examined**:
- `ChannelViewModel.kt`
- `screens/ChannelFeedScreen.kt`
- `screens/ChannelSearchScreen.kt`
- `ChannelViewModelTest.kt`
- `ChannelFeedScreenTest.kt`

---

## 1. Security

### Findings

| Issue | Location | Severity |
|-------|----------|----------|
| No admin/permission checks for channel creation | `createChannel()` | **High** |
| Channel creator not stored or validated on feed/posts | `loadFeed()`, `ChannelPost` data class | **Medium** |
| No role validation (admin vs subscriber vs viewer) | All APIs | **Medium** |
| `mediaIds` parsed but never validated or displayed | `ChannelPost` | **Low** |
| No rate limiting on subscribe/unsubscribe | `subscribe()`, `unsubscribe()` | **Low** |

**Details**:
- `createChannel()` sends a POST with only `name` and `description`. The server blindly accepts this—no user ID, no authentication token validation visible on the client. Client-side security relies entirely on the backend, but there is no explicit `Authorization` header being set ( ApiClient abstraction hides this). Unable to audit further without `ApiClient` source.
- `ChannelPost.authorId` is stored but never used to enforce edit/delete permissions. The UI shows `authorId.take(12)` as a display name, which is weak but not a security flaw per se.
- No `role` or `isAdmin` field exists in `Channel` or `ChannelPost`. Channel administration (pinning, moderating) is not modeled.

---

## 2. Bugs

### Findings

| Issue | Location | Severity |
|-------|----------|----------|
| `loadMore()` fails silently if cursor is null | `loadMore()` line 97-98 | **High** |
| `loadMore()` called even when already loading | `loadMore()` line 98 | **Medium** |
| No deduplication when appending new posts | `loadMore()` line 121 | **Medium** |
| `subscribe()` / `unsubscribe()` don't update `myChannels` or `discoverResults` | `subscribe()`, `unsubscribe()` | **Medium** |
| `searchChannels()` swallows all exceptions silently | `searchChannels()` line 285 | **Medium** |
| No error state on API failure for `createChannel` success path | `createChannel()` | **Low** |
| `createdAt` displayed as raw string, no formatting | `ChannelFeedScreen.kt` line 165 | **Low** |

**Details**:

1. **Silent failure in `loadMore()`**: The guard at line 97-98 returns early when cursor is null, but the UI never shows an error or "end of feed" indicator. The `isLoadingMore` flag is set at line 100 even if the early return already happened. While the flag prevents double-loads, this is opaque to the caller.

2. **No post deduplication**: When `loadMore()` appends posts via `_uiState.value.feed + posts.filter { !it.isPinned }`, there is no check for duplicate `postId`. If the server returns overlapping pages (e.g., cursor inconsistency), duplicate posts will appear in the UI.

3. **Subscribe state not propagated**: When `subscribe(channelId)` is called, only `channels` (the "my channels" list in UI state) is updated. `discoverResults` and `searchResults` are not updated, so the UI will show stale "Subscribe" buttons for the same channel in search results. Same issue for `unsubscribe()`.

4. **Search exception swallowed**: The `catch (_: Exception)` at line 285 silently clears results. No error message shown to user.

5. **Race condition in `subscribe()`**: No loading state is set during subscribe. Multiple rapid taps on "Subscribe" will fire multiple identical API requests with no debouncing or optimistic locking.

---

## 3. Completeness

### Findings

| Missing Feature | Impact |
|-----------------|--------|
| **Create post** — no function to create a new post in a channel | **High** |
| **Edit post** — no edit functionality | **Medium** |
| **Delete post** — no delete functionality | **Medium** |
| **Pin/unpin post** — only reading is implemented | **Medium** |
| **Admin management** — no add/remove admins, no role promotion | **Medium** |
| **Block channel** — no blocking/reporting | **Low** |
| **Channel avatar** — `avatarMediaId` is read but never loaded/displayed | **Low** |
| **Unsubscribe from `myChannels` list** — only subscribes update, no unsubscribes | **Medium** |
| **Load feed pagination info** — no `hasMore` boolean, only cursor | **Low** |

**Details**:

The module is **write-deficient**. It can read feeds and discover channels but has no way to create posts, edit channel metadata, or manage subscribers. The `ChannelPost` data class supports `isPinned` and `mediaIds` fields that are parsed but never used in the UI. The `Channel` data class has `avatarMediaId` that is never rendered—`ChannelSearchScreen` generates a placeholder from `channel.name.take(2).uppercase()` instead.

`loadFeed()` correctly extracts pinned posts and separates them, but there is no UI affordance for an admin to pin or unpin a post.

---

## 4. Code Quality

### Findings

| Issue | Location | Notes |
|-------|----------|-------|
| Duplicate JSON parsing logic | `loadFeed()`, `loadMore()` | Nearly identical post-parsing block repeated |
| Hardcoded Android logging | `loadMore()` lines 127, 132 | Uses `android.util.Log.w` directly |
| No sealed class for UI state errors | `ChannelUiState` | Uses nullable `error: String?` |
| No use of `Result` type for API responses | All functions | Uses manual `.fold()` with try/catch |
| Inconsistent state updates | `subscribe()` / `unsubscribe()` | Miss `myChannels` and `discoverResults` |
| No cancellation of in-flight requests | All `viewModelScope.launch` blocks | No ` Job.cancel()` on new operation |
| ViewModel uses deprecated parameterless constructor | `ChannelViewModel()` line 53 | Delegates to `ApiClient.getInstance()` at init time |
| UI state `cursor` not cleared on `loadFeed()` | `loadFeed()` | Old cursor not reset between channel switches |

**Details**:

1. **Duplicated JSON parsing**: Lines 66-75 (loadFeed) and lines 108-117 (loadMore) are nearly identical. This should be a private function like `parsePosts(jsonArray): List<ChannelPost>`.

2. **Hardcoded `android.util.Log`**: This ties the ViewModel to Android runtime. Should use a proper logging abstraction.

3. **`ChannelUiState` error handling**: Errors are just nullable strings. A sealed interface with typed errors (e.g., `sealed class ChannelError`) would be more maintainable.

4. **State update inconsistency**: `subscribe()` updates only `channels` in UI state. If the user has the subscribed channel in both `myChannels` and `discoverResults` (unlikely but possible), only one shows the subscribed state. Same for `unsubscribe()`.

5. **No request cancellation**: If `loadFeed()` is called twice rapidly (e.g., user switches channels quickly), both requests will complete and overwrite each other's state. The second response might arrive before the first, leaving the wrong feed displayed.

---

## Summary

| Category | Status |
|----------|--------|
| Security | Needs work — no client-side permission modeling, admin role missing |
| Bugs | Multiple — silent failures, race conditions, stale UI state |
| Completeness | Incomplete — no write operations (create/edit/delete posts), limited admin features |
| Code Quality | Moderate — duplicated code, Android dependencies in ViewModel, inconsistent state management |

### Priority Fixes

1. Add `createPost()` function with proper input validation.
2. Fix `subscribe()`/`unsubscribe()` to update `discoverResults` and `myChannels`.
3. Add deduplication in `loadMore()`.
4. Add cancellation/discard for in-flight requests when new ones start.
5. Model admin role explicitly in `Channel` data class.
6. Replace `android.util.Log` with abstraction.
7. Add `hasMore` flag or clear "end of feed" UX signal.
