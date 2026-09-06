# ADR-015: First-Run Onboarding (M16 Part 6)

**Status**: Accepted (implementation milestone, added 2026-08-07, extending
ADR-014's Profile/Personalization architecture and Phase 6's Stylist
Preferences)

## Context

M16's brief asked for a real first-run onboarding flow — Welcome, Name,
Style Preferences, AI/Privacy, Finish — that collects genuinely-used
information rather than a marketing slideshow, and that must never make an
existing install repeat it. Before writing any code, this milestone's own
instructions required inspecting what M15 (Profile) and Phase 6 (Stylist
Preferences) already built — that inspection determined nearly the entire
shape of this implementation.

## Decision

### 1. No new profile/preferences model — every screen writes through an existing repository

- **Name** persists through `PersonalizationRepository.setDisplayName`
  (`core:domain`, already built in M15) — the exact same repository
  Profile's own name field uses.
- **Style** persists through `StylistPreferencesRepository.setPreferences`
  (`core:domain`, Phase 6) — the exact same repository the standalone
  Stylist Preferences screen (`feature:outfits`) and the real
  `RecommendationRuleEngine` scoring already use.
- **AI** writes nothing at all — see §4.

This directly satisfies the brief's non-negotiables 5–7 (no duplicate
profile/style-preference/AI-provider storage). A genuinely orphaned
alternative was found and deliberately *not* used: `StyleProfileRepository`/
`StyleProfile` (`core:domain`/`core:data`, Phase 3) — occupation, sizing,
budget, preferred brands — is fully persisted and Hilt-bound but has **zero
callers anywhere in production code** (no screen ever reads or writes it).
Onboarding's Style step does not surface any of its fields, per the brief's
explicit "only expose preferences with a real downstream effect" rule. This
is flagged in `TECHNICAL_DEBT.md` as a genuine, pre-existing finding, not
introduced by this milestone.

### 2. `NameValidation` moved from `feature:settings` to `core:domain`

M15's `validateName`/`NameValidationResult`/`MAX_DISPLAY_NAME_LENGTH` lived
in `feature:settings/profile/`, `internal` to that module. Onboarding's Name
step needs the exact same validation (the brief says so explicitly: "Reuse
NameValidation"), but `feature:onboarding` cannot depend on
`feature:settings` — this app's standing rule is that feature modules only
depend on `core:*`, never on each other (only `app` composes them). The
function was relocated verbatim to `core:domain/profile/NameValidation.kt`
(now public, not `internal`) — both `feature:settings`'s `ProfileViewModel`
and `feature:onboarding`'s `OnboardingNameViewModel` import the identical
function from there. Nothing about its behavior changed; its test suite
moved with it.

### 3. First-run detection is a `Flow` derivation, never a one-time destructive migration

`core:datastore`'s new `OnboardingDataStore` (mirrors every other
`*DataStore` class in that shared `Preferences` file exactly) owns one raw
boolean, `PreferenceKeys.ONBOARDING_COMPLETED`, only ever written `true`
(never written `false` — the app has no "reset onboarding" action).
`OnboardingRepositoryImpl` (`core:data`) combines that flag with two real,
independent signals via `combine(...)`, recomputed live, never written back:

```kotlin
explicitlyCompleted || personalization.displayName != null || hasAnyGarment
```

`hasAnyGarment` is new: `GarmentDao.observeHasAnyGarment()`
(`SELECT EXISTS(SELECT 1 FROM garments)`) exposed through
`GarmentRepository.observeHasAnyGarment()`. This means an existing install
— one that added a garment or set a name any time before this version
shipped, at M13, M14, or M15 — is treated as already onboarded the very
first time this logic runs, with no migration step, no schema flag, no
one-time write, and therefore no race or "wrote the wrong thing before the
real data loaded" risk. A genuinely fresh install (no name, no garment, no
explicit flag) is the only case that ever sees Onboarding.

This was chosen over a Room-migration-style backfill (like `Migration8To9`'s
`INSERT OR IGNORE` backfills elsewhere in this app) specifically because the
signal here is a DataStore boolean, not a queryable column — there is
nothing for a `Migration` object to backfill, and a purely reactive
derivation is strictly safer than a startup-time write for exactly the kind
of "don't lose or misclassify existing users" guarantee this milestone
requires.

### 4. `WardrobeNavHost` gates its own `startDestination` on this signal — one `NavHost`, no second stack

`OnboardingGateViewModel` exposes `StateFlow<Boolean?>`
(`null` = still loading, `true`/`false` once resolved) over
`OnboardingRepository.observeIsComplete()`. `WardrobeNavHost()` composes a
blank frame while `null`, then composes its one existing `NavHost` with
`startDestination = if (complete) HomeRoute else OnboardingWelcomeRoute`.
The five onboarding screens (`OnboardingWelcomeRoute` →
`OnboardingNameRoute` → `OnboardingStyleRoute` → `OnboardingAiRoute` →
`OnboardingFinishRoute`) are five more flat, top-level `composable<Route>`
registrations in that same graph — not a nested `navigation(...)` sub-graph,
not a second `NavController`. Each screen persists directly through its own
already-existing repository as the user goes (§1), so there is no
cross-screen draft state that would have justified a shared, graph-scoped
ViewModel; every onboarding ViewModel is small and screen-specific, the
same convention every other screen in this app already follows.

Finish and Welcome's "Skip" both navigate to `HomeRoute` with
`popUpTo(OnboardingWelcomeRoute) { inclusive = true }`, clearing the entire
onboarding run from the back stack — Back from Home never re-enters it.
`OnboardingAiRoute`'s "Configure AI" is a plain forward navigation to the
*existing* `AiProvidersRoute` (no `popUpTo`), so Back from there returns to
the AI step exactly like any other screen-to-screen hop in this app —
`AiProvidersScreen` needed zero changes.

### 5. AI/Privacy step writes nothing, ever

`OnboardingAiScreen` is pure explanation plus two navigation callbacks —
"Configure AI" (→ the real `AiProvidersRoute`) and "Continue with on-device
AI" (→ `OnboardingFinishRoute`, no repository call at all, since on-device
is already every capability's real default,
`AiProviderConfig.onDeviceDefault`). There is no local "skip" for this step
distinct from "continue with on-device," since on-device *is* the
skip-equivalent state — adding a second button for the same outcome would
be exactly the "unnecessary screens/complexity" the brief warns against.

## Consequences

- Editing a name or a style preference from Profile or the standalone
  Stylist Preferences screen after onboarding, or the reverse, always
  agrees — there is exactly one write path for each, observed reactively
  by every screen that reads it (Home's greeting, Profile, Onboarding
  itself if somehow revisited).
- Skipping every optional step (Welcome's "Skip setup," or Name/Style
  individually) never writes fabricated data — verified directly:
  `OnboardingNameViewModelTest`'s "never saving leaves the name genuinely
  unset" and `OnboardingStyleViewModelTest`'s "never calling update
  preserves the existing defaults untouched" both assert the *fake*
  repository's backing state was never touched, not just that the UI
  looked right.
- `feature:onboarding` is a new Gradle module, depending only on
  `core:model`/`core:domain`/`core:designsystem`/`core:ui` — the same
  layering every other feature module already follows. `app` gained one new
  dependency (`androidx.hilt.navigation.compose`) it didn't need before,
  since `WardrobeNavHost` itself now resolves a Hilt ViewModel
  (`OnboardingGateViewModel`) directly, not just the feature screens it
  composes.
- No destructive migration, no existing DataStore key removed or changed,
  no existing repository method signature broken beyond the additive
  `GarmentRepository.observeHasAnyGarment()` (every existing implementer —
  production and the six per-feature-module test fakes — was updated to
  implement it, never left unimplemented or stubbed).
- Genuine, disclosed technical debt found (not introduced) by this
  milestone: `StyleProfileRepository`/`StyleProfile` is fully-built,
  Hilt-bound, and completely unused dead code (§1). Recorded in
  `TECHNICAL_DEBT.md`, not silently left for a future milestone to
  rediscover.
