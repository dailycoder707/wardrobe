# ADR-021: App-Wide Visual Polish, Accessibility & Production Hardening (M22)

**Status**: Accepted (implementation milestone, added 2026-08-08, closing pass
over M13–M21's shipped features rather than adding new ones)

## Context

M22's brief spans the entire application across 17 parts — visual
consistency, every major screen, accessibility, responsive/tablet layout,
dark/light theme, state-handling standardization, performance, security,
real-device validation support, and testing. Per its own instructions,
work began with six parallel research passes (foundational docs/ADRs, the
`core:designsystem`/`core:ui` component inventory, Home/Profile/Onboarding/
navigation, Closet/Add-Garment, Recommendations/Calendar/Insights, and
accessibility/security/performance) rather than assuming what needed
fixing. That research found the application already close to
production-ready — no fabricated AI, no fake data, consistent filter
semantics, real error handling in most (not all) places, a real
component library already in active reuse. This ADR records the
14 real, evidence-backed fixes this milestone made, the 3 findings that
looked like bugs but investigation showed were not, and the items
deliberately left for a future pass with the reasoning for each.

## What was already correct (confirmed, not touched)

- **Closet filtering**: OR within a facet, AND across facets, exactly as
  documented (`GarmentFilterMatching.kt`, ADR-016). Active filters are
  always visible via a dedicated chip row; a zero-result state already
  offers both "Clear filters" and "Modify filters" (`EmptyState`'s
  `secondaryActionLabel`, an M17 addition per its own KDoc).
- **Add Garment/AI Review**: AI-in-progress is a real discrete stage
  machine, never a fabricated percentage; confidence tiers are
  text-backed, not color-only; "Unknown — please choose" vs. "N/A" is
  already icon-and-text distinguished (`MissingFieldRow`); provenance
  (provider/model/confidence/cache-hit) is genuinely reachable per
  suggestion and in aggregate (`AiStatusCard`).
- **Recommendations**: a rule-engine outfit can never render the "AI
  Styled" badge — `ScoredOutfit.provenance` is only ever non-null on the
  real cloud path (`OutfitAssembler` never sets it), confirmed by tracing
  the actual construction site, not just the label text.
- **Profile/Onboarding**: exactly one source of truth for name, avatar,
  AI provider config, and stylist preferences — confirmed by finding
  every write path (`PersonalizationRepository`, `AiProviderSettingsRepository`,
  `StylistPreferencesRepository`) and their sole implementations. Name
  validation (`NameValidation.kt`) is shared between Profile and
  Onboarding, blank/too-long/Unicode-safe by design (UTF-16 code-unit
  length, not grapheme-unaware truncation).
- **Accessibility groundwork**: ~30 spot-checked `IconButton`s across
  6 feature modules all had real `contentDescription`s; the Calendar
  month grid and Insights `CalendarHeatmap` both already have per-cell
  `contentDescription`; AI confidence is already text-backed, not
  color-only.
- **Security**: zero `Log` calls anywhere in `core:ai`; every vendor
  adapter sends its API key via header, never a URL query param (the
  RC2 fix); cloud AI consent is a real, explicit `ConsentDialog` gated
  by `AiProviderConfig.isCloudReady()` before every dispatch; no
  analytics or crash-reporting SDK exists anywhere in the project;
  backup/data-extraction rules already exclude the database, images,
  DataStore, and the Keystore-bound API key file specifically.
- **Performance**: the Closet grid and Insights lists already use
  `LazyVerticalGrid`/`LazyColumn`, not an unbounded `Column` + `.map`;
  no ViewModel was found collecting the same Flow twice.

## What M22 actually fixed (14 real, evidence-backed issues)

1. **`HomeViewModel.assistantState` had no error handling at all** — a
   single throwing suspend call (e.g. a weather fetch inside
   `buildDailyBrief`) would leave an unhandled exception in a bare
   `viewModelScope.launch`, which crashes the app rather than leaving
   Home's assistant cards absent. `loadAssistantState()` now wraps its
   body in try/catch (re-throwing `CancellationException`, degrading to
   `isLoading = false` on any other failure); the three live `collect`
   loops (`observeRecentAiActivity`, `observeActiveAiOperation`,
   `observeSyncCompletion`) each gained a `.catch { }` so a transient
   repository failure skips that update instead of killing the
   collector. Regression test: a `FakeWardrobeIntelligenceRepository`
   configured to throw proves the app degrades instead of crashing.
2. **`AttentionItemsCard` conflated "0 items need attention" with "not
   loaded yet"** — `itemsNeedingAttentionCount` was a non-nullable `Int`
   defaulting to `0`, unlike `wardrobeHealthScore`'s correct `Int?`
   null-means-unknown pattern right next to it. Now nullable; the card
   is absent while `null`, exactly like its sibling.
3. **`showWardrobeHealthCard`'s toggle didn't actually gate
   `WardrobeHealthScoreCard`** — it only gated the differently-named
   `WardrobeSummaryCard`, so turning the toggle off left the health
   score card showing anyway. Now both respect the one toggle.
4. **Home's card titles used three different type scales** with no
   shared convention (`SectionHeader`'s `displayMedium`, "Recommended
   Outfit" at `titleMedium`, "Running fully on-device" at `titleSmall`).
   The latter now matches `titleMedium`.
5. **Import queue's retry button used the Close icon glyph** — reads as
   "dismiss," not "try again." Now `Icons.Filled.Refresh`, matching the
   review screen's own `RetryButton` iconography.
6. **A lost staged image silently closed the review screen** with zero
   explanation (`needsRestage` → immediate `onDone()`). The item
   genuinely does resume from the queue on its own; this only makes
   that visible via a brief `RestageNoticeOverlay` instead of the screen
   just vanishing.
7. **`ClosetGridSkeleton` was built specifically for this screen
   (matching its own column count) but never wired in** — `ClosetScreen`'s
   loading state rendered a generic centered spinner instead. Now uses
   the real skeleton.
8. **AI-suggested vs. user-entered values were indistinguishable once
   bound into the actual editable form controls** in Add Garment review
   — the "Detected attributes" summary already showed this distinction,
   but a Brand dropdown auto-filled from a HIGH-confidence suggestion
   looked pixel-identical to one typed by hand once you scrolled past
   that summary. `DropdownField` (`core:ui`) gained an additive,
   backward-compatible `helperText: String? = null` parameter; Garment
   Review's dropdown/attribute fields now show "AI suggested" under a
   field whenever its *current* value matches a real suggestion —
   reusing `isSuggestionApplied` (the exact function the summary section
   already uses), so the caption disappears the instant a user edits the
   field away from the suggestion, with no new "applied" concept
   invented.
9. **Recommendations' empty state conflated two different situations**
   into one message ("Not enough wardrobe items for a complete outfit")
   — a genuinely empty wardrobe and a wardrobe with items that just
   aren't enough. `RecommendationsUiState` gained `hasNoGarments`
   (computed from the same `ref.garmentsById` the screen already loads);
   the empty state now shows honestly different copy for each case.
10. **Calendar's top-level `uiState` combine had no error boundary** —
    unlike Recommendations' own `isError` state for the same class of
    failure, a throwing repository flow here left the screen stuck on
    its initial loading spinner forever. Added the same `.catch { }` →
    honest `EmptyState` pattern, with a regression test proving a
    repository failure surfaces a real, retryable-by-navigation message
    instead of an infinite spinner.
11. **`WardrobeFilterChip`'s selected state was color/fill-only** for
    sighted, non-screen-reader users (screen readers already had the
    real signal via its existing `role = Role.Checkbox` + explicit
    `contentDescription`). Added a small checkmark icon when selected —
    this component backs `MultiSelectChips` too, so the fix reaches
    Closet's filter chips, Calendar's occasion chips, and Edit Garment's
    multi-selects for free, without touching any of those call sites.
12. **11 call sites across 8 files used lifecycle-unaware
    `collectAsState()`** instead of this project's own established
    `collectAsStateWithLifecycle()` convention (61 correct call sites
    already existed) — each kept collecting its Flow while the screen
    was backgrounded. All 11 fixed to the established pattern.
13. **`HttpLoggingInterceptor` ran unconditionally in every build
    variant**, including release — a defense-in-depth gap explicitly
    disclosed as "not applied" in `TECHNICAL_DEBT.md`'s RC2 upgrade
    checklist (the actual security-critical issue, a leaked API key in
    a logged URL, was already fixed at RC2 by switching to header auth).
    Both instances (`core:ai`'s `AiNetworkModule`, `core:data`'s weather
    `NetworkModule`) now check `ApplicationInfo.FLAG_DEBUGGABLE` via the
    already-injected `@ApplicationContext` and only attach the
    interceptor on debug builds — no new `BuildConfig` needed in either
    library module.
14. **Try-On render decoded two full-resolution bitmaps and ran the
    on-device compositing engine on the Main dispatcher** —
    `VirtualTryOnRenderRepositoryImpl.render()` had no dispatcher switch
    anywhere in its call chain, and its only real caller
    (`TryOnCompareViewModel`) invokes it twice in sequence from a bare
    `viewModelScope.launch`. Wrapped the method body in
    `withContext(Dispatchers.IO)`, matching this repository layer's own
    existing convention for file-and-bitmap work (e.g.
    `GarmentMaskRepositoryImpl`).

A fifteenth issue was found and fixed during this milestone's own
verification, not the original research: the new `WardrobeFilterChipTest`
initially failed because the chip's `clickable` modifier merges its
descendants' semantics into one screen-reader-facing node (correct,
intentional behavior) — the test needed `useUnmergedTree = true` to query
the decorative checkmark icon directly. Fixed in the test, not the
component.

## Investigated and confirmed *not* bugs (reasoning recorded so they aren't rediscovered)

- **`AiActivityBanner` is used in only 2 of ~9 screens showing AI
  activity.** Investigation found `AiActivityBanner`'s actual charter is
  narrower than "any AI-related spinner": every existing use (Home,
  Recommendations) shows it *alongside already-visible content* during a
  background operation. Every screen the research flagged as "missing"
  it (Capture, Import Queue, Try-On, Recommendations' own initial load)
  uses a plain `CircularProgressIndicator` for a genuine *first-load,
  nothing-to-show-yet* state — the same pattern Recommendations' own
  `isLoading` branch already uses. Forcing the banner into more screens
  would have been inconsistent with the flagship AI screen's own
  precedent, not a fix for one.
- **Insights' bar-chart sections don't all hide identically when
  "empty."** `WardrobeMixSection` hides its whole card when a list is
  genuinely empty; `DistributionSections`/`ActivityChartSections` always
  show their card header even when the inner chart renders nothing. On
  inspection this isn't the same situation: Season/Dress-Code/Weekday
  distributions are always built from a fixed enum (`Season.entries`,
  `DressCode.entries`), so they can never truly be "no data" — a bar at
  zero height is a real, honest measurement ("no wears this season"),
  not a missing one. `WardrobeMixSection`'s lists (materials, fabrics,
  occasions) are genuinely variable-length and can be legitimately
  empty. No change made.
- **Calendar's Planned/Worn distinction uses different techniques on
  different surfaces** (the month grid: filled vs. outline dot, shape
  only; Day Detail: "Planned"/"Worn" text, no color). Both already
  satisfy "not color-only" independently — the month grid's tiny cells
  can't fit a text label, and Day Detail already has room for real
  words. Making them identical would mean either cramming text into a
  cell too small for it or losing the grid's at-a-glance dot pattern;
  left as two surface-appropriate techniques rather than forcing one
  visual language onto both.

## Deliberately deferred (disclosed, not silently dropped)

- **No shared `Spacing`/`Dimens` token object was introduced.** Research
  found 340+ hardcoded `.dp` literals across 73 files with no shared
  scale — a real DRY gap, but migrating all of them is a large,
  purely-mechanical, low-visual-risk refactor (the values are already
  visually consistent multiples of 4dp) disproportionate to a targeted
  polish pass. Left for a dedicated future pass rather than a partial,
  inconsistent migration in this one.
- **No shared `Dialog`/`BottomSheet`/`TopAppBar` wrapper was
  introduced**, despite 104 raw Material3 usages across 41 files. Each
  usage already renders with consistent Material3 defaults — this is a
  maintainability observation, not a visible inconsistency, and adding
  a new wrapper component risks being exactly the "new visual system"
  this milestone was explicitly told not to introduce.
- **Garment save's full-screen checkmark overlay vs. Outfit save's small
  toast were not unified.** Both are deliberate, already-documented
  choices from different milestones (the garment overlay traces to an
  explicit "premium finish" acceptance criterion). Unifying them would
  mean overriding an established, working, intentional design decision
  with no real bug driving the change — left alone per this milestone's
  own "if something is already correct, leave it alone" instruction.
- **Cache-hit disclosure differs in format** (dialog-gated in
  Recommendations vs. always-inline in Calendar's Day Recommendation
  sheet) — a persistent card benefits from keeping secondary detail
  behind a tap; a one-shot bottom sheet has room to show it inline.
  Different information contexts, not an unconsidered inconsistency.
- **7 `PersonalizationRepository` setters
  (`setGreetingStyle`/`setCustomHomeTitle`/`setShowGreeting`/
  `setShowWeatherCard`/`setShowRecommendationCard`/`setShowWardrobeHealthCard`/
  `setShowInspirationCard`) have no UI anywhere that calls them** outside
  test fakes — real end-to-end scaffolding with no screen surfacing it.
  Building a "Home customization" settings section is a new feature, not
  polish; recorded as a known gap rather than built here.
- **`WeatherSettingsScreen`'s two `IconButton`s use `Text("−")`/`Text("+")`**
  instead of `Icon`s — real but very low-impact (TalkBack still
  announces the text correctly); not fixed to keep this pass focused on
  higher-value findings.
- **RTL, TalkBack, and physical tablet/multi-width rendering** cannot be
  verified in this development environment — see the real-device
  checklist in the final report.

## Consequences

- **No database migration, no new AI capability, no new dependency.**
  Every fix above is either a bug fix (behavior was wrong) or an
  additive, backward-compatible API change (`DropdownField.helperText`).
- **`DropdownField`'s new parameter is used by exactly one caller**
  (Garment Review) today; every other existing call site is unaffected
  (default `null` renders identically to before).
- **`WardrobeFilterChip`'s checkmark reaches every screen using it or
  `MultiSelectChips`** without those call sites changing — the fix lives
  entirely in the one shared component, per this milestone's own "fix
  shared components, not per-screen copies" instruction.
- Real bugs found and fixed: 14 in application code, plus 1 in this
  milestone's own new test (found during verification, not shipped).
- Full test suites for every touched module pass; no test was deleted,
  disabled, or weakened to get there.
