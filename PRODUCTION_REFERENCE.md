# Production Reference — Kotlin Native E2EE Messaging App

> **Mandatory reading for every agent before writing code.**
> These 10 production rules are proven at scale by Signal Android (10+ years, millions of users).
> Every line of code must comply.

---

## Rule 1 — Background Execution

Android kills background processes aggressively. OEMs (Xiaomi, Huawei, Oppo) are worse than stock Android. Your app must survive.

| Requirement | Implementation | Signal's Approach |
|---|---|---|
| WebSocket persistence | **Foreground Service** with persistent low-priority notification | `IncomingMessageObserver.ForegroundService` + `MessageRetrievalThread` |
| Deferrable tasks | **WorkManager** for pre-key rotation, message trimming, backup | Custom `JobManager` (equivalent to WorkManager) |
| Message delivery trigger | **FCM as wake-up signal only** — never send payloads through FCM | `FcmReceiveService` → triggers WebSocket reconnect |
| Device reboot | **Boot Receiver** (`RECEIVE_BOOT_COMPLETED`) to restart services | `BootReceiver.java` |
| Network changes | **`ConnectivityManager.NetworkCallback`** (not deprecated `NetworkReceiver`) | Callback-based reconnection |
| Battery optimization | Guide user to disable battery optimization for the app | Settings intent with `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

**Implementation pattern:**
```kotlin
// Foreground Service for WebSocket
class WebSocketService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // Start WebSocket connection
        return START_STICKY
    }
}

// WorkManager for periodic tasks
val preKeyRotation = PeriodicWorkRequestBuilder<PreKeyRotationWorker>(30, TimeUnit.DAYS)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .build()
WorkManager.getInstance(context).enqueueUniquePeriodicWork("prekey-rotation", ExistingPeriodicWorkPolicy.KEEP, preKeyRotation)
```

---

## Rule 2 — Android 12/13/14/15 Compliance

Each Android version adds restrictions. Build for the latest, test on the oldest supported.

| Version | API | What Changes | Action Required |
|---|---|---|---|
| **Android 12** | 31 | SplashScreen API, notification trampoline ban, exportable manifest | Use `SplashScreen` API, `startActivity` only from Activity/Service |
| **Android 13** | 33 | `POST_NOTIFICATIONS` runtime permission, granular media permissions | Request notification permission during onboarding. Split media into `READ_MEDIA_IMAGES/VIDEO/AUDIO` |
| **Android 14** | 34 | Foreground service types required, runtime broadcast receivers restricted | Declare `android:foregroundServiceType="dataSync"` / `"microphone"` / `"connectedDevice"` in manifest |
| **Android 15** | 35 | Edge-to-edge mandatory (system bar insets), notification cooldown, 16KB page size | Handle `WindowInsets`, test native libs with 16KB page alignment |

**Foreground service type declaration (API 34+):**
```xml
<service
    android:name=".WebSocketService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
<service
    android:name=" .CallService"
    android:foregroundServiceType="microphone|connectedDevice"
    android:exported="false" />
```

**Notification permission flow (API 33+):**
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
}
```

**Edge-to-edge (API 35+):**
```kotlin
// Must be done in every Activity
WindowCompat.setDecorFitsSystemWindows(window, false)
// Handle insets in your views
ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
    v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
    insets
}
```

**16KB page size (API 35+):**
- Ensure any native libraries (libsodium, libsignal-client, etc.) are aligned to 16KB pages
- Test with `adb shell pm dump <package> | grep pageSize`
- If page size > 4KB, native libs may crash on older NDK builds

---

## Rule 3 — Device Fragmentation

Android runs on 24,000+ device models. OEMs modify behavior. Test accordingly.

| OEM | Known Issues | Workaround |
|---|---|---|
| **Samsung** | Deep Sleep puts app to sleep after 3+ days unused | Use `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, monitor with `isBackgroundRestricted()` |
| **Xiaomi** | Extreme background killing. Auto-start disabled by default | Detect MIUI, prompt user to enable autostart in settings |
| **Huawei** | No Google Play Services (international models) | No FCM → fallback to periodic REST polling every 30s |
| **OnePlus** | RAM optimization kills background services | Similar to Xiaomi, prompt for optimization exclusion |
| **Pixel** | Reference behavior, but still kills on low RAM | Standard handling |

**Detection pattern:**
```kotlin
fun isBackgroundRestricted(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.isBackgroundRestricted
}
```

**WebSocket keep-alive with jitter:**
```kotlin
private fun scheduleReconnect(attempt: Int) {
    val baseDelay = minOf(1000L * (1 shl attempt), 30_000L)  // 1s → 2s → 4s → ... → 30s cap
    val jitter = (baseDelay * 0.25 * Random.nextDouble()).toLong()  // ±25%
    val delay = baseDelay + if (Random.nextBoolean()) jitter else -jitter
    handler.postDelayed({ connect() }, delay)
}
```

---

## Rule 4 — Memory & Performance

Target metrics for a smooth messaging app:

| Metric | Target | Tool |
|---|---|---|
| RSS memory (normal use) | < 80MB | Android Studio Memory Profiler |
| RSS memory (message list with images) | < 50MB | Android Studio Memory Profiler |
| Cold start to ready state | < 2 seconds | Perfetto trace |
| Message list scroll jank | < 5ms frame time | Perfetto / FrameTimeline |
| Image decode time | < 200ms for 1080p | Systrace |
| Database query (100 messages) | < 10ms | SQLite profiling |

**RecyclerView with Paging 3:**
```kotlin
// Never load all messages into memory
class MessagePagingSource(private val db: MessageDatabase) : PagingSource<Int, Message>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Message> {
        val messages = db.getMessagesPaged(params.key ?: 0, params.loadSize)
        return LoadResult.Page(messages, prevKey = null, nextKey = messages.lastOrNull()?.id)
    }
}
```

**Image loading with Coil:**
```kotlin
// Memory cache: 25% of available heap
val imageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder(context)
            .maxSizePercent(0.25)
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("image_cache"))
            .maxSizeBytes(50 * 1024 * 1024)  // 50MB
            .build()
    }
    .build()
```

**SQLite WAL mode:**
```kotlin
db.execSQL("PRAGMA journal_mode=WAL")
db.execSQL("PRAGMA synchronous=NORMAL")
db.execSQL("PRAGMA foreign_keys=ON")
```

**LeakCanary in debug:**
```kotlin
// Only in debug builds
if (BuildConfig.DEBUG) {
    LeakCanary.config = LeakCanary.config.copy(
        retainedVisibleThreshold = 3
    )
}
```

---

## Rule 5 — Security (Android-Specific)

Non-negotiable security practices. Violations block the release.

| Requirement | Implementation | Why |
|---|---|---|
| **KeyStore** for identity keys | `KeyGenParameterSpec.Builder(context).setKeySize(256).setAlgorithm(Purposes.KEY_AGREEMENT)` | Hardware-backed, keys never leave secure hardware |
| **EncryptedSharedPreferences** | `EncryptedSharedPreferences.create(context, "settings", masterKey, PrefKeyEncryptionScheme.AES256_SIV, PrefValueEncryptionScheme.AES256_GCM)` | Replaces plain SharedPrefs for settings |
| **SQLCipher** for messages | `SQLiteDatabase.openOrCreateDatabase(file, password, null)` | Full-database encryption at rest |
| **Biometric unlock** | `BiometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)` | Verifies user identity before showing keys |
| **Certificate pinning** | `network_security_config.xml` with `<pin-set>` | Prevents MITM even if CA is compromised |
| **FLAG_SECURE** | `window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, ...)` | Blocks screenshots/screen recording on chat windows |
| **Tapjacking prevention** | `android:filterTouchesWhenObscured="true"` on all input fields | Prevents overlay attacks |

**Network Security Config:**
```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">your-backend.com</domain>
        <pin-set expiration="2027-01-01">
            <pin digest="SHA-256">base64_encoded_cert_hash_1</pin>
            <pin digest="SHA-256">base64_encoded_cert_hash_2</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

---

## Rule 6 — Storage & Data

| Requirement | Implementation |
|---|---|
| **Scoped Storage (API 30+)** | Use `MediaStore` for shared media, `ContentResolver` for SAF access |
| **Media cache** | LRU eviction, 100MB total cap. Periodic cleanup via WorkManager |
| **Backup exclusion** | `android:allowBackup="false"` in manifest. Keys and sessions must NEVER be backed up |
| **Message retention** | Auto-truncate after 12 months (configurable in settings 1/3/6/12 months/forever) |

**Backup exclusion:**
```xml
<application
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules">
</application>
```

```xml
<!-- res/xml/data_extraction_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="messages.db"/>
        <exclude domain="database" path="sessions.db"/>
        <exclude domain="database" path="keys.db"/>
        <exclude domain="sharedpref" path="signal_store.xml"/>
    </cloud-backup>
</data-extraction-rules>
```

**Message auto-truncate:**
```kotlin
class MessageTrimmingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val cutoffDate = System.currentTimeMillis() - (retentionDays * 86400000L)
        database.deleteMessagesOlderThan(cutoffDate)
        return Result.success()
    }
}
```

---

## Rule 7 — Crash & Error Handling

| Requirement | Implementation |
|---|---|
| **Crash reporting** | Firebase Crashlytics or Sentry. **Scrub ALL message content, keys, and personal data before sending** |
| **StrictMode in debug** | `StrictMode.enableDefaults()` in Application.onCreate() for debug builds |
| **Global crash handler** | `Thread.setDefaultUncaughtExceptionHandler()` for last-resort cleanup |
| **Graceful degradation** | Never show garbage data. Failed decryption → "Couldn't decrypt this message" with safety number change event |

**Crash report scrubbing:**
```kotlin
class ScrubbedCrashlyticsTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Remove any base64-encoded keys, message content, user IDs
        val scrubbed = message
            .replace(Regex("[A-Za-z0-9+/]{40,}={0,3}"), "[REDACTED_KEY]")
            .replace(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"), "[REDACTED_UUID]")
        Crashlytics.log(scrubbed)
    }
}
```

**StrictMode:**
```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
        .detectDiskReads()
        .detectDiskWrites()
        .detectNetwork()
        .penaltyLog()
        .build()
    )
}
```

---

## Rule 8 — User Experience

| Requirement | Target |
|---|---|
| **Cold start to ready** | < 2 seconds (WebSocket connected + conversations loaded) |
| **Zero-permission splash** | App works without any grants. Show cached data, settings, about |
| **Progressive permissions** | Notifications: ask on first incoming message. Contacts: ask on first "add contact". Camera: ask on first photo |
| **Full offline mode** | Cached messages readable. Compose messages offline. Auto-send when online. Show "Waiting for connection" not an error |
| **Accessibility** | TalkBack content descriptions on all icon buttons, action navigation, message item roles |
| **RTL support** | If targeting Arabic/Hebrew/Farsi, layout mirroring must work end-to-end |

**Progressive permission pattern:**
```kotlin
fun requestNotificationPermission(context: Context, activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return  // Not needed on older versions
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    // Call this when first message arrives, not on launch
    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
}
```

---

## Rule 9 — Release & Distribution

| Requirement | Implementation |
|---|---|
| **Play Store privacy policy** | URL pointing to privacy policy (mandatory for messaging apps) |
| **Data Safety section** | Accurate listing of what data is collected (minimal for E2EE) |
| **GDPR compliance** | Data export endpoint + account deletion that removes ALL server data |
| **ProGuard/R8** | Keep all JNI methods, protobuf classes, serialization. Full ProGuard test before release |
| **App signing** | Play App Signing (Google manages key). Upload key stored offline. **Losing the key = losing the app** |
| **API target** | Must target latest API within 1 year of release |
| **In-app updates** | `PlayCore` library with `immediate` update mode for security patches |

**ProGuard rules:**
```
# Keep libsignal JNI methods
-keep class org.signal.libsignal.** { *; }
-keep class org.thoughtcrime.securesms.service.webrtc.** { *; }

# Keep protobuf classes
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Keep serialization
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
}
```

---

## Rule 10 — Monitoring & Observability

| Metric | Target | Why |
|---|---|---|
| **WebSocket disconnect rate** | < 1 disconnect/device/hour | High rate = networking bug (proxy, DNS, timeout) |
| **Decryption failure rate** | < 0.1% of messages | Spike = protocol version mismatch or key corruption |
| **ANR rate** | < 0.5% (Android Vitals) | Main thread blocked by DB/network/crypto |
| **Crash-free session rate** | > 99.5% | Release quality gate |
| **Message delivery latency (P50)** | < 500ms | WS round-trip + decryption time |
| **Message delivery latency (P99)** | < 3 seconds | Worst-case (WS reconnect + retry) |

**Implementation:**
```kotlin
// Track decryption failures
class DecryptionMetrics {
    private val failures = AtomicInteger()
    fun recordFailure(envelopeType: String, error: String) {
        failures.incrementAndGet()
        Crashlytics.log("Decrypt failure: $envelopeType -> $error")
    }
    fun report() {
        Metrics.send("decryption_failures", failures.getAndSet(0))
    }
}
```

---

## Reference: Signal Android Implementation

For every rule above, Signal Android has a proven implementation. See the corresponding files in `LEADING_APPS_REFERENCE_MAP.md` for exact function-level references:

| Rule | Signal Files |
|---|---|
| Background Execution | `IncomingMessageObserver.kt`, `FcmReceiveService.java`, `BootReceiver.java` |
| Android API Compliance | `ApplicationContext.java`, `AppInitialization.java` |
| Device Fragmentation | `FcmFetchManager.kt`, `SignalCallManager.java` |
| Memory & Performance | `ConversationDataSource.kt`, `MessageDataFetcher.kt` |
| Security | `SignalBaseIdentityKeyStore.java`, `ReentrantSessionLock.java`, `ScreenSecurityService.java` |
| Storage & Data | `MessageTable.kt`, `ThreadTable.kt`, backup exclusions |
| Crash & Error | `MessageDecryptor.kt`, `MessageContentProcessor.kt` |
| UX | `ConversationViewModel.kt`, `ConversationListViewModel.kt` |
| Release | ProGuard rules in `app/proguard/` |
| Monitoring | `HealthMonitor.kt`, `SignalWebSocketHealthMonitor.kt` |

---

*Every agent must read this document AND `LEADING_APPS_REFERENCE_MAP.md` before writing any code for the native app.*
