# :feature:outfits

Outfit Builder (manual "Build a Look" with drag-and-drop and tap-to-fill),
Saved Looks (the lookbook grid), and Outfit Detail (Phase 5d) — restyle,
duplicate, archive, favorite, wear history, and delete-with-confirmation all
live here. Phase 6 added the rule-based styling engine's own UI:
Recommendations (complete-outfit suggestions with Quick Actions), Stylist
Preferences, and a 2D layered Outfit Preview — reached from a top-bar action
on Saved Looks, not a separate module.

## Packages
| Package | Contents |
|---|---|
| `builder/` | `OutfitBuilderScreen`/`OutfitBuilderViewModel` — the canvas (`OutfitCanvas`), tap-to-fill/replace (`GarmentPickerSheet`), metadata sheet (`OutfitMetadataSheet`), undo/redo, drag-and-drop |
| `list/` | Saved Looks grid (`SavedLooksScreen`/`SavedLooksViewModel`), `OutfitCard`'s flat-lay mosaic, search/filter/sort |
| `detail/` | `OutfitDetailScreen`/`OutfitDetailViewModel` — slot grid, metadata, wear-count/last-worn stats, wear history, restyle/duplicate/archive/delete |
| `recommendations/` | `RecommendationsScreen`/`RecommendationsViewModel` — complete-outfit suggestions from `core:domain`'s (now implemented) `StylingEngineRepository`, natural-language explanations, Quick Actions (Wear Today, Schedule, Save, Favorite, Replace a slot, Generate Another Look). Phase 7 added a "Weather" top-bar action next to "Preferences", navigating to `feature:settings`'s `WeatherSettingsRoute`; Phase 8 added a "Sync" action navigating to `WardrobeSyncRoute` — this module has no Weather Settings or Wardrobe Sync UI of its own, only the entry points (`RecommendationsScreen` is `@Suppress("LongParameterList")` now, the same precedent `feature:closet`'s `HomeScreen` already established for composables whose parameter list is entirely navigation callbacks) |
| `preferences/` | `StylistPreferencesScreen`/`StylistPreferencesViewModel` — the "Include Shoes/Bags/Jewelry/.../Prefer Favorites/Avoid Recently Worn/..." toggle list, persisted via `StylistPreferencesRepository` |
| `preview/` | `OutfitPreviewScreen`/`OutfitPreviewViewModel` — 2D layered mannequin preview (Canvas silhouette + Phase 5b garment cutouts), pan/zoom, per-item swap |
| `common/` | `Garment.toTileUiModel()` (this module's own copy — modules don't depend on each other). `ColorHarmony` moved to `core:domain` in Phase 6 so the recommendation engine could share it. |
| `navigation/` | This module's type-safe routes (`SavedLooksRoute`, `OutfitBuilderRoute`, `OutfitDetailRoute`, `RecommendationsRoute`, `StylistPreferencesRoute`, `OutfitPreviewRoute`) |

See `phase-5d-wardrobe-stylist.md` for the Outfit Builder's own architecture,
workflow, and known limitations (`TECHNICAL_DEBT.md` item 9),
`phase-6-personal-wardrobe-stylist.md` for the recommendation engine,
Stylist Preferences, Outfit Preview, and this phase's own known limitations
(`TECHNICAL_DEBT.md` item 11), and `phase-7-context-aware-assistant.md` for
how weather/calendar/trip/availability context now refines what
`RecommendationsScreen` shows (`TECHNICAL_DEBT.md` item 12) — this module's
own code changed only in `RecommendationsViewModel.generate()`'s call to
`RecommendationDiagnostics.recordGeneration`, now passing the richer
`RecommendationRunDiagnostics` bundle; the context-refinement logic itself
lives entirely in `core:data`.

## Phase 9 additions

`OutfitDetailScreen` gained an `OutfitInsightsSection` (average rating —
derived from Phase 6's `Feedback` votes, "Not yet rated" when none exist;
estimated comfort/warmth; suitable seasons/dress codes/occasions/weather;
rotation priority), backed by `OutfitDetailViewModel`'s new
`wardrobeIntelligenceRepository` dependency.

`RecommendationsScreen` gained an "Also consider wearing" section
(`AlsoConsiderWearingSection.kt`) rendering the new
`RecommendedOutfitUiModel.accessoryItems`/`jewelryItems` — the multi-category
accessory/jewelry breakdown `ScoredOutfit` now carries (see `core:model`'s
README for why these are presentation-only and never written into
`outfit_garments`) — plus a "Capsules"/"Duplicates" text-button row
navigating to two brand-new screens: `capsules/` (a chip selector across
the 9 `CapsuleType` presets, showing the generated item set + explanation)
and `duplicates/` (a grouped list of `DuplicateGroup`s — pure surfacing, no
delete action, per the brief).
