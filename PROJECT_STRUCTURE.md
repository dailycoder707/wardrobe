# Project Structure — Phase 2

Full folder tree and every file created in this phase, with purpose. No application
features are implemented — see each module's `README.md` for what's real vs. what's
scaffolded for a later phase. Architecture context: `phase-1-architecture.md`.
Feature-tier decisions (what's KEEP/DEFER/CUT): `alta-class-closet-app-master-prompt.md`.

**This tree is a Phase 2 snapshot** — by Phase 9 the real structure has grown
well beyond it (`core:sync`, `core:common`, real content in every `feature:*`
module, etc.); see each module's own `README.md` for current, accurate
contents. What *is* still permanently true, regardless of how much the tree
grows: every module lives under this same single-app, no-backend structure
— there is no `server/`, no `backend/`, no cloud-function directory, and
there never will be, per Constitution rule 13 / [ADR-011](docs/adr/ADR-011-permanent-privacy-first-principles.md)
("privacy-first, offline-first personal wardrobe operating system," added
2026-08-03). Any future module must fit inside `core:*`/`feature:*`/`app`
exactly as today — a `feature:tryon` or `core:tryon` module (Phase 10) is
exactly this shape, not an exception to it.

```
wardrobe/
├── .editorconfig                 Shared Kotlin style rules (indent, line length, Compose
│                                 function-naming exemption for ktlint)
├── .gitignore
├── build.gradle.kts              Root build file — plugin versions declared once (apply
│                                 false), ktlint + Detekt configured once for every subproject
├── settings.gradle.kts           Module list, plugin/dependency repositories
├── gradle.properties             JVM args, AndroidX/Kotlin flags, the AGP-9-bridge flags
│                                 (android.builtInKotlin=false, android.newDsl=false — see
│                                 BUILD_VERIFICATION.md for why these exist)
├── local.properties              Machine-specific SDK path (gitignored; this copy is for
│                                 this dev machine only)
├── keystore.properties.example   Template for release signing — copy to keystore.properties
│                                 (gitignored) to produce a Play-Store-signed release build
├── gradle/
│   ├── libs.versions.toml        The version catalog — every dependency version, with
│                                 comments recording *why* wherever it isn't simply "latest"
│   └── wrapper/                  Gradle 9.6.1 wrapper (jar committed, distribution not)
├── gradlew / gradlew.bat         Gradle wrapper scripts
├── config/
│   └── detekt/
│       └── detekt.yml            Project-specific Detekt overrides (builds on the bundled
│                                 default ruleset — see DEPENDENCIES.md)
│
├── app/                          Composition root only — see app/README.md
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml   Permissions (camera, coarse location, internet-for-weather-
│       │                        only), backup/data-extraction rule references, launcher activity
│       ├── kotlin/com/wardrobe/app/
│       │   ├── WardrobeApplication.kt   @HiltAndroidApp
│       │   ├── MainActivity.kt          @AndroidEntryPoint, hosts WardrobeTheme + NavHost
│       │   └── navigation/
│       │       ├── HomeRoute.kt         The one placeholder type-safe route that exists today
│       │       └── WardrobeNavHost.kt   NavHost wiring + the Phase-2 placeholder screen
│       └── res/
│           ├── drawable/         Placeholder adaptive-icon layers (background/foreground/
│           │                    monochrome) — real brand art is a Phase 4 deliverable
│           ├── mipmap-anydpi-v26/  Adaptive icon XML (vector-only, no raster PNGs needed)
│           ├── values/           strings.xml, themes.xml (manifest-level theme only —
│           │                    real theming is in core:designsystem, in Kotlin)
│           └── xml/
│               ├── backup_rules.xml           Legacy (API 23-30) Auto Backup exclusions
│               └── data_extraction_rules.xml  API 31+ cloud-backup exclusions / device-
│                                              transfer allowance — see Section 24, Phase 1
│
├── benchmark/                    Baseline Profile generation module — see benchmark/README.md
│   └── build.gradle.kts          (com.android.test + androidx.baselineprofile, no test
│                                 class yet — nothing to profile until Phase 5 has screens)
│
├── core/
│   ├── model/                    Pure Kotlin (kotlin("jvm")) domain models. Empty — Phase 3.
│   ├── domain/                   Pure Kotlin repository interfaces + use cases. Empty — Phase 3/6.
│   ├── common/                   Android lib: dispatcher qualifiers, AppError/Result, logging
│   │                             wrapper, unit-conversion utils. Empty — fills in alongside data/domain.
│   ├── database/                 Room (KSP + androidx.room Gradle plugin configured). No
│   │                             entities/DAOs yet — Phase 3. schemas/ dir wired for migration tests.
│   ├── datastore/                Preferences DataStore. Empty — Phase 3.
│   ├── network/                  Retrofit/OkHttp/kotlinx.serialization — Weather API only.
│   │                             Empty — Phase 7.
│   ├── image/                    CameraX configured. No ML Kit/TFLite dependency yet
│   │                             (blocked on a real-photo background-removal spike, Phase 5b).
│   ├── data/                     Repository implementations binding core:domain to
│   │                             database/datastore/network/image. Empty — Phase 3/5b/6.
│   ├── designsystem/              Material3 theme — the one module with real code this phase
│   │   └── src/main/kotlin/.../theme/
│   │       ├── Color.kt          Placeholder seed colors (Phase 4 replaces with real tokens)
│   │       ├── Type.kt           Material3 default Typography()
│   │       ├── Shape.kt          Material3 default Shapes()
│   │       └── Theme.kt          WardrobeTheme composable (light/dark, optional dynamic color)
│   ├── ui/                       Coil configured; shared ScreenState/EmptyState/ErrorBanner
│   │                             components. Empty — fills in with Phase 5's screens.
│   └── testing/                  Fakes, fixtures, Room in-memory test helper — exposed as
│                                 `api` dependencies so any module's tests get them transitively.
│
└── feature/
    ├── closet/                   Capture, browse, item detail/edit, "Style this item".
    │                             Only feature module depending on core:image + CameraX.
    ├── outfits/                  Manual builder, saved looks, outfit detail.
    ├── calendar/                 Wear logging, month/week/day views.
    ├── stats/                    Closet stats, cost-per-wear, gap analysis.
    ├── wishlist/                 Manually-entered wishlist + gap-analysis surface.
    ├── trips/                    Packing list, travel lookbook.
    ├── settings/                 Style profile, style rules, backup/restore, export/import.
    └── widget/                   Glance-based home-screen widget (no Compose UI/Navigation —
                                  App Widgets have no back stack). Will need its own
                                  AndroidManifest.xml once a real AppWidgetProvider exists.
```

Every `feature:*` and `core:*` module (except `model`/`domain`, and `widget` until it has a
provider) intentionally has **no `AndroidManifest.xml`** — AGP synthesizes one from the
Gradle-DSL `namespace` when a library module declares no components (activities, services,
receivers) of its own. Adding an empty manifest "for completeness" would be dead weight.

Every module's package directories exist on disk (per this phase's requirement to create
folders even where empty) but are otherwise unpopulated — see each module's own `README.md`
for exactly which phase fills each package in.
