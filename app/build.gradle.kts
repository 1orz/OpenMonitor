plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.cloudorz.openmonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cloudorz.openmonitor"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }

    packaging {
        jniLibs {
            // Force extraction of native libs so daemon binary is executable from nativeLibraryDir
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootProject.projectDir}/release.jks")
            val pw = System.getenv("STORE_PASSWORD") ?: "opmonitor12138"
            storePassword = pw
            keyAlias = "opmonitor"
            keyPassword = pw
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += "Instantiatable"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        val gitCommit = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }
}

// ── Build monitor-daemon from Go source ─────────────────────────────────────
val daemonSrcDir = file("${rootProject.projectDir}/monitor-daemon")
val daemonBinary = file("src/main/jniLibs/arm64-v8a/libmonitor-daemon.so")
val daemonCommitFile = file("src/main/assets/daemon/daemon-commit.txt")

// Resolve the `go` binary at configuration time — Gradle's Exec uses JVM ProcessBuilder
// which searches only the JVM's own PATH (not the shell's), so Homebrew/nvm paths are missing.
val goExecutable: String = listOf(
    System.getenv("GOROOT")?.let { "$it/bin/go" },
    "/opt/homebrew/bin/go",
    "/usr/local/go/bin/go",
    "/usr/local/bin/go",
    "/usr/bin/go",
).firstOrNull { it != null && file(it).exists() } ?: "go"

val buildMonitorDaemon by tasks.registering(Exec::class) {
    group = "build"
    description = "Compile monitor-daemon (Go → Android arm64)"
    onlyIf { daemonSrcDir.resolve("go.mod").exists() }

    workingDir = daemonSrcDir

    // Use hash of daemon source tree (no longer a submodule)
    // --no-show-signature avoids GPG verification text polluting stdout
    val daemonHash = providers.exec {
        workingDir = rootProject.projectDir
        commandLine("git", "log", "-1", "--format=%h", "--no-show-signature", "--", "monitor-daemon")
    }.standardOutput.asText.map { it.trim() }.getOrElse("unknown")

    val ldflags = "-s -w -X monitor-daemon/collector.GitCommit=$daemonHash"
    commandLine = listOf(goExecutable, "build", "-ldflags", ldflags, "-o", daemonBinary.absolutePath, "./cmd/daemon")
    environment("GOOS", "android")
    environment("GOARCH", "arm64")
    environment("CGO_ENABLED", "0")

    inputs.files(fileTree(daemonSrcDir) { include("**/*.go", "go.mod", "go.sum") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("daemonHash", daemonHash)
    outputs.file(daemonBinary)

    doLast {
        daemonCommitFile.parentFile.mkdirs()
        daemonCommitFile.writeText(daemonHash)
    }
}

tasks.named("preBuild") {
    dependsOn(buildMonitorDaemon)
}
// ─────────────────────────────────────────────────────────────────────────────

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-ui"))
    implementation(project(":feature:feature-overview"))
    implementation(project(":feature:feature-battery"))
    implementation(project(":feature:feature-fps"))
    implementation(project(":feature:feature-process"))
    implementation(project(":feature:feature-cpu"))
    implementation(project(":feature:feature-float"))
    implementation(project(":feature:feature-hardware"))
    implementation(project(":service"))

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.libsu.core)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    debugImplementation(libs.compose.ui.tooling)
}
