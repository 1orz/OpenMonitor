// Gradle wrapper around `server-rs/` Cargo crate.
//
// Produces:
//   - libopenmonitor_server.so       cdylib, loaded by the Shizuku shim.
//   - openmonitor-server             ELF binary, for the libsu launch path
//     (renamed to libopenmonitor-server.so so Android's packaging picks it up
//     from jniLibs/<abi>/).
//
// Both are packaged as JNI libs and end up in `nativeLibraryDir` on device,
// readable+executable by the `shell` user, which is what ShizukuExecutor and
// libsu both need.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.rust.android)
}

android {
    namespace = "com.cloudorz.openmonitor.serverrs"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters.add("arm64-v8a")
            // Add "x86_64" when emulator support becomes a requirement.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

cargo {
    module = "."
    libname = "openmonitor_server"
    targets = listOf("arm64")
    profile = "release"
    // Build both the cdylib and the bin target. rust-android-gradle picks up
    // the cdylib automatically; we copy the bin into jniLibs below.
    targetIncludes = arrayOf("libopenmonitor_server.so")
    prebuiltToolchains = true
}

// Bind Rust build into Android assemble.
afterEvaluate {
    tasks.named("preBuild").configure { dependsOn("cargoBuild") }
}

// Copy the standalone binary into jniLibs so Android packaging ships it.
tasks.register<Copy>("copyServerBinary") {
    dependsOn("cargoBuild")
    val arch = "arm64-v8a"
    val rustTarget = "aarch64-linux-android"
    val profileDir = "release"
    from(layout.buildDirectory.dir("rustJniLibs/${arch}/${rustTarget}/${profileDir}/openmonitor-server"))
    into(layout.buildDirectory.dir("generated/jniLibs/${arch}"))
    rename { "libopenmonitor-server.so" }
}

afterEvaluate {
    tasks.named("preBuild").configure { dependsOn("copyServerBinary") }
}

android {
    sourceSets.getByName("main") {
        jniLibs.srcDirs("${layout.buildDirectory.get()}/generated/jniLibs")
    }
}
