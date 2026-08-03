plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Deliberately a plain Kotlin/JVM module, not an Android library — Section 1 and
// Section 30 of phase-1-architecture.md: core:model has zero Android dependencies
// so a future Kotlin Multiplatform split (iOS/Desktop) is additive, not a rewrite.
// Do not add any androidx.* or android.* dependency here; if something in this
// module ever needs one, that's a signal it belongs in core:domain/core:data
// instead, not a reason to make this module Android-aware.

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit4)
}
