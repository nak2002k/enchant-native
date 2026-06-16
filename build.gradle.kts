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
        resolutionStrategy {
            force("com.google.protobuf:protobuf-javalite:${libs.versions.protobuf.get()}")
        }
    }
    tasks.withType<Test> {
        jvmArgs(
            "-Djava.library.path=${rootProject.projectDir}/native/libs_host",
            "-Djna.library.path=${rootProject.projectDir}/native/libs_host"
        )
        environment("DYLD_LIBRARY_PATH", "${rootProject.projectDir}/native/libs_host")
    }
}
