plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.cloudorz.openmonitor.server.api"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
