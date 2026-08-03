plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.wardrobe.app.core.testing"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Fakes implement core:domain's repository interfaces; the in-memory Room
    // helper builds a real core:database database — both are `api` so any
    // module's test source set that pulls in core:testing gets these transitively.
    api(project(":core:model"))
    api(project(":core:domain"))
    api(project(":core:database"))

    // core:database exposes Room only as `implementation`, so this module needs its
    // own direct (api) dependency to expose Room.inMemoryDatabaseBuilder to callers.
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)

    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.mockk)
    api(libs.turbine)
    api(libs.androidx.room.testing)
    api(libs.androidx.test.ext.junit)
    api(libs.hilt.android.testing)
    api(libs.robolectric)
}
