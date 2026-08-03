# :feature:stats

Wardrobe Intelligence: Insights, Wardrobe Story, Wardrobe Health, plus the
Home Insights row surfaced from `feature:closet`. Every number here is a
derived query over `WearEvent`/`Garment`/`Outfit` via `core:domain`'s
`StatsRepository` — there is no separate "Statistics" table holding
precomputed numbers as a second source of truth (Phase 1 Section 0 pushback
#1 and Section 9; reconfirmed in `phase-5e-wardrobe-intelligence.md`).

Full architecture, query strategy, performance/visualization decisions, and
known limitations: see `phase-5e-wardrobe-intelligence.md` at the repo root.

## Packages
| Package | Contents |
|---|---|
| `insights/` | The Insights screen — usage overview, favorites, season/dress-code/weekday distribution charts, usage heatmap, monthly/weekly wear, and eight tap-to-navigate lists (Most/Least Worn, Waiting to Be Worn, Best Value, Missing an Outfit, Looks You Haven't Worn Yet, Go-To Looks, Recent Activity) |
| `story/` | Wardrobe Story — natural-language cards built from the same derived data, never a persisted narrative |
| `health/` | Wardrobe Health — friendly, advisory-only observations (Color Balance, Reliable Favorites, Ready for a Turn, Favorite Brands, Recent Purchases to Try, Off to a Great Start, Unexplored Categories) |
| `common/` | Shared `InsightItemUiModel`/`InsightSectionCard`/chart composables (`ProgressRing`, `BarChart`, `CalendarHeatmap`, `InsightRow`), `StatsTiming`/`HeatmapBucketCache` instrumentation |
| `navigation/` | `InsightsRoute` (nav-dock top-level), `WardrobeStoryRoute`/`WardrobeHealthRoute` (reached only from Insights) |

Out of scope here, deliberately (see the phase doc): the AI Styling Engine,
weather, shopping, wishlist, trips, and anything requiring network access.
Everything in this module works fully offline.

## Phase 9 additions

`insights/InsightsUiState.kt` gained four new lists (favorite combinations,
most/least versatile garments, cost-per-wear least-valuable) and five new
fields (per-slot favorite footwear/bag/jewelry/accessory names, unused-
wardrobe percentage) — all sourced directly from `UsageStats`'s three new
Phase 9 fields (`categoryWearCounts`/`garmentVersatility`/
`topGarmentCombinations`, `core:model`) rather than new `StatsRepository`
methods (see that repository's README for why). `InsightsViewModel`'s
combine chain stayed at its original shape (`referenceDataFlow`,
`usageStatsFlow`, `heatmapFlow`, `listsFlow`, `activityFlow`) — an
intermediate `ExtendedSourceData`/`extendedFlow` construct built during
implementation was removed once the three new fields moved onto `UsageStats`
directly, since the extra combine layer was no longer needed.
`InsightsBuilders.kt`'s `buildLists` now takes a `usage: UsageStats`
parameter (rather than separate `versatility`/`combinations` lists) to stay
under detekt's `LongParameterList` threshold; its pre-existing heatmap-
bucketing helpers moved to a new sibling `ChartBucketing.kt` to make room
without crossing the file-level `TooManyFunctions` ceiling.

**Naming note**: this module's pre-existing `health/` "Wardrobe Health"
screen (Phase 5e — qualitative, advisory-only cards) is conceptually
distinct from Phase 9's new Home "Wardrobe Health Score" card
(`feature:closet`) — see `TECHNICAL_DEBT.md` item 15.
