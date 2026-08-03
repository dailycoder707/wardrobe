# :core:model

Plain Kotlin data classes — no Android import anywhere in this module, enforced by
it being a `kotlin("jvm")` module rather than an Android library (see
`build.gradle.kts`). This is the module that makes a future Kotlin Multiplatform
split (iOS/Desktop) additive instead of a rewrite (Phase 1, Section 30).

## Packages
| Package | Holds |
|---|---|
| `garment/` | `Garment`, `Category`, `Color`, `Material`, `Brand`, `Tag`, `Season`, `DressCode`, `ImageMetadata`, plus Phase 5b's pre-persist image-pipeline types (`ImagePipeline.kt`): `ImageImportSource`, `ProcessingStage`, `NormalizedRect`, `QualityCheck`/`QualityReport`/`QualityVerdict`, `BackgroundRemovalStatus`, `ImageVariant`, `StagedImage` |
| `outfit/` | `Outfit` (Phase 5d: `isFavorite`/`isArchived`/`notes`/`mood`/`seasons`/`dressCodes`/`tagIds`), its garment/layer composition, `OutfitSlot` (the nine builder slots, ordinal-based `slotIndex`, plus Phase 6's `classify(categoryName)` keyword heuristic for bucketing a garment by slot), `OutfitFilter`/`OutfitSort`, `OutfitSlotMapping.kt` (Phase 6 — `Outfit.garmentsBySlot()`), `Occasion.impliedDressCode()` (Phase 7 — a keyword heuristic mapping a calendar occasion name to a `DressCode`, same "honest heuristic" pattern as `OutfitSlot.classify`) |
| `wear/` | `WearEvent`, `WearEventStatus` (Phase 5d: `PLANNED`/`WORN` — the schema decision that lets Outfit Scheduling coexist with `WearEvent`'s original retrospective-log meaning; see `phase-5d-wardrobe-stylist.md`) |
| `styling/` | `StyleRule`, `Feedback`, `ScoredOutfit`, `SuggestionContext`, `RecommendationPreferences` (Phase 6 — the 14-field Stylist Preferences model), `AccessoryCategory` (Phase 6 — a finer belt/hair-accessory/sunglasses/scarf keyword classifier than `OutfitSlot.ACCESSORIES`'s catch-all), `RecommendationRunDiagnostics` (Phase 7 — `WeatherSource`/cache age/rules-evaluated-and-applied counts/planned-outfit-used flag/context notes, backing the Developer Panel) |
| `profile/` | `StyleProfile`, `PersonalizationSettings` + `GreetingStyle` (added Phase 5a — see `greetingText()`, the one function every greeting anywhere in the app must render through; never a hardcoded name) |
| `trip/` | `Trip`, `PackingListItem` |
| `wishlist/` | `WishlistItem` |
| `weather/` | `WeatherSnapshot` (extended Phase 7 with `currentTempC`/`feelsLikeC`/`humidityPercent`/`uvIndex`/`condition`), `WeatherCondition` (Phase 7 — Sunny/Cloudy/Rain/Storm/Fog/Snow/Unknown), `WeatherPreferences`/`TemperatureUnit` (Phase 7), `WeatherDisplay.kt` (Phase 7 — `updatedAtLabel`/`headline`/`displayTemp`, the presentation logic behind "Updated 2 hours ago"/"No weather available") |
| `stats/` | `UsageStats`, `CostPerWearEntry`, `ClosetGap` |
| `common/` | Shared value types with no natural single owner (e.g. money/date-range wrappers), added only when a second model actually needs one — not pre-built speculatively |

**Implemented in Phase 3** — every type in the table above exists now, plus
`common/Ids.kt` (one `@JvmInline value class` per entity id), `common/Money.kt`, and
`common/DateRange.kt`. See `phase-3-persistence.md` for the exact field-by-field
design. `Garment`, `WearEvent`, `Feedback`, and `PackingListItem` all validate their
own invariants in an `init` block (e.g. `WearEvent`'s garmentId-XOR-outfitId rule) so
an invalid domain object can't be constructed anywhere in the app, not just at the
database boundary.

**Rule for this module**: if a type here ever needs `android.*` or `androidx.*` to
compile, that's a sign the type belongs in `core:domain` or `core:data` instead,
not a reason to add an Android dependency to this module.

## Phase 8 addition — `sync/`

`PairedDevice`, `SyncState`/`SyncStatusSnapshot`, `SyncEntityType` (the 16
syncable table names as an enum), `ConflictReason`/`ConflictResolution`/
`SyncConflict`, `SyncOutcome`/`SyncHistoryEntry`, `SyncPreferences`
(auto-sync/Wi-Fi-only/charging-only). Plain data classes like everything
else here — no Android dependency, even though every consumer of them
(`core:sync`, `core:data`, `feature:settings`) is Android-only. See
`phase-8-multi-device-sync.md` for the full pairing/sync/conflict design.

## Phase 9 addition — `intelligence/`

`GarmentInsights`, `OutfitInsights` (+ `OutfitRating` — derived from Phase
6's `Feedback` votes, never a new rating schema; `ComfortLevel`/
`WarmthLevel`), `WardrobeAlerts` (`ForgottenGarment`/`ForgottenBucket`,
`OverusedGarment`, `NeverWornGarment`/`NeverWornReason` — bundled under one
type so `WardrobeIntelligenceRepository` stays under detekt's
`TooManyFunctions` ceiling), `ShoppingGapSuggestion`, `DuplicateGroup`,
`CapsuleType`/`CapsuleSuggestion`, `WardrobeHealthScore`, `CalendarConflict`/
`ConflictReason`, `DailyBrief`. `styling/JewelryCategory` (new, mirrors the
existing `AccessoryCategory` pattern exactly). `weather/SeasonMapping.kt`
(`LocalDate.toMeteorologicalSeason()` — Northern-hemisphere-only, disclosed
heuristic). `stats/Stats.kt`'s `UsageStats` gained `categoryWearCounts`/
`garmentVersatility`/`topGarmentCombinations` fields (folded in rather than
three new `StatsRepository` methods, for the same `TooManyFunctions`
reason). `styling/ScoredOutfit.kt` gained `accessoryItems`/`jewelryItems`
(presentation-only — see `phase-9-smart-wardrobe-intelligence.md` for why
these never get written into `outfit_garments`). See that phase doc for the
full design and `TECHNICAL_DEBT.md` item 15 for this phase's own gaps.
