# ADR-008: Background Removal as a Swappable Abstraction

**Status**: Accepted (Phase 1 Section 16) — implementation choice explicitly deferred

## Context

Garment cutout (background removal) is one of the most-praised capabilities in the
source-app teardown and, per the No Cloud decision (ADR-004), must run entirely
on-device. Two real candidates exist — ML Kit Subject Segmentation and a bundled
TFLite salient-object model — but neither had been verified against this app's actual
photo distribution (garment on a hanger, flat-lay, worn, thin straps, cutout
necklines) at the time this decision was made. Committing to either without that
verification would be exactly the kind of unverified capability claim Constitution
rule 4 exists to prevent.

## Decision

Define `BackgroundRemover` as a single-method interface in `core:image`
(`suspend fun removeBackground(bitmap: Bitmap): CutoutResult`, Phase 1 Section 16) with
no implementation yet. Before Phase 5b commits to one, run a short spike: process
~20 real garment photos through both ML Kit Subject Segmentation and a bundled TFLite
model, compare edge quality, and pick based on that evidence. Everything above
`core:image` (the capture pipeline, the edit UI, the confidence-signalled attribute
editor) is written against the interface and is unaffected by which implementation
wins.

## Consequences

**Positive**:
- The rest of the app (capture pipeline, UI) can be built now without waiting on the
  spike.
- Switching implementations later — or replacing this with a better on-device model
  that doesn't exist yet — is a `core:image`-internal change, not an architecture
  change.
- No dependency (ML Kit or a bundled TFLite model file) is taken on speculatively;
  APK size and licensing questions are deferred until there's a real basis for the
  choice.

**Negative**:
- No working cutout feature exists until both the spike *and* the chosen
  implementation land — this is a real, accepted sequencing cost, not free.
- Risk that both candidates underperform on this app's specific photo distribution
  (thin straps, cutout necklines) is not eliminated by this decision, only isolated so
  it's cheap to discover and address without a wider rewrite.

## Alternatives Considered

- **Commit to ML Kit Subject Segmentation now**: rejected — its demonstrated use
  cases (Phase 1 Section 16) are general single-subject photos, not specifically
  garment product photography; using it without verification would be asserting a
  capability this project hasn't checked.
- **Commit to a bundled TFLite model now**: rejected for the same verification reason,
  plus unresolved APK-size and pretrained-weights licensing questions that the spike
  is meant to also surface.
- **Server-side segmentation**: rejected outright — directly contradicts ADR-004 (No
  Cloud); not reconsidered unless that decision itself is revisited.

## Addendum (Phase 5b, 2026-08-01)

The spike this ADR called for was not run: it needs a real device camera and a
real sample of garment photos, neither of which exists in this development
environment. Rather than leave `BackgroundRemover` unimplemented indefinitely,
Phase 5b bound it to `MlKitBackgroundRemover` — the lower-cost candidate from
the comparison table above — as a **reasoned, not verified,** default. This is
tracked as open debt (`TECHNICAL_DEBT.md` item 6), not silently treated as
"decided." The interface's whole purpose — isolating this choice so it costs
nothing to revisit — is what makes that an acceptable way to proceed rather
than a shortcut around this ADR's own stated bar.
