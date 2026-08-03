# ADR-005: Room for Local Persistence

**Status**: Accepted (Phase 2 — dependency configured, no schema yet)

## Context

The app's data is genuinely relational: a garment has colors, materials, seasons, and
dress codes each with their own filterable many-to-many relationship
(`phase-1-architecture.md` Section 9); outfits reference garments with ordering;
wear events reference exactly one of a garment or an outfit; stats need multi-table
aggregation queries with real indices to stay fast at 1000+ garments (Section 21-23).
This is not a "just store some JSON blobs" problem.

## Decision

Room (`androidx.room`) as the single local database, one `WardrobeDatabase`, with real
`Migration` objects from the first schema version onward — `fallbackToDestructiveMigration`
is deliberately not used, because this is a personal data store users accumulate for
years (`phase-1-architecture.md` Section 9). Schema JSON is exported to
`core/database/schemas/` (`room { schemaDirectory(...) }`, configured in Phase 2) so
migration tests (Phase 8) can diff every historical version.

## Consequences

**Positive**:
- Compile-time-checked SQL (via KSP) instead of hand-written string queries.
- First-class `Flow`/coroutines support and Paging 3 integration
  (`androidx.room:room-paging`), both required by the closet-browse-at-scale and
  reactive-UI requirements already decided.
- Mature migration tooling, actively maintained by Google/Jetpack, with a long track
  record — a meaningful factor for a project scoped to be maintained for years.

**Negative**:
- KSP annotation processing adds build time (mitigated by Gradle's build cache and
  parallel module builds, ADR-001).
- Real migration discipline is required forever once the schema ships — a deliberately
  accepted cost, not an oversight (the alternative, destructive fallback, would delete
  users' wardrobes on every schema change).

## Alternatives Considered

- **Realm**: rejected — MongoDB's long-term Android investment and licensing direction
  were uncertain enough at evaluation time to be a risk for a years-long maintenance
  horizon; Room's Jetpack-native status carries less platform-continuity risk.
- **ObjectBox**: rejected — smaller community/ecosystem than Room, and its query API
  doesn't integrate with Paging 3 / Flow as natively as Room does.
- **SQLDelight**: a reasonable alternative (compile-time-checked SQL, multiplatform-
  capable) — rejected in favor of Room specifically for its tighter first-party
  integration with Hilt, Paging 3, and WorkManager, all of which this project already
  uses; SQLDelight's multiplatform advantage is not yet load-bearing for this project
  (see `phase-1-architecture.md` Section 30 — a future KMP move is planned around
  `core:model`/`core:domain` staying pure Kotlin, not around the persistence layer
  itself being multiplatform-portable).
- **Plain SQLite / raw `SupportSQLiteOpenHelper`**: rejected — no compile-time query
  safety, and migration/DAO boilerplate Room already solves.
