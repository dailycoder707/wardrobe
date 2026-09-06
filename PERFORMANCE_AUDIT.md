# RC2 Performance Audit

Phase 2 of RC2 (Production Hardening). Performed by inspection — reading
every code path that decodes/encodes/hashes/serializes/queries, tracing
how many times each operation actually runs per user action, and comparing
that against how many times it needs to. No profiler run, no real device:
that gap is unchanged from every prior milestone (M13/RC1/Beta 1) and is
not this document's job to close. What follows is what inspection *can*
establish with certainty.

## Method

For each category the RC2 spec named, the relevant call sites were traced
end-to-end (not sampled) for the two highest-traffic pipelines in this
app: the Add-to-Wardrobe import pipeline (`GarmentImagePipeline`) and the
AI Gateway (`DefaultAiGateway`). Every finding below cites the file(s)
actually read.

## Findings

### Bitmap decode count — reviewed, no duplication found

`GarmentImagePipeline.process()` (`core/image/.../pipeline/GarmentImagePipeline.kt`)
decodes the source file exactly **once** (`decodeAndOrient`, line 74),
then threads that single in-memory `Bitmap` by reference through crop →
extraction → enhancement → reconstruction → metadata → thumbnail. No stage
re-decodes the source file.

One legitimate **second** decode exists: `quickQualityCheck()` (same file,
line 57) decodes the source file again, independently, at a much smaller
target size (512px vs. the main pipeline's 2048px). This is not
duplicated work by oversight — `ImageRepositoryImpl` calls it as a fast,
throwaway pre-screen (blur/darkness check) *before* the user commits to
staging the photo at all, so the two decodes serve genuinely different
moments in the flow (instant feedback vs. the real, expensive pipeline).
**Estimated impact**: negligible — the pre-screen decode targets 512px vs.
2048px, roughly 1/16th the pixel count of the real decode it precedes.
**No change made** — removing it would remove the fast-feedback UX it
exists for.

### JPEG/WebP encode count — reviewed, no duplication found

Each image variant (`original`, `cutout`, `white_background`, `thumbnail`)
is encoded exactly once, in `GarmentImagePipelineIo.kt`'s
`writeCutoutVariant`/`writeWhiteBackgroundVariant`/`writeThumbnail`/
`saveOriginal` — each is called exactly once per `buildVariants` invocation
per import.

### Hashing — reviewed, no duplication found

`ImageHasher.sha256` is called once per file, at the one place a checksum
is actually needed (`File.toVariant`, `GarmentImagePipelineIo.kt:118`).
Its two other call sites (`BodyProfileRepositoryImpl`, `GarmentMaskRepositoryImpl`)
hash unrelated files (body profile photos, mask images) for their own,
separate checksums — not repeated computation on the same file.

The AI Gateway's in-memory `sha256(Bitmap)` (`core/ai/.../gateway/BitmapHash.kt`)
is computed exactly once per `runVisionPrompt`/`runImageTask` call, over
the *already privacy-preprocessed* (resized-down) payload — never the
full-resolution original.

### JSON serialization — reviewed, no duplication found

Both `Json` instances used for AI response parsing
(`MetadataPromptSupport.kt`'s `METADATA_JSON`, `CloudStylingEngine.kt`'s
`STYLING_JSON`) are top-level `private val`s — constructed exactly once
per process, not per call. `AiNetworkModule.provideAiJson()` is likewise a
`@Singleton`-scoped Hilt binding, not a per-request allocation.

### ML Kit invocations — reviewed, no duplication found

`MlKitBackgroundRemover`, `MlKitPersonRegionMasker`, `MlKitFaceBlurrer` each
call their respective ML Kit detector exactly once per pipeline stage per
image — no retry-without-need loop, no double-invocation found. Each
detector client is itself a `by lazy` singleton (constructed once,
reused across every call), not rebuilt per invocation.

### Room queries — spot-checked, no N+1 pattern found in the paths reviewed

`OrphanedImageCleanupWorker.doWork()` calls `imageMetadataDao.getAllFilePaths()`
once per sweep (not once per file) and diffs against the file-system
listing in memory — not a query-per-file loop. `AiJobManager.markStatus`
does one `getByCacheKey` + one `update` per state transition (PENDING →
RUNNING → SUCCEEDED/FAILED) — three total per dispatch, which is the
minimum needed to keep the ledger row honest at each transition, not
excess querying.

**Not exhaustively reviewed**: list/grid screens across `feature:closet`,
`feature:outfits`, etc. — a full N+1 audit of every `@Query`/Flow-mapping
call site across ~15 feature modules was out of scope for this pass given
RC2's time budget; nothing reviewed there raised a flag, but this is a
narrower claim than "audited," stated honestly rather than rounded up.

### WorkManager scheduling — reviewed, no excess scheduling found

Exactly one periodic worker exists (`OrphanedImageCleanupWorker`, once per
day) plus per-action one-time workers (`ImageProcessingWorker`,
`AiCapabilityWorker`, `SyncWorker`, `WeatherRefreshWorker`,
`BackupExportWorker`/`BackupRestoreWorker`) — each enqueued via
`enqueueUniqueWork`/`enqueueUniquePeriodicWork` with a policy that prevents
duplicate concurrent enqueues for the same logical unit of work. The one
real duplicate-dispatch defect in this area (`AiJobManager` not actually
achieving that de-duplication despite using unique work names) was a
**correctness** bug, not scheduling excess per se — see
`CODE_HEALTH_REPORT.md` for the fix.

## Estimated impact summary

| Area | Finding | Estimated impact |
|---|---|---|
| Bitmap decode | One legitimate extra small decode (quality pre-screen) | Negligible (~1/16th pixel count of the main decode) |
| Encode/hash/JSON/ML Kit | No duplication found | None |
| Room queries (AI/cleanup paths) | No N+1 pattern found | None |
| Room queries (feature list screens) | Not exhaustively reviewed | Unknown — disclosed gap, not a finding |
| WorkManager scheduling | Correctly minimal; one correctness bug (fixed) in de-duplication | See `CODE_HEALTH_REPORT.md` |

## Conclusion

No performance defect was found and fixed under this phase — every code
path traced was already efficient by the standard RC2 asked for (no
duplicate decode/encode/hash/serialize/ML-Kit-call/query). Per RC2's own
rule ("if no improvement is justified, leave the code unchanged"), nothing
was changed under this phase. The one performance-adjacent defect found
this milestone (`AiJobManager` failing to actually de-duplicate concurrent
identical dispatches) was a correctness bug with a performance/cost
consequence, not a pure performance finding — it's fixed and tested, and
detailed in `CODE_HEALTH_REPORT.md` rather than duplicated here.
