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

// Explicit: KMP modules embed the project name in the dex, and a floating name breaks
// P.CASH's reproducible F-Droid build.
rootProject.name = "thorchain-kit"

include(":thorchainkit")
include(":thorchainkit-proto")
include(":sample-shared")
include(":sample-android")
include(":sample-desktop")
