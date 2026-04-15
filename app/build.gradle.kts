import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
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

        val localProps = rootProject.file("local.properties")
        val hmacKey = if (localProps.exists()) {
            val props = Properties()
            localProps.reader().use { reader -> props.load(reader) }
            props.getProperty("api.hmac.key") ?: ""
        } else ""
        buildConfigField("String", "API_HMAC_KEY", "\"$hmacKey\"")

        val ecPublicKey = if (localProps.exists()) {
            val p = Properties()
            localProps.reader().use { reader -> p.load(reader) }
            p.getProperty("api.ec.public.key") ?: ""
        } else ""
        buildConfigField("String", "API_EC_PUBLIC_KEY", "\"$ecPublicKey\"")

        val activationPublicKey = if (localProps.exists()) {
            val p = Properties()
            localProps.reader().use { reader -> p.load(reader) }
            p.getProperty("activation.ed25519.public.key") ?: ""
        } else ""
        buildConfigField("String", "ACTIVATION_PUBLIC_KEY", "\"$activationPublicKey\"")
    }
}

dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-model"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-server-api"))
    implementation(project(":server-rs"))
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
    implementation(libs.zxing.core)
    implementation(libs.markdown.renderer.m3)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    debugImplementation(libs.compose.ui.tooling)
}
