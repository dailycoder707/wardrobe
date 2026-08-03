# Phase 3 — Data Model & Persistence

Explains the design before the code, per Constitution rule 1. Builds on
`phase-1-architecture.md` Sections 2 (domain model), 9 (schema), 10 (ERD), and 11
(repository interfaces) — this document finalizes exact field types/nullability,
resolves two things Phase 1 left as sketches, and adds the full-text-search design and
derived-stat query design Phase 3 explicitly asks for. Code follows in `core:model`
(domain), `core:database` (Room), and `core:domain` (repository interfaces).

## Two refinements over the Phase 1 sketch

**1. `Season` and `DressCode` become Kotlin enums, not Room reference tables.**
Phase 1 Section 9 chose real junction tables for both specifically because Closet
Browse must filter by them efficiently, and a bitmask column can't be cleanly indexed
in SQLite. That reasoning still holds — but it doesn't actually require a separate
`seasons`/`dress_codes` *reference* table, only that the join table's value column be
indexable. Since both are genuinely fixed, non-user-editable vocabularies (Phase 1's
own words), storing the enum directly as an indexed `TEXT` column on the junction table
(`garment_seasons(garment_id, season)`, `garment_dress_codes(garment_id, dress_code)`)
gets the same indexed-filter capability without an extra FK indirection to a table that
would only ever hold 4 and ~6 fixed rows respectively. **Rejected alternative**: keep
Phase 1's literal `seasons`/`dress_codes` reference tables — adds a join and a seeding
step for no query capability the enum-column approach doesn't already have.
`Category`, `Color`, `Material`, `Brand`, `Tag`, and `Occasion` remain real tables,
because those are genuinely user-extensible or hierarchical (Phase 1 says so
explicitly for Occasion; Brand/Tag are free-form user input; Category is a hierarchy).

**2. `Garment` gets an optional `name` field.** Phase 1's domain model (Section 2)
didn't list one, but full-text search needs *something* to match a query against, and
users routinely want to nickname an item ("the blue oxford"). Added as nullable — most
garments will rely on category/brand/color/tags for search instead.

## Entity list

All tables live in `core:database`'s `entity/` package. `Instant`/timestamp columns are
stored as epoch-millisecond `Long` (no converter needed); date-only fields (a wear date,
a purchase date, a trip's start/end) are stored as ISO-8601 `TEXT` (`YYYY-MM-DD`) so
SQLite's native `strftime`/date functions work directly on them without a Long↔epoch-day
round trip — this matters for the weekday/weekend stats query below. Enums are stored as
their `.name` via one shared `Converters` object.

| Table | Key columns (beyond an autoGenerate `id`) | Nullable | Notes |
|---|---|---|---|
| `categories` | `name` TEXT, `parentId` FK→self, `level` enum | `parentId` | Self-referencing hierarchy |
| `colors` | `name` TEXT, `hexValue` TEXT | — | |
| `materials` | `name` TEXT UNIQUE | — | |
| `brands` | `name` TEXT UNIQUE, `logoUri` TEXT | `logoUri` | |
| `tags` | `name` TEXT UNIQUE | — | Free-form user tags |
| `occasions` | `name` TEXT UNIQUE | — | User-extensible, seeded with defaults |
| `garments` | `name`, `categoryId` FK, `primaryColorId` FK, `pattern`, `fit` enum, `length` enum, `sleeveLength` enum, `warmthRating` INT(1-5), `breathabilityRating` INT(1-5), `brandId` FK, `size`, `price` REAL, `currencyCode`, `purchaseDate` TEXT, `condition` enum, `careNotes`, `status` enum NOT NULL, `isReviewed` BOOL NOT NULL, `searchText` TEXT NOT NULL, `createdAt`/`updatedAt` | most | `searchText` is a maintained denormalization — see FTS section |
| `garment_color_palette` | `garmentId` FK, `colorId` FK, `weightPercent` INT | — | Composite PK |
| `garment_materials` | `garmentId` FK, `materialId` FK, `percentage` INT | `percentage` | Composite PK |
| `garment_tags` | `garmentId` FK, `tagId` FK | — | Composite PK |
| `garment_seasons` | `garmentId` FK, `season` enum TEXT | — | Composite PK — see refinement #1 |
| `garment_dress_codes` | `garmentId` FK, `dressCode` enum TEXT | — | Composite PK — see refinement #1 |
| `outfits` | `name`, `occasionId` FK, `source` enum NOT NULL, `isSaved` BOOL NOT NULL, `photoUri`, `createdAt` | `name`, `occasionId`, `photoUri` | |
| `outfit_garments` | `outfitId` FK, `garmentId` FK, `layerSlot` INT | — | PK is `(outfitId, layerSlot)` |
| `wear_events` | `date` TEXT NOT NULL, `garmentId` FK, `outfitId` FK, `weatherCacheId` FK, `occasionId` FK, `note`, `createdAt` | `garmentId`/`outfitId` (XOR, app-enforced), rest | |
| `style_rules` | `description` NOT NULL, `sourceType` enum NOT NULL, `sourceFeedbackId` FK, `ruleType` enum NOT NULL, `parametersJson` TEXT NOT NULL, `isActive` BOOL NOT NULL, `createdAt` | `sourceFeedbackId` | `parametersJson` — see rationale below |
| `feedback` | `targetType` enum NOT NULL, `targetGarmentId` FK, `targetOutfitId` FK, `vote` enum NOT NULL, `reasonCode`, `reasonText`, `generatedStyleRuleId` FK, `createdAt` | most | |
| `style_profile_preferred_brands` | `brandId` FK (PK) | — | Singleton-profile junction — see below |
| `style_profile_avoided_categories` | `categoryId` FK (PK) | — | Same |
| `trips` | `name`, `destination` NOT NULL, `startDate`/`endDate` TEXT NOT NULL, `luggageSize` enum, `createdAt` | `name`, `luggageSize` | |
| `trip_activities` | `tripId` FK, `activityTag` TEXT NOT NULL | — | |
| `packing_list_items` | `tripId` FK, `garmentId` FK, `freeTextName`, `category`, `isPacked` BOOL NOT NULL, `rationale` | `garmentId`, `freeTextName`, `category`, `rationale` | Exactly one of `garmentId`/`freeTextName` in practice (app-enforced, same pattern as `wear_events`) |
| `wishlist_items` | `name` NOT NULL, `photoUri`, `notes`, `estimatedPrice` REAL, `currencyCode`, `categoryId` FK, `brandId` FK, `priority` INT, `createdAt`, `isPurchased` BOOL NOT NULL | most | |
| `weather_cache` | `latitude`/`longitude` REAL NOT NULL, `date` TEXT NOT NULL, `fetchedAt` NOT NULL, `tempHighC`/`tempLowC`/`apparentTempHighC`/`apparentTempLowC` REAL, `precipitationProbabilityPercent` INT, `windSpeedKph` REAL, `conditionCode` | temps/precip/wind/condition | Unique on `(latitude, longitude, date)` |
| `image_metadata` | `garmentId` FK NOT NULL, `type` enum NOT NULL, `filePath` NOT NULL, `width`/`height` INT NOT NULL, `fileSizeBytes` NOT NULL, `format` NOT NULL, `checksum`, `createdAt` | `checksum` | |
| `stats_cache` | `cacheKey` TEXT (PK), `jsonValue` NOT NULL, `computedAt` NOT NULL | — | Performance cache only — ADR-006 |

**Singleton style-profile junctions**: `style_profile_preferred_brands`/
`_avoided_categories` have no "profile id" column because there is exactly one style
profile per installed app (single-user product, ADR-004) — the table *is* the set.

**Why `style_rules.parametersJson` is a JSON blob, not normalized columns**: rule
parameters vary by `ruleType` (a temperature threshold for one rule, a category id for
another, a pair of colors for a third). The styling engine (Phase 6) always loads *all*
active rules into memory to evaluate them procedurally — no query ever needs to filter
rules by a parameter value at the SQL level. Normalizing into many nullable
per-rule-type columns would add schema complexity purely to support a query pattern
that doesn't exist. Revisit only if a future feature needs to query rules by parameter
value in SQL.

## Indices and why

| Index | Reason |
|---|---|
| `garments(categoryId)`, `garments(status)`, `garments(brandId)` | Category filter and the "active" status filter run on every Closet Browse query; brand backs the stats "favourite brands" query |
| `wear_events(date)` | Calendar range queries, every stats trend query |
| `wear_events(garmentId)`, `wear_events(outfitId)` | Cost-per-wear and dormant-item queries join through both |
| `garment_color_palette(garmentId, colorId)` unique | Prevents duplicate rows; signature-colour stats query |
| `garment_materials(garmentId, materialId)` unique | Same reasoning |
| `garment_seasons(garmentId, season)`, `garment_dress_codes(garmentId, dressCode)` unique | Filter + prevents duplicate rows (refinement #1) |
| `image_metadata(garmentId)` | One garment → up to 3 images, looked up constantly |
| `weather_cache(latitude, longitude, date)` unique | Cache-key lookup |
| `weather_cache(fetchedAt)` | Staleness-eviction sweep |
| `feedback(generatedStyleRuleId)` | Traceability lookups (rule → originating feedback), Constitution requirement that every rule be traceable |
| `outfit_garments(garmentId)` | "Which outfits contain this garment" — needed by cost-per-wear (a garment's wear count includes wears of outfits containing it) |

**Deliberately not indexed**: `garments.searchText`. It's queried with an infix
`LIKE '%term%'`, which a B-tree index cannot accelerate (only a `LIKE 'term%'` prefix
match benefits from one). At this app's realistic scale — a personal closet, realistically
low thousands of rows at the absolute high end — a full table scan over that column is
sub-millisecond. Adding an index here would be decorative, not functional.

## Foreign keys and cascade rules

The general rule: **CASCADE** when the child row has no meaning without the parent
(junction/cross-ref tables, a trip's activities, a garment's images); **RESTRICT** when
deleting the parent would silently destroy something the app has promised to keep (a
garment or outfit with wear history — stats integrity depends on that history
persisting); **SET_NULL** when the parent is optional context that can be cleanly
dropped (a garment's brand, a wear event's weather snapshot).

| FK | Action | Why |
|---|---|---|
| `garments.categoryId → categories.id` | RESTRICT | A garment must always have a valid category; deleting a category in use should force reassignment first, not silently orphan garments |
| `garments.brandId → brands.id`, `garments.primaryColorId → colors.id` | SET_NULL | Optional metadata; losing the reference is harmless |
| `garment_color_palette.garmentId`, `garment_materials.garmentId`, `garment_tags.garmentId`, `garment_seasons.garmentId`, `garment_dress_codes.garmentId` | CASCADE | Meaningless without the garment |
| `garment_color_palette.colorId`, `garment_materials.materialId` | RESTRICT | Reference data; force explicit cleanup rather than silently vanishing from every garment's palette |
| `garment_tags.tagId` | CASCADE | Tags are low-stakes free-form data; silently dropping a deleted tag from garments is the expected behaviour |
| `outfit_garments.outfitId` | CASCADE | Meaningless without the outfit |
| `outfit_garments.garmentId` | RESTRICT | **A garment that's part of a saved outfit can't be hard-deleted** — must be removed from the outfit first. This is a deliberate product rule, not just a technical one |
| `wear_events.garmentId`, `wear_events.outfitId` | RESTRICT | The single most important integrity rule in this schema: wear history backs cost-per-wear and every usage stat. A garment can be marked `SOLD`/`DONATING` (a status change) without ever needing to be hard-deleted, which is exactly why this restriction doesn't block the normal "I got rid of this item" flow |
| `wear_events.weatherCacheId`, `wear_events.occasionId` | SET_NULL | Optional context |
| `feedback.targetGarmentId`, `feedback.targetOutfitId` | CASCADE | Feedback about a deleted item has no remaining purpose |
| `feedback.generatedStyleRuleId`, `style_rules.sourceFeedbackId` | SET_NULL | Traceability link, not a hard dependency either direction |
| `style_profile_preferred_brands.brandId`, `style_profile_avoided_categories.categoryId` | CASCADE | The preference is meaningless once the brand/category itself is gone |
| `trip_activities.tripId`, `packing_list_items.tripId` | CASCADE | Meaningless without the trip |
| `packing_list_items.garmentId` | SET_NULL | Falls back to `freeTextName` if the suggested garment is later deleted, rather than deleting the packing-list line itself |
| `wishlist_items.categoryId`, `wishlist_items.brandId` | SET_NULL | Optional metadata |
| `image_metadata.garmentId` | CASCADE | An orphaned image record with no garment is meaningless — the actual *file* cleanup is `core:image`'s `OrphanedImageCleanupWorker` (Phase 1 Section 17), not a DB cascade |

The `wear_events` garmentId/outfitId **XOR** constraint and the `packing_list_items`
garmentId/freeTextName **"exactly one populated"** pattern are both enforced at the
repository layer, not the schema layer — SQLite/Room have no clean way to express an
XOR CHECK constraint across two nullable FK columns. Both will have dedicated unit
tests in Phase 8. Flagged explicitly here per Constitution rule 4: the schema does not
guarantee this on its own.

## Migration strategy

Version 1 ships with no migrations (nothing to migrate from). From version 2 onward:
`fallbackToDestructiveMigration` is never used (Phase 1 Section 9 — this is a personal
data store accumulated over years); every schema change ships a real `Migration(from,
to)` object in `core:database/migration/`, and the schema JSON
(`room { schemaDirectory(...) }`, already configured in Phase 2) is committed so Phase
8's migration tests can construct a v1 database and assert the migration produces the
expected v2 schema without data loss. The rule going forward: **no PR changes an
`@Entity` without adding the matching `Migration` in the same change.**

## Full-text search: decision

**Decision: no SQLite FTS4/FTS5 virtual table. A plain indexed-adjacent `LIKE` query
against a maintained `garments.searchText` column instead.**

`searchText` is a single denormalized column, rebuilt by the repository layer (in
`core:data`, Phase 5a) in the same transaction as any write that touches a garment's
searchable attributes (name, category name, brand name, color names, tag names, care
notes, size) — concatenated into one lowercase string. Search queries run
`WHERE searchText LIKE '%' || :query || '%'`.

**Why not real FTS**: SQLite FTS (via Room's `@Fts4`/`@Fts5`) exists to solve two
problems this app doesn't have at its actual scale — relevance ranking across large
free-text bodies, and fast search over hundreds of thousands of rows. A personal
closet realistically tops out in the low thousands of garments; a full-table `LIKE`
scan over a short denormalized string at that volume is sub-millisecond, the same
conclusion as the "why no index" note above. FTS's actual cost is real: a virtual
table's content must be kept in sync with the source columns (via triggers or manual
`INSERT OR REPLACE`), tokenizer choice affects match behaviour in ways that need
testing, and Room's FTS API has real edge cases around updates to `contentEntity`-backed
tables. That complexity buys nothing at this data volume. **Revisit if** a future
feature introduces large free-text bodies to search (e.g. long styling notes across
thousands of entries) where relevance ranking would start to matter.

## Derived-stat queries (Phase 3 requirement — `StatsDao`)

All of these are Room `@Query` methods over the tables above; none read from a
precomputed table (ADR-006). SQL sketched here, exact Kotlin in `StatsDao.kt`.

- **Cost-per-wear**: `price / wearCount` per garment, where `wearCount` = direct wear
  events (`wear_events.garmentId = g.id`) **plus** wears of any outfit containing the
  garment (`wear_events.outfitId` joined through `outfit_garments.garmentId = g.id`).
  Both counted via a `UNION ALL` of the two event sources before counting, so a garment
  worn directly twice and worn as part of an outfit three times shows 5 total wears.
- **Dormant items**: garments with zero rows in that same union, optionally filtered to
  "zero in the last N days" via a `date >=` bound, versus "never worn at all."
- **Usage percentage**: `COUNT(DISTINCT garment with ≥1 wear) / COUNT(active garments)`,
  over a given window.
- **Wear frequency by category/colour/dress-code**: the wear-event union joined back to
  `garments`/`garment_color_palette`/`garment_dress_codes`, grouped by the relevant
  column.
- **Weekday vs. weekend split**: `CASE WHEN CAST(strftime('%w', date) AS INTEGER) IN (0, 6) THEN 'WEEKEND' ELSE 'WEEKDAY' END`
  grouped and counted — this is exactly why `wear_events.date` is stored as ISO `TEXT`
  rather than an epoch `Long`: `strftime('%w', ...)` operates directly on an ISO date
  string with no conversion.

## Risks

| Risk | Mitigation |
|---|---|
| The union-of-two-sources wear-count query (direct + via-outfit) is the single most complex query in this schema and easy to get subtly wrong (e.g. double-counting a garment worn twice in the same outfit on the same day) | Dedicated adversarial unit tests in Phase 8: a garment worn only directly, only via outfits, via both, and via an outfit worn multiple times |
| `RESTRICT` on `wear_events`/`outfit_garments` FKs means a naive "delete this garment" UI action will throw a `SQLiteConstraintException` if history exists | The repository layer must catch this and guide the user toward `status = SOLD` instead of exposing a raw DB exception — a Phase 5a/5c concern, flagged here so it isn't a surprise later |
| `searchText` denormalization can drift from source data if a future write path forgets to rebuild it | Centralize the rebuild in one repository method every garment-mutating code path calls, not duplicated per call site — a Phase 5a implementation detail to get right |
