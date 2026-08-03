# :core:data

Repository **implementations** — Phase 5a for everything local-only,
Phase 5b added `ImageRepositoryImpl`, Phase 6 added `StylingEngineRepositoryImpl`,
Phase 7 added `WeatherRepositoryImpl` and made every repository context-aware.
This is the only module that depends on `core:database`, `core:datastore`,
`core:network`, and `core:image` all at once; it's where `core:domain`'s
interfaces get bound to real Room/DataStore/`core:network`/`core:image`-backed
classes via Hilt `@Binds`.

## Packages
| Package | Holds |
|---|---|
| `repository/` | One implementation class per interface — taxonomy repos (Category/Color/Material/Brand/Tag/Occasion), `GarmentRepositoryImpl`, `OutfitRepositoryImpl`, `WearEventRepositoryImpl`, `StyleRuleRepositoryImpl`, `StyleProfileRepositoryImpl`, `PersonalizationRepositoryImpl`, `TripRepositoryImpl`, `WishlistRepositoryImpl`, `StatsRepositoryImpl`, `BackupRepositoryImpl`, `ImageRepositoryImpl` (Phase 5b), `StylingEngineRepositoryImpl` (Phase 6, extended Phase 7 — see below), `WeatherRepositoryImpl`/`WeatherPreferencesRepositoryImpl`/`WeatherRefreshSchedulerImpl` (Phase 7), plus `ContextResolution.kt` and `styling/ContextScoring.kt` — sibling top-level-function files extracted from the repository classes above purely to stay under detekt's `TooManyFunctions`/`CyclomaticComplexMethod` thresholds, not a separate architectural layer |
| `repository/weather/` (Phase 7) | `DeviceLocationSource` (plain `LocationManager`, not Play Services' Fused Location client), `WeatherLocationResolver` (device-or-manual), `WeatherMapper` (entity ↔ domain, plus the "no data at all" empty-snapshot builder) |
| `mapper/` | Entity ↔ domain mapping functions, one file per aggregate — see phase-5a-data-layer.md's mapping strategy (`kotlinx.coroutines.flow.combine` over reference-data Flows, not per-item N+1 lookups) |
| `backup/` | `BackupFileOperations` (portable, plain `java.io`, unit-tested directly), `BackupExportWorker`/`BackupRestoreWorker` (`@HiltWorker`s that run it) |
| `image/` (Phase 5b) | `ImageProcessingWorker` (`@HiltWorker`, runs `core:image`'s `GarmentImagePipeline`), `OrphanedImageCleanupWorker` (periodic, two sweeps — see phase-5b-image-pipeline.md), `StagedImageStore` (in-memory seam between the worker and the repository — see its own doc comment for the accepted, documented limitation) |
| `weather/` (Phase 7) | `WeatherRefreshWorker` (`@HiltWorker`, periodic, default 3h, no-ops rather than fails when weather is disabled or offline-only is set) |
| `di/` | `DatabaseModule` (constructs the real `WardrobeDatabase` + every DAO), `RepositoryModule` (`@Binds` for every interface above), `WorkManagerModule`, `NetworkModule` (Phase 7 — Retrofit/OkHttp/`Json`/`OpenMeteoService`/`WeatherProvider` binding), `SyncModule` (Phase 8 — `DeviceIdentityKeyStore`/`DeviceDiscoveryService` bindings) |
| `sync/` (Phase 8) | `SyncEntityHandler` (the per-table apply-upsert/apply-delete/build-outgoing contract), `handlers/` (16 implementations — one per syncable table), `SyncEntityRegistry` (`TaxonomyDaos`/`WardrobeSyncDaos` DAO bags, dependency-ordered handler list, the `SyncIdResolver` every handler uses to translate a foreign `syncId` to this device's local row id), `SyncEngine` (orchestrates handshake → image transfer → change-batch exchange → conflict/history recording), `ImageTransferPhase.kt`, `DevicePairingRepositoryImpl`, `SyncRepositoryImpl` (the discovery race — see below), `SyncWorker`/`SyncSchedulerImpl` |

No `feature:*` module ever depends on this module directly — only `:app` does, so
Hilt can see the bindings at the composition root. Features depend on `core:domain`
interfaces only (Phase 1 Section 1's dependency rule).

## Phase 7 — context-aware recommendation refinement

`StylingEngineRepositoryImpl` now resolves weather, today's planned outfit,
and the calendar-implied dress code before scoring candidates (`loadEngineInput`
→ `ContextResolution.kt`'s `resolveWeather`/`resolvePlannedOccasionDressCode`),
and prepends any already-planned outfit ahead of freshly-generated suggestions
(`prependPlannedOutfit`) rather than replacing them. `RecommendationRuleEngine`
gained `weatherFactor`/`plannedOccasionFactor` (in the sibling
`styling/ContextScoring.kt`) — these only ever *add* to a candidate's score,
never exclude one; hard exclusions (packed-for-a-trip, in-laundry, weather-
inappropriate) still happen via the pre-existing filter path. This split
exists specifically so Constitution rule 12 (added this phase) holds by
construction: every context source is additive, and the engine still produces
a complete outfit from local wardrobe data alone if weather/calendar/trip data
is entirely unavailable.

`WeatherRepositoryImpl.getForecast(location, date)` tries a live fetch (unless
disabled/offline-only), then an exact-date cache row, then the most recent
cache row regardless of date, then a fully-empty `isStale = true` snapshot —
it always returns a value, never throws. This is the resilience boundary that
makes "Weather is an enhancement, not a dependency" true in code, not just in
the design doc.

## Phase 8 — multi-device sync

Every syncable table (16 total) gets one `SyncEntityHandler` implementation
translating between its Room entity and a `syncId`-keyed wire payload.
Conflict resolution is deterministic and table-agnostic across all 16:
scalar fields use last-write-wins (ties favor local, to avoid perpetual
no-op re-applies), collections (seasons/tags/dress codes/palette/materials/
outfit slots) always merge as a set/keyed union regardless of whether the
parent row's scalars won or lost, and a delete only actually happens if the
local copy has no edit newer than the remote delete — otherwise it becomes
one `SyncConflictEntity`, never a silent drop. `SyncRepositoryImpl.syncNow()`
runs both an NSD responder and initiator concurrently with a 20-second
timeout (`kotlinx.coroutines.selects.select` picks whichever connects
first) rather than a persistent listener, since `SyncWorker`'s execution
model is short WorkManager bursts. See `phase-8-multi-device-sync.md` for
the full protocol, pairing, and image-sync design, and this repo's
`TECHNICAL_DEBT.md` item 13 for the honest list of what real two-device
testing would still need to confirm.

## Deliberately not implemented here (Constitution rule 6 — stated, not silently skipped)
- **Use cases** (`core:domain/usecase/`) — still empty; ViewModels (Phase 5c+) will
  need them, but nothing in any phase so far has required one yet.
- **Geocoding** — the manual-location fallback in `WeatherLocationResolver` is
  raw latitude/longitude, not a typed city name, to respect `core:network`'s
  stated single-API-client budget. See `TECHNICAL_DEBT.md` item 12.

## Phase 9 — Smart Wardrobe Intelligence & Daily Assistant

New: `WardrobeIntelligenceRepositoryImpl.kt` (implements every
`WardrobeIntelligenceRepository` method, deliberately kept to exactly 9
class-member functions — see below) plus four sibling `*Builders.kt` files
in the same `repository/` package (`GarmentAndOutfitInsightsBuilders.kt`,
`WardrobeAlertsBuilders.kt`, `ShoppingGapsAndHealthBuilders.kt`,
`CalendarConflictBuilders.kt`) holding the actual computation as top-level
functions, `CapsuleGenerator.kt` (9 named preset `val`s + a scoring rule),
and `TripRepositoryImpl.generatePackingSuggestions` (+ 5 top-level helper
functions in the same file). `styling/OutfitAssembler.kt` gained smart
outfit completion (`SlotCandidatePool`'s weather-safe-then-fallback-to-all
pattern) and multi-category accessory/jewelry selection, with the new
helpers split into a sibling `styling/SlotCandidatePool.kt` for the same
function-count reason. `StylingEngineRepositoryImpl` gained
`suggestReplacementForAccessory`/`ForJewelry`, sharing a new top-level
`bestReplacement` helper with the pre-existing `suggestReplacementForSlot`.

**Why so many small sibling files this phase**: detekt's `TooManyFunctions`
rule empirically enforces ≤10 functions per class **and** per file (moving
functions out of an over-large class only helps if the destination file
doesn't itself cross the same ceiling) — `WardrobeIntelligenceRepositoryImpl`
started at 23 class-member functions, the single largest violation this
phase produced, brought down to 9 by moving every private helper to
top-level functions spread across four sibling files, each independently
kept under 10. See `phase-9-smart-wardrobe-intelligence.md`'s "Interface
consolidation" section for the full before/after breakdown, and
`TECHNICAL_DEBT.md` item 15 for the honest list of what real fixes were
required to get there.

A genuine Dagger dependency cycle (`TripRepositoryImpl` needs
`StylingEngineRepository`; `StylingEngineRepositoryImpl` already needs
`TripRepository` to exclude packed-away garments) was broken via
`dagger.Lazy<StylingEngineRepository>` injection — `.get()` called only
inside `generatePackingSuggestions`, well after both objects exist.

No new schema this phase — every new number is a derived `StatsDao`/
`FeedbackDao` query (4 + 1, see `core:database`'s README) or a plain Kotlin
computation over data these repositories already own.

## Known gaps, recorded rather than hidden
- Restore (`BackupRestoreWorker`) is not hardened against the app being backgrounded
  mid-restore in a way that lets something else touch the database files
  concurrently — a real gap for a single-user, foreground-triggered operation, not a
  solved problem. See `TECHNICAL_DEBT.md`.
- Restore requires an app restart afterward, by design — see
  phase-5a-data-layer.md's "restart decision." The eventual Settings UI (Phase 5f)
  must tell the user this explicitly.
- `StagedImageStore` (Phase 5b) is in-memory only — a staged (not-yet-committed)
  capture's result is lost if the process dies before the user commits or discards
  it. See `TECHNICAL_DEBT.md`.
- `MlKitBackgroundRemover` (`core:image`) is a provisional choice, not a
  spike-verified one — see phase-5b-image-pipeline.md and `TECHNICAL_DEBT.md`.
- No real two-device sync has ever run — every Phase 8 component is
  verified against a simulated peer only. See `TECHNICAL_DEBT.md` item 13.
