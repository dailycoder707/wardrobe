# Play Store Release Checklist

This is a checklist for a *future* release, written now while the constraints
(no cloud, no accounts, offline-first — ADR-003/004) are fresh, so release-time
decisions (like the Data Safety form) are easy and honest rather than guessed under
deadline pressure. Nothing here is done yet — this project is at the end of Phase 2.1,
with no features implemented. Treat every box as unchecked until actually verified,
the same discipline `BUILD_VERIFICATION.md` already applied to the build itself.

## 1. Versioning & signing

- [ ] `versionCode` incremented from the last released build (monotonic, never reused)
- [ ] `versionName` follows a real scheme (e.g. semver) and matches what's user-facing
- [ ] Real `keystore.properties` present (not the `.example` template) and **backed up
      somewhere outside this repo** — losing the release signing key means never being
      able to update the app under the same listing again
- [ ] Release build is actually signed with the release key, not silently falling back
      to debug signing (`app/build.gradle.kts`'s fallback logic — confirm which
      branch is active before uploading)
- [ ] Signing key algorithm/validity meets current Play Console requirements (RSA 2048+
      or equivalent; check current Play Console signing requirements at release time,
      since these do shift)

## 2. Build correctness

- [ ] `./gradlew clean build` passes with zero Lint/ktlint/Detekt issues
      (`BUILD_VERIFICATION.md`'s bar, re-verified fresh, not assumed still true)
- [ ] R8/minification (`isMinifyEnabled`, `isShrinkResources`) verified against the
      **actual release build**, not just configured — install the release APK/AAB on a
      real device and exercise every screen; R8 stripping a class Room/Hilt/
      kotlinx.serialization needed reflectively is a real, silent-until-runtime failure
      mode
- [ ] `proguard-rules.pro` reviewed — still close to empty (per its own comment) unless
      a real R8-stripped crash proved a specific rule necessary
- [ ] Baseline profile generated and bundled (`benchmark/README.md` — needs a connected
      device; not part of the default build)
- [ ] Built as an **Android App Bundle (.aab)**, not a universal APK, for the actual
      Play Store upload

## 3. Compatibility & device coverage

- [ ] Tested on a real minSdk-26 device or equivalent emulator (never actually done as
      of this checklist being written — `BUILD_MATRIX.md`'s "supported but not yet
      tested" table)
- [ ] Tested on at least one recent Android version (currently targetSdk 36, per
      `BUILD_MATRIX.md`)
- [ ] `targetSdk` meets Google Play's current target-API-level requirement (Play
      requires targeting within roughly one year of the latest Android release for new
      apps/updates — check the live requirement at release time, since the exact rule
      changes)
- [ ] Tested on at least one small-screen phone and one large-screen/tablet device —
      `phase-1-architecture.md` Section 30 notes `WindowSizeClass`-adaptive scaffolding
      is intended even before dedicated tablet layouts ship; confirm it doesn't look
      broken on a tablet even if not yet optimized for one
- [ ] Dark mode and light mode both checked on a real device (`core:designsystem`'s
      `WardrobeTheme`)
- [ ] RTL layout checked on a real device with a RTL system language, not just assumed
      from Compose's automatic mirroring (`phase-1-architecture.md` Section 29)
- [ ] TalkBack pass on the critical flows (capture → save, build outfit, log wear) —
      Constitution rule 7's confidence-signalled chips specifically need this, since
      they were designed to never be color-only

## 4. Permissions & privacy

- [ ] Every permission declared in `AndroidManifest.xml` (`CAMERA`,
      `READ_EXTERNAL_STORAGE` maxSdk 32, `ACCESS_COARSE_LOCATION`, `INTERNET`) is
      actually used by shipped code — remove any that aren't, since an unused
      dangerous permission is both a Play Console review flag and a user-trust cost
- [ ] Runtime permission rationale UI exists and was tested for camera, location, and
      (pre-33) storage — a bare OS permission dialog with no context is a real UX and
      review-risk gap
- [ ] **Play Console Data Safety form** completed and cross-checked against ADR-004: as
      of this project's current scope (no accounts, no analytics, no ad SDK, no crash
      SDK, no data leaves the device except the Open-Meteo weather call which sends no
      personal data — only coarse location or a manually-picked city), the honest
      answer is "no user data collected or shared." Re-verify this section against
      whatever the app *actually does* at release time, not against this document —
      if a future feature changes this, the form (and this checklist item) must change
      with it
- [ ] Privacy policy published and linked in the store listing, matching the Data
      Safety form exactly — required by Play Console regardless of how little data is
      collected
- [ ] `backup_rules.xml`/`data_extraction_rules.xml` re-verified against the actual
      shipped data model (ADR-009) — if Phase 3+ adds new local storage locations
      (new DataStore files, new file directories), confirm they're covered by the
      exclusion rules before release, not discovered after

## 5. Store listing

- [ ] App name, short description, full description written and accurate to what's
      actually shipped (no describing CUT-tier features from the original teardown —
      avatar try-on, shopping, community feed, AI chat — that this product doesn't have)
- [ ] Screenshots reflect the actual shipped UI (Phase 4's real design system, not the
      Phase 2 placeholder screen)
- [ ] Feature graphic / icon uses real brand art (Phase 4) — the current launcher icon
      (`app/src/main/res/drawable/ic_launcher_*.xml`) is an explicitly-labeled
      placeholder, not release-ready
- [ ] Content rating questionnaire completed
- [ ] Category set correctly (Lifestyle, matching the source-app teardown's category
      framing, unless a different category fits better once real screens exist)
- [ ] Contact email / support channel provided and actually monitored

## 6. Pre-launch testing

- [ ] Closed testing track (internal or closed) run with at least a few real users on
      real devices before any production rollout
- [ ] Play Console's automated pre-launch report reviewed (crash/ANR detection across
      Google's device farm) — this is free and doesn't require any SDK integration
      (ADR-004), so there's no reason to skip it
- [ ] Cold start performance measured (`phase-1-architecture.md` Section 21-23,
      Macrobenchmark in `benchmark/`) rather than assumed acceptable
- [ ] Closet browse tested at a realistic scale (several hundred garments, not just a
      handful) — the specific scale problem Paging 3 (`DEPENDENCIES.md`) was chosen to
      solve
- [ ] A full offline pass: airplane mode, exercise every screen, confirm nothing
      dead-ends (ADR-003) — this is the single most load-bearing promise of this
      product and deserves its own explicit pass, not just incidental coverage from
      other testing

## 7. Rollout & rollback plan

- [ ] Staged rollout used for the first production release (start at a small
      percentage, e.g. 5-10%, watch crash rate and reviews before expanding) — not a
      100% rollout on day one
- [ ] A rollback plan exists and is understood *before* rollout starts: Play Console
      supports halting a staged rollout, but does not support "unreleasing" a version
      already fully rolled out — know this distinction going in
- [ ] Crash-rate and ANR-rate thresholds decided in advance (e.g. "halt rollout if
      crash rate exceeds X%") rather than decided reactively under pressure
- [ ] A path back to a previous working release exists (keep the previous release's
      signed `.aab` and its `versionCode` on hand) in case a halt-and-fix is needed

## 8. Post-release

- [ ] Play Console's crash/ANR dashboard actually monitored on a cadence after release
      (this is the *only* crash visibility this app has, per ADR-004 — there is no
      Crashlytics fallback if this is neglected)
- [ ] User reviews monitored, at least during the staged-rollout window
- [ ] `TECHNICAL_DEBT.md` and `BUILD_MATRIX.md` updated to reflect whatever the actual
      shipped build's final dependency versions were, if anything changed between this
      checklist being written and the real release
