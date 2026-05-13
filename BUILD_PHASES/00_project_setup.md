# Phase 0 — Project Setup & Build Configuration

## Overview

This phase must be completed before any other phase. It sets up the entire Gradle project structure, declares all dependencies with versions, configures the Android manifest with all permissions, sets up signing for release builds, and defines the CI/CD pipeline.

---

## 1. Gradle Version Catalog

**File:** `gradle/libs.versions.toml`

All dependency versions in a single file. Every module's `build.gradle.kts` references this catalog — never hardcode versions.

```toml
[versions]
# Kotlin & Android
kotlin = "2.0.21"
agp = "8.7.3"

# Coroutines
coroutines = "1.9.0"

# Compose
compose-bom = "2024.12.01"
compose-compiler = "2.0.21"

# AndroidX
lifecycle = "2.8.7"
navigation = "2.8.5"
room = "2.6.1"
workmanager = "2.10.0"
datastore = "1.1.1"

# Networking
okhttp = "4.12.0"

# Images
coil = "2.7.0"

# WebRTC
webrtc = "1.0.32006"

# Firebase
firebase-bom = "33.6.0"

# Database
sqlcipher = "4.6.1"

# Performance
leakcanary = "2.14"

# Testing
junit5 = "5.11.4"
mockk = "1.13.13"
turbine = "1.2.0"
compose-test = "1.7.6"
robolectric = "4.14.1"

# Protobuf
protobuf = "4.29.2"

[libraries]
# Kotlin
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }

# Coroutines
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

# Compose BOM
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons = { module = "androidx.compose.material:material-icons-extended" }
compose-adaptive = { module = "androidx.compose.material3.adaptive:adaptive" }
compose-navigation = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }

# Lifecycle
lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }

# Navigation
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }

# Networking
okhttp-core = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
okhttp-mock = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }

# Images
coil-core = { module = "io.coil-kt.coil3:coil-core", version.ref = "coil" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }

# WebRTC
webrtc = { module = "io.getstream:stream-webrtc-android", version.ref = "webrtc" }

# Firebase
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase-bom" }
firebase-messaging = { module = "com.google.firebase:firebase-messaging" }
firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics" }
firebase-analytics = { module = "com.google.firebase:firebase-analytics" }
firebase-config = { module = "com.google.firebase:firebase-remote-config" }

# Database
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }

sqlcipher = { module = "net.zetetic:android-database-sqlcipher", version.ref = "sqlcipher" }
sqlite = { module = "androidx.sqlite:sqlite-ktx" }

# Encryption
security-crypto = { module = "androidx.security:security-crypto", version = "1.1.0-alpha06" }

# WorkManager
workmanager = { module = "androidx.work:work-runtime-ktx", version.ref = "workmanager" }

# Protobuf (Wire format — for WebSocket frames)
protobuf-javalite = { module = "com.google.protobuf:protobuf-javalite", version.ref = "protobuf" }

# Performance
leakcanary-android = { module = "com.squareup.leakcanary:leakcanary-android", version.ref = "leakcanary" }
leakcanary-android-core = { module = "com.squareup.leakcanary:leakcanary-android-core", version.ref = "leakcanary" }

# Testing
junit5-api = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit5" }
junit5-params = { module = "org.junit.jupiter:junit-jupiter-params", version.ref = "junit5" }
junit5-engine = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit5" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
compose-ui-test = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

# Debug
compose-ui-tooling-debug = { module = "androidx.compose.ui:ui-tooling" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
google-services = { id = "com.google.gms.google-services", version = "4.4.2" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version = "3.0.2" }
protobuf = { id = "com.google.protobuf", version = "0.9.4" }
```

---

## 2. Root Build Files

### File: `build.gradle.kts` (root)

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.protobuf) apply false
}

allprojects {
    configurations.configureEach {
        // Force protobuf-lite to avoid duplicate class conflicts
        resolutionStrategy {
            force("com.google.protobuf:protobuf-javalite:${libs.versions.protobuf.get()}")
        }
    }
}
```

### File: `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex(".*google.*")
                includeGroupByRegex(".*android.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Enchant"

// App module
include(":app")

// Core modules
include(":core:base")
include(":core:network")
include(":core:database")
include(":core:crypto")
include(":core:model")
include(":core:jobmanager")
include(":core:signalstore")
include(":core:notifications")
include(":core:push")
include(":core:calls")
include(":core:navigation")
include(":core:performance")
include(":core:accessibility")
include(":core:crash")
include(":core:config")

// Feature modules
include(":feature:auth")
include(":feature:chat")
include(":feature:chat-list")
include(":feature:calls")
include(":feature:groups")
include(":feature:contacts")
include(":feature:status")
include(":feature:channels")
include(":feature:profile")
include(":feature:settings")
include(":feature:stickers")
include(":feature:polls")
include(":feature:location")
include(":feature:backup")
include(":feature:share")
```

### File: `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

---

## 3. Per-Module Build Files Template

Every module follows this exact pattern. Use this template for ALL modules.

### File: `<module>/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)        // or .application for :app
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.enchant.${project.name.replace(':', '/').replace('-', '.')}"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35

        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-Xcontext-receivers",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}

dependencies {
    // Core dependencies (present in every module)
    implementation(libs.kotlin.stdlib)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Testing (present in every module)
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)

    // Module-specific dependencies follow...
}
```

### Special Module: `:app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "org.enchant"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.enchant.messenger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read from keystore.properties (gitignored)
        signingConfigs {
            create("release") {
                val props = java.util.Properties().apply {
                    load(file("keystore.properties").inputStream())
                }
                storeFile = file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core modules
    implementation(project(":core:base"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:crypto"))
    implementation(project(":core:model"))
    implementation(project(":core:jobmanager"))
    implementation(project(":core:signalstore"))
    implementation(project(":core:notifications"))
    implementation(project(":core:push"))
    implementation(project(":core:calls"))
    implementation(project(":core:navigation"))
    implementation(project(":core:performance"))
    implementation(project(":core:accessibility"))
    implementation(project(":core:crash"))
    implementation(project(":core:config"))

    // Feature modules
    implementation(project(":feature:auth"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:chat-list"))
    implementation(project(":feature:calls"))
    implementation(project(":feature:groups"))
    implementation(project(":feature:contacts"))
    implementation(project(":feature:status"))
    implementation(project(":feature:channels"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:stickers"))
    implementation(project(":feature:polls"))
    implementation(project(":feature:location"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:share"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.navigation)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Coil
    implementation(libs.coil.core)
    implementation(libs.coil.compose)

    // WebRTC
    implementation(libs.webrtc)

    // WorkManager
    implementation(libs.workmanager)

    // Security
    implementation(libs.security.crypto)

    // Debug
    debugImplementation(libs.leakcanary.android)
    debugImplementation(libs.compose.ui.tooling.debug)

    // Testing
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

---

## 4. Android Manifest — Full Permissions & Service Declarations

**File:** `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

    <!-- Notifications (API 33+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Camera & Audio -->
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <!-- Contacts -->
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.WRITE_CONTACTS" />

    <!-- Location -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Phone (for SMS auto-retrieval on API 26+) -->
    <uses-permission android:name="android.permission.RECEIVE_SMS" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_SMS" android:maxSdkVersion="32" />

    <!-- Storage (legacy, for API < 30) -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29"
        tools:ignore="ScopedStorage" />

    <!-- Foreground Service (API 28+) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

    <!-- System -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"
        tools:ignore="ProtectedPermissions" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.BLUETOOTH" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="android.permission.USE_FINGERPRINT" android:maxSdkVersion="27" />

    <application
        android:name=".EnchantApp"
        android:allowBackup="false"
        android:fullBackupContent="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:networkSecurityConfig="@xml/network_security_config"
        android:enableOnBackInvokedCallback="true"
        android:theme="@style/Theme.Enchant"
        tools:targetApi="35">

        <!-- Main Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Deep Links -->
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="enchant.app" android:pathPrefix="/chat/" />
                <data android:scheme="https" android:host="enchant.app" android:pathPrefix="/group/" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="enchant" android:host="chat" />
                <data android:scheme="enchant" android:host="group" />
                <data android:scheme="enchant" android:host="call-link" />
            </intent-filter>
        </activity>

        <!-- WebSocket Foreground Service -->
        <service
            android:name=".core.network.WebSocketService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />

        <!-- FCM Push Service -->
        <service
            android:name=".core.push.FcmReceiveService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

        <!-- FCM Fetch Foreground Service -->
        <service
            android:name=".core.push.FcmFetchForegroundService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />

        <!-- Boot Receiver -->
        <receiver
            android:name=".core.base.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>

        <!-- Call Foreground Service -->
        <service
            android:name=".core.calls.CallForegroundService"
            android:foregroundServiceType="microphone|connectedDevice"
            android:exported="false" />

        <!-- File Provider -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

        <!-- Share Target Activity -->
        <activity
            android:name=".feature.share.ShareTargetActivity"
            android:exported="true"
            android:theme="@style/Theme.Enchant.Transparent"
            android:excludeFromRecents="true">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="image/*" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="video/*" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND_MULTIPLE" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="image/*" />
            </intent-filter>
        </activity>

        <!-- Direct Share Chooser Target Service -->
        <service
            android:name=".feature.share.ConversationChooserTargetService"
            android:permission="android.permission.BIND_CHOOSER_TARGET_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.chooser.ChooserTargetService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

---

## 5. Resource Files

### File: `res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
    <files-path name="files" path="." />
    <external-files-path name="external_files" path="." />
    <external-cache-path name="external_cache" path="." />
</paths>
```

### File: `res/xml/data_extraction_rules.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="enchant.db"/>
        <exclude domain="database" path="enchant_jobs.db"/>
        <exclude domain="sharedpref" path="enchant_prefs.xml"/>
        <exclude domain="file" path="keys/"/>
        <exclude domain="file" path="sessions/"/>
    </cloud-backup>
    <device-transfer>
        <include domain="database" path="enchant.db"/>
        <include domain="sharedpref" path="enchant_prefs.xml"/>
    </device-transfer>
</data-extraction-rules>
```

### File: `res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <!-- Allow cleartext for local development -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">10.0.2.2</domain>
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">enchant.app</domain>
        <pin-set expiration="2027-01-01">
            <pin digest="SHA-256">base64_primary_cert_hash_here</pin>
            <pin digest="SHA-256">base64_backup_cert_hash_here</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

### File: `res/xml/backup_rules.xml` (for Android 12+)

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="database" path="enchant.db"/>
    <exclude domain="file" path="keys/"/>
</full-backup-content>
```

---

## 6. Keystore Configuration

**File:** `keystore.properties` (NOT committed — add to .gitignore)

```properties
storeFile=../keystore/release.keystore
storePassword=your_store_password
keyAlias=enchant_release
keyPassword=your_key_password
```

**.gitignore entry:**
```
keystore.properties
keystore/
```

---

## 7. ProGuard Rules

**File:** `app/proguard-rules.pro`

```proguard
# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Protobuf
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Keep Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Keep Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep Crypto (libsodium JNI)
-keep class org.enchant.core.crypto.** { *; }
-keepclassmembers class org.enchant.core.crypto.** { native <methods>; }

# Keep Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep class com.google.firebase.crashlytics.** { *; }

# Serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
```

---

## 8. CI/CD Pipeline

**File:** `.github/workflows/ci.yml`

```yaml
name: Enchant CI

on:
  pull_request:
    branches: [main, develop]
  push:
    branches: [main]

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew lintKotlin
      - run: ./gradlew ktlintCheck

  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew testDebugUnitTest --stacktrace

  build:
    runs-on: ubuntu-latest
    needs: [lint, unit-tests]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew assembleDebug --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: apk-debug
          path: app/build/outputs/apk/debug/
```

---

## 9. Testing Frameworks

Every test file must use these tools:

| Tool | Library | Purpose | Usage Pattern |
|---|---|---|---|
| **JUnit 5** | `org.junit.jupiter:junit-jupiter-api` | Test framework | `@Test`, `@ParametrizedTest`, `@BeforeEach`, `@AfterEach`, `@BeforeAll`, `@DisplayName` |
| **MockK** | `io.mockk:mockk` | Kotlin mocking | `mockk<T>()`, `every { } returns`, `coEvery { } coReturns`, `verify { }`, `slot`, `capture` |
| **Turbine** | `app.cash.turbine:turbine` | Flow testing | `viewModel.someFlow.test { val item = awaitItem(); assertEquals(x, item) }` |
| **Coroutines Test** | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | Async testing | `runTest { }`, `StandardTestDispatcher`, `advanceUntilIdle()`, `TestCoroutineScheduler` |
| **Compose UI Test** | `androidx.compose.ui:ui-test-junit4` | UI testing | `createComposeRule()`, `onNodeWithText("Hello")`, `performClick()`, `assertIsDisplayed()` |
| **Robolectric** | `org.robolectric:robolectric` | Android unit tests | `@RunWith(RobolectricTestRunner::class)`, `RuntimeEnvironment.application` |
| **MockWebServer** | `com.squareup.okhttp3:mockwebserver` | HTTP mocking | `MockWebServer()`, `server.enqueue(MockResponse())`, `server.url("/")` |

### Test File Naming Convention

```
src/test/java/org/enchant/core/network/ApiClientTest.kt         // Unit test
src/test/java/org/enchant/feature/chat/ConversationViewModelTest.kt  // ViewModel test (unit)
src/androidTest/java/org/enchant/feature/auth/WelcomeScreenTest.kt   // Instrumentation test
```

### Example: ApiClientTest.kt

```kotlin
@DisplayName("ApiClient")
class ApiClientTest {
    private val server = MockWebServer()
    private lateinit var client: ApiClient

    @BeforeEach
    fun setUp() {
        server.start(8080)
        client = ApiClient(baseUrl = server.url("/").toString())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @DisplayName("GET returns parsed JSON on success")
    fun `get request returns parsed JSON on success`() = runTest {
        server.enqueue(MockResponse().setBody("""{"key": "value"}"""))

        val result = client.get<JsonObject>("/test")

        assertTrue(result.isSuccess)
        assertEquals("value", result.getOrNull()?.get("key")?.asString)
    }

    @Test
    @DisplayName("GET returns failure on 401 and auto-refreshes JWT")
    fun `get request auto-refreshes JWT on 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("""{"access_token": "new_jwt"}"""))
        server.enqueue(MockResponse().setBody("""{"key": "value"}"""))

        val result = client.get<JsonObject>("/test")

        assertTrue(result.isSuccess)
        assertEquals(3, server.requestCount)  // original + refresh + retry
    }
}
```

### Example: ConversationViewModelTest.kt with Turbine

```kotlin
@DisplayName("ConversationViewModel")
class ConversationViewModelTest {
    private val mockRepo = mockk<ConversationRepository>()
    private val mockSendPipeline = mockk<MessageSendPipeline>()
    private lateinit var viewModel: ConversationViewModel

    @BeforeEach
    fun setUp() {
        coEvery { mockRepo.getMessages(any(), any(), any()) } returns flowOf(emptyList())
        viewModel = ConversationViewModel(mockRepo, mockSendPipeline)
    }

    @Test
    @DisplayName("sendTextMessage updates message list after sending")
    fun `sendTextMessage success`() = runTest {
        coEvery { mockSendPipeline.sendMessage(any(), any(), any()) } returns SendResult.Success("env123")

        viewModel.sendTextMessage("Hello")

        // Verify the message was inserted into the flow
        viewModel.messages.test {
            val messages = awaitItem()
            assertTrue(messages.any { it.content == "Hello" })
        }
    }
}
```

---

## 10. API Key Configuration

**File:** `.env.example` (for backend dev)

```bash
# Enchant Android - Environment Configuration
# Copy to .env and fill in values

# Backend API
API_BASE_URL=http://10.0.2.2:8080
WS_URL=ws://10.0.2.2:8003

# TURN/STUN
TURN_URL=turn:your-turn-server.com:3478
TURN_USERNAME=your_turn_username
TURN_PASSWORD=your_turn_password

# Firebase
# Requires google-services.json from Firebase Console
GOOGLE_SERVICES_JSON=app/google-services.json
```

---

## 11. Gradle Wrapper

Ensure the Gradle wrapper is configured:

```
gradle/wrapper/gradle-wrapper.properties
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
```

---

## Acceptance Criteria

- [ ] `./gradlew build` succeeds with zero errors
- [ ] `./gradlew lintKotlin` passes with zero warnings
- [ ] `./gradlew testDebugUnitTest` passes
- [ ] `./gradlew assembleRelease` succeeds (with keystore)
- [ ] All 32+ permissions correctly declared in manifest
- [ ] All services (WebSocket, FCM, Call, Boot) declared in manifest
- [ ] FileProvider configured with correct authorities
- [ ] Deep link schemes (`enchant://`) configured
- [ ] Share target intents (text, image, video) configured
- [ ] CI pipeline runs lint → test → build
- [ ] `keystore.properties` is gitignored
- [ ] `google-services.json` is gitignored
- [ ] ProGuard rules cover all major libraries
- [ ] Network security config with cert pinning
- [ ] Backup exclusion rules correctly configured
