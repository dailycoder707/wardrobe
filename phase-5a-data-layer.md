# Phase 5a — Data Layer

Explains the design before the code (Constitution rule 1). No UI in this phase, per
your milestone split — everything here lives in `core:data`, `core:datastore`, and
the Hilt wiring that connects them to `core:database`/`core:domain` (both already
built in Phase 3). Image capture/processing (`core:image`) is explicitly Phase 5b and
untouched here; the styling engine (`StylingEngineRepository`) is Phase 6; weather
(`WeatherRepository`) is Phase 7, since it needs `core:network`, not built yet.

## 1. Entity ↔ domain mapping strategy

`core:database`'s DAOs return raw rows (`GarmentWithRelations` etc. carry foreign-key
ids, not resolved objects — `phase-3-persistence.md`). Building a rich `Garment`
domain object needs the referenced `Category`/`Color`/`Brand`/`Material`/`Tag` rows
resolved too. Two ways to do this:

- **Per-item N+1 lookups**: for each garment, query its category/brand/colors/etc.
  individually. Simple but scales badly and, worse, doesn't naturally recompute a
  `Flow` when a *referenced* row changes (e.g. renaming a color) rather than the
  garment row itself.
- **`kotlinx.coroutines.flow.combine` over each reference-data Flow** (chosen): each
  repository observes `garmentDao.observeFiltered(...)` *and* `categoryDao
  .observeAll()`/`colorDao.observeAll()`/etc. together via `combine`, builds one
  in-memory `Map<Long, X>` per reference type per emission, and maps every
  `GarmentWithRelations` against those maps. Reference tables are small (dozens of
  rows at most — `phase-3-persistence.md`), so holding all of them in memory per
  emission is cheap, and the `Flow` now correctly re-emits if a referenced color's
  name changes, not just when a garment itself changes.

Every repository implementation follows this same shape; `OutfitRepositoryImpl` and
`WearEventRepositoryImpl` don't need it at all, since `Outfit`'s domain model only
carries `garmentId`s in its slots (`core:model`), not resolved `Garment` objects —
resolving a slot's actual garment is the *caller's* job (typically
`GarmentRepository.getGarment`), not something baked into every `Outfit` read.

## 2. Hilt module structure

- **`core:datastore`** gains Hilt (small `build.gradle.kts` addition — it had none in
  Phase 2, since nothing needed it until now) and provides the single
  `DataStore<Preferences>` singleton itself, the same pattern already used for
  Room in `core:database`.
- **`core:data/di`**: `DatabaseModule` constructs the real `Room.databaseBuilder(...)`
  (wiring `WardrobeDatabase.SeedCallback` with a real `@Singleton
  @ApplicationScope CoroutineScope`, Phase 3's placeholder finally getting a real
  scope) and provides every DAO from that instance; `RepositoryModule` binds every
  `core:domain` interface implemented in this phase to its `core:data` class via
  `@Binds`.
- **WorkManager**: `WardrobeApplication` (in `:app`) now implements
  `Configuration.Provider`, supplying a `HiltWorkerFactory` — WorkManager's
  on-demand initialization (stable since 2.6, in use here via 2.11.2) detects this
  automatically; no manifest surgery to disable the default initializer is needed.
  `BackupExportWorker`/`BackupRestoreWorker` (`core:data`) are `@HiltWorker`s taking
  `BackupRepository`'s actual file-handling logic as an assisted-injected dependency.

## 3. Backup / restore format and the restart decision

A `.wardrobebackup` file is a ZIP containing:

```
manifest.json        { schemaVersion, createdAt, appVersionName }
wardrobe.db           a checkpointed copy of the Room database file
datastore/            the DataStore preferences file, copied whole
images/               the entire images directory, copied whole (ADR-007's storage layout)
```

**Export**: issue `PRAGMA wal_checkpoint(FULL)` against the live database before
copying `wardrobe.db` — Room runs in WAL mode by default, so the main `.db` file
alone is only guaranteed consistent immediately after a full checkpoint, not at an
arbitrary moment. Progress (`BackupProgress`, `core:domain`) is reported per
top-level step (manifest → db → datastore → images → zip), not per-byte — a
single-user personal backup doesn't need byte-level granularity, and per-step is far
simpler to get right.

**Restore requires an app restart — a real, deliberate constraint, not an
oversight.** Room's `WardrobeDatabase` instance is a `@Singleton` held for the
process's lifetime; there is no supported way to close it, replace the underlying
file, and reopen a *new* instance into the same Hilt singleton component mid-process.
`RestoreWorker` closes the current database instance, replaces `wardrobe.db`/
`datastore/`/`images/` wholesale from the backup archive, and completes with
`RestoreProgress.Complete` — the eventual Settings UI (Phase 5f) is responsible for
telling the user to reopen the app. This is stated explicitly here because it's
exactly the kind of constraint that's easy to discover as a "bug" later if it isn't
written down as intentional now.

**Not yet hardened, stated rather than hidden**: this implementation does not defend
against the app being backgrounded mid-restore in a way that lets something else
touch the database files concurrently. For a single-user, foreground-triggered,
local-only operation this risk is low, but it is a real gap, not a solved problem —
worth a note in `TECHNICAL_DEBT.md` once this phase's build is verified.

## 4. Seeding

Phase 3's `WardrobeDatabase.SeedCallback` needed a `CoroutineScope` and a
`() -> WardrobeDatabase` lambda it didn't have a caller for yet. `DatabaseModule` now
supplies both: a `@Singleton` `CoroutineScope(SupervisorJob() + Dispatchers.IO)`
(qualified `@ApplicationScope` in `core:common`, joining the existing dispatcher
qualifiers from Phase 2) and a lazy reference to the database instance being built
(Hilt's `Provider<WardrobeDatabase>` breaks the circular "the callback needs the
database that needs the callback" dependency cleanly).

## 5. Testing strategy for this phase

Repository implementations are tested against a real **in-memory Room database**
(`core:testing`'s helper, Phase 2) rather than mocked DAOs — a repository's whole job
is translating between two real schemas (Room ↔ domain), and a mock DAO would let a
subtly wrong query slip past unnoticed. `BackupRepositoryImpl` is tested against a
temp-directory filesystem (JUnit's `TemporaryFolder`), verifying an export→restore
round trip reproduces the original data, not just that files get written somewhere.
Full instrumented/device testing of the actual `ACTION_CREATE_DOCUMENT` SAF flow is
Phase 8 territory (needs a real device), noted here rather than silently assumed
covered.

## What this phase does not include (stated per Constitution rule 6)

- **`WeatherRepository`** — needs `core:network`, which has zero code yet (Phase 7).
- **`StylingEngineRepository`** — Phase 6.
- **Anything in `core:image`** — Phase 5b, per your milestone split.
- **Any UI** — Phase 5c onward.
