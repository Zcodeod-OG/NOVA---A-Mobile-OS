pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "nova"

include(":app")
include(":runtime:models")
include(":runtime:utils")
include(":runtime:events")
include(":runtime:kernel")
include(":runtime:inference")
include(":runtime:understanding")
include(":runtime:memory")
include(":runtime:storage")
include(":runtime:reasoning")
include(":runtime:planner")
include(":runtime:execution")
include(":runtime:policy")
include(":runtime:capability")
include(":runtime:android-adapter")
include(":runtime:conversation")
