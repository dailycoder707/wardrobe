# Technical Debt Register

Every entry here was incurred deliberately while getting `./gradlew build` to actually
pass in Phase 2 (see `BUILD_VERIFICATION.md` for the blow-by-blow) — none are oversights.
This file exists so none of them are silently forgotten. Update it whenever an item is
resolved or a new one is knowingly taken on; don't let it go stale.

## Permanent Product Principles (binding constraint checklist, added 2026-08-03, amended 2026-08-05)

Per Constitution rule 13 / [ADR-011](docs/adr/ADR-011-permanent-privacy-first-principles.md),
**as amended by [ADR-012](docs/adr/ADR-012-cloud-ai-provider-amendment.md)**:
this is a privacy-first, offline-first personal wardrobe operating system,
not just "an AI wardrobe app." Every future debt item added below must be
checked against this list before being accepted as a reasonable tradeoff —
an item that violates one of these is not debt to track, it's a design that
must change.

**This is the current, binding text.** ADR-011's original rules 1, 2, 5, and
10 were absolute prohibitions on cloud AI; ADR-012 replaced them with the
gated versions below. Do not check new work against ADR-011's original
wording — that document is retained for the reasoning behind the stricter
rule set, not as the live constraint. Rules 3, 4, 6, 7, 8, and 9 were never
amended.

1. A wardrobe photo leaves the device **only** through `core:ai`'s
   `AiGateway`/`ProviderAdapter` architecture, and **only** after explicit,
   informed, per-capability consent naming the destination host. *(Amended
   by ADR-012; was: never leaves the device.)*
2. Same gate as rule 1, extended to any personal photo a capability
   processes (e.g. virtual try-on body references). *(Amended by ADR-012.)*
3. Outfit generation must work completely offline. *(Unchanged.)*
4. Recommendations must work completely offline. *(Unchanged.)*
5. Cloud AI providers are permitted **only behind the vendor-neutral
   provider interface** — no vendor SDK or vendor-specific wire format may
   appear outside an adapter file, and switching providers must never
   require a feature-code change. *(Amended by ADR-012; was: no cloud LLM
   integration, ever.)*
6. No cloud storage of wardrobe data. The amendment covers AI *processing*
   calls only — the Room database and garment images are never hosted or
   backed up in the cloud. *(Unchanged; clarified by ADR-012 §6.)*
7. No user accounts are required. *(Unchanged.)*
8. Multi-device sync remains local-network encrypted only. *(Unchanged.)*
9. Internet is only permitted for optional contextual data (weather,
   holidays, etc.) that refines but never gates a recommendation.
   *(Unchanged.)*
10. An on-device implementation must **exist and remain the default and
    fallback** for every AI capability; cloud is opt-in per capability and a
    misconfigured or unreachable provider must degrade to on-device rather
    than break the feature. *(Amended by ADR-012; was: all ML executes
    on-device.)*

As of Phase 10, a scan of every item below confirmed none violated the
original list — the only network call in the app at that point was the
optional Open-Meteo weather fetch (item 9), sync was local-network-only
(item 8, Phase 8), body reference photos/garment masks rode that same
local-network-only channel (item 1/2/8, Phase 10), ML Kit Pose Detection
ran entirely on-device (item 10, Phase 10), and no item involved an
account, cloud storage, or a remote model call.

As of M24, the added cloud AI surface is compliant with the amended list:
every capability retains a working on-device default (item 10), all six
vendors sit behind `AiGateway`/`ProviderAdapter` (item 5), no image is
dispatched without a recorded per-capability consent naming the host (items
1/2), and no wardrobe data is stored remotely (item 6).

---

## 1. AGP "built-in Kotlin" bridge flags

**What**: `gradle.properties` sets
```
android.builtInKotlin=false
android.newDsl=false
```

**Why they exist**: AGP 9.0+ made Kotlin support "built-in" to the Android Gradle Plugin
and, under its new DSL (on by default), hard-rejects the traditional
`org.jetbrains.kotlin.android` / `org.jetbrains.kotlin.jvm` Gradle plugins this project's
20 modules all apply. Those two plugins are what KSP, Room's Gradle plugin, Hilt's Gradle
plugin, ktlint-gradle, and Detekt's Gradle plugin are all still built around as of the
versions pinned in `gradle/libs.versions.toml` (verified 2026-08-01) — none of them
document or were verified against AGP's built-in-Kotlin model in this project. Both
flags were required together: `builtInKotlin=false` alone still hit a `ClassCastException`
under the new DSL (see `BUILD_VERIFICATION.md` item 1); `newDsl=false` is what actually
restores the legacy extension types those plugins expect.

**What should remove them**: Either of two things, whichever happens first —
1. The wider ecosystem (KSP, Room, Hilt, ktlint-gradle, Detekt) publishes verified
   compatibility with AGP's built-in Kotlin model, at which point this project migrates
   *to* built-in Kotlin (drop the `kotlin.android`/`kotlin.jvm` plugin aliases everywhere,
   configure Kotlin via AGP's own DSL instead) and removes both flags.
2. **AGP 10.0**, per Google's own AGP 9.0 release notes, removes this opt-out entirely.
   That is a hard deadline, not a "someday": **this project cannot upgrade past AGP 9.x
   without resolving this first.**

**Risk assessment**:

| Aspect | Assessment |
|---|---|
| Likelihood of silent breakage | Low today — the flags are explicit, documented, and the build is fully green |
| Blast radius if ignored | High at the AGP 10 upgrade boundary — every module's plugin block needs touching at once, not incrementally |
| Current functional impact | None — six residual deprecation/obsolete-API warnings (`BUILD_VERIFICATION.md`), zero build/runtime effect |
| Effort to resolve properly | Medium — mechanical across 20 modules once the target Kotlin-plugin model is decided, but needs a full re-verification pass per module the same way Phase 2 did |

**Upgrade checklist** (when revisiting):
1. Check whether KSP, Room, Hilt, ktlint-gradle, and Detekt have each published explicit
   AGP-built-in-Kotlin compatibility notes.
2. If yes: on a branch, remove `kotlin.android`/`kotlin.jvm` plugin aliases from every
   module, configure Kotlin via AGP's DSL, remove both flags from `gradle.properties`,
   run `./gradlew clean build` and fix whatever breaks, module by module.
3. If no, but AGP 10 is imminent: this becomes urgent regardless — start the migration
   before upgrading, not after.
4. Re-run the full verification pass in `BUILD_VERIFICATION.md` and update it.

---

## 2. Temporary version pins (compileSdk-37 ceiling)

**What**: three AndroidX libraries are pinned one minor version behind their actual
latest, in `gradle/libs.versions.toml`:

| Library | Pinned at | Latest available | Blocked by |
|---|---|---|---|
| `androidx.core:core-ktx` | 1.18.0 | 1.19.0 | Requires compileSdk 37 |
| `androidx.lifecycle:*` | 2.10.0 | 2.11.0 | `lifecycle-runtime-compose`/`lifecycle-viewmodel-compose` require compileSdk 37 |
| `androidx.hilt:*` (navigation-compose, work, compiler) | 1.3.0 | 1.4.0 | Requires compileSdk 37 |

**Why**: `compileSdk` is capped at 36 because the only SDK platforms installed on this
development machine are up to API 36.1, and AGP 8.13.2/9.2.1's own maximum-recommended
compileSdk was 36 at verification time regardless. Bumping compileSdk without also
resolving item 1 (or without installing platform 37) would trade a clean build for a
hard AAR-metadata failure — this was verified empirically, not assumed (see
`BUILD_VERIFICATION.md` item 2).

**Risk assessment**: Low. These are current, fully-supported releases one minor version
back, not abandoned ones — no known functional gap for this project's needs. Reversible
in isolation (unlike item 1, this doesn't require an ecosystem-wide plugin migration).

**Upgrade checklist**:
1. Install SDK platform 37 (`sdkmanager "platforms;android-37"` or via Android Studio's
   SDK Manager).
2. Bump `compileSdk` (and, separately/independently per Google's own guidance,
   consider `targetSdk`) to 37 in `app/build.gradle.kts`.
3. Bump `coreKtx`, `lifecycle`, `hiltNavigationCompose` in the version catalog to latest.
4. Re-run `./gradlew clean build`; confirm the `AndroidGradlePluginVersion`/
   `GradleDependency` lint suppressions in `app/build.gradle.kts` can be narrowed or
   removed now that the versions are current.

---

## 3. AGP is one minor version behind its own latest

**What**: `agp = "9.2.1"` in the version catalog; Lint itself reports `9.3.1` is
available (surfaced during Phase 2 verification, then suppressed via
`disable += "AndroidGradlePluginVersion"` in `app/build.gradle.kts`'s `lint {}` block).

**Why not bumped now**: 9.2.1 is the version this project's entire dependency graph was
actually verified against end-to-end (see `BUILD_VERIFICATION.md`). Bumping the moment a
newer patch appears, without re-verification, would reintroduce exactly the kind of
unverified-assumption risk this whole phase was about eliminating.

**Risk assessment**: Low — 9.2.1 is not deprecated or broken, this is a currency gap, not
a defect. Revisit on the normal cadence in `DEPENDENCY_POLICY.md`, not urgently.

---

## 4. Detekt/ktlint suppressions worth periodic re-review

Two rule suppressions in `config/detekt/detekt.yml` / `.editorconfig` are broad by
necessity (Compose's PascalCase-function convention conflicts with both tools' default
naming rules) and are not expected to ever need removal. Two suppressions in
`app/build.gradle.kts`'s `lint {}` block (`ObsoleteSdkInt`, and the version-currency
pair `AndroidGradlePluginVersion`/`GradleDependency`) are narrower and tied to items 2–3
above — they should be reconsidered whenever those items are resolved, not left
permanently. `PropertyEscape` is disabled because it's a verified false positive against
a machine-specific gitignored file (`local.properties`), not a real code-quality gap —
no future action needed there.

---

## 5. Backup restore is not hardened against concurrent access

**What**: `BackupRestoreWorker` closes the live `WardrobeDatabase` and overwrites
its file, the DataStore directory, and the images directory, on the assumption
that nothing else touches them mid-restore.

**Why this is debt, not a bug fix waiting to happen**: for a single-user,
foreground-triggered, local-only operation (Phase 1's stated posture), the actual
risk of something else writing to those files during the restore window is low —
but it's genuinely unguarded, not verified safe. Nothing currently locks the
files or prevents, say, a delayed `WorkManager` job from a different feature
writing to the database between the close and the overwrite.

**Risk assessment**: Low likelihood, low-to-medium impact (a corrupted restore,
recoverable by re-attempting from the same backup file) — acceptable to carry as
documented debt rather than over-engineer file locking for a scenario this
product's actual usage pattern makes unlikely.

**Upgrade checklist**: if this is ever tightened, the natural fix is a
process-wide "maintenance mode" flag other repositories check before writing,
rather than OS-level file locks (which don't compose well with Room's own file
handling). Revisit if instrumented testing (Phase 8) or real usage ever
surfaces an actual corruption case — don't speculatively build this now.

---

## 6. Background removal implementation is a reasoned default, not a spike-verified one

**What**: `MlKitBackgroundRemover` (`core:image`, Phase 5b) uses
`com.google.android.gms:play-services-mlkit-subject-segmentation` behind the
`BackgroundRemover` interface ADR-008 designed for exactly this kind of
deferred choice.

**Why this is debt, not a completed decision**: ADR-008 explicitly asked for a
~20-photo spike (this implementation vs. a bundled TFLite model) run against
this app's actual photo distribution — garment on a hanger, flat-lay, worn,
thin straps, cutout necklines — *before* committing to one. That spike needs a
real device camera and a real sample of garment photos; neither exists in this
development environment. Running it here would mean fabricating a result,
which is exactly what Constitution rule 4 exists to prevent. So this phase
made the best *reasoned* choice available (ML Kit is the lower-cost candidate
in ADR-008's own comparison table — no bundled model file, no separate
licensing review) without the empirical verification ADR-008 called for.

**Risk assessment**: Unknown until measured — that's the point. Blast radius if
the cutout quality turns out poor is contained by construction: swapping in a
bundled TFLite model touches exactly one class
(`MlKitBackgroundRemover`/a new equivalent) and one `@Binds` line
(`BackgroundRemoverModule`); nothing above `core:image` changes.

**Upgrade checklist**: once a real device and ~20 representative garment
photos are available (naturally available once Phase 5f's manual-usability
testing happens), run both candidates side by side, compare edge quality on
exactly the hard cases ADR-008 named (thin straps, cutout necklines,
overlapping items), and either keep ML Kit or swap to a bundled TFLite model
based on that evidence — not before.

**Secondary, smaller pin**: `mlkitSubjectSegmentation = "16.0.0-beta1"` in the
version catalog is a beta release because, as of 2026-08-01, it's the only
version published on Google's Maven — there is no stable release to pin
instead. Revisit when a stable release ships.

---

## 7. Staged image results live in memory only

**What**: `StagedImageStore` (`core:data`, Phase 5b) is a plain in-memory map
from staging id to the pipeline's result, populated by `ImageProcessingWorker`
and read by `ImageRepositoryImpl.stageImage`/`commitStagedImage`/
`discardStagedImage`.

**Why this is debt, not a bug**: if the process dies between a capture
finishing successfully and the user tapping "Save" or "Retake," that specific
result is lost — the temp files remain on disk (reclaimed later by
`OrphanedImageCleanupWorker`'s stale-staging sweep) but the capture itself must
be redone. For a single, foreground, seconds-long review step this is an
acceptable, deliberately-not-over-engineered tradeoff, not an oversight.

**Risk assessment**: Low likelihood (requires a process death in a narrow
window while the app is foregrounded), low impact (redo one capture, no data
corruption, no silent loss of a *saved* garment).

**Upgrade checklist**: if this ever proves to matter in practice, the fix is a
small Room table (`staged_image` — stagingId, variant paths, quality verdict)
rather than an in-memory map, so a process death mid-review survives like
everything else backed by Room. Not built now because there is no evidence yet
that the narrow window this protects against actually happens.

---

## Phase 3 readiness

**None of the above (items 1–4) blocked Phase 3.** Every item was build-tooling/
version-catalog scoped (AGP/Kotlin-plugin interop, compileSdk ceiling, static-analysis
suppressions) — none touched Room entities, DAOs, migrations, or schema.

## Phase 5a readiness

**Item 5 above does not block Phase 5b (image pipeline) or Phase 5c+ (UI).** It's a
narrow, documented gap in one feature (restore) that has no bearing on capture,
image processing, or any screen. Phase 5b can proceed without resolving it.

## Phase 5b readiness

**Items 6–7 above do not block Phase 5c (Closet/Garment Details UI).** Item 6
means the cutout feature works and is swappable, just not yet evidence-backed
on this app's specific photo distribution — a UI can be built against
`ImageRepository` regardless of which implementation is behind it. Item 7 is a
narrow, bounded-impact gap in the pre-commit review window, not a data-integrity
issue for anything already saved.

---

## 8. Phase 5c design-system and screen gaps

**What**: several deliberate simplifications made while building Home, Closet,
Garment Detail, Search, Filters, and Sorting (`core:designsystem`, `core:ui`,
`feature:closet`) — each documented in full in `phase-5c-wardrobe-experience.md`'s
"Known limitations" section:

- No bundled Fraunces/Inter font files — `Type.kt` falls back to
  `FontFamily.Serif`/`FontFamily.SansSerif` (this environment can't fetch
  Google Fonts binaries). The real type scale/hierarchy is in place; swapping
  in `.ttf` files is a one-file change.
- `wardrobeShadow()` approximates the design doc's independent
  y-offset/blur/opacity elevation spec onto Compose's single-value
  `Modifier.shadow`.
- Motion uses the real durations/easings from `WardrobeMotion` for toasts,
  sheets, and grid reflow, but does not implement shared-element
  tile→hero-image transforms or the gold-particle favorite-burst from
  `motion-guide.md`.
- The nav dock and modal scrims use a solid `surfaceContainerHigh` at high
  opacity everywhere (the documented API-26-30 fallback) rather than a real
  `RenderEffect` blur on API 31+.
- The full-screen Image Viewer supports tap-to-dismiss and swipe-between via
  `HorizontalPager`; pinch-to-zoom, double-tap-to-2×, swipe-down-to-dismiss,
  and the before/after compare slider are not implemented.
- Edit Garment covers the most commonly-edited fields, not every Phase 3
  attribute (fit, length, sleeve length, warmth/breathability, full
  palette/material-composition editing have no UI yet — purchase date and
  general notes were added by the Add-to-Wardrobe ingestion fix, item 17).
- No keyboard-shortcut framework beyond the search field's IME "search" action
  and Compose's default focus order.
- Performance targets (smooth scrolling at 300/600/1000 garments) are designed
  for, not measured — no device or emulator exists in this environment.
- `ClosetViewModel`'s reactive pipeline can emit one transient, intermediate
  state when a filter changes (the SQL-level filter flag updates one frame
  before `flatMapLatest`'s re-query settles) — observed directly in
  `ClosetViewModelTest`, not expected to be visually perceptible.

**Risk assessment**: Low for all of the above — each is a scoped, contained
simplification with a clear, isolated upgrade path (one file or one class),
not a structural gap. None block Phase 5d.

**Upgrade checklist**: revisit font files and blur once real assets/an API 31+
target matter; revisit shared-element motion and the Image Viewer's missing
gestures if user testing (Phase 5f) flags them as missed; measure performance
once a real device is available; extend Edit Garment's field coverage
opportunistically, not speculatively.

---

## Phase 5c readiness

**Item 8 above does not block Phase 5d.** Every item is a contained,
documented simplification inside the screens this phase built (Home, Closet,
Garment Detail, Edit Garment) — none touch the repository/domain layer Phase
5d's Outfit Builder and Calendar would build on top of.

---

## 9. Phase 5d Outfit Builder/Calendar gaps

**What**: several deliberate simplifications made while building Outfit
Builder, Saved Looks, Outfit Detail, Calendar, and Wear History
(`feature:outfits`, `feature:calendar`, plus the `core:model`/`core:database`/
`core:data` schema work underneath them) — each documented in full in
`phase-5d-wardrobe-stylist.md`'s "Known limitations" section:

- Drag-and-drop's `pointerInput`/`detectDragGesturesAfterLongPress` gesture
  wiring itself is not UI-tested (the ViewModel-level placement logic it
  calls, `onPlaceGarment`, is fully covered) — reliably driving a multi-step
  drag gesture under Robolectric was judged disproportionately fragile for
  what it would verify beyond the existing ViewModel tests.
- Outfit Builder's tap-to-fill/replace flow (tap any slot to open the full
  closet picker) is the accessible non-drag path this phase built, not the
  literal two-step "select a tile, then tap a slot" flow
  `screen-specifications.md` describes — both reach the same outcome; a
  deliberate implementation choice, not an oversight.
- Saved Looks' sort preference is session-only, not persisted to DataStore
  the way Closet's sort is (Phase 5c).
- "Recurring outfit" materializes 8 real, individually-editable weekly
  `PLANNED` rows rather than a persisted recurrence rule — simpler, fully
  visible/editable in the calendar, and avoids inventing RRULE-style
  infrastructure this app has no other use for yet.
- Color Harmony (`feature/outfits/common/ColorHarmony.kt`) is a lightweight
  hue-distance heuristic, explicitly labelled as such in its own KDoc — not
  real color theory.
- Outfit search matches only the outfit's name, not occasion/tag text — a
  real, stated simplification versus Closet's denormalized search.
- Performance targets (500+ saved outfits, 1000 garments, smooth scrolling)
  are designed for, not measured — no device/emulator exists in this
  environment, the same gap Phase 5c documented for its own targets.
- No shared-element transform for Calendar's day↔month transition;
  `motion-guide.md` calls for one, this phase uses a plain state-driven panel
  update instead — the same simplification Phase 5c made for Garment
  Detail's tile→hero transition.

**Separately, a real environment/version bug, not a design simplification**:
Room 2.8.4's driver-based connection manager makes
`androidx.room.testing.MigrationTestHelper` throw
`IllegalArgumentException: This driver is configured to open a database named
'X' but '<absolute path>' was requested` when run under Robolectric's
per-test randomized data directory. Worked around (not routed around) by
`Migration1To2Test` hand-building the v1 database directly from the
committed schema JSON (`core/database/schemas/.../1.json`) via the plain
framework `SQLiteDatabase`, then opening it through the real
`WardrobeDatabase` + `MIGRATION_1_2` — the migration itself is still
genuinely exercised, only the test's *setup* path differs from the
`MigrationTestHelper`-based approach other Room codebases use. Revisit if a
future Room release fixes this Robolectric interaction, at which point the
hand-built-schema setup can be replaced with `MigrationTestHelper` directly.

**Risk assessment**: Low for all of the above — each is a scoped, contained
simplification or a documented, worked-around tooling bug, not a structural
gap. None block Phase 5e.

**Upgrade checklist**: persist Saved Looks' sort preference to DataStore if
user testing (Phase 5f) flags its absence; revisit recurring-outfit scope if
real usage shows 8 weeks is insufficient or a delete/edit-one-instance UX is
needed; measure performance once a real device is available; retry
`MigrationTestHelper` the next time Room is upgraded.

---

## Phase 5d readiness

**Item 9 above does not block Phase 5e.** Every item is a contained,
documented simplification or a worked-around test-tooling bug inside the
Outfit Builder/Calendar screens and schema this phase built — none touch a
layer Phase 5e would need to build on top of differently than what's here
now.

---

## 10. Phase 5e Wardrobe Intelligence gaps

**What**: several deliberate, stated simplifications made while building
Wardrobe Intelligence — Home Insights, Wardrobe Story, Insights, Wardrobe
Health (`feature:stats`, plus the `StatsDao`/`StatsRepository` derived-query
surface underneath it) — each documented in full in
`phase-5e-wardrobe-intelligence.md`'s "Known limitations" section:

- Wardrobe Story's "this season" framing is really "the last 90 days" — a
  real hemisphere-aware season concept needs weather/location data, which is
  explicitly out of scope for this phase.
- The "cost saved by rewearing" Story card is withheld entirely (not shown
  with an approximate number) whenever the rewear-worthy garments span more
  than one currency — `CostPerWearEntry` carries no currency of its own (a
  pre-existing Phase 5a/5c gap), so `feature:stats` resolves currency from
  each garment's own price at display time and skips the card rather than
  merge amounts across currencies.
- "Unexplored Categories" (Wardrobe Health) generalizes to *any* top-level
  category with zero active garments — this schema's `CategoryLevel` has no
  reserved "accessory" semantics, so it can't specifically single out
  accessories the way the phase brief's example implied.
- Chart entrances (bars, rings, heatmap cells) render immediately, with no
  scroll-triggered or shared-element animation — `motion-guide.md` isn't yet
  extended to cover data visualization, the same "designed for later polish,
  not required for correctness" stance Phase 5c/5d took for their own
  transitions.
- Performance (1,000+ garments, 2 years of daily wear history) is verified
  via JVM-only large-dataset unit tests asserting bounded output and a wall-
  clock budget — not measured on a real device, the same gap every prior
  phase has stated for its own performance targets.
- Home shows a compact Home Insights chip row, not all twelve Home Insights
  bullets from the phase brief as separate sections — "Least Used" in
  particular is reachable one tap away via the full Insights screen rather
  than duplicated on Home. A deliberate consolidation in service of the
  phase's own "calm, not a dashboard" philosophy, not an omission.

**Real bugs found and fixed during this phase's own verification pass** (see
`phase-5e-wardrobe-intelligence.md`'s Verification section for the full
list): a `StatsDao` heatmap query that would have double-counted multi-
garment outfit wears (caught before ever compiling, by re-reading the query
against its own intended semantics); a Wardrobe Story card that read the real
wall-clock `Instant.now()` instead of the already-injected `Clock`-derived
date (caught by a real failing test assertion); a fake repository that
ignored the requested `StatsWindow` parameter (caught by a real test
timeout).

**Risk assessment**: Low for all of the above — each is a scoped,
stated simplification or an already-fixed, already-reverified bug, not a
structural gap. None block Phase 5f.

**Upgrade checklist**: add currency to `CostPerWearEntry`/cost-per-wear rows
if a future phase (Shopping/Wishlist) needs multi-currency amounts anyway;
revisit "this season" once Weather Integration exists to back a real season
concept; extend `motion-guide.md` to cover chart entrances if a future polish
pass calls for it; measure performance once a real device is available.

---

## Phase 5e readiness

**Item 10 above does not block Phase 5f.** Every item is a contained,
documented simplification inside the Wardrobe Intelligence screens and
queries this phase built, or a bug that was already found and fixed during
this phase's own verification — none touch a layer Phase 5f would need to
build on top of differently than what's here now.

---

## 11. Phase 6 Personal Wardrobe Stylist gaps

**What**: several deliberate, stated simplifications made while building the
offline recommendation engine, Stylist Preferences, and the 2D Outfit Preview
(`core:data`'s `styling/` package, `feature:outfits`' `recommendations/`/
`preferences/`/`preview/`) — each documented in full in
`phase-6-personal-wardrobe-stylist.md`'s "Known limitations" section:

- Weather stays a documented pass-through — `SuggestionContext.weather` is
  always `null` until Phase 7's `WeatherRepository` exists; the hard weather
  filter has real threshold logic but has never been exercised against real
  forecast data.
- `StyleRule.parametersJson` is a hand-rolled flat `key=value;key2=value2`
  format, not real JSON, chosen over adding a `kotlinx.serialization`
  dependency for what is currently at most two scalar values per rule.
- No rule-authoring or feedback-voting UI — `StyleRuleRepository` is read
  (`observeActiveRules`) but nothing lets a user create a rule or vote on a
  suggestion yet; out of this phase's explicit "Implement ONLY" scope.
- The 2D Preview's per-slot vertical offsets are fixed constants, not derived
  from each garment's real image dimensions or a body-fit model.
- No device-measured performance — the 1,000-garment budget test is a real
  passing JVM regression guard, not a profiling run on real hardware (none
  exists in this environment), the same gap every prior phase has stated.
- Smart Rotation avoids recently-worn *garments*, not recently-worn whole
  *outfits* — an initially-wired `wearEventRepository` dependency was removed
  (caught by detekt's `UnusedPrivateProperty`) once it became clear nothing
  used it; whole-outfit deduplication is deferred, not faked.
- `OutfitSlot.classify`/`AccessoryCategory.classify` are keyword heuristics
  tuned to this phase's own seeded category names — a user who renames their
  categories away from those names gets `null` classifications for the
  affected items (not a crash, but a real precision ceiling).

**Real bugs found and fixed during this phase's own verification pass** (see
`phase-6-personal-wardrobe-stylist.md`'s Verification section for the full
list): a regression where bumping `WardrobeDatabase` to version 3 broke the
pre-existing `Migration1To2Test` (Room requires the full migration path to
the class's *currently-declared* version, not just the step under test); a
detekt `MagicNumber` finding in `MIGRATION_2_3`'s `Migration(2, 3)` call; a
detekt `CyclomaticComplexMethod` finding in `OutfitSlot.classify` (33 against
a threshold of 15); a detekt `DestructuringDeclarationWithTooManyEntries`
finding in `DeveloperPanelViewModel` (a 4-component destructure against a
3-component cap); an unused constructor parameter/field
(`wearEventRepository`/`EngineInput.recentlyWornOutfitIds`) removed rather
than left as dead wiring; and several ktlint formatting violations across
newly-added files in `core:data`, `core:database`, and `feature:outfits`,
fixed via `ktlintFormat` per module.

**Risk assessment**: Low for all of the above — each is a scoped, stated
simplification or an already-fixed, already-reverified bug, not a structural
gap. None block Phase 7.

**Upgrade checklist**: tune weather-filter thresholds against real forecast
data once Phase 7's `WeatherRepository` lands; promote `parametersJson` to
real JSON if a future rule type needs structured parameters; build a
rule-authoring/feedback UI if user testing calls for it; measure performance
once a real device is available; revisit whole-outfit rotation if per-garment
avoidance proves insufficient in practice.

---

## Phase 6 readiness

**Item 11 above does not block Phase 7.** Every item is a contained,
documented simplification inside the recommendation engine and screens this
phase built, or a bug that was already found and fixed during this phase's
own verification — none touch a layer Phase 7 would need to build on top of
differently than what's here now.

---

## 12. Phase 7 Context-Aware Wardrobe Assistant gaps

**What**: several deliberate, stated simplifications made while building
weather integration, context-aware recommendation refinement, calendar/trip/
availability awareness, and the Home assistant experience (`core:network`'s
Open-Meteo layer, `core:data`'s `WeatherRepositoryImpl`/`ContextResolution`/
`ContextScoring`, `feature:settings`'s Weather Settings, `feature:closet`'s
Home screen) — each documented in full in
`phase-7-context-aware-assistant.md`'s "Known limitations" section:

- Weather-filter/factor thresholds (cold-day apparent temp, rainy-condition
  set) are honest heuristics, tuned by inspection, not against real device
  forecast data or real user feedback — the same "designed for, not
  measured" caveat every prior phase has stated for its own scoring logic.
- No geocoding: the manual-location fallback is raw latitude/longitude entry,
  not a typed city name, to respect `core:network`'s stated one-API-client
  budget (Open-Meteo only) rather than add a second network dependency for a
  fallback path.
- No Settings hub screen exists yet — Weather Settings is reached via a
  "Weather" action on the Recommendations screen's top bar, not from a
  fuller Settings home. A future Settings hub can absorb this entry point
  without changing `WeatherSettingsViewModel`/`WeatherSettingsScreen`.
- `Occasion.impliedDressCode()` is a keyword heuristic (Wedding/Gala→FORMAL,
  Office/Work→BUSINESS, etc.), the same "tuned to this project's own seeded
  names" precision ceiling `OutfitSlot.classify` documented in Phase 6 — a
  user who names a calendar occasion outside these keywords gets no implied
  dress code, not a crash.
- Trip-packed exclusion is keyed off *today's* packed state, not the state as
  of the planned outfit's actual date — a garment packed for a future trip
  isn't excluded from a recommendation generated for a date before the trip
  starts. Recommendations for "today" are unaffected; only future-dated
  planning is coarser than ideal.
- `RecommendationRunDiagnostics.rulesAppliedCount` counts only per-garment
  avoid-rule matches, not weather/planned-occasion scoring bonuses — the
  Developer Panel's "Rules applied" row undercounts relative to everything
  that actually influenced a run's scores.
- No device-measured performance for weather fetch/cache latency or
  recommendation-generation time under real network conditions — the same
  "no device or emulator exists in this environment" gap every prior phase
  has stated for its own performance targets.

**Real bugs found and fixed during this phase's own verification pass** (see
`phase-7-context-aware-assistant.md`'s Verification section for the full
list): a cross-module smart-cast compile failure in
`WeatherPreferencesDataStore` (nullable properties from a different module
need a local `val` capture before a null check); `Migration1To2Test`/
`Migration2To3Test` broke the same way Phase 6's own migration tests did when
`WardrobeDatabase` bumped to version 4 (Room validates the full migration
path to the class's *currently-declared* version); eleven detekt findings in
one pass (`CyclomaticComplexMethod`/`ReturnCount` on `weatherFactor`,
`TooManyFunctions` on `RecommendationRuleEngine.kt` and
`StylingEngineRepositoryImpl`, `ReturnCount` on `prependPlannedOutfit` and
`DeviceLocationSource.lastKnownLocation`, `LongParameterList` on
`RecommendationDiagnostics.recordGeneration` and `HomeViewModel`'s
constructor) fixed by extracting sibling files (`ContextScoring.kt`,
`ContextResolution.kt`) and bundling constructor parameters (the established
"bag of repositories" pattern), never suppressed; a MockK stub-ordering bug
in `StylingEngineRepositoryImplTest` where a shared test-builder's default
trip stub silently clobbered a test's own trip-specific stub because it was
registered *after* it (fixed by reordering, not by changing MockK config); a
subtle `stateIn`/`SharingStarted.WhileSubscribed` behavior where a new
subscriber's first emission is the seed value, not the real upstream value,
surfaced by a genuinely failing `WeatherSettingsViewModelTest` (fixed by
awaiting a second emission before asserting, not by weakening the
assertion) — the same subtlety was quietly present but unnoticed in Phase 6's
`StylistPreferencesViewModelTest`; a real Android Lint `MissingPermission`
error on `DeviceLocationSource.kt` (Lint's dataflow analysis doesn't trace a
permission guard through a separate private method wrapped in
`runCatching{}`/`mapNotNull{}`) fixed by inlining the guard into the same
method and adding a documented `@SuppressLint("MissingPermission")` for the
one call Lint still can't trace through the lambda.

**Risk assessment**: Low for all of the above — each is a scoped, stated
simplification or an already-fixed, already-reverified bug, not a structural
gap. None block Phase 8.

**Upgrade checklist**: tune weather/occasion scoring thresholds against real
forecast and usage data once real-world signal exists; add geocoding if a
future phase's network budget is revisited; fold Weather Settings into a
proper Settings hub if/when one is built; extend `rulesAppliedCount` to cover
weather/planned-occasion bonuses if the Developer Panel's diagnostics need to
be exact rather than indicative; key trip-packed exclusion off the planned
outfit's own date instead of "today" if future-dated planning around trips
becomes a real usage pattern; measure performance once a real device is
available.

---

## Phase 7 readiness

**Item 12 above does not block Phase 8.** Every item is a contained,
documented simplification inside the weather/context layer and screens this
phase built, or a bug that was already found and fixed during this phase's
own verification — none touch a layer Phase 8 would need to build on top of
differently than what's here now.

---

## 13. Phase 8 Multi-Device Sync & Companion Experience gaps

**What**: several deliberate, stated simplifications made while building
device pairing, incremental database sync, encrypted transport, conflict
resolution, image sync, and the Wardrobe Sync/Pairing screens (`core:sync`
— new module, `core:database`'s `MIGRATION_4_5`/outbox triggers, `core:data`'s
`sync/` package, `feature:settings`'s Wardrobe Sync/Pairing screens,
`feature:closet`'s Home confirmation and Developer Panel Sync Diagnostics)
— each documented in full in `phase-8-multi-device-sync.md`'s "Known
limitations" section:

- **No real two-device verification** — the single largest gap. Every
  protocol/crypto/conflict-resolution component is verified against a
  simulated peer (piped streams, in-memory byte arrays, two
  Robolectric-hosted handshake threads), genuinely correct at the component
  level, but pairing/discovery/transfer between two *physical* Android
  devices over a real Wi-Fi network has never run, because no second device
  exists in this development environment.
- Sync is a best-effort race (`SyncRepositoryImpl.syncNow()` runs both NSD
  responder and initiator roles concurrently with a 20-second timeout), not
  a guaranteed rendezvous — two devices' sync attempts must temporally
  overlap on the same network for a session to happen at all.
- Collections (seasons, dress codes, tags, palette, materials, outfit slot
  composition) merge by set/keyed union only, no per-entry tombstones — a
  tag removed on one device can reappear if the other device still has it
  at the next sync.
- Reference-data name collisions (two devices independently creating a
  same-named brand/material/tag/occasion before ever syncing) resolve via
  `OnConflictStrategy.IGNORE`, silently skipping one duplicate-named row
  rather than merging them — a deliberate choice over crashing a whole sync
  batch on one name clash.
- A harmless echo: applying an incoming change fires that row's own outbox
  trigger, so the next sync briefly re-sends a change back to the device
  that just sent it — wasteful, not incorrect (the peer's own LWW check
  makes it a no-op), not solved with origin-tracking in this pass.
- `resolveLocalIpAddress()` picks the first non-loopback IPv4 address on any
  active interface, which can pick the wrong one on a device with an active
  VPN or multiple simultaneous networks.
- No device-measured performance for sync latency/throughput/image transfer
  — the same "no device or emulator exists in this environment" gap every
  prior phase has stated for its own performance targets.
- Developer settings and `ClosetPreferencesRepository`/
  `WeatherPreferencesRepository`/`StyleProfileRepository` are deliberately
  not synced — genuinely per-device (diagnostics counters, display
  density, weather-location-specific preferences) rather than wardrobe
  data; syncing weather preferences in particular would actively break
  Travel Mode, since the phone's location differs from the tablet's while
  traveling.

**Scope note**: the brief's Personalized Avatar / 2D outfit-preview-on-avatar
system was **not** built this phase — agreed with the user before
implementation began, since it was already marked CUT in the master
prompt's own feature-tier table and listed under Phase 1's "Future
extensibility (planned, not implemented)" section. It remains a separate,
not-yet-scoped future phase.

**Real bugs found and fixed during this phase's own verification pass** (see
`phase-8-multi-device-sync.md`'s Verification section for the full list): a
build-blocking regression where `syncId: String = ""` as a *default*
constructor parameter on all 16 syncable entities meant every production
insert path that didn't explicitly generate a real syncId defaulted to
`""`, and a second such row violated the new unique index — surfaced as 17
pre-existing test failures across `core:data`, root-caused to the
repository/mapper layer never having been updated to generate real UUIDs,
and fixed across 8 repository implementations plus a `WearEventRepositoryImpl
.duplicateDay` bug that copied the source row's syncId instead of
generating a fresh one; `Migration1To2Test`/`Migration2To3Test`/
`Migration3To4Test` broke the same way Phase 6/7's own migration tests did
when `WardrobeDatabase` bumped to version 5 (Room validates the full
migration path to the class's *currently-declared* version) — the fourth
recurrence of this exact interaction, fixed by adding `MIGRATION_4_5` to all
three tests' migration lists; dozens of detekt `ReturnCount` findings across
every `SyncEntityHandler` implementation (guard-clause-heavy `applyUpsert`/
`applyDelete`/`currentFieldsJson` methods originally had 3–4 `return`
statements against a threshold of 2), fixed via a consistent
boolean-flag-then-single-return restructuring pattern applied identically
across all 16 handlers plus `SyncEngine.kt`; a `LongParameterList` on
`SyncEntityRegistry`'s DAO bag (14 params vs. threshold 10), fixed by
splitting it into `TaxonomyDaos`/`WardrobeSyncDaos`; a `LongMethod` on
`Migration4To5.kt`'s `createSyncTables` (61 lines vs. max 60), fixed by
splitting it into one function per new table; two documented
`@Suppress("TooGenericExceptionCaught")` additions (`SyncEngine.runSession`,
`SyncRepositoryImpl.syncNow`) for the same legitimate "this function *is*
the resilience boundary" reasoning `WeatherRepositoryImpl` already
established in Phase 7 — a sync session spans network I/O and arbitrary
peer-supplied data that can fail in ways the method can't enumerate, and the
failure must be recorded to sync history before propagating; and, across
three full `./gradlew clean build` iterations, several modules
(`feature:closet`, `feature:settings`, `feature:outfits`) whose Phase 8 edits
had never been run through `ktlintFormat`/`detekt` before the first full
build, surfacing formatting violations and two more `LongMethod`/
`LongParameterList` findings from screens that grew past threshold as Sync
Diagnostics/Wardrobe Sync sections were added.

**Risk assessment**: Low for the documented simplifications — each is a
scoped, stated tradeoff or an already-fixed, already-reverified bug, not a
structural gap, **except** the no-real-two-device-verification gap, which is
genuinely unknown-risk until a second physical device is available to test
against. This does not block a future Phase 9, but any user-facing claim of
"sync works" should be qualified until that test happens.

**Upgrade checklist**: run real two-device pairing/sync/conflict testing the
moment a second physical device is available, and tune the discovery-race
timeout against real Wi-Fi association latency observed there; add
per-entry tombstones for collection removals if real usage shows the
union-only merge's reappearing-tag behavior actually bothers anyone;
add origin-tracking to suppress the harmless sync echo if it proves to
matter at real database sizes; measure sync/image-transfer performance once
a real device is available; scope and build the Personalized Avatar system
as its own future phase.

---

## Phase 8 readiness

**Item 13 above does not block a future Phase 9.** Every item is a
contained, documented simplification inside the sync layer and screens this
phase built, or a bug that was already found and fixed during this phase's
own verification — the one open, higher-stakes item (no real two-device
verification) is a testing gap tied to this development environment, not a
design flaw, and is stated plainly rather than glossed over.

---

## 15. Phase 9 Smart Wardrobe Intelligence & Daily Assistant gaps

**What**: several deliberate, stated simplifications made while building
per-garment/per-outfit derived insights, the Daily Wardrobe Brief, forgotten/
overused/never-worn detection, shopping-gap and duplicate-garment surfacing,
capsule suggestions, real trip-packing generation (`feature:trips`, built
from scratch), calendar conflict detection, expanded Style Insights, and a
richer Home screen (`core:model`'s new `intelligence/` package, `core:data`'s
`WardrobeIntelligenceRepositoryImpl.kt` + four sibling `*Builders.kt` files,
`CapsuleGenerator.kt`, `TripRepositoryImpl.kt`'s `generatePackingSuggestions`,
`feature:trips`' three new screens, `feature:closet`/`feature:outfits`/
`feature:stats`/`feature:calendar` UI additions) — each documented in full
in `phase-9-smart-wardrobe-intelligence.md`'s "Known limitations" section:

- **No real `IMPORTED_NEVER_WORN` signal** — `Garment` has no import-source
  flag anywhere in the schema, so `NeverWornReason` only distinguishes
  recently-added vs. purchased-long-ago, a real gap rather than an invented
  flag.
- **`LocalDate.toMeteorologicalSeason()` is Northern-hemisphere-only**
  (month-bucket mapping), the same disclosed-heuristic family as item 10's
  "this season = last 90 days."
- **Trip packing has no real forecast** — each day's `SuggestionContext` is
  built with `weather = null`, since there is no forecast API for a future
  or distant destination; trip-day outfits reflect Stylist Preferences/
  rotation/favorite scoring only, not weather-appropriateness.
- **Capsule generation uses a deliberately lighter scoring rule** than the
  full Phase 6 `RecommendationRuleEngine` — a curated small set doesn't need
  the full whole-outfit-assembly engine's internals.
- **A naming overlap, not a functional collision**: `feature:stats` already
  has a pre-existing "Wardrobe Health" screen (Phase 5e — qualitative,
  advisory-only cards). This phase's new Home "Wardrobe Health Score" card
  is a different concept (a single 0–100 composite number) that happens to
  share the English name — no code conflict, but a real UX-naming risk
  worth resolving in a future phase.
- **Wardrobe Health Score and rotation-balance are explicitly labeled
  composite heuristics**, not scientifically validated metrics.
- **No device-measured performance** — the same "no device or emulator
  exists in this environment" gap every prior phase has stated.
- **No drag-gesture or interaction UI tests** for the new `feature:trips`
  screens or the new Capsules/Duplicates screens in `feature:outfits` —
  covered by ViewModel-level tests only, consistent with this codebase's
  existing UI-testing depth.

**Real bugs found and fixed during this phase's own verification pass** (see
`phase-9-smart-wardrobe-intelligence.md`'s Verification section for the full
list): detekt's `TooManyFunctions` empirically requiring ≤10 functions per
class/interface/file (the same off-by-one behavior items 9/12/13 already
documented — a labeled "threshold 11" still fails at exactly 11) surfaced
across seven files this phase touched, the largest being
`WardrobeIntelligenceRepositoryImpl` at **23** class-member functions,
fixed by splitting the interface (`StatsRepository`'s 3 new methods folded
into `UsageStats` fields instead; `WardrobeIntelligenceRepository`'s
`observeForgottenGarments`/`observeOverusedGarments`/`observeNeverWornGarments`
consolidated into one `observeWardrobeAlerts()`) and by moving private
helpers to four new sibling `*Builders.kt` files (each independently kept
under the same ≤10 per-file ceiling, since moving functions out of a class
only helps if the destination file doesn't itself cross the threshold);
the identical pattern applied to `OutfitAssembler.kt` (16→9, new sibling
`SlotCandidatePool.kt`), `StylingEngineRepositoryImpl.kt` (11→8),
`TripRepositoryImpl.kt` (13→8), and `CapsuleGenerator.kt` (a 66-line
`presetFor` `when` expression restructured into a table of named `val`
presets rather than a `when`, since `presetFor` itself needed to shrink
without adding nine new per-type functions that would have re-triggered
the same file-level ceiling); a genuine Dagger dependency cycle
(`TripRepositoryImpl` ↔ `StylingEngineRepositoryImpl`, each needing the
other) fixed via `dagger.Lazy<StylingEngineRepository>` injection, confirmed
by a real `:app:kspDebugKotlin` run succeeding; two ktlint "dangling
top-level KDoc" violations in the new sibling files (a file-level
explanatory KDoc immediately followed by another KDoc'd declaration or an
EOL comment, both disallowed orderings), fixed by demoting the file-level
explanatory comment to a plain block comment; and **one real test bug**
caught only by actually running the test suite, not just compiling it: a
newly-consolidated `observeWardrobeAlerts()` query now combines three flows
where the old `observeForgottenGarments()` only needed one, and
`WardrobeIntelligenceRepositoryImplTest`'s forgotten-bucketing test still
passed a bare `mockk(relaxed = true)` for the `GarmentRepository` dependency
— a relaxed mock's `Flow`-returning method never actually emits, so
`combine()` never produced a value and `.first()` hung until
`kotlinx.coroutines.test`'s `UncompletedCoroutinesError` fired; fixed by
stubbing `observeGarments(any())` to return `flowOf(emptyList())`, matching
the explicit-stub style the file's other two tests already used.

**Scope notes agreed with the user before implementation began** (via
`AskUserQuestion`, the same discipline Phase 7/8 already established for
resolving real ambiguities against the existing schema rather than
guessing): outfit "Average Rating" derives from Phase 6's existing
`Feedback` up/down votes rather than a new rating schema/UI; `feature:trips`
(previously a registered-but-empty placeholder module) got its first real
screens built this phase rather than backend generation logic alone.

**Risk assessment**: Low. Every item above is a scoped, stated tradeoff or
an already-fixed, already-reverified bug — none is a structural gap. The
Wardrobe Health naming overlap is the only item worth resolving proactively
in a future phase, purely for UX clarity, not because anything is broken.

**Upgrade checklist**: resolve the "Wardrobe Health" naming overlap between
the Home card (Phase 9) and the Insights advisory screen (Phase 5e); add a
real forecast-aware trip-packing pass if the weather integration ever grows
a way to forecast a non-current location; add an `IMPORTED_NEVER_WORN`
bucket if a future phase adds an import-source flag to `Garment`.

---

## Phase 9 readiness

**Item 15 above does not block a future Phase 10.** Every item is a
contained, documented simplification inside the intelligence/assistant
layer and screens this phase built, or a bug that was already found and
fixed during this phase's own verification — nothing pre-existing from
Phases 1–8 broke, and no schema changes were made (4 new derived `StatsDao`
queries, 1 new `FeedbackDao` query, zero new tables/columns/migrations).

---

## 16. Phase 10 Personal Virtual Try-On gaps

**What**: several deliberate, stated simplifications made while building a
fully local 2D virtual try-on system — affine-only garment compositing
(`core:tryon`'s `DefaultPlacementCalculator`/rendering, `feature:tryon`'s
`TryOnScreen`), guided front-camera body-profile capture, on-device ML Kit
Pose Detection as a best-effort default-placement seed, manual mask
editing, deterministic lighting-match and shadow rendering, a non-
interactive render cache, and integration into Outfit Detail/Home/Saved
Looks/Trip Planner — each documented in full in
`phase-10-personal-virtual-tryon.md`'s "Known limitations" section:

- **No visual render-quality verification is possible in this
  environment** — no device, no real human/garment photos. Compositing
  correctness (geometry, persistence, gesture wiring, cache invalidation)
  is genuinely tested; whether a render actually looks convincing is
  unknown until measured on a real device — the same category of gap item
  6 already states for background-removal accuracy.
- **Pose-detection landmark accuracy is likewise unverified here** —
  scoped so failure only degrades an overridable heuristic default, never
  breaks the feature.
- **Draping is necessarily approximate**: flat 2D affine compositing, no
  fabric physics, no true depth (only static `ClothingDepth`+`OutfitSlot`
  ordering).
- **A real, disclosed category-fidelity gradient** — tops/dresses/
  outerwear best, footwear hardest (selfie camera-to-feet perspective has
  no ground-plane correction at all).
- **Shadow blur only renders on API 31+** (`RenderEffect`'s real minimum;
  this project's `minSdk` is 26) — `ShadowRenderer.supportsBlur` is a real,
  tested capability gate, not a universal "blurred everywhere" claim.
- **Lighting matching is a deterministic color-grade heuristic**
  (`LightingMatcher`), not true relighting — it cannot correct for a
  strongly directional light source the garment cutout wasn't originally
  lit by.
- **Manual masking requires real per-garment user effort** — no auto-
  segmentation exists anywhere in `GarmentMaskEditor`, by design ("no AI
  for this step" per the brief), not a missing feature.
- **`ClothingDepth.INNER` has no current classifying category** — nothing
  in this schema models a true base/under-layer distinct from `TOP` yet;
  defined now for correctness/extensibility, not faked as meaningful.
- **"Last used becomes default" is a simple recency heuristic**
  (`TryOnPlacementRepository.defaultTemplateFor`), not context-aware.
- **CameraX front-camera guided capture is device-dependent** — the same
  "hardest tier to automate" testing bucket `phase-1-architecture.md`
  Section 27 already named for the back-camera pipeline, not a new gap.
- **Anchor-region resolution is keyword-based**
  (`TryOnPlacementRepositoryImpl.anchorRegionFor`, via `OutfitSlot`/
  `AccessoryCategory`/`JewelryCategory.classify`) — the same free-form,
  user-editable category-tree best-effort match every other slot-
  classification call in this codebase already accepts, not a guaranteed
  one.

**Real bugs found and fixed during this phase's own verification pass**
(see `phase-10-personal-virtual-tryon.md`'s Verification section for the
full list): a genuine Kotlin compile error from an unnecessary
`androidx.compose.foundation.layout.weight` import shadowing the correct
`ColumnScope`/`RowScope` member extension with an unrelated internal
`RowColumnParentData` property (fixed by removing the import entirely —
`weight` is a scope member, never imported elsewhere in this codebase);
the same `TooManyFunctions`/`ReturnCount`/`MagicNumber`/`LongMethod`/
`LongParameterList`/`MatchingDeclarationName` detekt/ktlint findings every
prior phase has hit, fixed the same way (splitting sibling files, bagging
callback parameters into a data class — `SavedLooksGridActions`, mirroring
`OutfitDetailActions`'s precedent — restructuring to single-return
expressions, suppressing `MagicNumber` only where a lookup key already
documents the literal, the same `WardrobeAlerts.kt` precedent); and one
real ViewModel-test race caught only by actually running the suite: a
first draft of `BodyProfileCaptureViewModelTest` called `onPhotoCaptured`
for all four guided poses back-to-back without awaiting each pose's own
state transition, racing ahead of the ViewModel's async `advance()` and
capturing every photo against the same stale `currentPose` — fixed by
sequencing the test through the real reactive `uiState` flow instead,
matching how the guided-capture screen's own UI can only ever call it once
per currently-displayed pose.

**Deliberately not unit-tested, and why**: `TryOnRenderCache.render`'s
undecodable-background-path `null` guard has no test, because
Robolectric's default `BitmapFactory` shadow doesn't faithfully reproduce
real Android's file-not-found behavior (it returns a stub bitmap rather
than null) — a test asserting against a missing path here would verify
something this environment cannot actually confirm. The production guard
remains in the code, honestly undocumented by a test rather than backed by
a fabricated one.

**Scope decisions agreed with the user before implementation began** (via
`AskUserQuestion` and two rounds of plan review, the same discipline every
prior phase has used to resolve real ambiguities rather than guess): body
profiles/placements/masks are eligible for Phase 8's existing local-network
sync; single active body profile per device for v1; placement scope is
per-garment (not per-outfit), with named templates layered on top so
several variants can still coexist; ML Kit Pose Detection added now as a
soft, always-overridable enhancement. The expanded scope (placement
templates, separately persisted measurements, render caching, clothing
depth, manual masking, lighting matching, soft shadows) was explicitly
confirmed in scope over a shorter alternative proposal.

**Risk assessment**: Low. Every item above is a scoped, stated tradeoff —
none is a structural gap, and none violates any of the ten Permanent
Product Principles at the top of this file (body photos/masks only ever
travel the existing local-network-only sync channel; pose detection runs
entirely on-device; no new network dependency of any kind was added).

**Upgrade checklist**: a real device/photo-set verification pass on render
quality, lighting-match naturalness, and shadow realism, the same
"unverifiable in this environment" gap every prior phase's own spikes have
carried forward; consider a true perspective/contour-warp mode as an
optional upgrade once warp quality can actually be measured on real
photos; an `IMPORTED_NEVER_WORN`-style category depth signal if a future
phase ever adds a true base/under-layer category distinct from `TOP`.

---

## Phase 10 readiness

**Item 16 above does not block future work.** Every item is a contained,
documented simplification inside the try-on layer and screens this phase
built, or a bug that was already found and fixed during this phase's own
verification — nothing pre-existing from Phases 1–9 broke, and the one
schema change (`Migration5To6`, 5 new tables) is additive-only, covered by
a real migration test, with zero changes to any pre-existing table.

---

## 17. Add-to-Wardrobe ingestion fix gaps (2026-08-04)

**What**: a release-blocking gap found on real-device first-time-user
testing (a OnePlus Pad) — there was no visible way to add a garment to the
wardrobe anywhere in the shipped app, even though the entire capture →
background-removal → save pipeline (`GarmentImagePipeline`,
`BackgroundRemover`, `ImageRepository`, `GarmentRepository.saveGarment`)
had existed since Phase 5b/5c. This was explicitly deferred in
`phase-5c-wardrobe-experience.md`'s own "Known limitations" section and
never revisited by any later phase. Fixed with: a permanent FAB on Home
and Closet opening an "Add to Wardrobe" sheet (Take Photo / Choose from
Gallery / Import Multiple Photos); a new `feature:capture` module
(`GarmentCaptureScreen`, `GarmentImportQueueScreen`,
`GarmentReviewMetadataScreen`); a Room-backed, device-local-only import
queue (`ImportQueueRepository`/`ImportQueueItemEntity`) that resumes
correctly after an app restart or crash, rather than losing an in-progress
multi-photo import; a `Save`/`Save as Draft` split reusing the
already-existing but previously-unused `Garment.isReviewed` flag and
Home's "Continue Editing" section; a `NeedsReviewBadge` making a draft
visible wherever it appears; an Undo Snackbar on garment deletion
(`GarmentDetailScreen`, Closet's multi-select delete); and a taxonomy
expansion (~43 new leaf categories nested under the existing 13 top-level
buckets, `Migration6To7`) to cover the requested clothing/footwear/bag/
accessory/jewelry vocabulary.

- **A real, pre-existing bug this same fix would otherwise have shipped**:
  the taxonomy expansion's ~43 new leaf names exposed that
  `OutfitSlot.classify` (Phase 6's keyword-based garment→slot heuristic)
  matched 21 of them to nothing, which would have silently dropped any
  garment tagged with one of those categories out of the recommendation
  engine's candidate pool and out of Try-On's placement-anchor resolution
  — no crash, no log, just a garment that never gets suggested for no
  visible reason. Fixed by widening `OutfitSlot`'s keyword lists
  (`OutfitSlotTest` now asserts every new leaf name classifies correctly).
  A second real collision was found the same way: "Laptop Bag" contains
  the substring "top" and was matching the `TOP` keyword list before ever
  reaching the `BAG` list — fixed by checking `BAG_KEYWORDS` before
  `TOP_KEYWORDS`/`BOTTOM_KEYWORDS`/`OUTERWEAR_KEYWORDS`/`SHOES_KEYWORDS`,
  the same crude-substring-matching risk this codebase's existing
  `AccessoryCategory`/`JewelryCategory.classify` heuristics already accept
  as a known tradeoff, not a new one.
- **No on-device category-suggestion ML.** Considered (ML Kit Image
  Labeling, mirroring `BackgroundRemover`/`BodyAnchorEstimator`'s
  swappable-interface pattern) and deliberately cut from scope — this fix
  is explicitly a bug fix with all intelligence/AI work paused, and a plain
  two-level `CategoryPicker` (TOP → SUB, no preselection) is a complete,
  zero-risk v1. A candidate fast-follow, not a gap in this fix's own scope.
- **Duplicate detection is metadata-heuristic plus exact-checksum only** —
  `GarmentRepository.findPotentialDuplicates` matches on category+color
  (independent of `WardrobeIntelligenceRepositoryImpl`'s wear-history-based
  `DuplicateGroup` machinery, which needs data that doesn't exist yet for a
  garment mid-creation) and `ImageRepository.findGarmentIdForChecksum`
  matches only a byte-identical re-imported file. No perceptual/visual
  similarity hash exists anywhere in this codebase or was built here.
- **Undo Delete is a timed in-memory pending-delete, not a persisted
  trash** — the standard Android Snackbar-undo shape (Gmail/Photos use the
  same pattern). A process kill during the ~5-second undo window means the
  delete simply never completes (fails safe — nothing is lost), rather
  than being reliably recoverable after a restart. This is genuinely new
  UI infrastructure for this app — no `SnackbarHost`/`showSnackbar` usage
  existed anywhere before this fix, and `deleteGarment` was, and remains, a
  real hard delete once the undo window elapses.
- **The persistent import queue resumes by restarting `stageImage` from
  the source file** for any row not yet `READY_FOR_REVIEW` (idempotent —
  safe to redo); a crash in the narrow `SAVING` window (between a metadata
  submit and its commit finishing) falls back to `READY_FOR_REVIEW` rather
  than losing the whole import, so only that one item's entered metadata
  needs redoing. A separate, narrower edge case — the in-memory staged
  cutout itself (`StagedImageStore`, already a disclosed limitation, item 7
  above) being lost to a process death between staging finishing and the
  review screen opening — is detected (`peekStagedImage` returns `null`)
  and handled by resetting that one item back to `PENDING` so the queue
  automatically restages it, rather than crashing or showing a blank
  screen.
- **No crop UI in the review screen** — the underlying pipeline supports
  an optional `cropRect`, but nothing in the request asked for a crop step,
  so it was not built, avoiding scope creep beyond what was actually
  requested.
- **Real camera/gallery/queue UX** (permission prompts, Photo Picker
  behavior, actual background-removal quality on real garment photos) is
  the same "hardest tier to automate here" gap already disclosed for every
  prior camera-touching phase (no physical device, no real human/garment
  photos in this environment) — which is exactly why the user's new
  permanent standing rule (real-device first-time-user testing before any
  phase is signed off, see below) exists, and this fix's own completion is
  explicitly gated on it, not on `clean build` alone.

**A real bug found and fixed during this fix's own verification pass**:
`GarmentSyncHandlerTest`'s hand-authored `GarmentWire` JSON fixture
predates the new `notes` field and lacked it entirely — since the wire
class has no default for it, deserializing the old fixture threw
`MissingFieldException` the moment the field was added, caught only
because the full test suite was actually run, not assumed passing, and
fixed by adding the field to the fixture rather than giving the wire field
a silent default that could mask a genuinely missing value from a real
peer device.

**Risk assessment**: Low. Every item above is a scoped, stated tradeoff —
none is a structural gap, and the two real classification bugs found
during this fix's own build (widened `OutfitSlot` keywords, the
"Laptop Bag" ordering fix) were caught and fixed before shipping, not
discovered after. The new `import_queue_items` table is intentionally
excluded from Phase 8 sync (device-local only, the same
`weather_cache`/`stats_cache` precedent), and the `garments.notes`/new
category rows added by `Migration6To7` are purely additive.

**Upgrade checklist**: an on-device category-suggestion model as a
fast-follow if intelligence work resumes; a persisted soft-delete/trash
table if the timed in-memory Undo window is ever judged insufficient; a
perceptual/visual duplicate-detection hash if metadata-heuristic matching
proves too coarse in practice — none of these are required for this fix
to be considered complete, only real, disclosed candidates for later.

---

## Add-to-Wardrobe ingestion fix readiness

**Item 17 above does not block future work, once the mandatory real-device
checklist below actually passes.** Every item is a contained, documented
tradeoff inside the ingestion flow this fix built, or a bug that was
already found and fixed during this fix's own verification — nothing
pre-existing from Phases 1–10 broke, and the schema change (`Migration6To7`
— one new column, ~43 new category rows, one new device-local-only table)
is additive-only, covered by real migration tests, with zero changes to
any pre-existing table's meaning.

**Permanent standing rule, effective from this fix forward** (see the
`feedback_real_device_validation_gate` memory): every phase must be tested
from a first-time user's perspective on a real physical device before it
is considered complete — `clean build` green is necessary but not
sufficient. This fix is the reason the rule exists: it was found by
exactly this kind of test, after ten prior phases' `clean build`s were all
genuinely green without ever catching it. No physical device exists in
this development environment, so this fix's own real-device pass must be
run by the user (or whoever holds the hardware) before sign-off — see the
checklist handed off alongside this entry.

---

## 18. Add-to-Wardrobe v2 / Unified AI Provider Architecture gaps (M1–M12)

**What**: the full cloud-AI provider architecture (ADR-012) — the `core:ai`
Gateway/Adapter/Job-Manager/Cache/Metrics/Privacy stack, capability Routers
for all five capabilities (Garment Extraction/Reconstruction/Metadata,
Outfit Styling, Virtual Try-On), the M10 premium review screen, and the
M12 Cloud Outfit Styling + Cloud Virtual Try-On fast-follow. M11's own
"full verification + limitations writeup" milestone was superseded by
the user moving directly to M12; this entry folds both together since
nothing from M11 shipped separately.

- **No named vendor adapter (OpenAI/Azure OpenAI/Gemini/Claude/OpenRouter/
  Ollama/Generic REST) has ever been exercised against a real, live
  account** — each is unit-tested against `MockWebServer` against its
  documented wire shape only. Validating against a real API key/vendor is
  explicitly the user's own M13 real-cloud validation gate; it cannot be
  run in this environment.
- **Cloud Outfit Styling's cache key is achieved by a synthesized
  "context fingerprint" bitmap, not a Gateway architecture change** — the
  Gateway's cache key is always `sha256(image):capability:provider:model:
  promptVersion` (ADR-012 §4), with no per-capability "extra fields" hook.
  Rather than add one (which would touch every capability's cache
  behavior, not just Styling's), `stylingContextFingerprintBitmap`
  (`core:data`'s `StylingCloudContext.kt`) encodes wardrobe/weather/
  occasion state into a small deterministic bitmap sent as the "image"
  whenever the user hasn't attached a real inspiration photo — the
  Gateway's existing, unmodified cache lookup then behaves exactly like
  the spec's `(wardrobeHash, weatherHash, occasion, provider, model,
  promptVersion)` key. When a real inspiration image *is* attached, the
  cache instead keys on that image's own contents, which does not vary
  with wardrobe/weather — a minor, disclosed deviation from the literal
  spec in that one case.
- **The inspiration-image parameter exists end-to-end but has no UI entry
  point** — `CloudStylingEngine.suggestOutfits`/`StylingEngineRouter` both
  accept an `inspirationImage: Bitmap?`, but no screen lets a user attach
  one yet. A real, wired capability with no caller, not a stub.
- **`OnDeviceVirtualTryOnEngine` anchors every garment at the shoulder
  line** (`TryOnAnchorRegion.SHOULDER_LINE`) — `VirtualTryOnEngine.render`
  has no garment-slot parameter (a plain "try this cutout on," not "try
  this top on"), so it can't distinguish a top from a pair of shoes the
  way the live, interactive `feature:tryon` `TryOnScreen` (which does know
  the slot, via `GarmentPlacementTemplate`) already can and continues to.
  A disclosed simplification specific to this new batch-render wrapper,
  not a regression in the existing interactive flow.
- **The on-device wrapper's lighting/mask application is new compositing
  code**, not a reuse of `TryOnRenderCache`'s own `flatten`/`drawLayer`
  (which operates on saved file paths and a real placement template,
  not raw in-memory bitmaps) — it mirrors that file's Canvas/Matrix
  technique and reuses `TRY_ON_LAYER_WIDTH_FRACTION` for consistent
  sizing, but is a second, independent implementation of the same idea.
  `LightingAdjustment`'s KDoc always said it was "applied to garment
  layers at render time"; this is the first real consumer of that
  contract — the live interactive screen does not yet apply it either.
- **The Try-On comparison viewer's mask input is always `null` in
  practice** — `VirtualTryOnRenderRepository.render`/`TryOnCompareViewModel`
  both accept/thread a `maskPath`, but nothing wires
  `feature:tryon`'s existing `GarmentMaskEditor` output into this specific
  call site yet, so every comparison render currently uses the cutout's
  own alpha unmodified.
- **No real cloud render has ever been visually verified** — the same
  "no device, no real photos, no live provider account" gap items 6/16
  already state for background-removal/try-on-render quality generally,
  now extended to the cloud render path specifically.

**Real bugs found and fixed during this pass** (caught by actually running
`./gradlew clean build`, not assumed from reading the code): four
pre-existing Hilt wiring defects in `core:ai` that only surface once
`:app` itself compiles (module-level `:core:ai:test` never builds the
full `SingletonComponent` graph) — `core:ai`'s own `AiNetworkModule`/
`AiWorkManagerModule` duplicate-bound `OkHttpClient`/`Retrofit`/`Json`/
`WorkManager` against `core:data`'s equivalents once both sit in the same
component (fixed by removing the redundant `WorkManager` binding and
qualifying `core:ai`'s vendor-API `OkHttpClient`/`Retrofit`/`Json` with a
new `@AiHttp` annotation, since those really are separate instances with
different timeouts/purpose); `PersonRegionMasker`, `PrivacyPreprocessor`,
and `FaceBlurrer` each had an `@Inject`-constructed implementation but no
`@Binds` module wiring it to its interface. Separately, `DefaultAiGateway.
runImageTask`'s cache key hashed only the first of `images: List<Bitmap>`
— harmless for Extraction/Reconstruction's always-single-image calls, but
a real latent bug for Try-On's body+garment(+mask) request (two different
garments on the same body photo would have collided in the cache) —
fixed by combining every payload's own hash into one digest when more
than one image is present, provably unchanged for the single-image case.
Separately, and unrelated to any M12 code: `WardrobeDatabaseSeedTest`
(Phase 6, `core:database`) intermittently failed only during a full
`./gradlew clean build`'s heavy parallel contention, never standalone —
root-caused to `WardrobeDatabase.SeedCallback.onCreate` dispatching its
DAO writes onto Room's own real background query executor, invisible to
`advanceUntilIdle()` (which only drains the test's own coroutine
dispatcher); fixed by waiting on the DAO's own invalidation-tracked
`Flow` for every asserted category name to actually appear (bounded by a
real timeout) instead of relying on virtual-time advancement to
synchronize with Room's real threads.

**Risk assessment**: Low for the disclosed simplifications above — each
is a scoped, stated tradeoff with a clear upgrade path, not a structural
gap. The four Hilt wiring bugs and the cache-key bug were real defects,
not simplifications, but were caught and fixed within this same pass,
before ever reaching a device. The still-open, higher-stakes item is the
same one every AI-touching milestone has carried: no named vendor adapter
has been validated against a real account, and no cloud render has been
seen with real eyes — both are explicitly the user's own M13 gate.

**Upgrade checklist**: run M13's real-device-plus-real-cloud-credential
pass per enabled capability; wire an inspiration-image picker into the
Styling review flow if cloud styling proves popular enough to want one;
resolve `OnDeviceVirtualTryOnEngine`'s anchor-region gap by threading the
garment's real `OutfitSlot` through `VirtualTryOnEngine.render` if this
wrapper needs multi-slot fidelity later; wire `feature:tryon`'s
`GarmentMaskEditor` output into `TryOnCompareViewModel`'s `maskPath` once
a natural navigation entry point from the mask editor to the comparison
screen exists.

---

## Add-to-Wardrobe v2 readiness

**Item 18 above does not block future work.** Every item is a contained,
documented simplification or a bug that was already found and fixed
during this same verification pass — the one open, higher-stakes item
(no real-device/real-cloud-account validation) is a testing gap tied to
this development environment, exactly like Phase 8's own two-device gap,
and is stated plainly rather than glossed over. `./gradlew clean build`
is genuinely green; the mandatory real-device-plus-real-cloud pass (M13)
is the user's own to run.

---

## 19. M13 Production Validation gaps

**What**: M13 is a validation-only gate (no architectural work) whose own
exit criteria require physical-device and real-cloud-provider testing —
see `PRODUCTION_VALIDATION_REPORT.md` for the full checklist and every
automated result this pass actually produced. Three genuine test-coverage
gaps were closed as part of this validation pass (verification work, not
new features): `DefaultAiGatewayTest` gained two tests proving the cache
key genuinely varies with `promptVersion` and with vendor (previously
only "identical repeated call" and "different image" were covered); a new
`BitmapEncodingTest` gives the "cloud payloads never carry EXIF metadata"
claim (ADR-012 §2) a real regression test instead of only an architectural
argument, by scanning the actual encoded bytes for an EXIF chunk/marker
signature.

**What remains genuinely untestable in this environment** (see
`PRODUCTION_VALIDATION_REPORT.md`'s checklist for the exact steps): the
entire physical-device Add-to-Wardrobe workflow; every named vendor
adapter against a real, live account; real-network failure modes (airplane
mode, a genuinely slow/flaky connection, an actually-expired key, a real
`429` from a real provider) as opposed to the mocked-server equivalents
already covered; and all performance measurement (`benchmark` module has
no `StartupBenchmark`/macrobenchmark test classes yet — its own README has
said so since Phase 2, and remains true here; `./gradlew
:app:generateBaselineProfile` needs a connected device/emulator, which
does not exist in this environment).

**Risk assessment**: Unknown until measured, by construction — the same
category every prior "no device in this environment" gap in this file has
carried. Everything that *is* mechanically verifiable from this codebase
(cache correctness, provenance/validation logic, privacy preprocessing
invariants, the full existing regression test suite, `./gradlew clean
build`) passed.

**Upgrade checklist**: execute `PRODUCTION_VALIDATION_REPORT.md`'s
device/cloud/performance checklists on real hardware and real provider
credentials; write `StartupBenchmark`/scroll-jank macrobenchmark test
classes against real screens (never done in this project's history) once
a device/emulator is available to run them against; update that report's
own checklist to "passed" per item as each is actually run, not assumed.

---

## 20. RC1 Production Hardening gaps

**What**: RC1 was a validation-only, no-new-features milestone (a full
security/release-build/dependency/memory/AI-pipeline audit) — its findings
and fixes are the full `SECURITY_AUDIT.md`, summarized here. Three real
defects were found and fixed (not simplifications): (1) `VirtualTryOnRenderRepositoryImpl`
(M12) wrote a `tryon_preview_*.webp` scratch file into `cacheDir` on every
render with nothing ever deleting them — `OrphanedImageCleanupWorker` now
sweeps these on its existing daily cadence; (2) `AiResultCacheDao.deleteByCacheKey`
existed but nothing in production ever called it, so the Gateway's
multi-stage cache (rows and files) grew without bound for the life of an
install — the same worker now retains rows/files for 30 days, then sweeps
them; (3) `EncryptedApiKeyStore`'s backing file was excluded from cloud
backup but not from Android's device-transfer channel — since its
`MasterKey` is generated inside the *source* device's Android Keystore and
cannot itself transfer, copying the raw encrypted file to a new device
would leave undecryptable ciphertext behind, a real, documented
`EncryptedSharedPreferences` failure mode that can throw on first read
rather than degrade gracefully; now excluded there too.

Also removed one genuinely unused dependency (`org.junit.jupiter:junit-jupiter`,
declared but never referenced by any module) and documented two
previously-undocumented but actively-used dependencies
(`play-services-mlkit-subject-segmentation`, `mlkit-pose-detection`) in
`DEPENDENCIES.md`. Created a root `README.md` (a genuine "how does a new
developer get started" gap — none existed before RC1, only a Phase-2
historical snapshot in `PROJECT_STRUCTURE.md` and two per-module
`README.md`s).

**AI pipeline architecture consistency** (RC1 Phase 5): all five
capabilities were checked against the same ten-point list (Router/
on-device impl/cloud impl/metrics/cache/provenance/prompt version/privacy
preprocessing/failure handling/retry) and confirmed structurally
consistent — no capability bypasses the Gateway. Two intentional, minor
asymmetries found, not fixed (out of RC1's "no new features" scope,
already implied by their own milestones' literal acceptance criteria):
`ImageRetryStage` has no `RECONSTRUCTION` entry (M10's own spec named only
Extraction/Enhancement/Metadata for one-tap retry); Outfit Styling and
Virtual Try-On have no dedicated "Retry" button distinct from their
existing "Generate Another Look"/re-render equivalents.

**What remains genuinely untestable in this environment**: unchanged from
item 19 — a real device, a real cloud-provider account, and real
performance measurement. RC1's audit did not (and could not) close any of
these; it closed every gap that source inspection and automated tests
*can* close.

**Risk assessment**: Low for the three fixes above (each is a contained,
verified, tested change) and the dependency/documentation cleanup. The
one disclosed-not-fixed item — Ollama/Generic REST needing HTTPS for a
local endpoint, since no network security config exception was added — is
the platform's own secure default working as intended, not a defect; a
future release can add a deliberate, scoped cleartext exception if a real
user's self-hosted setup needs one.

**Upgrade checklist**: none of the above blocks a beta release. Before a
*production* release: run `PRODUCTION_VALIDATION_REPORT.md`'s device/
cloud/performance checklists for real; decide whether Ollama/Generic REST
need a scoped network-security-config cleartext exception based on real
beta usage, not speculatively; keep `OrphanedImageCleanupWorker`'s new
sweeps in mind if a future capability adds another kind of cache file —
extend that worker rather than inventing a second cleanup mechanism.

## 21. Beta 1 code-audit findings (2026-08-06)

**What**: Beta 1 is a real-device-usage milestone (daily use, AI-quality
sampling, UX polish from observed friction) that this environment cannot
perform — no device, no camera, no real cloud account. The code-only
portions that *can* be done here (a static bug audit and an AI-prompt
review) were completed; see `BETA_1_REPORT.md` for the full writeup and
the templates the user fills in with real usage.

**Confirmed bug found and fixed**: `AiJobManager.dispatch` generated a
fresh random `UUID` as the WorkManager unique-work name on every call,
even though the Gateway's own cache key was already available at the call
site. This meant `ExistingWorkPolicy.KEEP` — the mechanism that's supposed
to stop a duplicate in-flight request from firing twice — could never
actually trigger, since the "unique" name was different every time by
construction. Two concurrent calls for the identical cache key (a
double-tapped "Get Outfit Ideas" button, a recomposition re-triggering the
same request before the first reply lands) would each enqueue their own
WorkManager job and fire their own real cloud request — duplicate cost for
a paid API call, and a race on the same `ai_jobs` ledger row (no unique
constraint on `cacheKey`, so `markStatus`'s `getByCacheKey` could observe
either row non-deterministically, leaving the other stuck at
`RUNNING`/`PENDING` forever).

Fixed by keying `AiWorkRegistry` on the cache key itself rather than a
random id, with a new `registerIfAbsent` (atomic `putIfAbsent`) that lets a
second concurrent call detect it's joining an already-in-flight request and
just await that result instead of dispatching again — real request
coalescing, not just a naming change. Moved the registry-entry cleanup from
`AiJobManager.dispatch`'s caller-side `finally` into `AiCapabilityWorker`
itself (on true terminal completion — success or final failure after
retries), so a caller getting cancelled mid-await (its ViewModel scope
closing) can no longer rip the shared entry out from under a second caller
who joined the same request. Verified with a new regression test
(`AiJobManagerTest`'s "two concurrent dispatches for the identical cache
key coalesce into one call") proving the underlying block runs exactly
once for two concurrent dispatches sharing a cache key, both callers get
the same result, and the ledger row reaches `SUCCEEDED`.

**Real, disclosed limitation found and *not* fixed (correctly out of
scope for a single pass)**: `MetadataSuggestionResolver.kt`'s
reference-backed fields (`CATEGORY`/`BRAND`/`PRIMARY_COLOR`/`MATERIAL`)
only bind a suggestion to the user's real reference-data row on an exact
(case-insensitive) name match — by design, per that file's own KDoc
("never guessed into the nearest match," Constitution rule 4). But
`buildMetadataSystemPrompt` (`MetadataPromptSupport.kt`) never tells the
model what the user's actual reference-data vocabulary *is* — unlike
Styling's prompt, which explicitly lists the real candidate wardrobe items
by id. So the model has no way to know whether the user's color reference
table says "Navy" or "Navy Blue", and a plausible-but-non-matching value
(right in spirit, wrong string) silently fails to autofill — the
suggestion still surfaces with its confidence in the review screen, it's
just not bound to a form control, so the user has to pick it manually. This
is a real UX/AI-quality gap (a likely source of "incorrect metadata" or
"low autofill rate" friction), but fixing it properly means threading a
reference-data repository into `GarmentMetadataEngineRouter` — new wiring,
not a one-line prompt edit — so it's logged here as a scoped Beta 2
candidate rather than done speculatively before real usage confirms it's
actually the top friction source, per Beta 1's own stated philosophy
("only fix confirmed issues," "UX polish only where proven").

**What remains genuinely untestable in this environment**: unchanged from
items 19/20 — real device usage, a real cloud-provider account, and the
100/50/30-sample AI-quality measurement Beta 1 asks for. `BETA_1_REPORT.md`
has the templates; this pass could not fill them with real data.

**Risk assessment**: Low for the `AiJobManager` fix — contained to two
files plus their tests, verified by a new regression test, no behavior
change for the (overwhelmingly common) non-concurrent case. Zero risk from
the disclosed-not-fixed metadata item since nothing was changed.

**Upgrade checklist**: run the daily-usage/AI-quality/performance phases
for real and fill in `BETA_1_REPORT.md`; revisit the metadata
reference-vocabulary gap above once real usage shows whether it's actually
a meaningful fraction of "AI got it wrong" reports.

## 22. RC2 Production Hardening gaps (2026-08-06)

**What**: RC2 was a hardening-only, no-new-features milestone — a full
static/performance/AI-robustness/Android/security/documentation/dependency
audit of everything M1–Beta 1 built. Full detail across
`SECURITY_AUDIT_RC2.md`, `PERFORMANCE_AUDIT.md`, `CODE_HEALTH_REPORT.md`;
summarized here. **5 confirmed defects found** (4 new this pass, 1 carried
forward from Beta 1's own audit), **all fixed and regression-tested**:

1. `AiJobManager` duplicate concurrent dispatch (Beta 1's own fix, cited
   here since RC2's audit re-touched this exact mechanism).
2. `GenericRestAdapter` hung permanently on a malformed/truncated
   `resultImageBase64` value from a self-hosted backend — `Base64.decode`'s
   `IllegalArgumentException` was neither caught by the adapter nor by
   `AiCapabilityWorker`, so the calling coroutine's `CompletableDeferred`
   was never completed. Fixed via `runCatching` around the decode.
3. Beta 1's own dispatch-coalescing fix (item 1) had an uncaught side
   effect: `DefaultAiGateway` recorded a duplicate `ai_result_cache` write
   and a duplicate `AiMetrics` event for the "joined" caller of a
   coalesced request, double-counting cost/latency/success telemetry.
   Fixed by threading an `isOwner` flag out of `AiJobManager.dispatch`
   (now returns `Dispatched<T>`) so only the owning caller persists the
   one-time side effect.
4. **Security-critical**: `GeminiAdapter` sent the user's real Gemini API
   key as a `?key=` URL query parameter, and every `OkHttpClient` in
   `core:ai` runs an unconditional (not debug-gated) `HttpLoggingInterceptor`
   at `Level.BASIC`, which logs the full request URL. Every real Gemini
   call wrote the user's live API key to Logcat, in every build variant.
   Fixed by switching to Gemini's own documented `x-goog-api-key` header
   auth method — the secret no longer appears in the URL at all. Full
   writeup in `SECURITY_AUDIT_RC2.md`.
5. `OrphanedImageCleanupWorker`'s orphan sweep raced against
   `ImageRepositoryImpl.commitStagedImage`, which moves a garment's files
   into place *before* inserting their `image_metadata` rows — a real,
   if millisecond-scale, window where the daily cleanup sweep could
   misidentify a just-saved photo as an orphan and delete it. Fixed by
   giving `OrphanedImageDetector.findOrphans` a required age cutoff
   (60 minutes), mirroring the pattern the same file already used for
   stale staging directories.

**Confirmed, evidenced, deliberately not fixed** (documented, not hidden):
`CameraCaptureController.awaitCameraProvider()` has the same
uncaught-exception-hangs-a-continuation shape as defect 2, in
`ListenableFuture.get()`'s documented failure modes — not fixed because
this class has no unit-test harness in this environment (CameraX requires
a real device/emulator), and RC2's own rule requires every fix to carry a
regression test. `MlKitFaceBlurrer`/`MlKitPersonRegionMasker`/
`MlKitBackgroundRemover`'s `suspendCancellableCoroutine` calls don't cancel
the underlying ML Kit task on coroutine cancellation — low severity
(resuming an already-cancelled continuation is a documented no-op, not a
crash), and the only "fix" (closing the shared detector client) would
break it for other concurrent calls, a worse outcome. Both logged with
full rationale in `CODE_HEALTH_REPORT.md`.

**Documentation accuracy**: `README.md` and `PRODUCTION_VALIDATION_REPORT.md`
both claimed "zero suppressions project-wide" — false. A full-repo grep
found 23 `@Suppress`/`@SuppressLint` sites across 19 files, each reviewed
and individually justified (Compose parameter counts, Room DAO column
counts, an SDK-version-gated deprecated API, intentional broad-exception
boundaries, one unavoidable generic-erasure cast). Both documents
corrected. RC2 itself added zero new suppressions.

**Dependency audit**: no unused, duplicate, or obsolete dependency found
beyond what RC1 already removed. A live CVE/vulnerability-database check
against pinned versions was not performed — no network access in this
environment — and is disclosed as unchecked rather than claimed clean.

**What remains genuinely untestable in this environment**: unchanged from
items 19–21 — real device usage, a real cloud-provider account, real
performance measurement, and (new, from the disclosed-not-fixed items
above) any CameraX-dependent regression test.

**Risk assessment**: Low for all 5 fixes — each is contained to 1–3 files
plus its own regression test, none changes public API shape beyond
`AiJobManager.dispatch`'s return type (whose only production caller,
`DefaultAiGateway`, was updated in the same change). The Gemini key fix is
the highest-value fix of this milestone despite being "low risk" in
implementation size — it closes a real, live secret-exposure path.

**Upgrade checklist**: none of the above blocks a beta release; the
Gemini fix specifically should ship before any user actually configures a
Gemini API key for real. Before a *production* release: the same
real-device/real-cloud-account/performance gates from items 19–21 remain
outstanding; consider the disclosed `CameraCaptureController` fix once
instrumented testing is available; consider whether `HttpLoggingInterceptor`
should be release-gated for defense-in-depth (disclosed, not applied, in
`SECURITY_AUDIT_RC2.md`).

## 23. M15 User Profile and AI-First Home Dashboard gaps (2026-08-07)

**What**: Parts 4–5 of the AI Wardrobe Assistant roadmap — a real,
persistent identity Profile screen (name + avatar editing, real
validation) and a Home dashboard extension (Profile entry point, Recent
AI Activity, a Cloud AI configuration nudge), built on the existing
`PersonalizationSettings`/`PersonalizationDataStore` and `ai_call_log`
infrastructure rather than new persistence. Full rationale in
`docs/adr/ADR-014-m15-user-profile-and-ai-home.md`.

**Deliberate, disclosed scope decisions** (not gaps found by accident):

- Max display name length is a stated default (50 UTF-16 code units) —
  no prior precedent in this codebase to match, since no editable
  free-text identity field existed before this milestone.
- The avatar is a single fixed local file
  (`filesDir/profile/avatar.jpg`) — no crop UI, no history, no multiple
  photos. A re-pick simply overwrites the existing file.
- Profile's AI/Wardrobe/App sections intentionally show a compact
  real-data summary (e.g. "2 of 5 using Cloud AI", "<device> · last
  synced <time>") rather than the full underlying state — by design, so
  the AI Providers/Wardrobe Sync/Style Preferences screens remain the
  one authoritative place to actually change any of it (the brief's own
  non-negotiable #8).
- `feature:settings/README.md`'s `profile/` package description (written
  Phase 7) was stale — it described a *style*-preference screen that
  this milestone's own inspection found was already built elsewhere, as
  `feature:outfits/preferences/StylistPreferencesScreen.kt`, under a
  different name. Corrected alongside this change.
- There is still no navigation-dock tile or drawer entry for
  Profile/Settings generally — Home's new header avatar is currently the
  only discoverable path to it, consistent with the standing, pre-M15
  note (`feature:settings/README.md`) that a Settings hub is deferred
  until more of Phase 5f's originally-planned screens exist.

**What remains genuinely untestable in this environment**: real Photo
Picker behavior on an actual device (the automated test exercises the
same `copyAvatarImage`/`ContentResolver.openInputStream` code path via a
Robolectric-backed `file://` URI, which is a faithful but not identical
substitute for a real `content://` grant from the system Photo Picker
UI) — flagged per the Real-Device Validation Gate, not claimed verified.

## 24. M16 First-Run Onboarding gaps (2026-08-07)

**What**: Part 6 of the AI Wardrobe Assistant roadmap — Welcome, Name,
Style Preferences, AI/Privacy, Finish, backed entirely by M15's
`PersonalizationRepository` and Phase 6's `StylistPreferencesRepository`
(no new profile/preferences storage), a new reactive first-run gate
(`OnboardingRepository`), and a new `feature:onboarding` module. Full
rationale in `docs/adr/ADR-015-m16-onboarding.md`.

**Genuine pre-existing dead code discovered (not introduced this
milestone)**: `StyleProfileRepository`/`StyleProfileRepositoryImpl`/
`StyleProfileDataStore` (`core:domain`/`core:data`/`core:datastore`,
Phase 3/5a) are fully implemented and Hilt-bound but have **zero
production callers** — no screen anywhere in the app reads or writes
`StyleProfile` (occupation, gender preference, budget, preferred brands,
avoided categories). This was confirmed by grep before deciding what
Onboarding's Style step should show, per the brief's "only expose
preferences with a real downstream effect" instruction — `StyleProfile`
was excluded from Onboarding entirely for exactly this reason. Not fixed
by this milestone (out of scope — M16 is additive, not a cleanup pass);
flagged here so a future milestone doesn't have to rediscover it. Options
for that future milestone: build the Phase 5f profile screen
`feature:settings/README.md` originally described for it, or remove the
dead code if no such screen is ever planned.

**Deliberate, disclosed scope decisions** (not gaps found by accident):

- Onboarding's Style step surfaces only 3 of `RecommendationPreferences`'s
  ~15 fields (`preferredDressCodes`, `preferFavorites`,
  `preferComfortableFit`) — confirmed by grep to be the ones
  `RecommendationRuleEngine` genuinely reads. The rest keep their existing
  defaults; the full set remains editable later via the standalone
  Stylist Preferences screen.
- The AI/Privacy step has no local "skip" distinct from "Continue with
  on-device AI" — on-device already *is* the skip-equivalent outcome
  (every capability's real default), so a second button for the same
  effect would be redundant UI, not a missing feature.
- First-run detection (`OnboardingRepositoryImpl.observeIsComplete`) is
  intentionally a live `Flow` derivation, never a one-time migration
  write — see ADR-015 §3 for why this is the safer choice for exactly the
  "don't lose or misclassify an existing user" guarantee this milestone
  requires.

**What remains genuinely untestable in this environment**: real navigation
timing on an actual device for the first-launch blank-frame gate (the
automated tests exercise `OnboardingGateViewModel`'s state derivation and
`WardrobeNavHost`'s conditional `startDestination` logic directly, but not
a real Activity launch's perceived latency) — flagged per the Real-Device
Validation Gate, not claimed verified.

## 25. M17 Closet Filters gaps (2026-08-08)

**What**: Part 7 of the AI Wardrobe Assistant roadmap — real multi-select
filtering (Category, Color, Brand, Material, Fabric, Season, Dress Code,
Occasion, Tag, Fit, Gender, Waterproofing, Favorites/Never Worn/Recently
Worn/Price), a real result count, real derived wardrobe insights, and
DressCode-based smart presets. Full rationale in
`docs/adr/ADR-016-m17-closet-filters.md`.

**Genuine, disclosed limitation**: `currentSeason(LocalDate)`
(`ClosetInsight.kt`) uses fixed Northern-Hemisphere meteorological month
boundaries to compute the "N {season} items" insight — the app has no
location signal available for this purpose (Weather's own forecast lookup
is a separate, opt-in concern with its own location input, not reused here
since insights must work with zero configuration). A Southern-Hemisphere
user will see this one insight's season label six months out of phase with
their actual season. Every other insight (favorites, work-ready) and every
filter facet is unaffected. Deferred rather than fixed in this milestone —
would require either a real device-location signal or a user-set hemisphere
preference, neither of which exists yet anywhere in the app.

**Deliberate scope decisions** (not gaps, recorded so they aren't
rediscovered later):

- Smart presets translate to `DressCode` sets only, never to `Occasion`-name
  matching — `Occasion` is user-extensible free text with no guaranteed
  rows, so an Occasion-based preset could silently return nothing for a
  user who never created a matching occasion. See ADR-016 §7 for the full
  reasoning.
- Closet's filtering moved fully in-memory (search remains the only
  SQL-pushed facet) rather than extending `GarmentDao`'s `WHERE` clause with
  `IN (...)` support for eleven facets — a deliberate architecture choice
  reusing the same "hundreds not millions of garments" scale assumption
  `GarmentFilter`'s own pre-M17 doc comment already relied on for its
  in-memory half. See ADR-016 §1.

**Regression check performed**: existing Closet browsing, Add Garment →
Closet, favorite toggling, garment deletion, navigation into Garment
Detail, and AI metadata field visibility were all re-verified via the full
`feature:closet` test suite and manual review of unchanged code paths — none
of M17's changes touch `GarmentRepository`, `GarmentDao`, `GarmentFilter`,
Add Garment, or Recommendations.

## 26. M18 App-Wide "AI is Alive" Experience gaps (2026-08-08)

**What**: Part 8 of the AI Wardrobe Assistant roadmap — a real "is AI
currently doing something" signal (`AiProviderSettingsRepository.observeActiveOperations()`,
backed by the already-existing `AiJobManager`/`ai_jobs` ledger), a reusable
`AiActivityBanner` (`core:ui`), Home's Recent AI Activity made live instead
of a one-shot snapshot, a direct Home → AI Providers nudge, and honest
rule-based-vs-AI-styled labeling on Recommendations. Full rationale in
`docs/adr/ADR-017-m18-ai-alive-experience.md`.

**Genuine, disclosed limitation**: `AiActiveOperation` (the new "currently
running" model) carries no `provider`/`model`/`confidence` field, unlike
`AiResultProvenance`. This is not an oversight — a job that is still
`PENDING`/`RUNNING` genuinely has not resolved a provider or produced a
confidence value yet, so there is nothing honest to show. The consequence:
`AiActivityBanner`'s running-state copy on Home can only ever say "Analyzing
garment…" (per-capability), never "Analyzing with GPT-4o…" — a provider name
only becomes knowable once the call completes, at which point it belongs to
`AiActivityEntry`/`AiResultProvenance` instead. Not deferred, not fixable
without fabricating a value — a structural property of when the information
actually becomes available.

**Deliberate scope decisions** (not gaps, recorded so they aren't
rediscovered later):

- Try-On's main render screen intentionally shows no AI-status UI — it
  dispatches no `AiCapability` at all (on-device placement-template
  rendering only); the real AI Try-On generation path (Compare-with-Cloud)
  already has full source/confidence transparency. Adding a status card to
  the main screen would have implied AI ran something it didn't.
- Stylist Preferences was left without its own AI-configuration entry
  point — Recommendations' existing direct "AI" nav button already reaches
  AI Providers in one tap from the screen users actually configure styling
  from; a second entry point on the preferences-toggle screen would be
  near-duplicate UI, not a genuine gap.
- Add-to-Wardrobe/Review's progress and post-analysis summary UI (M14) was
  left unchanged — inspection confirmed it already satisfies Phase 6 in
  full (real stage-name indeterminate progress, real provider/confidence/
  cache summary, real "what changed" bullets).

**Regression check performed**: `AiProviderSettingsRepositoryImpl`'s
constructor gained a dependency; every direct test-construction call site
(the module's own `AiProviderSettingsRepositoryImplTest`) and both existing
`FakeAiProviderSettingsRepository` implementations were updated, not
weakened — verified via the full `core:data`, `feature:closet`,
`feature:settings`, and `feature:outfits` test suites, none of which lost
a test.

## 27. M19 Recommendations Rebuild gaps (2026-08-08)

**What**: Part 9 of the AI Wardrobe Assistant roadmap — real "Show
another" via `OutfitAssembler`'s existing multi-candidate assembly (no
second engine), a genuinely-recomputing occasion selector
(`SuggestionContext.occasionId`, declared since M9, now actually read by
`ContextResolution.resolvePlannedOccasionDressCode`), an honest
recommendation-fetch error state, a Try On action wired into the existing
flow, and UI polish (bulleted "Why this?", Best Match/Alternative
labeling, a live Cloud AI activity banner reusing M18's signal). Full
rationale in `docs/adr/ADR-018-m19-recommendations-rebuild.md`.

**Genuine, disclosed limitation**: "Show another" grows its requested
candidate count in fixed steps of 3, capped at 9. A caller who wants a
10th genuinely distinct outfit for a context whose wardrobe could support
one will not get it from this screen — `requestedCount` simply stops
growing at the cap. This mirrors the same honest-clamping behavior
`OutfitAssembler.pickNth` already has (requesting past the pool's size
repeats the worst-ranked candidate, never fabricates a new one); the cap
exists to bound cloud-dispatch/query cost per session, not because a
larger count is unsupported. Not fixed this pass — a config-driven or
uncapped variant is reasonable future work if real usage shows the cap
binds in practice.

**Deliberate scope decisions** (not gaps, recorded so they aren't
rediscovered later):

- `CloudOutfitValidation`, `RecommendationExplainer`, and
  `RecommendationRuleEngine`'s scoring factors were inspected and found
  already correct — cloud outfits already could not bypass whole-outfit
  validation, and explanation text was already real and non-generic. No
  code changed in any of the three.
- `suggestForItem` was left without a `count` parameter — inspection
  confirmed it has zero production callers, so there is nothing that would
  use one.
- Parts 10–13 (Calendar, Insights, app-wide visual polish, and the rest of
  the wider epic) are untouched, per the milestone's own explicit scope.

**Regression check performed**: `StylingEngineRepository.suggestOutfits`
gained a defaulted `count` parameter — every implementation
(`StylingEngineRepositoryImpl`, `StylingEngineRouter`) and both
`FakeStylingEngineRepository` test doubles (`feature:outfits`,
`feature:closet`) were updated, not weakened. `RecommendedOutfitUiModel`
gained a required `reasonBullets` field — the one other direct-construction
test site (`AiStyledBadgeTest`) was updated accordingly. Verified via the
full `core:domain`, `core:data`, and `feature:outfits` test suites, plus
project-wide `detekt`/`ktlintCheck`, none of which lost or weakened a
test.

## 28. M20 Calendar & Outfit Planning gaps (2026-08-10)

**What**: Part 10 of the AI Wardrobe Assistant roadmap. Inspection found
`feature:calendar` was already a complete, shipping feature (month grid,
day detail, log/plan/reschedule/duplicate/recurring wear, Phase 9
conflict detection) built on the already-sync-registered `WearEvent`
model — not the greenfield "build a calendar" the brief's own framing
implied. M20's real scope: wiring the M19 recommendation engine into Day
Detail (recommend/show-another/plan/replace, reusing the exact engine and
dedup discipline), a real occasion-assignment picker (writing the
long-existing-but-unwritten `WearEvent.occasionId`), honest three-state
weather-for-a-date, and a wear-history "worn recently" signal. Full
rationale in `docs/adr/ADR-019-m20-calendar-and-outfit-planning.md`.

**Genuine, disclosed limitation**: "Show another" for a calendar date
caps its requested candidate count at 9 (steps of 3), mirroring M19's
identical, already-accepted limit on the Recommendations screen — a
caller wanting a 10th genuinely distinct outfit for a wardrobe that could
support one won't get it from this screen either. Not fixed this pass,
same rationale as M19's entry above (bounds cloud-dispatch/query cost per
session).

**Deliberate scope decisions** (not gaps, recorded so they aren't
rediscovered later):

- No database migration — every field M20 needed (`WearEvent.occasionId`,
  `weatherCacheId`) already existed on the entity/model/sync-wire format;
  the Calendar UI simply never wrote them before now. DB stays at
  version 9.
- No new sync code — `WearEvent` was already fully sync-registered with
  `occasionSyncId` already part of `WearEventWire` before this milestone.
- Notifications/reminders were deliberately not built — no
  `NotificationManager`/`NotificationChannel` infrastructure exists
  anywhere in the app, and every existing `WorkManager` worker is
  internal/maintenance (sync, weather refresh, backup, image processing,
  orphaned-file cleanup), none user-facing. Per this milestone's own
  instruction to leave notifications out when no precedent exists.
- Home's "Coming up" insight chip (`HomeInsightsUiModel.upcomingOutfitLabel`,
  M15/M16) already surfaces a real upcoming planned outfit from
  `WearEvent` data — a second, dedicated "upcoming plan" card was not
  added to avoid duplicating an already-honest, already-real surface.
- The recommendation-provenance label shown in Calendar's Day Detail
  duplicates a few lines of `feature:outfits`' "AI Styled"/"Styled from
  your wardrobe preferences" text rather than sharing a component —
  `feature:calendar` cannot depend on `feature:outfits` (ADR-010's
  module-layering rule), and the duplication is presentation text, not
  architecture (no shared logic, model, or repository is duplicated).

**Regression check performed**: `CalendarViewModel`'s constructor gained
4 dependencies (`StylingEngineRepository`, `WeatherRepository`,
`StatsRepository`, `Clock`) — every existing test updated to construct it
with fakes for all of them (not weakened), plus 3 new fakes added
mirroring `feature:outfits`' own M19 fakes exactly. `ContextResolution.prependPlannedOutfit`
gained a required `today` parameter — its one call site
(`StylingEngineRepositoryImpl.suggestOutfits`) was updated, and its
existing "planned outfit surfaced first" test was left passing unchanged
(the date/today match in that test), with a new test added proving the
non-today case. Verified via the full `core:data` and `feature:calendar`
test suites, plus project-wide `detekt`/`ktlintCheck`, none of which lost
or weakened a test.

## 29. M21 Insights Dashboard gaps (2026-08-08)

**What**: Parts 11–12 of the AI Wardrobe Assistant roadmap. Inspection
found `feature:stats` was already a complete, shipping Insights Dashboard
(Phase 5e/9) — wardrobe overview, wear activity, underused-wardrobe list,
category/dresscode/weekday distribution, favorites, cost/value insights,
~12 tap-through actionable lists, Wardrobe Story, Wardrobe Health — not
the greenfield "build a dashboard" the brief's own framing implied. M21's
real scope: material/fabric/occasion "wardrobe mix" distribution
(genuinely absent), tablet-adaptive layout (genuinely absent), a
documented decision not to add a new AI capability, and a real bug hunt.
Full rationale in `docs/adr/ADR-020-m21-insights-dashboard.md`.

**Real bugs found and fixed** (not speculative — each traced to its
responsible code before being called a bug):

- `observeActiveGarmentCountBySeason`/`ByDressCode` counted an archived
  garment's season/dress-code tag toward *active* coverage
  (`COUNT(DISTINCT gs.garmentId)` instead of `COUNT(DISTINCT g.id)` inside
  a compound-condition `LEFT JOIN`), capable of silently hiding a genuine
  `ClosetGap`. Fixed; two regression tests added.
- `InsightsViewModel`'s "Waiting to Be Worn" list hardcoded
  `StatsWindow.ALL_TIME` regardless of the period selector, unlike every
  other window-scoped section. Fixed to respect the selected window;
  regression test added.

**Deliberate scope decisions** (not gaps, recorded so they aren't
rediscovered later):

- Most Worn / Least Worn / Best Value / Highest Cost Per Wear remain
  lifetime-only metrics, not made window-scoped — `observeCostPerWear()`
  has no window parameter anywhere in its DAO query, and adding one would
  be a materially larger, riskier change touching M19/M20 code that
  already consumes it, for a requirement the brief didn't actually make
  (Part 2 requires *consistency*, not that every statistic become
  period-dependent). Each affected list's subtitle now discloses
  "...all time." instead.
- No new AI capability added for "AI Insights" (Part 10) — `AiCapability`
  has no existing entry for wardrobe-summary generation, and adding one
  would mean a new consent surface, provider-routing path, and provenance
  contract for a dashboard whose real value is already its plain derived
  numbers. This dashboard stays entirely deterministic; no "AI-generated"
  badge exists because nothing here is AI-generated.
- Tablet/large-screen layout (`BoxWithConstraints`/`WIDE_LAYOUT_MIN_DP`,
  mirroring the exact idiom `feature:calendar`/`feature:closet`/
  `feature:outfits` already use) is code-verified only, not exercised on
  physical tablet hardware this pass.
- `StatsRepository` gained two new methods (`observeWardrobeMixDistribution`,
  `observeWardrobeUsageGaps`) but two older ones were folded into the
  second (`observeNeverWornOutfitIds`, `observeGarmentsMissingOutfits` —
  both used nowhere outside `feature:stats`), keeping the interface under
  detekt's `TooManyFunctions` threshold without an unprecedented
  suppression — the same "fold into the model" choice `UsageStats`' own
  Phase 9 additions already made.

**Regression check performed**: every existing `StatsRepository` consumer
(`feature:calendar`, `feature:closet`'s fakes; `core:data`'s
`StatsRepositoryImplTest`; `feature:stats`'s own tests) was updated to the
consolidated method signatures, not weakened — verified via the full
`core:data`, `feature:stats`, `feature:calendar`, and `feature:closet`
test suites, plus `detekt`/`ktlintCheck` across all touched modules, none
of which lost or weakened a test.

## 30. M22 App-Wide Visual Polish, Accessibility & Production Hardening gaps (2026-08-08)

**What**: A whole-app closing pass, not a new feature milestone. Six
parallel research passes (docs/ADRs, the `core:designsystem`/`core:ui`
component inventory, Home/Profile/Onboarding/navigation, Closet/Add-
Garment, Recommendations/Calendar/Insights, accessibility/security/
performance) found the app already close to production-ready — real
component reuse, correct filter semantics, no fabricated AI/data. Real
scope: 14 genuine bugs/gaps, none of them net-new features. Full
rationale in `docs/adr/ADR-021-m22-visual-accessibility-production-hardening.md`.

**Real bugs found and fixed** (not speculative):

- `HomeViewModel.assistantState` had zero error handling — an uncaught
  exception anywhere in `loadAssistantState()` or its three live
  `collect` loops could crash the app. Fixed with try/catch (correctly
  re-throwing `CancellationException`) and `.catch { }` on each flow.
- `AttentionItemsCard`'s count was non-nullable, so "0 items, healthy"
  and "not loaded yet" were indistinguishable — made nullable, matching
  `wardrobeHealthScore`'s existing correct pattern.
- `showWardrobeHealthCard` only gated `WardrobeSummaryCard`, not the
  similarly-named `WardrobeHealthScoreCard` — now gates both.
- Import queue's retry button used the Close icon glyph (reads as
  "dismiss") instead of Refresh.
- A lost staged image (`needsRestage`) silently closed the review screen
  with no explanation — now shows a brief notice before returning to
  the queue.
- `ClosetGridSkeleton` was built specifically for `ClosetScreen` but
  never wired in; the screen showed a generic spinner instead. Now used.
- AI-suggested vs. user-entered values were indistinguishable once bound
  into Add Garment's actual editable fields (only the separate summary
  list above showed the distinction). `DropdownField` gained an
  additive `helperText` param; Garment Review now shows "AI suggested"
  under a field whose current value matches a real suggestion, reusing
  the existing `isSuggestionApplied` check.
- Recommendations' empty state used one message for two different
  causes (a genuinely empty wardrobe vs. an insufficient one) — now
  distinguished via a new `hasNoGarments` flag.
- Calendar's top-level `uiState` combine had no error boundary, unlike
  Recommendations' own `isError` state — a repository failure left the
  screen stuck loading forever. Fixed with the same `.catch { }`
  pattern.
- `WardrobeFilterChip`'s selected state was color/fill-only for sighted
  users (screen readers already had the real signal) — added a
  checkmark icon, which reaches every screen using this component or
  `MultiSelectChips` without touching those call sites.
- 11 call sites across 8 files used lifecycle-unaware `collectAsState()`
  instead of this project's own established
  `collectAsStateWithLifecycle()` convention (61 correct sites already
  existed) — all 11 fixed.
- `HttpLoggingInterceptor` ran unconditionally in every build variant,
  including release — a defense-in-depth gap explicitly disclosed as
  "not applied" in this file's own RC2 entry. Both instances now check
  `ApplicationInfo.FLAG_DEBUGGABLE` and only attach in debug builds.
- Try-On render decoded two full-resolution bitmaps and ran the
  on-device compositing engine on the Main dispatcher (no dispatcher
  switch anywhere in the call chain). Wrapped in
  `withContext(Dispatchers.IO)`, matching this layer's own convention.
- (Found during this milestone's own verification, not shipped) the new
  `WardrobeFilterChipTest` needed `useUnmergedTree = true` — the chip's
  `clickable` modifier correctly merges descendant semantics into one
  screen-reader node, so the decorative checkmark icon isn't queryable
  in the default merged tree. Fixed in the test.

**Investigated, confirmed not bugs** (recorded so they aren't
rediscovered): `AiActivityBanner`'s 2-of-9 screen adoption (the other
screens' plain spinners are a different, equally-correct "first load"
pattern, not a gap); Insights' bar-chart-card empty-guard inconsistency
(Season/Dress-Code/Weekday distributions always have real fixed-enum
data, unlike variable-length Material/Fabric/Occasion lists — a real
zero-height bar is honest, not a bug); Calendar's Planned/Worn using
different non-color techniques on the month grid (shape) vs. Day Detail
(text) — both already satisfy "not color-only," just surface-appropriate.

**Deliberately deferred** (disclosed, not silently dropped): no shared
`Spacing`/`Dimens` token object (340+ hardcoded `.dp` literals across 73
files — a real DRY gap, but a large mechanical refactor disproportionate
to this pass); no shared `Dialog`/`BottomSheet`/`TopAppBar` wrapper (104
raw Material3 usages across 41 files, functionally consistent already,
introducing one risks being a new visual system); Garment save's
full-screen overlay vs. Outfit save's toast left unaffected (both
deliberate, documented, working); cache-hit disclosure format difference
between Recommendations (dialog) and Calendar's Day Recommendation sheet
(inline) left as-is (different information-density contexts); 7
`PersonalizationRepository` setters with no UI (`setGreetingStyle` and
6 others) — real scaffolding, no screen surfaces them, building one is a
new feature not polish; `WeatherSettingsScreen`'s two text-glyph
IconButtons (`"−"`/`"+"`) not swapped to icons (low impact); RTL,
TalkBack, and physical tablet/multi-width rendering cannot be verified
in this development environment.

**Regression check performed**: full test suites for every touched
module (`core:ui`, `core:ai`, `core:data`, `feature:closet`,
`feature:capture`, `feature:outfits`, `feature:calendar`,
`feature:settings`, `feature:onboarding`, `feature:tryon`) pass; nothing
deleted, disabled, or weakened.

## 31. M23 AI Metadata Auto-Fill Transparency fix (2026-08-08)

**What**: Real-device evidence (a physical tablet) showed Add Garment's AI
review auto-filling only Primary/Secondary Color and Pattern, with 15
other fields stuck on "Unknown — Please choose" despite green tests and a
green `clean build`. Full pipeline audit (photo → `OnDeviceMetadataEngine`
→ `MetadataSuggestionResolver` → `GarmentReviewMetadataViewModel` →
Compose fields) found every layer downstream of the on-device engine
already correct and tested. Full rationale in
`docs/adr/ADR-022-m23-ai-metadata-autofill-transparency.md`.

**Root cause (proven, not the 6 other hypotheses M23 named)**:
`OnDeviceMetadataEngine` has always only produced suggestions for
`PRIMARY_COLOR`/`SECONDARY_COLOR`/`PATTERN`/`BRAND` — a real, documented,
bounded ML capability limit (k-means color clustering, a luminance-variance
heuristic, and OCR; no on-device category/material/fabric classifier
exists in this codebase, confirmed by a repo-wide dependency search finding
no ML Kit image-labeling or similar dependency). The real gap was that this
boundary lived only as KDoc prose — nothing downstream could distinguish
"this provider structurally can't detect this field" from "it can, but
didn't this time," so both rendered as the same "Unknown — Please choose."
A HIGH-confidence suggestion that failed reference-data resolution was
also invisible as a distinct state.

**Fixed**:

- Added `MetadataFieldSupport` (`core:model/ai`) — the first declared,
  testable per-`AiResultSource` field-capability contract, matching what
  `OnDeviceMetadataEngine` actually emits (true by construction, not just
  by convention).
- Added `MissingFieldReason` (`NOT_APPLICABLE`/`NOT_SUPPORTED`/
  `NOT_DETECTED`) so the review screen's "missing field" row now
  distinguishes all three, instead of collapsing the latter two.
- `MissingFieldRow` gained a genuinely new visual state — "Not supported
  by On-Device AI — Enable Cloud AI in Settings for full detection" — an
  informational, not warning, row.
- `SuggestionRow` now flags a HIGH-confidence suggestion that failed
  reference-data resolution ("Detected, but no matching option found —
  choose manually") instead of silently rendering it as an unselected
  chip indistinguishable from a low-confidence one.
- Added `MetadataPipelineDiagnostics` — a pure, unit-tested formatter
  reused by a new debug-build-only (`ApplicationInfo.FLAG_DEBUGGABLE`,
  same gate as M22's network-logging fix) logcat dump answering "what did
  the model actually return for this photo" per field: requested,
  supported, returned value, confidence, tier, resolved — without logging
  the image, an API key, or personal data.
- No change to `MetadataSuggestionResolver`, `AutoSaveEligibility`,
  `GarmentMetadataEngineRouter`, or `MetadataPromptSupport` — all four
  were already correct; auto-save's HIGH-or-N/A gate is untouched.

**Disclosed consequence**: on-device-only users still see most fields as
genuinely unsupported after this fix — that's now stated honestly instead
of silently implied. Actually detecting Category/Material/Fabric/etc.
on-device would require a new on-device classifier (a real dependency and
product decision, not attempted here — M23 explicitly forbade fabricating
a capability that doesn't exist) or the user enabling Cloud AI, which this
fix now points at directly from the row that needs it.

**Deliberately deferred**: a direct in-app navigation link from the "Not
supported" row to the AI Provider settings screen (informational copy only
this pass — wiring a real nav callback is a small, separate follow-up); a
runtime assertion that `OnDeviceMetadataEngine`'s output never exceeds its
declared supported-field set (redundant — already guaranteed by the
function's own hardcoded structure, not something a test would newly
prove); `OnDeviceMetadataEngineTest` still has no `Bitmap`/ML-Kit-backed
tests (only pure `patternConfidence` math), a pre-existing gap this
milestone's scope didn't require closing.

**Regression check performed**: `core:model`, `core:image`, and
`feature:capture` test suites pass; nothing deleted, disabled, or
weakened; `AutoSaveEligibility`'s required-field set and thresholds are
byte-for-byte unchanged.

## 32. M24 Cloud AI Full Garment Metadata Auto-Fill (2026-08-08)

**What**: M23 proved on-device AI genuinely only supports Color/Pattern/
Brand; M24 audited whether the existing Cloud AI path (which claims all
19 `MetadataField`s) actually works end-to-end. Full rationale in
`docs/adr/ADR-023-m24-cloud-ai-metadata-autofill.md`.

**Audit finding**: the cloud pipeline was already substantially real and
correct — dispatch, all 6 vendor adapters (OpenAI, Azure OpenAI, Gemini,
Claude, OpenRouter, Ollama), JSON parsing, genuine per-field confidence
(not a global-average fabrication), consent-scoped gating, cache
provenance, and honest fail-soft fallback were all already implemented
and tested. Three real, narrower gaps were fixed, not a rewrite:

1. No provider-native structured-output enforcement — every adapter
   relied on prompt compliance alone. Fixed: OpenAI-compatible vendors
   (`response_format: json_object`) and Gemini (`responseMimeType:
   application/json`) now use their real, documented JSON-mode
   parameters. Claude has no such mode — stays prompt-only, disclosed.
2. Reference-data matching was exact-string-only (case-insensitive, no
   more). Fixed: deterministic whitespace/hyphen stripping in
   `nameMatches` — `"T-Shirt"`/`"T Shirt"`/`"TShirt"` all resolve
   identically; a genuinely different value (`"Navy Blue"` vs `"Navy"`)
   still never matches — no alias table, no fuzzy semantic guessing.
3. A cloud dispatch failure's reason was silently discarded at the
   router. Fixed: debug-only diagnostics (gated the same way as M22/M23)
   now log provider/model/capability/cache-hit/requested-vs-returned
   fields/failure-reason under the `MetadataPipeline` tag, extending
   M23's per-field ViewModel-side diagnostics with the request-level half.

**Deliberately deferred**: a user-facing (non-debug) "Cloud AI failed,
using on-device" message — the current fallback is never dishonest (never
claims cloud succeeded), just less informative than ideal; surfacing the
reason to end users would require an interface-breaking change across
`GarmentMetadataEngine`/the router/the image pipeline/the review UI,
disproportionate to this pass versus the debug diagnostics that already
answer the same question for real-device debugging. `CloudStylingEngine`
(also JSON-expecting) wasn't given the same `expectJsonResponse` treatment
— near-identical opportunity, out of M24's Garment-Metadata-specific
scope. Claude structured output has no fix without adopting tool-forcing,
a materially different request shape — left prompt-only.

**Real-device verification (Phase 12) — performed once a tablet was
connected; found 3 more real bugs no automated test caught**:

1. Settings had no default Base URL and no validation gating consent —
   confirmed live by pulling the app's DataStore file directly off the
   device (`adb shell run-as ... cat files/datastore/...`), which showed
   `mode=CLOUD`/`vendor=GEMINI` but a genuinely blank `base_url`, so cloud
   silently stayed unreachable forever with zero user feedback. Fixed:
   `AiVendor.defaultBaseUrl()` pre-fills known vendors' real endpoints;
   the consent button is disabled with an explanatory label until
   Vendor + Base URL are both present.
2. Google's live API rejected Gemini's RC2 header-based auth
   (`x-goog-api-key`) with a `404` on `:generateContent` for the real
   key/project tested, while `?key=` query-param auth was accepted
   (confirmed independently of the app via a raw HTTP call). Fixed with
   `GeminiQueryParamAuthInterceptor`.
3. **Self-inflicted during the above fix**: the interceptor was first
   registered as an `addInterceptor` (application-level) call ordered
   after `HttpLoggingInterceptor`, on the theory that ordering alone kept
   the key out of its log lines. That theory was wrong — the real device
   showed the key leaking into `HttpLoggingInterceptor`'s response-side
   log line. The user's real API key was exposed as a result; they were
   told immediately to rotate it, and the exposed value was scrubbed from
   local logs. Corrected by registering the interceptor via
   `addNetworkInterceptor` instead (structurally invisible to any
   application interceptor, not just conveniently ordered around one),
   and by rewriting the regression test to use the *real*
   `HttpLoggingInterceptor` class rather than a hand-rolled stand-in that
   had only checked the request-side URL and missed the leak entirely.

After all three fixes, the full cloud dispatch path was confirmed working
end-to-end on physical hardware — consent gating, corrected auth, a real
garment-photo request reaching Google, and diagnostics correctly reporting
every step. The final wall hit was `HTTP 429` /
`"limit: 0"` for `generate_content_free_tier_requests` on
`gemini-2.0-flash`, confirmed via the actual JSON error body (not just the
status code) to be the tested Google account's billing/plan setting —
reproduced identically across multiple freshly-rotated keys from separate
Google accounts, the signature of a plan-level `0` limit rather than a
transient rate limit. Resolving it requires enabling billing on the
Google Cloud project tied to the key, outside this app's code.

**Regression check performed**: `core:model`, `core:ai`, `core:data`,
`feature:capture`, and `feature:settings` test suites pass; nothing
deleted, disabled, or weakened; `MetadataSuggestionApply`'s field-mapping
logic, `AutoSaveEligibility`, `FieldApplicability`, and
`MetadataFieldSupport` are all byte-for-byte unchanged.
