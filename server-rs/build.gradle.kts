// Gradle wrapper around `server-rs/` Cargo crate.
//
// Produces:
//   - libopenmonitor_server.so       cdylib, loaded by the Shizuku shim.
//   - openmonitor-server             ELF binary, for the libsu launch path
//     (renamed to libopenmonitor-server.so so Android's packaging picks it up
//     from jniLibs/<abi>/).
//
// Uses manual Cargo tasks instead of rust-android-gradle plugin because the
// plugin (0.9.6) is not compatible with AGP 9.x (BaseExtension removal).

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.cloudorz.openmonitor.serverrs"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main") {
        // Use string path (not Provider) so AGP 9.x doesn't complain.
        jniLibs.srcDirs(layout.buildDirectory.dir("generated/jniLibs").get().asFile)
    }
}

// --- Cargo build tasks ---

val rustTarget = "aarch64-linux-android"
val abi = "arm64-v8a"
val profile = "release"
val apiLevel = 26

val cargoBuild by tasks.registering(Exec::class) {
    description = "Build Rust crate for Android ($rustTarget)"
    group = "rust"
    workingDir(project.projectDir)

    commandLine(
        "cargo", "ndk",
        "--target", rustTarget,
        "--platform", apiLevel.toString(),
        "--",
        "build", "--profile", profile,
    )
}

// Directory where cargo places outputs.
val cargoOutDir = project.projectDir.resolve("target/$rustTarget/$profile")
val generatedJniLibsDir = layout.buildDirectory.dir("generated/jniLibs/$abi")

val copyCdylib by tasks.registering(Copy::class) {
    dependsOn(cargoBuild)
    description = "Copy cdylib (.so) into jniLibs"
    from(cargoOutDir.resolve("libopenmonitor_server.so"))
    into(generatedJniLibsDir)
}

val copyServerBinary by tasks.registering(Copy::class) {
    dependsOn(cargoBuild)
    description = "Copy standalone binary into jniLibs (renamed as .so)"
    from(cargoOutDir.resolve("openmonitor-server"))
    into(generatedJniLibsDir)
    rename { "libopenmonitor-server.so" }
}

afterEvaluate {
    tasks.named("preBuild").configure {
        dependsOn(copyCdylib, copyServerBinary)
    }
}
