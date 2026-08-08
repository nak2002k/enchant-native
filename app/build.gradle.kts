plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "org.enchant"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.enchant.messenger"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "AGENT_PORT", "19789")
    }

    flavorDimensions += "account"
    productFlavors {
        create("primary") {
            dimension = "account"
            buildConfigField("int", "AGENT_PORT", "19789")
        }
        create("peer") {
            dimension = "account"
            applicationId = "org.enchant.messenger.peer"
            buildConfigField("int", "AGENT_PORT", "19790")
            versionNameSuffix = "-peer"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        // Extract native libraries so Android copies them to the app's lib dir
        // at install time. The debug APK's JNI libs are large and rebuilt
        // frequently; direct-load (extractNativeLibs=false) requires strict
        // 64KB page alignment that a --rerun-tasks/streamed install can miss,
        // which surfaced as UnsatisfiedLinkError at runtime.
        jniLibs {
            useLegacyPackaging = true
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

    kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }
}
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:base"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(libs.sqlcipher)
    implementation(project(":core:crypto"))
    implementation(project(":core:model"))
    implementation(project(":core:jobmanager"))
    implementation(project(":core:protos"))
    implementation(project(":core:store"))
    implementation(project(":core:notifications"))
    implementation(project(":core:push"))
    implementation(project(":core:calls"))
    implementation(project(":core:navigation"))
    implementation(project(":core:performance"))
    implementation(project(":core:accessibility"))
    implementation(project(":core:ui"))
    implementation(project(":core:crash"))
    implementation(project(":core:config"))
    implementation(libs.timber)

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
    implementation(project(":feature:registration"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.animation:animation")
    implementation(libs.compose.navigation)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.adaptive.navigation)
    implementation(libs.compose.adaptive)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.activity.compose)
    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.webrtc)
    implementation(libs.workmanager)
    implementation(libs.security.crypto)
    implementation(libs.sqlite)

    debugImplementation(libs.leakcanary.android)
    debugImplementation(libs.compose.ui.tooling.debug)
    debugImplementation(project(":core:agent-debug"))

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Peer flavor is for same-device E2E; skip Firebase package check.
afterEvaluate {
    tasks.matching { it.name == "processPeerDebugGoogleServices" }.configureEach {
        enabled = false
    }
    tasks.matching { it.name == "processPeerReleaseGoogleServices" }.configureEach {
        enabled = false
    }
}
