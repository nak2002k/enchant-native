plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.enchant.core.ui"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.adaptive.navigation)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime.compose)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}