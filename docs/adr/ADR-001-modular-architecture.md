# ADR-001: Modular Gradle Architecture

**Status**: Accepted (Phase 2)

## Context

This project is scoped for years of maintenance (per the original brief), with an
ambitious feature surface even after cutting the CUT-tier items in
`alta-class-closet-app-master-prompt.md` (avatar try-on, shopping, community, etc.). A
single-module app would compile everything on every change and give every file access
to every other file, with nothing enforcing the layering `phase-1-architecture.md`
Section 1 requires (features never touching Room/Retrofit/DataStore directly).

## Decision

Split into 20 Gradle modules: `app`, `benchmark`, 11 `core:*` modules (`model`,
`domain`, `common`, `database`, `datastore`, `network`, `image`, `data`,
`designsystem`, `ui`, `testing`), and 8 `feature:*` modules (`closet`, `outfits`,
`calendar`, `stats`, `wishlist`, `trips`, `settings`, `widget`). Module boundaries are
enforced by Gradle dependency declarations, not just convention: a `feature:*` module
physically cannot `implementation(project(":core:database"))` without that line
existing in its `build.gradle.kts`, which makes an architecture violation a visible
diff, not a runtime discovery.

## Consequences

**Positive**:
- Gradle only recompiles/reprocesses (KSP, etc.) the modules actually touched by a
  change, and Gradle's build cache can reuse untouched modules' outputs.
- Module boundaries are physically enforced, not just documented.
- Each module has a clear, single owner-able responsibility, documented in its own
  `README.md`.
- `core:model`/`core:domain` being plain Kotlin (no Android dependency at all) keeps a
  Kotlin Multiplatform migration (ADR territory for a future decision, noted in
  `phase-1-architecture.md` Section 30) additive rather than a rewrite.

**Negative**:
- 20 `build.gradle.kts` files to keep consistent (mitigated today by hand-verifying each
  one against a real build in Phase 2; see the "convention plugins" alternative below
  for the tradeoff this implies).
- Cross-cutting refactors (e.g. renaming a domain model field) touch multiple modules'
  worth of files instead of one.
- Higher Gradle configuration-time overhead than a single module, though `org.gradle.
  parallel=true` and the configuration cache (both enabled in `gradle.properties`)
  offset most of this in practice.

## Alternatives Considered

- **Single module**: rejected — no compiler-enforced layering at all; every file can
  import every other file, which is exactly what Clean Architecture (ADR-002) is meant
  to prevent.
- **One module per screen (~35 modules)**: rejected — the per-module Gradle-config
  overhead (build file, version catalog wiring, Hilt module registration) outweighs the
  incremental-build benefit at that granularity for a project this size. Feature-area
  granularity (one module per bottom-nav destination's feature) is where the tradeoff
  starts paying for itself.
- **Precompiled Gradle convention plugins** (a `build-logic` composite build, as
  sketched conceptually in `phase-1-architecture.md`'s package tree): considered for
  Phase 2 to reduce the copy-pasted plugin blocks across the 8 `feature:*` modules, but
  deliberately deferred — see `DEPENDENCIES.md`'s decision table. Every module's build
  file was hand-verified against `./gradlew build` in Phase 2; adding a composite-build
  layer on top would have meant verifying convention-plugin script compilation *in
  addition to* 20 modules' real dependency resolution, for a real but non-urgent
  benefit. Revisit once per-module duplication actually starts causing drift (e.g. a
  version bump applied to some `feature:*` modules but not others).
