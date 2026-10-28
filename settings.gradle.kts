pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Kadb's SPAKE2 primitive is published under this JitPack group.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Flyfish233") }
        }
    }
}

rootProject.name = "turbo-touch"
include(":app")
