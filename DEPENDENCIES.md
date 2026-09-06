# Dependency Explanations

Every version in `gradle/libs.versions.toml` was checked against Google's Maven metadata
or Maven Central directly (not assumed from training knowledge) on 2026-08-01, and the
whole dependency graph was verified by actually running `./gradlew build` — see
`BUILD_VERIFICATION.md` for the real failures that surfaced and how each was resolved.
Where a version is *not* simply "latest," the version catalog itself carries a comment
explaining why — this file summarizes those reasons in one place.

## Build tooling

| Dependency | Version | Why |
|---|---|---|
| Android Gradle Plugin | 9.2.1 | Latest stable. Requires two bridge flags (below) to keep working with this project's plugin model — see BUILD_VERIFICATION.md |
| Gradle | 9.6.1 | AGP 9.2.1's minimum required version is 9.4.1; 9.6.1 is latest stable |
| Kotlin | 2.4.10 | Latest stable |
| KSP | 2.3.9 | Latest — KSP's versioning no longer prefixes with an exact matching Kotlin version; 2.3.9 works with Kotlin 2.4.10 |
| ktlint (via org.jlleitschuh.gradle.ktlint) | 14.2.0 (plugin) / 1.8.0 (ktlint engine, pinned in root build.gradle.kts) | Latest stable plugin; engine version pinned explicitly rather than left to the plugin's bundled default, so upgrades are a deliberate version-catalog-adjacent change |
| Detekt | 1.23.8 | Latest stable |

**The AGP 9 / Kotlin plugin bridge** (`gradle.properties`): AGP 9.0+ made Kotlin support
"built-in" and, by default, hard-rejects the traditional `org.jetbrains.kotlin.android` /
`org.jetbrains.kotlin.jvm` plugins that KSP, Room, Hilt, ktlint, and Detekt are all still
built around. `android.builtInKotlin=false` and `android.newDsl=false` opt back into the
traditional model — Google's own release notes call this a temporary bridge slated for
removal in AGP 10. Revisit this project's plugin model (likely a move to convention
plugins built on AGP's built-in Kotlin support) before upgrading past AGP 9.

## Kotlin libraries

| Dependency | Version | Why |
|---|---|---|
| kotlinx-coroutines | 1.11.0 | Latest stable |
| kotlinx-serialization-json | 1.11.0 | Latest stable; backs both Nav Compose's type-safe routes and the (future) Weather API DTOs |

## AndroidX

| Dependency | Version | Why |
|---|---|---|
| core-ktx | **1.18.0**, not 1.19.0 | 1.19.0's AAR metadata requires compileSdk 37 / AGP 9.1+; only platforms up to 36.1 are installed locally |
| lifecycle | **2.10.0**, not 2.11.0 | Same reason — 2.11.0's `lifecycle-runtime-compose`/`lifecycle-viewmodel-compose` require compileSdk 37 |
| activity-compose | 1.13.0 | Latest — not affected by the compileSdk-37 requirement |
| Compose BOM | 2026.06.01 | Latest stable |
| Navigation Compose | 2.9.8 | Latest stable; type-safe routes (kotlinx.serialization-backed) are stable as of 2.9.6+ |
| Room | 2.8.4 | Latest stable |
| DataStore (preferences) | 1.2.1 | Latest stable |
| WorkManager | 2.11.2 | Latest stable |
| CameraX (core/camera2/lifecycle/view) | 1.6.1 | Latest stable |
| Paging | 3.5.0 | Latest stable — backs Closet Browse at 1000+ items (Phase 1 Section 21) |
| Glance (appwidget/material3) | 1.1.1 | Latest stable — feature:widget only |
| profileinstaller / benchmark-macro-junit4 | 1.4.1 | Latest stable — baseline profile consumption/generation |

## Hilt / DI

| Dependency | Version | Why |
|---|---|---|
| com.google.dagger:hilt-android (+ compiler, + testing) | 2.60.1 | Required: 2.56.2 (an earlier, Maven-Central-search-index-reported "latest" that turned out to be stale — repo1.maven.org's actual metadata goes up to 2.60.1) cannot read Kotlin 2.4.10's class metadata format at all — a hard KSP/kapt failure, not a warning, hit by actually running the build |
| androidx.hilt (hilt-navigation-compose, hilt-work, hilt-compiler) | **1.3.0**, not 1.4.0 | 1.4.0 additionally requires compileSdk 37 |

## Networking (core:network — Weather only)

| Dependency | Version | Why |
|---|---|---|
| Retrofit (+ kotlinx-serialization converter) | 3.0.0 | Latest stable |
| OkHttp (+ logging-interceptor) | 5.4.0 | Latest stable — an earlier check under-reported 5.1.0/5.0.0-alpha as latest from an incomplete search result; the real Maven Central metadata shows 5.x went stable and is now at 5.4.0 |

Until 2026-08-05 this was deliberately the **only** module with a network
dependency. `core:ai` (below) is now a second, added deliberately under
ADR-012's amendment to the privacy/offline principles — not a quiet
loosening of this rule. Any *other* module still needs to revisit the
zero-cost/offline budget-posture decision in
`alta-class-closet-app-master-prompt.md` Section 0 before adding a network
client.

## On-device ML Kit (`core:image` / `core:tryon`)

**Added to this file at RC1** (a pre-existing documentation gap found during
RC1's dependency audit — both were already in use, just never written up
here):

| Dependency | Version | Why |
|---|---|---|
| com.google.android.gms:play-services-mlkit-subject-segmentation | 16.0.0-beta1 | Only version Google has published (no stable release exists yet, confirmed against the real Maven group-index) — backs `MlKitBackgroundRemover` (Phase 5b); see `TECHNICAL_DEBT.md` item 6 for the still-open on-device-vs-real-photo verification gap this carries |
| com.google.mlkit:pose-detection | 18.0.0-beta5 | Same reason — no stable release exists yet — backs `MlKitBodyAnchorEstimator` (Phase 10), a best-effort, always-overridable placement seed, never load-bearing |

## AI Gateway (`core:ai` — Add-to-Wardrobe v2, ADR-012)

| Dependency | Version | Why |
|---|---|---|
| Retrofit / OkHttp / kotlinx-serialization-json | same versions as `core:network` above | Reused, not re-picked — one client stack for both this app's outbound network surfaces |
| androidx.security:security-crypto | 1.1.0 | Latest stable, confirmed against Google's real Maven metadata on 2026-08-05 (1.0.0-alpha01..1.1.0-beta01 precede it). Backs `EncryptedApiKeyStore` — this app's first "encrypt an arbitrary secret string" need; `core:sync`'s existing raw-`KeyStore` code is a signing key, not a secret-blob store, so this is a genuinely new capability, not a version bump of something already present |
| com.google.mlkit:text-recognition | 16.0.1 | Latest stable, confirmed against Google's real Maven group-index on 2026-08-05 (16.0.0-beta1..6 precede it). On-device OCR backing `OnDeviceMetadataEngine`'s brand guess |
| com.google.mlkit:face-detection | 16.1.7 | Latest stable, confirmed against Google's real Maven group-index on 2026-08-05. On-device only, backs `PrivacyPreprocessor`'s pre-upload face blur — never sent anywhere itself, purely a local transform before any cloud call |
| androidx.work (runtime-ktx, hilt-work, hilt-compiler) | same versions as already used by `core:data` | Reused for `AiJobManager` — see `phase` plan doc for why WorkManager was chosen over a hand-rolled queue |

No vendor SDK (an official OpenAI/Google/Anthropic client library) is added
for any of the six named cloud providers — every adapter speaks plain HTTP
via the Retrofit/OkHttp/kotlinx-serialization stack already used for
weather, consistent with `DEPENDENCY_POLICY.md`'s cloud-AI carve-out
preferring plain HTTP over an SDK that would bundle its own telemetry or
require its own account/license beyond the user's own provider credentials.

## Image loading (core:ui — Coil)

| Dependency | Version | Why |
|---|---|---|
| io.coil-kt.coil3:coil-compose | 3.5.0 | Latest stable. Coil 3 changed its Maven group from `io.coil-kt` to `io.coil-kt.coil3` and renamed `coil-base` to `coil-core` — noted so a future contributor doesn't "fix" the group id back to the Coil 2 coordinates |

No `coil-network-*` artifact is included anywhere: every image this app displays is a
local file (Phase 1 Section 17) — there is no remote image URL in this product at all.

## Testing

| Dependency | Version | Why |
|---|---|---|
| JUnit4 | 4.13.2 | Standard, stable; used for every unit test in this project |
| MockK | 1.14.11 | Latest stable |
| Turbine | 1.2.1 | Latest stable — Flow-testing, used against `core:domain`'s Flow-returning repository interfaces |
| androidx.test (ext.junit, espresso-core, uiautomator) | 1.3.0 / 3.7.0 / 2.4.0 | Latest stable of each |
| Robolectric | 4.16.1 | Latest stable (not 4.17, which is still beta) |

**RC1 dependency audit (2026-08-06)**: `org.junit.jupiter:junit-jupiter`
(JUnit5) was declared in the version catalog with its own alias but never
actually applied by any module's `build.gradle.kts` — a genuinely unused
dependency, not a "kept for later" one, removed. `androidx.test.uiautomator`
(`benchmark` module only) has no current call site either, but is kept: it's
scaffolding for the macrobenchmark test classes `benchmark/README.md` already
discloses don't exist yet, not dead weight from something that used to exist.
Every other dependency in this file was cross-checked against a real
`implementation`/`testImplementation`/etc. call site and confirmed still in
active use; no duplicate or obsolete library was found.

## Why some choices were rejected

| Choice | Rejected alternative | Why |
|---|---|---|
| AGP 9.2.1 + bridge flags | AGP 8.13.2 (avoids the bridge flags entirely) | The newest Hilt (required for Kotlin 2.4.10 metadata support) requires AGP 9.0+ — AGP 8.13.2 has no path to a working Hilt+Kotlin-2.4 combination at all |
| AGP 9.2.1 + bridge flags | AGP 9.x with full built-in-Kotlin migration (drop org.jetbrains.kotlin.android everywhere) | That migration is real work across 20 modules and every KSP/Room/Hilt/ktlint/Detekt integration point; doing it under time pressure inside a "configure the skeleton" phase risked trading one set of unverified assumptions for another. The bridge flags get a working, fully-verified build today; the migration is better done as its own deliberate piece of work |
| No build-logic convention-plugin composite build | Precompiled Gradle convention plugins (as sketched in Phase 1's package tree) | Every module's `build.gradle.kts` was hand-verified against a real build in this phase; adding a composite build layer on top would have meant verifying convention-plugin script compilation *in addition to* 20 modules' worth of real dependency resolution, for a real but non-urgent benefit (less copy-pasted plugin blocks). Revisit once the module count or plugin-block duplication actually starts hurting |
| Real photos await a Phase 5b spike before picking ML Kit vs. TFLite | Adding one now | Neither has been verified against actual garment photos yet (Phase 1 Section 16) — adding either dependency now would assert a capability this project hasn't checked |
