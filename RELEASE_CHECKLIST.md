# Play Store Release Checklist

Originally written at Phase 2.1 (no features implemented yet) while the
constraints (no cloud, no accounts, offline-first — ADR-003/004) were
fresh. **Updated at RC1** (Production Hardening & Release Candidate,
2026-08-06): the app now has real features, including ADR-012's opt-in
cloud AI amendment — see the RC1 notes inline below wherever the original
Phase-2.1 assumption ("no user data collected or shared," in particular)
no longer holds unconditionally. Every box not explicitly marked done
below is still genuinely unverified — this checklist does not claim a
physical device or a live Play Console listing exists, because neither
does in this development environment. See `SECURITY_AUDIT.md` and
`PRODUCTION_VALIDATION_REPORT.md` for what *has* been verified so far
(automated tests and code inspection) and what's left for a real device
and a real Play Console session.

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

- [x] `./gradlew clean build` passes with zero Lint/ktlint/Detekt issues —
      re-verified fresh as of RC1 (2026-08-06), not assumed still true; re-run it
      again immediately before actually uploading, since this box goes stale the
      moment another commit lands
- [ ] R8/minification (`isMinifyEnabled`, `isShrinkResources`) verified against the
      **actual release build**, not just configured — install the release APK/AAB on a
      real device and exercise every screen; R8 stripping a class Room/Hilt/
      kotlinx.serialization needed reflectively is a real, silent-until-runtime failure
      mode. **Configuration itself confirmed correct** (`app/build.gradle.kts`: both
      flags on, `proguard-rules.pro` minimal and Kotlin-metadata-aware) — the *runtime*
      verification on a real device is still open
- [x] `proguard-rules.pro` reviewed — still close to empty (per its own comment); no
      R8-stripped crash has ever surfaced to justify adding a rule
- [ ] Baseline profile generated and bundled (`benchmark/README.md` — needs a connected
      device; not part of the default build). **Still not done** — `benchmark` has no
      `StartupBenchmark`/macrobenchmark test classes written yet either (its own
      README has said so since Phase 2; still true at RC1)
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
      `READ_EXTERNAL_STORAGE` maxSdk 32, `ACCESS_COARSE_LOCATION`, `INTERNET`,
      `ACCESS_NETWORK_STATE`/`ACCESS_WIFI_STATE`/`CHANGE_WIFI_MULTICAST_STATE` for
      local-network sync) is actually used by shipped code — remove any that
      aren't, since an unused dangerous permission is both a Play Console review
      flag and a user-trust cost. Verified present-and-used as of RC1; re-check at
      actual release time in case anything changed since
- [ ] Runtime permission rationale UI exists and was tested for camera, location, and
      (pre-33) storage — a bare OS permission dialog with no context is a real UX and
      review-risk gap
- [ ] **Play Console Data Safety form** — **RC1 update: the Phase-2.1 answer below no
      longer holds unconditionally.** With every AI capability left at its default
      (on-device), the original answer stands: no accounts, no analytics, no ad SDK,
      no crash SDK, no data leaves the device except the Open-Meteo weather call
      (no personal data — only coarse location or a manually-picked city). **But**
      ADR-012 added an opt-in path: if the user configures and consents to a cloud AI
      provider for any capability (Garment Extraction/Reconstruction/Metadata, Outfit
      Styling, Virtual Try-On), that capability *does* send data (a garment photo, or
      for Styling a wardrobe-context fingerprint) to a third party the user chose and
      explicitly consented to. The Data Safety form must reflect this as a real,
      conditional data-sharing path — "shared with a third party the user configures
      and consents to, only when cloud mode is enabled" — not silently glossed over
      as "no data collected." Complete the form against what the shipped build
      actually does, not against the pre-M12 assumption
- [ ] Privacy policy published and linked in the store listing, matching the Data
      Safety form exactly (including the RC1 cloud-AI conditional-sharing note above)
      — required by Play Console regardless of how little data is collected by default
- [ ] `backup_rules.xml`/`data_extraction_rules.xml` re-verified against the actual
      shipped data model (ADR-009) — **done as of RC1** (`SECURITY_AUDIT.md` §1): both
      already excluded all SharedPreferences (including `EncryptedApiKeyStore`) from
      cloud backup; RC1 additionally excluded that same file from device-transfer,
      since its Keystore-bound master key can't itself transfer. Re-verify again if a
      future release adds any new local storage location
- [ ] API key security reviewed — **done as of RC1**, see `SECURITY_AUDIT.md` §1 in
      full: never logged, never in a crash report (none exists), never in Room or
      DataStore, always read through `EncryptedApiKeyStore`, excluded from both backup
      channels

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
