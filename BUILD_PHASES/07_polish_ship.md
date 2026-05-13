# Phase 7 — Polish & Ship

## Overview

Performance optimization, accessibility, internationalization, crash handling, ProGuard/R8, release preparation, ANR monitoring, StrictMode, edge-to-edge, and the Navigation architecture (Signal's sealed route pattern with multi-pane support).

**Estimated files:** 15 files

---

## Navigation Architecture

Signal uses a sealed route class pattern with multi-pane (tablet) support via Material3 Adaptive API. We replicate this pattern.

### `core/navigation/src/main/java/org/enchant/navigation/NavRoute.kt`
```kotlin
sealed class NavRoute {
    // Auth
    data object Splash : NavRoute()
    data object Welcome : NavRoute()
    data object PhoneEntry : NavRoute()
    data object OtpVerify : NavRoute()
    data object Permissions : NavRoute()
    data object ProfileSetup : NavRoute()
    data object UsernamePicker : NavRoute()
    data object KeyGeneration : NavRoute()
    data object PinCreation : NavRoute()
    data object RestorePrompt : NavRoute()

    // Main tabs
    data object ChatList : NavRoute()
    data object CallLog : NavRoute()
    data object StatusFeed : NavRoute()
    data object ChannelsFeed : NavRoute()
    data object Settings : NavRoute()

    // Chat
    data class Conversation(val conversationId: String) : NavRoute()
    data class Search(val conversationId: String? = null) : NavRoute()

    // Calls
    data class IncomingCall(val callId: String) : NavRoute()
    data class OutgoingCall(val userId: String) : NavRoute()
    data class ActiveCall(val callId: String) : NavRoute()
    data class VideoCall(val callId: String) : NavRoute()
    data class GroupCall(val callId: String) : NavRoute()

    // Social
    data object Groups : NavRoute()
    data class GroupInfo(val groupId: String) : NavRoute()
    data object CreateGroup : NavRoute()
    data object Contacts : NavRoute()
    data object StatusCreate : NavRoute()
    data class StatusViewer(val statusId: String) : NavRoute()

    // Settings
    data object AccountSettings : NavRoute()
    data object SecuritySettings : NavRoute()
    data object PrivacySettings : NavRoute()
    data object NotificationSettings : NavRoute()
    data object AppearanceSettings : NavRoute()
    data object ChatsSettings : NavRoute()
    data object StorageSettings : NavRoute()
    data object About : NavRoute()
    data object BackupSettings : NavRoute()
    data object BlockedUsers : NavRoute()
    data object AppLock : NavRoute()

    // Misc
    data object Stickers : NavRoute()
    data class PollCreate(val conversationId: String) : NavRoute()
    data object LocationPicker : NavRoute()
    data object ShareTarget : NavRoute()
    data object QrCode : NavRoute()
    data object QrScanner : NavRoute()
    data class MediaViewer(val conversationId: String) : NavRoute()
}
```

### `core/navigation/src/main/java/org/enchant/navigation/NavHost.kt`
Type-safe navigation host using Jetpack Navigation Compose with sealed route classes.

| Function | Signature | Description |
|---|---|---|
| `EnchantNavHost` | `@Composable fun EnchantNavHost(navController: NavHostController, startRoute: NavRoute)` | Define all composable routes with type-safe args |
| `navigateTo` | `fun NavHostController.navigateTo(route: NavRoute)` | Type-safe navigation using `NavRoute` sealed class |
| `navigateAndClearStack` | `fun NavHostController.navigateAndClearStack(route: NavRoute)` | Navigate and pop everything (used after login) |

**Tests:** 5 — each route navigates correctly, back stack management, deep link handling

---

## File Manifest

### `app/src/main/java/org/enchant/EnchantApp.kt`
**Purpose:** Application class — init DI, crash reporting, StrictMode, LeakCanary.

| Function | Signature | Description |
|---|---|---|
| `onCreate` | `override fun onCreate()` | 1. Init DI → 2. Init Crashlytics → 3. Init LeakCanary (debug) → 4. Init StrictMode (debug) → 5. Init connectivity monitor → 6. Init notification channels |
| `onTerminate` | `override fun onTerminate()` | Cleanup DI |
| `initStrictMode` | `private fun initStrictMode()` | Enable StrictMode in debug builds only |
| `initNotificationChannels` | `private fun initNotificationChannels()` | Create MESSAGES, CALLS, VOICE notification channels on API 26+ |
| `initCrashReporting` | `private fun initCrashReporting()` | Init Crashlytics with PII scrubbing |

**Tests:** 4 — init lifecycle, StrictMode only in debug, notification channels created, crash reporting init

---

### `app/src/main/java/org/enchant/MainActivity.kt`
**Purpose:** Single activity entry — edge-to-edge, FLAG_SECURE, navigation host.

| Function | Description |
|---|---|
| `onCreate` | Set up Compose content, system bar insets, FLAG_SECURE |
| `onPause` | Remove FLAG_SECURE (for app switcher preview) |
| `onResume` | Re-apply FLAG_SECURE on sensitive screens |
| `onNewIntent` | Handle deep links and share intents |

**Edge-to-edge (API 35+):**
```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
```

**Tests:** 3 — FLAG_SECURE lifecycle, edge-to-edge insets, deep link handling

---

### `core/performance/src/main/java/org/enchant/core/performance/MessageCache.kt`
**Purpose:** LRU in-memory message cache to reduce DB reads.

| Function | Signature | Description |
|---|---|---|
| `getCachedMessages` | `fun getCachedMessages(conversationId: String): List<Message>?` | Return cached messages for conversation | Max 50 per conversation, max 20 conversations |
| `cacheMessages` | `fun cacheMessages(conversationId: String, messages: List<Message>)` | Cache with LRU eviction | Evict oldest conversation when at capacity |
| `invalidateConversation` | `fun invalidateConversation(conversationId: String)` | Clear cache for specific conversation | — |
| `clearAll` | `fun clearAll()` | Clear entire cache | — |

**Tests:** 5 — cache hit, cache miss, eviction, invalidation, clear

---

### `core/performance/src/main/java/org/enchant/core/performance/ImagePipeline.kt`
**Purpose:** Coil image loading with memory + disk cache.

| Function | Signature | Description |
|---|---|---|
| `loadImage` | `fun loadImage(context: Context, url: String, target: ImageView)` | Load image via Coil with memory + disk cache | Fallback placeholder on error |
| `prefetchImage` | `fun prefetchImage(context: Context, url: String)` | Preload into disk cache | — |
| `clearMemoryCache` | `fun clearMemoryCache(context: Context)` | Clear Coil memory cache | — |
| `clearDiskCache` | `fun clearDiskCache(context: Context)` | Clear Coil disk cache | — |

**Config:** Memory cache 25% of heap, disk cache 50MB

**Tests:** 4 — load success, load error, prefetch, cache clear

---

### `core/performance/src/main/java/org/enchant/core/performance/MessageTrimmer.kt`
**Purpose:** Periodic message trimming via WorkManager.

| Function | Signature | Description |
|---|---|---|
| `scheduleTrimming` | `fun scheduleTrimming(context: Context, retentionDays: Long)` | Schedule Daily WorkManager task | Default 365 days |
| `trimOldMessages` | `suspend fun trimOldMessages(retentionDays: Long)` | Delete messages older than retention period | Keep pinned and starred messages |

**Tests:** 3 — schedule, trim older, keep pinned/starred

---

### `core/accessibility/src/main/java/org/enchant/core/accessibility/AccessibilityDelegate.kt`
**Purpose:** Centralized accessibility content descriptions.

| Function | Signature | Description |
|---|---|---|
| `getContentDescriptionForMessage` | `fun getContentDescriptionForMessage(message: Message): String` | "Outgoing text message: Hello. Sent at 14:30. Delivered." | — |
| `getContentDescriptionForAvatar` | `fun getContentDescriptionForAvatar(userName: String, isOnline: Boolean): String` | "Alice's avatar. Online." | — |
| `getContentDescriptionForButton` | `fun getContentDescriptionForButton(action: String, state: String?): String` | "Send message button" or "Mute button, muted" | — |
| `getContentDescriptionForReaction` | `fun getContentDescriptionForReaction(emoji: String, count: Int): String` | "3 laughing face reactions. Tap to view." | — |

**Tests:** 4 — each description correct, handles edge cases (null names, zero counts)

---

### `core/accessibility/src/main/java/org/enchant/core/accessibility/RtlSupport.kt`
**Purpose:** RTL layout mirroring support.

| Function | Signature | Description |
|---|---|---|
| `isRtl` | `fun isRtl(context: Context): Boolean` | Check if current locale is RTL (Arabic, Hebrew, Farsi) |
| `mirrorLayoutDirection` | `fun Modifier.mirrorLayoutDirection(isRtl: Boolean): Modifier` | Compose modifier for RTL-aware layout |
| `getTextAlignment` | `fun getTextAlignment(isRtl: Boolean): TextAlignment` | Return appropriate text alignment |

**Tests:** 3 — detect RTL locale, mirror modifier, text alignment

---

### `core/crash/src/main/java/org/enchant/core/crash/CrashReporter.kt`
**Purpose:** Crash reporting with PII scrubbing.

| Function | Signature | Description |
|---|---|---|
| `init` | `fun init(context: Context)` | Init Crashlytics, set custom keys | Don't crash if Crashlytics unavailable |
| `logEvent` | `fun logEvent(name: String, data: Map<String, String>?)` | Log non-PII event | Strip UUIDs, base64, emails from data values |
| `logError` | `fun logError(message: String, throwable: Throwable?)` | Log non-PII error | Same scrubbing as logEvent |
| `logDecryptionFailure` | `fun logDecryptionFailure()` | Increment counter | No PII included |
| `setUserId` | `fun setUserId(userId: String?)` | Set Crashlytics user identifier | Null on logout |
| `sanitize` | `fun sanitize(input: String): String` | Strip UUIDs, base64, phone numbers, emails | Use regex replace |

**Regex patterns for scrubbing:**
```
UUIDs: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}
Base64 keys: [A-Za-z0-9+/]{40,}(=|==)?
Phone: \+[1-9]\d{1,14}
Email: [a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}
```

**Tests:** 6 — init, log event with scrub, log error, decryption counter, sanitize patterns, user ID set/clear

---

### `app/proguard-rules.pro`
**Purpose:** ProGuard/R8 rules for release build.

| Rule | Reason |
|---|---|
| `-keep class org.enchant.core.crypto.** { *; }` | Keep crypto classes (reflection/serialization) |
| `-keep class org.enchant.core.network.protos.** { *; }` | Keep protobuf classes |
| `-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }` | Keep protobuf generated methods |
| `-keep class org.webrtc.** { *; }` | Keep WebRTC JNI bindings |
| `-keepattributes *Annotation*` | Keep annotations for serialization |
| `-dontwarn com.google.protobuf.**` | Suppress protobuf warnings |

**Test:** Run release build + verify no missing class errors

---

### `app/src/main/AndroidManifest.xml` (updates)
| Setting | Value | Reason |
|---|---|---|
| `android:allowBackup` | `false` | Prevent message/key backup |
| `android:fullBackupContent` | `@xml/data_extraction_rules` | Exclude DB, prefs |
| `android:supportsRtl` | `true` | RTL support |
| `android:networkSecurityConfig` | `@xml/network_security_config` | Certificate pinning |
| `POST_NOTIFICATIONS` (API 33+) | Declared in manifest | Runtime permission |

### `res/xml/data_extraction_rules.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="enchant.db"/>
        <exclude domain="sharedpref" path="enchant_prefs.xml"/>
    </cloud-backup>
</data-extraction-rules>
```

### `res/xml/network_security_config.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">enchant.app</domain>
        <pin-set expiration="2027-01-01">
            <pin digest="SHA-256">base64_encoded_cert_hash_1</pin>
            <pin digest="SHA-256">base64_encoded_cert_hash_2</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

---

## Performance Targets

| Metric | Target | Measurement |
|---|---|---|
| Cold start (tap icon → conversation list) | < 2s | Perfetto trace |
| Message send latency (P50) | < 500ms | Custom metric |
| Message send latency (P99) | < 3s | Custom metric |
| Message list scroll jank | < 5ms frame time | FrameTimeline |
| Image decode (1080p) | < 200ms | Systrace |
| DB query (100 messages) | < 10ms | SQLite profiling |
| RSS memory (normal use) | < 80MB | Memory Profiler |
| RSS memory (chat with images) | < 50MB | Memory Profiler |

---

## Acceptance Criteria

- [ ] Cold start < 2 seconds
- [ ] Navigation uses sealed route classes (no string routes)
- [ ] Edge-to-edge rendering on API 35+
- [ ] FLAG_SECURE on chat screens
- [ ] RTL layout support detected and applied
- [ ] TalkBack content descriptions on all interactive elements
- [ ] Crashlytics init, no PII in crash reports
- [ ] StrictMode: zero warnings in debug
- [ ] LeakCanary: zero leaks
- [ ] ProGuard: release build succeeds
- [ ] Backup exclusion: keys/database not backed up
- [ ] Certificate pinning configured
- [ ] Notification channels created
- [ ] Message trimming scheduled via WorkManager
- [ ] Image pipeline with memory + disk cache
- [ ] All tests pass (target: 50+ tests)
- [ ] Final coverage: 95%+ core utilities, 90%+ services, all widget states

---

## Final Test Count

| Phase | Files | Tests |
|---|---|---|
| 01 — Foundation | 32 | 220+ |
| 02 — Auth & Onboarding | 35 | 120+ |
| 03 — Core Chat | 40 | 200+ |
| 04 — Calls | 18 | 70+ |
| 05 — Social | 30 | 100+ |
| 06 — Extended | 25 | 100+ |
| 07 — Polish & Ship | 15 | 50+ |
| **Total** | **~195 files** | **~860 tests** |

---

## Build Order

```
Phase 1: Foundation
    │
    ▼
Phase 2: Auth & Onboarding
    │
    ▼
Phase 3: Core Chat  ──────── Phase 4: Calls
    │                              │
    ▼                              │
Phase 5: Social ◄──────────────────┘
    │
    ▼
Phase 6: Extended
    │
    ▼
Phase 7: Polish & Ship
```
