# :feature:capture

The Add-to-Wardrobe ingestion flow — the release-blocking gap fixed
2026-08-04 (real first-time-user testing on a physical OnePlus Pad found no
visible way to add a garment anywhere in the app, even though the entire
capture→background-removal→save pipeline had existed since Phase 5b/5c).
Reached from a permanent FAB on Home and Closet (`feature:closet`'s
`AddToWardrobeSheet`) — see `TECHNICAL_DEBT.md` item 17 for the full
incident and `app`'s `WardrobeNavHost.captureDestinations` for the actual
wiring.

## Packages

| Package | Screen(s) |
|---|---|
| `capture/` | `GarmentCaptureScreen`/`GarmentCaptureViewModel` — CameraX preview + shutter (`core:image`'s `CameraCaptureController`, back camera), enqueues one file into the Room-backed import queue on success |
| `queue/` | `GarmentImportQueueScreen`/`GarmentImportQueueViewModel` — drives every not-yet-`READY_FOR_REVIEW` row through `ImageRepository.stageImage` **sequentially** (never concurrently — the pipeline decodes a full-resolution bitmap per item), always reading `ImportQueueRepository.observeQueue()` directly so "start a new import" and "resume one interrupted by a restart or crash" are the same code path |
| `review/` | `GarmentReviewMetadataScreen`/`GarmentReviewMetadataViewModel` — the staged cutout preview, a two-level `CategoryPicker`, the full metadata form, checksum/category+color duplicate-warning banners, and the `Save`/`Save as Draft` split |
| `navigation/` | `GarmentCaptureRoute`, `GarmentImportQueueRoute` (deliberately parameterless), `GarmentReviewMetadataRoute(queueItemId)` |

## One import pipeline, not three

"Take Photo", "Choose from Gallery", and "Import Multiple Photos" all
converge on the exact same Room-backed queue (`core:domain`'s
`ImportQueueRepository`) — the first two just enqueue a queue of one. This
avoids three separate code paths for what is structurally the same
operation at different batch sizes.

## Why the queue route takes no arguments

`GarmentImportQueueRoute` is an `object`, not a `data class` — the screen
never receives file paths via nav args. Whoever is adding files (this
module's own capture screen, or `feature:closet`'s `AddToWardrobeSheet`)
calls `ImportQueueRepository.enqueue(...)` *before* navigating here, and
the queue screen's ViewModel only ever reads `observeQueue()`. This means
reopening the queue after an app restart shows exactly the same state a
fresh import would — there is no separate "resume" code path to keep in
sync with the "start" one.

## Save vs. Save as Draft

Both call `GarmentRepository.saveGarment` then `ImageRepository
.commitStagedImage` — the only difference is `isReviewed` (`true` for
Save, `false` for Save as Draft). `isReviewed == false` is not new
plumbing: `Garment.isReviewed` and Home's "Continue Editing" section
(`feature:closet`) already existed and already filtered on it, but nothing
before this fix ever set it after initial creation — Save as Draft is
this codebase closing a loop it had already half-built.

## Testing

`GarmentImportQueueViewModelTest` drives a `FakeImageRepository`'s
per-file `Channel<ImageProcessingProgress>` by hand to prove true
sequential processing (the second item's `stageImage` call is asserted to
not happen until the first item's channel is completed), retry, and the
stale-`SAVING`-row resume correction. `GarmentReviewMetadataViewModelTest`
(Robolectric — `SavedStateHandle.toRoute` touches real `Bundle` internals)
covers checksum/metadata duplicate detection, the lost-staged-image
restage path, and both save actions.

## What's NOT here, deliberately

- On-device category-suggestion ML (e.g. ML Kit Image Labeling) — cut from
  scope since intelligence/AI work is explicitly paused for this fix; the
  `CategoryPicker` never preselects anything. A candidate fast-follow.
- A crop UI in the review screen — the underlying pipeline supports an
  optional `cropRect`, but nothing in the request asked for a crop step.
- A perceptual/visual duplicate-detection hash — only exact-checksum and
  category+color metadata matching exist.
- A real device/photo-set verification of camera capture, Photo Picker
  behavior, or background-removal quality — the same "hardest tier to
  automate here" gap already disclosed for every prior camera-touching
  phase (no physical device in this environment). This is exactly why the
  user's new permanent standing rule — every phase validated by a
  first-time-user real-device walkthrough before sign-off — now exists.
