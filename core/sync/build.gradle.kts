plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.wardrobe.app.core.sync"
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

// Local-network device pairing/sync transport (Phase 8) — NSD discovery, plain
// java.net sockets, Android Keystore-backed crypto, ZXing QR encode/decode.
// Deliberately no Hilt/DI dependency here, same posture as core:network: this
// module exposes plain classes; core:data's own DI module (`SyncModule`) wires
// them into the app's dependency graph. No Google Play Services dependency —
// NSD and sockets are plain platform APIs, ZXing is pure Java.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
