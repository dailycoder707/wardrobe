# :core:datastore

Preferences DataStore — implemented in Phase 5a. Holds the scalar, single-row,
non-relational parts of `StyleProfile` (occupation, gender preference, free-text
blurb, budget band) and all of `PersonalizationSettings` (display name, greeting
style, custom home title, avatar URI, and the five Home-card visibility toggles) —
see phase-3-persistence.md's decision note on why profile scalars are DataStore and
not Room (the relational parts — preferred brands, avoided categories — are Room
junction tables in `core:database` instead, since they reference other entities).

## Packages
| Package | Holds |
|---|---|
| `preferences/` | `PreferenceKeys` (every key in one place, so nothing is ever declared twice with different types), `StyleProfileDataStore`, `PersonalizationDataStore`, `ClosetPreferencesDataStore` (Phase 5c — sort/grid-density/recent-searches), `StylistPreferencesDataStore` (Phase 6 — the 14-field `RecommendationPreferences`, encoded with the same `U+001F` unit-separator list convention `ClosetPreferencesDataStore` established for `preferredDressCodes`), `WeatherPreferencesDataStore` (Phase 7 — the 8-field `WeatherPreferences`: use-weather/offline-only toggles, device-vs-manual location, temperature unit, refresh interval), `SyncPreferencesDataStore` (Phase 8 — auto-sync/Wi-Fi-only/charging-only toggles), and `di/DataStoreModule` providing the single `DataStore<Preferences>` singleton every one of these shares |

**Never hardcode a display name anywhere else in the app.** Every greeting reads
`PersonalizationSettings.displayName` through `PersonalizationRepository`
(`core:domain`) — that live value is what makes changing it update every open
screen immediately, no restart. See `core:model`'s `greetingText()` for the one
function that assembles greeting copy from it.

Theme/units/grid-density-default/last-backup-time are deliberately **not** here yet
— Phase 3 never defined a domain repository interface for general
`UserPreferences`, only for `StyleProfile` and (added this phase)
`PersonalizationSettings`. Add those keys alongside whatever Phase 5f's Settings
work actually needs, not speculatively now.

## Phase 7 note — a real cross-module smart-cast fix

`WeatherPreferencesDataStore`'s writer originally wrote
`if (preferences.manualLatitude != null) { prefs[...] = preferences.manualLatitude }`
directly against the `WeatherPreferences` (a `core:model` type) property —
this fails to compile ("smart cast to 'TypeVariable(T)' is impossible... the
value is a property declared in a different module") since Kotlin only
smart-casts a nullable property across a null check within the *same* module.
Fixed by capturing each nullable field into a local `val` before the null
check, for all three nullable fields (`manualLatitude`/`manualLongitude`/
`manualLocationLabel`) — not by adding `!!` or suppressing the compiler.

## Phase 8 note — `lastModifiedAt` tracking for sync

`PersonalizationDataStore` and `StylistPreferencesDataStore` each gained
`lastModifiedAt()` (reads a new `*_UPDATED_AT` `longPreferencesKey`) and
`applyFromSync(settings, modifiedAt)` (writes the settings and stamps that
key in one edit) — the scalar-preferences equivalent of a Room row's
`updatedAt` column, letting `core:data`'s sync handlers apply
newest-wins conflict resolution to DataStore-backed settings the same way
they do for database rows, without DataStore itself gaining any concept of
sync.
