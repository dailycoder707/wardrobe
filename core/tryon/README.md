# :core:tryon

Phase 10's fully local 2D virtual try-on engine — guided front-camera
capture, on-device pose-assisted default placement, affine-only garment
compositing math, manual masking, deterministic lighting/shadow, and a
non-interactive render cache. No 3D avatar, no cartoon avatar, no cloud
rendering, no uploaded photos — every byte this module touches stays
on-device except through Phase 8's existing encrypted local-network sync
(Constitution rule 13/`docs/adr/ADR-011-permanent-privacy-first-
principles.md`'s one narrow exception). See `phase-10-personal-virtual-
tryon.md` for the full design and the core technical decision (affine
"sticker" compositing, never a body-contour warp) this module implements.

Android but Room-agnostic, the same layering `core:image` established:
this module returns plain data/`Bitmap`s and never writes to Room —
`core:data`'s repositories are the only place a captured photo or edited
mask becomes a persisted row.

## Packages
| Package | Holds |
|---|---|
| `capture/` | `BodyCaptureController` — `core:image`'s `CameraCaptureController` mirrored onto `CameraSelector.DEFAULT_FRONT_CAMERA`, the first front-camera binding anywhere in this app |
| `pose/` | `BodyAnchorEstimator` (ADR-008's swappable interface, applied to pose estimation) + `MlKitBodyAnchorEstimator` (real ML Kit Pose Detection landmark math — every fraction is a direct function of a real landmark coordinate, never fabricated) + `BodyAnchorEstimatorModule` |
| `placement/` | `DefaultPlacementCalculator` — pure geometry: `TryOnAnchorRegion` + whatever `BodyMeasurements` fractions resolved → a `GarmentPlacementTemplate`'s seed offset/scale/rotation |
| `storage/` | `BodyImageFileStore` — `images/body/{bodyProfileId}/` (photos) and `.../masks/{garmentId}.png`, a sibling root to `core:image`'s `images/{garmentId}/`, never touched by its garment-orphan cleanup sweep |
| `lighting/` | `LightingMatcher` — one deterministic histogram pass over the body profile's `NEUTRAL` photo, producing a real color-grade `LightingAdjustment` |
| `shadow/` | `ShadowRenderer` — deterministic alpha-darkened shadow silhouette from a cutout's own alpha channel, plus `supportsBlur(sdkInt)`, a real (not assumed) `RenderEffect`-API-31+ capability gate |
| `masking/` | `GarmentMaskEditor` — pure `Bitmap` erase/restore in the cutout's own pixel space, entirely manual, no auto-segmentation |
| `rendering/` | `sortForRender` (depth+slot z-order, pure function over any item type) + `TRY_ON_LAYER_WIDTH_FRACTION` (shared with `feature:tryon`'s live screen so a cached thumbnail matches its sizing convention) |
| `rendercache/` | `TryOnRenderCache` + `TryOnRenderCacheKey`/`TryOnRenderLayerInput` — a background `Canvas` compositor for non-interactive preview contexts only; the live interactive screen never reads from it |

## An honest caveat, the same shape as `core:image`'s background-removal one

Real-photo/real-device verification of render quality, lighting-match
naturalness, and shadow realism needs a physical device and real human/
garment photos, neither of which exists in this development environment —
running that verification here would mean fabricating the result. Every
piece above is genuinely unit-tested for **correctness of computation**
(geometry, histogram math, pixel-level erase/restore, cache-key
invalidation) — never for visual convincingness, which is unknown until
measured on a real device. See `TECHNICAL_DEBT.md` item 16 and
`phase-10-personal-virtual-tryon.md`'s Known Limitations.

## What's NOT here yet, deliberately

- Any UI at all — `feature:tryon` owns every screen; this module is pure
  capture/pose/placement/masking/lighting/shadow/cache logic.
- Body-contour warping, cloth simulation, perspective/ground-plane
  correction — explicitly out of scope for the reasons
  `phase-10-personal-virtual-tryon.md`'s core technical decision states.
- A real device/photo-set verification spike of any kind.
