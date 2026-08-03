# Phase 5e — Wardrobe Intelligence

Home Insights, Wardrobe Story, Insights, Wardrobe Health, and Dashboard
Analytics — everything computed live from `Garment`/`WearEvent`/`Outfit` data,
nothing persisted as a separate "statistics" fact. Out of scope, per the
master prompt: the AI Styling Engine (Phase 6), weather, shopping, wishlist,
trips, and any networking/cloud feature. Everything here works fully offline,
the same as every prior phase.

## Philosophy, stated up front because it shaped every decision below

This is not an analytics dashboard. It's a personal wardrobe journal. Every
screen in this phase was built against one question: *if the user opened
this once a week, would they learn something genuinely useful about their
wardrobe, framed kindly?* Concretely, that meant:

- **Never a red number.** Wardrobe Health has no "wasted money" counter, no
  streak to keep up, no shaming copy anywhere. "Overused Items" (the
  spec's own term) is surfaced as **Reliable Favorites** — the same
  underlying signal (worn far more than the median), framed as a compliment.
  "Dormant Items" becomes **Ready for a Turn** — an invitation, not a
  complaint.
- **Every number is a real derived fact, or it doesn't ship.** Wardrobe
  Story's "cost saved" card is the clearest example: it's withheld entirely
  rather than shown with a number that would be misleading (see "Known
  limitations" below) — the phase's own instruction ("write every sentence
  naturally... avoid technical wording") doesn't excuse fabricating a
  sentence from insufficient data.
- **Depth is optional, never forced.** Home shows a compact highlight reel
  (`HomeInsightsSection`) with a "See all" link into the full Insights
  screen — it does not duplicate all twelve Home Insights bullet points as
  twelve separate cards on the screen the user opens every day.

## Architecture

No new persisted table — every method added this phase is a Room `@Query`
over `garments`/`wear_events`/`outfits`/`outfit_garments`/`outfit_categories`,
exactly ADR-006's rule. The layering is unchanged from every prior phase:

```
core:database  StatsDao          — 6 new @Query methods, all Flow
core:domain    StatsRepository   — 6 new interface methods + UsageStats.favouriteCategoryIds
core:data      StatsRepositoryImpl — implements them, no new dependencies
feature:stats  (new screens)     — Insights / Wardrobe Story / Wardrobe Health
feature:closet HomeViewModel     — extended with a 3rd combine() group for Home Insights
core:ui        StatsDiagnostics  — Developer Panel window, mirrors OutfitBuilderDiagnostics
```

`feature:stats` depends only on `core:model`/`core:domain`/`core:designsystem`/
`core:ui` — never `core:database`/`core:data` directly, and never
`feature:closet`/`feature:outfits`/`feature:calendar` (ADR-010: feature
modules don't depend on each other). Where it needs a garment/outfit tile
mapper or a `GarmentFilter`/`OutfitFilter`, it keeps its own copy
(`common/GarmentUiMappers.kt`, `common/OutfitUiMappers.kt`) — the same
duplication-over-coupling choice every other feature module already makes.

### Why narrative generation (Story/Health) lives in `feature:stats`, not `core:data`

`StatsRepositoryImpl` stays scoped to *mechanical* derived aggregates —
counts, group-bys, top-N queries — the same shape every method it already
had (Phase 3/5a) takes. Wardrobe Story's sentences and Wardrobe Health's
advisories are presentation text built by combining several of those
aggregates with taxonomy names (`ColorRepository`, `CategoryRepository`) —
display-text generation, not a derived stat in its own right. Putting that
in `StoryViewModel`/`HealthViewModel` (via top-level `buildStoryCards`/
`buildHealthAdvisories` functions, kept out of the ViewModel classes
themselves to stay under detekt's function-count threshold) mirrors exactly
how `HomeViewModel` already combines `StatsRepository` output with
`CategoryRepository`/`BrandRepository` to resolve display names — an
established pattern, not a new one invented for this phase.

## Query strategy

Six new `StatsDao` methods, all following the exact SQL shape of Phase 3's
existing ones:

| Method | Backs | Shape |
|---|---|---|
| `observeFavouriteCategories` | Favorite Categories | Same top-N-by-wear-count as `observeFavouriteBrands`, joined directly on `garments.categoryId` (no cross-ref table, unlike brand/color) |
| `observeWearCountByDate` | Usage Heatmap, Monthly/Weekly Wear | `GROUP BY date` over `wear_events` directly — deliberately **not** garment-expanded through the `all_wears` CTE (a day with one 5-garment outfit logged counts as 1, matching `observeWeekdayVsWeekend`'s existing semantics, not `observeCostPerWear`'s) |
| `observeOutfitWearCounts` | Frequently Repeated Looks | `GROUP BY outfitId HAVING wearCount > 1` over `wear_events` directly (an outfit's "wear" is always a deliberate whole-look log, never inferred from its garments) |
| `observeNeverWornOutfitIds` | Outfits Never Worn | `LEFT JOIN` outfits to `wear_events`, `HAVING COUNT = 0`, scoped to saved+non-archived looks |
| `observeGarmentsMissingOutfitIds` | Garments Missing Outfits | `LEFT JOIN` active garments to `outfit_garments`, `HAVING COUNT = 0` |
| `observeOutfitWearEventCount` | Wardrobe Story's "worn N outfits this year" | Plain `COUNT(*)` over `wear_events` where `outfitId IS NOT NULL` |
| `observeDressCodeByDayType` | Wardrobe Story's weekend-vs-weekday style card | The exact `strftime('%w', date)` weekend test `observeWeekdayVsWeekend` already used, parameterized by day type so `feature:stats` can compare the two and only surface a card when they genuinely differ |

Every query that touches `wear_events` filters `status = 'WORN'`, same as
every Phase 5d query — a `PLANNED` (scheduled) event never inflates a wear
count or a heatmap cell.

**Monthly and Weekly Wear share one query, not two.** `observeWearCountByDate`
returns one row per day; `feature:stats` buckets that list by `YearMonth`
(monthly) or by ISO-week start (weekly) in Kotlin. Re-querying SQLite once
per chart granularity would be wasted work for data that's already
date-grouped and cheap to re-bucket.

## Performance decisions

- **Kotlin-side aggregation is timed, not just assumed cheap.** `feature:stats`
  wraps its three real computation-heavy builders (`buildCharts`,
  `buildStoryCards`, `buildHealthAdvisories`) in `recordTiming(label) { }`,
  reporting into `StatsDiagnostics` — visible in the Developer Panel's new
  "Wardrobe Intelligence" section. This is *derived-computation time*
  (the Kotlin grouping/mapping work), not raw SQL execution time, which Room
  doesn't expose per invocation — the Developer Panel section says so
  explicitly rather than implying more precision than it has.
- **One real, justified cache**: `HeatmapBucketCache` memoizes the
  heatmap→monthly/weekly bucketing (a real `O(n)` grouping pass) against the
  last-seen heatmap payload, since Room occasionally re-emits an unchanged
  row list when an unrelated table it also watches changes. Cache hits/misses
  are reported into the same Developer Panel section. Nothing else in this
  phase caches anything — every other flow is either a cheap pass-through or
  already Room's own invalidation-tracked job (ADR-006's whole point).
- **Active flow-subscription counting**: each of the three new screens'
  `uiState` flows reports into `StatsDiagnostics.onSubscribed()`/
  `onUnsubscribed()`, giving real visibility into how many stats screens are
  live at once — genuinely useful for catching an accidental leaked
  subscription, not a synthetic metric.
- **Large-dataset tests, not just small hand-written fixtures**:
  `InsightsBuildersLargeDatasetTest` builds 1,000 garments / 500 outfits /
  two years of daily heatmap data and asserts the Kotlin-side builders stay
  bounded (list sizes capped, not unbounded) and complete well under a
  generous 2-second budget on a JVM unit test — not a real device benchmark
  (none exists in this environment), but a genuine regression guard against
  an accidental `O(n²)` creeping into the aggregation code.

## Visualization decisions

Four chart primitives, all in `feature:stats/common/charts/`, each answering
exactly one question per the master prompt's "avoid overwhelming numbers"
instruction — none reused from `core:ui`/`core:designsystem` since no other
feature module needs a bar chart or a calendar heatmap (the same
promote-when-a-second-consumer-needs-it discipline every prior phase used):

- **`ProgressRing`** — Usage Overview's "what fraction of your closet have
  you worn." A single arc, accent-colored progress over a track, center
  label. No legend, no axis.
- **`BarChart`** — Season/Dress Code/Weekday-vs-Weekend distribution,
  Monthly/Weekly Wear. Plain vertical bars, the tallest auto-highlighted in
  accent color (or an explicit `isHighlighted` override for e.g. "today" in
  a weekly view). No gridlines, no axis labels beyond the bar's own value.
- **`CalendarHeatmap`** — the Usage Heatmap. A GitHub-style contribution
  grid, deliberately **no per-cell numbers** — the whole point of a heatmap
  is the shape, not the digits; a cell's exact count lives in its
  accessibility content description for anyone who taps or inspects it.
- **`InsightRow`** — the one shared row every tap-to-navigate list uses
  (Most Worn, Never Worn, Cost Per Wear, Garments Missing Outfits, Outfits
  Never Worn, Repeated Looks, Recent Activity, and every Wardrobe Health
  advisory with specific items) — thumbnail, title, and a metric string that
  says *why* this item is in this particular list.

## Home Insights — a deliberate consolidation, not all twelve bullets literally

The master prompt lists twelve Home Insights (Today's Summary, Recently
Worn, Waiting to Be Worn, Most/Least Used, Recently Purchased, Recently
Added, Wardrobe Size, Favorite Categories/Colors/Brands, Upcoming Scheduled
Outfits). Home already showed five of these (summary card, Recently Added,
Recently Worn, wardrobe size, and — via `continueEditing` — a de facto
"needs attention" section) before this phase. Adding the remaining seven as
seven more full-width sections would directly contradict this phase's own
philosophy note ("avoid overwhelming the user," "calm, elegant"). Instead,
`HomeInsightsSection` (`feature:closet/home/HomeScreen.kt`) adds **one** new
horizontally-scrollable row of compact chips:

| Chip | Source | Taps to |
|---|---|---|
| Most reached for | `UsageStats.mostWornGarmentIds` | Garment Detail |
| Waiting to be worn | `StatsRepository.observeDormantItems` | Garment Detail |
| Newest addition | Garment list sorted by `purchaseDate` | Garment Detail |
| Favorite color/brand/category | `UsageStats.signatureColorIds`/`favouriteBrandIds`/`favouriteCategoryIds` | Insights screen |
| Coming up | `WearEventRepository` future `PLANNED` events | Insights screen |

Least Used is intentionally **not** duplicated on Home — it's one tap away
in the full Insights screen, which already shows it (paired with Most Worn,
so the contrast reads naturally rather than in isolation). `HomeViewModel`
gained two new constructor dependencies (`ColorRepository`, `OutfitRepository`)
and one new `combine()` group (`InsightsSourceData`) — the "reference +
activity + insights" three-group nested-combine shape mirrors the two-group
shape it already had.

## Wardrobe Story

Six candidate cards, each independently gated on having enough real signal —
`StoryBuilders.kt`'s `buildStoryCards` returns however many actually qualify
(`listOfNotNull`), including zero for a brand-new wardrobe (handled by a
dedicated empty state, not a broken/blank screen):

1. **"You've worn N outfits this year."** — `observeOutfitWearEventCount`.
2. **"Your most-loved color this year is X."** — `UsageStats.signatureColorIds.first()`, resolved to a name.
3. **"You haven't worn your X in N days"** / **"...yet — maybe today's the day."** — the single longest-dormant garment, gated at `>= 30 days` (a freshly-added item is never called out — see "Known limitations").
4. **"You've created N outfits in the last few months."** — outfits created in the last 90 days (this phase's honest stand-in for "season," since a real weather/hemisphere-aware season concept is out of scope — see below).
5. **"You've saved [amount] by rewearing pieces you already own."** — only when every rewear-worthy garment (worn 2+ times, priced) shares a single currency; otherwise withheld entirely, never shown with a misleading merged number.
6. **"You tend to go casual on weekends and business on weekdays."** — only when the weekend-dominant and weekday-dominant dress codes actually differ; identical dress codes produce no card rather than a vacuous one.

## Insights

One scrollable screen, a `StatsWindow` selector (1 Month/6 Months/1 Year/All
Time) at the top, then: Usage Overview (`ProgressRing`), Your Style
(favorite colors/brands/categories as chips), three distribution
`BarChart`s (Season, Dress Code, Weekday vs. Weekend), Monthly Wear,
Weekly Wear, Usage Heatmap, then eight tap-to-navigate `InsightRow` lists —
Most Worn, Least Worn, **Waiting to Be Worn** (a merged Never-Worn +
Dormant + Longest-Unused list, sorted longest-idle-first, since three
separate near-identical lists would repeat the same handful of garments
three times), Best Value (Cost Per Wear, ascending — cheapest-per-wear
first, framed as "getting the most value" rather than "most expensive"),
Missing an Outfit, Looks You Haven't Worn Yet, Go-To Looks (Frequently
Repeated), and Recent Activity. "Your Wardrobe Story" and "Wardrobe Health"
are reached from a row of two buttons at the top — real screens, not
nav-dock destinations, the same way Garment Detail isn't dock-level despite
being a real screen.

## Wardrobe Health

Seven advisories, each entirely optional and framed as an observation, never
a warning (`HealthBuilders.kt`):

- **Color Balance** — always shown once the wardrobe has any colored
  garments; either "nicely balanced" or a gentle "leans toward X."
- **Reliable Favorites** (the spec's "Overused Items," reframed) — garments
  worn at least 2× the median wear count among ever-worn garments.
- **Ready for a Turn** (the spec's "Neglected Items," reframed) — garments
  unworn (or never worn) for 60+ days.
- **Favorite Brands** — a one-line acknowledgment when `UsageStats` has any.
- **Recent Purchases to Try** ("Unused Purchases") — bought within 180 days,
  never worn.
- **Off to a Great Start** ("Recently Refreshed") — added within 30 days,
  *already* worn at least once — a positive-signal advisory, the mirror
  image of "Recent Purchases to Try."
- **Unexplored Categories** ("Never Purchased Accessories," generalized) —
  any top-level `Category` with zero active garments, not hardcoded to a
  category literally named "Accessories" (this schema doesn't reserve that
  name — see "Known limitations").

## Developer Panel additions

A new "Wardrobe Intelligence" section: cache hits/misses (from
`HeatmapBucketCache`), active flow subscriptions (from the three
`feature:stats` screens' own lifecycle), and a live map of derived-
computation timings by label (`insightsCharts`, `insightsLists`,
`wardrobeStory`, `wardrobeHealth`). `DeveloperPanelRepositories` was not
extended — `StatsDiagnostics` lives in `core:ui` (already a
`feature:closet` dependency), read the same way `OutfitBuilderDiagnostics`
already is.

## State

Only UI preferences would ever be persisted here (there are none new this
phase — the `StatsWindow` selector is session-only, like Saved Looks' sort
in Phase 5d). No calculated statistic is ever written back to disk;
`StatsWindow` itself, `HeatmapBucketCache`'s single memoized slot, and
`StatsDiagnostics`'s in-memory snapshot are all deliberately non-persistent
and reset on process death — recomputing them costs one Room query
round-trip, never a correctness risk.

## Testing strategy

| Layer | What | Where |
|---|---|---|
| Derived queries | 8 new real Room-backed tests (favourite categories, heatmap correctness incl. `PLANNED` exclusion, repeated-outfit threshold, never-worn-outfit exclusions, garments-missing-outfits, outfit-wear-event-count, weekday/weekend dress code with real calendar dates) + 1 larger-dataset aggregation-correctness test | `core/data/.../StatsRepositoryImplTest.kt` |
| ViewModel | 5 Insights, 6 Story, 6 Health tests against fakes | `feature/stats/src/test/.../{insights,story,health}/` |
| Compose/chart | `BarChart`, `ProgressRing`, `InsightRow`, `CalendarHeatmap` — render + interaction + edge cases (empty list, out-of-range progress) | `feature/stats/src/test/.../common/charts/` |
| Large dataset | 1,000 garments / 500 outfits / 2 years of heatmap data through the real Kotlin builders, asserting bounded output and a time budget | `InsightsBuildersLargeDatasetTest.kt` |

**Real bugs this testing loop actually caught** (not hypothetical — each
failed a real assertion on the first run):

1. `StoryBuilders.kt`'s longest-unworn card computed "days since added"
   against `Instant.now()` (real wall-clock time) instead of the
   already-injected, already-clock-derived `today` parameter — meaning a
   garment "added today" per the test's fixed clock could still be flagged
   as dormant if the *real* system clock had moved on since. Fixed by
   deriving `daysSinceAdded` from `today` (itself built from the injected
   `Clock`) instead — restores the same clock-injection discipline
   `phase-5a-data-layer.md` established specifically so this class of bug
   is testable at all.
2. `FakeStatsRepository.observeUsageStats(window)` ignored the requested
   `window` parameter entirely, always returning whatever `UsageStats` it
   was seeded with — masking that a real window-toggle test couldn't
   actually observe a window change. Fixed to `.map { it.copy(window = window) }`,
   matching `StatsRepositoryImpl`'s real contract.
3. A self-authored test for "Reliable Favorites" used a 2-item dataset
   where the median-based threshold (`>= 2× median`) could never
   mathematically be satisfied by the item defining the median itself —
   not a production bug, but a reminder that a median-based gap detector
   needs a real three-point spread to test meaningfully; fixed at the test
   data, not the algorithm.

No navigation-instrumentation test exists (this project has never had one —
every prior phase verifies `WardrobeNavHost` wiring by compiling and by each
screen's own tests, not a dedicated cross-module nav test, since there's no
device/emulator in this environment to run `androidTest` navigation
assertions against).

## Known limitations, stated rather than hidden

- **"This season" in Wardrobe Story means "the last 90 days," not a real
  hemisphere-aware calendar season.** A genuine season concept needs
  location/weather data this phase explicitly excludes (Weather Integration
  is out of scope). Stated as such in the card-building code, not silently
  approximated.
- **Cost-saved is withheld, not estimated, for a multi-currency wardrobe.**
  `CostPerWearEntry` carries no currency of its own (a pre-existing Phase
  5a/5c gap this phase worked around by resolving each garment's own
  `Money.currencyCode` at the `feature:stats` layer) — rather than extend
  the DAO row shape to carry currency for one narrative card, the card is
  simply skipped when the rewear-worthy garments span more than one
  currency. A future phase adding real multi-currency support should revisit
  this.
- **"Unexplored Categories" can't specifically detect "Accessories"** — this
  schema's `CategoryLevel` is a generic `TOP`/`SUB` split with no reserved
  "accessory" semantics. The advisory generalizes to *any* top-level
  category with zero garments, which is more honest than hardcoding a
  category name this app has no structural guarantee exists.
- **Performance is designed-for, not measured on a real device** — the same
  honest gap every prior phase has stated. The large-dataset JVM tests are a
  real regression guard against an accidental algorithmic blowup, not a
  substitute for a device profiling pass.
- **No shared-element or scroll-triggered chart animation** — `motion-guide.md`
  isn't specifically extended for chart entrances this phase; bars/rings/
  heatmap cells render immediately rather than animating in. A tasteful
  reveal animation is a reasonable Phase 5f-or-later polish pass, not
  something this phase's correctness depends on.
- **`recordTiming`'s numbers are Kotlin-side computation time, not SQL
  execution time** — stated explicitly in both the code comments and the
  Developer Panel section's own label, to avoid the Developer Panel implying
  more precision than it actually has.

## Future improvements

- A real multi-currency `Money`-aware cost-per-wear pipeline, once a second
  feature (Shopping/Wishlist, both explicitly out of scope this phase) needs
  currency-aware amounts elsewhere too.
- A genuine weather/hemisphere-aware "season" concept for Wardrobe Story's
  "this season" card, once Weather Integration (a later phase) exists to
  back it.
- Chart entrance animation once `motion-guide.md` is extended to cover data
  visualization, not just the screen transitions it currently documents.

## Verification

`./gradlew clean build` — real per-module verification loop run the same way
every prior phase's was: `feature:stats:testDebugUnitTest` (26 tests, all
passing after the 3 real bug fixes above), `core:data:testDebugUnitTest`
(the 9 new `StatsRepositoryImplTest` cases plus every pre-existing test,
unbroken), `feature:closet:testDebugUnitTest` (unbroken by the `HomeViewModel`
extension), full-project `detekt`/`ktlint`/`lint` fixed to green — see the
closing summary for the complete real-bug/real-fix list from that pass.
