# Technical Debt Register

Every entry here was incurred deliberately while getting `./gradlew build` to actually
pass in Phase 2 (see `BUILD_VERIFICATION.md` for the blow-by-blow) — none are oversights.
This file exists so none of them are silently forgotten. Update it whenever an item is
resolved or a new one is knowingly taken on; don't let it go stale.

## Permanent Product Principles (binding constraint checklist, added 2026-08-03)

Per Constitution rule 13 / [ADR-011](docs/adr/ADR-011-permanent-privacy-first-principles.md):
this is a privacy-first, offline-first personal wardrobe operating system,
not just "an AI wardrobe app." Every future debt item added below must be
checked against this list before being accepted as a reasonable tradeoff —
an item that violates one of these is not debt to track, it's a design that
must change:

1. User wardrobe photos never leave the user's own devices.
2. Personal photos never leave the user's own devices.
3. Outfit generation must work completely offline.
4. Recommendations must work completely offline.
5. No OpenAI, Gemini, Claude, or any cloud LLM integration.
6. No cloud storage of wardrobe data.
7. No user accounts are required.
8. Multi-device sync remains local-network encrypted only.
9. Internet is only permitted for optional contextual data (weather,
   holidays, etc.) that refines but never gates a recommendation.
10. Any future machine learning must execute locally on-device.

As of Phase 10, a scan of every item below confirms none violates this
list — the only network call anywhere in the app is the optional Open-Meteo
weather fetch (item 9), sync is local-network-only (item 8, Phase 8), body
reference photos/garment masks ride that same local-network-only channel
(item 1/2/8, Phase 10), ML Kit Pose Detection runs entirely on-device
(item 10, Phase 10), and no item involves an account, cloud storage, or a
remote model call.

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
  attribute (purchase date, fit, length, sleeve length, warmth/breathability,
  full palette/material-composition editing have no UI yet).
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
