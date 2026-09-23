pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Only the artifact under test is allowed to resolve from mavenLocal — everything
        // else must come from a real repository, or a stale local cache could mask a
        // publication bug.
        mavenLocal {
            content {
                includeGroup("com.github.piratecash.thorchain-kit-android")
            }
        }
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "thorchain-kit-consumer-smoke"
