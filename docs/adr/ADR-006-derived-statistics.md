# ADR-006: Derived Statistics, No Persisted "Statistics" Source of Truth

**Status**: Accepted (Phase 1, Section 0 pushback #1 — formalized here)

## Context

The original feature list (`alta-class-closet-app-master-prompt.md` Section 0/database
design) called for a `Statistics` Room entity alongside `Wardrobe`, `Items`, etc. Taken
literally, that means a table of precomputed numbers — usage %, favourite colour,
cost-per-wear — updated whenever the underlying data changes. This is a specific,
well-known failure mode: a second copy of derived truth that drifts from the source
data the moment an update path is missed.

## Decision

No `Statistics` entity holds numbers as a source of truth. Every stat (usage %,
cost-per-wear, dormant items, favourite brand/colour/fabric, weekday-vs-weekend split,
closet gaps) is computed by a Room query over `Garment`/`WearEvent`/`Outfit` — the
existing source of truth — exposed through `core:domain`'s `StatsRepository`. A thin
`StatsCacheEntity` (Phase 1 Section 9) exists **only** as a performance cache for the
handful of genuinely expensive multi-join aggregates, invalidated on the relevant
writes — it is never read as authoritative, only as a memo of a query result.

## Consequences

**Positive**:
- Impossible for a stat to be "wrong" relative to the actual logged data — there is
  only one place the numbers can come from.
- No write path can forget to update a stats table, because there is no stats table to
  update.

**Negative**:
- Some aggregate queries (weekday-vs-weekend split, favourite fabric by month) are
  genuinely expensive at scale; the performance-only cache table adds its own (much
  smaller) invalidation logic to get right.
- Historical trend graphs (item-count/usage over 1mo/6mo/1yr/all-time,
  `phase-1-architecture.md` Section 3) must be computable from raw
  `WearEvent`/`Garment` timestamps rather than from stored snapshots — verified as
  possible in Phase 1 (the raw event log has everything needed), but it means the
  query logic for "what did usage % look like a year ago" is nontrivial and needs real
  test coverage in Phase 8.

## Alternatives Considered

- **Persisted `Statistics` entity, updated eagerly on every relevant write**: rejected
  — this is the literal reading of the original spec, and is exactly the
  two-sources-of-truth risk this decision avoids. Every new write path (there will be
  several: wear logging, garment edit, garment delete, outfit save) would need to
  remember to update it, and any one omission produces silently wrong numbers with no
  natural way to detect the drift.
- **Event-sourcing with periodic snapshot rebuild**: considered as a more principled
  version of the same idea (append-only event log, materialized views rebuilt on a
  schedule) — rejected as overkill for this app's scale (a single user, low thousands
  of rows at most) and deferred; the simpler "recompute on read, cache the expensive
  bits" approach is the right amount of engineering for the actual data volume.
