# Phase 7 — Context-Aware Wardrobe Assistant

Real weather (Open-Meteo, cached, never a hard dependency), a Weather
Settings screen, and context-aware refinement of the Phase 6 recommendation
engine — weather, today's planned calendar occasion, trip packing, and
availability all *nudge* scoring and explanations, but the engine still
produces complete outfits with zero context available. That guarantee is now
a permanent standing rule (Constitution rule 12, Context Refinement Rule,
added by the user while approving this phase): *"Context must refine
recommendations, never replace them... the recommendation engine must always
be able to generate a complete outfit using only the user's local wardrobe
data."*

## Architecture

```
core:model     WeatherCondition, extended WeatherSnapshot (current/feels-like/
               humidity/UV/condition), WeatherPreferences, RecommendationRunDiagnostics,
               Occasion.impliedDressCode()
core:database  MIGRATION_3_4 (weather_cache's 5 new columns)
core:network   OpenMeteoService/DTOs, WeatherProvider + OpenMeteoWeatherProvider
core:domain    WeatherRepository (implemented), WeatherPreferencesRepository,
               WeatherRefreshScheduler, StylingEngineRepository.lastRunDiagnostics()
core:data      WeatherRepositoryImpl, DeviceLocationSource, WeatherLocationResolver,
               WeatherRefreshWorker, StylingEngineRepositoryImpl's context resolution
core:datastore WeatherPreferencesDataStore
core:ui        RecommendationDiagnostics extended with weather/context fields
feature:settings  Weather Settings screen (this module's first real screen)
feature:closet Home screen weather line + recommendation preview card
```

Every piece of this was anticipated by earlier phases, confirmed by reading
the code rather than assumed: `WeatherRepository`/`WeatherSnapshot`/the
`weather_cache` Room table/DAO all already existed as unimplemented stubs
since Phase 3/5a specifically so this phase would be a real implementation,
not a from-scratch design. `WearEvent.weatherCacheId`/`occasionId` and
`SuggestionContext.weather`/`occasionId` likewise already existed with zero
callers. `core:network`'s dependencies (Retrofit/OkHttp/kotlinx.serialization)
were already pinned in the version catalog with a build-file comment stating
"Weather only... do not add any other API client... without updating the
Section 0 budget-posture decision first" — confirming Open-Meteo was always
the intended (and only ever intended) provider, not a choice made now.
Phase 1's own `phase-1-architecture.md` Section 18 had already fully
specified this design (Open-Meteo, a ~3h periodic worker, device-location-
or-manual-city fallback, `INTERNET`/`ACCESS_COARSE_LOCATION` permissions) —
both permissions were already in `AndroidManifest.xml` before this phase
touched anything.

## Weather strategy

`WeatherProvider` (`core:network`) is a thin abstraction over "how do we
fetch live weather" — mirrors `BackgroundRemover`'s interface-over-one-
implementation pattern (ADR-008, Phase 5b): a future provider swap only
touches `OpenMeteoWeatherProvider`, never `WeatherRepository` or the
recommendation engine. `OpenMeteoService` requests both `current` (today's
live reading) and `daily` (a 7-day lookahead window, `forecast_days = 7`) in
one call, so a calendar-planned outfit a few days out can still resolve real
forecast data, not just "today." Open-Meteo's own WMO weather-code table is
mapped to the simple `WeatherCondition` enum (SUNNY/CLOUDY/RAIN/STORM/FOG/
SNOW/UNKNOWN) at this network boundary — `core:model`/`core:domain` never
see a WMO code.

`WeatherRepositoryImpl.getForecast(location, date)` is the resilience
boundary Section 18 calls for: try a live fetch (rounding coordinates to
~2 decimals, ~1.1km, per the DAO's pre-existing documented convention),
upsert every returned day into `weather_cache`, and return the requested
day's row as non-stale. On any failure — network, parsing, anything — it
falls back to the exact-date cache row (`isStale = true`), then the most
recent cache row for that location regardless of date, then a fully-empty
`isStale = true` snapshot if nothing was ever cached. This method's `catch
(e: Exception)` is a deliberate, documented exception to this project's
usual narrow-catch discipline: there is no `AppError`/`Result` error-handling
layer to route through instead (Section 25's design was never actually built
in any prior phase — a pre-existing gap, not introduced here), and Section
18's own contract requires this class to never propagate an exception.

`getForecastForConfiguredLocation(date)` is the convenience most callers
actually want: resolves *where* from Weather Settings (device's last-known
coarse location via `DeviceLocationSource`/plain `LocationManager` — not
Play Services' Fused Location client, since a one-shot "whatever the OS
already has cached" read doesn't need continuous updates — falling back to
a manually-entered latitude/longitude when device location is off, denied,
or has no fix yet) and delegates to `getForecast`. Both
`StylingEngineRepositoryImpl` and `HomeViewModel`'s Weather Card use this one
method — no duplicated location-resolution logic.

**Geocoding a typed city name is deliberately not implemented.** This app's
network budget (Section 0/18, `core:network`'s own build-file comment) is
Open-Meteo and *only* Open-Meteo; adding a geocoding API call would violate
that budget decision. Manual location is entered as raw latitude/longitude
in Weather Settings, not a searchable place name — a real, stated UX
compromise, not an oversight.

## Offline behaviour

The whole point of Section 18's "always returns a value" contract: a
completely offline device still gets weather (from cache, marked stale) or,
on a brand-new install with no network ever reached, a fully-empty stale
snapshot — never an error screen, never a blocked recommendation. Every
display surface renders `WeatherSnapshot?.updatedAtLabel(now)`
(`core:model`) — "Updated 2 hours ago" / "Updated yesterday" / "No weather
available" — instead of showing stale data silently as if it were fresh.

Weather Settings' three toggles compose cleanly:
- **Use weather** (master) — off means weather is never fetched, never
  shown, never scored; `SuggestionContext`/`EngineInput.weather` stays `null`
  throughout, exercising the exact same code path Phase 6 always used.
- **Offline only** — still shows/scores whatever's already cached, but
  `WeatherRepositoryImpl` skips the live-fetch attempt entirely (checked
  before calling the provider, not after) — for a user who wants weather-
  aware styling from old data without ever letting the app touch the
  network.
- **Use device location** — off, or the OS permission not granted, or no
  last-known fix yet, all fall through identically to the manual
  latitude/longitude fields.

`WeatherRefreshWorker` (periodic, `NetworkType.CONNECTED`, ~3h default per
Section 18, configurable in Weather Settings) is an optimization, not a
requirement: it does nothing (`Result.success()`, never a failure) when
either toggle says not to fetch, since `WeatherRepositoryImpl` would make the
identical decision on its next on-demand call regardless.

## Caching

`weather_cache`'s schema (Phase 3, extended here with `MIGRATION_3_4`'s five
nullable columns: `currentTempC`/`feelsLikeC`/`humidityPercent`/`uvIndex`/
`condition`) already had its unique `(latitude, longitude, date)` index and
`upsert`/`get`/`getMostRecent`/`evictOlderThan` DAO methods fully built —
this phase only needed to actually call them. Every existing cached row
simply has the five new columns as `NULL` until the next refresh
repopulates them; no backfill needed, no default value beyond nullable.
`Migration3To4Test` verifies exactly this (existing rows survive, new
columns are present and null) the same way `Migration1To2Test`/
`Migration2To3Test` do — hand-building the v3 database from its committed
schema JSON rather than `androidx.room.testing.MigrationTestHelper` (the
same Room 2.8.4/Robolectric interaction documented in `TECHNICAL_DEBT.md`).
`Migration1To2Test`/`Migration2To3Test` both needed `MIGRATION_3_4` added to
their own migration lists too — the same "Room validates the full path to
the class's current declared version" regression Phase 6 already
encountered and documented.

## Recommendation refinement

Every context source resolves *inside* `StylingEngineRepositoryImpl.loadEngineInput`
— the same place Trip/Laundry/Availability already resolved in Phase 6, not
threaded in by the caller:

- **Weather** — `resolveWeather` prefers `SuggestionContext.weather` if the
  caller already knows it (e.g. a future trip day whose forecast was already
  fetched), otherwise calls `WeatherRepository.getForecastForConfiguredLocation(date)`.
  `RecommendationRuleEngine.weatherFactor` (new score factor) rewards a warm
  outerwear layer or warm shoes on a cold day (apparent temperature ≤ 10°C,
  the same threshold `passesWeatherFilter` already used), and an outerwear
  layer or scarf on a rainy/stormy day — each contributing a plain-language
  reason ("it's cool out, so a warmer layer helps," "it may rain today, so a
  jacket helps") through the exact same `ScoreFactor`/`buildExplanation`
  pipeline Phase 6 built. This only ever *adds* to a score, never subtracts
  — weather can make an outfit better without ever making one impossible to
  assemble; `passesWeatherFilter` (unchanged) is still the only hard
  exclusion.
- **Calendar** — `resolvePlannedOccasionDressCode` looks up today's `WearEvent`s
  (`WearEventRepository.observeEvents(DateRange(date, date))`, the same
  single-day-range convention `CalendarViewModel` already uses) for one with
  `status == PLANNED && occasionId != null`, then maps that `Occasion`'s name
  to a `DressCode` via the new `Occasion.impliedDressCode()` keyword
  heuristic (`core:model`) — "Wedding"/"Gala" → FORMAL, "Office"/"Work" →
  BUSINESS, "Gym"/"Athletic" → ATHLETIC, "Dinner"/"Date" → SMART_CASUAL,
  "Casual"/"Travel" → CASUAL, "Lounge"/"Home" → LOUNGE. The new
  `plannedOccasionFactor` score factor rewards a garment whose own
  `dressCodes` include that implied code, contributing "it matches your
  plans for today" — separate from the user's general `preferredDressCodes`
  Stylist Preference, since this is about *today specifically*.
- **Already-planned outfit** — separately, if today already has a `PLANNED`
  `WearEvent` with a real `outfitId` (not just an occasion), that saved
  `Outfit` is fetched and prepended to `suggestOutfits`' result with the
  fixed explanation "You already planned this outfit for today." — the
  brief's own example, verbatim. The freshly generated suggestions still
  follow it in the list; context refines, it never replaces the rest of the
  generation.
- **Trip/Availability** — unchanged from Phase 6 (packed-garment exclusion,
  `isInLaundry`, `GarmentStatus.ACTIVE`), reused as-is. New this phase: when
  half or more of the user's active wardrobe is packed for a currently-active
  trip, a whole-recommendation context note ("Many of your items are packed
  for a trip, so today's choices are more limited.") is prepended to every
  returned outfit's explanation — the brief's "if most wardrobe items are
  packed, explain why choices are limited."

`RecommendationRunDiagnostics` (`core:model`) is the new domain-level return
of `StylingEngineRepository.lastRunDiagnostics()` — a genuinely new,
additive interface method (not a caller-side approximation) exposing what
the *last* `suggestOutfits`/`suggestForItem` call actually resolved:
`weatherSource` (LIVE/CACHED/NONE, derived from `WeatherSnapshot.isStale`),
`weatherCacheAgeMinutes`, `rulesEvaluatedCount`/`rulesAppliedCount` (the
latter via a new `countAppliedAvoidRules` — how many active
`AVOID_CATEGORY`/`AVOID_BRAND` rules actually rejected ≥1 candidate, not just
how many exist), `plannedOutfitUsed`, and `contextNotes`. Reported into
`core:ui`'s `RecommendationDiagnostics` by `RecommendationsViewModel` right
after calling the engine, extending the exact same "core:data can't depend
on core:ui, so the caller reports" pattern Phase 6 established — only now
with real per-call data instead of an approximation.

## Full outfits

Nothing about "always recommend complete looks" changed — `OutfitAssembler`
is untouched. Weather/calendar context only ever adjusts *which* candidate
wins a slot (via scoring) or adds a sentence to the explanation; it never
adds or removes a slot, never makes a category optional that wasn't already
optional per Stylist Preferences (Phase 6), and never blocks assembly.

## Performance

Weather resolution adds at most one cache read (or one network call, WorkManager-scheduled
separately from any user-facing wait) to `loadEngineInput` — no new O(n²) work
over the candidate garment list. `weatherFactor`/`plannedOccasionFactor` are
each a single comparison per candidate, identical complexity class to the
existing Phase 6 factors; `RecommendationRuleEngineLargeWardrobeTest`'s
1,000-garment/2-second budget (unchanged, still passing) already covers this
class of scoring-function addition. `WeatherRefreshWorker` runs on
WorkManager's own background thread pool, `NetworkType.CONNECTED`-gated, not
expedited — never wakes the radio or blocks the UI thread. Home's weather/
recommendation preview loads once via `viewModelScope.launch` in `init`,
separate from `HomeUiState`'s own reactive `combine` chain, so a slow or
absent weather fetch never delays the rest of Home from rendering.

## Accessibility

Weather Settings and the Home assistant additions use the same
`MaterialTheme.typography`/`Switch`/`OutlinedTextField`/`TextButton`
primitives every other screen in this app already uses — TalkBack, large
fonts, and high-contrast support come from those shared components, not
anything new built for this phase. No new custom touch targets below the
existing minimum size.

## Testing

- **Weather repository / offline fallback** (`WeatherRepositoryImplTest`,
  6 tests, real in-memory Room `weather_cache`) — live fetch succeeds and
  caches; fetch failure falls back to the exact-date cache row; falls back
  further to the most recent row regardless of date; a fully-empty stale
  snapshot when nothing was ever cached; `offlineOnly` never attempts a live
  fetch even when the provider would otherwise succeed;
  `getForecastForConfiguredLocation` returns `null` when `useWeather` is off.
- **Provider mapping** (`OpenMeteoWeatherProviderTest`, `core:network`,
  4 tests) — every WMO code family maps to the right `WeatherCondition`;
  `current` block data only applies to index 0 (today), never a future day;
  a missing `daily` block yields an empty list, never a crash.
- **Recommendation refinement** (`RecommendationRuleEngineTest` additions,
  4 tests) — `weatherFactor` rewards warm outerwear on a cold day and
  outerwear on a rainy day, contributes zero bonus when weather is `null`;
  `plannedOccasionFactor` rewards a garment matching today's implied dress
  code over one that doesn't.
- **Repository-level context behavior** (`StylingEngineRepositoryImplTest`,
  new, 3 tests, MockK) — an already-planned outfit is surfaced first with
  the fixed explanation; a limited-choices context note appears when ≥50% of
  active garments are packed for an active trip; `lastRunDiagnostics`
  reports `WeatherSource.NONE` when no weather is available.
- **Weather Settings** (`WeatherSettingsViewModelTest`, `feature:settings`,
  3 tests) — defaults, an edit persists through the repository and
  reschedules the refresh worker, disabling one toggle leaves the others
  untouched (the last test surfaces a genuinely subtle `stateIn`/
  `SharingStarted.WhileSubscribed` timing detail — a brand-new subscriber's
  *first* emission is the `stateIn` seed value, not yet the upstream
  repository's real current value; the *second* emission is; both this
  test and the equivalent Phase 6 `StylistPreferencesViewModelTest` share
  this same underlying behavior, only this test's assertions were specific
  enough to be sensitive to it).

## Developer Panel

Extended, not new — the existing "Personal Wardrobe Stylist" section now
also shows: Weather source, Weather cache age, Rules applied, Planned
outfit used, Context notes (count) — alongside Phase 6's existing
generation-time/suggestion-count/top-score/active-rule-count/active-flow-
subscription rows.

## Known limitations, stated rather than hidden

- **Weather-filter/factor thresholds are still a simple, defensible
  starting point, not tuned against real forecast data at scale** — this
  phase is the first time real Open-Meteo data actually flows through
  `passesWeatherFilter`/`weatherFactor`, but no device/real-usage validation
  exists in this environment (the same "no device available" gap every
  prior phase has stated for its own performance/UX targets).
- **Geocoding a typed city name is not implemented** — manual location is
  raw latitude/longitude, a deliberate consequence of this app's one-API
  network budget (Section 0/18), not an oversight.
- **The Weather Settings screen has no Settings hub to live inside** —
  `feature:settings` was scaffolded (full `build.gradle.kts`, zero source
  files) but never built before this phase; Weather Settings is reached via
  a top-bar action on Recommendations, not from a fuller Settings home
  screen that doesn't exist yet.
- **Calendar occasion-to-dress-code mapping is a keyword heuristic**
  (`Occasion.impliedDressCode`), tuned to this app's own seeded default
  occasion names (Casual/Work/Event/Formal/Athletic/Travel/Date Night/
  Loungewear) — a user-created occasion with an unrecognized name simply
  doesn't bias scoring for that day, the same "explicitly labeled heuristic,
  not a guarantee" honesty pattern `OutfitSlot.classify`/`ColorHarmony`
  already use.
- **Trip-packed exclusion is still keyed off "today," not the date being
  planned for** (`context.date` is available but `currentlyPackedGarmentIds`
  doesn't use it) — a pre-existing Phase 6 limitation, not introduced or
  fixed here; a real gap for "plan next Tuesday's outfit while packing for a
  trip that starts Thursday," left as-is to avoid scope creep into Phase
  6's own trip-awareness design.
- **`rulesAppliedCount` only counts per-garment `AVOID_CATEGORY`/
  `AVOID_BRAND` rules** — whole-outfit rules (`AVOID_COLOR_COMBO`/
  `ALWAYS_INCLUDE_CATEGORY`) aren't separately counted in diagnostics, a
  stated simplification of the Developer Panel's own numbers, not a scoring
  gap (those rules still fully apply during assembly).
- **No device-measured performance** — same honest gap every prior phase
  has stated; the large-wardrobe JVM test is a real regression guard, not a
  profiling run on real hardware.

## Future improvements

- Tune weather-filter/factor thresholds against real forecast/usage data
  once a real device is available.
- A Settings hub screen, once one exists, would be a more natural home for
  Weather Settings than a Recommendations top-bar action.
- Extend trip-packed exclusion to use `context.date` instead of always
  "today," if planning-ahead-while-packing proves a real user need.
- Whole-outfit rule application counts in `RecommendationRunDiagnostics`,
  if the Developer Panel's numbers need to be more precise than they are
  today.
- Geocoding, if this app's network budget is ever revisited to allow a
  second outbound API.
