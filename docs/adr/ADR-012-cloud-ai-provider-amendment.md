# ADR-012: Amending the Permanent Privacy Principles to Allow Pluggable Cloud AI

**Status**: Accepted (Constitution rule 13 addendum, `alta-class-closet-app-master-prompt.md`,
added 2026-08-05, amending ADR-011)

## Context

ADR-011 (2026-08-03) made ten privacy/offline principles permanent and
explicitly "no longer up for reconsideration," closing the door ADR-004 had
left open ("revisit later as a funded product"). Two days later, planning
the Add-to-Wardrobe v2 redesign (Alta-level garment extraction,
reconstruction, and metadata generation) surfaced a direct conflict: real
clothing-only segmentation, occlusion-aware reconstruction, and rich
multi-attribute fashion metadata each need either a trained model this
project doesn't have and can't train, or a cloud call — and ADR-011 rule 5
("No OpenAI, Gemini, Claude, or any cloud LLM integration, ever") and rule
10 ("Any future machine learning must execute locally on-device") forbid
exactly that outright.

This conflict was raised explicitly with the user rather than resolved
silently in either direction. The user's response: **this is a private,
single-household application (used only by the user and their spouse), and
Alta-level AI quality is now prioritized over a strict offline-only
architecture.** This is a knowing, deliberate amendment of a rule the user
themselves had marked permanent 48 hours earlier — not scope creep to push
back on, and not a decision this project should make unilaterally either
direction without the user's explicit say-so, which was obtained.

## Decision

ADR-011's rules 1, 2, 5, 6, and 10 are amended as follows. Rules 3, 4, 7, 8,
and 9 are **not** touched — recommendation/outfit generation still work
fully offline, no user accounts, local-network-only sync, and contextual
data (weather/holidays) usage are all unchanged.

**Amended rules:**

1. ~~User wardrobe photos never leave the user's own devices~~ → **A
   wardrobe photo may be sent to a cloud AI provider only through this
   project's own AI Gateway/provider-adapter architecture, and only after
   the user has given explicit, informed consent for that specific
   capability and host.** Consent is not a buried toggle — selecting a
   cloud provider in Settings immediately shows a plain-language consent
   dialog naming the destination host; declining reverts to on-device.
2. ~~Personal photos never leave the user's own devices~~ → same amendment
   as rule 1, extended to any personal photo a future capability (e.g.
   virtual try-on) processes.
5. ~~No OpenAI, Gemini, Claude, or any cloud LLM integration, ever~~ →
   **Cloud AI providers are permitted, but only behind a vendor-neutral
   provider interface** (`core:ai`'s `AiGateway` + per-vendor `ProviderAdapter`).
   No vendor SDK or vendor-specific request/response logic may appear
   outside an adapter file — application/feature code only ever depends on
   this project's own capability interfaces
   (`GarmentExtractionEngine`/`GarmentReconstructionEngine`/
   `GarmentMetadataEngine`/`StylingEngineRepository`/`VirtualTryOnEngine`),
   never a vendor's client library or wire format directly. Switching
   providers must never require a feature-code change.
6. No cloud storage of wardrobe data — **unchanged**. This amendment is
   about AI *processing* calls only; wardrobe data itself (the Room
   database, garment images) is never hosted or backed up in the cloud by
   this app.
10. ~~Any future machine learning must execute locally on-device~~ → **An
    on-device implementation must exist and remain the default/fallback for
    every AI capability** — cloud is opt-in per capability, never the only
    option, and a misconfigured or unreachable cloud provider must always
    degrade to the on-device path rather than break the feature.

**New rules added by this amendment:**

11. API keys/credentials for a cloud provider must be stored via Android
    Keystore-backed encrypted storage, never hardcoded, never committed,
    never stored in plain preferences.
12. No vendor-specific logic may leak outside its adapter — this is a
    structural requirement (see rule 5), not just a style preference,
    enforced by the module boundary between `core:ai`'s adapters and
    everything that calls the Gateway.

## Consequences

**Positive**:
- Unblocks Alta-level quality (real clothing segmentation, occlusion
  reconstruction, rich metadata, richer styling/try-on) that no on-device
  model in this project's reach can currently deliver.
- The vendor-neutral Gateway/adapter requirement means this amendment
  doesn't lock the project into any single cloud vendor — switching or
  adding providers stays a contained, adapter-level change.
- Explicit consent + secure key storage preserve the spirit of the original
  privacy principles (informed control over what leaves the device) even
  though the absolute "never" is gone.

**Negative** (accepted knowingly by the user):
- This is no longer a strictly offline-capable product for the capabilities
  where cloud is enabled — a user without configured cloud credentials (or
  without network access) only gets the on-device tier's quality for those
  capabilities, which is honestly weaker for segmentation/reconstruction/
  rich metadata than a real cloud model.
- Cloud AI use has a real, if user-controlled and vendor-dependent, dollar
  cost — mitigated but not eliminated by aggressive per-image/per-stage
  caching (see the Add-to-Wardrobe v2 plan's multi-stage cache design).
- This reopens, for AI processing calls specifically, a category of privacy
  exposure ADR-011 had permanently closed. This is acceptable *because* the
  application's actual user base is now known and fixed (the user and their
  spouse) rather than a hypothetical general public release — this
  reasoning would need to be revisited if this app were ever distributed
  beyond that household.

## Alternatives Considered

- **Keep ADR-011 as strictly permanent and decline the cloud AI request**:
  rejected — the user is the one who set the original rule and is the one
  now amending it with clear, specific reasoning (private single-household
  use, quality priority). Refusing to let the project's own rule-owner
  amend their own rule would substitute my judgment for theirs on a
  question that is genuinely theirs to decide.
- **Silently build the cloud integration without amending the documented
  rule**: rejected — would leave the codebase's own stated principles
  (ADR-011, Constitution rule 13, `DEPENDENCY_POLICY.md`) contradicted by
  what the code actually does, exactly the kind of silent drift this
  project's documentation discipline exists to prevent.
- **Supersede ADR-011 entirely rather than amend specific rules**:
  rejected, matching ADR-011's own precedent of preferring an additive ADR
  over rewriting history — ADR-011's reasoning for rules 3/4/7/8/9 remains
  fully valid and unchanged; only the cloud-AI-specific rules needed
  revision.
- **Allow cloud AI with a hardcoded single vendor (e.g. just OpenAI)**:
  rejected — the user explicitly required vendor neutrality via a
  Gateway/adapter architecture so that switching providers is never a
  feature-code change; a hardcoded single vendor would recreate the exact
  lock-in this amendment's own rule 5 revision forbids.
