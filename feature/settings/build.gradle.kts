plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.wardrobe.app.feature.settings"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))

    // Phase 8 — QR pairing. A narrow, documented exception to "features
    // depend only on core:domain": scanning a live camera frame needs
    // core:sync's stateless ZXing decode function on every analyzed frame,
    // which would be architecturally awkward (and slower) routed through a
    // suspend repository call. See phase-8-multi-device-sync.md's
    // "Architecture" section — the same precedent feature:closet's
    // Developer Panel already set for core:image.
    implementation(project(":core:sync"))
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // M15 Part 4 — the Profile screen's avatar picker reuses
    // core:image's GalleryImportSource (the same Photo Picker + local-copy
    // pattern feature:closet's AddToWardrobeSheet already uses) rather than
    // reimplementing it; coil.compose renders the resulting local file path,
    // the same library (and the same "implementation, not api" scoping)
    // core:ui's GarmentTile already uses for garment thumbnails.
    implementation(project(":core:image"))
    implementation(libs.coil.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Weather Settings' "Use device location" toggle requests
    // ACCESS_COARSE_LOCATION at the point of use (Phase 7) — the only screen
    // in this module that needs a runtime permission request.
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    // Backup/export/import run as foreground-service-backed WorkManager jobs
    // (Section 19/20, Phase 1) — the only feature module besides closet's
    // capture pipeline that schedules work directly.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
