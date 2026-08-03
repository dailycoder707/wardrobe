plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Also plain Kotlin/JVM — see core:model's build file for why. Repository
// interfaces and use cases here must stay implementable without any Android
// import; core:data (Android module) provides the real implementations.

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
