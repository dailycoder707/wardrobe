# :feature:tryon

Phase 10's virtual try-on screens — guided body-profile capture, the
interactive "Try On Me" canvas, and the manual mask editor. Reached from
Outfit Detail, Home's recommendation preview, Saved Looks, and Trip
Planner's packing list — see `phase-10-personal-virtual-tryon.md` for the
full design and `app`'s `WardrobeNavHost.tryOnDestinations` for the actual
wiring.

## Packages
| Package | Screen(s) |
|---|---|
| `capture/` | `BodyProfileCaptureScreen`/`BodyProfileCaptureViewModel` — the guided 4-pose sequence (`GuidedPoseOverlay` shows per-pose instructions), storage/DB round-trip via `BodyProfileRepository`, triggers `recomputeMeasurements()` exactly once after the last pose |
| `render/` | `TryOnScreen`/`TryOnViewModel` — the live interactive canvas: one `graphicsLayer`-backed layer per garment (sorted via `core:tryon`'s `sortForRender`), per-layer `detectTransformGestures`, the "Auto-placed — drag to adjust" confidence chip, "Reset to Auto Placement"/"Edit Mask" actions |
| `masking/` | `MaskEditorScreen`/`MaskEditorViewModel` — a dedicated erase/restore mode over one garment's own cutout, backed by `core:tryon`'s `GarmentMaskEditor` |
| `navigation/` | `TryOnRoute(outfitId, garmentIds)` (dual-input, mirrors `OutfitPreviewRoute`'s shape), `BodyProfileCaptureRoute`, `MaskEditorRoute(garmentId)` |

## Dual-input `TryOnRoute`

`outfitId` is set when a real, persisted `Outfit` exists (Outfit Detail,
Saved Looks, Trip Planner); `garmentIds` (a comma-joined string) is set
instead for Today's Recommendation's not-yet-saved scored outfit — the
same pattern `OutfitPreviewRoute` already established for the identical
problem.

## Reactive "needs body profile" prompt

`TryOnViewModel` observes `BodyProfileRepository.observeBodyProfile()` via
`collectLatest`, not a one-shot check — completing guided capture from
`TryOnScreen`'s own "needs body profile" prompt updates the same screen
instance in place once the profile exists, no fresh route navigation
required to pick it up.

## Testing

`BodyProfileCaptureViewModelTest` (3 tests, module-local
`FakeBodyProfileRepository`) — pose sequencing, one photo saved per pose,
measurements recomputed exactly once after the last pose. `TryOnCanvasTest`/
`MaskEditorCanvasTest` (11 tests, Compose UI, Robolectric) — layer count/
semantics/action-presence only, never visual appearance (no device, no
real photos to compare against — see `phase-10-personal-virtual-
tryon.md`'s Known Limitations).

## What's NOT here yet, deliberately

- Placement-template management UI (saving/naming/switching `CASUAL`/
  `FORMAL`/`CUSTOM` variants beyond the auto-managed `DEFAULT`) — the data
  layer (`TryOnPlacementRepository`) fully supports it; no screen surfaces
  it yet.
- A real device/instrumented test of the guided-capture camera flow or the
  live gesture-drag UX — the same "hardest tier to automate here" gap
  `core:tryon`'s README and `phase-1-architecture.md` Section 27 already
  state for garment capture.
