# Security Audit — RC1

**Date**: 2026-08-06
**Scope**: the full application, with particular focus on the AI Gateway
architecture (M1–M12) and its handling of secrets and user photos.
**Method**: source-code inspection plus automated tests (existing and, in
three cases, added during this audit specifically to give a claim below
real, re-runnable evidence rather than a one-time visual check). No
physical device or real cloud account was used — see
`PRODUCTION_VALIDATION_REPORT.md` for the items that still need one.

Each item is marked **PASS** (verified, no issue), **FIXED** (a real issue
was found and corrected during this audit), or **DISCLOSED** (a real,
accepted limitation, not fixed — see the reasoning given).

---

## 1. API key handling

| Check | Result |
|---|---|
| API keys never appear in logs | **PASS** — zero `android.util.Log` calls exist anywhere in this codebase's main source; the only logging is OkHttp's `HttpLoggingInterceptor`, configured at `Level.BASIC` on both network clients (`core:ai`'s `AiNetworkModule`, `core:data`'s `NetworkModule`) — that level logs only the request/response line, never headers (where the API key travels as `Authorization: Bearer <key>`) or the body |
| API keys never appear in crash reports | **PASS** — no crash-reporting SDK (Crashlytics, Sentry, Bugsnag, etc.) is present anywhere in the dependency graph; there is nothing to send a crash report to in the first place |
| API keys never enter Room | **PASS** — no entity in `core:database` has an API-key-shaped field; grepped for `apiKey`/`api_key` across every entity, zero matches |
| API keys never enter DataStore | **PASS** — `AiProviderPreferencesDataStore` stores only the non-secret half of a provider's config (mode, vendor, base URL, model, cost rate, consent state); its own KDoc and `PreferenceKeys.kt` both explicitly document "the key itself is never here" |
| API keys always read from `EncryptedApiKeyStore` | **PASS** — every one of the five capability Routers (`GarmentExtractionEngineRouter`, `GarmentReconstructionEngineRouter`, `GarmentMetadataEngineRouter`, `StylingEngineRouter`, `TryOnRouter`) and `AiProviderSettingsRepositoryImpl` read exclusively through the `ApiKeyStore` interface, whose only real implementation is `EncryptedApiKeyStore` |
| Encryption backing | **PASS** — `EncryptedApiKeyStore` uses `androidx.security.crypto`'s `EncryptedSharedPreferences`, keyed by a `MasterKey` generated inside `AndroidKeyStore` (hardware-backed where available) — AES256-SIV for preference keys, AES256-GCM for values |
| Excluded from cloud backup | **PASS** — both `backup_rules.xml` (legacy, API 23–30) and `data_extraction_rules.xml`'s `<cloud-backup>` section exclude `sharedpref` entirely, which covers `EncryptedApiKeyStore`'s backing file |
| Excluded from device-transfer | **FIXED** — `data_extraction_rules.xml`'s `<device-transfer>` section had no excludes at all before this audit. Because `EncryptedApiKeyStore`'s `MasterKey` lives inside the *source* device's Android Keystore (non-exportable by design), transferring the raw encrypted file to a new device leaves ciphertext with no matching key — a real, documented `EncryptedSharedPreferences` failure mode that can throw on first read rather than degrade gracefully. Now excluded (`<exclude domain="sharedpref" path="ai_api_keys.xml" />`); every other app file remains transferable, unchanged |

## 2. Temporary/cache file lifecycle

| Check | Result |
|---|---|
| Import-staging directories cleaned up | **PASS** (pre-existing) — `OrphanedImageCleanupWorker` sweeps staging directories older than 24h with no commit/discard |
| Orphaned permanent image files cleaned up | **PASS** (pre-existing) — the same worker sweeps files on disk with no matching `image_metadata` row |
| Try-On comparison-preview scratch files cleaned up | **FIXED** — `VirtualTryOnRenderRepositoryImpl` (M12) writes one `tryon_preview_*.webp` per render into `cacheDir`; nothing deleted them before this audit. `OrphanedImageCleanupWorker` now sweeps these too, same 24h-plus cadence |
| AI result cache (Gateway) rows/files bounded | **FIXED** — `AiResultCacheDao.deleteByCacheKey` existed but nothing in production ever called it; the multi-stage cache table (and its matching `AiResultImageCacheStore` files) grew without bound for the life of the install. `OrphanedImageCleanupWorker` now also deletes cache rows/files older than 30 days (long enough to preserve the "why did the app suggest this"/regenerate-with-improved-prompt provenance value ADR-012 designed this table for; short enough to bound growth) |
| Cleanup actually runs | **PASS** — `OrphanedImageCleanupWorker.schedule()` is called from `WardrobeApplication.onCreate()`, `NetworkType.NOT_REQUIRED`, daily — confirmed via `grep`, not assumed |

## 3. Cross-request/cross-session isolation ("cannot leak between users")

This is a single-account, single-device (or local-network-synced,
device-to-device) product — there is no server-side multi-tenant concept
to leak across. The check that actually applies: can one cached AI result
ever be served for the wrong input?

| Check | Result |
|---|---|
| Cache key is content-addressed | **PASS** — `sha256(image):capability:provider:model:promptVersion`; a different image, capability, vendor, model, or prompt version always produces a different key (verified by automated test, see `DefaultAiGatewayTest`) |
| Multi-image requests (Try-On) can't collide on the first image alone | **PASS** (fixed in M12, re-verified here) — the cache key combines every payload's own hash, not just the first |
| Cached image files are named by their full cache-key hash | **PASS** — `AiResultImageCacheStore.save()` writes to `$cacheKeyHash.webp`; no shared/predictable file name exists for two different results to collide on |
| Android multi-user-profile isolation | **PASS** — `context.cacheDir`/`filesDir`/Room DB are all OS-scoped per Android user profile automatically; nothing in this app writes to shared/external storage that would cross that boundary |

## 4. Exported components

| Component | `exported` | Justification |
|---|---|---|
| `MainActivity` | `true` | Required — it's the launcher activity (`MAIN`/`LAUNCHER` intent filter); Android mandates this be exported |
| `androidx.startup.InitializationProvider` | `false` | Explicitly set, standard for this provider |
| Everything else | — | No other `<activity>`, `<service>`, `<receiver>`, or `<provider>` exists in the manifest at all |

**PASS** — nothing is unintentionally exposed; the only exported component
is the one Android requires to be exported.

## 5. Network Security Config / cleartext traffic

| Check | Result |
|---|---|
| `network_security_config.xml` | Does not exist — and doesn't need to. `targetSdk = 36` already makes Android's platform default "cleartext traffic blocked" active with no configuration required |
| `android:usesCleartextTraffic` | Not set anywhere (defaults to `false` at this target SDK) |
| Release build blocks cleartext unless explicitly allowed | **PASS** — confirmed by the absence of any override, not merely assumed |

**DISCLOSED, not fixed**: two supported cloud vendors — **Ollama** and
**Generic REST** — are exactly the kind of provider a user might
realistically self-host on a local network without TLS (e.g.
`http://192.168.1.50:11434`). With no network security config exception,
a plaintext local endpoint for either of these two vendors will not
connect. This is the platform's secure default working as intended, not a
defect to silently patch around with an IP-range cleartext exception
(which would itself be a new, unrequested security-policy decision, out
of RC1's "no new features" scope) — recorded in `KNOWN_LIMITATIONS.md`
and left for the user to decide on for a future release if it matters in
practice.

## 6. Debug-only code / release hygiene

| Check | Result |
|---|---|
| `isDebuggable` only in the `debug` build type | **PASS** — the `release` block never sets it, defaulting to `false` |
| `debugImplementation`-scoped tooling stays out of release | **PASS** — Compose UI tooling/test-manifest deps are correctly `debugImplementation`-scoped in `app/build.gradle.kts` |
| No `TODO`/`FIXME`/`XXX`/`HACK` markers in shipped code | **PASS** — zero matches across every module's main source |
| No stray `println`/debug logging | **PASS** — zero matches |

## 7. Privacy preprocessing (cloud AI payloads)

| Check | Result |
|---|---|
| EXIF stripped from every cloud payload | **PASS**, with a new regression test added this audit (`BitmapEncodingTest`, M13) that scans the actual encoded bytes every vendor adapter sends for a WebP EXIF chunk or JPEG EXIF marker and asserts neither is present — not just an architectural argument |
| Faces blurred before the one capability whose cloud call can see one | **PASS** — `DefaultPrivacyPreprocessorTest` proves `prepareExtractionPayload` always invokes `FaceBlurrer`; every other capability's cloud call receives only the already-extracted, already-faceless cutout |
| Consent required before first cloud use | **PASS** — `AiProvidersViewModel`'s mode-switch flow always surfaces the consent dialog; declining reverts to on-device |
| Changing Base URL invalidates prior consent | **PASS** — `onBaseUrlChanged` clears `consentGrantedAt`/`consentHost` unless the new URL matches the already-consented host |

---

## Summary

| Category | Result |
|---|---|
| Critical issues found | **0** |
| Real defects found and fixed | **3** (Try-On preview file leak, AI cache unbounded growth, device-transfer API-key crash risk) |
| Disclosed, accepted limitations | **1** (Ollama/Generic REST cleartext local endpoints) |
| Items requiring real-device/real-account validation | See `PRODUCTION_VALIDATION_REPORT.md` |

No critical security issue remains open. The three fixes above are
release-quality hardening of genuine gaps, not speculative additions —
each is documented with the exact evidence that surfaced it and the exact
change that resolved it, and each is covered by `./gradlew clean build`
remaining green afterward.
