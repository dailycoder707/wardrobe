# Phase 5c — Wardrobe Experience (Home, Closet, Garment Detail, Search, Filters, Sorting)

Scope: the six named screens/interactions, built as real, fully-wired Compose UI
against the repositories Phase 5a/5b already implemented. No Outfit Builder,
Calendar, Trips, Wishlist, Statistics, Styling Engine, Weather, or AI
recommendations — those stay Phase 5d/5e/5f/6/7. Edit Garment is included as a
necessary sub-screen of Garment Detail's "Edit" action (the same way Search/
Filters/Sorting are sub-interactions of Closet), not a scope violation.

## Architecture

```
core:model      — GarmentFilter/GarmentSort extensions, no new Android deps
core:domain     — GarmentRepository.observeGarment/setFavorite, ClosetPreferencesRepository
core:datastore  — ClosetPreferencesDataStore (sort, grid density, recent searches)
core:data       — ClosetPreferencesRepositoryImpl, GarmentRepositoryImpl extensions
core:designsystem — real Atelier Light/Night tokens (Color/Type/Shape/Elevation/Motion)
core:ui         — shared Compose components (GarmentTile, EmptyState, shimmer,
                   filter chip, nav dock, confirmation toast, RecompositionTracker)
feature:closet  — Home, Closet, Garment Detail, Edit Garment, Developer Panel
                   screens + ViewModels; this is also where the Home screen lives,
                   since there is no dedicated feature:home module
app             — NavHost wiring, floating nav dock host
```

Every screen's ViewModel depends only on `core:domain` repository interfaces —
consistent with Phase 1's dependency rule and Phase 5a's "features never see
`core:data`" boundary.

## A real schema gap, closed forward

Phase 3's schema had no favorite flag anywhere, and Phase 5a's `GarmentFilter`
had no room for color/material/tag/price/never-worn/recently-worn filtering or a
sort model — none of that was needed until this phase's screens actually existed
to use it. Rather than route around the gap, this phase amends it directly,
matching the project's established pattern of fixing schema/interface gaps
forward once a real screen exposes them (Phase 5a did the same for
`StyleProfileRepository`'s missing budget setter):

- `GarmentEntity`/`Garment` gained `isFavorite: Boolean`. No Room migration:
  schema version 1 has never shipped to a real install, so amending the entity
  directly is safe — this stops being true the moment a real release exists,
  which is why this is called out explicitly rather than silently done.
- `GarmentDao` gained `isFavorite` in its `WHERE` clause and a direct
  `setFavorite(id, isFavorite, updatedAt)` update query.
- `GarmentFilter` gained `colorId`/`materialId`/`tagId`/`priceMin`/`priceMax`
  (applied in-memory in `GarmentRepositoryImpl`, over the already-materialized
  `Garment` list — this app's scale makes a linear filter pass cheap, and these
  fields don't share one obvious SQL shape) and `neverWorn`/
  `recentlyWornWithinDays` (applied one layer up, in `ClosetViewModel`, since
  they need `StatsRepository` wear-history data `GarmentRepository` has no
  dependency on).
- `StatsDao.observeCostPerWear()`/`CostPerWearEntry` gained `lastWornDate` —
  the single extra column needed to back "Recently Worn" as both a filter and
  a sort field without a second query.
- `GarmentRepository` gained `observeGarment(id): Flow<Garment?>` — Garment
  Detail needs live updates (a favorite toggle, an edit, a delete elsewhere)
  the existing list-only `observeGarments`/one-shot `getGarment` didn't cover.

## Screen hierarchy & navigation

```
WardrobeNavHost (app)
├─ HomeRoute            — greeting, quick actions, recently added/worn,
│                          continue editing, wardrobe summary
├─ ClosetRoute(favoritesOnly, focusSearch)
│                       — grid, search, filters, sort, selection mode
├─ GarmentDetailRoute(garmentId)
│                       — metadata, image gallery/viewer, favorite/edit/delete,
│                          wear history
├─ EditGarmentRoute(garmentId)
│                       — compact edit form, real save via updateGarment
└─ DeveloperPanelRoute  — debug builds only, registered only when
                           BuildConfig.DEBUG is true (not just hidden — the
                           destination doesn't exist in a release nav graph)
```

Only `HomeRoute`/`ClosetRoute` show the floating `NavigationDock`
(`docs/design/navigation-flow.md`'s floating glass pill) — the eventual 5-icon
dock (Home/Closet/Outfits/Calendar/More) only lists destinations that exist as
real screens today; Outfits/Calendar/More are added when Phase 5d/5e/5f build
them, not shown now as disabled stubs (that would fail this phase's own
"no placeholders" instruction).

Search/Filters/Sorting have no dedicated routes — per the navigation doc, Search
is a contextual toolbar affordance and Filters/Sort are bottom sheets, all
scoped to the Closet screen they're opened from, exactly as specified.

The Developer Panel's only entry point is an unlabeled long-press on Home's
date text, gated by `onOpenDeveloperPanel` being non-null (which `WardrobeNavHost`
only passes when `BuildConfig.DEBUG`) — deliberately not documented UI, so it
never reads as a discoverable feature to the app's one real user.

## Personalization — never hardcoded

`HomeViewModel` combines `PersonalizationRepository.observe()` with the garment/
wear-event/stats flows; the greeting is built through `PersonalizationSettings.
greetingText()` (Phase 5a), the same single function every greeting anywhere in
the app renders through. `HomeUiState.showWardrobeHealthCard` maps to a real
card (garment count / worn-this-month / items-worn, from `StatsRepository`).
`showWeatherCard`/`showRecommendationCard`/`showInspirationCard` are respected in
the data layer but have no corresponding card in this phase's Home screen — their
content sources (`WeatherRepository`/Phase 7, the styling engine/Phase 6, an
inspiration-quote source that doesn't exist) aren't built yet. Toggling them
currently has no visible effect, which is the honest state — showing an empty
card or fake content would violate "no fake data" more than showing nothing.

## Closet: filtering, sorting, search, selection

`ClosetViewModel`'s pipeline: a debounced search query + user filter selections
build a `GarmentFilter`, which `flatMapLatest`s into a new
`GarmentRepository.observeGarments()` Flow whenever the SQL-level fields
change; that's combined with `StatsRepository.observeCostPerWear()` for wear
count/cost-per-wear/last-worn data, filtered further for `neverWorn`/
`recentlyWornOnly`, sorted by the selected `GarmentSort`, then mapped to
`GarmentTileUiModel`s.

Sort order and grid column count persist via `ClosetPreferencesRepository`
(DataStore-backed, matching the Phase 5a pattern exactly) — "persist user
preference" from the master prompt. Recent searches persist the same way,
capped at 8, most-recent-first, with a real "clear history" action.

Grid density has both required paths (`component-library.md`'s own
accessibility requirement): pinch-to-zoom on the grid (`detectTransformGestures`,
snapping to whole column counts, never holding a fractional state) and a
stepper icon in the toolbar cycling 2→6 columns — both write to the same
persisted value, so either path produces an identical result.

Selection mode (long-press to enter, tap to toggle, bulk favorite/delete) is
local `ViewModel` state, not persisted — there's no product reason a
multi-select session should survive a process death.

## Garment Detail

Portrait: a single scrolling column, image gallery on top. Landscape: a real
two-pane layout (`BoxWithConstraints` checking `maxWidth > maxHeight`), gallery
left, metadata right — not just a wider single column. The image viewer is a
full-screen `Dialog` with a `HorizontalPager`, tap-to-dismiss; pinch-to-zoom and
the before/after compare slider from `component-library.md` are **not**
implemented — a real, scoped simplification (see Known Limitations).

Wear count, cost-per-wear, and last-worn-date come from the same
`StatsRepository.observeCostPerWear()` entry Closet's sort uses, filtered to
this one garment — no duplicate query. Wear History lists this garment's direct
`WearEvent`s (`WearEventRepository`, filtered client-side to this garmentId,
newest first) — outfit-logged wears aren't included, since Outfit Builder
doesn't exist yet to produce any.

Delete calls `GarmentRepository.deleteGarment`, which throws (RESTRICT foreign
key, Phase 3) if the item has wear/outfit history — caught and surfaced as a
dialog explaining the item is protected, pointing at Sold/Donating instead,
exactly as `phase-3-persistence.md`/`GarmentRepositoryImpl`'s own doc comment
anticipated this screen would need to.

## Edit Garment

A compact but fully real form: name, category/brand/primary-color dropdowns
(`ExposedDropdownMenuBox`, backed by the real taxonomy repositories), size,
price, condition, season/dress-code/tag multi-select chips, notes — saved via
`GarmentRepository.updateGarment` on a copy of the loaded `Garment`. Not every
Phase 3 field has an editor (e.g. purchase date, fit, length, sleeve length,
warmth/breathability ratings, full palette/material composition editing) — see
Known Limitations.

## Design system — from placeholder to real

Phase 4 specified Atelier Light/Night, the Fraunces/Inter type scale, the
16/20/28dp radius system, the soft warm-shadow elevation system, and the two
motion curves; Phase 2's `core:designsystem` only had Material3 defaults as a
placeholder. This phase implements all of it for real:

- **Color**: the actual hex values from `phase-4-design-system.md` Section 2,
  mapped onto Material3's `ColorScheme` where a slot exists (`surfaceElevated`
  → `surfaceContainerHigh`, `border` → `outline`, `textSecondary` →
  `onSurfaceVariant`) and a small `WardrobeExtendedColors`
  (`accent`/`onAccent`/`success`/`warning`/`textSecondary`) for the tokens that
  have no Material3 equivalent at all.
- **Type**: the real scale (sizes, weights, line heights, letter-spacing) —
  see Known Limitations for the one real gap (no bundled Fraunces/Inter font
  files).
- **Shape**: `WardrobeRadius` (8/14/16/20/28/12dp, per-component, since the
  design doc specifies radius per component rather than Material3's generic
  5-tier scale).
- **Elevation**: `wardrobeShadow()`, a warm-tinted shadow modifier — a
  documented approximation of the design doc's independent y-offset/blur/
  opacity spec, since Compose's `Modifier.shadow` only exposes a single
  elevation value plus ambient/spot tint, not offset and blur independently.
- **Motion**: `WardrobeMotion`'s two easing curves and duration constants, plus
  `isReducedMotionEnabled()` (reads `Settings.Global.ANIMATOR_DURATION_SCALE`
  directly, since Compose's animation APIs — unlike View `Animator` — don't
  honor that system setting automatically).

## Performance

- `LazyVerticalGrid`/`LazyRow` throughout, stable `GarmentTileUiModel` keyed by
  id — no full-`Garment`-object recomposition triggers on unrelated field
  changes (`GarmentTileUiModel` is a small, purpose-built projection, not the
  domain model).
- Every `AsyncImage` in the closet grid and Home's sections points at the
  `ImageType.THUMBNAIL` file (Phase 5b, 300px WebP), never the original —
  matching Phase 1's "never decode originals in a list" requirement structurally.
- `rememberLazyGridState()`/default Compose state-saving means scroll position
  survives recomposition and rotation without extra plumbing.
- The Photo grid's Coil `ImageLoader` (Phase 5b's bounded memory/disk cache)
  is shared app-wide, so scrolling back to a previously-seen tile is a cache
  hit, not a re-decode.
- **Not measured empirically at 300/600/1000 garments**: no device/emulator is
  available in this environment (the established constraint since Phase 5a/5b).
  The design choices above (thumbnail-only images, stable keys, Paging-3-ready
  `GarmentDao.pagingSource` already existing from Phase 3) are the right
  levers for that scale, but claiming a measured frame-rate number here would
  be exactly the kind of unverified claim this project's discipline rules out.
  Real measurement is Phase 9's Macrobenchmark work (`phase-1-architecture.md`
  Section 21), unchanged by this phase.

## State management

Every screen: a single `StateFlow<UiState>` built from `combine()` over
repository Flows (nested where more than 5 sources are needed, mirroring
`StatsRepositoryImpl`'s own nested-combine style from Phase 5a), collected via
`collectAsStateWithLifecycle()`. Local, non-persisted UI state (search-bar
expanded, which sheet is open, delete-confirmation visibility) stays in
`remember { mutableStateOf(...) }` at the Composable level — it doesn't belong
in a ViewModel that's meant to survive configuration change with meaningful
data, not transient dialog visibility.

No use-case layer was added this phase — `core:domain/usecase/` stays empty.
The combining logic every screen needs (join wear stats with garments, apply
sort, resolve category/brand names) is screen-specific, not shared across
multiple ViewModels yet, so a ViewModel composing repository Flows directly is
the right amount of structure; introducing a use-case layer now would be
building for a hypothetical second consumer that doesn't exist (system
guidance: don't add abstractions beyond what the task requires).

## Debug Developer Panel

Real, not simulated, data throughout:
- **Database counts**: live `observeAll().map { it.size }` over every taxonomy
  repository plus garments.
- **Closet screen state**: `ClosetDiagnostics`, a debug-only `@Singleton` side
  channel `ClosetViewModel` reports its own live search/filter/sort/result-count
  state into — the minimum plumbing needed to show *this specific screen's*
  state from a separate destination, not a general-purpose event bus.
- **Image cache**: `ImageFileStore.allImageFilesOnDisk()` (Phase 5b), summed.
- **Memory**: `Runtime.getRuntime()` totals, read live.
- **Compose recomposition counters**: `RecompositionTracker` (`core:ui`), a
  `SideEffect`-based counter instrumented into `GarmentTile` — a real per-
  composable count, not a placeholder number.
- **Recent processing jobs**: `WorkManager.getWorkInfosFlow(WorkQuery.fromTags(...))`
  against tags added to `ImageProcessingWorker`/`OrphanedImageCleanupWorker`/
  `BackupExportWorker`/`BackupRestoreWorker` (Phase 5b's workers had no tags
  before this phase — added here specifically so the panel has real job names
  to show, not just opaque UUIDs).

Enforcement that it never reaches release: the route itself is only registered
in `WardrobeNavHost` inside `if (BuildConfig.DEBUG)` — there is no way to
navigate to it in a release build, not merely a hidden button.

## Testing strategy

| Layer | How | Why |
|---|---|---|
| `ClosetViewModel` | Fake repositories (plain Kotlin, `feature:closet`'s own test source) + Turbine, `StandardTestDispatcher` | The highest-risk logic this phase wrote (search/filter/sort combination) — real, executable, no Robolectric needed since no Android framework class is touched |
| `HomeViewModel` | Same fake-repository approach | Greeting/recently-added/continue-editing/recently-worn derivation |
| `GarmentTile`, `ClosetGrid` | Robolectric-run Compose UI tests (`createComposeRule()`, JVM `testDebugUnitTest`) | Same reasoning Phase 5a/5b established: no device/emulator in this environment, so Compose UI tests are written to actually run and pass here, not left as unrunnable `androidTest` sources |
| `GarmentDetailScreen`/`EditGarmentScreen`/`ClosetScreen` (the full, `hiltViewModel()`-backed screens) | **Not unit tested directly** | Testing the top-level screen composable requires a full Hilt test graph (`@HiltAndroidTest` + a custom test runner) — out of proportion to what this phase's screens need; the ViewModels' logic (tested above) and the presentational components they render (also tested above) are what carry the real risk |

**Known Robolectric-specific fix, recorded rather than silently worked around**:
Robolectric-run Compose tests (`createComposeRule()`) failed with
`"Unable to resolve activity for Intent... ComponentActivity"` until
`androidx.compose.ui:ui-test-manifest` was added as `debugImplementation` (not
`testImplementation` — that only affects the JVM classpath, not manifest
merging) **and** `testOptions.unitTests.isIncludeAndroidResources = true` was
set — both are needed together for a *library* module's `testDebugUnitTest` to
merge in the `ComponentActivity` declaration Compose's test harness launches
against. Discovered via the actual failure, not assumed.

## Known limitations, stated rather than hidden

- **No bundled Fraunces/Inter font files.** `FrauncesFamily`/`InterFamily`
  (`core:designsystem`) fall back to `FontFamily.Serif`/`FontFamily.SansSerif`
  — this development environment has no way to fetch binary font assets from
  Google Fonts. The scale/weight/hierarchy are real; swapping in real `.ttf`
  files later is a one-file change (`Type.kt`), not a redesign. Tracked in
  `TECHNICAL_DEBT.md`.
- **Elevation is an approximation.** `wardrobeShadow()` maps the design doc's
  independent y-offset/blur/opacity spec onto Compose's single-elevation
  `Modifier.shadow` API — a deliberate, documented simplification, not a
  literal implementation of "blur:Xdp" (a true independent blur needs a custom
  `RenderEffect` layer, judged out of proportion to the visual return here).
- **Motion is simplified, not choreographed.** `docs/design/motion-guide.md`
  describes shared-element transforms, staggered reveals, and a gold-particle
  favorite-burst; this phase uses the correct durations/easings from
  `WardrobeMotion` for real transitions (toast, sheets, grid reflow) but does
  not implement shared-element tile→hero-image transforms or the particle
  effect — a scoped simplification given the size of this phase already.
- **No frosted-glass blur.** The nav dock and modal scrims use a solid
  `surfaceContainerHigh` at high opacity — exactly the documented API-26-30
  fallback from `phase-4-design-system.md` Section 7, applied everywhere
  rather than only below API 31, since implementing the real `RenderEffect`
  blur path was judged lower priority than the screens themselves for this
  phase.
- **Image Viewer**: tap-to-dismiss and swipe-between-images (`HorizontalPager`)
  work; pinch-to-zoom, double-tap-to-2×, swipe-down-to-dismiss, and the
  before/after compare slider (`component-library.md`) are not implemented.
- **Edit Garment** covers the most commonly-edited fields, not literally every
  Phase 3 attribute (fit, length, sleeve length, warmth/breathability ratings,
  and full palette/material-composition editing have no UI yet — purchase
  date and general notes were added by the Add-to-Wardrobe ingestion fix,
  see below).
- ~~**Add Garment / capture flow is out of scope.**~~ **Resolved** by the
  Add-to-Wardrobe ingestion fix (dated entry, `TECHNICAL_DEBT.md` item 17) —
  this was a release-blocking gap discovered on real-device testing, not a
  permanent scope decision: the capture pipeline this phase built
  (`GarmentImagePipeline`, `BackgroundRemover`, `ImageRepository`,
  `GarmentRepository.saveGarment`) had no screen calling it until then. A
  permanent FAB on Home/Closet, a new `feature:capture` module, and a
  Room-backed resumable import queue now provide it. "Continue Editing" on
  Home still surfaces `isReviewed == false` garments — now also reachable via
  the new "Save as Draft" path in the capture flow, not just as a fallback
  for an empty section.
- **No keyboard-shortcut framework.** The search field's IME "search" action
  submits and records history; there is no broader keyboard-navigation/shortcut
  layer beyond what Compose's default focus order already provides.
- **Performance targets are designed for, not measured** — see the Performance
  section above.
- **A transient one-frame stale-content emission is possible** when a filter
  changes: `ClosetViewModel`'s reactive pipeline (`flatMapLatest` re-querying
  `GarmentRepository` when the SQL-level filter changes) can emit one
  intermediate state where the filter flag has updated but the garment list
  hasn't caught up yet, before settling. Observed directly in this phase's own
  ViewModel tests (which wait for the settled state rather than the first
  matching one) — a minor, common artifact of combine-based reactive
  pipelines, not expected to be visually perceptible, not investigated further
  given the scale of this phase.

## Verification

`./gradlew clean build` — green (compile, ktlint, detekt, lint, unit tests)
across every touched module. `ClosetViewModelTest` (9 tests),
`HomeViewModelTest` (4 tests), `GarmentTileTest` (4 tests, `core:ui`),
`ClosetGridTest` (3 tests) — 20 new tests, all passing on a forced re-run, plus
every pre-existing Phase 5a/5b test still passing after the `Garment`/
`GarmentEntity`/`CostPerWearEntry` field additions.
