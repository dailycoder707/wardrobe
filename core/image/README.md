# :core:image

The complete image pipeline (Phase 5b): capture support, storage layout, EXIF/
resize/crop/hash processing, the Photo Quality Assistant, and background
removal behind ADR-008's swappable interface. See phase-5b-image-pipeline.md
for the full design (pipeline diagram, storage layout rationale, caching
strategy, cleanup strategy, testing strategy, and what's explicitly deferred).

Android but Room-agnostic: this module returns plain data (`StagedImage`,
`QualityReport`, from `core:model`) and never writes to Room — `core:data`'s
`ImageRepositoryImpl` is the only place a processed image becomes an
`image_metadata` row, the same layering Phase 5a established for every other
repository.

## Packages
| Package | Holds |
|---|---|
| `storage/` | `ImageFileStore` (the `images/{garmentId}/` directory-per-garment layout, kept from ADR-007) and `ImageFileNames` (deterministic, one filename per `ImageType`) |
| `pipeline/` | `GarmentImagePipeline` (the orchestrator — `quickQualityCheck` for the fast pre-processing check, `process` for the full stage sequence), `ExifOrientation`, `ImageResizer`, `ImageCropper`, `ImageProcessingException` |
| `quality/` | `ImageQualityAnalyzer` — the Photo Quality Assistant's six lightweight, model-free heuristics (resolution/sharpness/brightness/exposure/framing/background complexity) |
| `validation/` | `ImageValidator` — hard rejection of corrupted/unsupported/too-small files, before any full decode |
| `hashing/` | `ImageHasher` — SHA-256, pure `java.io`, no Android dependency |
| `segmentation/` | `BackgroundRemover` (ADR-008's interface), `MlKitBackgroundRemover` (this phase's provisional implementation — see caveat below), `BackgroundRemoverModule` (the one `@Binds` line that would change to swap it) |
| `capture/` | `CameraCaptureController` (CameraX wrapper), `GalleryImportSource` (Photo Picker + content-`Uri`-to-`File` copy) — thin, UI-framework-agnostic; Phase 5c wires these into actual screens |
| `cleanup/` | `OrphanedImageDetector` — pure functions backing `core:data`'s `OrphanedImageCleanupWorker` |

## Background removal: an honest caveat

ADR-008 asked for a real-photo spike (ML Kit Subject Segmentation vs. a
bundled TFLite model, ~20 garment photos) before committing to an
implementation. That spike needs a physical device and a real photo set,
neither of which exists in this development environment — running it here
would mean fabricating the result. `MlKitBackgroundRemover` is therefore a
**reasoned default, not a verified one**: the lower-cost candidate from
ADR-008's own comparison table, swappable by construction. See
`TECHNICAL_DEBT.md` and phase-5b-image-pipeline.md's "Background removal"
section.

## What's NOT here yet, deliberately
- Any UI (capture screen, review/edit screen, crop overlay) — Phase 5c.
- Share-to-app / drag-and-drop entry points — `ImageImportSource` (`core:model`)
  reserves the vocabulary, nothing wires an `Intent` filter yet (no screen to
  receive it).
- The empirical background-removal spike itself.
