# Architecture Decision Records

One decision per file, in the standard Context / Decision / Consequences / Alternatives
Considered format. These are the *why* behind choices already detailed technically in
`phase-1-architecture.md` and `alta-class-closet-app-master-prompt.md` — read those for
implementation detail, read these for the reasoning and the road not taken.

New ADRs should be added as `ADR-0NN-short-title.md`, numbered sequentially, and never
renumbered or deleted once accepted — if a decision is later reversed, add a new ADR
that supersedes it and says so explicitly, rather than editing history.

| # | Title | Status |
|---|---|---|
| [001](ADR-001-modular-architecture.md) | Modular Gradle Architecture | Accepted |
| [002](ADR-002-clean-architecture.md) | Clean Architecture Layering | Accepted |
| [003](ADR-003-offline-first.md) | Offline-First | Accepted |
| [004](ADR-004-no-cloud.md) | No Cloud, No Accounts, No Third-Party SDKs | Accepted |
| [005](ADR-005-room-database.md) | Room for Local Persistence | Accepted |
| [006](ADR-006-derived-statistics.md) | Derived Statistics, No Persisted Source of Truth | Accepted |
| [007](ADR-007-image-storage-strategy.md) | Image Storage Strategy | Accepted |
| [008](ADR-008-background-removal-abstraction.md) | Background Removal as a Swappable Abstraction | Accepted |
| [009](ADR-009-backup-exclusion.md) | Exclude App Data from Android Auto Backup's Cloud Channel | Accepted |
| [010](ADR-010-navigation-strategy.md) | Type-Safe Navigation-Compose Across Feature Modules | Accepted |
| [011](ADR-011-permanent-privacy-first-principles.md) | Permanent Privacy-First, Offline-First Product Principles | Accepted |
| [012](ADR-012-cloud-ai-provider-amendment.md) | Amending the Permanent Privacy Principles to Allow Pluggable Cloud AI | Accepted |
| [013](ADR-013-m12-cloud-styling-and-tryon.md) | Cloud Outfit Styling and Cloud Virtual Try-On (M12) | Accepted |
