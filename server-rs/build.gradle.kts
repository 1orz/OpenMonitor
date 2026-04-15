import java.security.KeyStore
import java.security.MessageDigest

// Gradle wrapper around `server-rs/` Cargo crate.
//
// Produces:
//   - openmonitor-server             ELF binary, the daemon itself. Shipped
//     inside the APK at jniLibs/<abi>/libopenmonitor-server.so so Android's
//     packaging preserves it per-ABI; the app exec's it from
//     `applicationInfo.nativeLibraryDir` at runtime.
//
// Only ONE artifact now — the old cdylib (consumed by the Shizuku JNI shim)
// is gone along with server-shim. Every launch path (libsu / Shizuku / ADB)
// exec's the same binary with different `--mode` flags.
//
// Uses manual Cargo tasks instead of rust-android-gradle plugin because the
// plugin (0.9.6) is not compatible with AGP 9.x (BaseExtension removal).
//
// ── Cert SHA-256 pinning (Phase A3) ──
//
// The Rust build.rs reads OPENMONITOR_CERT_SHA256 (64-char lowercase hex) and
// embeds it as a compile-time constant for APK cert verification at runtime.
// The `computeCertSha256` task computes this hash from the active signing
// keystore and feeds it to `cargoBuild` via env var.
//
// By default the task uses ~/.android/debug.keystore (password "android",
// alias "androiddebugkey"). For release or non-default keystores, pass
// Gradle project properties:
//
//   ./gradlew :server-rs:cargoBuild \
//       -Popenmonitor.signing.storeFile=/path/to/release.jks \
//       -Popenmonitor.signing.storePassword=secret \
//       -Popenmonitor.signing.keyAlias=myalias
//

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

    sourceSets.getByName("debug") {
        // Use string path (not Provider) so AGP 9.x doesn't complain.
        jniLibs.srcDirs(layout.buildDirectory.dir("generated/jniLibs-debug").get().asFile)
    }
    sourceSets.getByName("release") {
        jniLibs.srcDirs(layout.buildDirectory.dir("generated/jniLibs-release").get().asFile)
    }
}

// --- Cert SHA-256 computation ---

abstract class ComputeCertSha256Task : DefaultTask() {
    @get:InputFile abstract val keystoreFile: RegularFileProperty
    @get:Input abstract val keystorePassword: Property<String>
    @get:Input abstract val keyAlias: Property<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty
    @get:Internal val hex: Provider<String> = outputFile.map { it.asFile.readText().trim() }

    @TaskAction fun run() {
        val ksFile = keystoreFile.get().asFile
        val ks = KeyStore.getInstance(detectFormat(ksFile))
        ksFile.inputStream().use { stream -> ks.load(stream, keystorePassword.get().toCharArray()) }
        val cert = ks.getCertificate(keyAlias.get())
            ?: error("alias '${keyAlias.get()}' not found in ${ksFile}")
        val der = cert.encoded
        val sha256 = MessageDigest.getInstance("SHA-256").digest(der)
        val hexStr = sha256.joinToString("") { b -> "%02x".format(b) }
        outputFile.get().asFile.apply { parentFile.mkdirs() }.writeText(hexStr)
        logger.lifecycle("Computed cert SHA-256 = $hexStr (from ${ksFile.name} alias=${keyAlias.get()})")
    }

    private fun detectFormat(file: File): String {
        // PKCS12 files start with 0x30 (ASN.1 SEQUENCE tag); JKS files start
        // with the magic 0xFEEDFEED. Peek at the first byte to decide.
        val firstByte = if (file.length() > 0) file.inputStream().use { stream -> stream.read() } else -1
        return if (firstByte == 0x30) "PKCS12" else "JKS"
    }
}

// --- Per-variant cert SHA-256 computation ---

val computeCertSha256Debug by tasks.registering(ComputeCertSha256Task::class) {
    group = "rust"
    description = "Compute SHA-256 of the debug signing certificate for Rust cert pinning"

    val ksPath = project.findProperty("openmonitor.signing.storeFile") as? String
        ?: "${System.getProperty("user.home")}/.android/debug.keystore"
    keystoreFile.set(file(ksPath))
    keystorePassword.set(
        project.findProperty("openmonitor.signing.storePassword") as? String ?: "android"
    )
    keyAlias.set(
        project.findProperty("openmonitor.signing.keyAlias") as? String ?: "androiddebugkey"
    )
    outputFile.set(layout.buildDirectory.file("generated/cert_sha256_debug.hex"))
}

val computeCertSha256Release by tasks.registering(ComputeCertSha256Task::class) {
    group = "rust"
    description = "Compute SHA-256 of the release signing certificate for Rust cert pinning"

    val ksPath = project.findProperty("openmonitor.signing.storeFile") as? String
        ?: System.getenv("KEYSTORE_PATH")
        ?: "${rootProject.projectDir}/release.jks"
    keystoreFile.set(file(ksPath))
    val pw = project.findProperty("openmonitor.signing.storePassword") as? String
        ?: System.getenv("STORE_PASSWORD")
        ?: "opmonitor12138"
    keystorePassword.set(pw)
    keyAlias.set(
        project.findProperty("openmonitor.signing.keyAlias") as? String ?: "opmonitor"
    )
    outputFile.set(layout.buildDirectory.file("generated/cert_sha256_release.hex"))
}

// --- Cargo build tasks (per variant) ---

val rustTarget = "aarch64-linux-android"
val abi = "arm64-v8a"
val profile = "release"   // Cargo profile — always optimised, independent of Android buildType
val apiLevel = 26

val cargoBuildDebug by tasks.registering(Exec::class) {
    dependsOn(computeCertSha256Debug)
    description = "Build Rust crate for Android ($rustTarget) — debug cert"
    group = "rust"
    workingDir(project.projectDir)

    val certHexFile: Provider<RegularFile> = computeCertSha256Debug.flatMap { it.outputFile }
    val targetDir = project.projectDir.resolve("target/android-debug").absolutePath

    doFirst {
        val hex = certHexFile.get().asFile.readText().trim()
        environment("OPENMONITOR_CERT_SHA256", hex)
        environment("CARGO_TARGET_DIR", targetDir)
    }

    commandLine(
        "cargo", "ndk",
        "--target", rustTarget,
        "--platform", apiLevel.toString(),
        "--",
        "build", "--profile", profile,
    )
}

val cargoBuildRelease by tasks.registering(Exec::class) {
    dependsOn(computeCertSha256Release)
    description = "Build Rust crate for Android ($rustTarget) — release cert"
    group = "rust"
    workingDir(project.projectDir)

    val certHexFile: Provider<RegularFile> = computeCertSha256Release.flatMap { it.outputFile }
    val targetDir = project.projectDir.resolve("target/android-release").absolutePath

    doFirst {
        val hex = certHexFile.get().asFile.readText().trim()
        environment("OPENMONITOR_CERT_SHA256", hex)
        environment("CARGO_TARGET_DIR", targetDir)
    }

    commandLine(
        "cargo", "ndk",
        "--target", rustTarget,
        "--platform", apiLevel.toString(),
        "--",
        "build", "--profile", profile,
    )
}

// --- Copy tasks (per variant) ---

val cargoOutDirDebug   = project.projectDir.resolve("target/android-debug/$rustTarget/$profile")
val cargoOutDirRelease = project.projectDir.resolve("target/android-release/$rustTarget/$profile")

val copyServerBinaryDebug by tasks.registering(Copy::class) {
    dependsOn(cargoBuildDebug)
    description = "Copy debug-pinned server binary into jniLibs"
    from(cargoOutDirDebug.resolve("openmonitor-server"))
    into(layout.buildDirectory.dir("generated/jniLibs-debug/$abi"))
    rename { "libopenmonitor-server.so" }
}

val copyServerBinaryRelease by tasks.registering(Copy::class) {
    dependsOn(cargoBuildRelease)
    description = "Copy release-pinned server binary into jniLibs"
    from(cargoOutDirRelease.resolve("openmonitor-server"))
    into(layout.buildDirectory.dir("generated/jniLibs-release/$abi"))
    rename { "libopenmonitor-server.so" }
}

afterEvaluate {
    tasks.named("preDebugBuild").configure { dependsOn(copyServerBinaryDebug) }
    tasks.named("preReleaseBuild").configure { dependsOn(copyServerBinaryRelease) }
}
