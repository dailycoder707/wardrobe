# RC2 Code Health Report

Consolidates RC2's Phase 1 (Full Static Audit), Phase 3 (AI Robustness
Audit), Phase 4 (Android Audit), and Phase 7 (Dependency Audit). Every
finding cites the file(s) actually read; every fix cites its regression
test. Where nothing was found in a named category, that's stated
explicitly rather than omitted — per RC2's own rule, silence is not the
same as "checked and clean."

## Scope actually covered

Read in full: every file in `core/ai` (43 files — gateway, all 7 adapters,
job manager, cache, metrics, privacy, security, prompt versioning),
`core/image`'s highest-risk files (pipeline I/O, ML Kit segmentation/pose/
face wrappers, cleanup detector, camera controller, resizer), `core/database`'s
migration registry and wiring. Spot-checked: Hilt scope usage (grep-based,
whole repo), Flow-sharing patterns (`SharingStarted.Eagerly`/`GlobalScope`/
`runBlocking` misuse, grep-based, whole repo), `gradle/libs.versions.toml`.
**Not exhaustively read**: the ~15 feature modules' ViewModels/Compose
screens line-by-line — spot-checked, not audited file-by-file; stated as a
scope boundary, not rounded up to "fully audited."

## Confirmed defects found and fixed (4)

### 1. `AiJobManager` duplicate concurrent dispatch (real, cost-doubling)

Already fixed in Beta 1, carried forward here for completeness since RC2's
Phase 3 (retry safety, cache poisoning) directly concerns this mechanism.
See `TECHNICAL_DEBT.md` item 21 for the original writeup.

### 2. `GenericRestAdapter` hang on malformed base64 (real, deadlock)

**Evidence**: `Base64.decode(base64, Base64.DEFAULT)` throws
`IllegalArgumentException` on invalid/truncated input (verified — a test
feeding a 1-character, invalidly-padded string reproduced a real,
uncaught `IllegalArgumentException` from `adapter.run()`). `GenericRestAdapter.run()`'s
try/catch only handled `HttpException`/`SerializationException`, not this.
Since `AiCapabilityWorker.doWork()` (the caller, one layer up) also only
catches `IOException`/`TimeoutCancellationException`, this exception would
have propagated all the way out of the WorkManager worker uncaught —
crashing that worker's coroutine *without* completing the
`CompletableDeferred` the calling coroutine is awaiting, hanging it
permanently.

**Fix**: `decodeBase64Bitmap` now wraps the decode in `runCatching`,
turning malformed input into the same graceful `Failure` path an
undecodable image already takes.

**Regression test**: `GenericRestAdapterTest`'s "run returns Failure
rather than hanging when resultImageBase64 is not valid base64" —
reproduced red (uncaught `IllegalArgumentException`) before the fix,
green after.

### 3. `DefaultAiGateway` recorded duplicate cache writes/metric events for coalesced requests

**Evidence**: a direct consequence of fix #1 — once `AiJobManager.dispatch`
coalesces two concurrent identical requests into one real network call, both
the "owner" and the "joiner" caller in `DefaultAiGateway.dispatchVisionPrompt`/
`dispatchImageTask` still unconditionally called `handleVisionOutcome`/
`handleImageTaskOutcome`, which writes the `ai_result_cache` row *and*
records an `AiMetrics` event. A reproduced test showed 2 recorded `SUCCESS`
metric events and 2 cache upserts for what was genuinely one network call —
inflating cost/latency/success-rate telemetry and doing a redundant DB write
every time two callers raced for the same cache key.

**Fix**: `AiJobManager.dispatch` now returns `Dispatched<T>` (the value plus
an `isOwner` flag). `DefaultAiGateway` only performs the cache write and
metric recording when `isOwner` is true; a joined caller still gets the
identical result value, it just doesn't redundantly persist it.

**Regression test**: `DefaultAiGatewayTest`'s "two concurrent
runVisionPrompt calls for the identical request record only one SUCCESS
metric" — reproduced red (2 metric events, 2 cache upserts) before the
fix, green (1 of each) after.

### 4. `GeminiAdapter` API key leak into Logcat

See `SECURITY_AUDIT_RC2.md` for the full writeup — the headline finding of
this milestone. Fixed via `x-goog-api-key` header instead of a `?key=`
query parameter; verified by a rewritten `GeminiAdapterTest`.

### 5. `OrphanedImageCleanupWorker` orphan-sweep race (real, low-probability data loss)

**Evidence**: `ImageRepositoryImpl.commitStagedImage`
(`core/data/.../repository/ImageRepositoryImpl.kt:96`) moves a garment's
image files into their final location (`fileStore.moveStagingToGarment`)
**before** inserting the matching `image_metadata` rows
(`imageMetadataDao.insertAll`) — two sequential, non-atomic steps with a
real (if narrow, millisecond-scale) window between them. `OrphanedImageDetector.findOrphans`
(`core/image/.../cleanup/OrphanedImageDetector.kt`) had no age check at
all — it treated a file that had existed for 2 milliseconds identically to
one that had existed for 2 months: any file with no matching DB row was
deleted. If the daily cleanup sweep happened to run during that exact
commit window, it would delete a legitimately-just-saved photo the user
had not yet had a chance to see fail — real, silent, unrecoverable data
loss, however low the odds of the specific timing.

The old KDoc's own claim — "only possible after a crash/interruption,
since normal writes and DB inserts happen together" — was itself
inaccurate; they do not happen together, they happen sequentially.

**Fix**: `findOrphans` gained a required `olderThan: Instant` cutoff — a
file must be both unreferenced *and* older than the cutoff to be treated
as an orphan. `OrphanedImageCleanupWorker` passes a 60-minute cutoff (`MIN_ORPHAN_AGE_MINUTES`),
generous relative to the real window (which never involves anything
slower than local file/Room I/O) while still reclaiming true orphans well
within the same day. This mirrors the exact pattern the same file already
used for stale staging directories (`STALE_STAGING_AGE_HOURS`).

**Regression test**: `OrphanedImageDetectorTest`'s "findOrphans never
deletes a just-written unreferenced file, only ones older than the
cutoff" — a fresh, unreferenced file with a cutoff in the past (simulating
the real race window) is not deleted; a genuinely old one is.

**Severity assessment**: real but low-probability — the periodic worker
fires once per day; the actual race window is milliseconds. Fixed anyway
because the fix is small, fully testable, reuses an existing pattern, and
the failure mode (silent deletion of a user's real photo) is severe enough
that "very unlikely" isn't the same as "acceptable."

## Confirmed, evidenced, deliberately **not** fixed

### `CameraCaptureController.awaitCameraProvider()` — possible hang on `ListenableFuture` failure

**Evidence**: `awaitCameraProvider()` (`core/image/.../capture/CameraCaptureController.kt:71`)
calls `future.get()` inside a `future.addListener { }` callback without a
try/catch. `ListenableFuture.get()` is documented to throw
`ExecutionException`/`InterruptedException` on failure. If
`ProcessCameraProvider.getInstance(context)` ever fails to initialize, the
listener callback would throw before reaching `continuation.resume(...)`,
leaving the calling `suspend fun bindToLifecycle` hung indefinitely — the
same structural pattern as defect #2 above, in a different file.

**Why not fixed this pass**: this class's own KDoc states it "requires a
real camera; only exercisable via an instrumented test on a device/
emulator" — no unit test harness exists for CameraX in this project (confirmed:
no `CameraCaptureControllerTest.kt` exists anywhere). RC2's own acceptance
criteria require "every fix must include regression tests." A defensive
`runCatching`-style fix here would very likely be correct (it's the same
low-risk pattern already proven correct twice this milestone), but
applying it without any way to verify it in this environment would be the
same kind of unverified claim this whole audit exists to prevent. Logged
in `TECHNICAL_DEBT.md` as a scoped, ready-to-apply fix for whenever
instrumented/device testing becomes available.

### `MlKitFaceBlurrer`/`MlKitPersonRegionMasker`/`MlKitBackgroundRemover` — coroutine cancellation doesn't cancel the underlying ML Kit task

**Evidence**: each wraps `detector.process(input)` in
`suspendCancellableCoroutine` without an `invokeOnCancellation` callback.
If the calling coroutine is cancelled mid-detection (e.g. the user backs
out of the capture screen), the ML Kit task keeps running in the
background; resuming an already-cancelled continuation is documented-safe
(a no-op) in kotlinx.coroutines, so this is not a crash risk — just
wasted work for a result nobody will use.

**Why not fixed**: the shared ML Kit client (`by lazy`, one instance
across all calls) has no per-call cancellation handle to attach — the only
plausible "fix" would be closing the shared client itself, which would
break it for every *other* concurrent or future call, a strictly worse
outcome than the current minor waste. No safe fix exists without a larger
redesign (a per-call detector instance, or a cancellation-token wrapper),
which is out of RC2's "no redesign" scope for a low-severity, no-crash
finding. Documented, not changed.

## Areas checked with no defect found

- **Hilt scopes**: grep-verified zero use of `@ActivityScoped`/
  `@ViewModelScoped` anywhere in the project — every binding is
  `@Singleton` or unscoped, and every `Context` injection in `core/ai`/
  `core/data`/`core/image` is `@ApplicationContext`-qualified (grep-verified,
  zero unqualified `Context` injections found). No Activity-leak-via-Singleton
  pattern exists.
- **Flow/coroutine leaks**: zero `GlobalScope` usage, zero `SharingStarted.Eagerly`
  usage, zero production `runBlocking` usage anywhere in the repo (grep-verified).
- **Room migrations**: all 7 migrations (`MIGRATION_1_2` through `MIGRATION_7_8`)
  are registered in `DatabaseModule.kt`'s `.addMigrations(...)` call,
  matching the database's declared `version = 8` — no gap between the
  highest migration and the declared schema version.
- **Vendor adapters (Claude, OpenAI, Azure OpenAI, OpenRouter, Ollama)**:
  each reviewed individually — none decodes an incoming base64 image (only
  `GenericRestAdapter` does, see defect #2), none has an unguarded parsing
  path beyond the existing `HttpException`/`SerializationException` catches.
- **Bitmap/stream resource handling**: every `OutputStream` in
  `core/image`'s pipeline I/O and `core/ai`'s image caching uses `.use { }`;
  no unclosed stream found.
- **JSON instance reuse**: both response-parsing `Json` instances and the
  Gateway's DI-provided `Json` are singletons, not reconstructed per call.

## Phase 4 — Android Audit

Checked by code inspection; a real device remains the only way to verify
some of these (unchanged limitation, carried forward from every prior
milestone):

| Concern | Finding |
|---|---|
| Configuration changes / rotation | Handled by construction — every screen uses `ViewModel` (survives rotation) and `CameraCaptureController.bindToLifecycle` binds to the screen's own `LifecycleOwner`, so CameraX handles unbind/rebind across a config change natively. Not independently re-verifiable without a device. |
| Process death | Two known, already-documented in-memory-only stores would lose state on process death: `StagedImageStore` (a capture result mid-review) and `AiWorkRegistry` (an in-flight AI dispatch). Both are pre-existing, disclosed limitations (see their own KDoc and `TECHNICAL_DEBT.md`), not new findings — re-verified still accurate. |
| Low memory / `onTrimMemory` | No `onTrimMemory`/`ComponentCallbacks2` override exists anywhere in the app. The only explicit out-of-memory handling found is `decodeCapped`'s narrow `catch (oom: OutOfMemoryError)` around a single bitmap decode. **Disclosed gap, not fixed**: adding a systemic low-memory response (e.g., proactively clearing the AI result image cache) would be new capability, not a confirmed-bug fix, and is out of RC2's "no new features" scope. |
| Background restrictions / Doze | Every deferred/retriable operation already goes through WorkManager (`AiCapabilityWorker`, `ImageProcessingWorker`, `OrphanedImageCleanupWorker`, `SyncWorker`, `WeatherRefreshWorker`, backup workers), which natively defers correctly under Doze/App Standby — this is WorkManager's own guarantee, not something this app implements itself. |
| Camera interruptions | See `CameraCaptureController`'s disclosed hang risk above (found, not fixed — untestable in this environment). |
| Permission revocation | `DeviceLocationSource`'s `@SuppressLint("MissingPermission")` implies a permission check happens via a helper the linter can't trace — not independently re-verified against a live permission-revocation scenario (needs a device). |
| Storage removal / disk full | `writeOrThrow` (`GarmentImagePipelineIo.kt`) converts any `IOException` from a file write (which a disk-full or media-removed condition would raise) into an `ImageProcessingException` the pipeline already surfaces as a review-screen failure state — not a crash. |
| Network changes | `AiJobManager`'s `NetworkType.CONNECTED` WorkManager constraint means a dispatch already correctly waits out a network transition rather than failing immediately. |
| Clock changes | The AI subsystem (cache expiry, job timestamps, provenance) consistently uses an injected `Clock`, not `System.currentTimeMillis()` directly — testable and not vulnerable to wall-clock jumps affecting *logic* (only display timestamps elsewhre use `System.currentTimeMillis()` directly, which is cosmetic, not correctness-affecting). |
| Locale changes | Not applicable — this app has no locale-variant resources (single-language), confirmed via the resource directory structure; nothing to test here. |
| Multi-window | Not evaluated — requires a real device/emulator in split-screen; no code pattern found that would obviously break it (no hardcoded fullscreen assumptions found in the screens reviewed), but this is not the same as verifying it. |

## Suppression catalog (Phase 6/7 cross-check)

`README.md` and `PRODUCTION_VALIDATION_REPORT.md` previously claimed "zero
suppressions project-wide" — **false**, corrected this pass (see those
files' diffs). A full-repo grep for `@Suppress`/`@SuppressLint` found 23
sites across 19 files. Each was reviewed; all are individually justified,
none hides an unaddressed real issue:

| Category | Count | Justification |
|---|---|---|
| `LongParameterList` (Compose composables) | 9 | Callback-heavy composables (`GarmentTile`, `ClosetGrid`, `ClosetScreen`, `HomeScreen` ×2, `HomeScreenBody`, `RecommendationsScreen` ×2, `WardrobeSyncScreen`) — standard, idiomatic Compose practice |
| `LongParameterList` (Room DAOs) | 3 | `GarmentDao` ×2, `OutfitDao` — update methods over wide entities; already documented in `config/detekt/detekt.yml`'s own comment |
| `MagicNumber` | 4 | `CalendarConflictBuilders`, `StatsRepositoryImpl`, `WardrobeAlerts`, `DefaultPlacementCalculator` — file-level, named-constant-adjacent domain arithmetic |
| `TooGenericExceptionCaught`(/`SwallowedException`) | 3 | `WeatherRepositoryImpl`, `SyncEngine`, `SyncRepositoryImpl` — deliberate graceful-degradation boundaries (network/sync failure → stale cache/retry, not a crash) |
| `DEPRECATION` | 2 | `ImageResizer` — `Bitmap.CompressFormat.WEBP` has no non-deprecated equivalent below API 30, this app's minSdk is 26 |
| `UNCHECKED_CAST` | 1 | `AiJobManager` — `CompletableDeferred<Any?>`'s generic erasure, the standard, unavoidable pattern for this kind of typed bridge |
| `@SuppressLint("MissingPermission")` | 1 | `DeviceLocationSource` — permission is checked via a helper the lint tool can't statically trace |

**RC2 added zero new suppressions.**

## Dependency audit (Phase 7)

Cross-checked every library alias in `gradle/libs.versions.toml` against
actual usage in `build.gradle.kts` files project-wide:

- **No unused dependency found** beyond the one RC1 already removed
  (`org.junit.jupiter:junit-jupiter`).
- **No duplicate dependency found** (no two aliases resolving to
  competing libraries for the same job).
- **No obviously obsolete/superseded dependency found** — every version
  in the catalog carries a dated verification comment (2026-08-01 through
  2026-08-05) recording what was actually checked against Google's/Maven
  Central's real metadata, not assumed.
- **Not performed**: a live CVE/vulnerability-database lookup against
  each pinned version — this environment has no network access for that,
  and fabricating a "no known CVEs" claim without actually checking would
  violate this milestone's own "every claim needs evidence" rule. Disclosed
  as unchecked, not claimed as clean.
- **One naming clarity nitpick, not a defect**: `hiltNavigationCompose`
  (the version key) is reused for three different `androidx.hilt`
  artifacts (`hilt-navigation-compose`, `hilt-work`, `hilt-compiler`) — this
  is correct (those three ship in lockstep under one `androidx.hilt`
  release train) but the key's name doesn't make that obvious. Purely
  cosmetic; not changed, since renaming a version key is a zero-benefit
  churn RC2's "no redesign" rule argues against.

## Summary

| Metric | Count |
|---|---|
| Confirmed defects found | 5 (4 new this pass + 1 carried forward from Beta 1) |
| Confirmed defects fixed | 5 |
| New regression tests added | 4 |
| Confirmed, evidenced, deliberately unfixed (with rationale) | 2 |
| New suppressions added | 0 |
| Stale documentation claims found and corrected | 2 (`README.md`, `PRODUCTION_VALIDATION_REPORT.md`) |
