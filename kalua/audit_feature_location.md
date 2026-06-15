# Audit Report: `feature:location` Module

**Module path:** `feature/location/src/main/java/org/enchant/location/`
**Files examined:** `LocationPickerScreen.kt` (179 lines), `LocationPickerScreenTest.kt` (14 lines)
**Build config:** `build.gradle.kts` (46 lines)

---

## 1. Security

### Issues Found

| # | Severity | Issue | Location |
|---|----------|-------|----------|
| S1 | **HIGH** | **No permissions requested.** The screen uses `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` but never requests them. A `SecurityException` is caught in `requestCurrentLocation()` but the UI never informs the user that permission was denied. | `LocationPickerScreen.kt:90` |
| S2 | **HIGH** | **No permission check before location retrieval.** `requestCurrentLocation()` proceeds directly to `getLastKnownLocation()` without verifying permission state first. | `LocationPickerScreen.kt:73` |
| S3 | **MEDIUM** | **Sensitive coordinate data logged.** `Log.w("Location", "Location fetch failed: ${e.message}")` on line 93 can leak GPS coordinates in logcat on release builds. `Log` calls should be removed or guarded with `BuildConfig.DEBUG`. | `LocationPickerScreen.kt:93` |
| S4 | **MEDIUM** | **No expiration/sanitization of stored location.** The `address` string (derived from user-controlled geocoder output) is passed directly to `onLocationSelected` with no sanitization. | `LocationPickerScreen.kt:47`, `104` |
| S5 | **LOW** | **No location data clearing on back navigation.** When the user taps back, any selected location state persists in memory (latitude, longitude, address vars). | `LocationPickerScreen.kt:30-32` |

### Privacy Assessment

- Location data is transient (stored in Compose state only) — good.
- No location data written to disk or shared with analytics — good.
- Reverse geocoding converts coordinates to a human-readable address; this address is shared with the app via `onLocationSelected`. The caller is responsible for handling it securely.
- No network transmission of location data occurs within this module itself — good.

---

## 2. Bugs

| # | Severity | Bug | Location |
|---|----------|-----|----------|
| B1 | **HIGH** | **Race condition: `isGettingLocation` never reset on error paths.** If `requestSingleUpdate` throws (e.g., airplane mode), `onProviderDisabled` sets `isGettingLocation = false`, but if the provider is already disabled before the listener is registered, the function returns early at line 68 without resetting the flag (it does set it to false before `return`). However, if `getLastKnownLocation` throws `SecurityException` or any other exception before `requestSingleUpdate`, `isGettingLocation` stays `true` forever. | `LocationPickerScreen.kt:57-96` |
| B2 | **HIGH** | **`requestSingleUpdate` leaks the listener if the composable leaves composition.** The `LocationListener` is registered but never unregistered (no `removeUpdates` call). On configuration change or back navigation, the listener remains registered, potentially causing crashes or callbacks into a dead composable. | `LocationPickerScreen.kt:79-89` |
| B3 | **MEDIUM** | **Search field fires a geocoder request on every keystroke with no debounce.** Typing "San Francisco" fires 13 geocoder requests. Each blocks `Dispatchers.IO`. No cancellation of previous searches. | `LocationPickerScreen.kt:117-129` |
| B4 | **MEDIUM** | **`getLastKnownLocation` may return stale location (hours/days old).** The code shows this location immediately without indicating it may be cached. No timestamp validation. | `LocationPickerScreen.kt:73-77` |
| B5 | **LOW** | **`reverseGeocode` callback updates state after composable may be disposed.** If user navigates away during geocoding, `address = ...` at line 51 updates state in a detached composable. Generally harmless but indicates missing cancellation scope. | `LocationPickerScreen.kt:40-55` |

---

## 3. Completeness

### Location Sharing Integration

| Aspect | Status | Notes |
|--------|--------|-------|
| Location picker UI | Done | Basic picker with search and current location support |
| Integration with message sending | **MISSING** | No evidence that `LocationPickerScreen` is connected to any messaging layer. The `onLocationSelected` callback exists but there are no callers in the codebase. The module has no integration with the `:core:network` dependency declared in build.gradle. |
| Message envelope with location | **MISSING** | No code composing a message envelope (e.g., `Message` data class with `latitude`, `longitude`, `address` fields) exists in this module. |
| Location in message list UI | **MISSING** | No UI for displaying shared locations in a conversation. |
| Location persistence / history | **MISSING** | No local storage of shared locations. |
| Unsend / delete location message | **MISSING** | No revocation capability. |

### What exists vs. what is needed

```
What exists:                    What is missing:
─────────────────────────────   ──────────────────────────────────────────
LocationPickerScreen            Integration with ChatService / message send
- search address               LocationMessage data class + serialization
- get current GPS              Message envelope with location payload
- display lat/lng/address      UI to render location in message bubble
                               Permission request flow (user-facing dialog)
                               Cleanup of LocationManager listener
```

---

## 4. Code Quality

| Aspect | Rating | Notes |
|--------|--------|-------|
| **Architecture** | Poor | Single 179-line composable with inline business logic (geocoding, location management, state). No ViewModel, no separate intent/state model. Not testable beyond screenshot tests. |
| **State management** | Fair | Uses Compose `remember`/`mutableStateOf` correctly for UI state. However, the location listener is stored in an anonymous inner class with no lifecycle awareness — it outlives the composable. |
| **Error handling** | Poor | `SecurityException` caught but only silences the error. User gets no feedback. Network/geocoder exceptions are swallowed with a Log statement. No user-facing error UI. |
| **Resource management** | Critical bug | `LocationManager.requestSingleUpdate` listener never unregistered. Will leak and potentially crash. |
| **Testability** | Poor | The test file has a single empty test (`assertTrue(true)`) that explicitly admits no real testing. Dependencies like `Geocoder` and `LocationManager` are instantiated directly in the composable, making unit testing impossible without extensive refactoring. |
| **Naming** | Good | Function/variable names are clear and follow Kotlin conventions. |
| **Compose best practices** | Mixed | Correct use of `rememberCoroutineScope`, `withContext(Dispatchers.IO)` for blocking calls. However, missing `LaunchedEffect` for side effects, no `rememberUpdatedState` for callbacks, no debounce on search input. |

---

## Summary

| Category | Verdict |
|----------|---------|
| **Security** | FAIL — Missing runtime permission requests, sensitive data in logs, no input sanitization on address |
| **Bugs** | FAIL — Listener leak (critical), `isGettingLocation` stuck on exception paths, no search debounce |
| **Completeness** | FAIL — Picker UI exists but is not integrated with messaging; no message envelope, no location message rendering, no permission dialog |
| **Code Quality** | FAIL — Massive composable with no separation of concerns, no ViewModel, no cleanup of location listener, untested |

### Priority Fixes

1. **Listener leak** — Store listener in a `remember` var and call `locationManager.removeUpdates()` in a `DisposableEffect` cleanup.
2. **Permission flow** — Use `rememberLauncherForActivityResult` to request `ACCESS_FINE_LOCATION` before calling `requestCurrentLocation()`.
3. **Debounce search** — Wrap geocoder call in a `DebouncingCompletableJob` or use `LaunchedEffect` with `delay`.
4. **Reset `isGettingLocation`** — Use a `finally` block or structured concurrency (`CoroutineScope`) to ensure the flag is reset in all exit paths.
5. **Integration** — Add `LocationMessage` data class, wire `onLocationSelected` to a `ChatService` or equivalent so locations actually appear in messages.
