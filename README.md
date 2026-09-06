# Wardrobe

A private, single-household personal wardrobe app: capture your clothes,
get AI-assisted metadata/background removal, build outfits, get
weather/calendar-aware recommendations, and (optionally, opt-in per
capability) use a cloud AI provider you configure yourself for garment
extraction, reconstruction, metadata, outfit styling, or virtual try-on.
Everything else is fully offline by design — see
[`docs/adr/ADR-011-permanent-privacy-first-principles.md`](docs/adr/ADR-011-permanent-privacy-first-principles.md)
and its [ADR-012](docs/adr/ADR-012-cloud-ai-provider-amendment.md) amendment
for exactly what that does and doesn't cover.

**Status**: Release Candidate 1 (RC1) — see
[`PRODUCTION_VALIDATION_REPORT.md`](PRODUCTION_VALIDATION_REPORT.md) for
what's been verified and what still needs a real device/cloud account, and
[`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md) for the full release gate.

## Prerequisites

- **JDK 21** (matches `compileOptions`/`jvmToolchain(21)` in every module)
- **Android SDK platform 36** installed (`compileSdk`/`targetSdk` are both
  36 — see `DEPENDENCIES.md` for why not 37 yet)
- No other accounts, API keys, or external services are required to build,
  run, or test the app. A cloud AI provider key is only needed if you want
  to exercise that optional, opt-in path yourself (Settings → AI Providers).

The Gradle wrapper (`./gradlew` / `gradlew.bat`) downloads the pinned
Gradle 9.6.1 distribution itself — no separate Gradle install needed.

## Getting started

```bash
git clone <this repo>
cd wardrobe
./gradlew build
```

That's it — a fresh clone builds with no other setup. `assembleRelease`
also works out of the box, falling back to debug signing when no release
keystore is configured (see below).

## Common tasks

```bash
./gradlew build                 # full build: compile, lint, detekt, ktlint, all tests
./gradlew test                  # unit tests only, every module
./gradlew detekt                # static analysis only
./gradlew ktlintCheck           # style check only
./gradlew ktlintFormat          # auto-fix style violations
./gradlew :app:assembleDebug    # just the debug APK
./gradlew :app:assembleRelease  # release APK (R8/shrink enabled; see below for signing)
```

Every module also supports these individually, e.g.
`./gradlew :core:ai:test :core:ai:detekt`.

## Release signing (optional, only needed for a Play-Store-signed build)

1. Copy `keystore.properties.example` to `keystore.properties` (repo root —
   already gitignored, never commit the real file).
2. Fill in your real `storeFile`/`storePassword`/`keyAlias`/`keyPassword`.
3. `./gradlew :app:assembleRelease` now signs with that key instead of
   falling back to debug signing.

## Project layout

25 Gradle modules — `core:*` (pure logic/data layers, no UI), `feature:*`
(one per user-facing area, Compose UI + ViewModel), and `app` (the
composition root only — no feature logic belongs there). See
[`PROJECT_STRUCTURE.md`](PROJECT_STRUCTURE.md) for the historical Phase-2
snapshot of the tree's shape (still accurate for the top-level layout
rules, though the module count has grown since) and each module's own
`README.md` where one exists.

## Key documents

| Doc | What it's for |
|---|---|
| [`DEPENDENCIES.md`](DEPENDENCIES.md) | Every dependency version and why it was chosen |
| [`DEPENDENCY_POLICY.md`](DEPENDENCY_POLICY.md) | What kinds of dependencies this project will/won't add |
| [`TECHNICAL_DEBT.md`](TECHNICAL_DEBT.md) | Every known tradeoff/gap, tracked deliberately, never silently forgotten |
| [`SECURITY_AUDIT.md`](SECURITY_AUDIT.md) | RC1's full security review |
| [`KNOWN_LIMITATIONS.md`](KNOWN_LIMITATIONS.md) | User-facing summary of what doesn't work yet |
| [`PRODUCTION_VALIDATION_REPORT.md`](PRODUCTION_VALIDATION_REPORT.md) | What's been verified automatically vs. what needs a real device/cloud account |
| [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md) | The full Play Store release gate |
| [`BETA_TEST_GUIDE.md`](BETA_TEST_GUIDE.md) | How to run/use a beta build |
| `docs/adr/` | Architecture Decision Records — the *why* behind major choices |

## Contributing / conventions

- Kotlin style is enforced by ktlint (`.editorconfig`) and Detekt
  (`config/detekt/detekt.yml`) — `./gradlew build` fails on any violation,
  there is no warn-only mode.
- Never suppress a lint/detekt finding just to make a build pass — fix the
  underlying issue (split a file, bundle parameters, etc.) unless the
  finding is genuinely structural, not a code smell (Compose's naturally
  parameter-heavy composables, a Room DAO's column count, an SDK-version-
  gated deprecated API with no replacement below minSdk). Suppressions are
  rare and each one carries a comment explaining why it's justified rather
  than fixable — see `CODE_HEALTH_REPORT.md` for the full, reviewed list.
- New ADRs go in `docs/adr/` as `ADR-0NN-short-title.md`, numbered
  sequentially, never renumbered or deleted once accepted.
