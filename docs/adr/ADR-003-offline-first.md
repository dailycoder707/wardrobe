# ADR-003: Offline-First

**Status**: Accepted (Section 0, `alta-class-closet-app-master-prompt.md`)

## Context

The budget-posture decision (zero-cost/offline, made explicitly by the user before
Phase 1) rules out a server. But "no server" and "offline-first" are not automatically
the same thing — an app could still be built network-first and simply break without
connectivity. This ADR records the deliberate design stance, not just the budget
constraint that motivated it.

## Decision

Every core feature (closet browse/edit, wear logging, manual outfit building, stats,
wishlist, trip packing lists) must be fully functional with the device in airplane
mode, permanently, not just when a cache happens to be warm. The **one** exception is
weather-aware suggestions, which make the app's only network call (Open-Meteo) and are
explicitly designed to degrade rather than fail (Constitution rule 8,
`phase-1-architecture.md` Section 18): a stale cached forecast plus a visible "using
yesterday's forecast" signal, never a dead-end error screen.

## Consequences

**Positive**:
- The app works identically on a plane, in a basement, or with a dead data plan — for
  a personal wardrobe tool used getting-dressed-in-the-morning, that's a real
  reliability requirement, not a nice-to-have.
- Forces every screen's state model (`core:ui`'s `ScreenState`, Phase 1 Section 8) to
  represent "offline, using cached data" as a first-class state, which also makes the
  app more robust to slow/flaky connectivity generally.

**Negative**:
- The weather cache/staleness logic (`WeatherRepository`, Phase 1 Section 18) is more
  code than "just fail the request and show an error" would be.
- No cross-device sync of any kind without a manual export/import (see ADR-004) — a
  deliberate trade against ADR-004, not an oversight.

## Alternatives Considered

- **Network-first with offline as a degraded fallback**: rejected — this is the
  default posture of most consumer apps and is exactly what produces the "no signal,
  app is useless" experience this product is explicitly trying not to have.
- **Optional account with cloud sync, offline as a mode**: this is the natural Phase-
  beyond-this-one evolution (`phase-1-architecture.md` Section 30 lists Cloud Sync as a
  planned-not-implemented future feature) but was rejected for v1 specifically because
  it re-opens the No Cloud decision (ADR-004) that the budget posture already settled.
