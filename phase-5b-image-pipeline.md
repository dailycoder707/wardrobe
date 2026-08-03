# Phase 5b — Image Pipeline

Scope: capture, storage, processing, quality checking, background removal (behind
the ADR-008 abstraction), caching configuration, and cleanup for garment photos.
No UI, no outfit/styling/weather logic, no use-case orchestration — those stay
Phase 5c+/6/7 as instructed. `core:domain`'s `ImageRepository` is the seam Phase 5c's
ViewModels will call; nothing calls it yet.

## Architecture

```
core:model      — plain-Kotlin pipeline types (QualityReport, StagedImage, …)
core:domain     — ImageRepository interface (Flow-based progress, like BackupRepository)
core:image      — Android-but-Room-agnostic: storage layout, EXIF/resize/crop,
                   hashing, validation, quality analysis, BackgroundRemover +
                   ML Kit implementation, capture helpers, orphan detection
core:data       — ImageRepositoryImpl, ImageProcessingWorker,
                   OrphanedImageCleanupWorker, StagedImageStore (Hilt/Room/WorkManager glue)
core:ui         — Coil ImageLoader cache configuration
app             — SingletonImageLoader.Factory wiring, periodic cleanup scheduling
```

`core:image` still has no dependency on `core:database`/Room, same rule Phase 5a
established for `core:domain`: it processes files and returns plain data
(`StagedImage`), it never writes a Room row. `core:data` is the only place a
`StagedImage` gets turned into `ImageMetadataEntity` rows — this is the same
layering already used for garments/outfits/etc.

## Pipeline diagram

```
CameraX capture / Photo Picker import
        |
        v
  [validate: exists, decodable, format, min dimensions]
        |
        v
  [EXIF-read orientation, correct, strip EXIF] --> write images/{garmentId-or-temp}/original.jpg
        |                                           (long edge capped 2048px, JPEG q85)
        v
  [quick quality check available here — fast, no bg-removal wait]
        |
        v
  [optional user crop — applied in-memory to the decoded original, not
   persisted as its own file; see storage layout below]
        |
        v
  [BackgroundRemover.removeBackground] --> images/.../cutout.webp
        |            (failure => keep going, status = FAILED_KEPT_ORIGINAL,
        |             never a dead end — Constitution rule 8)
        v
  [thumbnail from best available: cutout > cropped-in-memory > original] --> images/.../thumb.webp
        |
        v
  [hash every file] --> StagedImage(stagingId, variants, qualityReport, bgStatus)
        |
        v
  ImageProcessingWorker reports progress via WorkManager.setProgress at each stage
        |
        v
  ImageRepository.commitStagedImage(stagingId, garmentId)
        -> moves temp/{stagingId}/* into images/{garmentId}/*, inserts image_metadata rows
  ImageRepository.discardStagedImage(stagingId)
        -> deletes temp/{stagingId}/ (user rejected the capture)
```

Every stage is a `ProcessingStage` enum value (`core:model`); the worker maps each
to a coarse percentage for `setProgress`.

## Storage layout — decision and why

```
filesDir/
  images/
    {garmentId}/
      original.jpg     — long edge capped 2048px, JPEG q85, EXIF stripped. Written
                          once during processing (into the staging directory
                          below, then moved — not rewritten — into place at
                          commit). Nothing in this codebase ever overwrites it
                          once written: cropping is an in-memory transform of the
                          decoded original, never a rewrite of the file, and
                          cutout/thumbnail are distinct files. So "never overwrite
                          the original" and "every derived asset reproducible"
                          both hold structurally — any derived file, including a
                          re-crop, can be regenerated from original.jpg alone.
      cutout.webp       — only written if background removal ran (lossless WebP,
                          alpha channel, same 2048px cap — ADR-007)
      thumb.webp        — 300px, quality 80, generated once from the best
                          available source (never regenerated at scroll time)
    temp/
      {stagingId}/      — same three filenames, for a capture not yet attached
                          to a garment. A stale-staging sweep (below) reclaims
                          these if the user backgrounds the app mid-review and
                          never returns.
    cache/              — deliberately empty; see note below
```

This keeps ADR-007's **directory-per-garment** decision rather than the
type-keyed top-level layout the master prompt sketched as an example
(`images/original/`, `images/cropped/`, …). The prompt explicitly left the exact
layout to this phase's judgment, and the type-keyed layout is strictly worse for
this phase's own orphan/cleanup requirement: "delete every derived image, never
delete another garment's assets" is a single `File.deleteRecursively()` on one
directory under the per-garment layout, versus filtering four separate top-level
folders by a `{garmentId}-` filename prefix under the type-keyed one. The
per-garment layout makes the safety property structural instead of something a
cleanup routine has to get right by careful filtering.

`images/cache/` is intentionally unused by this app's own code. Coil already owns
a disk cache (Section "Caching" below) at `context.cacheDir/image_cache/`, a
separate, OS-clearable location — mixing an app-managed cache into `filesDir`
alongside real user data would blur the one boundary (`images/` = irreplaceable,
`cacheDir` = throwaway) that makes Backup/Restore's "just zip `images/`" design
(ADR-007/ADR-009) simple. The directory exists as a reserved namespace so a
future non-Coil cache need never invent a new top-level folder, not because
anything writes to it today.

No `images/backup/` subdirectory: Phase 5a's Backup/Restore already zips the
whole `images/` directory wholesale (`BackupFileOperations`), so a nested backup
folder inside the thing being backed up would be self-referential.

## File naming — deterministic, no random filenames

One fixed filename per `ImageType` per garment/staging directory
(`ImageFileNames`, `core:image`) — `original.jpg` / `cropped.jpg` / `cutout.webp`
/ `thumb.webp`. Collisions are avoided structurally, not by hashing or UUIDs in
the filename:
- Two garments never collide — each owns its own directory
  (`{garmentId}`/`{stagingId}`).
- Re-running a downstream stage (re-crop, re-run background removal) overwrites
  only that stage's canonical file, which is exactly the desired "latest
  derived version" behavior — it is not the *original*, so overwriting it does
  not violate "never overwrite the original."
- The staging directory's random component (`stagingId`, a `UUID`) exists only
  to key *pre-commit* captures that don't have a `garmentId` yet; it plays no
  role once `commitStagedImage` moves the files into their permanent,
  garmentId-addressed home.

Content-hash-based dedup (importing the exact same photo file twice) is provided
as a mechanism (`ImageHasher.sha256`), not a policy: `core:image` computes and
exposes the hash, but *deciding* what to do about two garments sharing a photo
(warn? block? allow?) is a UX/use-case judgment call that belongs to Phase 5c,
once there's a screen to make that decision on.

## Photo Quality Assistant

`ImageQualityAnalyzer` (`core:image`) runs six checks against a downsampled
bitmap, deliberately **before** the (comparatively expensive) background-removal
step — so a UI can show quality feedback and let the user decide whether to
continue *before* paying for the heavy part of the pipeline:

| Check | Heuristic | Why this and not ML |
|---|---|---|
| `RESOLUTION` | Raw pixel dimensions vs. an absolute floor and a recommended floor | Free, exact |
| `SHARPNESS` | Variance of a Laplacian-style edge kernel over grayscale luminance | A well-established blur proxy that needs no model or network |
| `BRIGHTNESS` | Mean luminance | Catches "photo taken in a dim closet" |
| `EXPOSURE` | Fraction of pixels clipped near 0 or 255 | Distinct from brightness — a photo can have a mid-range mean while a large fraction of it is blown-out highlights |
| `FRAMING` | Foreground bounding-box estimate (pixels differing from the sampled border color) vs. frame area, plus an edge-touching check | Flags both "garment too small in frame" and "garment likely clipped by the frame edge" without needing segmentation |
| `BACKGROUND_COMPLEXITY` | Variance/edge-density sampled from the border region (assumed background) | A cluttered background is exactly what makes background removal (next stage) more likely to produce a bad edge |

Each check returns `PASS` / `WARNING` / `FAIL`; the report's `canProceedAnyway`
is always `true` — this is advisory, matching the prompt's own examples
("✓ Image sharp", "⚠ Garment partially cropped") and Constitution rule 8 (never
a dead end). None of this uses ML Kit or any model — it's the "lightweight"
analysis the prompt asked for, explicitly distinct from the AI-based background
removal.

## Background removal

`BackgroundRemover` (interface, `core:image/segmentation`) is unchanged from
ADR-008's Phase 1 design: `suspend fun removeBackground(bitmap): CutoutResult`.

**Implementation decision, with an honest caveat.** ADR-008 asked for a ~20-photo
spike (ML Kit Subject Segmentation vs. a bundled TFLite model) before committing
to one, run against this app's actual photo distribution (garment on a hanger,
flat-lay, worn, thin straps, cutout necklines). That spike needs a real device
camera and real garment photos; neither exists in this development environment
(no connected device/emulator, no sample photo set). Running it here would mean
fabricating the result, which is exactly what Constitution rule 4 rules out.

So this phase makes a **provisional, reasoned choice** rather than a **verified**
one: `MlKitBackgroundRemover`, using
`com.google.android.gms:play-services-mlkit-subject-segmentation` (real, on-device,
no server call — the model downloads once via Play Services' optional-module
mechanism, then every inference call is local; this does not conflict with
ADR-004's "no cloud," any more than any other Play Services library would). It's
the lower-cost candidate from ADR-008's own comparison table (no bundled model
file, no separate licensing review) and a reasonable default to build the rest of
the pipeline against, since `BackgroundRemover` is swappable by construction —
replacing it with a bundled TFLite model later is a one-file-plus-one-Hilt-binding
change, not an architecture change.

**What's still open, tracked in `TECHNICAL_DEBT.md`**: the actual edge-quality
comparison against this app's specific photo distribution has not been run. If
ML Kit's output looks bad on real garment photos (thin straps, cutout necklines —
exactly what ADR-008 flagged as unverified), swapping in a TFLite implementation
touches one class and one `@Binds` line.

Confidence is read from ML Kit's own `foregroundConfidenceMask` (averaged), not
fabricated — if a scalar confidence isn't obtainable, `CutoutResult.Success`
carries `confidence = null` rather than a made-up number.

## Caching

Per ADR-007 ("Coil… no custom cache layer invented on top"), this phase does not
build a second cache mechanism. It configures the one Coil already provides
(`core:ui`'s `WardrobeImageLoaderFactory`, wired through `WardrobeApplication`
implementing `SingletonImageLoader.Factory`):
- Memory cache: 25% of available app memory (`MemoryCache.Builder().maxSizePercent`).
- Disk cache: capped at 64MB at `context.cacheDir/image_cache/` (separate from
  `filesDir/images/` — see the storage-layout note above), LRU eviction is
  Coil's own.

This is the full answer to "memory cache / disk cache / cache invalidation /
cleanup / maximum size / eviction policy": bounding Coil's existing knobs, not
inventing new ones. Coil already invalidates by content (new file write = new
Coil cache key path/mtime) and evicts LRU once either bound is hit.

## Storage cleanup

`OrphanedImageCleanupWorker` (`core:data`, periodic, `NetworkType.NOT_REQUIRED`,
daily — matches Phase 1 Section 17's stated design) does two independent sweeps:
1. **True orphans**: `image_metadata.filePath` values are the source of truth;
   any file under `images/{garmentId}/` not referenced by a row is deleted. This
   only happens if a crash/interruption left a write half-done — normal
   operation never produces one, since `commitStagedImage` writes the DB row and
   moves the file together.
2. **Stale staging directories**: `temp/{stagingId}/` directories older than 24h
   with no corresponding commit — the user captured a photo, then closed or
   killed the app before saving or discarding it.

Garment hard-delete (`GarmentRepositoryImpl.deleteGarment`) now also deletes
`images/{garmentId}/` after the Room delete succeeds (Room's own `CASCADE` on
`image_metadata` only removes the *rows*; nothing deletes the *files* without
this). Because deletion is `ImageFileStore.deleteGarmentDirectory(garmentId)` —
one directory, addressed by id — "never delete another garment's assets" is
structural, the same property the storage-layout decision above was chosen for.

## Database integration

`ImageMetadataDao` gains one query this phase: `observeForGarment(garmentId):
Flow<List<ImageMetadataEntity>>`, alongside the existing suspend `getForGarment`/
`getAllFilePaths` (the latter is what cleanup's orphan sweep reads). No schema
change — `image_metadata`'s columns (Phase 3) already cover dimensions, byte
size, format, checksum, created date; "processing state" and "version" live in
`StagedImage`/`QualityReport` pre-commit and are not persisted post-commit
because nothing downstream needs to query by them — only whether an image
*exists* for a garment does, and that's the existing table.

## WorkManager

`ImageProcessingWorker` (`@HiltWorker`, `core:data`) runs the full pipeline for
one source file, reporting `ProcessingStage` progress via `setProgress`.
`ImageRepositoryImpl.stageImage` enqueues it as **unique work keyed by
`stagingId`** and exposes a `Flow<ImageProcessingProgress>` — the same
WorkInfo-to-Flow shape Phase 5a's `BackupRepositoryImpl` already established, not
a second pattern invented for this feature. A batch gallery import is simply N
enqueues, no special batch worker: WorkManager's own queueing handles that.

**Known limitation, documented rather than hidden**: the full `StagedImage`
result (file paths, quality report, hash) is handed from the worker to the
repository via an in-memory `StagedImageStore` (a `ConcurrentHashMap`), not a
Room table. If the process dies between a successful capture and the user
tapping "Save," that specific result is lost — the temp files remain on disk and
are eventually reclaimed by the stale-staging sweep, but the capture must be
redone. Accepted for a single, foreground, seconds-long review step; not worth a
dedicated persistence table for. Tracked in `TECHNICAL_DEBT.md`.

## Performance decisions

- Quality analysis and thumbnail generation both run on a **downsampled**
  bitmap (`BitmapFactory.Options.inSampleSize` computed from the target size
  *before* decoding), never a full decode-then-scale — avoids holding a
  full-resolution bitmap in memory for work that doesn't need one.
- The original write path decodes once, at the capped 2048px long edge; nothing
  in the pipeline decodes a garment photo at full camera resolution more than
  once.
- Thumbnails are generated once, at commit time, never regenerated at scroll
  time (ADR-007, restated) — Coil's disk cache means even the *decode* of
  `thumb.webp` is cached across scroll/re-entry.
- Bitmap decode failures from insufficient memory are caught narrowly around
  just the decode call (`OutOfMemoryError`), not a blanket `catch (Throwable)`,
  and surfaced as a typed `ImageProcessingException` the same way every other
  pipeline failure is — not a special case.

## Error handling

`ImageProcessingException(stage, cause)` — thrown by pipeline stages, caught by
`ImageProcessingWorker` and mapped to `Result.failure()` with a reason string in
output data, the same shape `BackupExportWorker`/`BackupRestoreWorker` already
use. Specific cases: corrupted/unsupported file → `ImageValidator` rejects
before any decode is attempted; interrupted processing → WorkManager retries or
the stale-staging sweep reclaims it; duplicate import → hash is exposed, policy
deferred (see File Naming above); storage full / permission denied → surface as
the underlying `IOException`/`SecurityException` wrapped in
`ImageProcessingException`, no attempt to paper over a real device-storage state
the app can't fix.

## Testing

| Layer | How | Why |
|---|---|---|
| `ImageHasher`, `OrphanedImageDetector`, `ImageFileNames`/`ImageFileStore` path logic | Plain JUnit, real `java.io.File` on a `TemporaryFolder` | Pure I/O, no Android needed |
| `ImageQualityAnalyzer` | Robolectric, synthetic bitmaps (solid color, checkerboard, deliberately dark/bright) | Robolectric's `Bitmap` shadow supports real `getPixel`/`setPixel`, so the heuristics are genuinely exercised, not mocked |
| `ImageValidator` | Robolectric, real malformed/valid byte arrays | Exercises `BitmapFactory.Options` bounds-decode against real corrupt input |
| `GarmentImagePipeline` | Robolectric, `FakeBackgroundRemover` test double | The pipeline's own orchestration (stage ordering, file writes, fallback on cutout failure) is what's under test, not ML Kit itself |
| `ImageRepositoryImpl` | Robolectric + in-memory Room (existing `core:testing` helper) | Stage → commit → observe round-trip, and stage → discard leaves no DB row |
| `MlKitBackgroundRemover` | **Not unit tested** | Requires real Play Services + device inference — same category Phase 1 Section 27 already named as the hardest tier to automate ("CameraX/ML Kit: instrumented, device-dependent"). Verifying its actual cutout quality is the deferred spike above, not something a JVM test can stand in for |
| `CameraCaptureController` | **Not unit tested** | Needs a real camera; instrumented-only, Phase 8 territory, consistent with the existing CameraX testing stance |

## Explicitly out of scope this phase

- Any UI (capture screen, review/edit screen, crop gesture overlay) — Phase 5c.
- Share-to-app / drag-and-drop: `ImageImportSource` (`core:model`) reserves the
  vocabulary (`CAMERA`, `GALLERY`, `SHARE`, `DRAG_AND_DROP`) so the pipeline's
  entry point is already source-agnostic, but no `AndroidManifest` intent
  filters are added — there is no screen yet to receive that intent, and adding
  one now would be a manifest entry with no working handler.
- Outfit/garment business logic, the styling engine, weather — untouched.
- The empirical background-removal spike itself (see above) — needs a real
  device and real garment photos.
