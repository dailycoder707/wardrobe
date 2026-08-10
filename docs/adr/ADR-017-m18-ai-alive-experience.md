# ADR-017: App-Wide "AI is Alive" Experience (M18 Part 8)

**Status**: Accepted (implementation milestone, added 2026-08-08, extending
ADR-012's provider architecture and M15's Home AI-activity work)

## Context

M18's brief asked for AI state, activity, capability availability, and
provenance to be surfaced honestly throughout the app — never a decorative
"AI" label, never fabricated activity, confidence, or provider names.
Before writing any code, this milestone's own instructions required
inspecting the existing AI infrastructure and every AI-facing screen —
three parallel research passes covering `AiProviderSettingsRepository`,
`AiGateway`/`AiJobManager`, `AiResultProvenance`, Home, Recommendations,
Add-to-Wardrobe/Review, Try-On, Styling, and AI Providers. That inspection
found substantially more already built than the brief's framing implied,
and one genuine, concrete gap. This ADR records both.

## What already existed (not rebuilt)

- **`AiProviderSettingsRepository`** — the single, authoritative seam onto
  provider config, consent, usage aggregation (`observeUsageSummaries`),
  and a chronological activity feed (`observeRecentActivity`, `ai_call_log`).
- **`AiResultProvenance`** (source/provider/model/promptVersion/
  generatedAt/cacheHit/latencyMs) — already attached to `MetadataSuggestion`
  (Add-to-Wardrobe review) and `ScoredOutfit` (Recommendations).
- **Home's "Recent AI Activity" and Cloud-AI-nudge cards** (M15) — real,
  already reading `ai_call_log` and `AiProviderConfig.isCloudReady()`, never
  fabricated placeholder rows.
- **Recommendations' "AI Styled" badge** — a full provider/confidence/reason
  dialog, already shown whenever `ScoredOutfit.provenance != null` (a cloud-
  styled outfit), plus a direct "AI" nav button to AI Providers already in
  the top bar.
- **Add-to-Wardrobe's `AiStatusCard`/`WhatAiChangedSummary`/
  `QualityWarningBanner`/`RetryActionsRow`** (M14) — a real post-analysis
  summary (provider, confidence, processing time, cache hit) and a bullet
  list built entirely from actual pipeline output, plus real (indeterminate,
  never percentage) stage-name progress at the import-queue level
  ("Isolating garment…", "Reconstructing hidden areas…", etc.).
- **Try-On's Compare screen** (M12) — full source/confidence per render tab
  (Original/On-Device/Cloud).
- **AI Providers screen** — the authoritative, complete mode/vendor/
  consent/test-connection/usage UI; every new entry point this milestone
  adds routes into it, none duplicate it.

None of the above was rebuilt or touched beyond what's listed under
"Changed" below.

## What M18 actually added

### 1. A real "is AI currently doing something" signal (the one genuine gap)

`AiJobManager`'s own job ledger (`ai_jobs` / `AiJobDao` / `AiJobStatus`
`PENDING`/`RUNNING`/`SUCCEEDED`/`FAILED`/`CANCELLED`) already existed and
already matches the brief's job-state vocabulary closely — but **no
repository method or UI anywhere read it**. Every dispatched capability call
already writes/updates a row here; M18 only reads it, via one new
repository method:

```kotlin
// AiProviderSettingsRepository
fun observeActiveOperations(): Flow<List<AiActiveOperation>>
```

backed by `AiJobDao.observeAll()` filtered to the two non-terminal statuses
and mapped to a new, minimal model:

```kotlin
data class AiActiveOperation(val capability: AiCapability, val status: AiJobStatus, val startedAt: Instant)
```

This is the *only* new domain model M18 introduces (Phase 2's "only if the
existing model doesn't already provide an adequate equivalent" — it
didn't). It deliberately carries no `provider`/`model`/`confidence`: those
aren't known yet while a job is still `PENDING`/`RUNNING` — only
`AiResultProvenance`, attached once a real result exists, ever carries
them. Showing a provider name before dispatch resolves one would be
fabrication.

No new table, no new write path — `AiJobManager` already wrote every row
this reads.

### 2. `AiActivityBanner` (`core:ui`) — one reusable component, not a template library

A single composable, `AiActivityBanner(label, tone, actionLabel?, onAction?)`,
with four tones (`RUNNING`/`SUCCESS`/`FAILED`/`INFO`). It never generates
its own copy — every string is supplied by the caller, who already knows
the real state. Deliberately named to avoid colliding with
`feature:capture`'s existing, unrelated `AiStatusCard` (a post-hoc
processing summary, not a live status line) discovered during inspection.

This is this codebase's first use of `Modifier.semantics { liveRegion = ... }`
(on the label `Text`, not the whole row, so a trailing action button stays
independently focusable for screen readers) — a deliberate new
accessibility precedent for dynamic AI-status text, documented here rather
than silently introduced.

### 3. Home — the only screen that gets the new "running" banner

`HomeViewModel` gained two new live collectors (mirroring the existing
`observeSyncCompletion()` pattern) instead of `loadAssistantState()`'s old
one-shot `.first()` reads:

- `observeRecentAiActivity()` — recent activity now updates while Home
  stays open, not only at the screen's initial load (a real bug fix: the
  data was already real, it just wasn't live).
- `observeActiveAiOperation()` — the new `observeActiveOperations()` signal,
  projected to a present-tense label ("Analyzing garment…") via a new
  `AiCapability.inProgressLabel()` sibling to the existing past-tense
  `activityHeadline()`. `null` — and the banner absent — the instant
  nothing is genuinely in flight, which is most of the time; that's honest,
  not a bug.
- The existing Cloud-AI-not-configured nudge now navigates straight to
  `AiProvidersRoute` (was a two-hop path through Profile) and its own copy
  gained an explicit "Configure Cloud AI" line — the concrete Phase 8 gap
  ("the user should never have to hunt through the app").

### 4. Recommendations — rule-based outfits get an honest label too

Before M18, only cloud-styled outfits (`provenance != null`) had any
provenance label ("AI Styled"); the default, rule-engine-scored case just
showed its explanation text with no label distinguishing it from an AI
result. `RuleBasedBadge` ("Styled from your wardrobe preferences and
today's context") now appears for that case — reusing the exact same
`explanation` string already computed by `RecommendationRuleEngine`, no
new signal. `AiStyledInfoDialog` also now shows "Result loaded from cache"
when `provenance.cacheHit == true`, previously silently dropped.

### 5. Deliberately NOT touched — and why

- **Try-On's main render screen**: inspection found it dispatches **no AI
  capability at all** — it's on-device geometric placement-template
  rendering (`TryOnPlacementRepository`), not `AiGateway`/`VIRTUAL_TRY_ON`.
  The real AI try-on generation happens only via the separate "Compare with
  Cloud" screen, which already has full transparency. Adding an "AI status"
  card to the main screen would imply AI ran something it didn't — directly
  the non-negotiable "never imply AI did something if it did not actually
  execute." Left alone on purpose.
- **Stylist Preferences screen**: a pure `RecommendationPreferences` toggle
  list with no relationship to Cloud Styling (`OUTFIT_STYLING` capability)
  config. `RecommendationsScreen`'s own direct "AI" nav button already
  covers this screen's actual AI-configuration entry point one tap away;
  adding a second one here would be a near-duplicate, not a genuine gap.
- **Add-to-Wardrobe/Review**: already satisfies Phase 6 in full (real
  stage-name progress, real post-completion summary) — nothing added.

## Consequences

- No database migration, no new table — `ai_jobs` already existed and was
  already written to on every dispatch; M18 only adds a read path.
- `AiProviderSettingsRepository`'s constructor gained one dependency
  (`AiJobDao`, already Hilt-provided) — every existing test constructing
  `AiProviderSettingsRepositoryImpl` directly was updated to pass it, no
  test weakened or deleted. Both existing `FakeAiProviderSettingsRepository`
  implementations (`feature:settings`, `feature:closet`) gained the new
  method.
- Genuine, disclosed limitation: `AiActiveOperation` has no
  `provider`/`model` — a currently-running job cannot honestly report a
  provider before it resolves one. Recorded in `TECHNICAL_DEBT.md`.
- Parts 9–13 of the wider "AI Wardrobe Assistant" epic remain untouched.
