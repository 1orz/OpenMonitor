plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cloudorz.monitor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cloudorz.monitor"
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

    androidResources {
        // Keep daemon binary uncompressed in APK so shell can unzip it as a direct copy
        noCompress += listOf("monitor-daemon")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

// ── Build monitor-daemon from Go source ──────────────────────────────────────
val daemonSrcDir = file("${rootProject.projectDir}/../monitor-daemon")
val daemonBinary = file("src/main/assets/daemon/monitor-daemon")

val buildMonitorDaemon by tasks.registering(Exec::class) {
    group = "build"
    description = "Compile monitor-daemon (Go → Android arm64)"
    onlyIf { daemonSrcDir.exists() }

    workingDir = daemonSrcDir
    commandLine = listOf("go", "build", "-o", daemonBinary.absolutePath, "./cmd/daemon")
    environment("GOOS", "android")
    environment("GOARCH", "arm64")
    environment("CGO_ENABLED", "0")

    inputs.files(fileTree(daemonSrcDir) { include("**/*.go", "go.mod", "go.sum") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(daemonBinary)
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
    implementation(project(":feature:feature-power"))
    implementation(project(":feature:feature-charge"))
    implementation(project(":feature:feature-fps"))
    implementation(project(":feature:feature-process"))
    implementation(project(":feature:feature-cpu"))
    implementation(project(":feature:feature-float"))
    implementation(project(":service"))

    implementation(libs.shizuku.api)

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

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    debugImplementation(libs.compose.ui.tooling)
}
