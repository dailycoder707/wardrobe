plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.wardrobe.app.core.image"
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

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Photo Picker (ActivityResultContracts.PickVisualMedia) — gallery import.
    implementation(libs.androidx.activity)

    // Hilt — this module has `@Inject constructor` classes (GarmentImagePipeline,
    // MlKitBackgroundRemover, ImageFileStore) and a `@Module` binding
    // BackgroundRemover, so it needs its own KSP/Hilt processing, same reason
    // core:datastore needed it added in Phase 5a.
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Background removal (Phase 5b, ADR-008) — see phase-5b-image-pipeline.md for
    // why ML Kit was chosen as a provisional default and TECHNICAL_DEBT.md for
    // what's still unverified about that choice.
    implementation(libs.mlkit.subject.segmentation)

    // Person-region masking (Add-to-Wardrobe v2) — the same bundled-model Pose
    // Detection dependency `core:tryon`'s `MlKitBodyAnchorEstimator` already
    // uses, added here too since `PersonRegionMasker` needs its own landmark
    // detection pass on the pre-extraction photo.
    implementation(libs.mlkit.pose.detection)

    // On-device metadata (Add-to-Wardrobe v2) — `OnDeviceMetadataEngine`'s
    // OCR-based brand guess. Lives alongside `GarmentExtractionEngine`/
    // `GarmentReconstructionEngine` in this module, not `core:ai`, so
    // `GarmentImagePipeline` can call all three on-device engines directly.
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
