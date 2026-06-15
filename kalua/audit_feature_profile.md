# Audit: `feature:profile` Module

**Module path:** `/home/nsk/project/personal/Enchant/frontend/feature/profile/`
**Files examined:**
- `ProfileViewModel.kt`
- `screens/ProfileScreen.kt`
- `ProfileViewModelTest.kt`

---

## 1. Security

### 1.1 Profile Data (avatar, about) — No E2E Encryption in Module

The module stores and transfers `avatarMediaId`, `displayName`, `username`, and `about` as plaintext JSON over the wire. There is no encryption of these fields within the profile module itself.

- `ProfileData` data class holds raw `displayName`, `about`, `avatarMediaId` — none are encrypted.
- `loadProfile()` fetches `/v1/profile/$userId` and parses JSON directly into `ProfileData`.
- `updateProfile()` sends `displayName` and `about` as plaintext JSON to `PUT /v1/profile`.
- `updateAvatar()` sends `avatarMediaId` to `POST /v1/profile/avatar`.

The core crypto layer (`SealedSender.encryptProfileData` / `decryptProfileData`) exists and is documented for encrypting profile fields before upload, but **the profile module never calls it**. The `ProfileData` class has no field for an encrypted payload, and neither `updateProfile` nor `updateAvatar` attempt encryption before sending.

> **Verdict:** Profile data — name, about, avatar reference — is sent and stored in plaintext on the server. If the server intended these to be E2E encrypted (matching Signal's architecture), the profile module's current implementation does not implement that contract. This is a significant security gap if E2E encryption is a requirement.

### 1.2 Profile Search — No Logging Observed

The `searchByUsername` function at line 129–159 accepts a `query` string and calls `apiClient.get("/v1/profile/search", mapOf("username" to query))`. No search query is logged to Logcat or any diagnostics. The test file also does not verify any logging behavior. This is acceptable.

However, there is no rate-limit or abuse protection on the client side (e.g., debouncing the search input) — the search fires on every keystroke with no delay, which could be exploited for username enumeration via rapid-fire requests.

### 1.3 Token Handling — Delegated to ApiClient

The ViewModel defers all auth token management to `ApiClient`, which uses `AuthInterceptor`. The interceptor handles JWT storage via `SecurePreferences` (good). The profile module itself does not touch raw tokens.

### 1.4 `about` Field — No Length Validation

`updateProfile` accepts any `String?` for `about` without clamping to a maximum length. The database schema in `AppDatabase.kt` has `about TEXT` with no explicit length constraint at the SQL level. Malformed or oversized `about` payloads could be sent.

### 1.5 `displayName` — Empty String Allowed

`updateProfile` sends `displayName` even if it is an empty string (`if (displayName != null) put("display_name", displayName)`). An empty display name will be accepted and stored without validation.

---

## 2. Bugs

### 2.1 Avatar Upload — No Upload Pipeline in Module

`updateAvatar(mediaId: String)` at line 104 only updates the avatar *reference* (`avatarMediaId`) on the server. It does **not** upload the actual image data. 

The flow for avatar change should be:
1. Client uploads image binary → receive `mediaId`
2. Client calls `updateAvatar(mediaId)` to associate the `mediaId` with the profile

But the module provides no function to upload image bytes. The actual image upload relies on an external caller (presumably `MediaService`). If a caller passes a `mediaId` that does not exist or has not been uploaded, the server will accept it — the `avatarMediaId` field will reference a non-existent media object.

There is also **no validation** that the `mediaId` format is correct or non-empty.

### 2.2 Avatar Download — Completely Absent

The module stores `avatarMediaId` but provides **no method to retrieve the actual avatar image bytes**. There is no `loadAvatar(mediaId: String): ByteArray` function anywhere in the profile module. The UI (`ProfileScreen.kt`) uses a fallback: initials rendered in a colored circle when no avatar image is available (lines 68–73). This is a basic placeholder, not a real avatar image.

Real avatar image loading would come from `MediaService.getBinary()` or similar, which is in the `chat` module — not accessible here. There is no explicit cross-module dependency or documentation on how avatar images should be fetched.

### 2.3 Race Condition in `updateProfile` / `updateAvatar`

Both `updateProfile` (line 92) and `updateAvatar` (line 117) call `loadMyProfile()` at the end of their success branch. Since these are `launch` coroutines, if the user rapidly triggers two successive updates (e.g., changes displayName then about), both coroutines fire simultaneously. Both will call `loadMyProfile()`. The second call's result will overwrite the first call's result in `_uiState.profile`.

This is not a crash, but it produces a subtle race: if the server processes them in order B→A, the UI will briefly show A then snap to B. There is no sequentialization (no mutex, no sequential coroutine execution).

### 2.4 `searchByUsername` — Silent Failure

On failure (line 151–157), `searchByUsername` sets `searchResults = emptyList()` without setting `error`. The user sees no indication that the search failed — they just get an empty result. This masks network errors.

### 2.5 `getBlockedUsers` — Unnamed Error Swallowed

Line 183: `catch (e: Exception)` silently swallows exceptions without recording an error. The `isLoading` flag is set to `false`, but `error` remains `null`. The UI has no way to show this failure.

### 2.6 `clearMessages` — `isEditing` Not Cleared

`clearMessages()` at line 217 only clears `error` and `successMessage`. It does **not** reset `isEditing` back to `false`. If the user enters edit mode and an error occurs, the UI may stay in edit mode even after the error is dismissed.

### 2.7 Back Navigation — Empty `onClick`

`ProfileScreen.kt` line 35: `IconButton(onClick = {})` — the back button does nothing. There is no `onBack` handler passed into the composable.

### 2.8 `setEditing` — No Validation

`setEditing(true)` can be called multiple times without guard. There is no check preventing re-entry into editing while already editing (though `isLoading` provides an indirect guard in some flows).

---

## 3. Completeness

### 3.1 Profile Viewing

- `loadProfile(userId)` — fetches and displays a user profile ✅
- `loadMyProfile()` — convenience for own profile ✅  
- Avatar display: falls back to initials (no real image loading) ⚠️
- About text: displayed correctly ✅
- Username: displayed with `@` prefix ✅

### 3.2 Profile Editing

- `updateProfile(displayName, about)` — sends updates via `PUT /v1/profile` ✅
- No validation on `displayName` length (could exceed server limits)
- No validation on `about` length (could exceed server limits)
- No optimistic UI update — the profile edits vanish immediately; UI waits for server response.

### 3.3 Avatar Upload

- `updateAvatar(mediaId: String)` accepts a media ID ✅
- No image upload/crop/resize in this module
- No upload progress feedback
- The UI "Tap to change" flow has no wired `onEdit` handler that triggers an actual upload flow.

### 3.4 Profile Search

- `searchByUsername(query)` — searches by username ✅
- Returns list of `ProfileData` (includes userId, displayName, username, about, avatarMediaId) ✅
- No debouncing — fires on every keystroke
- No minimum query length enforcement
- No result count pagination limit enforcement
- `searchResults` accumulate across searches (not cleared on new query until success) — line 149 overwrites correctly, but errors leave stale results visible

### 3.5 Block/Unblock

- `blockUser(userId)` ✅
- `unblockUser(userId)` ✅
- `getBlockedUsers()` ✅
- Blocked list displayed in `ProfileUiState` but **no UI screen in this module** to display the blocked user list or manage blocks from a profile screen.

### 3.6 `isEditing` Flag

Present in `ProfileUiState`, controlled by `setEditing()`. However, **no UI in this module** actually enters editing mode or shows an edit form. The only "edit" action available is the edit icon button that calls `onEdit()` (line 41), but the composable `ProfileScreen` is purely a display screen, not an edit form. There is no `EditProfileScreen` in this module.

---

## 4. Code Quality

### 4.1 ViewModel Design

The `ProfileViewModel` conflates multiple responsibilities:
- Profile viewing
- Profile editing
- Search
- Block/unblock management
- Edit mode state

This violates **Single Responsibility Principle**. A user-profile feature with this many actions would better be split into `ProfileViewModel` (display/view) + `ProfileEditorViewModel` (edit form) + `ProfileSearchViewModel` (search).

### 4.2 State Management

- `ProfileUiState` is a single flat state class holding profile data, search results, blocked users, edit mode, loading, and messages — all in one ✅ for simplicity but ⚠️ for scalability when more features are added.
- No sealed class for UI states (Loading/Content/Error). Using simple booleans (`isLoading`) mixed with nullable data (`profile: ProfileData?`) is common but harder to extend.
- No `UiEvent` or one-time event wrapper — `successMessage` and `error` are held in state and persist until explicitly cleared. If the consumer forget to call `clearMessages()`, stale messages persist.

### 4.3 API Integration

- Direct use of `ApiClient.get/put/post/del` — no repository abstraction. Calls Leak from network layer into ViewModel.
- No pagination support for `searchByUsername` results.
- Response parsing uses manual JSON extraction (`json["key"]?.jsonPrimitive?.content`) — verbose and error-prone. A `kotlinx.serialization` `@Serializable` data class with a deserialized response would be safer.

### 4.4 Data Model Naming

`ProfileData` (line 16) vs `Profile` (imported from `org.enchant.core.model` in test file, line 11) — two different model classes for the same concept. No mapper between them. `ProfileData` is local to the profile module; `Profile` from core model is not used in the actual module code. This is a naming inconsistency.

### 4.5 Test Coverage — Severely Inadequate

The test file (`ProfileViewModelTest.kt`) contains **exactly 4 test cases**, all of which are hollow:

```kotlin
// Test body — completely empty
fun `load profile`() = runTest {
    viewModel.loadProfile("user-1")
    // NO ASSERTIONS
}
```

```kotlin
fun `block user`() = runTest {
    viewModel.blockUser("user-1")
    // NO ASSERTIONS
}
```

```kotlin
fun `ui state defaults`() = runTest {
    val state = viewModel.uiState.value
    assertNotNull(state)
    assertNull(state.profile)
    assertTrue(state.blockedUsers.isEmpty())
    // Only default-state assertions; no behavior assertions
}
```

**Violations of AGENT_QUALITY_RULES.md:**
- Rule 1: No tests for error branches (network failure, 401, 500), null inputs, empty queries, race conditions.
- Rule 4: Tests are empty bodies with no real assertions of behavior. Two tests verify nothing beyond calling a suspend function that runs network requests.

Missing test coverage:
- `loadProfile` — success path, error path, invalid JSON, empty response
- `updateProfile` — success, failure, null inputs
- `updateAvatar` — success, failure, empty mediaId
- `searchByUsername` — empty query (should clear results), blank query, query too short, results parsing, failure
- `getBlockedUsers` — success, failure
- `blockUser` / `unblockUser` — success, failure
- `setEditing` / `clearMessages` — state transitions
- Race condition between `updateProfile` + `loadMyProfile`
- `about` max-length boundary test
- `displayName` max-length boundary test

### 4.6 Dependency Injection

`ProfileViewModel` has a secondary constructor:
```kotlin
constructor() : this(org.enchant.core.network.ApiClient.getInstance())
```
This static Coupling to `ApiClient.getInstance()` makes the ViewModel harder to unit test in isolation. A proper DI framework (Hilt/Koin) would be preferred.

---

## Summary Table

| Category | Issue | Severity |
|---|---|---|
| Security | Profile data (name, about) sent in plaintext — E2E encryption not applied | High |
| Security | `about` has no length validation | Medium |
| Security | Search fires per keystroke with no debounce (username enumeration risk) | Medium |
| Bug | Avatar download completely absent — only initials fallback | High |
| Bug | `updateAvatar` only sets reference; no image upload method | High |
| Bug | Race condition: concurrent `updateProfile` / `updateAvatar` both call `loadMyProfile` | Medium |
| Bug | `searchByUsername` silently fails on error — no error shown to user | Medium |
| Bug | `getBlockedUsers` silently catches exceptions | Medium |
| Bug | `clearMessages` does not reset `isEditing` | Low |
| Bug | Back button `onClick = {}` — no navigation | Medium |
| Bug | `ProfileScreen` has no edit form body — `isEditing` flag unused | Medium |
| Completeness | No avatar image loading (download) | High |
| Completeness | No edit form UI (only display screen exists) | High |
| Completeness | No blocked-user list screen in module | Medium |
| Quality | `ProfileData` vs `Profile` naming inconsistency | Low |
| Quality | ViewModel conflates viewing + editing + search + block management | Medium |
| Quality | No sealed class for UI states | Low |
| Quality | Manual JSON parsing instead of `kotlinx.serialization` | Medium |
| Quality | Test coverage: 4 tests, 2 are empty bodies, 1 only checks defaults | High |
| Quality | Static `ApiClient.getInstance()` in secondary constructor | Medium |

---

## Recommendations (Priority Order)

1. **Add avatar image loading** — either expose `MediaService.loadAvatar(mediaId)` to the profile module or add a `loadAvatar(mediaId: String): ByteArray?` function in `ProfileViewModel`.
2. **Wire up edit form UI** — `ProfileScreen` shows an edit icon but has no edit mode. Either implement `EditProfileScreen` or remove the `isEditing` state.
3. **Add E2E encryption for profile fields** — if Signal-style E2E encrypted profiles are intended, `SealedSender.encryptProfileData`/`decryptProfileData` must be called in `updateProfile` and `loadProfile`. This requires a profile key exchange flow.
4. **Fix/expand test coverage** — current tests are non-functional. All public methods need happy-path + error-path assertions.
5. **Add debounce to `searchByUsername`** — minimum 300–500ms debounce to prevent usernameEnumeration via rapid requests.
6. **Isolate `isEditing` cleanup** — `clearMessages()` should also `setEditing(false)`.
7. **Replace manual JSON parsing with `@Serializable` data classes** — safer, compile-time verified.
8. **Validate field lengths** — `displayName` and `about` should be clamped before sending.
9. **Replace static `ApiClient.getInstance()`** with constructor injection for testability.
