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
    }
}

rootProject.name = "nova"

include(":app")
include(":runtime:models")
include(":runtime:utils")
include(":runtime:events")
include(":runtime:kernel")
include(":runtime:ai-core")
include(":runtime:ai-native")
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
