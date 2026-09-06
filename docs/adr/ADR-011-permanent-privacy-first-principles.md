# ADR-011: Permanent Privacy-First, Offline-First Product Principles

**Status**: Accepted (Constitution rule 13, `alta-class-closet-app-master-prompt.md`,
added 2026-08-03, after Phase 9, before Phase 10). **Amended 2026-08-05 by
[ADR-012](ADR-012-cloud-ai-provider-amendment.md)** — rules 1, 2, 5, 6, and
10 below no longer read as absolute; see ADR-012 for the current, binding
text of those rules (cloud AI is now permitted through a vendor-neutral
provider architecture with explicit consent and secure key storage, for
this specific private/single-household deployment). Rules 3, 4, 7, 8, and 9
are unaffected. The rest of this document is kept as-written for historical
context — it accurately describes the reasoning behind the original,
stricter rule set.

## Context

ADR-003 (Offline-First) and ADR-004 (No Cloud, No Accounts, No Third-Party
SDKs) established the original zero-cost/offline posture at Phase 1, but
ADR-004's own "Alternatives Considered" section left the door open —
"revisit later as a funded product" — framing the no-cloud stance as a
current business decision rather than a permanent product identity. Nine
phases in (persistence, image pipeline, closet/home/detail UX, manual
outfit building, wardrobe intelligence, a rule-based recommendation engine,
weather/calendar/trip context, encrypted local-network multi-device sync,
and daily-assistant intelligence), none of that door has ever been opened —
every phase shipped fully offline-capable, with the single narrow exception
of the optional Open-Meteo weather call (which itself degrades to a cached/
stale value, never blocking core functionality, per Constitution rule 12).

Before Phase 10 (a personal virtual try-on feature — historically the exact
capability ADR-004 predicted would be "newly viable if this decision is
ever reversed"), the user made explicit that this product is no longer
just "an AI wardrobe app" with an open question about its infrastructure
model. It is now formally defined as a **privacy-first, offline-first
personal wardrobe operating system**, and the constraints below are
permanent — not a Phase-1-era default that a future funding event could
reverse.

## Decision

The following ten principles are binding on every phase, past and future,
and supersede ADR-004's "revisit later as a funded product" framing (ADR-004
itself is not reversed — its reasoning and consequences still hold; this
ADR hardens its conclusion from "current posture" to "permanent identity"):

1. User wardrobe photos never leave the user's own devices.
2. Personal photos never leave the user's own devices.
3. Outfit generation must work completely offline.
4. Recommendations must work completely offline.
5. No OpenAI, Gemini, Claude, or any cloud LLM integration, ever.
6. No cloud storage of wardrobe data.
7. No user accounts are required.
8. Multi-device sync remains local-network encrypted only (Phase 8's
   pairing/outbox/conflict-resolution design, ADR-004) — never
   server-mediated, regardless of how convenient a relay server would be
   for devices that are never on the same network.
9. Internet access is only permitted for optional contextual data (weather,
   holidays, and similar signals) that refine but never gate a
   recommendation (Constitution rule 12, the Context Refinement Rule).
10. Any future machine learning must execute locally on-device — no remote
    inference API, no cloud model-serving endpoint, regardless of vendor or
    how small/cheap the call would be. This directly forecloses the
    "AI Stylist chat" future-extensibility row in
    `phase-1-architecture.md` Section 30 ever being LLM-backed via a cloud
    API; an on-device small-model implementation is not excluded, provided
    it satisfies this same rule.

**Immediate consequence for Phase 10**: the virtual try-on feature ADR-004
once described as "CUT... for as long as this decision stands" is not a
reversal of that decision — it is Phase 10, built as a **fully local 2D
try-on system** (no 3D avatar, no cloud rendering, no uploaded photos ever
leaving the device). ADR-004's original cost/privacy objection was to a
*cloud-rendered or cloud-hosted* avatar pipeline (the "moat feature" the
source-app teardown priced as server-bound); a fully on-device
photo-and-garment compositing pipeline does not reopen that objection, so
building it is consistent with, not a violation of, ADR-003/ADR-004's
reasoning.

## Consequences

**Positive**:
- Removes any ambiguity for future phases about whether a "just this once"
  cloud call (an LLM stylist chat, a hosted try-on renderer, a managed
  sync relay) is on the table — it is not, permanently.
- Strengthens the privacy story for the most sensitive data class this app
  handles (photos of a user's body, home, and belongings) from "current
  policy" to "structural guarantee."
- Gives `DEPENDENCY_POLICY.md` a hard filter for any future dependency
  addition: a cloud LLM SDK, a remote ML inference client, or an analytics/
  crash SDK that phones home is rejected on sight, no case-by-case
  deliberation needed.

**Negative** (all already accepted under ADR-004, now made explicit as
permanent rather than provisional):
- No cloud-LLM-backed styling chat, ever — the recommendation engine stays
  rule-based indefinitely, a real capability ceiling against an
  LLM-backed competitor that this project accepts permanently, not just
  for now.
- No managed/cross-network device sync — two devices that are never on the
  same Wi-Fi network (e.g. a family member's phone at a different address)
  cannot sync at all. Accepted per Phase 8's own Known Limitations.
- Any future on-device ML (try-on body-profile estimation, a future local
  small-model stylist) must ship its own model weights/runtime inside the
  app or as an on-device ML Kit/similar capability — no server-side
  training-as-a-service shortcut, ever.

## Alternatives Considered

- **Leave ADR-004's "revisit later as a funded product" framing as-is**:
  rejected — the user explicitly closed this door before Phase 10 began,
  and Phase 10 is precisely the feature ADR-004 flagged as the one most
  likely to tempt a cloud-hosted shortcut (cloud-rendered avatars are the
  industry-standard approach). Leaving the door open risked exactly that
  drift.
- **Treat this as a reversal requiring a new ADR that supersedes ADR-004**:
  rejected — ADR-004's Decision and Consequences sections remain entirely
  accurate; nothing about "no backend, no accounts, no third-party SDK" has
  changed. This ADR hardens ADR-004's conclusion rather than replacing it,
  so it's recorded as a new, additive ADR, not a supersession.
- **Scope this only to Phase 10 (try-on)**: rejected — the user's own
  framing ("permanent project principles," "long-term roadmap") applies to
  every future phase, not just the one about to start, so it's recorded as
  a standing Constitution rule (13) and a standalone ADR rather than a
  Phase-10-doc-only note that later phases might not think to check.
