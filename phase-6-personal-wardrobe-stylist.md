# Phase 6 — Personal Wardrobe Stylist

The offline recommendation engine: complete-outfit generation, natural-
language explanations, Stylist Preferences, a 2D layered Outfit Preview, and
the Quick Actions that turn a suggestion into a logged, scheduled, or saved
outfit. Everything here works fully offline — no weather API, no cloud AI,
no shopping/wishlist/trips integration beyond reading existing trip packing
lists to keep away-on-a-trip items out of rotation.

**Naming note**: this phase was originally requested as "Phase 5e (Personal
Wardrobe Stylist)," but Phase 5e was already shipped under a different name
(Wardrobe Intelligence — Insights/Story/Health). Its own brief explicitly
listed the AI Styling Engine as out of scope, and this project's own master
prompt (`alta-class-closet-app-master-prompt.md`, Section 3) already reserves
this exact scope as "Phase 6 — Styling engine." Renumbered here after
confirming with the user; see `alta-class-closet-app-master-prompt.md`
Constitution rule 11 for the "Wardrobe Completeness Rule" the user added
permanently while approving this phase.

## Architecture

```
core:model     RecommendationPreferences, AccessoryCategory, OutfitSlot.classify()
core:database  MIGRATION_2_3 (garments.isInLaundry), default category seed
core:domain    StylingEngineRepository (impl'd), StylistPreferencesRepository,
               ColorHarmony (promoted from feature:outfits)
core:data      StylingEngineRepositoryImpl + the styling/ rule-engine package
core:datastore StylistPreferencesDataStore
core:ui        RecommendationDiagnostics (Developer Panel window)
feature:outfits recommendations/ preferences/ preview/ — all new UI
```

`StylingEngineRepository` and `SuggestionContext`/`ScoredOutfit` already
existed as an unimplemented contract since Phase 3/5a specifically so
`feature:outfits`/`feature:closet` could be built against a stable interface
before this phase landed — this phase is the first and only implementation.
Likewise `StyleRule`/`Feedback` (entities, DAOs, `StyleRuleRepository`) were
wired end-to-end since Phase 3 with zero callers; this phase is the first to
actually read `StyleRuleRepository.observeActiveRules()`.

`Outfit.isSaved`'s own KDoc already anticipated this phase: "separates a
deliberately-kept look from an ephemeral AI-suggested-but-not-saved one
(Phase 6)." Recommendations are therefore real `Outfit` domain objects
(`source = AI_SUGGESTED`, `isSaved = false`, `id = OutfitId(0)` — the same
"unsaved" sentinel Outfit Builder and Saved Looks already use) rather than a
parallel `RecommendedOutfit` type. A Quick Action that commits to a
suggestion (Wear Today, Schedule, Save, Favorite) calls
`OutfitRepository.saveOutfit(...)` exactly once, on demand — recommendations
that are only browsed and discarded never touch the database.

## Item taxonomy: a real gap, closed by seeding, not by inventing an enum

`Category` is (and remains) a free-form, user-editable TOP/SUB tree with no
seed data before this phase — confirmed by reading `WardrobeDatabase`'s
`SeedCallback`, which only ever seeded `DEFAULT_OCCASIONS`. None of the
twenty item types the brief lists (Tops, Bottoms, Dresses, Jumpsuits,
Outerwear, Shoes/Sandals/Boots/Sneakers, Bags, Watches/Earrings/Necklaces/
Bracelets/Rings, Belts, Scarves, Hair Accessories, Sunglasses, Other
Accessories) existed as first-class rows anywhere.

Two decisions, made deliberately rather than guessed:

1. **Seed a seventeen-row default `Category` tree** (five top-level clothing
   categories, Shoes→3 sub-categories, Bags, Jewelry→5 sub-categories, Belts,
   Scarves, Hair Accessories, Sunglasses, Other Accessories) on first launch,
   mirroring exactly how `DEFAULT_OCCASIONS` is seeded — every row is a
   plain, user-editable/deletable `CategoryEntity`, not a hardcoded taxonomy.
2. **`OutfitSlot` (the existing 9-value Outfit Builder enum from Phase 5d)
   was not touched.** Expanding it would have been a breaking change to
   shipped Phase 5d UI. Instead, `OutfitSlot.classify(categoryName: String):
   OutfitSlot?` is a new companion function — a keyword heuristic against a
   garment's own category name, the same "good enough, explicitly labeled as
   a heuristic" honesty `ColorHarmony` already uses, not a guarantee. An
   unrecognized category name yields `null`, and the engine simply can't
   place that garment automatically — it never guesses wrong silently.
   `AccessoryCategory` (a second, narrower classifier for
   belt/hair-accessory/sunglasses/scarf, used only for the "Include X"
   preference toggles) follows the identical pattern.

## Rule engine

`core:data/repository/styling/` — five small files, following the exact
"thin orchestrator class, top-level functions do the work" split
`StatsRepositoryImpl` established in Phase 5e, so function/line counts stay
small regardless of how the algorithm grows:

| File | Responsibility |
|---|---|
| `EngineInput.kt` | One immutable snapshot of every source `StylingEngineRepositoryImpl` needs, loaded once per call via `.first()` on each source `Flow` — a suggestion is generated on demand ("Generate Another Look"), not continuously recomputed |
| `RecommendationRuleEngine.kt` | `buildSlotCandidates` (slot bucketing + per-garment `AVOID_CATEGORY`/`AVOID_BRAND` filtering), `scoreCandidate` (favorites/rarely-worn/recently-worn/color-bias/dress-code/comfort scoring, each contributing a reason string), `passesWeatherFilter` (the Constitution's hard weather filter) |
| `OutfitAssembler.kt` | `generateRecommendations` — picks a dress *or* a top+bottom pair (mirroring Outfit Builder's own mutual exclusion), then optional slots gated by preference *and* availability, applies `AVOID_COLOR_COMBO`/`ALWAYS_INCLUDE_CATEGORY` whole-outfit rule checks, scores color harmony via the promoted `ColorHarmony` |
| `RecommendationExplainer.kt` | Joins the strongest 1–3 reasons per outfit into natural sentences — never technical wording, matching the brief's own examples |
| `StyleRuleParameters.kt` | A deliberately simple flat `key=value;key2=value2` parser for `StyleRule.parametersJson` — see Known Limitations for why this isn't real JSON |

**Rule types enforced automatically**: `AVOID_CATEGORY`/`AVOID_BRAND` (per
garment, filtered before scoring), `AVOID_COLOR_COMBO`/
`ALWAYS_INCLUDE_CATEGORY` (whole-outfit, checked after assembly).
`MIN_WARMTH_BELOW_TEMP` is folded into the weather filter itself rather than
evaluated as a separate rule, since it's the same check. `CUSTOM` has no
fixed parameter schema by design (the enum's whole point) and is not
auto-enforced — it exists for a future feedback/rule-authoring UI to
display, not for this engine to interpret.

**"Generate Another Look" produces genuine variety**, not the same outfit
re-scored: `generateRecommendations(input, count)` scores and sorts every
slot's candidates once, then each of the `count` outfits picks the *next*-best
candidate per slot (`pickNth`), not always the top one.

## Scoring logic

A plain weighted-sum score per candidate garment, not a constraint solver —
appropriately scoped for "a rule engine," not oversold as ML:

| Signal | Effect | Gated by preference |
|---|---|---|
| Favorite | `+3.0` | `preferFavorites` |
| Worn within the repeat interval | `−5.0` | `avoidRecentlyWorn` / `maxRepeatIntervalDays` |
| Rarely worn (≤2 wears) | `+1.5` | `preferRarelyWorn` |
| Primary color is a signature color | `+1.0` | `favoriteColorBias` |
| Dress code matches a preferred one | `+2.0` | `preferredDressCodes` |
| Relaxed/oversized fit | `+0.5` | `preferComfortableFit` |
| Whole-outfit color harmony (not `MIXED`) | `+1.0` | always (a genuine finding, not a preference) |

Wear-history signals (`totalWearCount`, `lastWornDate`) come from
`StatsRepository.observeCostPerWear()` — Phase 5e's existing derived query,
not a re-derivation. Favorite colors come from `UsageStats.signatureColorIds`
(`StatsWindow.SIX_MONTHS`) — the same "recent, not all-time" bias Wardrobe
Story already uses for its own "your most-loved color" card.

## Trip Status and Laundry Status

- **Laundry Status** is genuinely new: `Garment.isInLaundry: Boolean = false`,
  an additive column (`MIGRATION_2_3`) exactly mirroring how Phase 5d added
  `outfits.isFavorite`/`isArchived`. A manual toggle
  (`GarmentRepository.setInLaundry`), not an automated laundry-tracking
  subsystem — no history, no schedule, no "wash cycle" concept. The engine
  simply excludes `isInLaundry == true` garments from candidates.
- **Trip Status is derived, not stored.** A garment counts as "away" only
  when it's `isPacked == true` on a `PackingListItem` belonging to a `Trip`
  whose date range includes today (`TripRepository.observeTrips()` +
  `observePackingList(tripId)`, both pre-existing Phase 5c/5d repository
  methods) — no new schema. A garment merely *listed*, not yet packed, is
  still considered available.
- **Repair Status already existed**: `Garment.status == GarmentStatus.REPAIR`
  (one of Phase 3's original five statuses). `observeGarments(GarmentFilter
  (status = ACTIVE))` already excludes it from candidates — no new code.
- **Availability** = `GarmentStatus.ACTIVE`, same existing filter.

## Preference system

`RecommendationPreferences` (14 fields, all with defaults so a user who never
opens the preferences screen still gets complete, varied outfits) persists
via `StylistPreferencesRepository` → `StylistPreferencesDataStore`, an exact
structural mirror of `ClosetPreferencesRepository`/`ClosetPreferencesDataStore`
(Phase 5c's own established pattern) — same shared `DataStore<Preferences>`
instance, same feature-scoped key namespacing in the existing
`PreferenceKeys.kt`, same unit-separator (`U+001F`) list-encoding convention
for `preferredDressCodes`.

`preferredDressCodes` deliberately reuses the existing fixed `DressCode`
vocabulary rather than inventing a new "style" taxonomy this schema has no
other use for. `preferComfortableFit` is a single boolean (biasing toward
`Fit.RELAXED`/`OVERSIZED`) rather than a speculative multi-point comfort
scale — the brief's "Preferred Comfort" has no concrete definition to build
against, and inventing one would be exactly the kind of unverified capability
Constitution rule 4 warns against.

## Preview pipeline

`feature:outfits/preview/` — a genuinely new Canvas-based 2D layering screen,
since no avatar/mannequin/layering code existed anywhere in the codebase
before this phase (confirmed by search). `MannequinSilhouette` is a plain
`Canvas` line drawing (a head circle + a torso/leg line) — no bitmap asset
needed. Each garment in the outfit layers its `cutout.webp` (Phase 5b's
background-removed image, when segmentation succeeded) via Coil `AsyncImage`,
offset vertically by its `OutfitSlot`; falls back to the garment's own
thumbnail when no cutout exists — never a blank slot. Pan/zoom uses the
standard Compose `detectTransformGestures` recipe, scale clamped `1x`–`3x`.
"Swap individual items" reuses the same `suggestReplacementForSlot` Quick
Action the Recommendations screen uses — one replace mechanism, not two.

## Quick Actions

| Action | Implementation |
|---|---|
| Wear Today / Schedule | `persistSelectedOutfit` (saves the suggestion if not already saved) then `logOutfitWear` — `WearEventStatus.WORN` or `PLANNED` depending on whether the date is in the future, the exact `CalendarViewModel.logWear` convention |
| Save Outfit / Favorite | `persistSelectedOutfit`, then `setFavorite` for Favorite |
| Generate Another Look | Re-invokes `suggestOutfits` |
| Replace Shoes/Bag/Jewelry/Watch/Jacket/etc. | `suggestReplacementForSlot(outfit, slot, context)` — the best-scoring alternative for that one slot, excluding the current occupant; swaps it into the already-displayed suggestion without a full re-generate |

Every recommended item is a real `GarmentTileUiModel` tap target
(Searchable Recommendations) — tapping opens Garment Detail, the same
`onOpenGarment` callback pattern every other feature module uses.

## Performance

`RecommendationRuleEngineLargeWardrobeTest` — 1,000 garments through
`generateRecommendations` (plain JVM test, no Room/Robolectric needed, since
the engine's own logic is pure Kotlin operating on an already-loaded
`EngineInput`) — asserts a bounded result size and a 2-second wall-clock
budget, the same discipline Phase 5e's `InsightsBuildersLargeDatasetTest`
established. `EngineInput` itself is loaded via one `.first()` snapshot per
source `Flow` — no continuous subscription overhead, no Kotlin-side cache
(nothing here was judged expensive enough to justify one, unlike Phase 5e's
`HeatmapBucketCache`).

## Testing

- **Rule engine** (`RecommendationRuleEngineTest`, 15 tests) — slot
  classification, `AVOID_CATEGORY`/`ALWAYS_INCLUDE_CATEGORY` rule
  enforcement, favorite/recently-worn scoring, the weather filter's pass-
  through-when-null and cold-temperature-rejects-low-warmth behavior, empty
  results when no dress and no top+bottom pair exists, explanation-string
  fallback and `MIXED`-harmony exclusion, parameter round-tripping.
- **Large wardrobe** (`RecommendationRuleEngineLargeWardrobeTest`, 1 test) —
  1,000 garments, bounded output, time budget.
- **Repository integration** — `GarmentRepositoryImplTest` extended with a
  real Room-backed `setInLaundry` round-trip; `Migration2To3Test` (real
  Robolectric-backed migration, same "build the prior version from its
  committed schema JSON" approach `Migration1To2Test` established, for the
  same Room-2.8.4/Robolectric interaction reason —
  see `TECHNICAL_DEBT.md`); `WardrobeDatabaseSeedTest` (the new category seed
  produces every expected default row).
- **ViewModel** — `RecommendationsViewModelTest` (4 tests: initial load,
  empty state, Wear Today's save+log round trip, slot replacement) and
  `StylistPreferencesViewModelTest` (3 tests: defaults, persistence,
  partial-update isolation), both against hand-written fakes
  (`FakeStylingEngineRepository`, `FakeStyleRuleRepository`,
  `FakeStylistPreferencesRepository`) added to `feature:outfits`' existing
  fakes file.

**A real regression this testing loop caught**: bumping `WardrobeDatabase`
to version 3 broke the pre-existing `Migration1To2Test`, which only supplied
`MIGRATION_1_2` to its `Room.databaseBuilder(...)` call — Room validates the
*full* migration path reaches the class's currently-declared version, not
just the one step a given test cares about, so it failed with "A migration
from 1 to 3 was required but not found." Fixed by adding `MIGRATION_2_3`
alongside `MIGRATION_1_2` in that test's own migrations list — a real,
caught-by-running-the-actual-test-suite bug, not a hypothetical.

## Developer Panel

New "Personal Wardrobe Stylist" section reads `core:ui`'s
`RecommendationDiagnostics` (last generation time, suggestion count, top
score, active rule count, active flow subscriptions) — reported by
`RecommendationsViewModel` around its own call into the engine, since
`StylingEngineRepositoryImpl` lives in `core:data`, which has no `core:ui`
dependency (the identical layering constraint Phase 5e's `StatsDiagnostics`
navigated). `activeFlowSubscriptions` specifically tracks
`StylistPreferencesViewModel`'s `observePreferences()` subscription, the one
continuously-observed `Flow` this phase has — recommendation generation
itself is a one-shot suspend call, not a subscription.

## Known limitations, stated rather than hidden

- **Weather stays a documented pass-through.** `SuggestionContext.weather`
  is always `null` in this phase (`WeatherRepository` is genuinely Phase 7,
  unimplemented, needs `core:network`). `passesWeatherFilter` has real logic
  for when weather *is* present, but it's never been exercised against real
  forecast data — thresholds (10°C/27°C apparent-temperature cutoffs,
  warmth-rating 3 as the pivot) are a simple, defensible starting point, not
  tuned against anything real. Revisit once Phase 7 lands.
- **`StyleRule.parametersJson` uses a hand-rolled flat `key=value;key2=value2`
  format, not real JSON**, despite the field's name — every rule type this
  engine currently interprets needs at most two scalar values, and adding a
  `kotlinx.serialization` dependency to `core:data`/`core:model` for that
  felt like more machinery than the actual need. If a future rule type needs
  nested/structured parameters, that's the trigger to introduce real JSON,
  not before.
- **No rule-authoring or feedback-voting UI in this phase.** `StyleRule`/
  `Feedback`/`StyleRuleRepository` are read (`observeActiveRules`) and would
  correctly enforce a user-authored or derived rule if one existed, but
  nothing in this phase's UI lets a user create one, and no
  feedback→derived-rule learning loop was built — the brief's own "Implement
  ONLY" list didn't ask for it, and building it would be real scope creep
  into a "Constitution: no black-box suggestions" feature that deserves its
  own dedicated design pass, not a bolt-on here.
- **The 2D Preview's per-slot vertical offsets are fixed constants**, not
  derived from each garment's actual image dimensions or a real body-fit
  model — a deliberately simple "roughly where this slot sits on a body"
  layout, not a garment-fitting engine.
- **No device-measured performance** — the 1,000-garment test is a real,
  passing JVM regression guard, not a profiling run on real hardware (none
  exists in this environment), the same honest gap every prior phase has
  stated for its own performance targets.
- **Smart Rotation avoids recently-worn *garments*, not recently-worn whole
  *outfits*.** The engine assembles fresh garment combinations each call
  rather than selecting among previously-saved `Outfit` rows, so "don't
  suggest the exact same combination as last Tuesday" has no natural
  comparison target the way per-garment rotation does — `WearEventRepository`
  was initially wired in for this and then removed (an unused constructor
  parameter, caught by detekt's `UnusedPrivateProperty` during this phase's
  own verification pass) once it became clear it wasn't actually driving any
  logic. Per-garment recently-worn avoidance (which *is* real, tested, and
  score-affecting) covers most of the practical "don't repeat myself" need in
  practice; literal whole-outfit deduplication is deferred, not faked.
- **`OutfitSlot.classify`/`AccessoryCategory.classify` are keyword heuristics**
  tuned to this phase's own seeded category names. A user who renames or
  restructures their categories away from those names will get `null`
  classifications for the affected items, and those garments simply won't be
  automatically placeable by the engine — not a crash, but a real, stated
  precision ceiling.

## Verification

`./gradlew clean build` — run to completion multiple times as real issues
surfaced and were fixed, not assumed green:

- **`MIGRATION_2_3`'s `Migration(2, 3)` call tripped detekt's `MagicNumber`**
  (detekt's default ignore list is `-1, 0, 1, 2`; `3` isn't in it, which is
  also why `MIGRATION_1_2`'s `Migration(1, 2)` never tripped it). Fixed with
  named `SCHEMA_VERSION_2`/`SCHEMA_VERSION_3` constants, the same
  named-constant convention Phase 5d used for its own `MagicNumber` findings.
- **`OutfitSlot.classify` tripped detekt's `CyclomaticComplexMethod`**
  (33 against a threshold of 15) — its one `when` block had up to five
  `||`-joined string checks per branch. Fixed by extracting each slot's
  keyword list to a named `private val ..._KEYWORDS` constant and reducing
  each branch to a single `.any { it in lower }` call, the identical pattern
  `AccessoryCategory.classify` already used (and which never tripped the same
  rule, confirming the fix).
- **A `DestructuringDeclarationWithTooManyEntries` finding** in
  `DeveloperPanelViewModel` — `val (diagnostics, builder, stats,
  recommendations) = diagnosticsBundle` destructured 4 components against
  detekt's 3-component cap (a rule that predates this phase but this phase's
  `DiagnosticsBundle` addition tripped for the first time). Fixed by reading
  each field off `diagnosticsBundle` directly instead of destructuring.
- **ktlint formatting violations** (multi-line `when`-branch blank-line/brace
  rules in `RecommendationRuleEngine.kt`/`OutfitAssembler.kt`; argument-
  wrapping and line-length in `RecommendationRuleEngineTest.kt`,
  `RecommendationRuleEngineLargeWardrobeTest.kt`, `WardrobeDatabaseSeedTest.kt`,
  and several `feature:outfits` screens) — fixed via
  `./gradlew ktlintMainSourceSetFormat ktlintTestSourceSetFormat` per affected
  module, then hand-verified no line exceeded 120 characters afterward.
- **An unused `OutfitPreviewScreen.kt` constant** (`LAYER_IMAGE_SIZE`,
  `UnusedPrivateProperty`) — removed; the preview layers size garments via
  `fillMaxWidth()`, not a fixed dimension.

All of the above are genuine static-analysis findings caught by actually
running `detekt`/`ktlint`, not hypothetical — each was fixed and the full
build re-run until `BUILD SUCCESSFUL` with zero `FAILED` tasks across all 20
modules (compile, unit tests, lint, ktlint, detekt) confirmed nothing was
missed. See `TECHNICAL_DEBT.md`'s Phase 6 entry for the standing gaps this
phase leaves behind.

## Future improvements

- Real weather-filter tuning once Phase 7's `WeatherRepository` provides
  actual forecast data to validate the thresholds against.
- A rule-authoring screen (create `AVOID_CATEGORY`/`AVOID_BRAND`/etc. rules
  directly) and a thumbs-up/down feedback UI wired to
  `StyleRuleRepository.recordFeedback`'s existing derivation contract.
- Promote `StyleRule.parametersJson` to real JSON if a future rule type
  needs structured parameters.
- A Home-screen recommendation card using the already-existing
  `PersonalizationSettings.showRecommendationCard` toggle (currently
  persisted but not yet rendered anywhere) — deferred from this phase to
  keep Home's own data-flow changes scoped; Recommendations is reachable
  today via a top-bar action on Saved Looks instead.
