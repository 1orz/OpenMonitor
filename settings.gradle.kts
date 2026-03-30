pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
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
include(":feature:feature-overview")
include(":feature:feature-battery")
include(":feature:feature-fps")
include(":feature:feature-process")
include(":feature:feature-cpu")
include(":feature:feature-float")
include(":service")
