# RC2 Security Audit

Phase 5 of RC2 (Production Hardening). This is a delta audit against
`SECURITY_AUDIT.md` (RC1's full audit) — it re-verifies every RC1 finding
still holds (nothing regressed since), and specifically hunts for defects
RC1's own pass didn't cover. One real, evidenced, **high-severity** defect
was found and fixed. Every claim below cites the file(s) actually read.

## Headline finding: Gemini's API key was being written to Logcat

**Severity: High.** **Status: Fixed, verified by test.**

### What was found

- `GeminiAdapter.run()` (`core/ai/.../gateway/adapter/GeminiAdapter.kt`)
  built its request URL as
  `{baseUrl}/v1beta/models/{model}:generateContent?key={apiKey}` — the
  user's real Gemini API key embedded directly in the URL, per Gemini's
  own documented (valid) query-parameter auth method.
- `AiNetworkModule.provideAiOkHttpClient()` (`core/ai/.../di/AiNetworkModule.kt:50`)
  attaches an `HttpLoggingInterceptor` at `Level.BASIC` to the **same**
  shared `OkHttpClient` every vendor adapter (including Gemini) uses. This
  interceptor is unconditionally active in every build variant — it is not
  gated behind `BuildConfig.DEBUG` or any release check.
- `HttpLoggingInterceptor.Level.BASIC` logs the full request line —
  method, **complete URL**, response code, response time. It does not log
  headers or the request/response body.
- Combined: every real Gemini cloud call in this app — debug or release —
  wrote the user's live Gemini API key, in plaintext, to the device's
  system log (Logcat), retrievable via `adb logcat`, bug-report tooling,
  or any crash-reporting SDK that captures a recent-log tail.
- This directly contradicts this project's own repeatedly-stated,
  audited security principle ("API keys never in logs" — `SECURITY_AUDIT.md`
  §1, restated verbatim in this milestone's own Phase 5 checklist) and had
  gone undetected through M12 (Gemini's adapter was added), M13, and RC1's
  own security audit, because RC1's audit checked "does the app ever call
  `Log.d`/`println` with a key" (it doesn't — grep-verified, zero hits) but
  did not check what a *third-party logging interceptor* does with a URL
  the app itself constructed.

### Why every other vendor was unaffected

Every other `VisionPromptAdapter` (OpenAI, Azure OpenAI, Claude, OpenRouter,
Ollama) sends its key via an HTTP **header** (`Authorization: Bearer …`,
`x-api-key`, `api-key`) — `Level.BASIC` never logs headers, so none of
those leak. `GenericRestAdapter` (the self-hosted `ImageTaskAdapter`) also
uses a header. Gemini was the sole adapter putting a secret in the URL.

### Fix

`GeminiService.generateContent` gained an `x-goog-api-key` header
parameter (Gemini's own documented alternative auth method — equally
valid, per Google's real API); `GeminiAdapter` now sends the key that way
and the URL no longer contains it at all. This is a root-cause fix (the
secret is no longer in the URL, so `Level.BASIC` logging it is no longer
dangerous), not a change to the shared logging interceptor.

### Verified

`GeminiAdapterTest` (`core/ai/src/test/.../adapter/GeminiAdapterTest.kt`)
was rewritten to assert the key **never** appears in the request path and
**does** appear as the `x-goog-api-key` header — run against a real
`MockWebServer`, not a mock of the Retrofit interface, so the assertion is
against the actual bytes sent over the wire. `:core:ai:testDebugUnitTest`
green.

### Considered and deliberately not done

Gating `HttpLoggingInterceptor`'s level to `NONE` outside debug builds
(defense-in-depth, protecting against a *future* adapter making the same
mistake) was considered. It would require enabling `buildFeatures.buildConfig`
for `core:ai` (and `core:data`, which shares the same interceptor pattern
for the weather client) — a build-configuration change beyond this
confirmed defect's actual fix, which RC2's "no redesign" rule argues
against doing speculatively. **Logged in `TECHNICAL_DEBT.md` as an
optional future hardening item**, not applied now.

## RC1 findings re-verified (no regression)

Each of RC1's `SECURITY_AUDIT.md` sections was re-checked against the
current code (not assumed unchanged):

| RC1 finding | Re-verified this pass |
|---|---|
| API keys never in logs (`Log`/`println` grep) | Re-ran the grep across `core/ai`, `core/data`, `core/image` production code — zero hits, unchanged. The Gemini finding above is a *different* leak path (URL, not a direct log call) that grep alone can't catch — now closed too. |
| `EncryptedApiKeyStore` device-transfer exclusion | `data_extraction_rules.xml` still excludes `ai_api_keys.xml`; unchanged since RC1. |
| Temp file / cache cleanup (`OrphanedImageCleanupWorker`) | Still sweeps `tryon_preview_*` and `ai_result_cache`; RC2 *hardened* this worker further (see `CODE_HEALTH_REPORT.md` — the orphan-sweep race fix), no regression. |
| Cross-request cache isolation | Cache keys still include `imageSha256:capability:provider:model:promptVersion` — unchanged, still collision-safe. |
| Exported components | No new `Activity`/`Service`/`Receiver` added since RC1; still only the launcher `Activity`. |
| Network security config / cleartext | Unchanged; Ollama/Generic-REST-needs-HTTPS remains a disclosed, accepted limitation (§ below). |
| Debug-only code / release hygiene | Re-grepped for `TODO`/`FIXME`/`Log.` — zero hits in production code, unchanged. |
| Privacy preprocessing (EXIF strip, face blur) | `PrivacyPreprocessor`/`FaceBlurrer` unchanged since RC1; still verified by `BitmapEncodingTest`'s EXIF-absence regression test. |

## New checks this pass (RC2-specific, not in RC1's checklist)

- **Provider URL validation**: no adapter validates that a user-supplied
  Base URL uses `https://` before sending real payloads/keys to it. This
  is the same disclosed, accepted gap RC1 already logged (Ollama/local
  self-hosted setups may legitimately need `http://` for a LAN endpoint) —
  re-confirmed still accurate, not a new finding.
- **TLS assumptions**: no adapter pins certificates or overrides OkHttp's
  default TLS trust — this is the correct, standard behavior (trust the
  platform's CA store), not a gap.
- **PII exposure via crash reports**: this app has no crash-reporting SDK
  integrated at all (confirmed via `libs.versions.toml`/dependency graph
  review, Phase 7) — there is no third-party service that could capture
  and transmit a stack trace or log tail off-device. This also means the
  Gemini key leak above was confined to the *local device's* Logcat, not
  actively exfiltrated anywhere — still a real defect (local Logcat access
  is a real attack surface: `adb`, other on-device tooling, manual bug
  reports), just not a network-exfiltration one.

## Critical issues found this pass

**1** — the Gemini API key leak above. Fixed, verified, zero suppressions
added.

## Exit status

Per RC2's acceptance criteria ("no critical security issues" as a release
gate): the one critical issue found is fixed, not merely documented. No
other critical or high-severity issue was found in this pass's scope.
