# ADR-016: Closet Filters & AI-First Wardrobe Browsing (M17 Part 7)

**Status**: Accepted (implementation milestone, added 2026-08-08, extending
Phase 5c's Closet architecture)

## Context

M17's brief asked for real, multi-select, AND/OR filtering across every
filterable garment facet, a real result count, real derived "insight" stats,
and a small set of honest "smart" filter presets — explicitly not a
database-style flat list, and explicitly not fake AI. Before writing any
code, this milestone's own instructions required inspecting the existing
Closet screen — that inspection found `feature:closet` already ships a
substantial search/filter/sort/chips/empty-state system (Phase 5c), not a
blank slate. This ADR records what M17 changed and why, on top of that
existing system.

## Decision

### 1. Filtering consolidated into one pure, in-memory matcher — `GarmentFilter`/`GarmentRepository`/`GarmentDao` untouched

Before M17, `ClosetViewModel` split filtering two ways: `categoryId`,
`brandId`, `status`, `season`, `dressCode`, `isFavorite`, and `searchQuery`
were pushed down to `GarmentDao`'s SQL `WHERE` clause via `GarmentFilter`;
`colorId`, `materialId`, `tagId`, and price range were applied in-memory in
`GarmentRepositoryImpl`, by `GarmentFilter`'s own documented design ("this
app's realistic scale — hundreds, not millions, of garments — makes a
linear in-memory filter pass cheap").

Supporting genuine multi-select (`Set<CategoryId>` instead of `CategoryId?`,
etc.) for every existing facet, plus four entirely new facets (Fabric,
Occasion, Fit, Gender, WaterproofLevel — see §2), would have meant either
extending `GarmentDao`'s hand-written `WHERE` string with `IN (...)`
clauses for eleven different facets, or moving everything to the
already-established in-memory path. This milestone chose the latter:
`ClosetViewModel.sqlFilterFlow` now pushes down only `status` and
`searchQuery` (search's own denormalized-`searchText` LIKE query is
untouched — see §3); every facet, including the ones that used to be
SQL-pushed, is now evaluated by one new pure function,
`Garment.matchesClosetFilters()` (`feature/closet/.../closet/GarmentFilterMatching.kt`).

This is a genuine simplification, not a regression: `GarmentFilter` (core
model), `GarmentRepository`, and `GarmentDao` are **completely unchanged** —
every other consumer (Home's `isFavorite` query, Stats, Wishlist, etc.)
behaves exactly as before. Only Closet's own filtering computation moved
fully in-memory, consistent with the scale assumption `GarmentFilter`
already documented for its own in-memory half.

**AND/OR semantics**: `matchesClosetFilters` evaluates a list of
independent per-facet checks, each OR'ing within its own `Set` (e.g. Color
= Black OR White) and ANDs all facets together (`.all { it }`) — exactly
the brief's `(Black OR White) AND Winter AND Work` example. Each facet
check is its own tiny function (`matchesAny`, `matchesSet`,
`matchesCategory`, `matchesColor`, `matchesPriceRange`) specifically so no
single function accumulates the whole facet count's cyclomatic complexity —
a real refactor forced by detekt's `CyclomaticComplexMethod` threshold
during implementation, not a stylistic choice. Fully covered by
`GarmentFilterMatchingTest` (12 tests) with no ViewModel/Flow machinery
required.

**Category hierarchy**: `Category` already carries `parentId`/`CategoryLevel`
(Phase 3) but no filter used it. `matchesCategory` now treats a top-level
category selection as matching itself *or* any of its subcategories — real
refinement of existing hierarchy data, not a fabricated grouping. The filter
sheet's Category section (`groupCategoriesForFilterUi`) sorts subcategories
directly under their parent and labels them "Parent · Sub"
(`Category.displayLabel`) so the hierarchy is visible in the otherwise flat
chip list.

### 2. Four new facets — all backed by fields the schema already had

`Garment.fabrics`, `occasionIds`, `fit`, `gender`, and `waterproofLevel`
were all already real, persisted fields (added in earlier milestones) with
**zero UI to filter by them anywhere in the app**. M17 adds Fabric,
Occasion, Fit, Gender, and Waterproofing sections to `ClosetFilterSheet` and
corresponding `Set` fields to `ClosetFilterState`, `ClosetFilterOptions`
(now carrying `fabrics: List<Fabric>` / `occasions: List<Occasion>`, sourced
from the already-existing `FabricRepository`/`OccasionRepository`), and
`GarmentFilterMatching`. **No database migration, no new Room columns, no
new DAO methods** — Part 7L's "stop and document if a field is missing"
clause never triggered, because nothing was missing.

### 3. Search — reused as-is

Closet's search (debounced 300ms, matched against `GarmentDao`'s
denormalized `searchText` column covering name/category/brand/colors/tags/
fabrics/occasions/size/notes, `GarmentRepositoryImpl.buildGarmentSearchText`)
was already comprehensive and already covers every field M17's brief asked
for (name, brand, category, material/fabric, tags, color, occasion — via
the denormalized index). It is unchanged by this milestone.

### 4. Sorting — `FAVORITE_FIRST` added, everything else already existed

`GarmentSort`/`GarmentSortField` already had `RECENTLY_ADDED`,
`RECENTLY_WORN`, `ALPHABETICAL`, `BRAND`, `COLOR`, `PRICE`, `WEAR_COUNT`, and
`COST_PER_WEAR` — the wear-history sorts are genuinely backed by
`StatsRepository.observeCostPerWear()` (derived-query, per ADR-006), already
joined into `ClosetViewModel`. Only `FAVORITE_FIRST` (Part 7E's explicit
minimum) was missing; added to the enum and to `sortGarments`'s comparator
(`compareBy { it.isFavorite }`). No new wear-history plumbing was built —
it already existed end to end.

### 5. Result count — a real, computed label, never hardcoded

`ClosetUiState.resultCountLabel` ("N items" / "M of N items") is computed
directly from `garments.size` (the real filtered/searched list) and
`totalUnfilteredCount` (a real `garmentRepository.observeGarments(status =
ACTIVE)` count, independent of search/filters) — both already-real numbers,
just not previously surfaced in the UI.

### 6. Insights (Part 7I) — real derived stats, no AI, no fake labels

`computeClosetInsights` (`ClosetInsight.kt`) computes, from the real active-
garment list: favorites count, current-season item count (via
`currentSeason(LocalDate)`, a documented Northern-Hemisphere meteorological
simplification — the app has no location signal for wardrobe purposes,
consistent with the existing "good enough, honestly stated" heuristic
pattern `Occasion.impliedDressCode()` already uses), and "work-ready" item
count (dress codes matching `ClosetFilterPreset.WORK`). Each is a plain
`"N noun(s)"` label; a zero count is omitted rather than shown as "0 items."
Tapping an insight applies its exact `ClosetFilterState` the same way any
filter chip does. No AI Gateway call, no "AI picked"/"Perfect match" style
copy anywhere in this feature.

### 7. Smart presets (Part 7J) — DressCode-based, not Occasion-based, and why

`ClosetFilterPreset` (Work/Casual/Travel/Date Night/Athletic) each map to a
fixed `Set<DressCode>`, reusing the exact same
business/athletic/smart-casual/casual categorization already established by
`Occasion.impliedDressCode()`'s keyword buckets (`core:model`), rather than
inventing new classification rules — directly satisfying the brief's
instruction to consult existing Occasion/DressCode semantics before adding
new ones.

**Deliberately not Occasion-name-based**: an alternative design would match
a preset against `Occasion` rows whose *name* matches (e.g. an occasion
literally called "Travel"). This was rejected: `Occasion` is a free-form,
user-extensible reference table (Phase 3) with no guaranteed rows — a
preset built on it could silently return zero results for a user who never
created a matching occasion, which is exactly the kind of thing Part 7J
says not to ship ("if a preset cannot be implemented honestly from existing
data, do not add it"). `DressCode` is a fixed enum every garment can
carry, so it is the only signal available to every user regardless of how
they've tagged occasions.

Applying a preset requires **no new persisted state** — it's a one-line
`filters.copy(dressCodes = preset.dressCodes)`, so it shows up as ordinary,
individually-removable "Dress Code" chips in `ActiveFilterChipsRow`, fully
overridable, not a separate mode.

### 8. UI — `MultiSelectChips` adopted for consistency; `EmptyState` extended with a secondary action

The pre-M17 filter sheet hand-rolled a single-select-with-deselect
`SingleSelectFilterSection` for every facet, even though a true multi-select
component (`MultiSelectChips`, `core:ui`, already used by Onboarding,
Capture Review, and Edit Garment) already existed. M17's filter sheet now
uses `MultiSelectChips` for every facet, matching the rest of the app and
enabling real multi-select without a new component.

`EmptyState` (`core:ui`) gained an optional `secondaryActionLabel`/
`onSecondaryAction` pair (backward-compatible, defaults `null`) so Closet's
"no filter results" state can offer both "Clear filters" (primary) and
"Modify filters" → opens the filter sheet (secondary), per Part 7G. No
other `EmptyState` call site (Home, Outfits, Stats, Trips) needed changes.

`WardrobeFilterChip` (`core:ui`) gained an optional
`contentDescriptionOverride` (backward-compatible, defaults `null`) so
active-filter chips announce "Remove X filter" and insight chips announce
"X. Tap to filter." instead of the default checkbox "selected/not selected"
phrasing — a real accessibility improvement for Part 7N, not a cosmetic one.

## Consequences

- **No database migration, no schema changes.** Every field M17 filters on
  already existed; Part 7L's "stop and document if genuinely missing" gate
  never fired.
- **`GarmentFilter`/`GarmentRepository`/`GarmentDao` are unchanged.** Every
  other consumer of `observeGarments()` is unaffected by this milestone.
- **Facet toggles no longer re-query the database** — only `search` does
  (via `flatMapLatest`); every other filter change recomputes the
  already-loaded, already-search-filtered list in `buildUiState`. This is a
  genuine performance improvement over the pre-M17 split design, at the
  documented cost of loading every active garment into memory for Closet's
  own use (already the established, accepted scale assumption).
- **Genuine limitation, disclosed**: `computeClosetInsights`'s season
  heuristic assumes the Northern Hemisphere (no location data is available
  for this purpose) — recorded in `TECHNICAL_DEBT.md`.
- Parts 8–13 of the wider "AI Wardrobe Assistant" epic remain untouched.
