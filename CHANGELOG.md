# Changelog

All notable changes to this project are documented here, in the
[Keep a Changelog](https://keepachangelog.com/) style. New file, added at
RC1 — this project has no prior release, so there is one entry so far.
See `RELEASE_NOTES.md` for the user-facing version of this same entry.

## [Unreleased] — M24

M23 proved on-device AI genuinely only supports Color/Pattern/Brand. M24
audited whether the existing Cloud AI metadata path — which claims all 19
fields — actually works end-to-end, or only appears to. It mostly does:
dispatch, all 6 vendor adapters, JSON parsing, genuine per-field
confidence, consent gating, caching, and honest fallback were all already
correct and tested. Three real, narrower gaps were fixed. Real-device
testing on a physical tablet then found three *more* real bugs (Base URL
never pre-filled/validated, Gemini's RC2 header auth rejected by Google's
live API, and a self-inflicted API-key logging leak in the first fix
attempt for that) — all fixed and verified. See
`docs/adr/ADR-023-m24-cloud-ai-metadata-autofill.md`.

### Added

- Provider-native structured JSON output: OpenAI-compatible vendors
  (OpenAI, Azure OpenAI, OpenRouter, Ollama) now send `response_format:
  json_object`; Gemini sends `responseMimeType: application/json` — the
  cloud provider itself now enforces the JSON-only response the prompt
  already asks for, instead of relying on prompt compliance alone. Claude
  has no native equivalent and stays prompt-only (disclosed limitation).
- Deterministic whitespace/hyphen normalization in reference-data matching
  (`"T-Shirt"`/`"T Shirt"`/`"TShirt"` all resolve identically) — never a
  fuzzy/semantic guess; a genuinely different value still never matches.
- Router-level debug diagnostics (`GarmentMetadataEngineRouter`, gated the
  same way as M22/M23's debug-only logging) extending M23's per-field
  funnel with provider/model/capability/cache-hit/requested-vs-returned
  fields/failure-reason, under the same `MetadataPipeline` logcat tag.

### Fixed

- A cloud dispatch failure's reason was silently discarded at the router —
  now captured in debug diagnostics (never surfaced insecurely; no
  secrets/images/raw response text logged).
- **Found via real-device testing:** Settings' Base URL field had no
  default and nothing validated it before consent could be granted — a
  user could complete cloud setup with a blank Base URL and have it
  silently stay on-device forever. `AiVendor.defaultBaseUrl()` now
  pre-fills known vendors' real endpoints; the consent button is disabled
  with an explanatory label until Vendor + Base URL are both present.
- **Found via real-device testing:** Google's live API rejected Gemini's
  header-based auth (`x-goog-api-key`) with a `404` on `:generateContent`
  for a real key/project, while `?key=` query-param auth was accepted.
  Fixed via a dedicated OkHttp network interceptor that moves the key to
  the query parameter only for Gemini requests.
- **Self-inflicted, found and fixed in the same real-device session:** the
  first attempt at the fix above leaked the API key into debug Logcat by
  registering the rewriting interceptor at the wrong OkHttp layer
  (application-level ordering, not a network interceptor). The user's key
  was exposed as a result and they were told to rotate it immediately;
  the interceptor is now registered correctly (`addNetworkInterceptor`),
  and its regression test uses the real `HttpLoggingInterceptor` class
  instead of a hand-rolled stand-in that had missed the leak.

## [Unreleased] — M23

Real-device evidence (a physical tablet) showed Add Garment's AI review
auto-filling only Color/Pattern, with every other field stuck on "Unknown
— Please choose." A full pipeline audit found the resolver/UI-state/
Compose-binding layers already correct — the actual gap was that the
on-device engine's real (and always documented) capability boundary
(Color/Pattern/Brand only) was never surfaced as a distinct state from
"AI can detect this but didn't this time." See
`docs/adr/ADR-022-m23-ai-metadata-autofill-transparency.md`.

### Added

- `MetadataFieldSupport` (`core:model`): a declared, testable contract for
  which metadata fields each AI source (on-device/cloud/manual) can
  actually produce.
- A third "Not supported by On-Device AI" state on Add Garment's review
  screen, distinct from "Unknown — please choose" — with a pointer to
  enabling Cloud AI for full detection.
- A "Detected, but no matching option found" flag on suggestions that
  fail reference-data resolution, previously indistinguishable from a
  low-confidence suggestion awaiting review.
- Debug-build-only structured metadata-pipeline diagnostics
  (`MetadataPipelineDiagnostics`), gated the same way as M22's
  network-logging fix, for real-device debugging.

### Fixed

- Add Garment's review screen collapsed "this AI provider can't detect
  this field at all" and "it can, but didn't detect it in this photo"
  into the same generic "Unknown — please choose" row.

## [Unreleased] — M22

A whole-app closing pass rather than a new feature milestone — six
parallel research passes across docs/ADRs, the design-system component
inventory, and every major screen found the app already close to
production-ready. Real scope: 14 genuine bugs/gaps.

### Added

- **AI-suggested vs. user-entered field indicator** in Add Garment
  review: a field bound to an AI suggestion now shows an "AI suggested"
  caption at the point of the actual editable control, not just in the
  separate summary list above. `DropdownField` (`core:ui`) gained an
  additive `helperText` parameter to support this.
- **Non-color selected-state signal** on `WardrobeFilterChip`: a small
  checkmark icon now accompanies the existing color/fill change,
  reaching every screen that uses this component or `MultiSelectChips`.

### Fixed

- `HomeViewModel.assistantState` had no error handling anywhere — a
  single throwing suspend call could crash the app. Now degrades safely
  instead.
- `AttentionItemsCard` couldn't distinguish "0 items, healthy" from
  "not loaded yet" (a non-nullable count defaulting to `0`).
- `showWardrobeHealthCard`'s toggle didn't actually gate
  `WardrobeHealthScoreCard`, only the similarly-named summary card.
- Home's card titles used three different type scales with no shared
  convention.
- Import queue's retry button used the Close icon glyph instead of
  Refresh.
- A lost staged image silently closed the Garment Review screen with no
  explanation.
- `ClosetGridSkeleton` was built for `ClosetScreen` but never wired in.
- Recommendations' empty state conflated a genuinely empty wardrobe with
  an insufficient one under one message.
- Calendar's top-level state had no error boundary — a repository
  failure left the screen stuck loading forever.
- 11 call sites used lifecycle-unaware `collectAsState()` instead of
  this project's own `collectAsStateWithLifecycle()` convention.
- `HttpLoggingInterceptor` ran unconditionally in every build variant,
  including release — now debug-build-only (defense-in-depth; the
  actual API-key leak this could have caused was already fixed at RC2).
- Virtual Try-On's render decoded two full-resolution bitmaps and ran
  the compositing engine on the Main dispatcher.

### Documentation

- `docs/adr/ADR-021-m22-visual-accessibility-production-hardening.md`
  records what was already correct across the app, the 14 real fixes
  above, 3 findings investigated and confirmed *not* bugs, and every
  deliberately deferred item with its reasoning.

## [Unreleased] — M21

### Added

- **Wardrobe Mix section** on the Insights dashboard: Material Mix,
  Fabric Mix, and Occasion Coverage bar charts, built from three new
  composition-based `StatsRepository`/`StatsDao` queries (never
  wear-based — how many *active* garments are tagged with each
  material/fabric/occasion). Occasion Coverage includes real occasions
  with zero garments as the gap itself, mirroring `ClosetGap`'s existing
  reasoning; a new honest "N items have no occasion tagged" disclosure
  line appears when applicable. Each chart is absent entirely when
  nothing is tagged — never an empty chart implying a real measurement.
- **Tablet/large-screen layout** for the Insights screen: a
  `BoxWithConstraints`-based width-capping reading column
  (`WIDE_LAYOUT_MIN_DP = 840`), the same idiom already used by
  `feature:calendar`/`feature:closet`/`feature:outfits`.

### Fixed

- `observeActiveGarmentCountBySeason`/`ByDressCode` (Phase 9) silently
  counted an archived garment's season/dress-code tag toward *active*
  coverage — a `COUNT(DISTINCT gs.garmentId)` vs. `COUNT(DISTINCT g.id)`
  bug inside a compound-condition `LEFT JOIN`, capable of hiding a real
  wardrobe gap. Fixed; the new material/fabric/occasion queries were
  written correctly from the start.
- Insights' "Waiting to Be Worn" list hardcoded `StatsWindow.ALL_TIME`
  regardless of the period selector — every other window-scoped section
  respected it, this one silently didn't. Now respects the selected
  window.

### Documentation

- `docs/adr/ADR-020-m21-insights-dashboard.md` records the
  already-complete `feature:stats` Insights Dashboard this milestone
  built on top of (wardrobe overview, wear activity, favorites,
  cost/value insights, ~12 actionable lists, Wardrobe Story, Wardrobe
  Health — none of it net-new), the real gaps this milestone filled, and
  the decision not to add a new AI capability for "AI Insights."

## [Unreleased] — M20

### Added

- **Recommend for a calendar date**: Day Detail's empty state ("No outfit
  planned") gained a real "Get recommendation" action that calls the
  exact M19 `StylingEngineRepository.suggestOutfits` path with that
  date's context — no second recommendation engine. "Show another" reuses
  M19's identical genuinely-different-outfit dedup and honest exhaustion
  message.
- **Replace a planned outfit** with a new recommendation, via a new icon
  on planned Day Detail rows — updates the existing `WearEvent` in place
  rather than creating a duplicate.
- **Occasion assignment per day**: a new occasion chip row in Day Detail
  (reusing the real `Occasion` reference table) writes `WearEvent.occasionId`
  for the first time — previously a field that existed end-to-end
  (model/entity/sync wire) but nothing ever set it. Feeds directly into
  M19's existing `SuggestionContext.occasionId` calendar-fallback
  behavior, unchanged.
- **Honest weather context for a selected date**: three genuinely
  distinct states (Available / Unavailable / Forecast unavailable for
  this date — the last one derived from a real date mismatch on the
  returned snapshot, not a guessed cutoff) plus a new `WeatherSnapshot.dailyHeadline()`
  (`core:model`) for non-today dates, since the existing `headline()`
  only reads today's live reading.
- **"Worn recently" / "Not worn recently"** on planned Day Detail rows,
  from real `StatsRepository.observeCostPerWear()` data.

### Fixed

- `CalendarViewModel` now computes "today" from an injected `Clock`
  instead of bare `LocalDate.now()`/`YearMonth.now()`, matching every
  other date-sensitive ViewModel's convention — fixes a latent
  test-determinism gap.
- `ContextResolution.prependPlannedOutfit`'s "You already planned this
  outfit for today." explanation is now date-aware — it previously
  hardcoded "today," which was only ever exercised with `date == today`
  until Calendar's date-aware recommendation calls exposed it.
- Replacing a still-planned "today" event with a new recommendation no
  longer silently marks it worn — mirrors `onRescheduleEvent`'s existing
  status-preservation rule.

### Documentation

- `docs/adr/ADR-019-m20-calendar-and-outfit-planning.md` records the
  already-complete `feature:calendar` module this milestone built on top
  of (month grid, day detail, log/plan/reschedule/duplicate/recurring
  wear, Phase 9 conflict detection — none of it net-new), the six real
  gaps this milestone filled, and why notifications were deliberately
  left out.

## [Unreleased] — M19

### Added

- **Real "Show another"**: `StylingEngineRepository.suggestOutfits` gained
  a `count` parameter, threaded into `OutfitAssembler`'s existing
  multi-candidate assembly (no second engine). The Recommendations screen
  requests progressively more candidates and only advances when a
  genuinely different outfit (by garment-ID signature) appears, reporting
  honestly — "No other complete outfit matches this context with your
  current wardrobe." — once the wardrobe's own candidate pool is
  exhausted, instead of silently repeating.
- **Occasion selection that genuinely recomputes**: `SuggestionContext.occasionId`
  (declared since M9, never read) now overrides the calendar-derived
  planned occasion in `ContextResolution.resolvePlannedOccasionDressCode`.
  A new occasion chip row on the Recommendations context header drives a
  real recompute, proven by tests asserting the engine's own context
  input changes.
- **An honest error state**: recommendation-fetch failures (weather,
  engine, cloud parsing) now land on a dedicated "Something went wrong" /
  "Try again" card instead of crashing or silently showing stale data.
- **Try On**, wired into the existing outfit-actions row and the existing
  `TryOnRoute` — no new flow.
- **"Why this?"** now renders `RecommendationExplainer`'s existing real
  explanation text as a bulleted list; suggestion tabs relabeled "Best
  Match"/"Alternative N"; a live "Using Cloud AI to style your outfit…"
  banner reuses M18's `AiActivityBanner` and `observeActiveOperations()`
  signal, scoped to `OUTFIT_STYLING`.

### Changed

- `StylingEngineRepository.suggestOutfits` signature change (new
  defaulted `count` parameter) — every implementation and both
  `FakeStylingEngineRepository` test doubles updated.
- `RecommendedOutfitUiModel` gained a required `reasonBullets` field.

### Documentation

- `docs/adr/ADR-018-m19-recommendations-rebuild.md` records what the
  recommendation engine, cloud validation, and explanation text already
  did correctly before this milestone (substantially more than the
  brief's "rebuild" framing implied), the four concrete gaps this
  milestone filled, and why Parts 10–13 were left untouched.

## [Unreleased] — M18

### Added

- **Real "AI is currently active" signal**: `AiProviderSettingsRepository.observeActiveOperations()`
  projects `AiJobManager`'s own job ledger (already written on every real
  dispatch, previously read by nothing) into a live list of genuinely
  `PENDING`/`RUNNING` operations — no new table, no second AI-activity
  tracking system.
- **`AiActivityBanner`** (`core:ui`): a reusable, caller-supplied-copy-only
  status line (Running/Success/Failed/Info tones), shown on Home while a
  real AI job is in flight ("Analyzing garment…", etc.) — this codebase's
  first `liveRegion` accessibility usage for dynamic status text.
- **Home's Recent AI Activity is now live** — was a one-shot snapshot taken
  once at screen load; a capability call completing while Home stays open
  now updates the list without leaving and reopening the screen.
- **Home's Cloud-AI nudge now links directly to AI Providers** (was a
  two-hop path through Profile), with an explicit "Configure Cloud AI"
  action.
- **Recommendations now labels rule-based outfits too**: previously only
  cloud-styled outfits got a provenance label ("AI Styled"); the default
  rule-engine case now shows "Styled from your wardrobe preferences and
  today's context" so it's never ambiguous whether an outfit came from an
  actual AI model. The "AI Styled" info dialog also now discloses a cache
  hit when the result was cached.

### Changed

- `AiProviderSettingsRepositoryImpl`'s constructor gained `AiJobDao`
  (already Hilt-provided) to back the new `observeActiveOperations()`
  method — every direct test construction site and both existing
  `FakeAiProviderSettingsRepository` implementations updated accordingly.

### Documentation

- `docs/adr/ADR-017-m18-ai-alive-experience.md` records what AI-transparency
  UI already existed per screen (substantially more than expected — Home,
  Recommendations, and Add-to-Wardrobe/Review all already had real
  provenance/activity/progress surfaces), the one genuine gap this
  milestone filled, and why Try-On's main screen and Stylist Preferences
  were deliberately left untouched (the former dispatches no AI capability
  at all; the latter already has a one-tap path to AI Providers via
  Recommendations).

## [Unreleased] — M17

### Added

- **Closet filters, real multi-select** (`feature:closet`): every filterable
  garment facet (Category — including subcategory refinement — Color,
  Brand, Material, Fabric, Season, Dress Code, Occasion, Tag, Fit, Gender,
  Waterproofing, plus Favorites/Never Worn/Recently Worn/Price range) now
  supports true multi-select with OR-within-a-facet, AND-across-facets
  semantics, evaluated by a single new pure function
  (`Garment.matchesClosetFilters`, `GarmentFilterMatching.kt`), fully
  unit-tested (12 tests covering the AND/OR contract directly). Fabric,
  Occasion, Fit, Gender, and Waterproofing are newly filterable — the
  underlying fields already existed, no schema changes were needed.
- **Active filter chips** now show one removable chip per *selected value*
  (not per facet), each with a "Remove X filter" accessible action.
- **Real result count** ("N items" / "M of N items") shown above the grid,
  always computed from the actual filtered/total counts.
- **Wardrobe insights row**: real, derived counts ("N favorites", "N
  {season} items", "N work-ready items"), shown when browsing the whole
  closet; tapping one applies the matching filter. No AI call, no "AI
  picked"/"Perfect match" style labels.
- **Smart filter presets** (Work/Casual/Travel/Date Night/Athletic): each
  maps to a `DressCode` set reusing the existing
  `Occasion.impliedDressCode()` categorization; applying one is an ordinary,
  fully removable Dress Code filter — no new persisted state.
- **`FAVORITE_FIRST` sort field** added to `GarmentSort` (the only sorting
  gap found — every other requested sort, including the real wear-history
  ones, already existed).
- `EmptyState` (`core:ui`) gained an optional secondary action — Closet's
  "no filter results" state now offers "Clear filters" and "Modify
  filters."

### Changed

- Closet's filtering is now fully evaluated in-memory in `feature:closet`
  (consolidating what was previously split between SQL pushdown and
  in-memory filtering) — `GarmentFilter`/`GarmentRepository`/`GarmentDao`
  are unchanged; only Closet's own computation moved. Facet toggles no
  longer re-query the database, only search does.

### Documentation

- `docs/adr/ADR-016-m17-closet-filters.md` records the filtering
  architecture, AND/OR semantics, preset design rationale (DressCode-based,
  not Occasion-name-based, and why), and the performance decision.

## [Unreleased] — M16

### Added

- **First-run onboarding** (`feature:onboarding`, new module): Welcome →
  Name → Style Preferences → AI/Privacy → Finish. Persists through the
  exact same repositories Profile and Stylist Preferences already use
  (`PersonalizationRepository`, `StylistPreferencesRepository`) — no new
  profile or preferences storage. Every step is genuinely skippable
  without writing fabricated data; a user who skips everything still
  reaches a fully usable Home.
- Real first-run detection (`OnboardingRepository`, `OnboardingDataStore`):
  an existing install (an existing display name or an existing garment)
  is treated as already onboarded automatically, with no migration step
  and no risk of an existing user unexpectedly repeating setup.
- First-run detection's "existing garment" signal reuses the already-existing
  `GarmentRepository.observeGarments()` query (mapped to non-empty) rather
  than adding a new repository method — an added `observeHasAnyGarment()`
  was tried and reverted after it pushed `GarmentRepository` past detekt's
  `TooManyFunctions` threshold.
- `NameValidation` moved from `feature:settings` to `core:domain` so
  Onboarding and Profile share the exact same validation, not two copies.

### Documentation

- `docs/adr/ADR-015-m16-onboarding.md` records the persistence, first-run
  detection, and navigation-integration decisions.
- `TECHNICAL_DEBT.md` item 24 discloses a genuine pre-existing finding:
  `StyleProfileRepository`/`StyleProfile` (Phase 3) is fully built and
  Hilt-bound but has zero production callers — confirmed while deciding
  what Onboarding's Style step should (and should not) show.

## [Unreleased] — M15

### Added

- **User Profile screen** (`feature:settings/profile/`): edit and persist
  your display name and avatar photo. Validated (trimmed, rejects blank,
  50-character maximum, Unicode-safe), survives app restarts, and reflects
  everywhere the name is already shown (Home's greeting) immediately.
  Reachable via a new avatar button in Home's header. Also surfaces a
  compact real summary of AI provider and sync status, linking to the
  existing AI Providers, Wardrobe Sync, and Style Preferences screens
  rather than duplicating them.
- **Home dashboard**: a Recent AI Activity feed (real `ai_call_log`
  entries — garment analyzed, outfit styled, try-on generated, etc. —
  absent entirely until something has actually run), a Cloud AI
  configuration nudge (shown only when every capability is genuinely
  on-device), and an "AI Wardrobe Assistant" header treatment.
- `AiProviderSettingsRepository.observeRecentActivity(limit)` — a new,
  real, chronological read over the existing `ai_call_log` table.

### Documentation

- `docs/adr/ADR-014-m15-user-profile-and-ai-home.md` records the
  persistence/layering decisions behind both features.
- Corrected `feature:settings/README.md`'s stale Phase 7 description of
  the `profile/` package (it described a style-preference screen that
  was actually already built elsewhere, under a different name).

## [Unreleased] — RC2

### Fixed

- **Security**: `GeminiAdapter` sent the user's real Gemini API key as a
  `?key=` URL query parameter; combined with an unconditional
  `HttpLoggingInterceptor` at `Level.BASIC` on the shared `OkHttpClient`,
  every real Gemini call wrote the key to Logcat in every build variant.
  Now sent via Gemini's own documented `x-goog-api-key` header instead —
  the key no longer appears in the URL at all.
- `GenericRestAdapter` hung permanently (never completed its caller's
  awaited result) if a self-hosted backend returned a malformed/truncated
  `resultImageBase64` value — `Base64.decode`'s `IllegalArgumentException`
  was uncaught at every layer. Now caught and turned into a graceful
  failure, the same as an undecodable image already was.
- `AiJobManager`'s own request-coalescing (added in Beta 1) had an
  uncaught side effect: `DefaultAiGateway` recorded a duplicate cache
  write and a duplicate metric event for a "joined" caller of a coalesced
  request, double-counting cost/latency/success telemetry for calls that
  only happened once. Now only the owning caller records the one-time
  side effect.
- `OrphanedImageCleanupWorker`'s daily orphan sweep raced against
  `ImageRepositoryImpl.commitStagedImage` (which moves a garment's files
  into place before inserting their database rows) — a real, if
  narrow, window where the sweep could delete a just-saved photo before
  its row existed. Now requires a file to be both unreferenced and at
  least 60 minutes old before treating it as an orphan.

### Documentation

- Corrected a false claim in `README.md` and `PRODUCTION_VALIDATION_REPORT.md`
  ("zero suppressions project-wide") — a full-repo audit found 23
  pre-existing, individually-justified `@Suppress`/`@SuppressLint` sites
  (Compose parameter counts, Room DAO column counts, an SDK-version-gated
  deprecated API, intentional broad-exception boundaries). Cataloged in
  the new `CODE_HEALTH_REPORT.md`.
- Added `SECURITY_AUDIT_RC2.md`, `PERFORMANCE_AUDIT.md`,
  `CODE_HEALTH_REPORT.md` (all new); updated `PRODUCTION_VALIDATION_REPORT.md`
  and `TECHNICAL_DEBT.md`.

## [Unreleased] — RC1

### Added

- Unified, vendor-neutral AI Gateway architecture (`core:ai`): provider
  adapters (OpenAI, Azure OpenAI, Gemini, Claude, OpenRouter, Ollama,
  Generic REST), a WorkManager-backed job manager, a multi-stage result
  cache keyed by image/capability/provider/model/prompt version, prompt
  versioning, structured metrics, a mandatory privacy-preprocessing gate
  (EXIF-safe re-encoding, face blurring, resizing), and Android
  Keystore-backed encrypted API key storage.
- Cloud paths for all five AI capabilities: Garment Extraction,
  Reconstruction, Metadata, Outfit Styling, and Virtual Try-On — each
  on-device by default, each with automatic fallback if cloud isn't
  configured or a dispatch fails.
- Cloud Outfit Styling: an optional cloud-suggested outfit, always
  validated against the real wardrobe and existing styling rules before
  it can be shown.
- Cloud Virtual Try-On: an optional cloud-rendered try-on with a side-by-
  side Original/On-Device/Cloud comparison viewer.
- Premium Add-to-Wardrobe review screen: segmented image viewer with
  pinch-zoom/pan, a 4-stage comparison strip, confidence-tiered attribute
  suggestions, per-value provenance, quality warnings, and one-tap retry.
- Settings → AI Providers screen: per-capability mode/vendor/consent
  configuration, Test Connection, and an AI Usage panel.
- `README.md`, `SECURITY_AUDIT.md`, `KNOWN_LIMITATIONS.md`,
  `BETA_TEST_GUIDE.md`, `RELEASE_NOTES.md`, `PRODUCTION_VALIDATION_REPORT.md`
  (all new).

### Fixed

- `DefaultAiGateway`'s multi-image cache key previously hashed only the
  first image — harmless for single-image capabilities, but meant two
  different garments tried on the same body photo could have collided in
  the Try-On cache. Now combines every image's hash.
- Four Hilt dependency-injection defects in `core:ai` (duplicate
  `WorkManager`/`OkHttpClient`/`Retrofit`/`Json` bindings; three missing
  `@Binds` for `PersonRegionMasker`/`PrivacyPreprocessor`/`FaceBlurrer`)
  that only surfaced once the full `:app` Hilt graph was compiled.
- A pre-existing flaky test (`WardrobeDatabaseSeedTest`) that failed only
  under full-build parallel load, root-caused to a test synchronizing on
  the wrong dispatcher rather than the database's own result stream.
- Virtual Try-On comparison-preview scratch files (`cacheDir`) were never
  cleaned up — now swept by the existing periodic cleanup worker.
- The AI Gateway's result cache (rows and files) grew without bound —
  now retained for 30 days, then swept by the same worker.
- The encrypted API key store was not excluded from Android's
  device-transfer data-extraction channel, risking an
  `EncryptedSharedPreferences` read failure on a new device (its master
  key can't itself transfer). Now excluded — a new device correctly
  starts with no stored key rather than an undecryptable one.

### Removed

- `org.junit.jupiter:junit-jupiter` (JUnit5) — declared in the version
  catalog but never actually used by any module; every test in this
  project uses JUnit4.

### Security

- Full security audit performed (`SECURITY_AUDIT.md`): API key handling,
  temporary-file lifecycle, cache isolation, exported components, network
  security config, and cloud-payload privacy preprocessing all reviewed;
  zero critical issues found (three real hardening fixes, listed above,
  applied).
