# Beta 1 Report — Real-World Usage & Product Polish

**Status: partially complete.** Beta 1 is a real-device, real-usage
milestone. This pass covers everything that can be done from source alone
(a static bug audit and an AI-prompt review); the daily-usage, AI-quality
sampling, performance, and UX-polish phases require actually using the app
on a real device over time, which this environment cannot do — no device,
no camera, no real cloud account. Sections below are explicitly marked
**Done (code audit)** or **Awaiting real usage** — do not treat the latter
as complete until it says otherwise.

## Important note on how to read this report

Following the same discipline as `PRODUCTION_VALIDATION_REPORT.md` (M13)
and `SECURITY_AUDIT.md` (RC1): nothing below claims a result from real
hardware, a real cloud provider account, or actual daily use that didn't
happen. Where the spec asks for a number (import count, success rate,
performance figure), it's either a real number sourced from
`BETA_1_AI_QUALITY_LOG.md`/`BETA_1_FRICTION_LOG.md`, or it's left blank
with a pointer to where it gets filled in.

## Phase 1 — Daily Usage Audit — **Awaiting real usage**

Source: `BETA_1_FRICTION_LOG.md`. Not started — no entries logged yet in
this environment. Once you've used the app for a while, summarize the
log's rollup here:

- Total friction entries logged: `___`
- By severity: Blocker `___`, High `___`, Medium `___`, Low `___`
- Top 3 recurring themes: `___`

## Phase 2 — AI Quality Review — **Awaiting real usage**

Source: `BETA_1_AI_QUALITY_LOG.md`. Targets: 100 imports / 50 outfit
recommendations / 30 try-ons. Not started — zero real samples collected in
this environment (no cloud account, no device camera, no wardrobe photos
to feed the on-device pipeline realistically).

| Capability | Sampled | Target | Success rate | Most common failure |
|------------|---------|--------|---------------|----------------------|
| Garment import (extraction + metadata) | 0 | 100 | — | — |
| Outfit recommendations | 0 | 50 | — | — |
| Virtual try-on | 0 | 30 | — | — |

## Phase 3 — UX Polish — **Awaiting real usage**

Spec requires UX changes to be driven by *observed* friction (Phase 1),
not assumptions. Since Phase 1 has no real entries yet, no UX polish work
has been done under this phase — doing so now would be exactly the
assumption-driven design the spec explicitly rules out. Revisit once
`BETA_1_FRICTION_LOG.md` has real, recurring entries.

## Phase 4 — Performance Review — **Awaiting real usage**

Requires a real device. No cold/warm launch, capture time, AI processing
time, scrolling, memory, or battery numbers exist from this environment —
same gap already disclosed in `PRODUCTION_VALIDATION_REPORT.md`'s
performance section, unchanged by this milestone.

## Phase 5 — AI Prompt Tuning — **Done (code audit)**

Reviewed both of the two prompts that actually exist in this architecture
— `GarmentMetadataEngineRouter`'s cloud path and `CloudStylingEngine`'s
cloud path (`MetadataPromptSupport.kt`, `CloudStylingEngine.kt`).
Extraction, Reconstruction, and Virtual Try-On's cloud paths are
image-in/image-out tasks (`AiGateway.runImageTask`) with no natural-language
prompt to tune — there's nothing to version-bump there.

**Findings**:
- Both existing prompts already have solid hallucination guards: an
  explicit closed vocabulary (valid `MetadataField` names / valid
  `OutfitSlot` names and real wardrobe item ids), an explicit instruction
  to omit rather than guess, and an explicit instruction that confidence
  must be a genuine per-value estimate, never a placeholder. JSON parsing
  on both paths already drops (never fabricates) malformed entries,
  unknown field names, out-of-range confidence, and non-numeric ids.
- **One real, concrete gap found**, not fixed this pass: the metadata
  prompt never tells the model the user's actual reference-data vocabulary
  (existing category/brand/material/color names), unlike the styling
  prompt, which does exactly this for wardrobe items. This means a
  correct-in-spirit suggestion ("Navy Blue") can silently fail to bind to
  the user's actual reference row ("Navy") — see `TECHNICAL_DEBT.md` item
  21 for the full writeup and why it wasn't fixed in this pass (needs new
  repository wiring into `GarmentMetadataEngineRouter`, which is scoped
  work for Beta 2, not a same-file prompt edit).
- No prompt version bump this pass — no prompt *text* changed, only a gap
  was identified for future work.

## Phase 6 — Bug Fix Sprint — **Done (code audit)**

One confirmed, concrete bug found and fixed via source review (not
inferred from usage, since none exists yet):

**`AiJobManager` duplicate-dispatch bug.** `dispatch()` generated a fresh
random UUID as the WorkManager unique-work name on every call, even though
the Gateway's own cache key was already available at the call site. This
meant `ExistingWorkPolicy.KEEP` — meant to stop a duplicate in-flight
request from firing twice — could never trigger, since the "unique" name
was different every time. Two concurrent calls for the identical cache key
(a double-tapped action, a recomposition re-firing the same request) would
each enqueue their own job and fire their own real cloud request:
duplicate cost for a paid API call, plus a race on the shared `ai_jobs`
ledger row.

**Fix**: `AiWorkRegistry` is now keyed by the cache key itself, with a new
`registerIfAbsent` (atomic) that lets a second concurrent caller detect an
already-in-flight request for the same key and await its result instead of
dispatching again — genuine request coalescing. Registry cleanup moved
from the caller's `finally` into `AiCapabilityWorker`'s own terminal
completion, so a cancelled caller can't remove an entry a second caller is
still relying on.

**Verified**: new regression test (`AiJobManagerTest`, "two concurrent
dispatches for the identical cache key coalesce into one call") proves the
underlying block runs exactly once for two concurrent dispatches sharing a
cache key. `:core:ai:testDebugUnitTest`, `ktlintFormat`, and `detekt` all
green on the touched files.

No crashes, data-loss, or incorrect-wardrobe-state bugs were found
elsewhere in the areas reviewed (capability routers, consent/base-URL
invalidation logic, non-null-assertion usage across `core:data`/`core:ai`/
`core:image`/`feature:outfits`/`feature:tryon`). Real, usage-triggered bugs
that only show up on a device (gesture conflicts, memory pressure,
OEM-specific camera quirks) are outside what a static pass can find — that
gap is Phase 1/4's, not this phase's, to close.

## Phase 7 — Beta Report — **this document**

### Summary counts

| Metric | Count |
|--------|-------|
| Garment imports sampled | 0 (target 100) |
| Outfit recommendations sampled | 0 (target 50) |
| Try-ons sampled | 0 (target 30) |
| Confirmed bugs found | 1 |
| Confirmed bugs fixed | 1 |
| Real, disclosed limitations logged (not fixed) | 1 (metadata reference-vocabulary gap) |

### AI success rates

Not available — zero real samples. Fill in from
`BETA_1_AI_QUALITY_LOG.md`'s totals once populated.

### Most common failure types

Not available yet — same reason.

### Resolved issues

- `AiJobManager` duplicate-dispatch / cost-doubling bug (see Phase 6
  above and `TECHNICAL_DEBT.md` item 21).

### Remaining issues

- Metadata autofill reference-vocabulary gap (Phase 5 above) — logged,
  not fixed, scoped for Beta 2 pending real usage confirming its actual
  impact.
- Everything already carried forward from `TECHNICAL_DEBT.md` items 19/20:
  real-device validation, real cloud-provider validation, and real
  performance measurement remain outstanding — unchanged by this pass.

### Top five improvements for the next beta

These are code-review hypotheses, not confirmed priorities — re-rank once
`BETA_1_FRICTION_LOG.md`/`BETA_1_AI_QUALITY_LOG.md` have real entries,
since the spec's own philosophy is to prioritize by *observed* friction,
not assumption:

1. Thread reference-data (existing category/brand/material/color names)
   into the metadata cloud prompt so autofill can actually match the
   user's own vocabulary (Phase 5 finding above).
2. Run the real-device validation checklist from
   `PRODUCTION_VALIDATION_REPORT.md` that's been outstanding since M13 —
   it blocks every other real number in this report.
3. Populate `BETA_1_AI_QUALITY_LOG.md` with the full 100/50/30 samples to
   get an actual metadata/styling/try-on success-rate baseline to compare
   future prompt changes against.
4. Once Phase 1 has real entries, revisit `AiStyledBadge`/confidence-chip
   copy and the retry affordances called out in `BETA_TEST_GUIDE.md` — the
   most likely source of "confusing wording" friction based on how many
   distinct confidence/provenance states the review screen already
   surfaces.
5. Decide whether Ollama/self-hosted Generic REST needs a scoped
   cleartext network-security exception, based on whether real beta usage
   actually exercises a local, non-HTTPS endpoint (disclosed but
   unresolved since RC1).

## Exit criteria — status against the spec

| Criterion | Status |
|-----------|--------|
| App used in normal daily scenarios | Not done — needs real device |
| All critical bugs found during beta are fixed | 1 found, 1 fixed (code-audit scope only) |
| AI quality measured with real usage | Not done — needs real usage |
| UX improvements driven by observed behavior | Not applicable yet — no observed behavior logged |
| No architectural regressions introduced | Confirmed — the fix stayed within `AiJobManager`/`AiWorkRegistry`/`AiCapabilityWorker`, no new abstractions, no dependency changes |

**Beta 1 is not yet complete** by its own exit criteria. What's done: the
bug audit and prompt review portions, plus the tracking scaffolding
(`BETA_1_FRICTION_LOG.md`, `BETA_1_AI_QUALITY_LOG.md`) needed to do the
rest. What's outstanding is entirely the real-usage work only the user can
perform — the same category of gap M13 and RC1 both disclosed rather than
faked.
