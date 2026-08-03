# :core:database

Room. Full schema implemented in Phase 3 — 26 entities across 19 files, 17 DAOs, one
shared `Converters` class, and `WardrobeDatabase` (version 1, schema exported to
`schemas/com.wardrobe.app.core.database.WardrobeDatabase/1.json`). See
`phase-3-persistence.md` for the complete field-by-field design, index rationale, FK
cascade rules, migration strategy, the full-text-search decision, and the
derived-stat query design.

## Packages
| Package | Holds |
|---|---|
| `entity/` | Every `@Entity` — `Category`, `Color`, `Material`, `Brand`, `Tag`, `Occasion`, `Garment` (+ its five cross-ref tables), `Outfit` (+ its cross-ref), `WearEvent`, `StyleRule`, `Feedback`, the style-profile cross-refs, `Trip`/`TripActivity`/`PackingListItem`, `WishlistItem`, `WeatherCache`, `ImageMetadata`, `StatsCache` |
| `dao/` | One `@Dao` per aggregate root, plus `GarmentWithRelations`/`OutfitWithRelations` (`@Relation`-based read POJOs) and `StatsDao`. Every `StatsDao` query returns `Flow` (changed from `suspend` during Phase 5a) so `StatsRepositoryImpl` gets live-updating stats — Room's invalidation tracker re-runs a query automatically when a table it references changes, even for the multi-table `WITH`-CTE queries here |
| `migration/` | `MIGRATION_1_2` (Phase 5d) — the schema's first real migration; see "Phase 5d additions" below. Every migration from here on: a real `Migration` object in the same change as any `@Entity` change, never `fallbackToDestructiveMigration` (Phase 1 Section 9: this is a personal data store accumulated over years) |
| `converter/` | One shared `Converters` class — every enum in the schema stored as its `.name`, nullable-in/nullable-out uniformly (see the class's own KDoc for why) |

## Phase 5a additions
- `WardrobeDatabase.DATABASE_NAME` — a shared constant so `core:data`'s
  `Room.databaseBuilder` call and `BackupRepositoryImpl`'s file-location logic never
  duplicate the filename string.
- `WardrobeDatabase.checkpoint()` — issues `PRAGMA wal_checkpoint(FULL)` before a
  backup export copies the `.db` file, since Room's WAL mode means the file alone
  isn't guaranteed consistent otherwise.
- `StatsDao.observeActiveGarmentCountBySeason()`/`observeActiveGarmentCountByDressCode()`
  — closet *composition* counts (independent of wear history), backing
  `StatsRepositoryImpl`'s gap-analysis check.
- `TripDao.clearPackingList()`/`setPackedState()` — added alongside
  `TripRepositoryImpl`'s "replace the packing list wholesale" and "toggle packed by
  id" needs.

## Phase 5b addition
- `ImageMetadataDao.observeForGarment()` — a `Flow`-returning counterpart to the
  existing suspend `getForGarment()`, backing `ImageRepository.observeImages`
  (`core:data`, Phase 5b). No schema change: every column the image pipeline
  needs (dimensions, byte size, format, checksum) already existed from Phase 3.

## Phase 5d additions — the schema's first real migration
- **`WardrobeDatabase` version 1 → 2** (`MIGRATION_1_2`), backing Outfit
  Builder and Outfit Scheduling:
  - `outfits.isFavorite`/`isArchived`/`notes`/`mood` (all nullable-default,
    additive `ALTER TABLE`s).
  - Three new cross-ref tables mirroring `garment_seasons`/etc.'s exact
    pattern: `outfit_seasons`, `outfit_dress_codes`, `outfit_tags`.
  - `wear_events.status` (`WearEventStatus`: `PLANNED`/`WORN`, default
    `'WORN'` so every pre-existing row keeps its original retrospective-log
    meaning) plus an index — the single schema decision Outfit Scheduling
    needed to not corrupt `WearEvent`'s prior semantics.
  - `OutfitDao`/`WearEventDao` extended accordingly (`observeWithRelations`,
    `setFavorite`/`setArchived`, season/dressCode/tag cross-ref writers on
    `OutfitDao`; `update`/`deleteForDate`/`getForDate` on `WearEventDao`).

## Phase 5e additions — no schema change, six new derived `StatsDao` queries
Wardrobe Intelligence (`feature:stats`) needed no new tables or columns —
every number is a `Flow`-returning derived query, per Phase 1 Section 9.
`observeFavouriteCategories`, `observeWearCountByDate`,
`observeOutfitWearCounts`, `observeNeverWornOutfitIds`,
`observeGarmentsMissingOutfitIds`, `observeOutfitWearEventCount`, and
`observeDressCodeByDayType` all reuse this DAO's established idioms (the
`all_wears` CTE for garment-expanded wear counts where an outfit wear should
count toward each of its garments; a plain `GROUP BY` over raw `wear_events`
rows where it shouldn't — see `phase-5e-wardrobe-intelligence.md`'s query
strategy for exactly which is which and why).
  - `Migration1To2Test` (`core:database`'s `src/test`) verifies it — see that
    test's own KDoc for why it hand-builds the v1 database from the
    committed schema JSON rather than using
    `androidx.room.testing.MigrationTestHelper` (a real Room 2.8.4/
    Robolectric interaction bug, `TECHNICAL_DEBT.md` item 9).

## Phase 6 additions — the schema's second migration, plus a first-launch category seed
- **`WardrobeDatabase` version 2 → 3** (`MIGRATION_2_3`): a single additive
  column, `garments.isInLaundry` (`INTEGER NOT NULL DEFAULT 0`) — the styling
  engine's manually-toggled "don't recommend right now" flag, distinct from
  `GarmentStatus` (a permanent lifecycle state). `GarmentDao.setInLaundry()`
  added accordingly. `Migration2To3Test` verifies it the same way
  `Migration1To2Test` does (hand-built v2 database from the committed
  `2.json` schema, same Room/Robolectric workaround).
- **`Migration1To2Test` needed a real fix**: bumping `WardrobeDatabase.version`
  to 3 broke that pre-existing test, since Room validates the *full*
  migration path reaches the class's currently-declared version, not just the
  one step a given test targets. Fixed by adding `MIGRATION_2_3` alongside
  `MIGRATION_1_2` in that test's own migrations list.
- **`WardrobeDatabase.SeedCallback` now also seeds a default `Category`
  tree** (13 top-level categories, 8 sub-categories) on first launch — the
  schema's `Category` table had zero seed data before this phase (only
  `DEFAULT_OCCASIONS` was ever seeded), and the recommendation engine needs
  *some* recognizable category names to classify garments by slot
  (`OutfitSlot.classify`/`AccessoryCategory.classify`, both in `core:model`).
  Every seeded row is a plain, user-editable/deletable `CategoryEntity` —
  not a hardcoded taxonomy. `WardrobeDatabaseSeedTest` verifies every
  expected default row is present after `onCreate`.

## Phase 7 additions — the schema's third migration, no new tables
- **`WardrobeDatabase` version 3 → 4** (`MIGRATION_3_4`): five additive
  nullable columns on the pre-existing `weather_cache` table —
  `currentTempC`, `feelsLikeC`, `humidityPercent`, `uvIndex`, `condition`
  (stored as `WeatherCondition.name`, same enum-as-string convention as every
  other enum column in this schema). No new table: `weather_cache`'s
  `(latitude, longitude, date)` unique index and `upsert`/`get`/
  `getMostRecent`/`evictOlderThan` DAO methods were already built (Phase 3/
  5a) in anticipation of this phase — this migration only widens the row
  shape to carry the extra observation fields `WeatherSnapshot` gained.
  `Migration3To4Test` verifies it the same way `Migration1To2Test`/
  `Migration2To3Test` do (hand-built v3 database from the committed `3.json`
  schema, same Room/Robolectric workaround); both of those pre-existing tests
  needed the same real fix Phase 6 already required — `MIGRATION_3_4` added
  to their own migrations lists once `WardrobeDatabase.version` bumped to 4.

## Phase 8 additions — the schema's fourth migration, plus a database-level outbox

- **`WardrobeDatabase` version 4 → 5** (`MIGRATION_4_5`): every independently
  sync-tracked ("aggregate root") table gains `syncId` (TEXT, UNIQUE — the
  cross-device identity the wire protocol references, never the local
  `Long AUTOINCREMENT` `id`) and, for the 15 tables that didn't already have
  one, `updatedAt` (INTEGER epoch millis, for newest-wins conflict
  resolution). Pre-existing rows are backfilled with a real, randomly
  generated `syncId` one row at a time (`ALTER TABLE ... ADD COLUMN` can't
  take a function default). See `phase-8-multi-device-sync.md`'s "Why not
  UUID primary keys" section for why this is a second column, not a
  primary-key rewrite.
- **Four new tables**: `sync_change_log` (the outbox — one row per INSERT/
  UPDATE/DELETE, written by a per-table SQLite trigger, not a repository
  hook, so it can't be forgotten at a new call site), `paired_device` (the
  pairing registry, including each device's `lastSyncedChangeLogId` sync
  cursor), `sync_conflict` (surfaced edit/delete conflicts), `sync_history`
  (the Sync History list).
- New/extended DAOs: `SyncChangeLogDao`, `PairedDeviceDao`, `SyncConflictDao`,
  `SyncHistoryDao`, plus `getBySyncId`/`getById` added to every existing DAO
  a sync handler needs to resolve a foreign-key reference by `syncId`.
- **`Migration1To2Test`/`Migration2To3Test`/`Migration3To4Test` needed the
  same real fix Phase 6/7 already required for the prior version bumps**:
  `WardrobeDatabase.version` moving to 5 meant all three tests' own
  `addMigrations(...)` lists needed `MIGRATION_4_5` added, since Room
  validates the full migration path reaches the class's *currently
  declared* version, not just the one step each test targets — the fourth
  time this exact interaction has recurred at a version bump.
- `Migration4To5Test` (new) verifies syncId backfill is distinct/non-blank
  per row, `updatedAt` is backfilled, and a post-migration INSERT genuinely
  fires the new outbox trigger.

## Phase 9 additions — no schema change, four new derived `StatsDao` queries plus one `FeedbackDao` query

Smart Wardrobe Intelligence (`feature:closet`/`feature:outfits`/
`feature:stats`/`feature:trips`/`feature:calendar`) needed **zero** new
tables, columns, or migrations — every number is a `Flow`-returning derived
query, the same discipline every prior phase's stats additions already
established:
- `StatsDao.observeWearDatesForGarment(garmentId)` — the same dual-source
  `all_wears` CTE every other wear-count query already uses, scoped to one
  garment and returning its sorted wear-date list; backs first-worn/
  most-recent/total-wears/average-days-between-wears/rotation-score, all
  computed in Kotlin from this one list rather than a second, inconsistent
  SQL-side definition of "worn."
- `StatsDao.observeActiveGarmentCountByCategory()` — Shopping Gap
  Analysis's "how many do I own" side, the same shape as the pre-existing
  `observeActiveGarmentCountBySeason`/`observeActiveGarmentCountByDressCode`.
- `StatsDao.observeOutfitAppearanceCountByGarment()` — "most/least versatile
  garments": how many distinct saved outfits a garment appears in.
- `StatsDao.observeTopGarmentPairs(limit)` — "favorite combinations": a
  self-join on `outfit_garments` pairing every two garments sharing an
  outfit, `a.garmentId < b.garmentId` avoiding double-counting a pair.
- `FeedbackDao.observeVoteCountsForOutfit(outfitId)` — backs `OutfitRating`,
  derived from Phase 6's existing up/down `Feedback` votes rather than a
  new rating table.

No migration tests were needed since `WardrobeDatabase.version` did not
change this phase.

## What's NOT here yet, deliberately
- **No repository implementations** beyond what Phase 5a's `core:data` module calls
  — this module only declares the schema and query surface.
- **Two invariants the schema itself cannot enforce**, both already validated in
  `core:model` and documented in `phase-3-persistence.md`: `WearEvent`'s
  garmentId-XOR-outfitId, and `PackingListItem`'s garmentId-XOR-freeTextName.
