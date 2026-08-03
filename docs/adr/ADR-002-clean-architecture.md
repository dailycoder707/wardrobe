# ADR-002: Clean Architecture Layering

**Status**: Accepted (Phase 2, no code yet — layering decided, not implemented)

## Context

The app's core value — reliable closet data, a styling engine, wear stats — needs to
outlive whatever UI framework or persistence library is fashionable in a few years, and
needs to be testable without booting an Android runtime. `phase-1-architecture.md`
Section 1 already assumed a layered design; this ADR records it as a first-class,
revisit-before-changing decision rather than an implicit assumption.

## Decision

Three layers, strictly inward-pointing dependencies:
- **Domain** (`core:model` + `core:domain`): plain Kotlin, repository *interfaces* and
  use cases, zero Android imports.
- **Data** (`core:data` + `core:database`/`core:datastore`/`core:network`/`core:image`):
  repository *implementations*, Room, DataStore, Retrofit, CameraX/image processing.
  Bound to domain interfaces via Hilt `@Binds`.
- **Presentation** (`feature:*`): Compose UI + ViewModels, depends only on
  `core:domain` (use cases) and `core:model`/`core:designsystem`/`core:ui` — never on
  `core:data` or `core:database` directly.

ViewModels call use cases, not repositories directly, so orchestration logic (e.g.
"re-trigger suggestions after recording feedback") lives in one place per use case
rather than being re-implemented per ViewModel.

## Consequences

**Positive**:
- The styling engine, stats calculations, and all domain logic are unit-testable with
  plain JUnit — no Robolectric, no instrumented tests, no Android dependency at all.
- Swapping a data source (e.g. Room for something else, or adding a remote source
  later per `phase-1-architecture.md` Section 30) touches `core:data` only; every
  `feature:*` module is unaffected because it only ever saw the interface.
- Forces an explicit, reviewable seam (the repository interface) at every point where
  the app talks to the outside world.

**Negative**:
- More files and more indirection per feature than calling Room DAOs straight from a
  ViewModel would need — a real cost for an app this size, accepted deliberately for
  the testability and future-extensibility payoff.
- New contributors need to understand the layering rule to avoid accidentally reaching
  past it (e.g. a `feature:*` module adding a direct Room dependency "just this once").

## Alternatives Considered

- **MVVM with ViewModels calling repositories directly, no explicit use-case layer**:
  rejected — it's less ceremony, but it collapses "what the app can do" (domain) and
  "how a specific screen orchestrates it" (presentation) into the same class, which
  tends to duplicate orchestration logic across ViewModels as features grow (e.g. the
  feedback→style-rule→re-suggest flow would otherwise need reimplementing in every
  screen that touches suggestions).
- **Repositories defined and implemented together in one module** (no separate
  `core:domain`/`core:data` split): rejected — it would let `feature:*` modules
  transitively depend on Room/Retrofit/DataStore, defeating the point of the interface
  seam and blocking the Kotlin Multiplatform path noted in ADR-001.
