plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.wardrobe.app.core.tryon"
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
    implementation(project(":core:model"))
    // OnDeviceVirtualTryOnEngine (M12) implements core:ai's VirtualTryOnEngine —
    // the dependency points this way only, core:ai never depends on core:tryon.
    implementation(project(":core:ai"))

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Hilt — BodyImageFileStore/MlKitBodyAnchorEstimator are @Inject constructor
    // classes, and BodyAnchorEstimatorModule is a @Module binding, same reason
    // core:image needs its own KSP/Hilt processing.
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // On-device body-landmark estimation (Phase 10) — see the version catalog
    // comment for why this is pinned to a beta, and phase-10-personal-virtual-
    // tryon.md for why it's wired in as a best-effort, always-overridable
    // default-placement seeder, never load-bearing.
    implementation(libs.mlkit.pose.detection)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
