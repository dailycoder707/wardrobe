# ADR-014: User Profile and AI-First Home Dashboard (M15 Parts 4–5)

**Status**: Accepted (implementation milestone, added 2026-08-07, extending
ADR-012's provider architecture and Phase 5c/7/9's Home dashboard work)

## Context

M15's brief asked for two things: a real, persistent user Profile/Settings
screen (Part 4), and a redesign of Home into something that reads as an AI
wardrobe assistant rather than a database front-end (Part 5), using only
real data. Before writing any code, this milestone's own instructions
required inspecting what already existed — that inspection changed the
scope of both parts substantially from what a from-scratch reading of the
brief would suggest.

## Decision

### 1. Identity persistence reuses `PersonalizationSettings`/`PersonalizationDataStore` — no second store

`core:model/profile/PersonalizationSettings.kt` already had
`displayName: String?` and `avatarImageUri: String?`, fully wired through
`core:datastore`'s `PersonalizationDataStore` (Preferences DataStore,
single shared `wardrobe_preferences` file), `core:domain`'s
`PersonalizationRepository`, and `core:data`'s `PersonalizationRepositoryImpl`
— already bound in Hilt and already read by `HomeViewModel` for the
greeting. The only gap was that nothing in the app ever *wrote* a name:
`setDisplayName` had no caller in production code, and no screen existed
to call it. Part 4 is therefore an editing UI plus real input validation,
not a new persistence layer — building a second `UserProfile`
model/table/DataStore file would have been exactly the "duplicate
persistence architecture" the brief explicitly ruled out.

### 2. Name validation lives in the ViewModel layer as a pure function, not in the DataStore

`PersonalizationDataStore.setDisplayName` still just trims and stores
whatever it's given (unchanged) — it's also the target of Phase 8's sync
`applyFromSync` path, which must accept an already-validated remote value
without re-deriving UI-facing error states. `NameValidation.kt`
(`feature:settings/profile/`) is a pure `validateName(raw): NameValidationResult`
(`Valid`/`Blank`/`TooLong`) that `ProfileViewModel.onSaveName()` calls
before ever touching the repository — an invalid draft leaves the
persisted name completely untouched and surfaces `ProfileUiState.nameError`
instead, satisfying "do not silently replace the user's name with a
default after saving." Length is validated via `String.length` (UTF-16
code units), never transliterated, so Unicode names round-trip exactly.
Max length (50) is a stated, reasonable default — no existing precedent in
this codebase constrained it either way.

### 3. Avatar upload reuses `GalleryImportSource`, extended with one new method

`core:image/capture/GalleryImportSource.kt` already wrapped the Photo
Picker contract and a `copyToTempFile(uri, destination)` primitive for
garment photos. The Photo Picker's granted `Uri` is not a persistable
grant (unlike the legacy document picker) and does not reliably survive
process death, so "restart the app and still see it" requires copying the
picked image into this app's own storage immediately — exactly what
garment photos already do. `copyAvatarImage(uri): File` is a small,
additive method on the existing class (always the same destination file,
`filesDir/profile/avatar.jpg` — a re-pick overwrites it) rather than a new
class or a new core module. `feature:settings` gained a direct dependency
on `core:image` and `coil.compose` to use it and render the result — the
same "implementation, not api" scoping `core:ui`'s `GarmentTile` already
uses for the same library, and the same kind of narrow, documented
feature→core exception `feature:closet`'s Developer Panel (`core:image`)
and `feature:settings`'s own Pairing screen (`core:sync`) already
established.

### 4. The app version needs a new qualified Hilt binding, `@AppVersion String`

Only the `app` module's generated `BuildConfig` has a real `VERSION_NAME`
— library modules don't get one. Rather than give `feature:settings` a
dependency on `app` (a real layering violation — `app` depends on every
feature module, never the reverse), `feature/settings/di/AppVersion.kt`
declares a `@Qualifier` annotation (mirroring `core:ai`'s `AiHttp`
precedent for the same "avoid an unqualified-`String`/`OkHttpClient`
collision" reason), and `app/di/AppVersionModule.kt` provides the real
`BuildConfig.VERSION_NAME` bound to it. Hilt aggregates modules
app-wide, so `ProfileViewModel` (in `feature:settings`) can inject it
without ever seeing `app`'s `BuildConfig` directly.

### 5. Profile does not duplicate AI Providers, Wardrobe Sync, or Style Preferences — it links to them

Per the brief's explicit non-negotiable, `ProfileScreen`'s AI/Wardrobe/App
sections are compact real-data summaries (`"N of 5 using Cloud AI"`,
`"<device> · last synced <time>"`) that navigate to the existing
`AiProvidersRoute`/`WardrobeSyncRoute`/`StylistPreferencesRoute` screens
for anything mutable. `ProfileViewModel` reads `AiProviderSettingsRepository`
and `SyncRepository` read-only for those summaries; it never calls their
mutating methods. `feature:settings/README.md`'s `profile/` package
description (written in Phase 7, before this milestone) described a
*style*-preference screen (occupation, sizing, budget) that — as this
milestone's own inspection found — was actually already built, in
`feature:outfits/preferences/StylistPreferencesScreen.kt`, under a
different name. This ADR's Part 4 profile screen is the *identity*
profile (name, avatar) the brief actually asked for; the README is
corrected alongside this change to stop pointing at stale, superseded
Phase 5f planning notes.

### 6. Home is a real, already-substantial assistant screen — Part 5 is an extension, not a rewrite

`feature:closet/home/` (`HomeScreen`, `HomeViewModel`, `HomeUiState`,
`HomeAssistantUiState`) already computed a personalized greeting, a daily
weather line, a scored outfit recommendation with a real "why" explainer,
a wardrobe health/rotation score, an attention-items count, an upcoming
trip reminder, and a laundry reminder — all from real repositories
(`WardrobeIntelligenceRepository.buildDailyBrief`, `StatsRepository`,
`WeatherRepository`, `TripRepository`). Rewriting this to satisfy Part
5's letter would have violated its own instruction not to replace working
architecture for cosmetic reasons. This milestone adds three genuinely
new, real pieces on top:

- **A Profile entry point** — a small tappable avatar (or a person icon
  when none is set) in Home's header, navigating to `ProfileRoute`. This
  is the only discoverable path to Profile anywhere in the app (there is
  still no navigation-dock tile or drawer for it, consistent with
  `feature:settings/README.md`'s standing note that a Settings hub tile
  is deferred), so without it Part 4's screen would be unreachable.
- **Recent AI Activity** — genuinely new. `ai_call_log`/`AiCallLogDao`
  already existed (Add-to-Wardrobe v2) but its only consumer was the
  Settings screen's per-capability `AiUsageSummary` aggregate. A new
  `AiActivityEntry` domain model and `AiProviderSettingsRepository
  .observeRecentActivity(limit)` (implemented as a thin chronological
  projection of the same `observeAll()` flow `observeUsageSummaries`
  already reads — no second write path, no new table) feed a small,
  real, newest-first feed on Home. Absent entirely — never an empty
  placeholder row — until at least one AI capability has actually run.
- **A Cloud AI configuration nudge** — shown only when
  `AiProviderConfig.isCloudReady()` is false for every one of the app's 5
  capabilities (the exact same check `AiProvidersScreen`/`AiProvidersViewModel`
  already use), linking to Profile. Absent the instant any capability is
  configured, and absent entirely while the assistant state is still
  loading — never a flash of "not configured" before the real answer is
  known.

`HomeAssistantRepositories` (the existing "bag of repositories" DI-arity
helper) gained one field, `aiProviderSettingsRepository`; `HomeUiState`
gained `avatarImageUri` (part of the already-reactive `uiState` combine
chain, since personalization was already being read there); `HomeAssistantUiState`
gained `recentAiActivity`/`cloudAiConfiguredCount`/`totalAiCapabilities`
(computed alongside the existing one-shot `loadAssistantState()` call,
the same non-blocking pattern weather/recommendation/health-score already
use).

## Consequences

- Profile and Home now share one identity source
  (`PersonalizationRepository`) and one AI-status source
  (`AiProviderSettingsRepository`) — a name or avatar change made in
  Profile is reflected on Home immediately (both are `Flow`-backed), with
  no manual refresh and no second cache to go stale.
- The empty-state discipline already established elsewhere in this app
  (`EmptyState`, absent-not-placeholder cards) extends cleanly to the two
  new Home sections: a user with no AI history sees no Recent Activity
  section at all, not a fabricated "no activity yet" row; a user with
  every capability on-device sees the configuration nudge; a user with
  any capability configured sees neither the nudge nor a fabricated
  "fully configured" claim.
- `feature:settings` gained two new external dependencies
  (`core:image`, `coil.compose`) purely for the avatar picker — both are
  already-vetted libraries used elsewhere in the app for the identical
  purpose, not new architecture.
- Disclosed, deliberate scope decisions (not gaps found by accident):
  the Profile screen's max name length (50) is a stated default with no
  prior precedent to match; the avatar is a single fixed file (no
  history/crop/multiple photos); the AI Providers/Wardrobe Sync/Style
  Preferences links show a compact real summary, not their full
  underlying state, by design (§5 above).
- No destructive migration: every change is additive (new model fields
  with defaults, one new DataStore-backed getter path already existed,
  one new domain method, one new Hilt qualifier+binding). No existing
  table, DataStore key, or repository method signature was removed or
  changed incompatibly.
