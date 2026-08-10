@file:Suppress("UnstableApiUsage")

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

rootProject.name = "wardrobe"

include(":app")
include(":benchmark")

// core modules — Section 2/3, Phase 1 architecture
include(":core:model")
include(":core:domain")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:image")
include(":core:ai")
include(":core:sync")
include(":core:tryon")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

// feature modules
include(":feature:closet")
include(":feature:outfits")
include(":feature:calendar")
include(":feature:stats")
include(":feature:wishlist")
include(":feature:trips")
include(":feature:settings")
include(":feature:widget")
include(":feature:tryon")
include(":feature:capture")
include(":feature:onboarding")
