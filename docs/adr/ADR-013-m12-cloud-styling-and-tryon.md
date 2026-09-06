# ADR-013: Cloud Outfit Styling and Cloud Virtual Try-On (M12)

**Status**: Accepted (implementation milestone, `alta-class-closet-app-master-prompt.md`,
added 2026-08-05, extending ADR-012)

## Context

ADR-012 established the vendor-neutral AI Gateway/Adapter/Router
architecture and implemented it for three capabilities (Garment
Extraction, Reconstruction, Metadata). M12's brief was explicit: complete
the remaining two capabilities — Cloud Outfit Styling and Cloud Virtual
Try-On — **reusing that architecture exactly as built**, with "no new
networking architecture, no duplicate providers, no capability-specific
HTTP clients, and no bypassing of the Gateway." This ADR records the
handful of places where satisfying that brief required a small, deliberate
extension rather than a pure reuse, and why each one was judged to still
honor the "extend, don't rewrite" instruction rather than violate it.

## Decision

### 1. `AiGateway`/`ImageTaskAdapter`/`VisionPromptAdapter` needed zero changes

Both capabilities dispatch through the exact same two entry points
Extraction/Reconstruction/Metadata already use — `runVisionPrompt` for
Styling (reason over wardrobe/weather/occasion context, return JSON),
`runImageTask` for Try-On (image(s) in, image out). `GenericRestAdapter`
already sent every element of `request.images` as an indexed multipart
part, and `ImageTaskAdapter`'s own KDoc already named "Extraction/
Reconstruction/Try-On" as its intended scope — this milestone is the
first to actually exercise the multi-image case that shape was built for.

### 2. `DefaultAiGateway.runImageTask`'s cache key now hashes every image, not just the first

**This is a bug fix, not new architecture.** The prior cache key used
`payloads.firstOrNull()?.let(::sha256)` — correct by coincidence for
Extraction/Reconstruction/Metadata, which only ever call with one image,
but silently wrong for Try-On's body+garment(+mask) request: two
different garments tried on the same body photo would have collided on
the same cache entry. `combinedImageHash` now digests every payload's own
hash together when more than one image is present, and is provably
identical to the old behavior for the single-image case (verified by
`DefaultAiGatewayTest`). No cache-key *shape*, dispatch order, or
capability-facing API changed.

### 3. Styling's cache key is achieved by a synthesized context bitmap, not a Gateway change

M12's spec calls for a `(wardrobeHash, weatherHash, occasion, provider,
model, promptVersion)` cache key for Styling. The Gateway has no hook for
a capability to contribute non-image cache-key material, and adding one
would touch cache behavior for every capability, not just this one.
Instead, `stylingContextFingerprintBitmap` (`core:data`'s
`StylingCloudContext.kt`) encodes exactly those fields into a small
deterministic bitmap sent as the vision-prompt "image" whenever the user
hasn't attached a real inspiration photo. Identical wardrobe/weather/
occasion state always hashes to the identical bitmap, so the Gateway's
existing, completely unmodified cache lookup already behaves like the
requested composite key. When a real inspiration image is attached
instead, the cache keys on that image's contents, which is the more
natural behavior for that case and a disclosed, minor deviation from the
literal spec (`TECHNICAL_DEBT.md` item 18).

### 4. `VirtualTryOnEngine.render` gained a `mask: Bitmap? = null` parameter; `TryOnRenderResult.Success` gained a `source: AiResultSource`

Both are additive, backward-compatible extensions of an interface that
had zero real implementations or call sites before this milestone (it was
scaffolded, unused, in an earlier pass). Adding them now — rather than
inventing a parallel interface — was judged the correct "extend, don't
duplicate" reading of the instruction, since there was nothing yet to
break.

### 5. `OnDeviceVirtualTryOnEngine` is new code, wrapping — never modifying — the existing Phase 10 pipeline

`BodyAnchorEstimator`, `DefaultPlacementCalculator`, `LightingMatcher`,
and `ShadowRenderer` are called exactly as they were, unchanged. This new
class exists because none of Phase 10's own code flattens a render into a
single static `Bitmap` the way `VirtualTryOnEngine`'s stateless contract
requires — `TryOnRenderCache` is the nearest existing analog, but it
operates on saved file paths and a real `GarmentPlacementTemplate`/
`OutfitSlot`, not raw in-memory bitmaps with no slot information. The new
wrapper mirrors `TryOnRenderCache`'s own Canvas/Matrix compositing
technique and reuses its `TRY_ON_LAYER_WIDTH_FRACTION` constant for
consistent sizing, and is the first real consumer of `LightingAdjustment`
applying it to a bitmap — that model's own KDoc always described this as
its purpose, but nothing exercised it before now. Disclosed simplification:
this generic wrapper has no slot parameter to anchor by, so it always
anchors at `TryOnAnchorRegion.SHOULDER_LINE` (`TECHNICAL_DEBT.md` item 18)
— the live, interactive `feature:tryon` screen is unaffected and continues
to anchor by each garment's real slot.

### 6. A new `core:domain` interface, `VirtualTryOnRenderRepository`

`feature:tryon` may only depend on `core:domain` repository interfaces,
never on `core:ai` directly (this project's standing layering rule) — and
`core:domain` is pure Kotlin/JVM, so it cannot reference `android.graphics.
Bitmap`. `VirtualTryOnRenderRepository.render` takes/returns file paths and
a new `core:model` type, `VirtualTryOnRenderOutcome`, mirroring the
`AiConnectionTestResult`/`AiUsageSummary` precedent `AiProviderSettingsRepository`
already established for domain-safe AI result types. Its implementation
decodes the paths, dispatches through `TryOnRouter` (or, when the Try-On
Review comparison viewer's `forceOnDevice` flag is set, directly through
`OnDeviceVirtualTryOnEngine`), and writes a successful render to a scratch
cache file — never over the source body photo or garment cutout.

### 7. Four pre-existing Hilt wiring defects, found and fixed, not introduced

Running `./gradlew clean build` for the first time against `:app` (rather
than per-module tests) surfaced duplicate `WorkManager`/`OkHttpClient`/
`Retrofit`/`Json` bindings between `core:ai`'s own network module and
`core:data`'s, and three missing `@Binds` for already-`@Inject`-constructed
classes (`PersonRegionMasker`, `PrivacyPreprocessor`, `FaceBlurrer`). These
predate M12 (introduced during the earlier `core:ai` build-out) but were
only caught during this milestone's own verification pass. Fixed via a new
`@AiHttp` qualifier annotation (the two `OkHttpClient`/`Retrofit`/`Json`
pairs are genuinely different instances — different timeouts, different
purpose — not accidental duplicates) and the three missing `@Binds` lines,
following the existing per-capability module binding pattern exactly.

## Consequences

- Every capability now shares one Gateway, one cache mechanism, one metrics
  stream, and one privacy-preprocessing gate — adding capability #6 later
  means one more Router plus, if genuinely needed, one more small
  cache-key-shaping helper in the style of `StylingCloudContext.kt`, never
  a second Gateway or a capability-specific HTTP client.
- The three "wrap, don't rewrite" wrappers (`OnDeviceVirtualTryOnEngine`,
  `CloudStylingEngine`'s validation dependency on `OutfitAssembler`,
  `VirtualTryOnRenderRepositoryImpl`) each depend on the exact existing
  building blocks named in the M12 brief, verified by grep and by reading
  each file before writing a single line against it.
- All disclosed simplifications and the one genuine cache-key bug fix are
  recorded in `TECHNICAL_DEBT.md` item 18, not silently absorbed.
