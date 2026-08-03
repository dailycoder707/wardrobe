# ADR-007: Image Storage Strategy

**Status**: Accepted (Phase 1 Section 17, configured in Phase 2)

## Context

Photos are this app's core content type, and with no cloud storage (ADR-004) the
device's own storage is the only place they can live. At 500+ garments, storage
footprint, thumbnail performance, and cleanup of orphaned files all need a real
strategy rather than "just save the file somewhere."

## Decision

App-internal storage (`context.filesDir/images/{garmentId}/`), not `MediaStore` —
these are not user-facing gallery media until the user explicitly exports one. Three
variants are stored per garment:
- `original.jpg` — long edge capped at 2048px, JPEG quality 85 (no alpha needed).
- `cutout.webp` — lossless WebP, alpha channel preserved, same size cap.
- `thumb.webp` — 300px, quality 80, generated once during the capture pipeline, never
  on-demand at scroll time.

Every stored file has a corresponding `image_metadata` Room row (dimensions, byte
size, format — Phase 1 Section 9), which is what makes orphaned-file cleanup and
Backup/Restore integrity checking possible. Estimated footprint: ~207MB for 500
garments (250KB + 150KB + 15KB each, Phase 1 Section 17) — small enough to just show
as a number in Settings rather than needing active management in v1.

## Consequences

**Positive**:
- Predictable, calculable storage footprint communicated to the user.
- Not cluttering the system photo gallery with cutouts/thumbnails the user never
  intended to browse there.
- Backup exclusion (ADR-009) is straightforward: exclude one directory
  (`images/`), not a scattered set of MediaStore-managed URIs outside the app's
  control.
- Coil (`core:ui`) reads these as plain local files — no network fetcher artifact
  needed at all (see `DEPENDENCIES.md`).

**Negative**:
- The app fully owns file lifecycle: an `OrphanedImageCleanupWorker`
  (`phase-1-architecture.md` Section 17) must exist and run periodically, because
  nothing else will garbage-collect a file left behind by an interrupted capture.
- No OS-level gallery integration — a user can't casually find a garment photo via
  their normal Photos app, which is a deliberate tradeoff, not an oversight.

## Alternatives Considered

- **MediaStore-backed storage**: rejected — makes cutouts/thumbnails visible in the
  system gallery (noisy, confusing for the user), and complicates the "this app's data
  stays contained and excludable" story that backs ADR-009.
- **Store only the original, regenerate cutout/thumbnail on demand**: rejected —
  background removal (ADR-008) is comparatively expensive; regenerating it on every
  scroll/view would be a real performance and battery cost for no benefit over storing
  the result once.
- **Single combined image per garment (no separate cutout/thumbnail)**: rejected —
  the confidence-signalled edit flow (Constitution rule 7) needs the original
  available for re-processing/comparison even after a cutout exists, and the browse
  grid needs a cheap-to-decode thumbnail distinct from the full-res cutout.
