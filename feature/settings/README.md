# :feature:settings

Style profile, style rules (viewable/editable/deletable — Constitution: a
persistent rule must always be traceable to the feedback that created it, never a
silent black box), and Backup/Restore/Export/Import.

## Packages
| Package | Screen(s) |
|---|---|
| `profile/` | **Built in M15** — `ProfileScreen`/`ProfileViewModel`: the *identity* profile (display name, avatar), editing `PersonalizationSettings` — see `docs/adr/ADR-014-m15-user-profile-and-ai-home.md`. Also shows a compact real AI-provider/sync status summary, linking to `aiproviders/`/`sync/` rather than duplicating them. (This package's original Phase 7 description named a *style*-preference screen — occupation, sizing, budget, preferred brands — that turned out to already be built elsewhere, as `feature:outfits/preferences/StylistPreferencesScreen.kt`, under a different name; that content lives there, not here.) |
| `rules/` | The style-rules list — human-readable, user-extendable, deletable — **not built yet, Phase 5f territory** |
| `backup/` | Backup/Restore — foreground-service-backed WorkManager jobs with a visible progress notification (Phase 1 Section 19/20) — **not built yet, Phase 5f territory** |
| `exportimport/` | Export a single look/item as an image/PDF via the Android share sheet — this is also the mechanism replacing "friends/private sharing" (Section 0 DEFER) — **not built yet, Phase 5f territory** |
| `weather/` | **Built in Phase 7** — `WeatherSettingsScreen`/`WeatherSettingsViewModel`: use-weather/offline-only/use-device-location toggles, manual latitude/longitude/label fields (shown when device location is off, requesting `ACCESS_COARSE_LOCATION` via `rememberLauncherForActivityResult` when device location is turned on), temperature unit selector, refresh-interval stepper. Every change persists through `WeatherPreferencesRepository` and calls `WeatherRefreshScheduler.reschedule(...)` so a changed interval takes effect without an app restart |
| `sync/` | **Built in Phase 8** — `WardrobeSyncScreen`/`SyncViewModel` (connected device, last sync, pending changes, storage used, Manual Sync, preferences, unresolved conflict cards, sync history, Export/Restore Backup) and `PairingScreen`/`PairingViewModel` (Show QR / Scan QR tabs — `CameraQrScanner` wraps a CameraX `ImageAnalysis` frame feed decoded by `core:sync`'s `PairingQrCodec`) |
| `navigation/` | This module's nav graph extension — `WeatherSettingsRoute` (Phase 7), `WardrobeSyncRoute`/`PairingRoute` (Phase 8, split into two files to avoid `MatchingDeclarationName` — the same lesson Phase 7's `SettingsRoutes.kt` incident already established) |

This is the only `feature:*` module besides `closet` that schedules `WorkManager`
jobs directly — currently `WeatherRefreshScheduler`'s reschedule call (Phase 7);
`androidx.hilt:hilt-work` was already wired in for the backup/restore work Phase
5f will add.

## Phase 7 — this module's first real code

Before this phase, `feature:settings` was a fully-scaffolded, zero-source-file
module (a complete `build.gradle.kts` with the Compose/Hilt/WorkManager
dependencies already declared, but nothing to build). `WeatherSettingsScreen`/
`WeatherSettingsViewModel`/`WeatherSettingsRoute` are its first screen, reached
today via a "Weather" top-bar action on `feature:outfits`' Recommendations
screen — **there is no Settings hub screen yet**, so this is not reachable from
a general Settings entry point. When Phase 5f's profile/rules/backup/
exportimport screens and navigation shell are built, Weather Settings should be
folded into that hub rather than left as its own separate entry point.

No screens beyond Weather Settings existed before Phase 8 — the rest is
still Phase 5f (profile, style rules, backup/restore, export/import,
navigation shell) territory.

## Phase 8 — Wardrobe Sync and Pairing

This module's second and third real screens. `feature:settings` is the one
`feature:*` module that depends directly on `core:sync` (not just
`core:domain`) — needed for `PairingQrCodec.decode` on every analyzed
camera frame, since that decode has to happen off the ViewModel/repository
layer to keep up with the camera feed. This mirrors the Developer Panel's
pre-existing direct `core:image` dependency, not a new kind of exception.
`CameraPreviewAnalyzer` uses a plain `AtomicBoolean` one-shot gate rather
than Compose state to flip once a QR is decoded, since writing Compose
snapshot state from a non-main CameraX analysis thread is itself a bug
class this deliberately avoids. `feature:settings/build.gradle.kts` needed
`testOptions { unitTests.isIncludeAndroidResources = true }` added — its
first Compose UI test (`WardrobeSyncScreenTest`) crashed with a
`RoboMonitoringInstrumentation` exception without it; `feature:closet`
already had this setting from its own earlier Compose UI tests.

## M15 — Profile (identity)

This module's fourth real screen, `profile/`. Reached from a new avatar
button in `feature:closet`'s Home header (still the only entry point —
the Settings-hub gap noted above is unchanged). Edits
`PersonalizationRepository` (already existed, `core:domain`/`core:data`)
directly — no new persistence. Gained two new dependencies purely for the
avatar picker: `core:image` (reuses `GalleryImportSource.copyAvatarImage`,
a small addition to that existing class) and `coil.compose` (renders the
result — the same library, same `implementation`-only scoping, `core:ui`'s
`GarmentTile` already uses). See `docs/adr/ADR-014-m15-user-profile-and-ai-home.md`.
