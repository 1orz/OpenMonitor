// server-shim: 20 lines of Kotlin + AIDL stub that lets Shizuku UserService
// treat us as a normal Java service while the actual server is pure Rust.
//
// Shizuku starts a JVM via app_process and invokes RustEntry.main(). The JVM
// loads libopenmonitor_server.so, which hands control to Rust. We hold the
// JVM around only because Shizuku's API requires a Java class name — we never
// run application logic on it.

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.cloudorz.openmonitor.server"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:core-server-api"))
    // Pull the native library built by :server-rs into the final APK.
    runtimeOnly(project(":server-rs"))
}
