# ADR-022: AI Wardrobe Auto-Fill Transparency Fix (M23)

**Status**: Accepted (implementation milestone, added 2026-08-08)

## Context

Real-device evidence: on a physical Android tablet, after "AI Processing
Complete" (On-Device provider, 48% confidence, 1.3s), only Primary Color,
Secondary Color, and Pattern auto-filled. Category, Subcategory, Brand,
Material, Fabric, Fit, Sleeve Length, Length, Neckline, Gender, Waterproof
Level, Season, Dress Code, Occasion, and Style Tag all showed "Unknown —
Please choose" despite every automated test and `clean build` passing.

M23's brief demanded a full-pipeline audit before any fix, explicitly
distrusting "tests pass" as proof of correctness, and named seven possible
root causes to prove or rule out from code, not guess.

## Root cause (proven, not assumed)

A full trace — photo → `OnDeviceMetadataEngine`/`GarmentMetadataEngineRouter`
→ `MetadataSuggestionResolver`/`MetadataSuggestionApply` →
`GarmentReviewMetadataViewModel` → `GarmentReviewMetadataUiState` → Compose
fields → save — found every layer downstream of the on-device engine
working correctly and already tested:

- `MetadataSuggestionResolver.isBindableField`/`autoFillForm`/`applySuggestion`
  correctly resolve all 18 fields against real reference data when given a
  suggestion, never fabricating a match.
- `ConfidenceTier` gating is per-suggestion (HIGH auto-fills, MEDIUM offers
  a tappable chip, LOW/no-confidence stays unapplied) — there is no single
  global-confidence gate anywhere in the apply path. The "48%" shown on the
  "AI Processing Complete" card is `AiProcessingSummary.averageConfidence`,
  a display-only aggregate never consumed by `autoFillForm` or
  `AutoSaveEligibility`.
- `GarmentReviewMetadataUiState`/Compose bindings are 1:1 correct — every
  dropdown reads the matching `form.xxx` field.

**The actual gap**: `OnDeviceMetadataEngine.generateMetadata` (`core:image`)
only ever constructs a `MetadataSuggestion` for `PRIMARY_COLOR`,
`SECONDARY_COLOR`, `PATTERN` (k-means clustering / luminance-variance
heuristic), and `BRAND` (ML Kit OCR, always `confidence = null`). This was
already documented in the class's own KDoc and is a genuine, bounded ML
capability limit — there is no ML Kit image-labeling dependency or any
other coarse-category signal sitting unused in this codebase (confirmed by
a repo-wide dependency and symbol search). Category, Material, Fabric, Fit,
Sleeve Length, Length, Neckline, Gender, Waterproof Level, Season, Dress
Code, Occasion, and Style Tag genuinely require either a cloud vision
model or a new on-device classifier this app does not have.

What was missing was **not correctness** but **honesty of representation**:
this capability boundary lived only as prose in one class's KDoc. Nothing
downstream could distinguish "this provider structurally cannot detect
this field" from "the provider can detect this but didn't this time" — both
rendered as the identical "Unknown — Please choose" row. A HIGH-confidence
suggestion that failed reference-data resolution was also invisible as a
distinct state (silently unapplied, indistinguishable from a MEDIUM/LOW
suggestion awaiting review).

Per M23's explicit instruction ("if the existing on-device model genuinely
cannot determine fields... DO NOT fake them... identify actual supported
capabilities, make those work end-to-end, clearly indicate unsupported
fields, provide the existing Cloud AI path"), the fix is transparency and
capability-declaration, not fabricating a new detector.

## What changed

1. **`MetadataFieldSupport`** (new, `core:model/ai`) — the single declared,
   testable contract for which `MetadataField`s each `AiResultSource` can
   ever produce. `ON_DEVICE_SUPPORTED_FIELDS` = exactly
   `{PRIMARY_COLOR, SECONDARY_COLOR, PATTERN, BRAND}`, matching what
   `OnDeviceMetadataEngine` actually constructs (enforced by construction —
   the engine's `generateMetadata` literally cannot emit any other field,
   since it only ever calls `colorSuggestions`/`patternSuggestion`/
   `brandSuggestion`). Cloud and Manual are unbounded, since
   `MetadataPromptSupport.buildMetadataSystemPrompt` already requests every
   field from the cloud provider — a missing cloud suggestion always means
   "asked but not detected," never "impossible to ask."
2. **`MissingFieldReason`** (new, `feature:capture/review/FieldApplicability.kt`) —
   three states for a bindable field with no current suggestion:
   `NOT_APPLICABLE` (category-gated, e.g. Fit on a Shoes item — unchanged
   from M22's `FieldApplicability`), `NOT_SUPPORTED` (the field is genuinely
   outside the running provider's capability — new), `NOT_DETECTED` (the
   provider is capable but this photo didn't yield a value — the original
   "Unknown — please choose" meaning, now correctly narrowed to only this
   case).
3. **`GarmentReviewSuggestions.kt`** — `MetadataSuggestionsSection` takes a
   new `source: AiResultSource?` param (`state.aiProcessingSummary?.source`
   at the call site; defaults to `null`, preserving prior behavior for any
   caller that doesn't pass it). `MissingFieldRow` renders three visually
   distinct states: quiet "N/A", an informational (not warning) "Not
   supported by On-Device AI — Enable Cloud AI in Settings for full
   detection", or the original warning "Unknown — Please choose".
   `SuggestionRow` gained a fourth surfaced state: a HIGH-confidence
   suggestion that failed reference-data resolution now shows "Detected,
   but no matching option found — choose manually" instead of silently
   rendering as an unselected chip indistinguishable from a low-confidence
   one.
4. **`MetadataPipelineDiagnostics.kt`** (new, pure function) — reuses the
   exact same `missingFieldReason`/`isSuggestionApplied` logic the UI
   renders from (so logged state can never drift from displayed state) to
   build a per-field diagnostic line: requested, supported, returned value,
   confidence, tier, resolved. `GarmentReviewMetadataViewModel` logs this
   via `android.util.Log.d` gated by `ApplicationInfo.FLAG_DEBUGGABLE`
   (same pattern as M22's `AiNetworkModule`/`NetworkModule` debug-gating,
   avoiding a new `BuildConfig` in a module that doesn't have one) — never
   logs the image, an API key, or any user-identifying data, only field
   names/values/confidence/booleans already shown on-screen.
5. No change to `MetadataSuggestionResolver`, `MetadataSuggestionApply`,
   `AutoSaveEligibility`, `FieldApplicability`, `GarmentMetadataEngineRouter`,
   or `MetadataPromptSupport` — the audit found each already correct and
   already tested; auto-save's HIGH-confidence-or-N/A gate is untouched
   (M14–M18 safety rules preserved, per M23's explicit instruction not to
   weaken it).

## Disclosed consequence (not smoothed over)

On-device-only users will still see most fields as "Not supported by
On-Device AI" after this fix — that is now stated honestly instead of
implied by an unexplained "Unknown." Closing that gap for real requires
either a new on-device classifier (a real product/dependency decision, out
of scope here — M23 explicitly forbids fabricating a capability that
doesn't exist) or the user enabling Cloud AI (their own choice, existing
consent architecture, now referenced directly from the row that needs it).

## Deliberately deferred

- **Direct in-app navigation from the "Not supported" row to the AI
  Provider settings screen** — this pass adds the informational copy only;
  wiring an actual nav callback through
  `GarmentReviewMetadataScreen`/`GarmentReviewMetadataViewModel` is a small,
  real follow-up that touches call sites this pass didn't need to change.
- **A runtime assertion that `OnDeviceMetadataEngine.generateMetadata`'s
  output never exceeds `MetadataFieldSupport.ON_DEVICE_SUPPORTED_FIELDS`** —
  not added as a test, since the guarantee already holds by construction
  (the function's `buildList` body only ever calls three private methods,
  each hardcoded to one or two specific fields); a runtime check would be
  redundant defensive code, not a real regression guard.
- **On-device Brand-detection-with-no-evidence and reference-resolution
  edge cases in `OnDeviceMetadataEngineTest`** — this file has never had
  `Bitmap`/ML-Kit-backed tests (only the pure `patternConfidence` math is
  covered); adding them is a pre-existing test-infrastructure gap, not
  something this milestone's scope (UI/diagnostics transparency) introduced
  or is positioned to fix without a larger Robolectric/ML-Kit test harness
  investment.
