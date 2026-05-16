plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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
    sourceSets {
        getByName("main") {
            java.srcDir(project.layout.buildDirectory.dir("generated/source/proto/java"))
        }
    }
}

val protoSrcDir = file("src/main/proto")
val protoOutputDir = project.layout.buildDirectory.dir("generated/source/proto/java").get().asFile

val generateProto by tasks.registering(Exec::class) {
    description = "Generate Java Lite protobuf classes"
    group = "generation"
    workingDir = rootDir

    val fileList = protoSrcDir.walkTopDown()
        .filter { it.isFile && it.extension == "proto" }
        .map { it.absolutePath }
        .toList()

    commandLine("/opt/homebrew/bin/protoc")
    args("--proto_path=$protoSrcDir")
    args("--java_out=lite:${protoOutputDir.absolutePath}")
    args(fileList)

    inputs.dir(protoSrcDir)
    outputs.dir(protoOutputDir)

    doFirst { protoOutputDir.mkdirs() }
}

tasks.matching { it.name.startsWith("compile") }.configureEach {
    dependsOn(generateProto)
}

dependencies {
    implementation("com.google.protobuf:protobuf-java:${libs.versions.protobuf.get()}")
    implementation("com.google.protobuf:protobuf-javalite:${libs.versions.protobuf.get()}")
}
