plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.cloudorz.openmonitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cloudorz.openmonitor"
        minSdk = 26
        targetSdk = 36

        // versionName from CI env (tag) or fallback
        versionName = System.getenv("VERSION_NAME") ?: "0.0.1"
        // versionCode from CI env or fallback; tag v1.2.3 → 10203
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
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
        resources {
            excludes += "**"
        }
    }

    androidResources {
        generateLocaleConfig = true
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
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
// All project-level APIs (file(), fileTree(), providers.exec()) are resolved here
// at configuration time, so the Exec task closure captures only plain Java types
// (File, String, FileCollection) and stays configuration-cache compatible.

val daemonSrcDir = file("${rootProject.projectDir}/monitor-daemon")
val daemonCommitFile = file("src/main/assets/daemon/daemon-commit.txt")
val daemonGoModExists = daemonSrcDir.resolve("go.mod").exists()

val goExecutable: String = listOf(
    System.getenv("GOROOT")?.let { "$it/bin/go" },
    "/opt/homebrew/bin/go",
    "/usr/local/go/bin/go",
    "/usr/local/bin/go",
    "/usr/bin/go",
).firstOrNull { it != null && file(it).exists() } ?: "go"

// --no-show-signature avoids GPG verification text polluting stdout
val daemonHash: String = providers.exec {
    workingDir = rootProject.projectDir
    commandLine("git", "log", "-1", "--format=%h", "--no-show-signature", "--", "monitor-daemon")
}.standardOutput.asText.map { it.trim() }.getOrElse("unknown")

val daemonSourceFiles = fileTree(daemonSrcDir) { include("**/*.go", "go.mod", "go.sum") }

daemonCommitFile.parentFile.mkdirs()
daemonCommitFile.writeText(daemonHash)

// Build daemon for both architectures
data class DaemonTarget(val goArch: String, val abiDir: String, val extraEnv: Map<String, String> = emptyMap())
val daemonTargets = listOf(
    DaemonTarget("arm64", "arm64-v8a"),
    DaemonTarget("arm", "armeabi-v7a", mapOf("GOARM" to "7", "GOOS" to "linux")),
)

val daemonTasks = daemonTargets.map { target ->
    val outputFile = file("src/main/jniLibs/${target.abiDir}/libmonitor-daemon.so")
    tasks.register<Exec>("buildDaemon_${target.abiDir}") {
        group = "build"
        description = "Compile monitor-daemon (Go → Android ${target.abiDir})"
        enabled = daemonGoModExists

        workingDir = daemonSrcDir
        val appVersion = System.getenv("VERSION_NAME") ?: "0.0.1"
        val ldflags = "-s -w -X monitor-daemon/collector.GitCommit=$daemonHash -X monitor-daemon/collector.Version=$appVersion"
        commandLine = listOf(goExecutable, "build", "-ldflags", ldflags, "-o", outputFile.absolutePath, "./cmd/daemon")
        environment("GOOS", target.extraEnv["GOOS"] ?: "android")
        environment("GOARCH", target.goArch)
        environment("CGO_ENABLED", "0")
        target.extraEnv.filterKeys { it != "GOOS" }.forEach { (k, v) -> environment(k, v) }

        inputs.files(daemonSourceFiles).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.property("daemonHash", daemonHash)
        outputs.file(outputFile)
    }
}

tasks.named("preBuild") {
    dependsOn(daemonTasks)
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
    implementation(project(":feature:feature-key-attestation"))
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

    implementation(libs.material.kolor)

    implementation(libs.navigation3.runtime)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigationevent.compose)
    implementation(libs.navigation3.ui)

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
