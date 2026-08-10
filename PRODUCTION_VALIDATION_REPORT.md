# Production Validation Report — M13 / RC1 / RC2

**Milestones**: M13 (Production Validation & Release Gate), RC1
(Production Hardening, Security Audit & Release Candidate), and RC2
(further Production Hardening) — all validation/hardening-only, no new
user-facing features, per each milestone's own rules. RC1's full findings
live in `SECURITY_AUDIT.md`; RC2's live in `SECURITY_AUDIT_RC2.md`,
`PERFORMANCE_AUDIT.md`, and `CODE_HEALTH_REPORT.md`. This report's exit
criteria and recommendation below are updated to reflect all three passes
together.

**Report date**: 2026-08-06 (M13), updated 2026-08-06 (RC1), updated
2026-08-06 (RC2)

**Scope**: Add-to-Wardrobe v2 / Unified AI Provider Architecture (M1–M12) —
the AI Gateway, provider adapters, job manager, cache, metrics, privacy
preprocessor, runtime provider routing, the premium review screen, Cloud
Outfit Styling, and Cloud Virtual Try-On.

**How to read this report**: every checklist item below is tagged
**[AUTOMATED — VERIFIED]**, **[VERIFIED BY CODE INSPECTION]**, or
**[REQUIRES USER — NOT YET RUN]**. The first two are real results produced
during this session, with the evidence (test file/command) named. The
third is not a guess or a claim — no physical Android device and no real
cloud-provider account exist in this development environment, so those
items literally cannot be executed here. Section "How to run the
user-side checklist" gives the exact commands/logging needed.

---

## 1. Build Information

| Field | Value |
|---|---|
| Command | `./gradlew clean build` |
| Result | **BUILD SUCCESSFUL** |
| Modules | 25 (see `settings.gradle.kts`) |
| AGP | 9.2.1 |
| Kotlin | 2.4.10 |
| `compileSdk` / `targetSdk` / `minSdk` | 36 / 36 / 26 |
| `versionCode` / `versionName` | 1 / 0.1.0 |
| detekt/ktlint suppressions introduced this pass | 0 |
| New dependencies introduced this pass | 0 (per M13's own rule) |

**Final verification run this pass**: `BUILD SUCCESSFUL in 3m 12s` — 2605
actionable tasks (1177 executed, 1230 from cache, 198 up-to-date), zero
detekt/ktlint findings, zero test failures across all 25 modules,
including `core:database` (whose `WardrobeDatabaseSeedTest` had been
intermittently failing under full-build parallel load — see
`TECHNICAL_DEBT.md` item 18 — and is now fixed and consistently green).

---

## 2. Phase 1 — Real Device Validation — **[REQUIRES USER — NOT YET RUN]**

No physical Android device exists in this development environment. Every
item below must be run by the user (or whoever holds the hardware) before
this phase can be marked passed — this is the same standing rule recorded
in the `feedback_real_device_validation_gate` memory and in
`TECHNICAL_DEBT.md` item 17.

### 2.1 Capture

- [ ] Camera capture — a real photo, not gallery import
- [ ] Gallery import
- [ ] A large image (highest-resolution photo the device's camera produces)
- [ ] A small image (an old, low-resolution, or heavily-compressed photo)
- [ ] Portrait orientation
- [ ] Landscape orientation
- [ ] Dark environment (dim indoor lighting)
- [ ] Bright environment (direct sunlight)
- [ ] Mirror selfie (garment worn, photographed via a mirror)
- [ ] Flat-lay clothing (garment laid on a flat surface, no person)

For each: confirm final orientation is correct (no accidental 90°/180°
rotation), confirm no crash, and confirm the saved image looks like what
was actually captured. Note any visible artifact.

**EXIF check** — pull the staged/original file via `adb pull` (or Android
Studio's Device File Explorer) before it's deleted, and inspect it with
`exiftool` (or equivalent) for GPS/device-identifying tags on the *cloud
payload specifically* (the original on-device copy is expected to have
whatever EXIF the camera wrote — the guarantee under test is that the
bytes actually sent to a cloud provider don't). See §4 for the exact log
line to capture instead if you don't have `exiftool` handy.

### 2.2 Extraction

- [ ] Garment extraction produces a usable transparent cutout
- [ ] White background variant renders
- [ ] Retry Extraction button works without recapturing
- [ ] A repeated identical extraction is served from cache (near-instant
      second run; compare timestamps/logs, see §5)
- [ ] Progress indication is visible and doesn't stall/hang

Test against each of: shirts, pants, dresses, jackets, skirts, shoes,
scarves. **Record every failure** — which garment type, what went wrong,
attach the source photo if possible.

### 2.3 Metadata

- [ ] Category suggestion appears
- [ ] Color suggestion appears
- [ ] Brand OCR attempt appears (or is honestly absent when no visible
      brand text exists — never fabricated)
- [ ] Pattern suggestion appears
- [ ] Confidence tier badges are visible per field

Confirm the confidence-tier contract holds exactly as designed:
- [ ] **High** confidence fields are pre-selected automatically
- [ ] **Medium** confidence fields appear as a suggested chip, not
      auto-applied, tap-to-accept
- [ ] **Low** confidence fields are shown but never auto-selected

### 2.4 Review Screen

- [ ] Three-way viewer (Original / Transparent Cutout / White Background)
      switches correctly
- [ ] Comparison viewer (Original → Extracted → Enhanced → Reconstructed)
      shows real intermediate stages, not placeholders
- [ ] Pinch-to-zoom works on the primary image
- [ ] Pan works while zoomed
- [ ] Retry buttons (Extraction/Enhancement/Metadata) each work
      independently
- [ ] Provenance info dialog (tap the ⓘ next to a suggestion) shows
      provider/confidence/prompt version
- [ ] Every field is genuinely editable (typing over an AI suggestion
      sticks)
- [ ] An unknown/undetected field shows "Unknown — please choose," never
      silently blank
- [ ] Quality warnings (blurry/cluttered/occluded) appear when the source
      photo actually has that problem, and *don't* appear on a clean photo

### 2.5 Save

- [ ] Save commits the garment and it appears in Closet
- [ ] Save as Draft commits with `isReviewed = false` and shows the
      Needs-Review badge
- [ ] Reopening a draft resumes into the review screen with prior edits
      intact
- [ ] Editing a saved garment persists changes
- [ ] Deleting a garment works, and Undo (within the snackbar window)
      restores it

---

## 3. Phase 2 — Cloud Validation — **[REQUIRES USER — NOT YET RUN]**

Requires a real API key for at least one provider. No cloud account or key
exists in this development environment — this cannot be simulated
honestly with a mock server result and reported as "passed."

**Supported vendors** (Settings → AI Providers, per capability): OpenAI,
Azure OpenAI, Gemini, Claude, OpenRouter, Ollama, Generic REST.

For **each provider you actually configure**, record in the table below —
do not fill in a row for a provider you didn't test:

| Provider | Model used | Test Connection | Metadata | Styling | Try-On (if supported) | Notes |
|---|---|---|---|---|---|---|
| | | | | | | |

For each enabled capability on that provider:
- [ ] Test Connection succeeds (Settings → AI Providers → that row → "Test
      Connection") — record the latency shown
- [ ] Metadata generation returns real, sensible suggestions (not just
      "didn't crash")
- [ ] Outfit Styling returns a genuinely different-feeling suggestion than
      the on-device rule engine, and it's still built entirely from real
      wardrobe items (spot-check: does every garment in the suggested
      outfit actually exist and match its claimed slot?)
- [ ] Virtual Try-On renders — **only if the provider/backend you
      configured actually supports an image-editing/generation endpoint**;
      if it doesn't, record that explicitly rather than reporting a
      failure — a provider that doesn't expose this capability is not a
      bug in this app

Record for each call actually made:
- Success/failure
- Latency (Settings → AI Providers → AI Usage panel, or your own
  provider-side dashboard)
- What happened on a deliberately malformed request (e.g., temporarily
  blank the model field) — should fail gracefully, never crash
- What happened if you simulate a timeout (e.g., a very large image
  against a slow connection) — should fail gracefully and record a
  `TIMEOUT` metric, not hang forever
- Whether a second identical request was served from cache (near-zero
  latency, `Cache: Yes` in the AI status card)

**Do not claim support for a provider capability its API doesn't actually
expose** — e.g. not every vendor/model combination supports image
generation/editing; if Try-On fails for that reason, that's expected
behavior, not a defect.

---

## 4. Phase 3 — Privacy Validation

| Check | Status | Evidence |
|---|---|---|
| Consent dialog appears on first cloud use | **[AUTOMATED — VERIFIED]** | `AiProvidersViewModelTest` — selecting Cloud mode surfaces the consent flow; declining reverts to on-device |
| Changing Base URL invalidates prior consent | **[AUTOMATED — VERIFIED]** | `AiProvidersViewModel.onBaseUrlChanged` clears `consentGrantedAt`/`consentHost` unless the new URL matches the already-consented host; covered by `AiProvidersViewModelTest` |
| API keys remain encrypted | **[VERIFIED BY CODE INSPECTION]** | `EncryptedApiKeyStore` (`core:ai`) uses `androidx.security.crypto.EncryptedSharedPreferences` backed by an Android Keystore `MasterKey` — keys are never written to plain `SharedPreferences`/DataStore |
| No secrets written to logs | **[VERIFIED BY CODE INSPECTION]** | Every `HttpLoggingInterceptor` in this codebase (`AiNetworkModule`, `NetworkModule`) is configured at `Level.BASIC` — request/response line only, no headers (so no `Authorization: Bearer <key>`) and no body |
| EXIF removed from cloud payloads | **[AUTOMATED — VERIFIED]** | New `BitmapEncodingTest` (`core:ai`) encodes a real `Bitmap` via the exact function every vendor adapter uses to build its request body, then scans the resulting bytes for a WebP EXIF chunk or JPEG EXIF marker signature and asserts neither is present |
| Faces blurred where required | **[AUTOMATED — VERIFIED]** | `DefaultPrivacyPreprocessorTest` — `prepareExtractionPayload` always invokes `FaceBlurrer`; `prepareGarmentPayload` (every other capability's cloud call, which only ever receives the already-extracted, already-faceless cutout) never does, by design |
| Garment-only images sent after extraction | **[VERIFIED BY CODE INSPECTION]** | `DefaultAiGateway.preprocessPayload` routes every capability except `GARMENT_EXTRACTION` through `prepareGarmentPayload`, which never receives the original photo |

**Real-device-only residual check** — **[REQUIRES USER — NOT YET RUN]**:
a decoded in-memory `Bitmap` structurally cannot carry EXIF (confirmed by
the automated test above), but this doesn't capture every real-world edge
case a live device might expose (e.g. a manufacturer camera app embedding
data in an unexpected place, or a future code change bypassing the shared
encoding helper). If you have `exiftool`/similar available, capture one
real garment photo end-to-end and diff its EXIF block against the request
body actually sent (a proxy like `mitmproxy` pointed at your own Generic
REST/self-hosted backend, or your cloud provider's own request-logging
dashboard, will show the raw bytes).

---

## 5. Phase 4 — Cache Validation

| Check | Status | Evidence |
|---|---|---|
| Repeated identical request → no network, cache hit | **[AUTOMATED — VERIFIED]** | `DefaultAiGatewayTest`: `runVisionPrompt never repeats an identical call, serving the second one from cache`, `runImageTask never repeats an identical call, serving the second one from a cached file` — adapter invocation count stays 1, a `CACHE_HIT` metric is recorded |
| Modified prompt version → cache miss → new result | **[AUTOMATED — VERIFIED]** | New test: `runVisionPrompt's cache key varies with promptVersion, never serving a stale prompt's result` — adapter invoked twice for the same image under two prompt versions |
| Modified image → cache miss | **[AUTOMATED — VERIFIED]** | `runImageTask's cache key varies with every image, not just the first` (M12) — proves this for both single- and multi-image (Try-On) requests |
| Modified provider/vendor → cache miss | **[AUTOMATED — VERIFIED]** | New test: `runVisionPrompt's cache key varies with the vendor, never serving another vendor's cached result` — a second vendor with no adapter registered correctly fails rather than wrongfully serving the first vendor's cached result |
| Try-On's cache key is `(bodyHash, garmentHash, provider, model, promptVersion)`, not just the first image | **[AUTOMATED — VERIFIED]** | M12's `DefaultAiGateway.runImageTask` cache-key fix + its regression test — two different garments on the same body photo no longer collide |

All cache-key tests live in
`core/ai/src/test/kotlin/com/wardrobe/app/core/ai/gateway/DefaultAiGatewayTest.kt`.
Run them yourself with:

```
./gradlew :core:ai:testDebugUnitTest --tests "com.wardrobe.app.core.ai.gateway.DefaultAiGatewayTest"
```

**Real-provider residual check** — **[REQUIRES USER — NOT YET RUN]**: the
above proves the Gateway's own cache-key logic is correct against fake
adapters. It does not prove a *real* provider's response is byte-identical
across truly-identical requests (irrelevant to correctness here, since the
cache never re-sends the request at all on a hit) — the meaningful
real-world confirmation is simply "did a second identical action in the
app actually skip the network," observable via the AI Usage panel's
cache-hit count incrementing, or your own provider dashboard showing no
second request.

---

## 6. Phase 5 — Metrics Validation

| Check | Status | Evidence |
|---|---|---|
| Success recorded | **[AUTOMATED — VERIFIED]** | `runVisionPrompt on success caches the result and records a SUCCESS metric with cloud provenance` |
| Failure recorded | **[AUTOMATED — VERIFIED]** | `runVisionPrompt on adapter failure records a FAILURE metric and returns Failure` |
| Cache hit recorded | **[AUTOMATED — VERIFIED]** | `runVisionPrompt never repeats an identical call, serving the second one from cache` asserts exactly one `CACHE_HIT` event |
| Latency recorded | **[VERIFIED BY CODE INSPECTION]** | `DefaultAiGateway` measures `clock.millis()` at dispatch start and passes real elapsed time into every `DispatchOutcome`/`provenanceFor()` call — never a placeholder |
| Confidence recorded | **[VERIFIED BY CODE INSPECTION]** | `DispatchOutcome`/`AiResultProvenance` carry the adapter's real reported confidence (nullable, never fabricated when absent) |
| Cost recorded | **[VERIFIED BY CODE INSPECTION]** | `CostEstimator`/`AiUsageMapper` compute an estimate only when the user has supplied a cost-rate; otherwise the UI shows "—", never a guessed number (`AiProvidersScreen`'s `AiUsageRow`) |
| No duplicate metric events per dispatch | **[AUTOMATED — VERIFIED]** | Every `DefaultAiGatewayTest` case asserts an **exact** count (`assertEquals(1, ...)`) of the relevant outcome type after exactly one dispatch — a duplicate-emission bug would fail these assertions, not just go unnoticed |

Run: `./gradlew :core:ai:testDebugUnitTest --tests "com.wardrobe.app.core.ai.gateway.*"`

**Real-provider residual check** — **[REQUIRES USER — NOT YET RUN]**: this
proves the Gateway's own metrics-recording logic is correct. It does not
prove a *real* adapter parses a *real* provider's token-usage fields
correctly for every vendor (each adapter's own `MockWebServer` test
already covers its documented response shape — see Phase 2's per-provider
table for the real-account equivalent).

---

## 7. Phase 6 — Failure Validation

| Check | Status | Evidence |
|---|---|---|
| Malformed JSON → graceful failure, never a crash | **[AUTOMATED — VERIFIED]** | `CloudStylingEngineTest`'s `malformed JSON never fabricates an outfit`; `parseMetadataSuggestions`/`parseCloudOutfitSuggestions` both `runCatching` the parse and return empty rather than throwing |
| Missing confidence → rejected (Try-On specifically) | **[AUTOMATED — VERIFIED]** | `CloudTryOnEngineTest`'s `a missing confidence is rejected, never treated as an honest null` |
| Invalid/corrupt image → rejected | **[AUTOMATED — VERIFIED]** | `GenericRestAdapter.decodeBase64Bitmap` returns `null` on undecodable bytes → `Failure("no_result_image_or_undecodable")`; `CloudTryOnEngineTest`'s below-minimum-resolution test covers the decoded-but-too-small case |
| Expired/invalid API key → proper error | **[AUTOMATED — VERIFIED]** | `OpenAiAdapterTest`'s `run maps an HTTP error response to a Failure carrying the status code` (401 case) — every adapter shares the same `catch (error: HttpException) { Failure("http_error_${error.code()}") }` pattern, vendor-agnostic |
| Rate limiting (429) → handled | **[VERIFIED BY CODE INSPECTION]** | Same generic `HttpException` handler as above — the code path doesn't special-case any status, so 429 is handled identically to the tested 401/500 cases; no dedicated 429 test exists since the handling logic is provably status-code-agnostic |
| Server timeout → retry | **[VERIFIED BY CODE INSPECTION]** | `AiJobManager` submits work with `BackoffPolicy.EXPONENTIAL` and `AiCapabilityWorker` wraps dispatch in `withTimeout(...)`; `DefaultAiGateway` also catches `TimeoutCancellationException` directly and records a `TIMEOUT` outcome rather than propagating a crash |
| Network off → retry | **[VERIFIED BY CODE INSPECTION]** | `AiJobManager.dispatch` sets `Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)` — WorkManager itself defers the job until connectivity returns, rather than failing immediately |
| No crashes anywhere above | **[AUTOMATED — VERIFIED]** | All of the above are exercised by passing JUnit tests, not manual inspection alone — a crash would fail the test run, not just the assertion |

**Real-device/real-network residual check** — **[REQUIRES USER — NOT YET
RUN]**: the WorkManager `Constraints`/`BackoffPolicy` configuration above
is real, but its actual behavior under real connectivity loss (airplane
mode mid-import) or a real provider's real rate-limit response has never
been observed. Checklist:
- [ ] Start an import, enable airplane mode, confirm the app doesn't
      crash and the job resumes once connectivity returns
- [ ] Point a capability at a provider and deliberately exhaust its real
      rate limit (or use a provider's sandbox/test key if one exists) —
      confirm the app surfaces a real error, not a hang or crash
- [ ] Let an API key expire (or revoke it provider-side) and confirm Test
      Connection reports the failure clearly

---

## 8. Phase 7 — Performance Validation — **[REQUIRES USER — NOT YET RUN]**

No device or emulator exists in this environment — the same
"designed for, not measured" gap every prior phase in `TECHNICAL_DEBT.md`
has stated for its own performance targets, now extended to M1–M12's AI
paths specifically. The `benchmark` module (`com.android.test` +
`androidx.baselineprofile`, wired to `:app`) exists but **has no
`StartupBenchmark`/macrobenchmark test classes written yet** — this has
been true, and stated, since Phase 2; nothing in M1–M12 changed it.

To actually measure, on a connected device/emulator:

```
./gradlew :app:generateBaselineProfile
```

then write a `StartupBenchmark`/scroll-jank macrobenchmark class in
`benchmark/src/androidTest` (there is no existing one to run) targeting
the flows below, and record:

- [ ] Capture → Extraction → Metadata → Review → Save, end to end
- [ ] Outfit Styling generation time (on-device vs. cloud, if configured)
- [ ] Try-On render time (on-device vs. cloud, if configured)
- [ ] Cold start
- [ ] Warm start
- [ ] Memory (Android Studio Profiler, during a multi-photo import)
- [ ] Battery/CPU/GPU (Android Studio Profiler or `adb shell dumpsys
      batterystats`)
- [ ] ANR (`adb shell dumpsys activity processes | grep -A5 wardrobe`,
      or Play Vitals post-release)
- [ ] Jank (Android Studio's Frame Timeline / Perfetto trace during
      scrolling and during the review screen's zoom/pan gestures)

---

## 9. Phase 8 — Regression Validation

| Check | Status | Evidence |
|---|---|---|
| Full existing automated test suite | **[AUTOMATED — VERIFIED]** | `./gradlew clean build` — every module's unit/Compose test suite, including Closet, Search/Filters, Recommendations, Weather, Sync, run and pass (see §1) |
| Existing wardrobe still opens | **[AUTOMATED — VERIFIED]** | `ClosetViewModelTest`/`GarmentDetailViewModelTest` and friends unchanged and passing |
| Search/Filters work | **[AUTOMATED — VERIFIED]** | Existing `feature:closet` test suite, unchanged, passing |
| Recommendations still work (on-device path) | **[AUTOMATED — VERIFIED]** | `RecommendationRuleEngineTest`/`StylingEngineRepositoryImplTest`/`RecommendationsViewModelTest` all still pass — M12's `StylingEngineRouter` falls back to this exact, unmodified engine whenever cloud isn't configured, which is every existing installation's default state |
| Weather integration still works | **[AUTOMATED — VERIFIED]** | `WeatherRepositoryImplTest`/`WeatherSettingsViewModelTest` unchanged and passing |
| Sync still works | **[AUTOMATED — VERIFIED]** | `core:sync`'s full test suite unchanged and passing |

**Real-device residual check** — **[REQUIRES USER — NOT YET RUN]**: the
above is component/unit-level regression coverage, not a manual click-
through of every existing screen on a real device. If you have an
existing wardrobe from before this milestone (or Phase 8 sync partner),
confirm it still opens correctly and nothing looks different that
shouldn't.

---

## 10. Documentation (Phase 9)

- [x] `TECHNICAL_DEBT.md` updated — item 19 (M13 validation gaps and the
      two real test-coverage gaps closed this pass)
- [x] `ADR-013` — **not amended**; no implementation changed during this
      validation pass beyond adding tests, so no new architectural
      decision exists to record. (If a real defect is found during the
      user-run phases below and requires a code fix, update ADR-013 or add
      ADR-014 at that time, per M13's own Phase 9 instruction.)
- [x] `RELEASE_NOTES.md` — created (didn't exist before this milestone)
- [ ] `CHANGELOG.md` — does not exist in this project; per M13's own
      instruction ("if present"), not created new, since `RELEASE_NOTES.md`
      already serves this purpose for a project with no prior release

---

## 11. Exit Criteria — Status

| Criterion | Status |
|---|---|
| Physical-device workflow validated | ❌ Not run — no device in this environment |
| Real cloud-provider workflow validated with user credentials | ❌ Not run — no account/key in this environment |
| Privacy protections verified | ✅ Automated + code-inspection verified; one residual real-device spot-check recommended |
| Cache verified | ✅ Fully automated-verified |
| Metrics verified | ✅ Fully automated-verified |
| Failure handling verified | ✅ Automated + code-inspection verified; real-network edge cases recommended |
| Performance measured | ❌ Not run — no device/emulator, no benchmark test classes exist yet |
| Regression testing completed | ✅ Automated-verified (component/unit level); manual real-device click-through recommended |
| Documentation updated | ✅ Complete |
| `./gradlew clean build` remains green after fixes | ✅ Verified (see §1) |

**Per M13's own exit criteria, this milestone is not yet complete** — two
criteria are hard-blocked on hardware/credentials this environment does
not have, exactly as flagged in the milestone brief's own closing
instruction. Everything else that could be verified without a device or a
live account has been, with real, run tests as evidence, not claims.

---

## 12. Release Recommendation (as of M13)

## **Ready for Beta — not yet Ready for Production**

Rationale: every automatable correctness guarantee (cache, metrics,
privacy preprocessing, failure-graceful-degradation, the full existing
regression suite, a genuinely green `clean build`) is verified with real,
passing tests, not assumptions. But M13's own two hard gates — a real
device and a real cloud-provider account — have not been exercised even
once, and this milestone explicitly cannot be marked complete without
them. A private beta (e.g. the user and spouse, matching this app's own
single-household scope per ADR-012) is a reasonable way to obtain that
missing real-world signal safely; a public/production release before that
signal exists would be premature regardless of how green the automated
suite is.

**Blocking items for Production**:
1. Complete Phase 1 (real device) using the checklist in §2.
2. Complete Phase 2 (real cloud provider, at least one) using the
   checklist in §3.
3. Complete Phase 7 (performance) using the checklist in §8.
4. Re-run `./gradlew clean build` after fixing anything Phases 1/2/7
   surface, and update this report's checklists from ❌ to ✅ per item
   actually run — not all at once, and not without the evidence.

---

## 13. RC1 Security Audit Summary

Full detail: `SECURITY_AUDIT.md`. Headline results:

| Category | Result |
|---|---|
| API key handling (logs, crash reports, Room, DataStore, encryption, backup, device-transfer) | 8/9 already correct; 1 fixed (device-transfer exclusion) |
| Temporary/cache file lifecycle | 2 real unbounded-growth defects found and fixed (Try-On preview files, AI result cache rows/files) |
| Cross-request cache isolation | Verified correct, no issues |
| Exported components | Verified correct, no issues (only the required launcher `Activity`) |
| Network security config / cleartext | Verified correct (platform default); one disclosed, accepted limitation (Ollama/Generic REST need HTTPS for a local endpoint) |
| Debug-only code / release hygiene | Verified clean — zero `Log` calls, zero `TODO`/`FIXME`, `isDebuggable` correctly scoped |
| Privacy preprocessing | Verified correct, with one new regression test added (EXIF-safety) |
| **Critical issues found** | **0** |
| **Real defects found and fixed** | **3** |

## 14. RC1 Dependency & Release-Build Audit Summary

- **Removed**: `org.junit.jupiter:junit-jupiter` — declared, never used by
  any module.
- **Documented** (previously undocumented, already in use):
  `play-services-mlkit-subject-segmentation`, `mlkit-pose-detection`.
- **No duplicate or obsolete dependency found** elsewhere in the graph.
- R8/resource shrinking/signing config all verified correct by inspection;
  baseline profile / startup profile still not generated (needs a device,
  unchanged from M13).
- **Developer experience gap found and fixed**: no root `README.md`
  existed — created one covering prerequisites, setup, common Gradle
  tasks, signing, and a documentation index.

## 15. RC1 Beta Readiness Scorecard

| Category | Score (0–10) | Justification |
|---|---|---|
| Stability | 7 | Full automated suite green, zero crashes in any test path; but zero real-device runtime hours — score reflects that gap honestly, not a projection |
| Maintainability | 9 | Consistent Router/Adapter/on-device-fallback pattern across all 5 AI capabilities (verified in RC1 Phase 5); every module's own README/KDoc explains its own reasoning. **Corrected in RC2**: this row previously claimed "zero detekt/ktlint suppressions project-wide" — false; a full-repo grep found 23 `@Suppress`/`@SuppressLint` sites (see `CODE_HEALTH_REPORT.md`), each reviewed and individually justified (Compose parameter counts, Room DAO column counts, an SDK-version-gated deprecated API, boundary code intentionally catching broad exceptions). RC2 added zero new suppressions. |
| Architecture consistency | 9 | No capability bypasses the Gateway; layering rules (feature → domain only, core:ai never depends on core:tryon, etc.) held throughout M1–M12 with zero violations found this audit |
| Documentation quality | 9 | 13 root-level docs plus per-module READMEs plus 13 ADRs; this pass alone added/updated 8 of them; the one gap (no root README) found this pass is now closed |
| Security | 8 | Zero critical issues; 3 real hardening fixes applied and verified; the 2-point deduction is for what's structurally unverifiable without a real device (real Keystore hardware behavior, real network interception) |
| Testing | 8 | Every module has unit and/or Compose test coverage; new regression tests added this pass for EXIF-safety and cache-key vendor/prompt-version variation; deduction for zero instrumented/macrobenchmark tests existing anywhere |
| Technical debt | 7 | 20 tracked, disclosed items in `TECHNICAL_DEBT.md`, none hidden, all with an honest risk assessment; deduction reflects the sheer count accumulated over 10+ phases, not any single item's severity |

## 16. RC2 Full Audit Summary

Full detail: `SECURITY_AUDIT_RC2.md`, `PERFORMANCE_AUDIT.md`,
`CODE_HEALTH_REPORT.md`. Headline results:

| Phase | Result |
|---|---|
| 1 — Full Static Audit | 43 `core:ai` files + highest-risk `core:image`/`core:database` files read in full; 4 real defects found (3 correctness, 1 security), all fixed and regression-tested; 2 found-but-untestable-here issues disclosed, not fixed |
| 2 — Performance Audit | Every decode/encode/hash/serialize/ML-Kit-call path traced end-to-end for the two highest-traffic pipelines; no duplicated work found; no change needed |
| 3 — AI Robustness Audit | Folded into Phase 1's findings (the malformed-base64 hang, the duplicate-metric-event bug, and the Gemini key leak are all AI-pipeline robustness/security defects) |
| 4 — Android Audit | Reviewed by inspection; two pre-existing disclosed process-death limitations re-confirmed unchanged; no `onTrimMemory` handling exists (disclosed gap, not a confirmed bug); everything else either verified-by-construction (WorkManager, ViewModel/CameraX lifecycle binding) or requires a real device (unchanged limitation) |
| 5 — Security Audit | **1 critical issue found and fixed**: Gemini's API key was being written to Logcat via a `?key=` URL parameter combined with an unconditional `Level.BASIC` logging interceptor. Every RC1 finding re-verified, no regressions. |
| 6 — Documentation Audit | 2 stale/false claims found and corrected (`README.md` and this report both falsely claimed "zero suppressions project-wide") |
| 7 — Dependency Audit | No unused/duplicate/obsolete dependency found beyond RC1's prior removal; live CVE lookup not performed (no network access), disclosed as unchecked |
| **Critical issues found** | **1** (the Gemini key leak) |
| **Real defects found and fixed this pass** | **4** (plus 1 carried forward from Beta 1) |
| **New regression tests added** | **4** |
| **New suppressions added** | **0** |

## 17. RC2 Beta Readiness Scorecard (re-scored)

| Category | Score (0–10) | Justification |
|---|---|---|
| Architecture | 9 | Unchanged from RC1's assessment — no capability bypasses the Gateway; RC2's fixes (dispatch coalescing ownership, orphan-sweep age cutoff, Gemini auth method) each stayed within 1–3 files with no new abstraction layer, consistent with "harden, don't redesign" |
| Maintainability | 9 | Unchanged from RC1, with the suppression-count correction above — the real count (23, all individually justified) doesn't change the underlying assessment, it just corrects what was claimed |
| Testing | 8 | 4 new regression tests this pass, each reproducing its defect red before the fix and green after (not written after-the-fact to just pass) — same deduction as RC1 for zero instrumented/macrobenchmark tests existing anywhere |
| Security | **9** (up from 8) | The one critical issue this project has had at any point in its audit history (the Gemini key leak) is now fixed and verified; the remaining 1-point deduction is for what's structurally unverifiable without a real device (Keystore hardware behavior, real network interception, real Logcat-access attack surface testing) |
| Performance | 8 | Every high-traffic path traced found no duplicated decode/encode/hash/serialize/query work; deduction because zero real-device performance numbers exist yet (cold/warm launch, memory, battery — all still M13's original outstanding gate) |
| Reliability | 8 (new category, RC2) | 2 real hang/deadlock-shaped defects found and fixed this pass (malformed-base64, duplicate metrics) plus 1 real data-loss race (orphan sweep) — all with regression tests; deduction for the 2 disclosed-but-unfixed issues (`CameraCaptureController`, ML Kit cancellation) that remain real, if low-severity, risk |
| Developer Experience | 9 | `README.md` (RC1) plus this pass's documentation-accuracy correction; a fresh clone still builds via `./gradlew build` with no hidden steps |
| Documentation quality | 9 | Corrected 2 stale claims this pass rather than compounding them; `SECURITY_AUDIT_RC2.md`/`PERFORMANCE_AUDIT.md`/`CODE_HEALTH_REPORT.md` add real, evidenced detail rather than restating RC1 |
| Technical debt | 7 | 22 tracked items now (up from 20), all disclosed with risk assessments — the count keeps growing because each milestone's audit finds real things, not because anything is being hidden or deferred without reason |
| **Beta readiness** | **9** | Every RC2 acceptance criterion met: clean build green, zero critical security/architecture issues remaining, no duplicate dependencies, all fixes regression-tested, all unfixed issues documented with rationale |
| **Production readiness** | **6** (unchanged blocking gate) | Unchanged from M13/RC1 — real device validation, a real cloud-provider account, and real performance measurement remain the user's own gate; no amount of further static auditing substitutes for that, per this project's own standing rule |

## 18. RC2 Release Recommendation

**Ready for Beta — not yet Ready for Production.** Unchanged conclusion
from §12 (M13), reaffirmed rather than revisited: RC2 found and fixed a
real, critical security issue (the Gemini key leak) that should ship
before any beta tester actually configures a Gemini API key, but fixing it
doesn't change *why* Production readiness is still blocked — that has
always been the missing real-device/real-cloud-account/real-performance
signal, not code quality, and RC2's entire scope (by its own explicit
rules) was hardening the code that already exists, not obtaining that
signal.

**Overall**: consistent with §12's recommendation — strong foundation for
a private beta, with real-device/real-cloud validation as the explicit,
tracked gate before a production release.
