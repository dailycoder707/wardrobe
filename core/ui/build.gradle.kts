plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wardrobe.app.core.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:designsystem"))

    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Robolectric-run Compose UI tests (testDebugUnitTest) resolve a launcher
    // ComponentActivity from the *debug* variant's merged manifest — this
    // artifact's manifest only reaches that merge via `debugImplementation`,
    // not `testImplementation` (which only affects the JVM classpath, not
    // manifest merging). Discovered via a real
    // "Unable to resolve activity for Intent... ComponentActivity" failure,
    // not assumed up front.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Coil — every AsyncImage in the app (garment thumbnails, cutouts) goes
    // through this shared module so caching behavior (Section 17, Phase 1) is
    // consistent everywhere, not reconfigured per screen. No network fetcher
    // artifact: every image this app displays is a local file (Section 17 —
    // internal storage, never a remote URL), and this app makes zero other
    // network calls that would justify pulling in coil-network-okhttp too.
    implementation(libs.coil.compose)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
}
