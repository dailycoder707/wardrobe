# ADR-010: Type-Safe Navigation-Compose Across Feature Modules

**Status**: Accepted (Phase 1 Section 6/7, configured in Phase 2)

## Context

8 `feature:*` modules (ADR-001) need to navigate to each other's screens (e.g. Home →
item detail owned by `feature:closet`) without depending on each other's Gradle modules
— cross-feature module dependencies would defeat the modular boundary Clean
Architecture (ADR-002) is meant to enforce. Navigation also needs to be safe: a typo'd
string route or a missing argument should be a compile error, not a runtime crash
discovered by a user.

## Decision

Navigation-Compose with kotlinx.serialization-backed **type-safe routes**
(`@Serializable` route classes/objects, stable since Navigation 2.9.6+). A single
`NavHost` lives in `:app` (`WardrobeNavHost`); each feature module will contribute a
`NavGraphBuilder.xGraph(navController)` extension function that gets registered into
that one `NavHost` starting in Phase 5. Cross-feature navigation targets (e.g. Home
navigating into Closet's item detail) are constructed from a route class any module can
reference without depending on the module that actually owns the destination screen.

## Consequences

**Positive**:
- Route arguments are compile-time typed — no `Bundle`/string-key mismatches.
- No `feature:*` module needs to depend on another `feature:*` module's Gradle module
  just to navigate to it, preserving the modular boundary from ADR-001.
- One `NavHost`/one back stack, matching the single-Activity Compose architecture the
  rest of this app already assumes (`MainActivity` hosts everything).

**Negative**:
- Every feature module that defines a route needs the `kotlinx.serialization` Kotlin
  plugin and `kotlinx-serialization-json` dependency (already wired in Phase 2's
  per-module `build.gradle.kts` files where relevant).
- Route arguments must be serializable, which occasionally constrains what can be
  passed directly through a route (e.g. large objects should be looked up by ID from a
  repository, not passed inline — already the intended pattern, since routes should
  carry IDs like `garmentId`, not whole domain models).

## Alternatives Considered

- **Navigation3**: a newer Jetpack library, reported stable around the time of this
  decision — considered and rejected for now as too new to have the same weight of
  real-world usage and long-term-support confidence as Navigation-Compose 2.9.x for a
  project scoped to be maintained for years. Worth revisiting in a future ADR once it
  has more track record; not a permanent rejection.
- **String-based routes with manual argument (de)serialization**: rejected — exactly
  the runtime-discoverable-typo risk this decision exists to avoid.
- **One `NavHost` per feature module (nested activities or separate graphs entirely)**:
  rejected — fragments the back stack and complicates shared bottom-navigation state
  for no compensating benefit; a single `NavHost` with per-feature graph-registration
  extensions gets the modularity benefit without fragmenting the navigation state.
