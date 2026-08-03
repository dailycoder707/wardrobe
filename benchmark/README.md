# :benchmark

Baseline Profile generation (Section 22, Phase 2 request) and, later, Macrobenchmark
tests for cold start / scroll jank (Phase 1 Section 21-23: "measure, don't assume").

## What's configured now
- `com.android.test` + `androidx.baselineprofile` Gradle plugins wired, targeting
  `:app`.
- `:app`'s `build.gradle.kts` declares `baselineProfile(project(":benchmark"))` and
  bundles `androidx.profileinstaller`, so a generated profile is actually consumed
  at install time once one exists.
- A `benchmark` build type (debuggable but profileable, falls back to the
  `release` build type's resources/minification) — benchmarking a `debug` build
  measures the debugger, not the app.

## What's NOT here yet, deliberately
No `StartupBenchmark`/`BaselineProfileGenerator` test class exists yet — there are
no screens or user flows to profile (Phase 2 explicitly does not implement
features). Writing a benchmark test now would mean profiling the one placeholder
screen in `WardrobeNavHost`, which produces a profile that gets thrown away the
moment Phase 5 adds real screens — that's not "measuring," it's motion.

## Real constraint, stated honestly
`./gradlew generateBaselineProfile` requires a connected device or running
emulator — it is not part of `./gradlew build` or `check`, and was **not** run as
part of this phase's build verification (see the root build verification
checklist). Run it manually once real screens exist and a device/emulator is
attached: `./gradlew :app:generateBaselineProfile`.

`minSdk = 28` here (vs. the app's `minSdk = 26`) is `androidx.benchmark.macro`'s
own floor, not a project-wide minSdk change — it only affects this test-only
module, never a real device below API 28 running the actual app.
