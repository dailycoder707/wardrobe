# Phase 10 — Personal Virtual Try-On (Privacy First)

A fully local 2D virtual try-on system: guided front-camera capture builds a
private, on-device body profile; existing background-removed garment
cutouts (Phase 5b) are composited onto the user's own reference photo via
**affine transforms only** — translate/scale/rotate, never a body-contour
warp, cloth simulation, or perspective mesh deformation. Integrated into
Outfit Detail, Today's Recommendation (Home), Saved Looks, and Trip
Planner's packing list via one shared "Try On Me" route. No 3D avatar, no
cartoon avatar, no cloud rendering, no uploaded photos — every photo, every
render, every placement decision stays on-device, per Constitution rule
13/`docs/adr/ADR-011-permanent-privacy-first-principles.md`'s narrow
exception (Phase 8's existing encrypted local-network device-to-device
sync between the user's own paired devices).

Immediately before this phase, the user made a permanent product-identity
change — this app is now formally a **privacy-first, offline-first personal
wardrobe operating system**, not an "AI wardrobe app." That change is
already fully documented (Constitution rule 13, `docs/adr/ADR-011-*`,
`PROJECT_STRUCTURE.md`, `DEPENDENCY_POLICY.md`) and is the binding
constraint this entire phase was built under.

## The core technical decision: best fully-local approach

**Affine "sticker" compositing** — each garment cutout is an independently
draggable/scalable/rotatable Compose layer (`Modifier.graphicsLayer` +
`pointerInput(detectTransformGestures)`), never warped to the body's actual
shape. On-device ML Kit Pose Detection seeds a *default* placement only,
never a warp target.

**Rejected alternative**: perspective/contour-warping each cutout to the
body's real shape — what commercial try-on products actually do. Rejected
because warp quality is exactly the part of this feature that cannot be
verified in this development environment (no physical device, no real
human/garment test photos — the same hard constraint `TECHNICAL_DEBT.md`
item 6 already states honestly for background-removal accuracy). A bad
warp reads as *worse* than a slightly-mispositioned flat rectangle, and
there is no way here to catch that failure mode before it ships. Affine-
only compositing is fully unit/Compose-UI-testable without a device (a
transform matrix is a testable geometric property, not a visual one) and
matches Constitution rule 7 — an auto-computed placement is "an editable
suggestion... never a fact," so manual adjustment always overrides and
persists.

**Honest category-fidelity gradient** (a direct, disclosed consequence):
tops/dresses/outerwear (shoulder-anchored) are best; bottoms/belts/scarves
(waist/hip/neck-anchored) are good; bags are moderate (no single stable
pose landmark maps to "wherever a bag naturally hangs"); watches/jewelry
need finer landmark precision than the base pose model reliably gives,
so expect heavier reliance on manual adjustment; footwear is hardest —
selfie camera-to-feet perspective varies wildly and this model has no
ground-plane/perspective correction at all, only a 2D affine transform.

## Architecture

```
core:model     tryon/ (new package): BodyPose, BodyReferencePhoto, BodyProfile,
               MeasurementSource, BodyMeasurements (persisted separately from
               BodyProfile — see below), ClothingDepth (+ defaultDepthFor),
               TryOnAnchorRegion (+ classify, mirrors AccessoryCategory/
               JewelryCategory's finer-than-OutfitSlot pattern),
               PlacementTemplateType, GarmentPlacementTemplate, GarmentMask,
               LightingAdjustment (+ NEUTRAL).
core:database  5 new tables (Migration5To6, v5→v6): body_profiles,
               body_reference_photos, body_measurements (child rows, no own
               syncId — ride along in the parent's sync payload), 
               garment_placement_templates, garment_masks (both independently
               sync-tracked). BodyProfileDao (covers all 3 body-profile
               tables, one DAO per aggregate root — TripDao's precedent),
               GarmentPlacementTemplateDao, GarmentMaskDao.
core:tryon     (new Gradle module, Android library) — capture/BodyCaptureController
               (front-camera CameraX wrapper, mirrors core:image's
               CameraCaptureController), pose/BodyAnchorEstimator (interface,
               ADR-008's swappable pattern) + MlKitBodyAnchorEstimator (real
               ML Kit Pose Detection landmark math, never fabricated),
               placement/DefaultPlacementCalculator (pure geometry: anchor
               region + measurements → seed offset/scale/rotation),
               storage/BodyImageFileStore (images/body/{profileId}/ — a
               sibling root to core:image's images/{garmentId}/, never
               touched by its orphan-cleanup sweep), lighting/LightingMatcher
               (deterministic histogram color-grade), shadow/ShadowRenderer
               (deterministic alpha-darkened silhouette + a real, disclosed
               API-31 blur capability gate), masking/GarmentMaskEditor (pure
               Bitmap erase/restore, no AI), rendering/ (sortForRender +
               shared TRY_ON_LAYER_WIDTH_FRACTION), rendercache/TryOnRenderCache
               (background Canvas compositor for non-interactive thumbnails).
core:domain    BodyProfileRepository, TryOnPlacementRepository,
               GarmentMaskRepository — split by concern to stay under the
               ≤10-function ceiling, not merged into one large repository.
core:data      BodyProfileRepositoryImpl, TryOnPlacementRepositoryImpl (resolves
               a garment's TryOnAnchorRegion from its own category — the same
               categoryId→CategoryEntity.name→OutfitSlot.classify lookup
               OutfitPreviewViewModel already does), GarmentMaskRepositoryImpl.
               ImageTransferPhase extended (ImageChecksumSources bag) so body
               photos/masks get the same checksum-deduplicated sync transfer
               garment photos already have — no new sync protocol invented.
               3 new sync handlers (BodyProfileSyncHandler — the one handler
               whose payload embeds a nested photo list + measurements,
               GarmentPlacementTemplateSyncHandler, GarmentMaskSyncHandler).
feature:tryon  (new module) — capture/ (guided 4-pose body-profile capture:
               BodyProfileCaptureScreen/ViewModel, GuidedPoseOverlay),
               render/ (TryOnScreen/ViewModel — per-layer graphicsLayer +
               gestures, depth+slot z-order, placement-template
               selection/persistence, confidence chip, Reset to Auto
               Placement), masking/ (MaskEditorScreen/ViewModel — dedicated
               erase/restore mode), navigation/ (TryOnRoute dual-input,
               BodyProfileCaptureRoute, MaskEditorRoute).
app            WardrobeNavHost gained tryOnDestinations(); "Try On"/"Try On Me"
               actions wired into Outfit Detail's top bar, Home's recommendation
               preview card, Saved Looks' per-card action, and Trip Planner's
               packing list (garment-backed items only).
```

## Body profile & measurements: a deliberate two-table split

`BodyProfile` (the actual guided reference photos — needed as the render
background) is architecturally separate from `BodyMeasurements` (derived
landmark-ratio numbers) as **two distinct Room tables**, not one row with
extra columns. This makes "recompute only when the profile's photos
actually change, never per render" a structural fact — `BodyMeasurements`
has its own `computedAt`, written only by
`BodyProfileRepository.recomputeMeasurements()`, never touched by
`captureBodyPhoto` itself.

## Placement templates: several named variants per garment

`GarmentPlacementTemplate` is keyed by `(bodyProfileId, garmentId,
templateType, customName)` — `DEFAULT`/`CASUAL`/`FORMAL` presets plus as
many `CUSTOM`-named variants as the user saves, all coexisting per garment
(not per outfit). `TryOnPlacementRepository.defaultTemplateFor` never
returns null: the row with the greatest `lastUsedAt`, else the
`DEFAULT`-typed row, else a freshly computed-and-persisted one seeded by
`DefaultPlacementCalculator` — a garment with no saved template yet still
gets a placement to render with.

**Confidence disclosure** (Constitution rule 7): a template with
`isUserAdjusted == false` shows an "Auto-placed — drag to adjust" chip.
The user's first manual drag/pinch/rotate on that layer flips
`isUserAdjusted` permanently true — the chip is gone until an explicit
"Reset to Auto Placement" action (which recomputes via
`DefaultPlacementCalculator` and clears the flag again).

## Clothing depth & z-order — fixing forward, not repeating, a known weakness

`ClothingDepth` (`INNER`/`NORMAL`/`OUTER`) sorts render order by
`(depth.ordinal, OutfitSlot.ordinal)` via `core:tryon`'s `sortForRender` —
a coarser front-to-back order than `OutfitSlot` alone gives, so a jacket
composites in front of the shirt it's worn over. This is a direct fix
forward of the raw list-iteration z-order weakness `TECHNICAL_DEBT.md` item
11 already flagged in `OutfitPreviewScreen`, not a second copy of the same
gap. `ClothingDepth.INNER` has no current category that classifies into
it — this schema has no true base/under-layer distinct from `OutfitSlot.TOP`
yet; defined now for correctness/extensibility, not faked as meaningful.

## Manual masking, deterministic lighting, deterministic shadow

- **`GarmentMaskEditor`** — pure `Bitmap` erase/restore in the garment
  cutout's own pixel space (not body-photo space), so a mask survives
  placement changes and stays shared across every template for that
  garment. Entirely user-drawn; no auto-segmentation anywhere ("no AI for
  this step" per the brief).
- **`LightingMatcher`** — one histogram pass over the active profile's
  `NEUTRAL` photo, producing a real (not fabricated) mean-channel-derived
  `LightingAdjustment` gain/brightness triple. A color-grade heuristic, not
  true relighting — documented in `LightingAdjustment`'s own KDoc.
- **`ShadowRenderer`** — derives a shadow silhouette deterministically from
  a cutout's own alpha channel (darkened, alpha-scaled). Blur is gated by
  `supportsBlur(sdkInt)` — a real, disclosed API-31+ capability check
  (`RenderEffect`'s actual minimum), not a universal "blurred everywhere"
  claim; below API 31 the shadow renders unblurred.

## Render cache — non-interactive thumbnails only

`TryOnRenderCache` flattens a body photo + garment stack into one bitmap
via plain `Canvas` draw calls (needed because it runs off the UI thread),
keyed by `TryOnRenderCacheKey.digest()` — a SHA-256 over exactly the four
invalidation triggers requested: each visible garment's cutout checksum,
its active template's id+`updatedAt`, its mask's `updatedAt` (if any), and
the body profile's `updatedAt`/measurements' `computedAt`. The live,
interactive `TryOnScreen` **never** reads from this cache — it always
composites live via Compose layers so in-progress drags render
immediately; the cache exists only for non-interactive preview contexts
(e.g. a Saved Looks thumbnail).

## Integration into the four surfaces

`TryOnRoute(outfitId: Long? = null, garmentIds: String? = null)` — dual
input, directly modeled on `OutfitPreviewRoute`'s shape: a real, persisted
outfit id when one exists, or a raw comma-joined garment-id list for
Today's Recommendation's not-yet-saved scored outfit.

- **Outfit Detail** (`feature:outfits`) — a new "Try On" top-bar icon
  action alongside Restyle/Duplicate/Archive/Delete.
- **Home** (`feature:closet`) — the Recommended Outfit preview card gained
  a "Try On Me" button, separate from tapping through to the full
  Recommendations screen.
- **Saved Looks** (`feature:outfits`) — each `OutfitCard` gained a per-item
  "Try On" icon button (bottom-start, alongside the existing top-end
  favorite star), independent of the card's own tap-to-open target.
- **Trip Planner** (`feature:trips`) — each packing-list row backed by a
  real garment (`PackingItemUiModel.garmentId != null`) gained a "Try On"
  action; free-text items (toiletries, reminders) show none.

`TryOnScreen`'s own "needs body profile" prompt reactively observes
`BodyProfileRepository.observeBodyProfile()` (a `collectLatest`, not a
one-shot check) — completing guided capture from that same prompt updates
the screen in place once the profile exists, no fresh navigation required.
A per-layer "Edit Mask" action reaches `MaskEditorScreen`.

## Guided capture design

Four front-facing captures — the first place this app's camera pipeline
ever binds `CameraSelector.DEFAULT_FRONT_CAMERA` rather than the back
camera: **neutral full-body stance** (the canonical render background),
**arms-slightly-raised full-body** (pose-detection-friendlier shoulder/
armpit separation, preferred by `recomputeMeasurements()` when present,
never shown as a render background itself), **torso/waist close-up**
(belt/hem anchor precision), **feet/lower-legs close-up** (footwear anchor
precision). Multi-angle (side/¾) capture was deliberately excluded from
v1: garment cutouts are single-angle already, so a side-view body photo
wouldn't improve matching fidelity, only add scope.

## Testing

Real, passing tests exercising real computation — never a constructor
smoke test standing in for actual logic:

- `DefaultPlacementCalculatorTest.kt` (6 tests, `core:tryon`) — no-
  measurements fallback reports itself as a heuristic; full real
  measurements report as pose-detection; one partially-missing measurement
  downgrades the *whole* result to heuristic (never partially confident);
  waist-line reads `waistHeightFraction`, not `neckPositionYFraction`; a
  region with no measured field (hip/full-torso/wrist/finger/earlobe)
  always uses the fallback; a wider-than-reference shoulder measurement
  scales proportionally.
- `TryOnLayerOrderingTest.kt` (3 tests, `core:tryon`) — outerwear renders
  after a normal-depth top regardless of input list order; same-depth
  slots keep `OutfitSlot` declaration order; outerwear stays last even
  when it's first in the input list.
- `LightingMatcherTest.kt` (4 tests) — a reference-mid-tone photo yields no
  adjustment; brighter/darker photos yield the correctly-signed
  brightness delta and gain; a warm color cast yields a higher red gain
  than blue gain.
- `ShadowRendererTest.kt` (5 tests) — opaque/transparent/half-transparent
  pixels produce the exact expected shadow alpha (color channels always
  zeroed); `supportsBlur` is exercised on both real code paths (API 31+
  true, API 26/30 false) via a parameterizable `sdkInt`, not Robolectric
  SDK-level simulation.
- `GarmentMaskEditorTest.kt` (4 tests) — erase zeroes alpha inside the
  brush radius, leaves pixels outside it untouched, preserves color
  channels; restore brings back the *original* (pre-erase) alpha, proving
  restore never invents coverage the source cutout never had.
- `TryOnRenderCacheKeyTest.kt` (6 tests) — identical inputs produce an
  identical digest; each of the four invalidation triggers independently
  changes the digest (cutout checksum, template `updatedAt`, mask
  `updatedAt`, body-profile `updatedAt`, measurements `computedAt`).
- `TryOnRenderCacheTest.kt` (4 tests, Robolectric, real temp PNG files) —
  rendering twice with the same key reuses the exact cached `Bitmap`
  instance (reference equality proves no recompute); a changed cutout
  checksum or body-profile `updatedAt` forces a fresh render;
  `invalidateAll()` forces a fresh render even with an unchanged key.
- `BodyProfileSyncHandlerTest.kt`/`GarmentPlacementTemplateSyncHandlerTest.kt`/
  `GarmentMaskSyncHandlerTest.kt` (12 tests total, `core:data`) — the same
  LWW/conflict/safe-delete contract every other sync handler already
  proves, plus `BodyProfileSyncHandler`'s specific nested-photos/
  measurements round-trip.
- `BodyProfileCaptureViewModelTest.kt` (3 tests, `feature:tryon`) — starts
  on `BodyPose.NEUTRAL`; capturing a photo saves it and advances to the
  next pose; after the last pose, measurements are recomputed **exactly
  once** (never per-photo) and the flow reports complete.
- `TryOnCanvasTest.kt`/`MaskEditorCanvasTest.kt` (11 tests, Compose UI,
  Robolectric) — layer count/semantics/structure only, **never visual
  appearance**: exactly one rendered layer per garment; the confidence
  chip appears iff `isUserAdjusted == false`; every layer exposes both
  "Reset to Auto Placement" and "Edit Mask" actions; each layer's
  semantics content description matches its garment title.
- `OutfitCardTest.kt`/`OutfitDetailTopBarTest.kt`/
  `RecommendationPreviewCardTest.kt`/`PackingItemRowTest.kt` (new/extended,
  Compose UI) — each of the four integration surfaces' new "Try On" action
  fires its callback without also triggering the surface's own primary tap
  target; the Packing screen's action only appears for garment-backed
  items.

One migration test (`Migration5To6Test.kt`, 2 tests) covers the v5→v6
schema — table creation plus the sync-outbox triggers on the three
independently-sync-tracked tables.

## Known limitations (disclosed upfront, mirroring `TECHNICAL_DEBT.md` item 6's precedent)

- **No visual render-quality verification is possible in this
  environment** — no device, no real human/garment photos. Compositing
  correctness (geometry, persistence, gesture wiring, cache invalidation)
  is genuinely tested; whether a render actually looks convincing, whether
  the lighting match looks natural, or whether the shadow reads as a
  shadow on a real photo is unknown until measured on a real device.
- **Pose-detection landmark accuracy is likewise unverified here** —
  scoped so its failure only degrades an overridable default
  (`DEFAULT_HEURISTIC`), never breaks the feature.
- **Draping is necessarily approximate**: flat 2D affine compositing, no
  fabric physics; occlusion between layers is resolved only by static
  depth + `OutfitSlot` order, not true depth.
- **Real, disclosed category-fidelity gradient** — tops best, footwear
  hardest (see "The core technical decision" above).
- **Shadow blur only renders on API 31+** (`RenderEffect`'s real minimum);
  this project's `minSdk` 26 devices get an unblurred offset shadow.
- **Lighting matching is a deterministic color-grade heuristic**, not true
  relighting — it will not correct for a strongly directional light source
  the garment cutout wasn't originally lit by.
- **Manual masking requires real user effort per garment** — there is no
  auto-segmentation of e.g. "hair over shoulder"; by design, not a missing
  feature.
- **`ClothingDepth.INNER` has no current classifying category** — nothing
  in this schema models a true base/under-layer distinct from `TOP` yet;
  defined now for correctness/extensibility, not faked as meaningful.
- **"Last used becomes default" is a simple recency heuristic**, not
  context-aware — it doesn't know *why* a template was last used (e.g. for
  a specific occasion).
- **CameraX front-camera guided capture is device-dependent** — the same
  "hardest tier to automate" testing bucket `phase-1-architecture.md`
  Section 27 already named for the back-camera pipeline, not a new gap.
- **`TryOnPlacementRepositoryImpl.anchorRegionFor` resolves only via
  `OutfitSlot`/`AccessoryCategory`/`JewelryCategory` keyword classification**
  — the same free-form, user-editable category tree every other
  slot-classification call in this codebase already accepts as an honest
  best-effort match, not a guaranteed one.

## Verification

`./gradlew clean build` across all 22+ modules (now including the two new
modules, `core:tryon` and `feature:tryon`) is **BUILD SUCCESSFUL** — 2,379
actionable tasks, 1,740 executed, zero `FAILED` occurrences anywhere in the
full log. Built and verified incrementally, milestone by milestone (Model →
Database → Sync → Repositories → Rendering → Lighting/Shadow/Masking →
Render Cache → Navigation → Full Build), compiling/linting/testing after
every milestone rather than batching to the end — consistent with every
prior phase's discipline.

Real issues found and fixed along the way (not glossed over): a Kotlin
compile error from importing `androidx.compose.foundation.layout.weight`
explicitly, which shadowed the correct `ColumnScope`/`RowScope` member
extension with an unrelated internal `RowColumnParentData` property (fixed
by removing the import — `weight` needs no import, it's a scope member);
several detekt findings (`MagicNumber` on named heuristic-constant maps,
suppressed with the same "the key already documents the number" precedent
`WardrobeAlerts.kt` established; `ReturnCount` on functions restructured to
single-return `if/else` expressions; `MatchingDeclarationName` from a
stray top-level enum sharing a file with unrelated composables, fixed by
extracting it; `LongMethod` on `WardrobeNavHost`'s `outfitsDestinations`
once the new Try On wiring pushed it over 60 lines, fixed by splitting a
`outfitsSettingsDestinations` sibling; `LongParameterList` on
`SavedLooksContent` once a fourth callback was threaded through, fixed by
bagging the grid's three action callbacks into a `SavedLooksGridActions`
data class, the same pattern `OutfitDetailActions` already established);
a real ViewModel-test race (calling `onPhotoCaptured` for all four poses
back-to-back without awaiting each one's state transition raced ahead of
the ViewModel's own async advance, capturing every photo against the same
stale `currentPose` — fixed by sequencing the test through the real
reactive `uiState` flow instead, matching how the guided-capture screen's
UI can only ever call it once per currently-displayed pose); and one test
intentionally *not* written (`TryOnRenderCache.render`'s undecodable-path
`null` guard) because Robolectric's default `BitmapFactory` shadow doesn't
faithfully reproduce real Android's file-not-found behavior — asserting
against it here would mean testing something this environment cannot
actually verify, so the production guard stands, undocumented by a test,
rather than backed by a fabricated one.
