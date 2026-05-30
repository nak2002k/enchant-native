# Audit: feature:registration Module

**Module Path:** `/home/nsk/project/personal/enchant-native/feature/registration/`

---

## 1. Security

### 1.1 Phone Number Validation

| Finding | Severity | Details |
|---------|----------|---------|
| NO SERVER-SIDE PHONE VALIDATION | **HIGH** | `PhoneNumberEntryViewModel` stores phone number in state but `Submit` is a no-op (`when (event) { ... PhoneNumberEntryEvent.Submit -> {} })`. No network call is ever made to send an OTP. The `repository: Any` in `RegistrationViewModel` is never used. |
| PHONE NUMBER LOGGED IN PLAINTEXT | **HIGH** | `RegistrationFlowEvent.E164Chosen.debugDescription` logs the raw E164 string: `"E164Chosen(e164=$e164)"`. Phone numbers should not appear in logs. |
| NO INPUT VALIDATION ON PHONE NUMBER | **MEDIUM** | `PhoneNumberEntryState.phoneNumber` is a raw `String` with no validation. `e164CharsOnly()` utility exists in `core/base` but is not used. |
| CAPTCHA TOKEN NOT VALIDATED | **MEDIUM** | `CaptchaScreenEvents.CaptchaCompleted` accepts any token string with no signature/format validation before being stored in `PhoneNumberEntryState.captchaToken`. |

### 1.2 OTP Handling

| Finding | Severity | Details |
|---------|----------|---------|
| STUB AEP USED FOR BACKUP RESTORE | **HIGH** | In `RegistrationNavigation.kt` (lines 491, 509, 526), `AccountEntropyPool("stub_aep")` is hardcoded stub data. No actual verification of the recovery key/AEP occurs. |
| VERIFICATION CODE NOT VERIFIED | **HIGH** | `VerificationCodeViewModel` has no state machine transition for a successful verification. `VerificationCodeEvent.Submit -> {}` is a no-op. The OTP code is never checked against a server-side session. |
| RESEND CODE / CALL ME ARE NO-OPS | **HIGH** | `VerificationCodeEvent.ResendCode -> {}` and `VerificationCodeEvent.CallMe -> {}` do nothing. OTP resend functionality is unimplemented. |
| NO RATE LIMITING ON OTP | **MEDIUM** | No rate limiting visible in the flow for SMS OTP requests. |

### 1.3 Key Generation / Cryptographic Material

| Finding | Severity | Details |
|---------|----------|---------|
| MASTER KEY HARDCODED AS EMPTY BYTE ARRAY | **CRITICAL** | In `RegistrationNavigation.kt` line 354: `MasterKeyRestoredFromSvr(MasterKey(byteArrayOf()))` -- the master key is a literal empty byte array. This is not a placeholder; it is actual cryptographic material being passed around as zeros. |
| NO SECURE RANDOM USAGE | **HIGH** | No `SecureRandom`, `KeyGenerator`, or any CSPRNG usage found in the entire module. The module relies entirely on external `repository` (type `Any`) to supply keys. There is no local key generation. |
| MASTERKEY `equals` DOES NOT VERIFY KEY CONTENTS | **LOW** | `MasterKey.equals()` only compares `value.contentEquals()` -- if two different `MasterKey` instances have the same byte array, they are equal. No timing-safe comparison. |
| AEP SERIALIZER ENCODES RAW STRING | **MEDIUM** | `AccountEntropyPoolSerializer` encodes `AccountEntropyPool.value` directly as a string. This is a security risk if the serialization is ever used across process boundaries. |

---

## 2. Bugs

### 2.1 Flow State Machine

| Bug | Location | Details |
|-----|----------|---------|
| `applyEvent` NEVER CALLS SUPER | `RegistrationViewModel.kt:28` | `processEvent` calls `applyEvent` but the base class `EventDrivenViewModel.processEvent` is abstract and `RegistrationViewModel` never calls `super.processEvent(event)`. The base class launches a coroutine on `eventChannel` but `processEvent` is an override that just mutates `_state.value` directly -- bypassing the channel-based processing entirely. |
| RESULT BUS TYPE UNCHECKED CAST | `RegistrationViewModel.kt:54` | `@Suppress("UNCHECKED_CAST")` to `(RegistrationFlowEvent.NavigateToScreen)` on `event.route as RegistrationNavKey` -- if navigation ever passes a non-`RegistrationNavKey` event, this will throw `ClassCastException` at runtime. |
| NAVIGATE BACK FROM FIRST SCREEN | `RegistrationViewModel.kt:38-41` | `NavigateBack` removes from backstack if size > 1, but if `isRestoringNavigationState = true`, the Welcome screen loads. If a user somehow triggers `NavigateBack` when only Welcome is on the stack (size == 1), nothing happens -- but if `isRestoringNavigationState = false` was toggled manually, no guard prevents empty backstack. |
| `PINEntryForSvrRestore` PIN ENTERED IGNORES PIN | `RegistrationNavigation.kt:352-354` | When a PIN is entered on the SVR restore screen, the PIN value is received (`PinEntered(val pin: String)`) but `pin` is completely discarded. The `MasterKeyRestoredFromSvr` is emitted with an empty byte array regardless of what the user typed. |
| REGISTRATION COMPLETE TWICE | `RegistrationNavigation.kt:447-448,452-453` | When `ArchiveRestoreOption.None` or `Skip` is selected, navigation goes to `Profile`, then immediately calls `RegistrationComplete`. But `ProfileScreen` also calls `onProfileComplete` or `onSkip` which both emit `RegistrationFlowEvent.RegistrationComplete`. No deduplication guard in `applyEvent`. |
| UNHANDLED EVENTS SWALLOWED SILENTLY | `RegistrationViewModel.kt:51` | `else -> state` silently swallows any unhandled event types. `PendingRestoreOptionSelected`, `UserSuppliedAepSubmitted`, `UserSuppliedAepVerified`, `RecoveryPasswordInvalid`, `MasterKeyRestoredFromSvr` have no matching case in `applyEvent`. The events are emitted but produce no state change and no error. |

### 2.2 Event Handling Issues

| Bug | Location | Details |
|-----|----------|---------|
| `eventChannel` NOT CONNECTED TO `processEvent` | `EventDrivenViewModel.kt:11-18` | The `init` block launches a coroutine iterating `eventChannel`, calling `processEvent`. But `RegistrationViewModel.processEvent` does not emit to the channel -- it calls `applyEvent` directly. The two patterns are mixed, creating a confusing dual-path event flow. |
| `PhoneNumberEntryEvent.Submit` IS NO-OP | `ScreenViewModels.kt:24` | The submit event is handled but does nothing. There is no state transition to a "submitting" or "sent" state. The `isLoading` flag in `PhoneNumberEntryState` is never set. |
| `VerificationCodeEvent.Submit` IS NO-OP | `ScreenViewModels.kt:110` | Same issue -- no code verification, no state transition. |
| COPIES ON STATE FLOW NOT ATOMIC | `ScreenViewModels.kt` | Multiple viewmodels do `_state.value = _state.value.copy(...)` which is not thread-safe if the state is read/modified concurrently. The `copy` creates a new object, but the flow's value is being mutated from potentially multiple coroutines. |

### 2.3 Race Conditions

| Bug | Location | Details |
|-----|----------|---------|
| MULTIPLE `LaunchedEffect` WITH SAME KEY | `RegistrationNavigation.kt:185-193,589-591` | Two separate `LaunchedEffect(Unit)` blocks in the same composable -- one for `finishRequests` collection (line 196) and one for `FullyComplete` callback (line 589). These run concurrently with no coordination. |
| BACKSTACK MUTATION NOT THREAD-SAFE | `RegistrationViewModel.kt:39-41` | `_state.value.backStack.toMutableList()` on line 39-41 creates a mutable copy, but if two navigation events fire concurrently, the backstack could be corrupted. No locking or atomic operation. |
| RESULT BUS SEND WITHOUT ACK | `RegistrationNavigation.kt:339` | `registrationViewModel.resultBus.sendResult(CAPTCHA_RESULT, event.token)` is fire-and-forget. No confirmation the result was received before navigating back. |

---

## 3. Completeness

### Registration Steps Checklist

| Step | Status | Notes |
|------|--------|-------|
| Welcome screen | ✅ Implemented | Present but empty UI (`{}`) |
| Permissions screen | ✅ Implemented | Present but empty UI (`{}`) |
| Phone number entry | ⚠️ Partial | Empty UI, no phone validation |
| OTP / SMS verification | ❌ Not implemented | `VerificationCodeViewModel` is a stub |
| Captcha | ⚠️ Partial | Screen exists but passes stub token |
| PIN creation | ⚠️ Partial | Screen exists, result is discarded |
| Archive restore selection | ⚠️ Partial | Screen exists, all options stubbed |
| Local backup restore | ⚠️ Partial | No passphrase processing, no file selection |
| Remote backup restore | ⚠️ Partial | Stub AEP used throughout |
| QR code transfer | ⚠️ Partial | Screen exists, all events no-ops |
| Profile creation | ⚠️ Partial | Screen exists, fires Completion immediately |
| Keys generated | ❌ Missing | No key generation in module; all stubs |

### Screens with Empty Bodies (`{}`)

All screen composables in `Screens.kt` have empty bodies:
- `PhoneNumberScreen`, `WelcomeScreen`, `PermissionsScreen`, `CountryCodePickerScreen`, `VerificationCodeScreen`, `CaptchaScreen`, `PinEntryScreen`, `AccountLockedScreen`, `PinCreationScreen`, `ArchiveRestoreSelectionScreen`, `LocalBackupRestoreScreen`, `EnterLocalBackupV1PassphraseScreen`, `EnterAepScreen`, `RemoteBackupRestoreScreen`, `QuickRestoreQrScreen`, `TransferScreen`, `ProfileScreen`

**Impact:** The entire UI is non-functional stubs. This is likely a "placeholders pending implementation" situation, but from an audit standpoint, zero screens are actually operational.

### Unimplemented Screen ViewModels / State Classes

| Screen | ViewModel State | Notes |
|--------|-----------------|-------|
| `PermissionsScreen` | No ViewModel | Only `onProceed: () -> Unit` callback |
| `CaptchaScreen` | No ViewModel | Direct event callback |
| `AccountLockedScreen` | No ViewModel | Direct event callback |
| `PinCreationScreen` | No ViewModel | Direct event callback |
| `ProfileScreen` | No ViewModel | Direct completion callback |
| `EnterLocalBackupV1PassphraseScreen` | No ViewModel | Direct callback |

---

## 4. Code Quality

### 4.1 State Machine Design

| Issue | Details |
|-------|---------|
| MIXED PATTERNS | `EventDrivenViewModel` uses a channel-based pattern (`eventChannel` + `processEvent`), but `RegistrationViewModel` overrides `processEvent` to mutate state directly via `applyEvent`. The init block in the base class is designed for a different usage pattern. The override breaks the base class contract. |
| STATE NOT IMMUTABLE | `RegistrationFlowState` is a `data class` with mutable `StateFlow`. The `_state.value = newState` pattern in `RegistrationViewModel` replaces the entire state object, but sub-objects (like `backStack`, `sessionMetadata`) may still have dangling references if not deep-copied. |
| NO STATE VALIDATION | `applyEvent` accepts any event and produces a state with no invariants enforced. There is no `require()` or validation on the resulting state (e.g., that `sessionE164` is a valid E164 format, or that `accountEntropyPool` is non-null after registration). |
| BACKSTACK AS PLAIN LIST | `NavBackStack<NavKey>` is used but `backStack.toMutableList()` is called in multiple places. NavBackStack may have its own semantics that are being bypassed by the direct list mutation. |

### 4.2 Event-Driven Patterns

| Issue | Details |
|-------|---------|
| `DebugLoggable` MIXED INTO EVENTS | `RegistrationFlowEvent` extends `DebugLoggable` but `debugDescription` is a computed property that could throw if `route::class.simpleName` is called on a null. There is no guarantee every `NavKey` subtype has a `simpleName`. |
| EVENT CHANNEL BUFFERED WITHOUT OVERFLOW HANDLING | `EventDrivenViewModel.eventChannel` uses `BUFFERED` (capacity unlimited on Buffered channel type) but `send` can still suspend if the buffer is full. No timeout or overflow handling. If many events are fired before processing, this could cause deadlocks. |
| RESULT BUS COUPLING | `ResultEffect` in `RegistrationNavigation` reads from a bus but the result types (`String?`, `CountryData?`) are dynamically passed without type safety. A wrong result type would cause a runtime crash. |

### 4.3 ViewModel Architecture

| Issue | Details |
|-------|---------|
| `RegistrationViewModel` HOLDS NO DEPENDENCIES | `private val repository: Any` is stored but never used. The ViewModel cannot function without an external caller providing all state transitions. |
| FACTORY CREATES UNCHECKED CAST | `RegistrationViewModel.Factory` always casts to `T` with `@Suppress("UNCHECKED_CAST")`. It will throw a cryptic `ClassCastException` if the requested `modelClass` is not `RegistrationViewModel`. |
| `RegistrationNavHost` PARAMETER `repository: Any` | The repository is `Any` (untyped). There is no contract defining what operations the registration flow expects from this repository. This is a type safety anti-pattern that will cause runtime failures if the wrong type is passed. |
| NO SCOPE OR LIFECYCLE MANAGEMENT | No `viewModelScope` is exposed. No `SavedStateHandle` is used for state restoration. No `CreationExtras` keys are defined. The `isRestoringNavigationState` flag in `RegistrationFlowState` is a manual proxy for something that `viewModelNavEntryDecorator` should handle. |

### 4.4 Other Code Quality Issues

| Issue | Location | Details |
|-------|----------|---------|
| `e164` DIRECTLY STORED FROM EVENT | `RegistrationFlowState.sessionE164` accepts any string. No normalization or validation when stored. |
| `stub_aep` STRINGS IN PRODUCTION CODE | `AccountEntropyPool("stub_aep")` appears 3 times in `RegistrationNavigation.kt`. These are not marked as TODO or FIXME. If the UI ever reaches these paths with real data, the stub will silently corrupt account recovery. |
| HARDCODED `timeRemainingMs` DISPLAY AS "ms" | `RegistrationNavigation.kt:366` displays `"${key.timeRemaining}ms remaining"` -- if `timeRemaining` is a seconds-based value, the display will be confusing (showing "5000ms" when it means 5 seconds). No unit conversion. |
| `CaptchaScreen` DISPLAY NAME IS `null` | In `CaptchaScreenEvents.CaptchaCompleted.debugDescription`, `route::class.simpleName` is called but `CaptchaScreenEvents` is the sealed interface, not the route. This is inconsistent with `NavigateToScreen` which correctly uses the route's class name. |
| `MasterKey` HAS NO `copy()` PROTECTION | `MasterKey` is a `data class` with a custom `equals`/`hashCode`/`toString`. The auto-generated `copy()` preserves the raw `ByteArray`. If `MasterKey` is ever serialized with `copy()`, the result may not protect the key. The `toString()` is good (returns `"MasterKey(hidden)"`), but other methods on `ByteArray` (like `decodeToString()`) could inadvertently expose the key. |

---

## Summary Table

| Category | Issue Count | Critical |
|----------|-------------|----------|
| Security | 8 | 1 (empty byte array MasterKey) |
| Bugs | 12 | 3 (stub OTP, swallowed events, no-super-call) |
| Completeness | 6 screens stubbed | N/A |
| Code Quality | 10 | 2 (mixed event patterns, Any-typed repository) |

**Overall Assessment:** The module is structurally laid out but the majority of the registration flow is non-functional stubs. Security-sensitive operations (OTP verification, key generation, PIN validation) are all no-ops. The state machine has several silent failure modes where events are processed without producing any state change or error. The `repository: Any` dependency is never used, suggesting the business logic for phone verification, OTP exchange, and key storage is planned to be injected but has not been implemented.
