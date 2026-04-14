pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "OpenMonitor"

include(":app")
include(":core:core-common")
include(":core:core-model")
include(":core:core-data")
include(":core:core-database")
include(":core:core-ui")
include(":core:core-server-api")
include(":feature:feature-overview")
include(":feature:feature-battery")
include(":feature:feature-fps")
include(":feature:feature-process")
include(":feature:feature-cpu")
include(":feature:feature-float")
include(":feature:feature-hardware")
include(":feature:feature-key-attestation")
include(":service")
include(":server-rs")
include(":server-shim")
