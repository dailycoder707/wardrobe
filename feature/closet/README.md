# :feature:closet

Home, Closet browsing, Garment Detail, Search, Filters, Sorting, and Edit Garment
(Phase 5c) — plus, from Phase 5b, the only feature module that depends on
`core:image` (the debug-only Developer Panel reads `ImageFileStore` directly for
cache diagnostics).

## Packages
| Package | Contents |
|---|---|
| `home/` | Home screen — greeting/date, Quick Actions, Wardrobe Summary, Continue Editing/Recently Added/Recently Worn. Phase 7 added the "personal assistant" experience: a weather line (`WeatherLine`, headline + "Updated N ago"/"No weather available" label) and a recommendation preview card (`RecommendationPreviewCard`, tapping it opens the full Recommendations screen), both driven by a new `HomeViewModel.assistantState: StateFlow<HomeAssistantUiState>` kept deliberately separate from `uiState`'s own reactive `combine` chain — see "Phase 7 additions" below. Phase 8 added `HomeSyncConfirmation.kt`'s `SyncConfirmationLine` — the plain-language "Wardrobe updated just now" line, extracted to its own file to keep `HomeScreen.kt` under detekt's `TooManyFunctions`/`LongMethod` thresholds once it grew a fourth reason to change |
| `closet/` | Closet grid (adaptive columns, pinch-to-zoom density), search, filters (bottom sheet), sorting (bottom sheet), multi-select, `ClosetSelectionController` |
| `detail/` | Garment Detail — hero image gallery, full-screen viewer, metadata, favorite/edit/delete, wear history, landscape two-pane layout |
| `edit/` | Garment attribute editor (name, category, brand, color, size, price, condition, seasons, dress codes, tags, notes) |
| `debug/` | `DeveloperPanelScreen`/`DeveloperPanelViewModel`/`ClosetDiagnostics` — debug-build-only, never registered in `:app`'s release nav graph. Phase 5d added an "Outfits & Calendar" and an "Outfit Builder" section (saved-outfit/planned-wear-event counts, recent saves, undo/redo stack sizes) reading `OutfitRepository`/`WearEventRepository` and `core:ui`'s `OutfitBuilderDiagnostics`; the repositories the ViewModel needs are grouped into an injectable `DeveloperPanelRepositories` bundle rather than an 11-parameter constructor. Phase 7 extended "Personal Wardrobe Stylist" with five rows sourced from `RecommendationRunDiagnostics`: weather source, weather cache age, rules applied, planned-outfit-used, context-note count; also added `"WeatherRefreshWorker"` to the tracked-worker tag list. Phase 8 added a "Sync Diagnostics" section (connected device, pending uploads, bytes transferred, last success/failure, conflicts resolved, queue size, `SyncWorker` status) reading `SyncRepository` directly — no `core:ui` diagnostics bridge needed, since sync state is already durably persisted in Room rather than ephemeral like Stats/Recommendations' in-memory diagnostics singletons |
| `common/` | `Garment.toTileUiModel()` — the shared mapper Home and Closet both use to build `GarmentTileUiModel` |
| `navigation/` | This module's type-safe routes (`HomeRoute`, `ClosetRoute`, `GarmentDetailRoute`, `EditGarmentRoute`, `DeveloperPanelRoute`) |
| `browse/`, `capture/`, `style/` | Reserved, empty — capture (camera pipeline UI) and "Style this item" are out of scope for Phase 5c; see `phase-5c-wardrobe-experience.md` |

See `phase-5c-wardrobe-experience.md` for architecture, state management, testing
strategy, and known limitations. `TECHNICAL_DEBT.md` item 8 tracks this phase's
deliberate simplifications.

## Phase 7 additions

`HomeViewModel`'s constructor was at detekt's `LongParameterList` threshold
(10 params) even before this phase; rather than push it over, the pre-existing
insight-related repositories were bundled into a new `HomeInsightsRepositories`
class and the two new Phase 7 dependencies (`WeatherRepository`,
`StylingEngineRepository`) into `HomeAssistantRepositories` — the same "bag of
repositories" pattern `DeveloperPanelRepositories`/`StylingContextDependencies`
already established elsewhere in this codebase.

`assistantState` is computed once in `init { viewModelScope.launch { ... } }`,
not folded into `uiState`'s reactive `combine` chain, because both a weather
fetch and a full recommendation-engine run are one-shot suspend calls, not
`Flow`-backed data the rest of Home already observes reactively — and neither
should ever block the rest of Home from rendering. This is Constitution rule
12 (Phase 7's "context refines, never replaces" rule) made concrete at the UI
layer, not just the recommendation engine.

## Phase 8 additions

`HomeAssistantUiState` gained `syncConfirmationMessage: String?`;
`HomeViewModel.observeSyncCompletion()` watches
`SyncRepository.observeStatus().map { it.lastSyncAt }.distinctUntilChanged()`,
drops the value already present when Home first loads (so a sync that
finished before this screen opened doesn't retroactively announce itself),
and shows "Wardrobe updated just now" for four seconds. `HomeAssistantRepositories`
gained `syncRepository` as a third constructor parameter — the bag pattern
absorbing a new Phase 8 dependency the same way it already absorbed Phase
7's two.

## Phase 9 additions

`GarmentDetailScreen`/`GarmentMetadataSection` now render real derived
stats from `WardrobeIntelligenceRepository.observeGarmentInsights` — Last
Worn/Total Wears/First Worn/Cost Per Wear/Rotation Score/Wear Frequency
(`GarmentWearStatsRow`), Season Usage chips, and an Availability section
with a Packing Status line and an in-Laundry `Switch` wired to the
previously-dead `GarmentRepository.setInLaundry` path — this is the first
UI exposure for laundry status anywhere in the app. `GarmentDetailViewModel`
now injects `WardrobeIntelligenceRepository` instead of raw `StatsRepository`
for this combine chain. These five new derived-stat composables live in a
new sibling `GarmentInsightsSection.kt` (mirroring `feature:outfits`'
`OutfitInsightsSection.kt`), split out to keep `GarmentMetadataSection.kt`
under detekt's `TooManyFunctions` ceiling.

`HomeAssistantRepositories` gained `wardrobeIntelligenceRepository`/
`tripRepository` (5 params total, still under detekt's constructor
threshold). `HomeAssistantUiState` gained `todaysOccasionName`,
`wardrobeHealthScore`, `rotationScore`, `itemsNeedingAttentionCount`,
`upcomingTripReminder`, `laundryReminderCount`. `loadAssistantState()` now
calls `wardrobeIntelligenceRepository.buildDailyBrief(today, greeting)` once,
reusing its internal weather fetch for both the weather card and the
recommendation preview rather than two separate calls. `HomeIntelligenceCards.kt`
(new sibling file, mirroring the Phase 8 `HomeSyncConfirmation.kt`
precedent) holds the five new composables (`TodaysOccasionLine`,
`WardrobeHealthScoreCard`, `AttentionItemsCard`, `UpcomingTripReminderLine`,
`LaundryReminderLine`) — split out purely to keep `HomeScreen.kt` under
detekt's `LongMethod`/`TooManyFunctions` thresholds, the same reason
`HomeSyncConfirmation.kt` exists. `HomeScreen`/`HomeContent` gained
`onOpenTrips`, navigating to `feature:trips`' new `TripsRoute`.

**Naming note**: the new "Wardrobe Health Score" card is a different
concept from `feature:stats`' pre-existing "Wardrobe Health" advisory-cards
screen (Phase 5e) — see `TECHNICAL_DEBT.md` item 15.
