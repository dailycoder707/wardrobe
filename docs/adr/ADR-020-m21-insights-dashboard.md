# ADR-020: Insights Dashboard (M21, Parts 11–12)

**Status**: Accepted (implementation milestone, added 2026-08-08, extending
`feature:stats`'s existing Phase 5e/9 Insights Dashboard, M19's
recommendation-provenance conventions, and M20's calendar/context work)

## Context

M21's brief read as if an Insights Dashboard did not yet exist — asking
for a "dedicated Insights screen," a wardrobe-overview model, wear
statistics, distribution charts, favorites, seasonal/occasion coverage,
cost/value insights, and AI-generated insight text, each described as if
being built from nothing. Per this milestone's own Phase 0 instruction,
three parallel research passes inspected `feature:stats`,
`StatsRepository`/`WardrobeIntelligenceRepository`/garment metadata, and
the AI-capability/preferences/tablet/navigation surfaces *before* any
code was written. That inspection found **a complete, already-shipping
Insights Dashboard** (`feature:stats`, built across Phase 5e and Phase 9),
already one of exactly five bottom-navigation destinations, covering
roughly 90% of the brief. This ADR records what already existed, the
real gaps M21 filled, and the AI-insights decision this milestone made
instead of adding new AI infrastructure.

## What already existed (not rebuilt)

- **The Insights screen itself** — `InsightsScreen.kt`/`InsightsViewModel.kt`,
  reached via `InsightsRoute` (already nav-dock top-level), with a period
  selector over `StatsWindow { ONE_MONTH, SIX_MONTHS, ONE_YEAR, ALL_TIME }`.
- **Wardrobe Overview** (Part 3) — `UsageOverviewSection`: total active
  garments, garments worn at least once, usage percent, all from real
  `StatsRepository.observeUsageStats(window)` data.
- **Wear Insights** (Part 4) — `ActivityChartSections`: usage heatmap
  (`CalendarHeatmap`, the one chart with per-cell accessibility
  `contentDescription`), monthly/weekly wear bar charts, all derived from
  `observeWearHeatmap`/`observeUsageStats` — never inferred from
  recommendations or calendar plans.
- **Underused Wardrobe** (Part 5) — the "Waiting to Be Worn" list, backed
  by `observeDormantItems(window)`, with an honest "nothing wrong with
  these" framing rather than a guilt-driven one.
- **Category/Style Distribution** (Part 6) — `DistributionSections`:
  season, dress-code, and weekday-vs-weekend bar charts from real
  `UsageStats` fields.
- **Favorites Insights** (Part 7) — `FavoritesSection`: favorite colors,
  brands, categories, and per-slot favorites (footwear/bags/jewelry/
  accessories), entirely absent when nothing qualifies rather than
  showing an empty card. Deliberately distinct from "most worn" (a
  favorite is a signature-frequency signal, not a wear count).
- **Cost/Value Insights** (Part 9) — "Best Value" and "Highest Cost Per
  Wear" lists from `observeCostPerWear()`, whose existing divide-by-zero
  guard (`price?.takeIf { wearCount > 0 }?.div(wearCount)`) was already
  correct: `null`, never `0` or a fabricated number, whenever price is
  missing or the item has never been worn.
- **Actionable Insights** (Part 11) — every one of the dashboard's ~12
  tap-through lists (`InsightRow`) already navigates to a real screen
  (`GarmentDetailRoute`, `OutfitDetailRoute`, `ClosetRoute`,
  `CalendarRoute`, `DuplicateGarmentsRoute`, `CapsulesRoute`,
  `WardrobeStoryRoute`, `WardrobeHealthRoute`) — no dead buttons.
- **Wardrobe Story and Wardrobe Health** — separate, already-built
  screens reached only from within Insights (`WardrobeStoryRoute`/
  `WardrobeHealthRoute`), narrative and advisory surfaces distinct from
  this dashboard's own charts. `feature/stats/README.md` already
  documents that there is no separate "Statistics" table acting as a
  second source of truth.
- **Home integration** (Part 14) — `HomeInsightsSection`'s `SectionHeader(title
  = "Your Style", actionLabel = "See all", onAction = onOpenInsights)` and
  `AttentionItemsCard` both already navigate into this exact screen
  (`WardrobeNavHost.kt`'s `onOpenInsights = { navController.navigate(InsightsRoute) }`).
  Its own doc comment already frames it as "a highlight reel, not a
  second Insights screen" — Part 14's requirement was already satisfied
  by real data; no new Home card was added.
- **Accessibility groundwork** (Part 15) — `BarChart` already renders
  each bar's numeric value and label as real `Text` composables (not
  color-only marks), so chart data is already available as text to a
  screen reader without a separate content-description layer; only
  `CalendarHeatmap`'s color-only cells needed (and already had) explicit
  per-cell `contentDescription`.

None of the above was rebuilt or touched beyond what's listed under
"What M21 actually added" below.

## What M21 actually added

### 1. Wardrobe Mix Distribution (Parts 6 and 8) — genuinely new data

Material fiber-content, fabric/weave construction, and occasion coverage
had no existing query anywhere — `Garment.materials`/`.fabrics`/
`.occasionIds` (M19 additions) were tagged on garments but never
aggregated. Three new `StatsDao` queries follow the exact composition-count
shape Phase 9 already established for `observeActiveGarmentCountBySeason`/
`ByDressCode`/`ByCategory` — never wear-based:

- `observeActiveGarmentCountByMaterial`/`ByFabric` — `INNER JOIN` (a
  material or fabric nobody owns has nothing to report).
- `observeActiveGarmentCountByOccasion` — `LEFT JOIN` from the `occasions`
  reference table, so a real occasion with **zero** active garments still
  appears at count 0 — the coverage gap is itself the useful signal, the
  same reasoning `ClosetGap` already uses for season/dress-code gaps.
- `observeActiveGarmentCountWithoutOccasion` — how many active garments
  have no occasion tagged at all, disclosed honestly (Part 6's "honest
  about missing metadata") rather than letting the occasion chart imply
  full coverage.

Surfaced as a new `WardrobeMixSection` (Material Mix / Fabric Mix /
Occasion Coverage bar charts), each individual chart absent entirely
when its list is empty — never an empty chart implying a real
measurement (Part 12) — plus an honest "N items have no occasion
tagged" disclosure line when that count is nonzero.

### 2. A real bug fix: archived garments leaking into active coverage

While building the material/fabric/occasion queries against the
established season/dress-code template, tracing `GarmentRepositoryImpl.setStatus`
confirmed it never cleans up a garment's cross-reference rows when its
status changes away from `ACTIVE`. The existing `observeActiveGarmentCountBySeason`/
`ByDressCode` queries used `COUNT(DISTINCT gs.garmentId)` — the cross-ref
table's own column, present regardless of whether the `LEFT JOIN garments
g ON g.id = gs.garmentId AND g.status = 'ACTIVE'` condition actually
matched. An archived garment's season/dress-code tag was silently still
counted as active coverage, capable of hiding a genuine `ClosetGap`. Both
queries now use `COUNT(DISTINCT g.id)` (correctly `NULL`, excluded by
`COUNT`, whenever the compound join condition fails) — the same pattern
the new material/fabric/occasion queries were written with from the
start. Two regression tests added
(`StatsRepositoryImplTest`: "an archived garment's season/dress code no
longer counts toward active coverage").

### 3. A real bug fix: the period selector didn't reach "Waiting to Be Worn"

`InsightsViewModel.listsFlow` called `statsRepository.observeDormantItems(StatsWindow.ALL_TIME)`
— hardcoded, ignoring the `window` variable already in scope from the
enclosing `windowState.flatMapLatest { window -> ... }`. Every other
window-scoped section respected the selector; this one silently didn't.
Fixed to `observeDormantItems(window)`, with a regression test proving
the dormant-items query is re-requested when the window changes.

### 4. Honest "all time" disclosure instead of a functional change

Most Worn, Least Worn, Best Value, and Highest Cost Per Wear are all
genuinely lifetime metrics: `observeCostPerWear()` has no window
parameter anywhere in its DAO query, unlike `observeUsageStats(window)`.
Making them window-scoped would be a materially larger, riskier
repository-signature change touching M19/M20 code that already consumes
`observeCostPerWear()`, for a benefit the brief didn't actually ask for
(Part 2 only requires that period-dependent statistics *consistently*
respect the selector — it does not require every statistic to become
period-dependent). The chosen fix is honest, not evasive: each of these
four list subtitles now ends in "...all time." so the period selector's
presence never implies these numbers reset with it.

### 5. Tablet/large-screen layout (Part 16)

No shared `WindowSizeClass` utility exists anywhere in this codebase
(confirmed by inspection) — the established convention is the inline
`BoxWithConstraints { val isLandscape = maxWidth > maxHeight; ... }`
idiom used identically by `feature:calendar`'s `CalendarScreen.kt`,
`feature:closet`'s `GarmentDetailScreen.kt`, and `feature:outfits`'
`OutfitBuilderScreen.kt`. Insights' content is a single linear reading
flow of cards and charts, not a list-plus-detail split, so rather than a
row-split it uses a width-capping centered column
(`WIDE_LAYOUT_MIN_DP = 840`, `InsightsContent`/`InsightsList` in
`InsightsScreen.kt`) — on a wide/landscape pane the reading column caps
at a fixed width instead of stretching every card and chart edge to
edge. Verified by code inspection and unit-testable-state only; not
exercised on physical tablet hardware this pass (see the final report's
device checklist).

### 6. AI Insights (Part 10) — decision made against adding a new AI capability

`AiCapability` has exactly five values (`GARMENT_EXTRACTION`,
`GARMENT_RECONSTRUCTION`, `GARMENT_METADATA`, `OUTFIT_STYLING`,
`VIRTUAL_TRY_ON`) — no existing capability covers "summarize my wardrobe
insights," and this milestone's own instruction is explicit: use
deterministic, clearly-labeled insights unless existing AI architecture
*genuinely* supports the feature. Adding a sixth capability would mean a
new consent surface, a new provider-routing path, a new provenance
contract, and a new prompt — a disproportionately large, risky addition
for a dashboard whose actual numbers are already the valuable part.
**Decision: no new AI capability this pass.** Every number and list in
this dashboard is a plain derived query, and nothing in its UI claims or
implies AI generation — there is no "Data / Rule-based / AI-generated"
badge to add because nothing here is AI-generated. This keeps the
dashboard honest by construction rather than by a label.

## `TooManyFunctions` and the "fold into the model" precedent

Adding the wardrobe-mix queries and reusing them meant `StatsRepository`
needed new methods. A first pass added four (`observeMaterialDistribution`,
`observeFabricDistribution`, `observeOccasionCoverage`,
`observeGarmentCountWithoutOccasion`), which pushed the interface (and
`StatsRepositoryImpl`) to 14 functions against detekt's `TooManyFunctions`
threshold of 11. Rather than adding an unprecedented per-declaration
`@Suppress` (none exists anywhere in this codebase — confirmed by
search), the four were folded into one `observeWardrobeMixDistribution(): Flow<WardrobeMixDistribution>`
— the identical "fold into the model, not the interface" choice
`UsageStats`' own Phase 9 additions (`categoryWearCounts`,
`garmentVersatility`, `topGarmentCombinations`) already made for the same
reason. That brought the interface to exactly 11 — which detekt's
`TooManyFunctions` flags *at* the threshold, not only above it (the same
off-by-one behavior confirmed repeatedly in M19/M20). A second fold
consolidated two older, `feature:stats`-only, parameterless methods
(`observeNeverWornOutfitIds`, `observeGarmentsMissingOutfits` — both used
nowhere outside `InsightsViewModel`) into `observeWardrobeUsageGaps(): Flow<WardrobeUsageGaps>`,
bringing the interface to 10. Both new aggregate models (`WardrobeMixDistribution`,
`WardrobeUsageGaps`) live in `core/model/.../stats/Stats.kt` beside
`UsageStats`, documented with the same rationale.

## Consequences

- **No database migration** — every field M21's new queries read
  (`garment_materials`, `garment_fabrics`, `garment_occasions`, the
  `occasions` reference table) already existed from M19's ADR-018
  additions; M21 only added read queries over them. DB version unchanged.
- **No new AI capability, no new AI provider, no new consent surface** —
  this dashboard remains entirely deterministic.
- **`StatsRepository` interface**: two new methods added
  (`observeWardrobeMixDistribution`, `observeWardrobeUsageGaps`), two
  older ones removed by folding into the second — net effect keeps the
  interface under `TooManyFunctions` without a suppression.
- **Real bugs found and fixed** (2): the archived-garment season/dress-code
  coverage leak, and the dormant-items window bug — both covered by new
  regression tests, neither speculative (each traced to the exact
  responsible code before being called a bug).
- Genuine, disclosed limitation: Most Worn / Least Worn / Best Value /
  Highest Cost Per Wear remain lifetime-only metrics; not changed to be
  window-scoped this pass (see "Honest 'all time' disclosure" above).
- Genuine, disclosed limitation: tablet layout is code-verified against
  the codebase's own established `BoxWithConstraints` idiom, not verified
  on physical tablet hardware this pass.
- Parts 3, 4, 5, 7, 9, 11, 14, and most of 15 required no new code —
  each was already real, already tested, and already wired; this ADR
  documents that instead of re-describing already-shipped behavior as
  new work.
