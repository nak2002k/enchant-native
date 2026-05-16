plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "org.enchant.protos"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

protobuf {
    protoc { path = "/opt/homebrew/bin/protoc" }
    generateProtoTasks {
        all().configureEach { task ->
            task.builtins {
                java {
                    option("lite")
                }
            }
        }
    }
}

tasks.register("generateProto") {
    dependsOn("generateDebugProto")
}

dependencies {
    implementation(libs.protobuf.javalite)
}
