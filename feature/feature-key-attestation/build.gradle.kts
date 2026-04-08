plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.cloudorz.openmonitor.feature.keyattestation"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
    }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-common"))

    implementation(libs.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.navigation.compose)

    implementation(libs.shizuku.api)

    // Key attestation dependencies
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("com.google.guava:guava:33.5.0-android")
    implementation("co.nstant.in:cbor:0.9")

    debugImplementation(libs.compose.ui.tooling)
}
