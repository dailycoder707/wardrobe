# Dependency Update Policy

This project pins exact versions in `gradle/libs.versions.toml` rather than version
ranges, and every version currently in that file was checked against real Maven
metadata and a real build on 2026-08-01 (see `DEPENDENCIES.md`, `BUILD_VERIFICATION.md`).
This document is how that stays true over time instead of decaying into stale, unverified
pins.

## Dependencies permanently out of scope

Per Constitution rule 13 / [ADR-011](docs/adr/ADR-011-permanent-privacy-first-principles.md)
(as amended 2026-08-05 by [ADR-012](docs/adr/ADR-012-cloud-ai-provider-amendment.md)
for cloud AI specifically — see the carve-out below), the following
categories are rejected on sight, with no case-by-case deliberation needed,
regardless of how small, cheap, or convenient the integration would be:

- **Cloud LLM/vision SDKs and remote ML inference clients are no longer a
  blanket rejection** — see "Cloud AI dependencies" below for the narrow,
  conditional carve-out ADR-012 introduced. A dependency in this category
  still requires the Gateway/adapter isolation ADR-012 mandates; it is not
  a free pass to add any vendor SDK anywhere in the codebase.
- Any analytics or crash-reporting SDK that transmits data off-device
  (Firebase/Crashlytics and equivalents) — already excluded by ADR-004,
  restated here as a dependency-approval filter, not just an architecture
  note. **Unaffected by ADR-012** — this exclusion is about telemetry
  phoning home automatically, not user-initiated AI processing calls.
- Any backend-as-a-service client (Supabase, Firebase, AWS Amplify, or
  similar) for auth, storage, or sync — multi-device sync is local-network
  only (Phase 8) and stays that way. **Unaffected by ADR-012.**

### Cloud AI dependencies (conditional carve-out, added 2026-08-05)

A cloud AI vendor's HTTP API (OpenAI, Azure OpenAI, Gemini, Claude,
OpenRouter, Ollama, or a self-hosted generic REST backend) may be
integrated **only** when all of the following hold — otherwise it is
rejected on sight exactly like before:

- The integration lives entirely inside a `core:ai` `ProviderAdapter`
  class for that vendor; no vendor-specific request/response shape or auth
  scheme appears in any feature module or in `core:ai`'s own Gateway/router
  layer.
- The call is only ever reachable through `core:ai`'s `AiGateway`, gated by
  the user's explicit per-capability consent and an on-device fallback.
- No vendor SDK that requires an account, license, or bundles its own
  telemetry is added — plain HTTP via the existing Retrofit/OkHttp/
  kotlinx.serialization stack (already used for the weather API) is
  preferred over an official vendor SDK, consistent with keeping the
  dependency footprint vendor-neutral.
- Still rejected regardless of the above: any dependency that adds
  analytics/crash-reporting, requires a user account for *this app itself*
  (as opposed to the user's own separately-held cloud vendor account), or
  provides data storage/backup — those exclusions are untouched by ADR-012.

On-device ML runtimes (ML Kit, TensorFlow Lite, ONNX Runtime Mobile, and
similar) remain allowed exactly as before (`core:image`'s background
removal, and the on-device providers `core:ai` adds alongside the cloud
ones) — this was never part of what ADR-012 needed to change.

## Update cadence

| Category | Cadence | Trigger |
|---|---|---|
| Patch releases (AndroidX, Kotlin, Coil, Retrofit/OkHttp, testing libs) | Monthly check | Calendar, not urgency — low risk, low effort |
| Minor releases (same rule as patch, but read the changelog first) | Monthly check, same pass as patch | Calendar |
| Major releases (AGP, Gradle, Kotlin language version, Hilt/Dagger major) | Quarterly review | Calendar, unless a blocking security advisory forces it sooner |
| Security advisories (any dependency) | Immediate, out-of-band | GitHub/Google Play security bulletins, `./gradlew dependencyCheckAnalyze`-style tooling if later added |
| Anything listed in `TECHNICAL_DEBT.md` | Whenever its stated unblocking condition is met | See that file's per-item upgrade checklist |

A monthly/quarterly cadence is a floor, not a ceiling — nothing here prevents an
out-of-cycle bump when there's a specific reason (a bug fix this project actually hits,
a feature this project actually needs).

## Semantic version rules

- **Patch (`x.y.Z`)**: bump directly, no separate approval step beyond the normal
  upgrade-testing process below. Patch releases are expected to be behavior-preserving.
- **Minor (`x.Y.z`)**: read the release notes/changelog first — specifically checking
  for (a) new AAR-metadata requirements (compileSdk/AGP floor bumps — this bit this
  project three times in Phase 2, see `BUILD_VERIFICATION.md`), (b) deprecations that
  affect this codebase, (c) new Lint/Detekt-flagged behavior. Then run the full
  upgrade-testing process.
- **Major (`X.y.z`)**: treated as an architectural decision, not a routine bump. Record
  it as a new ADR (`docs/adr/`) if it changes a decision another ADR already made (e.g.
  a Kotlin major version bump that finally allows removing the AGP bridge flags from
  `TECHNICAL_DEBT.md` item 1 should reference and update that ADR/debt entry, not just
  silently change a number). Run the full upgrade-testing process on a dedicated branch
  before merging.
- **Pinning below latest** (as already done for `core-ktx`, `androidx.lifecycle`,
  `androidx.hilt` — see `DEPENDENCIES.md`): always requires a comment in
  `libs.versions.toml` explaining why, and a corresponding entry in `TECHNICAL_DEBT.md`
  with an upgrade checklist. An undocumented pin is a bug in this policy's application,
  not an acceptable state.

## Upgrade testing process

1. Bump the version(s) in `gradle/libs.versions.toml` on a branch — one logical change
   at a time (e.g. "bump AndroidX Lifecycle" as its own change, not bundled with an
   unrelated AGP bump), so a regression is traceable to a single version change.
2. `./gradlew clean build` — must reach `BUILD SUCCESSFUL` with the same "zero
   ktlint/Detekt/Lint issues" bar as `BUILD_VERIFICATION.md` records today. A new
   warning appearing is not automatically a blocker, but it must be explained (either
   fixed, or added to `TECHNICAL_DEBT.md` with reasoning — never silently ignored).
3. Run unit tests (`./gradlew test`) and, once instrumented tests exist, connected
   tests on at least one device/emulator.
4. Manual smoke test of the app's critical flows on a device/emulator — a green build
   is necessary but not sufficient (see `BUILD_VERIFICATION.md`'s own unchecked items).
5. If the upgrade resolves or changes anything tracked in `TECHNICAL_DEBT.md`, update
   that file in the same change — don't let it drift out of sync with reality.
6. If the upgrade was blocked by something (an AAR-metadata requirement, a plugin
   incompatibility), record what and why in `TECHNICAL_DEBT.md` before abandoning the
   attempt, so the next person doesn't re-discover the same wall from scratch.
