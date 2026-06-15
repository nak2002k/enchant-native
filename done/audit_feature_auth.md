# feature:auth Audit

## Security Issues

### Critical
1. **PIN stored as SHA256 only (no key derivation)**
   - `TwoStepPinScreen.kt` lines 33, 44, 51, 165-168: PIN is hashed with plain SHA256. No salt, no Argon2/bcrypt.
   - `AppLockScreen.kt` lines 42-43, 46: Same issue - PIN hashed with plain SHA256.
   - A dictionary or rainbow table attack would be trivial. Each PIN has only ~10^6 possibilities.

2. **PIN transmitted to server in hash form**
   - `TwoStepPinScreen.kt` line 51: `put("pin", sha256(pin))` sends the hash to the server. If the server stores this hash, there is no protection against server-side compromise - an attacker with database access can verify any 6-digit PIN.

3. **OTPs and sensitive identifiers logged**
   - `OtpVerifyScreen.kt` line 63: `Log.w("OtpVerify", "SMS retriever not available: ${e.message}")` - could log phone number in exception message.
   - `OtpVerifyScreen.kt` line 88: The `identifier` (phone number) is displayed in the UI text "Enter the code sent to $identifier" - this is expected UX but worth noting.

4. **Biometric authentication with no fallback limit**
   - `AppLockScreen.kt` line 50: `authenticateWithBiometric()` never locks out the user. An attacker with physical access could indefinitely try PINs after failing biometric.

### Moderate
5. **No secure text field for PIN entry**
   - `TwoStepPinScreen.kt` and `AppLockScreen.kt`: PIN digits are displayed as filled circles but use `FilledTonalButton` - the button text is still in the view hierarchy. A screen reader or viewservice could read the PIN. Should use `android.view.inputmethod.C editable` or custom secure view.

6. **SMS OTP auto-read without user confirmation**
   - `OtpVerifyScreen.kt` lines 47-51: OTP is auto-filled and immediately submitted without requiring user to press submit. This is a race condition where the code might be submitted before the user has a chance to verify.

7. **No rate limiting on OTP requests visible in UI**
   - `OtpVerifyScreen.kt` lines 126-140: Countdown is 30s after first load, but the `requestOtp` path does not show remaining attempts or enforce lockout after repeated failures.

## Bugs

1. **Race condition: OTP auto-submit vs manual entry**
   - `OtpVerifyScreen.kt` lines 47-51: If the SMS arrives during manual entry, the coroutine at line 48 checks `if (code.length < 6)` but then immediately overwrites `code` and calls `onCodeSubmitted`. This can cause a race where the user's partial input is lost when SMS fills it.

2. **Countdown reset only on explicit resend, not after timeout**
   - `OtpVerifyScreen.kt` lines 70-75: The `LaunchedEffect` countdown runs once on composition. After 30s, it stops. If user does not press resend (e.g., they just wait), the countdown never resets to show the next possible resend time if the screen is still visible. The countdown in lines 132-137 only starts on explicit resend press.

3. **KeyGenerationScreen progress check fires immediately**
   - `KeyGenerationScreen.kt` lines 19-23: `LaunchedEffect(Unit)` checks `if (progress >= 1f && !isError)` immediately on composition. If `progress` is already 1f (e.g., from previous run), `onKeysGenerated()` fires on every recomposition. However, since `progress` is an input parameter this is less severe in practice.

4. **TwoStepPinScreen onPinCreated receives plaintext PIN**
   - `TwoStepPinScreen.kt` line 60: `onPinCreated(pin)` passes the plaintext PIN back to the caller, which then navigates to KeyGeneration. The plaintext PIN lives in memory longer than necessary.

5. **ProfileSetupScreen ignored avatar and profile data**
   - `ProfileSetupScreen.kt` lines 58: `onProfileDataEntered` is called with empty string `""` for username, empty `""` for about, and `null` for avatar. This is a placeholder where real profile data should flow, but the data is discarded.

6. **ProfileSetupScreen onProfileDataEntered ignores parameters**
   - Line 114-116 in `AuthNavDisplay.kt`: When `onProfileDataEntered` fires, it calls `viewModel.updateProfile("", "", null)` ignoring all the actual profile data passed from the screen.

7. **RestorePromptScreen onRestore is empty**
   - `AuthNavDisplay.kt` line 139: `onRestore = { }` - the restore action is a no-op.

8. **PermissionsScreen doesn't actually request permissions**
   - `PermissionsScreen.kt`: The screen describes permissions but never actually calls `Activity.requestPermissions()` or similar. Clicking "Continue" just navigates forward without granting any runtime permissions.

9. **AppLockScreen biometric enabled flag set before verification completes**
   - `AppLockScreen.kt` lines 55-56: `SecurePreferences.putBoolean("applock.biometric", true)` is called in `onAuthenticationSucceeded`, but this is inside the callback. If the process dies after this line but before `onVerified()`, the flag is set incorrectly on next launch.

## Completeness Gaps

1. **No actual biometric enrollment Check**
   - `AppLockScreen.kt` lines 38-40: `canAuthenticateWithBiometric` checks if the device CAN authenticate, not if biometric is ENROLLED. Should check `BiometricManager.canAuthenticate(...BIOMETRIC_STRONG)` includes enrollment check.

2. **No username availability check wired up**
   - `UsernamePickerScreen.kt` line 17: The `onCheckAvailability` callback always returns `true` (line 125 in AuthNavDisplay). The debounced availability check is in the screen but the actual network call is stubbed.

3. **No actual OTP request wired to phone number**
   - `PhoneEntryScreen.kt` line 73: `onPhoneNumberSubmitted` calls `backStack.add(AuthNavKey.OtpVerify(identifier = phone))`. It navigates to OTP screen but `requestOtp` is only called on resend (line 90 in AuthNavDisplay). The initial OTP request is never made.

4. **No key generation progress feedback**
   - `KeyGenerationScreen.kt` lines 15-17: The screen receives `progress`, `isError`, `errorMessage` as parameters but nowhere in `AuthNavDisplay` are these wired to actual progress updates from the ViewModel.

5. **No TwoStepPin flow in navigation**
   - `AuthNavDisplay.kt` lines 106-109: `TwoStepPin` entry exists but is never added to the back stack in the normal registration flow (`Welcome → Permissions → PhoneEntry → OtpVerify → KeyGeneration → ...`). Two-step PIN is not connected to any entry point.

6. **No auth state observation driving the UI**
   - `AuthViewModel.kt` exposes `registrationState` and `authState` but `AuthNavDisplay.kt` does not observe these. When OTP verification completes or key registration finishes, the navigation does not automatically advance based on auth state changes.

7. **No logout flow handling**
   - `AuthViewModel.kt` has a `logout()` function but there is no UI to trigger it and no navigation back to Welcome on logout.

8. **CountryCodePickerScreen does not provide actual code**
   - `AuthNavKey.kt` lines 11-13: `Permissions(nextRoute: AuthNavKey)` serializes the `nextRoute` which would require serializing a NavBackStack entry - this could fail at runtime for deep navigation.

## Code Quality Issues

1. **AuthNavDisplay is a 150-line God Composable**
   - Should be split: each screen entry could be its own composable function, and the navigation logic could be extracted to a dedicated navigator class.

2. **AuthViewModel delegates 100% to AuthManager**
   - No local UI state. All flows call through to `AuthManager`. Cannot test any UI state transitions without mocking AuthManager.

3. **No separation between UI state and business logic**
   - All screens use `remember { mutableStateOf(...) }` for local UI state, which is correct, but there is no `StateFlow` exposure for external observers (e.g., tests) to inspect UI state changes.

4. **Redundant `else -> ""` in when expressions**
   - `UsernamePickerScreen.kt` line 32 and `TwoStepPinScreen.kt` line 150: Empty string defaults that suppress warnings but can mask bugs.

5. **Hardcoded delay values**
   - `OtpVerifyScreen.kt` line 33: 30s countdown, line 131: 60s resend timer. `UsernamePickerScreen.kt` line 64: 300ms debounce. These should be constants or configurable.

6. **Inconsistent error handling**
   - `TwoStepPinScreen.kt` lines 53-55: Network failure is swallowed with just a log warning.
   - `OtpVerifyScreen.kt` lines 54, 63: Exceptions are caught and silently discarded with `catch (_: Exception)`.

7. **No tests for any screen or viewmodel**
   - No test files exist for the auth module. All screens should have compose tests verifying state transitions, error display, and user interactions.

## Recommendations (prioritized)

1. **[HIGH] Replace SHA256 PIN hashing with Argon2id or scrypt**
   - Use a proper key derivation function. Both `AppLockScreen` and `TwoStepPinScreen` must be updated together to maintain consistency.

2. **[HIGH] Wire up actual OTP request on phone submission**
   - `PhoneEntryScreen` when submitting phone number must call `viewModel.requestOtp(phone)` before navigating to OTP screen.

3. **[HIGH] Observe AuthState in AuthNavDisplay to drive navigation**
   - When auth state changes (OTP verified, keys registered), the NavDisplay should automatically navigate to the next screen based on the new state.

4. **[HIGH] Request actual runtime permissions in PermissionsScreen**
   - Call `activity.requestPermissions()` with the appropriate permission strings, handle the result callback, and conditionally navigate only when permissions are granted.

5. **[MEDIUM] Fix OTP race condition in OtpVerifyScreen**
   - Prevent auto-submit on SMS receive if user has already typed a code, or require user confirmation before submitting auto-detected codes.

6. **[MEDIUM] Wire up profile data flow**
   - `ProfileSetupScreen` should pass actual `displayName`, `about`, and `avatarUri` to `onProfileDataEntered`. The `AuthNavDisplay` handler should pass these to `viewModel.updateProfile()`.

7. **[MEDIUM] Implement username availability check**
   - `UsernamePickerScreen` currently stubs availability with `onCheckAvailability = { true }`. The actual check needs to call a network API and return real availability.

8. **[MEDIUM] Add biometric enrollment check**
   - Instead of `canAuthenticate`, check `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` return value distinguishes between "no hardware", "no biometric enrolled", etc. UI should guide user to enroll if not enrolled.

9. **[MEDIUM] Add rate limiting UI for OTP**
   - After N failed OTP attempts, show a lockout message. After M OTP requests, show a longer cooldown.

10. **[LOW] Split AuthNavDisplay into smaller composables**
    - Each screen entry (WelcomeEntry, PhoneEntryEntry, etc.) should be its own composable for readability and testability.

11. **[LOW] Add Unit tests for all screens**
    - Each screen should have a compose_test verifying happy path and error states.

12. **[LOW] Extract hardcoded delay values to constants**
    - OTP resend cooldown, username debounce delay, etc. should be constants at the top of the file or in a dedicated constants object.
