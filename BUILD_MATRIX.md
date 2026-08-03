# Build Matrix

Only combinations actually verified are listed as "Verified." Nothing here is asserted
from documentation alone — see `BUILD_VERIFICATION.md` for what "verified" meant in
practice (a real `./gradlew clean build` on real hardware, not just a plausible-looking
version set).

## Verified

| Component | Version |
|---|---|
| OS | Windows 11 |
| Android Studio | 2026.1.2 (build `AI-261.25134.95.2612.15822958`) — **JBR/JDK only used**; see caveat below |
| JDK | 21.0.10 (OpenJDK, Android Studio's bundled JetBrains Runtime) |
| Gradle | 9.6.1 |
| Android Gradle Plugin (AGP) | 9.2.1 |
| Kotlin | 2.4.10 |
| KSP | 2.3.9 |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 26 |

**Verification method**: `./gradlew clean build` via the command line, with
`JAVA_HOME` pointed at Android Studio's bundled JBR. This exercises Kotlin compilation,
KSP (Hilt + Room) annotation processing, resource merging, Lint, ktlint, Detekt, and
debug APK assembly across all 21 modules (`BUILD_VERIFICATION.md`).

**Caveat — what this does *not* verify**: Android Studio itself was never opened for
this project; only its bundled JDK was used from the command line. IDE project sync,
the Compose Preview renderer, and the Layout Inspector are **unverified** — a clean
command-line build does not guarantee a clean Android Studio sync (they occasionally
diverge, e.g. over IDE-cached Gradle state or IDE-specific plugin versions). Do this
before relying on Android Studio day-to-day: open the project in Studio 2026.1.2+ and
confirm Gradle sync completes with no errors.

**Also not verified**: running the app on a device or emulator. No device/emulator was
connected during Phase 2/2.1 (`adb devices` returned empty), and this was flagged
explicitly in `BUILD_VERIFICATION.md`'s checklist rather than assumed to be fine. A
successful build is evidence the code compiles and links — it is not evidence the app
launches, renders, or behaves correctly on an actual device.

## Supported but not yet tested

| Component | Range | Basis |
|---|---|---|
| minSdk | 26 (API 26, Android 8.0) | Configured `defaultConfig.minSdk = 26` in `app/build.gradle.kts`; compiles successfully, but no test has run on an actual API 26 device/emulator |
| compileSdk / targetSdk | Capped at 36 | See `TECHNICAL_DEBT.md` item 2 — platform 37 isn't installed locally and several AndroidX libraries' latest patches require it |
| AGP | Minimum 9.4.1-Gradle-compatible 9.x releases | Only 9.2.1 has actually been run; `TECHNICAL_DEBT.md` item 3 notes 9.3.1 is available but unverified against this project |
| Gradle | 9.4.1+ (AGP 9.2.1's stated minimum) | Only 9.6.1 has actually been run |

## Known-incompatible combinations (discovered empirically, not from documentation)

| Combination | Result | Where it's explained |
|---|---|---|
| AGP 9.2.1 + `org.jetbrains.kotlin.android`/`.jvm` plugins, without the bridge flags | Hard failure — AGP 9's built-in Kotlin model rejects those plugins outright | `BUILD_VERIFICATION.md` item 1, `TECHNICAL_DEBT.md` item 1 |
| AGP 8.13.2 + `androidx.hilt:hilt-navigation-compose:1.4.0` (or `androidx.core:core-ktx:1.19.0`, or `androidx.lifecycle:*:2.11.0`) at compileSdk 36 | Hard failure — AAR metadata requires compileSdk 37 / AGP 9.1+ | `BUILD_VERIFICATION.md` item 2, `TECHNICAL_DEBT.md` item 2 |
| `com.google.dagger:hilt-android:2.56.2` + Kotlin 2.4.10 | Hard failure — Hilt's bundled `kotlin-metadata-jvm` reader caps at metadata version 2.2.0; Kotlin 2.4.10 emits 2.4.0 | `BUILD_VERIFICATION.md` item 3 |
| `com.google.dagger:hilt-android:2.60.1` + AGP 8.13.2 | Hard failure — Hilt's Gradle plugin requires AGP 9.0+ | `BUILD_VERIFICATION.md` item 3 |

## Updating this file

Add a new row to "Verified" only after actually running the full verification process
in `DEPENDENCY_POLICY.md`, on real hardware, not after just editing the version
catalog. If a new incompatible combination is discovered, add it to the last table
immediately — that table is often more valuable to a future maintainer than the
"verified" one, since it saves someone from re-discovering the same wall.
