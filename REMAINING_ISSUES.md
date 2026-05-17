# Enchant Native — Remaining Issues & Fix Plan

> **Generated:** 2026-05-17
> **Based on:** Codebase audit, BUILD_PHASES specs, CURRENT_PROGRESS.md, SUNDAY_AUDIT_PROGRESS.md
> **Build status:** `assembleDebug` passes. ~300 tests pass.
> **Estimated remaining work:** 2-3 months for production readiness

---

## Priority Legend

| Icon | Meaning |
|------|---------|
| 🔴 P0 | Ship-stopping — must fix before any release |
| 🟠 P1 | High — security or major feature gap |
| 🟡 P2 | Medium — important but not blocking |
| 🔵 P3 | Low — nice to have / polish |

---

## 🔴 P0 — Critical (Fix Immediately)

### P0-1: CryptoHelper uses AES-GCM instead of XChaCha20-Poly1305

**File:** `core/crypto/src/main/java/org/enchant/core/crypto/CryptoHelper.kt`

The spec requires XChaCha20-Poly1305 AEAD (libsodium `crypto_aead_xchacha20poly1305_ietf_encrypt`). The code uses `AES/GCM/NoPadding` via Java `Cipher`. This is wrong and fails to match the backend's AEAD scheme.

- `encryptAesGcm()` → should be `encryptXChaCha20Poly1305()`
- `decryptAesGcm()` → should be `decryptXChaCha20Poly1305()`
- DoubleRatchet also calls these internally

**Fix:** Replace AES-GCM with XChaCha20-Poly1305 using Bouncy Castle's implementation (`org.bouncycastle.crypto.modes.XChaCha20Poly1305`).

---

### P0-2: SodiumProvider tries to load `libsodium.so` — doesn't exist

**File:** `core/crypto/src/main/java/org/enchant/core/crypto/SodiumProvider.kt`

`System.loadLibrary("sodium")` is called but no `libsodium.so` is bundled in the project. The native lib directory only has `libsqlcipher.so`, `libjingle_peerconnection_so.so`, and `libandroidx.graphics.path.so`.

**Fix:** Either bundle libsodium JNI or remove the loading attempt. The code already has Bouncy Castle — use it consistently. Remove the libsodium loading and have `sodiumMemZero` delegate directly to `CryptoHelper.zeroBytes()`.

---

### P0-3: `RtlSupport.mirrorLayoutDirection()` returns `Int` instead of Compose `Modifier`

**File:** `core/accessibility/src/main/java/org/enchant/core/accessibility/RtlSupport.kt`

```kotlin
fun Int.mirrorLayoutDirection(isRtl: Boolean): Int  // BROKEN — returns Int, not Modifier
```

This is supposed to be a Compose `Modifier` extension function. Returning `Int` makes it unusable in Compose UI.

**Fix:** Change to `fun Modifier.mirrorLayoutDirection(isRtl: Boolean): Modifier`

---

### P0-4: `search`, `qr_code`, `qr_scanner` routes show placeholder "Coming Soon"

**File:** `app/src/main/java/org/enchant/MainActivity.kt:714-728`

These 3 routes render a `Box` with centered text "Search coming soon" / "QR Code coming soon" / "QR Scanner coming soon". These are dead navigation targets.

**Fix:** Pop the back stack with a toast/snackbar.

---

### P0-5: `media_viewer` and `share_target` routes immediately pop

**File:** `app/src/main/java/org/enchant/MainActivity.kt:761-770`

These routes call `navController.popBackStack()` immediately, creating a flash-and-disappear UX if navigated to.

**Fix:** Same — pop with toast.

---

### P0-6: FLAG_SECURE never removed/re-applied in onPause/onResume

**File:** `app/src/main/java/org/enchant/MainActivity.kt:59`

`FLAG_SECURE` is set once in `onCreate()` but never:
- Removed in `onPause()` (per spec: remove for app switcher preview)
- Re-applied in `onResume()` (re-apply on sensitive screens)

**Fix:** Override `onPause`/`onResume` to manage FLAG_SECURE lifecycle.

---

### P0-7: `handleCallIntent()` is empty

**File:** `app/src/main/java/org/enchant/MainActivity.kt:110-113`

```kotlin
private fun handleCallIntent(intent: Intent?) {
    if (intent?.hasExtra("navigate_to") == true) {
        // Call screen navigation is handled reactively via CallViewModel
    }
}
```

Just a comment — no actual handling.

**Fix:** Implement deep link call intent parsing or remove the dead path.

---

## 🟠 P1 — High Priority

### P1-1: NavHost uses raw string routes instead of sealed `NavRoute` class

**File:** `app/src/main/java/org/enchant/MainActivity.kt`

All 48 `composable()` calls use string literals like `"chat_list"` instead of using the sealed `NavRoute` class in `core/navigation/NavRoute.kt`. The `NavHost.kt` file has a `toRouteString()` helper and `navigateTo(NavRoute)` but neither is used in MainActivity.

The spec says: "Navigation uses sealed route classes (no string routes)."

**Fix:** Inline the `NavRoute.toRouteString()` usage and create an `@Composable EnchantNavHost` function.

---

### P1-2: No actual `@Composable EnchantNavHost` function

**File:** `core/navigation/src/main/java/org/enchant/navigation/NavHost.kt`

The navigation module contains route definitions and helper extensions but no actual composable `NavHost`. The navigation host is defined inline in `MainActivity.kt`.

**Fix:** Factor it into the navigation module.

---

### P1-3: Many screens render with `emptyList()` / placeholder data

**File:** `app/src/main/java/org/enchant/MainActivity.kt`

Screens like `ContactListScreen(emptyList())`, `GroupListScreen(emptyList())`, `CallLogScreen(emptyList())`, `ChannelFeedScreen("")`, `GroupInfoScreen(null)`, etc. Are wired to no actual ViewModel data.

**Fix:** Wire screens to their ViewModels with real data sources.

---

### P1-4: Certificate pinning has placeholder hashes

**File:** `app/src/main/res/xml/network_security_config.xml`

```xml
<pin digest="SHA-256">base64_primary_cert_hash_here</pin>
```

These are placeholder values. Without real certificate hashes, pinning does nothing.

**Fix:** Add real SHA-256 certificate hashes or disable pinning until ready.

---

### P1-5: GroupEditor missing 12/17 functions

**File:** `feature/groups/src/main/java/org/enchant/groups/GroupEditor.kt`

CURRENT_PROGRESS.md says "all 17 GroupEditor functions verified" — verify this claim. The missing functions from the spec were: `updateGroupTimer()`, `updateAttributesRights()`, `updateMembershipRights()`, `setAnnouncementGroup()`, `revokeInvites()`, `banUser()`, `unbanUser()`, `ejectMember()`, `terminateGroup()`, `acceptInvite()`, `cycleGroupLinkPassword()`, `setJoinByGroupLinkState()`, `commitChangeWithConflictResolution()`.

---

### P1-6: GroupStateProcessor lacks conflict resolution

**File:** `feature/groups/src/main/java/org/enchant/groups/GroupStateProcessor.kt`

No 409 Conflict → re-fetch → retry (max 3) logic per spec.

---

### P1-7: Database DAO Flows are single-emit, not reactive

**Files:** `core/database/src/main/java/org/enchant/core/database/dao/*.kt`

`callbackFlow` is used but emits once. The spec requires reactive Flow via a `DatabaseNotifier` trigger-based system (which CURRENT_PROGRESS.md claims was added — verify).

---

### P1-8: Message search uses `LIKE` not FTS5

**File:** `core/database/src/main/java/org/enchant/core/database/dao/MessageDao.kt`

Search uses `LIKE '%query%'` which is a full table scan. Spec requires FTS5. Impact: significant at scale.

---

### P1-9: Backup `restoreBackup()` may be missing

**File:** `feature/backup/src/main/java/org/enchant/backup/BackupViewModel.kt`

The audit flagged `restoreBackup()` as missing. Verify and implement if so.

---

### P1-10: No test coverage for critical paths

~715 tests missing. 0 tests for auth screens, chat data layer, settings screens, etc.

---

## 🟡 P2 — Medium Priority

### P2-1: Sealed Sender not implemented
### P2-2: Multi-device sync not implemented
### P2-3: PQXDH not implemented
### P2-4: Safety Number verification UI minimal
### P2-5: SMS auto-fill for OTP
### P2-6: Biometric integration in AppLock
### P2-7: Real-time Location Sharing
### P2-8: No session locking for thread safety

---

## Fix Execution Log

| # | Issue | Status | Date |
|---|-------|--------|------|
| P0-3 | RtlSupport return type | ✅ Fixed 2026-05-17 | |
| P0-1 | AES-GCM → XChaCha20-Poly1305 | ✅ Fixed 2026-05-17 | |
| P0-2 | SodiumProvider libsodium loading | ✅ Fixed 2026-05-17 | |
| P0-4 | Search/QR placeholder routes | ✅ Fixed 2026-05-17 | |
| P0-5 | media_viewer/share_target stub routes | ✅ Fixed 2026-05-17 | |
| P0-6 | FLAG_SECURE lifecycle | ✅ Fixed 2026-05-17 | |
| P0-7 | handleCallIntent empty | ✅ Fixed 2026-05-17 | |
| P1-3 | Wire screens to ViewModels (10 screens) | ✅ Fixed 2026-05-17 | |
| P1-8 | Add FTS5 migration for message search | PENDING | |
| P1-7 | Fix database search Flows to be reactive | PENDING | | |
