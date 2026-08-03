# :core:domain

Repository interfaces and use cases. Also a plain `kotlin("jvm")` module — depends
only on `core:model` and `kotlinx-coroutines-core` (for the `Flow`-returning
repository signatures). No Room, no Retrofit, no Android Context: this module
defines *what* the app can do, never *how*.

## Packages
| Package | Holds |
|---|---|
| `repository/` | Interfaces defined in Phase 3, **implemented in `core:data`**: `GarmentRepository`, `OutfitRepository`, `WearEventRepository`, `StyleRuleRepository`, `StylingEngineRepository` (implemented Phase 6, extended Phase 7 with `lastRunDiagnostics()`, Phase 9 with `suggestReplacementForAccessory`/`ForJewelry`), `StylistPreferencesRepository` (new Phase 6), `StatsRepository`, `WishlistRepository`, `TripRepository` (Phase 9 added `generatePackingSuggestions`), `WeatherRepository` (implemented Phase 7 — see below), `WeatherPreferencesRepository`/`WeatherRefreshScheduler` (new Phase 7), `BackupRepository`, `StyleProfileRepository`, `PersonalizationRepository` (added Phase 5a — see `core:model`'s `PersonalizationSettings`), `ImageRepository` (added Phase 5b), `ClosetPreferencesRepository` (added Phase 5c — DataStore-backed sort/grid-density/recent-searches, consumed only by `feature:closet`'s `ClosetViewModel`), `DevicePairingRepository`/`SyncRepository`/`SyncScheduler`/`SyncPreferencesRepository` (new Phase 8 — see below), `WardrobeIntelligenceRepository` (new Phase 9 — see below), plus the small reference-data repositories in `TaxonomyRepositories.kt` |
| `usecase/garment/` `usecase/outfit/` `usecase/wear/` `usecase/styling/` `usecase/stats/` `usecase/trip/` `usecase/wishlist/` `usecase/backup/` | Still not implemented — one class per use case, one `operator fun invoke(...)`, listed in Phase 1 Section 12. Every phase's ViewModels combine repository flows directly instead; a use-case layer was deliberately not introduced since the combining logic is screen-specific, not yet shared across multiple ViewModels (see `phase-5c-wardrobe-experience.md`) |
| `styling/` | `ColorHarmony` (new Phase 6 — promoted here from `feature:outfits/common/` so the recommendation engine in `core:data` and Outfit Builder's own display label could share one implementation; zero Android dependencies, verified before moving) |

`core:data` implements every interface here, bound via Hilt `@Binds` —
`feature:*` modules only ever see this module's interfaces, never `core:data` or
`core:database` directly. `WeatherRepository` was the last unimplemented
interface; Phase 7's `WeatherRepositoryImpl` (wrapping `core:network`'s
`WeatherProvider` plus the `weather_cache` Room table) is its first
implementation. The use-case layer is still empty — revisit once a second
ViewModel needs the same orchestration logic, not speculatively.

**Phase 7**: `WeatherRepository` gained
`getForecastForConfiguredLocation(date)` — resolves the device-or-manual
location internally (via `core:data`'s `WeatherLocationResolver`) so callers
never need to know how location is configured; both
`StylingEngineRepositoryImpl` and `HomeViewModel` use only this one method.
`StylingEngineRepository` gained `lastRunDiagnostics()` (non-suspend — reads a
`@Volatile` field populated by the most recent `suggestOutfits` run) backing
the Developer Panel's weather-source/cache-age/rules-applied/planned-outfit-
used/context-notes rows. Per Constitution rule 12 (added this phase): every
context source these interfaces expose — weather, calendar, trips,
availability — must only ever *refine* what `StylingEngineRepository` returns;
it must still produce a complete outfit from local wardrobe data alone if
every context source is unavailable.

**Phase 8**: `DevicePairingRepository` (`generatePairingOfferImage()`/
`completePairing(scannedQrText)`/`observePairedDevices()`/`unpairDevice()`),
`SyncRepository` (`observeStatus()`/`syncNow()`/`observeUnresolvedConflicts()`/
`resolveConflict()`/`observeHistory()`), `SyncScheduler`
(`reschedule(wifiOnly, chargingOnly)`/`syncNow()`), and
`SyncPreferencesRepository` are new — every implementation lives in
`core:data`, and the actual protocol/crypto/pairing/discovery machinery
lives in the new `core:sync` module (a sibling to `core:network`, not
depended on by any `feature:*` module except `feature:settings`, which
needs `core:sync`'s `PairingQrCodec` directly to decode camera frames — a
narrow, documented layering exception, the same shape as the Developer
Panel's existing direct `core:image` dependency).

**Phase 9**: `WardrobeIntelligenceRepository` (new, 9 methods —
`observeGarmentInsights`, `observeOutfitInsights`, `observeWardrobeAlerts`
(consolidating forgotten/overused/never-worn — see below),
`observeShoppingGaps`, `observeDuplicateGroups`, `suggestCapsule`,
`observeWardrobeHealthScore`, `observeCalendarConflicts`, `buildDailyBrief`)
is grouped separately from `StatsRepository` because it backs per-item/
rule-based intelligence (Garment/Outfit Detail insights, Home's attention
cards, the Daily Brief), not the window-based Style Insights dashboard.
Detekt's `TooManyFunctions` rule (empirically ≤10 per interface, confirmed
again this phase) is why `observeForgottenGarments`/`observeOverusedGarments`/
`observeNeverWornGarments` are one method returning a `WardrobeAlerts`
bundle rather than three — Home's "Items Needing Attention" card always
needed all three together anyway. The same pressure is why `StatsRepository`
did **not** gain three planned new methods
(`observeCategoryWearCounts`/`observeGarmentVersatility`/
`observeTopGarmentCombinations`) — those became `UsageStats` fields instead
(`core:model`). `StylingEngineRepository` gained two narrow additive
methods, `suggestReplacementForAccessory`/`suggestReplacementForJewelry`
(same shape as the pre-existing `suggestReplacementForSlot`), needed because
`OutfitSlot.ACCESSORIES`/`JEWELRY` alone can no longer disambiguate "replace
just the ring" from "replace just the necklace" now that a slot can carry
several concurrent presentation-only picks (see `phase-9-smart-wardrobe-
intelligence.md`'s constraint section). `TripRepository` gained
`generatePackingSuggestions(tripId)` — pure generation, the caller decides
whether to `savePackingList` the result, preserving the pre-existing
"replace wholesale" contract.

`GarmentFilter` (`core:model`) gained Phase 5c fields (`isFavorite`, `colorId`,
`materialId`, `tagId`, `priceMin`/`priceMax`, `neverWorn`,
`recentlyWornWithinDays`) split across three layers by where each can actually
be evaluated — SQL-level (`GarmentDao`), in-memory (`GarmentRepositoryImpl`,
for fields needing joined color/material/tag/price data), and ViewModel-level
(`ClosetViewModel`, for fields needing a `StatsRepository` join
`GarmentRepository` doesn't own) — see that class's own doc comment for the
exact split. `GarmentSort`/`GarmentSortField`/`SortDirection` (new) are applied
client-side in `ClosetViewModel` for the same reason.

`ImageRepository` (Phase 5b) additionally exposes `ImageProcessingProgress`, a
`Flow`-based progress model mirroring `BackupProgress`/`RestoreProgress`'s shape
— the WorkManager-backed image pipeline (`core:image`, `core:data`) surfaces
progress through the same pattern rather than a new one.

**Phase 5d**: `OutfitRepository` gained `observeOutfits(filter: OutfitFilter)`/
`observeOutfit(id)`/`setFavorite`/`setArchived` (Outfit Builder, Saved Looks,
Outfit Detail); `WearEventRepository` gained `updateWear`/`clearDay`/
`duplicateDay` (Calendar's reschedule/confirm-worn, clear day, duplicate day)
on top of Phase 3's `logWear`/`deleteEvent`/`observeEvents`. No use case was
added for either — `feature:outfits`/`feature:calendar`'s ViewModels combine
these repository flows directly, the same choice Phase 5c made, for the same
reason: the orchestration logic is screen-specific, not yet shared across
multiple ViewModels.
