# :app

Composition root only. Owns `WardrobeApplication` (`@HiltAndroidApp`), `MainActivity`
(`@AndroidEntryPoint`), and the single top-level `NavHost` (`navigation/`) that every
feature module's screens get registered into starting Phase 5.

This module should never contain feature logic — if you're about to add a
ViewModel, a repository, or a screen here, it belongs in a `feature:*` or `core:*`
module instead. `:app`'s only job is to wire everything else together and hold the
Android-required entry points that can't live anywhere else (the `Application`
class, the launcher `Activity`, the manifest).

## What's real right now (as of Phase 5d)
- Hilt, Navigation-Compose (type-safe routes), and the Material3 theme are wired
  and building.
- `WardrobeNavHost` registers four top-level nav-dock destinations — Home,
  Closet, Saved Looks (`feature:outfits`), and Calendar (`feature:calendar`) —
  plus their detail/builder routes (Garment Detail, Edit Garment, Outfit
  Builder, Outfit Detail) and the debug-only Developer Panel route (never
  registered in release builds). Each destination group is split into its own
  private `NavGraphBuilder` extension (`closetDestinations`,
  `outfitsDestinations`, `calendarDestinations`) to keep the host itself short.
- Release signing reads from a gitignored `keystore.properties` at the repo root
  (see `keystore.properties.example`); without it, `assembleRelease` still
  succeeds by falling back to debug signing.
- `backup_rules.xml` / `data_extraction_rules.xml` exclude the Room DB, the
  images directory, DataStore, and SharedPreferences from Android's cloud Auto
  Backup — see `phase-1-architecture.md` Section 0/24 for why this matters.

See `/phase-1-architecture.md` for the full architecture this module wires
together, and `phase-5c-wardrobe-experience.md`/`phase-5d-wardrobe-stylist.md`
for how the nav dock grew from one placeholder screen to its current shape.

## Phase 7 additions
- `WardrobeApplication.onCreate()` now calls
  `WeatherRefreshWorker.schedule(WorkManager.getInstance(this))` — the
  periodic (~3h default) weather-cache refresh job, scheduled once at process
  start alongside the pre-existing image-cleanup worker.
- `WardrobeNavHost` gained `feature:settings`' `WeatherSettingsRoute` (reached
  from `feature:outfits`' Recommendations screen, not from a nav-dock
  destination or a Settings hub — none exists yet), and `HomeRoute`'s
  `HomeScreen` call site gained `onOpenRecommendations` so tapping Home's new
  recommendation preview card navigates to the full Recommendations screen.

## Phase 8 additions
- `WardrobeApplication.onCreate()` also calls
  `SyncWorker.schedulePeriodic(WorkManager.getInstance(this), wifiOnly = true,
  chargingOnly = false)` — the default periodic background-sync job,
  scheduled alongside the pre-existing image-cleanup and weather-refresh
  workers.
- `AndroidManifest.xml` gained `ACCESS_NETWORK_STATE`/`ACCESS_WIFI_STATE`/
  `CHANGE_WIFI_MULTICAST_STATE` permissions for NSD-based peer discovery
  (`CAMERA` was already present from Phase 5b's capture pipeline, reused here
  for QR scanning).
- `WardrobeNavHost` gained `feature:settings`' `WardrobeSyncRoute`/
  `PairingRoute`, and `RecommendationsScreen`'s call site gained
  `onOpenWardrobeSync`.
- A new `core:sync` module joins the 21-module build — see its own README
  for why it's a sibling to `core:network` rather than folded into
  `core:data`.

## Phase 9 additions

`WardrobeNavHost` gained `feature:outfits`' two new routes (`CapsulesRoute`,
`DuplicateGarmentsRoute`, both reached from Recommendations) and a new
`tripsDestinations(navController)` extension registering `feature:trips`'
first-ever screens (`TripsRoute`, `TripDetailRoute`, `PackingRoute`) — that
module had been a registered-but-empty placeholder since Phase 1 until this
phase built its UI. `HomeScreen`'s call site gained `onOpenTrips`. No new
Gradle module, no new manifest permissions, no new WorkManager scheduling —
this phase's only `:app`-level change is nav wiring for pre-existing
modules' new screens.
