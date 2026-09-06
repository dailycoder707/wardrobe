plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.wardrobe.app.core.data"
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
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:image"))
    implementation(project(":core:sync"))
    implementation(project(":core:tryon"))
    // Add-to-Wardrobe v2 (ADR-012) — the per-capability Routers (this is the
    // composition root that can see `core:image`, `core:ai`, and
    // `core:datastore` together) live here.
    implementation(project(":core:ai"))

    // core:sync exposes ZXing/kotlinx.serialization only as `implementation`, so
    // the sync engine's own protocol encode/decode here needs a direct
    // dependency too (Phase 8), same pattern as core:network's Retrofit above.
    implementation(libs.zxing.core)

    // core:database exposes Room only as `implementation`, so DatabaseModule's own
    // `Room.databaseBuilder(...)` call needs a direct dependency here too.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // core:network exposes Retrofit/OkHttp/kotlinx.serialization only as
    // `implementation`, so NetworkModule's own Retrofit/OkHttpClient/Json
    // construction needs a direct dependency here too (Phase 7).
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    // M25 Gemini-segmentation follow-up: GarmentExtractionEngineRouterTest's
    // Gemini success path now exercises real Bitmap/Base64 compositing
    // (`compositeGeminiSegmentationCutout`, core:image) end-to-end rather
    // than mocking it away, so this module needs the same Robolectric shadow
    // environment core:ai/core:image already use for real Bitmap tests.
    testImplementation(libs.robolectric)
}
