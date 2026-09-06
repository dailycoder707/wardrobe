# ADR-023: Cloud AI Full Garment Metadata Auto-Fill (M24)

**Status**: Accepted (implementation milestone, added 2026-08-08); real-device
verification (Phase 12) pending physical hardware access — see "Real-device
verification" below.

## Context

M23 proved the real, honest limitation: the on-device metadata engine
genuinely only produces Color/Pattern/Brand, and closing that gap requires
either a new on-device classifier (deliberately not fabricated) or Cloud
AI — which the codebase already claims to support for all 19
`MetadataField` values via `MetadataPromptSupport`. M24's brief demanded a
full, skeptical audit of that claim before trusting it, then closing
whatever real gap the audit found — never declaring success from a green
Gradle run alone.

## Phase 1 audit finding: the cloud pipeline was already substantially real

A full trace (photo → `GarmentMetadataEngineRouter` → six real vendor
adapters (OpenAI, Azure OpenAI, Gemini, Claude, OpenRouter, Ollama) →
`MetadataPromptSupport`'s JSON parsing → `MetadataSuggestionResolver`/
`MetadataSuggestionApply` → UI state → Compose fields) found, with file:line
evidence, that dispatch, provider requests, JSON parsing, **genuine
per-field confidence** (not a global-average fabrication), consent-scoped
gating, cache provenance, and fail-soft/no-crash error handling were **all
already correctly implemented and already tested** — this was not the
resolver/UI transparency gap M23 found; the cloud path itself works. Three
real, narrower gaps were confirmed and are what this milestone fixes:

1. **No provider-native structured-output enforcement** — every adapter
   relied purely on the system prompt's own "respond with ONLY a JSON
   object" instruction; no adapter passed a vendor's actual JSON-mode
   parameter, even though OpenAI-compatible and Gemini both support one.
2. **Reference-data matching was exact-string-only** (case-insensitive, but
   nothing else) — a cloud model's free-text formatting differences
   (`"T-Shirt"` vs `"T Shirt"` vs `"TShirt"`, incidental whitespace) could
   cause a genuinely-correct value to fail to resolve for no real reason.
3. **A cloud dispatch failure's reason was silently discarded** at the
   router — the fallback to on-device was never mislabeled as cloud
   success (verified), but nothing captured *why* cloud didn't run, which
   is exactly what real-device debugging (M24 Phase 12) needs.

## What changed

### 1. Provider-native structured JSON output (Phase 2)

`VisionPromptAdapterRequest`/`AiGateway.runVisionPrompt` gained
`expectJsonResponse: Boolean = false` — set `true` only by callers whose
prompt asks for JSON (`GarmentMetadataEngineRouter`; the connection-test
ping, which expects the literal word "OK", correctly leaves it `false` by
never passing it). Adapters use it where a real, documented mode exists:

- **OpenAI-compatible** (OpenAI, Azure OpenAI, OpenRouter, Ollama's
  compatibility endpoint — all four share `runChatCompletions`):
  `response_format: {"type": "json_object"}`.
- **Gemini**: `generationConfig.responseMimeType: "application/json"`.
- **Claude**: no native JSON-schema/response-format mode exists in
  Anthropic's Messages API as this app calls it (no tool-forcing is used
  here) — stays prompt-only, an honest, disclosed limitation, not a gap
  this milestone can close without fabricating a capability.

A backend that rejects the new field (an incompatible self-hosted model
behind Ollama/OpenRouter) fails the HTTP call, which the existing
`catch (HttpException)` already turns into a `Failure` → on-device
fallback — the same tested degradation path, never a crash.

### 2. Deterministic reference-data normalization (Phase 3)

`MetadataSuggestionResolver.nameMatches` now strips whitespace and hyphens
entirely (not just case) before comparing — `"T-Shirt"`, `"T Shirt"`, and
`"TShirt"` all normalize identically. This is **formatting-noise
collapsing, never a semantic/fuzzy guess**: `"Navy Blue"` still never
matches `"Navy"` (a real test proves this explicitly). No alias table was
added — a genuine alias dictionary (e.g. "Denim" implying a Fabric vs. a
Category) would risk exactly the "hardcoded common garment guess" M24
explicitly forbade.

### 3. Extended debug diagnostics (Phase 10)

M23 already logs a per-field funnel (requested/returned/confidence/
resolved) from `GarmentReviewMetadataViewModel` (feature:capture) under
the `MetadataPipeline` logcat tag. `GarmentMetadataEngineRouter` (core:data)
now logs the request-level half under the same tag, gated the same way
(`ApplicationInfo.FLAG_DEBUGGABLE`, matching M22/M23's established
pattern): provider, model, capability, cache hit/miss, the full requested-
field list, the returned-field list on success, or the failure reason and
fallback notice on failure. Together the two log sources answer every item
M24's Phase 10 asked for, split at the natural architectural boundary
(dispatch-level facts vs. per-field resolution facts) rather than forcing
one class to own both. Never logs the API key, an auth header, the image,
or the raw response text — only field names, non-secret identifiers, and
the same values already shown on the review screen.

### 4. Regression and gap-closing tests

- `MetadataSuggestionResolverTest`: per-field mixed-confidence gating (a
  HIGH Category, MEDIUM Material, and no-confidence Brand in one response
  each resolve independently — never governed by an average); case/hyphen/
  whitespace normalization (both a positive match and a "still doesn't
  fuzzy-match a genuinely different value" negative case); a realistic
  ~10-field cloud response for a clear garment auto-populates every
  resolvable field simultaneously in one `autoFillForm` call — the closest
  an automated test can get to M24's real acceptance criterion.
- `GarmentMetadataEngineRouterTest`: explicit tests that cloud is never
  dispatched without granted consent, and never dispatched without an API
  key (previously only the combined "not cloud-ready" case was covered);
  `expectJsonResponse = true` is actually passed on every cloud dispatch.
- `DefaultAiGatewayTest`: `expectJsonResponse` threads from `runVisionPrompt`
  through to the adapter request (both `true` and the `false` default); a
  real network failure (`IOException` thrown from the adapter, not a
  well-formed `Failure` result) becomes an honest `VisionPromptResult.Failure`
  with a `TIMEOUT`-outcome metric, never an uncaught exception.
- `OpenAiAdapterTest`/`GeminiAdapterTest`: the actual HTTP request body
  contains `response_format`/`generationConfig.responseMimeType` only when
  `expectJsonResponse` is `true`, absent (not just falsy) otherwise.

## Deliberately deferred (disclosed, not silently dropped)

- **A user-facing (non-debug) "Cloud AI failed, using on-device this time"
  message.** The audit confirmed the current fallback is never dishonest
  (it never claims cloud succeeded), just less informative than ideal.
  Surfacing the failure *reason* to the end user would require changing
  `GarmentMetadataEngine`'s return shape across the on-device engine, the
  router, the image pipeline, `StagedImage`/`AiProcessingSummary`, and the
  review UI — a real, legitimate improvement, but disproportionate to add
  in this pass versus the debug-diagnostics fix that already answers the
  same question for the person who can actually act on it (a developer
  debugging on a real device, per Phase 12's own framing).
- **`CloudStylingEngine`** (`OUTFIT_STYLING`, also a JSON-expecting
  `runVisionPrompt` caller) was not given `expectJsonResponse = true` in
  this pass — it would benefit identically, but M24's scope is Garment
  Metadata specifically; changing Styling's dispatch behavior without a
  Styling-focused audit of its own prompt/parser risks an undisclosed
  side effect. Flagged here as a real, near-identical follow-up.
- **Claude structured output** — no fix exists without adopting
  tool-forcing (a materially different request shape); left prompt-only,
  matching the honest-limitation framing M24 itself anticipated ("if it
  does not [support schema], implement robust parsing" — already true).

## Real-device verification (Phase 12) — performed; found and fixed three more real bugs

M24 is explicit: a green Gradle run is not sufficient, and this milestone
must not be declared complete without an actual physical-device test.
Once a physical tablet was connected (`adb devices` confirmed it; the
Android SDK's `platform-tools/adb.exe` was used directly to install the
debug APK and stream/dump `logcat`), that testing found three real,
significant bugs that every automated test in this milestone had missed —
proving the milestone's own premise that Gradle-green is not sufficient.

1. **Settings never guided the user to a working cloud config.** The Base
   URL field had no default and nothing validated it before consent could
   be granted — a user could pick a vendor, tap through consent, and have
   cloud AI silently stay permanently unreachable (`isCloudReady()`
   correctly required a non-blank `baseUrl`, but nothing told the user
   why it was blank). Confirmed live: pulling the app's DataStore file
   directly off the device (`adb shell run-as ... cat files/datastore/...`)
   showed `mode=CLOUD`, `vendor=GEMINI`, but no `base_url` at all. Fixed:
   `AiVendor.defaultBaseUrl()` (`core:model`) pre-fills the field for
   vendors with one real fixed public endpoint (OpenAI, Gemini, Claude,
   OpenRouter — never for Azure OpenAI/Ollama/Generic REST, which have no
   safe universal default); the consent button is now disabled with an
   explanatory label until Vendor + Base URL are both actually present.

2. **Gemini's documented header-based auth (`x-goog-api-key`) — the exact
   RC2 security choice — was rejected by Google's live API with a plain
   `404` on `:generateContent`** for the real key/project tested, while
   the identical request authenticated via the `?key=` query parameter
   was accepted (confirmed independently of this app with a raw HTTP
   call). Fixed with `GeminiQueryParamAuthInterceptor`, which moves the
   key from the header into the query parameter — but only for requests
   that actually carry that header, so every other vendor is unaffected.

3. **The first attempt at fixing #2 leaked the API key into debug
   Logcat.** The interceptor was initially registered via
   `OkHttpClient.Builder.addInterceptor` (an *application* interceptor),
   ordered after `HttpLoggingInterceptor` on the theory that
   `HttpLoggingInterceptor` would have already captured its log lines from
   the pre-rewrite request. That theory was wrong in practice: on the real
   device, the key appeared in cleartext in `HttpLoggingInterceptor`'s own
   response-side log line. **The user's real API key was exposed as a
   result** — disclosed to them immediately, with instructions to rotate
   it at Google AI Studio, and the exposed value was scrubbed from local
   log files. Root cause: `HttpLoggingInterceptor`'s response-side log
   line reflects the request that was actually sent (post-rewrite) when
   the rewriting interceptor is *also* an application interceptor, not
   the interceptor's own pre-rewrite local copy the request-side line
   uses. Corrected fix: `GeminiQueryParamAuthInterceptor` is registered
   via `addNetworkInterceptor` instead — network interceptors sit below
   the entire application-interceptor layer and are structurally
   invisible to `HttpLoggingInterceptor`, not just conveniently ordered
   around it. The regression test for this was also rewritten to use the
   *real* `okhttp3.logging.HttpLoggingInterceptor` class (capturing its
   actual log output via a custom `Logger`) instead of a hand-rolled
   stand-in interceptor — the hand-rolled version had only checked the
   request-side URL and would have kept passing throughout this entire
   mistake.

After all three fixes, real-device testing confirmed the *entire* cloud
dispatch path genuinely works end-to-end on physical hardware: consent
gating, the corrected auth transport, a real ~30–50KB garment-photo
request reaching Google, and the debug diagnostics correctly reporting
provider/model/cache-hit/requested-vs-returned fields at every step. The
final blocker hit — Google returning `HTTP 429` /
`RESOURCE_EXHAUSTED` with `"limit: 0"` for
`generate_content_free_tier_requests` on `gemini-2.0-flash` — was
confirmed, by reading the actual JSON error body (not just the status
code), to be a **Google Cloud billing/plan setting on the tested
account**, not a bug: a `0` free-tier limit doesn't refill with time and
reproduced identically across multiple freshly-rotated keys from
multiple separate Google accounts, which is exactly the signature of a
plan-level restriction rather than a per-key rate limit. Resolving it
requires enabling billing on the Google Cloud project tied to the API
key — outside this app's code entirely, and correctly so: nothing in this
milestone should or could route around a provider's real billing
requirement.
