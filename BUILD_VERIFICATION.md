# Build Verification — Phase 2

This phase's "ensure everything builds successfully" requirement was not asserted — it
was checked by actually running Gradle on this machine (Android Studio's bundled JBR as
JDK 21, the Android SDK already installed at `%LOCALAPPDATA%\Android\Sdk`, platforms up
to API 36.1, build-tools 36.0.0) and fixing every real failure that came up, in order.
That process is recorded below because the failures themselves are informative — several
are current, dated ecosystem tensions (AGP 9's Kotlin migration, Hilt/Kotlin-metadata
compatibility) that a from-memory answer would not have caught.

## How to reproduce

```
$env:JAVA_HOME = "<path to a JDK 21, e.g. Android Studio's bundled jbr>"
./gradlew.bat build
```

`local.properties` in this repo already points `sdk.dir` at this machine's SDK install;
on another machine, replace it (it's gitignored) or let Android Studio regenerate it.

## Final result

```
BUILD SUCCESSFUL
```

for `./gradlew clean build`, which runs, across all 21 modules (`app`, `benchmark`, 11
`core:*`, 8 `feature:*`):
- Kotlin compilation (main + test + androidTest source sets)
- KSP annotation processing (Hilt, Room)
- Android resource merging/linting (`lint`, zero issues after configuration — see below)
- ktlint style check (`ktlintCheck`, zero violations)
- Detekt static analysis (`detekt`, zero issues against `config/detekt/detekt.yml`)
- Unit test compilation (no tests exist yet — nothing to implement in this phase — but
  the test source sets and their dependencies compile cleanly)
- Debug APK assembly (`assembleDebug`)

## What "no warnings" actually means here

Nearly clean. Six residual warnings remain, all tracing to one deliberate, documented
choice (see `gradle.properties` and `DEPENDENCIES.md`'s "AGP 9 / Kotlin plugin bridge"
note):

```
WARNING: The option setting 'android.builtInKotlin=false' is deprecated.
WARNING: The option setting 'android.newDsl=false' is deprecated.
WARNING: API 'applicationVariants' is obsolete and has been replaced with 'AndroidComponentsExtension'.
WARNING: API 'libraryVariants' is obsolete and has been replaced with 'AndroidComponentsExtension'.
WARNING: API 'testVariants' is obsolete and has been replaced with 'AndroidComponentsExtension'.
WARNING: API 'unitTestVariants' is obsolete and has been replaced with 'AndroidComponentsExtension'.
```

The first two are the bridge flags themselves being deprecated (expected — Google flags
them as a temporary migration aid). The four "obsolete API" warnings are a direct,
verified consequence of the same flags: `android.newDsl=false` re-enables the legacy
variant API that `org.jetbrains.kotlin.android`'s compatibility shim relies on. All six
disappear together the moment this project migrates to AGP's built-in Kotlin model —
they are not independent problems to chase one at a time.

Reporting these rather than suppressing them: they're genuinely informative about this
project's current, temporary position in an ecosystem migration, and a future maintainer
should see them rather than have them hidden.

## Real failures hit during this verification, in the order they were found

1. **AGP 9.2.1 rejected `org.jetbrains.kotlin.android` outright.** AGP 9.0+ made Kotlin
   support "built-in" and treats the traditional Kotlin Gradle plugin as incompatible
   with its new DSL. Fixed by first trying AGP 8.13.2 (see next item for why that alone
   wasn't the answer), then landing on AGP 9.2.1 + `android.builtInKotlin=false` +
   `android.newDsl=false`.
2. **AGP 8.13.2 + newest AndroidX (hilt-navigation-compose 1.4.0, core-ktx 1.19.0,
   lifecycle 2.11.0) failed AAR metadata checks** — those library versions require
   compileSdk 37 / AGP 9.1+, which 8.13.2 can't provide. Fixed by pinning those three
   to their prior minor version (1.3.0 / 1.18.0 / 2.10.0).
3. **Hilt 2.56.2 (AGP-8-compatible) can't read Kotlin 2.4.10's class metadata** —
   `[Hilt] Provided Metadata instance has version 2.4.0, while maximum supported version
   is 2.2.0`, a hard KSP/kapt failure. The fix (bump to Hilt 2.60.1) reintroduced the AGP
   9.0+ requirement Hilt's own Gradle plugin now has, which is what settled the AGP
   version at 9.2.1 with the bridge flags rather than 8.13.2.
4. **`app/build.gradle.kts`'s keystore-loading script had two real Kotlin script bugs**:
   `java.util.Properties()` failed to resolve (`java` was shadowed by an implicit
   extension in that script scope) and `Map["key"] as String` warned as an unnecessary
   cast. Fixed with an explicit `import java.util.Properties` and `Properties.getProperty()`.
5. **`com.android.test` plugin classpath conflict** between `:benchmark`'s
   `alias(libs.plugins.android.test)` and the AGP classpath already pulled in by
   `com.android.application`/`com.android.library` elsewhere. Fixed by declaring
   `alias(libs.plugins.android.test) apply false` in the root `build.gradle.kts` plugins
   block alongside the other two, the same way AGP's own docs recommend.
6. **`WardrobeNavHost.kt`'s fully-qualified `androidx.navigation.compose.composable<HomeRoute>`
   call didn't resolve** as a reified extension function called that way. Fixed with a
   normal `import` and unqualified call.
7. **`local.properties`'s `sdk.dir` value failed Android Lint's `PropertyEscape` check**
   even after using the exact escaped form Lint itself recommended (`C\:\\Users\\...`) —
   a verified false positive against a machine-specific, gitignored file that was never
   meant to be portable. Disabled `PropertyEscape` in `app`'s `lint {}` block with that
   reasoning recorded inline.
8. **ktlint found real formatting violations** (multiline expression wrapping, an
   `if { } // comment \n else { }` layout it doesn't allow, Compose function names
   tripping the standard naming rule) across `app/build.gradle.kts` and
   `core:designsystem`'s `Theme.kt`. Fixed the Kotlin-script issues by hand; ran
   `./gradlew ktlintFormat` to auto-fix the rest; added `.editorconfig`'s
   `ktlint_function_naming_ignore_when_annotated_with = Composable` so PascalCase
   `@Composable` functions stop being flagged project-wide (not just in one file).
9. **Detekt flagged `Color.kt`'s hex literals as `MagicNumber`** and `WardrobeNavHost.kt`'s
   file name / Composable names via `MatchingDeclarationName`/`FunctionNaming`. Fixed by
   renaming `WardrobeDestinations.kt` → `HomeRoute.kt` (matching its single top-level
   declaration), adding `ignoreAnnotated: ["Composable"]` to `FunctionNaming` in
   `config/detekt/detekt.yml`, and broadening `MagicNumber`'s excludes to
   `**/designsystem/theme/**`.
10. **Renaming `mipmap-anydpi-v26` → `mipmap-anydpi`** (Lint's `ObsoleteSdkInt` suggestion,
    since minSdk is already 26) **broke resource resolution** — AAPT2 could no longer find
    `mipmap/ic_launcher` at all. Reverted the rename (kept `-v26`) and disabled
    `ObsoleteSdkInt` in `app`'s `lint {}` block with the empirical finding recorded, since
    the "fix" Lint suggested doesn't actually work with this AAPT2 version.
11. **Missing `<monochrome>` layer** on the adaptive icon (`MonochromeLauncherIcon`).
    Added a placeholder monochrome vector layer and referenced it from both
    `ic_launcher.xml` and `ic_launcher_round.xml`.

## Checklist

- [x] `./gradlew clean build` succeeds from a clean checkout
- [x] All 21 modules configure and compile (main, test, androidTest source sets)
- [x] `lint` — 0 issues across every module
- [x] `ktlintCheck` — 0 violations across every module
- [x] `detekt` — 0 issues against the project's `config/detekt/detekt.yml`
- [x] Debug APK assembles (`:app:assembleDebug`)
- [ ] Release APK is Play-Store-signed — needs a real `keystore.properties`
      (see `keystore.properties.example`); `assembleRelease` still succeeds without one,
      falling back to debug signing, by design
- [ ] Baseline profile generated — needs a connected device/emulator (`benchmark/README.md`);
      not part of `build`/`check`, and there are no real screens to profile yet regardless
- [ ] Instrumented tests run on a device/emulator — none exist yet (nothing to test; no
      features implemented in this phase)
- [ ] App verified running on-device/emulator with the naked eye — not done as part of
      this documentation-and-configuration phase; do this before treating Phase 2 as
      fully closed out, since a build succeeding is not the same as an app that launches
      and renders correctly
