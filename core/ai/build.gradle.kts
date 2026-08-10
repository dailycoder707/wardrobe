plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.wardrobe.app.core.ai"
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

    // `core:ai`'s cloud implementations of `GarmentExtractionEngine`/
    // `GarmentReconstructionEngine` implement interfaces that live in
    // `core:image` (Add-to-Wardrobe v2) — the on-device implementations stay
    // there; only the cloud adapters live here.
    implementation(project(":core:image"))

    // `AiJobManager`/`RoomAiMetricsRecorder`/the result cache reference
    // `AiJobDao`/`AiResultCacheDao`/`AiCallLogDao` and their entities
    // directly — the actual `Room.databaseBuilder(...)`/DAO `@Provides`
    // bindings still live in `core:data`'s `DatabaseModule` (same
    // composition-root pattern as every other DAO), this is just the type
    // dependency needed to reference them from here.
    implementation(project(":core:database"))

    // Hilt — `AiGateway`, `AiJobManager`, every engine/adapter is `@Inject
    // constructor`, and `di/`-style `@Module` bindings live here too.
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // AI Gateway's outbound HTTP surface (ADR-012) — this app's second network
    // dependency after `core:network`'s weather-only Retrofit client (see
    // DEPENDENCIES.md). Plain HTTP, no vendor SDK, per DEPENDENCY_POLICY.md's
    // cloud-AI carve-out.
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // AI Job Manager (ADR-012) — built on WorkManager, same precedent as
    // `core:data`'s `ImageProcessingWorker`.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Secure API-key storage — `EncryptedApiKeyStore` (ADR-012).
    implementation(libs.androidx.security.crypto)

    // Privacy preprocessing (`PrivacyPreprocessor`'s pre-upload face blur) —
    // on-device only, no network round-trip. (`OnDeviceMetadataEngine`'s OCR
    // dependency, `mlkit.text.recognition`, lives in `core:image` — that's
    // where the engine itself lives.)
    implementation(libs.mlkit.face.detection)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    // AiJobManagerTest needs a real, synchronously-executing WorkManager
    // (WorkManagerTestInitHelper) rather than mocking WorkManager itself.
    testImplementation(libs.androidx.work.testing)
}
