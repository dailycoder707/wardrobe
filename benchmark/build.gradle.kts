plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.wardrobe.app.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 28 // androidx.benchmark.macro's floor — see README for why this differs from the app's minSdk 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Only a release-like, non-debuggable-but-profileable build type is useful
    // for benchmarking — measuring against a debug build measures the debugger,
    // not the app (Phase 1 Section 21-23: "measure, don't assume").
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

kotlin {
    jvmToolchain(21)
}

baselineProfile {
    // Runs on a connected device/emulator only — see README. Not part of the
    // default `./gradlew build` / `check` task graph.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
}
