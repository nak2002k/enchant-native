# Audit: `feature:settings` Module

## Files Audited

| File | Lines |
|------|-------|
| `SettingsViewModel.kt` | 272 |
| `SettingsHomeScreen.kt` | 83 |
| `PrivacySettingsScreen.kt` | 146 |
| `NotificationsSettingsScreen.kt` | 162 |
| `AppearanceSettingsScreen.kt` | 112 |
| `SecuritySettingsScreen.kt` | 114 |
| `AccountSettingsScreen.kt` | 143 |
| `BlockedUsersScreen.kt` | 98 |
| `BackupSettingsScreen.kt` | 142 |
| `StorageSettingsScreen.kt` | 157 |
| `ChatsSettingsScreen.kt` | 138 |
| `AboutScreen.kt` | 82 |
| `SettingsViewModelTest.kt` | 122 |
| `BackupSettingsScreenTest.kt` | 66 |

---

## 1. Security

### Finding S-1: Privacy Settings Sent to Server for Sync

**Severity: Medium**

Privacy visibility settings (`lastSeenVisibility`, `onlineVisibility`, `avatarVisibility`, `aboutVisibility`, `readReceipts`) are sent to the server via `PUT /v1/settings/privacy` (SettingsViewModel lines 136-158). This is by design for cross-device sync. However:

- There is **no field-level encryption** applied to these values before sending — they travel over the network as plaintext JSON.
- There is **no audit trail** confirming the server honored the user's privacy request.
- If the server is compromised or the network man-in-the-middle'd, privacy preferences could be read.

**Recommendation:** Use an authenticated, encrypted channel (TLS + token auth is already in use via `ApiClient`). Log or verify that server-side enforcement matches client-side intent.

### Finding S-2: `onlineVisibility` Treated as Boolean but Server API Uses String

**Severity: Low (Potential Bug)**

In `SettingsUiState` line 40, `onlineVisibility: Boolean = true`, but in `loadSettings()` (line 80) it is parsed from JSON as a boolean primitive (`toBoolean()`). In `updatePrivacy()` (line 141) it is put into a JSON object as a boolean.

However, privacy visibility for `lastSeen`, `avatar`, and `about` uses string values (`"everyone"`, `"contacts"`, `"nobody"`). The `onlineVisibility` being boolean is inconsistent with the server API design philosophy implied by the other visibility fields. The server or a future API version may expect `"online_visibility": "contacts"` instead of a boolean. The test code also passes `org.enchant.core.model.Visibility.CONTACTS` (an enum string) for `onlineVisibility` even though the UI state declares it as Boolean.

**Recommendation:** Investigate whether the server truly accepts boolean for `online_visibility` or if it should be a string enum like the other visibility fields.

### Finding S-3: Security Settings Screen Uses Local `SecurePreferences` Only

**Severity: Low**

In `SecuritySettingsScreen.kt` (lines 21-24), the app lock state, biometric availability, safety number, and two-step status are all read from local `SecurePreferences` — they are **never synced to the server**. This is actually correct for security-sensitive settings that should remain on-device. However:

- If the user sets up App Lock on one device, it does **not** propagate to other linked devices.
- The safety number displayed is the locally stored `"UNVERIFIED"` default — it is never fetched or verified against a server-side value.

**Recommendation:** Document that security settings are intentionally device-local. Consider whether `safety_number` should be fetched from the server or derived from conversation key fingerprints.

### Finding S-4: Delete Account Sends Request with No Confirmation UI in ViewModel

**Severity: Medium**

`deleteAccount()` in `SettingsViewModel` (lines 251-267) fires a `DELETE /v1/account` request. The UI in `AccountSettingsScreen` does show a confirmation dialog (lines 127-142). However, the ViewModel method itself does not guard against re-entrant or double invocation — `_uiState.value.isProcessing` is set but the UI must enforce the dialog before calling this method. If `deleteAccount()` is called twice rapidly, it could send two delete requests.

** Recommendation:** Add a guard in `deleteAccount()` to return early if `isProcessing == true`.

### Finding S-5: BlockedUsersScreen Creates Its Own `ApiClient`

**Severity: Medium**

`BlockedUsersScreen.kt` (lines 21, 27) uses `remember { ApiClient() }` and calls `client.init()` directly in `LaunchedEffect`. This bypasses the dependency-injected singleton pattern used by `SettingsViewModel`. If the global `ApiClient` singleton holds auth tokens or session state, this local instance may be uninitialized or have stale state.

**Recommendation:** Inject the `ApiClient` or use the singleton pattern consistently across all settings screens.

---

## 2. Bugs

### Bug B-1: `loadSettings()` Silently Swallows All Failures

**Severity: High**

`loadSettings()` (lines 65-90) has an empty `onFailure` block (`onFailure = {}`). If the API call fails (network error, 401, 500), the UI state remains with defaults and the user sees no error message. The `error` field in `SettingsUiState` is never set.

**Recommendation:** At minimum, set `_uiState.value = _uiState.value.copy(error = it.message)` in the failure branch.

### Bug B-2: `updateFontSize()` Has No Server Call — Local State Only

**Severity: Medium**

`updateFontSize()` (lines 102-109) updates the local UI state but sends a `PUT /v1/settings/font-size` request. However, when `loadSettings()` runs, it NEVER reloads the `fontSize` from the server response — only parses it (line 75). So `fontSize` does sync to the server, but if the user opens the app fresh, the font size comes from the server correctly. The bug is that there is no confirmation the server call succeeded before clearing the "saving" state.

**Fix is adequate** — the real bug is that `SettingsUiState` has no `isUpdating` / `isSaving` flag for `fontSize` or `theme` changes, so the UI never shows "saving..." feedback.

### Bug B-3: `updateTheme()` — No Error Feedback

**Severity: Medium**

Similar to B-2, `updateTheme()` (lines 92-100) updates local state immediately and fires a background API call, but never sets `error` or `successMessage` on failure. The app theme changes instantly (via `AppThemeManager.setTheme(theme)`), so the user gets instant feedback — but if the server call fails, the local state is out of sync with server on next app restart.

### Bug B-4: `revokeDevice()` Re-Calls `loadDevices()` Without Clearing Device from State

**Severity: Low**

`revokeDevice()` (lines 194-211) sets `isProcessing = true`, sends the delete request, then on success calls `loadDevices()` again. However, the device being revoked is still in `_uiState.value.devices` while the revoke is in flight. If `loadDevices()` is slow, the UI shows the device briefly before it disappears.

**Recommendation:** Filter out the revoked device from state immediately before making the API call, or show a loading indicator on the specific device row.

### Bug B-5: DND Day Selector — `index in dndDaysOfWeek` Logic Bug

**Severity: Medium**

In `NotificationsSettingsScreen.kt` (lines 141-150), the day toggle logic is:

```kotlin
val newDays = if (index in dndDaysOfWeek) {
    dndDaysOfWeek - index
} else {
    dndDaysOfWeek + index
}
```

Using `List<Int> - index` removes the element **at position `index`**, not the element **with value `index`**. If days are stored as 0-6 (Monday=0) and the user deselects Monday (index=0), then `dndDaysOfWeek - 0` removes the element at index 0 (which is Monday), which is correct. But if the list order changes or is non-sequential, this is fragile. More critically, `dndDaysOfWeek + index` **appends** the index rather than inserting at the correct sorted position, which could break the set's intended semantics.

**Recommendation:** Use a `Set<Int>` instead of `List<Int>` for `dndDaysOfWeek`:

```kotlin
val newDays = if (index in dndDaysOfWeek) {
    dndDaysOfWeek - index
} else {
    dndDaysOfWeek + index
}
```

(`Set<Int>` operations work correctly with `+` and `-` for membership add/remove.)

### Bug B-6: ChatsSettingsScreen `onDisappearingTimerChange` Callback Never Wired to ViewModel

**Severity: High**

`ChatsSettingsScreen.kt` accepts `onDisappearingTimerChange: (Int) -> Unit` as a parameter, but **there is no corresponding function in `SettingsViewModel`** to handle updating the default disappearing timer. The `SettingsViewModel` has no `updateDisappearingTimer()` or equivalent method, and the server API call path for this setting does not exist. This means the UI control in the Chats settings is completely non-functional.

**Recommendation:** Add `updateDisappearingTimer(seconds: Int)` to `SettingsViewModel` with a corresponding `PUT /v1/settings/chats` or `PUT /v1/settings/disappearing-timer` API call.

### Bug B-7: `autoDownloadWifi` and `autoDownloadCellular` in ChatsSettingsScreen Never Synced

**Severity: High**

`ChatsSettingsScreen` receives `autoDownloadWifi` and `autoDownloadCellular` as parameters, but these are **never persisted — no server call exists and no ViewModel function handles them**. Similar to B-6, the toggles are rendered but non-functional.

---

## 3. Completeness

### Missing Setting: Notification Sound / Vibration

**Severity: Low**

`NotificationsSettingsScreen` covers master toggle, message notifications, show preview, and Do Not Disturb scheduling. It does **not** include per-conversation notification sound selection or vibration pattern settings. These are standard in messenger apps and should be covered.

### Missing Setting: Language / Locale

**Severity: Low**

There is no language or locale setting in the settings module. This is a common and important setting for global apps.

### Missing Setting: Data Saver / Auto-Download Size Limits

**Severity: Low**

`ChatsSettingsScreen` has auto-download toggles but does **not** include file size thresholds (e.g., "Only auto-download under 5MB"). This is a standard data-saving feature.

### Missing Setting: Message Trim (Stub Only)

**Severity: Low**

`StorageSettingsScreen` (lines 106-118) shows a "Message Trim Settings" card that says "Coming soon" — the setting is not implemented. For a complete storage management suite, this should be functional with server-side support.

### Missing Setting: Backup Encryption Password

**Severity: Medium**

`BackupSettingsScreen` initiates backups but does **not** prompt for or set a backup encryption password. Backup encryption is mentioned in the AboutScreen ("Signal Protocol") implying E2E encryption should be standard. Without a dedicated backup password, backups may be less secure than the messaging itself.

### Missing Setting: Two-Factor Authentication Setup Flow

**Severity: Medium**

`SecuritySettingsScreen` shows a button `onSetupTwoStep` but the screen itself does not contain any UI for actually setting up two-step verification. It only shows the status ("Enabled" / "Not set up"). The navigation or dialog for actually setting up 2FA is missing.

---

## 4. Code Quality

### CQ-1: `NotificationPrefs` Method Has Wrong Parameter Names vs. Defined Parameters

**Severity: Low (Confusing API)**

`updateNotificationPrefs()` in `SettingsViewModel` (lines 111-131) uses parameter names `enabled`, `messageNotif`, `preview`. The UI state fields are named `notificationEnabled`, `messageNotifications`, `showPreview`. The function parameter names are abbreviated and inconsistent with the domain model.

### CQ-2: Inconsistent Error Handling Across Methods

**Severity: Medium**

Some methods (e.g., `revokeDevice()`, `deleteAccount()`, `clearCache()`) set `isProcessing` and `error` state. Other methods (`loadSettings()`, `updateTheme()`, `updateFontSize()`, `updatePrivacy()`, `updateNotificationPrefs()`) **never** set `error` even on failure. This is inconsistent UX — some operations show errors, others silently fail.

**Recommendation:** Standardize error handling: set `error = it.message` on every failure path, or create a sealed class result wrapper for shared behavior.

### CQ-3: `BlockedUsersScreen` and `BackupSettingsScreen` Create Their Own `ApiClient` Instances

**Severity: Medium**

Both `BlockedUsersScreen` (line 21) and `BackupSettingsScreen` (line 21) call `remember { ApiClient() }` instead of using the injected or singleton ApiClient. This:
- Bypasses any singleton initialization state (auth tokens, base URL)
- Creates duplicate instances in memory
- Makes testing harder

**Recommendation:** Use the singleton `ApiClient` or pass a shared `ApiClient` instance via a higher-level composable / injection mechanism.

### CQ-4: `SettingsViewModel` Default Constructor Creates Its Own `ApiClient`

**Severity: Medium**

`SettingsViewModel` constructor (lines 55-60) allows default construction that creates a new `ApiClient()` and calls `init()`. This is a code smell — callers should provide a fully initialized `ApiClient`. New instances created mid-stream could have duplicate initialization side effects.

### CQ-5: Test Coverage Is Shallow — No Assertions on Network Calls

**Severity: High**

`SettingsViewModelTest` has tests in `UpdatePrivacyTest` and `UpdateNotificationTest` that call `loadSettings()` then update methods, but:
- They **never assert** that `apiClient.put(...)` was called with the correct JSON payload
- They **never assert** the state changed correctly after a network success
- They **never assert** error handling behavior (that state is set correctly on network failure)
- Tests in `UpdatePrivacyTest`, `UpdateNotificationTest`, `UpdateThemeTest`, `UpdateFontSizeTest` simply call methods without any assertions on `coVerify` or state values

The tests are effectively smoke tests — they call the code but don't verify it works correctly. This is especially critical for security-critical code like privacy settings.

**Recommendation:** Add `coVerify` assertions for every API call to confirm payloads, and assert state changes on both success and failure paths.

### CQ-6: Test Uses Classes That Do Not Exist in the Codebase

**Severity: High**

`SettingsViewModelTest` lines 50-55 pass `org.enchant.core.model.Visibility.EVERYONE`, `org.enchant.core.model.Visibility.CONTACTS` etc. to `updatePrivacy()`, but these enum values **do not exist** in `org.enchant.core.model` — `DomainModels.kt` has no `Visibility` or `FontSize` enums. The method signature for `updatePrivacy()` takes `String` parameters, so the Kotlin compiler would coerce these enum names to strings, but `Theme.DARK` and `FontSize.MEDIUM` similarly do not exist.

The test will fail to compile against the actual codebase. Either the enums need to be added to `core/model`, or the tests need to be updated to use actual string values.

### CQ-7: `ChatsSettingsScreen` Callback Parameters Partially Unused

**Severity: Low**

Four of the six callback parameters in `ChatsSettingsScreen` (`onDisappearingTimerChange`, `onAutoDownloadWifiChange`, `onAutoDownloadCellularChange`, `onBackupSettings`) have no counterparts in `SettingsViewModel`. The callbacks are wired up in the UI but no action is dispatched to the server or local store.

---

## Summary Table

| ID | Severity | Category | Description |
|----|----------|----------|-------------|
| S-1 | Medium | Security | Privacy settings sent to server (intended but unverified encrypted transport) |
| S-2 | Low | Security | `onlineVisibility` is Boolean vs. String for other visibility fields |
| S-3 | Low | Security | Security settings are device-local only, unverified safety number |
| S-4 | Medium | Security | `deleteAccount()` lacks re-entrancy guard |
| S-5 | Medium | Security | `BlockedUsersScreen` creates its own uninitialized `ApiClient` |
| B-1 | High | Bug | `loadSettings()` silently swallows all failures |
| B-2 | Medium | Bug | `updateFontSize()` / `updateTheme()` give no saving/saved feedback |
| B-3 | Medium | Bug | Same as B-2 |
| B-4 | Low | Bug | `revokeDevice()` shows stale device list briefly |
| B-5 | Medium | Bug | DND day selector uses List instead of Set, wrong removal semantics |
| B-6 | High | Bug | Disappearing timer callback wired but ViewModel has no handler |
| B-7 | High | Bug | Auto-download toggles wired but ViewModel has no handler |
| C-1 | Low | Completeness | Missing notification sound/vibration settings |
| C-2 | Low | Completeness | Missing language/locale setting |
| C-3 | Low | Completeness | Missing auto-download size threshold |
| C-4 | Low | Completeness | Message trim setting is stub-only |
| C-5 | Medium | Completeness | Backup encryption password not handled |
| C-6 | Medium | Completeness | Two-step setup flow is placeholder |
| CQ-1 | Low | Quality | Inconsistent parameter naming in `updateNotificationPrefs` |
| CQ-2 | Medium | Quality | Inconsistent error handling across viewmodel methods |
| CQ-3 | Medium | Quality | `BlockedUsersScreen` and `BackupSettingsScreen` create own ApiClient |
| CQ-4 | Medium | Quality | `SettingsViewModel` default constructor anti-pattern |
| CQ-5 | High | Quality | Tests have no actual assertions on API calls or state |
| CQ-6 | High | Quality | Tests reference non-existent enum types |
| CQ-7 | Low | Quality | Partial unused callback parameters in `ChatsSettingsScreen` |

---

## Verdict

**Overall: Needs significant work before production.**

- **Security**: The privacy data is synced to the server over an encrypted channel — but there is no local validation that the server honored the settings, and the `OnlineVisibility` boolean vs. string inconsistency is a latent bug.
- **Bugs**: Three high-severity bugs: `loadSettings()` silently fails, disappearing timer and auto-download settings are completely non-functional (no ViewModel wiring). The DND day selector has a logic bug (using List instead of Set).
- **Completeness**: The module covers most major setting categories but leaves two high-impact features non-functional (disappearing timer, auto-download) and has several stub areas (message trim, 2FA setup, backup password).
- **Code Quality**: Tests do not assert behavior, creating high risk of regressions. Two settings screens bypass the singleton ApiClient pattern, and the SettingsViewModel has an anti-pattern default constructor.
