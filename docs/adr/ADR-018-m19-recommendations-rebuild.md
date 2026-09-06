# ADR-018: Recommendations Rebuild (M19 Part 9)

**Status**: Accepted (implementation milestone, added 2026-08-08, extending
ADR-012's provider/router architecture and M18's real AI-activity signal)

## Context

M19's brief asked for the Recommendations experience to genuinely answer
"What should I wear today?" — and, where the underlying data supports it,
"Why this?", "What alternatives do I have?", "What if context changes?" —
using only real wardrobe/context/AI data, with an explicit instruction not
to rebuild what already works. Before writing any code, this milestone's
own instructions required inspecting 27 named pieces of existing
architecture (`OutfitAssembler`, `RecommendationRuleEngine`,
`ContextScoring`, `StylingEngineRouter`, `CloudOutfitValidation`,
`ContextResolution`, `RecommendationExplainer`, and more) before touching
anything. That inspection found the on-device/cloud engine, its whole-
outfit validation, and its explanation text were already real, already
rigorous, and already reused identically by both paths — the actual gap
was narrower than the brief's "rebuild" framing implied: four concrete
missing capabilities, plus UI-side polish that re-presents data the engine
already computed.

## What already existed (not rebuilt)

- **`OutfitAssembler.generateRecommendations(input, count, anchorGarment)`**
  — already returns `count` genuinely distinct candidates per call via a
  `variation` index fed into `pickNthSmart`, not a single best guess. No
  second engine was needed for "Show another."
- **`CloudOutfitValidation.validateCloudOutfit(...)`** — every cloud-
  returned outfit already passes the *same* `passesWholeOutfitRules()` the
  on-device path uses, plus garment-existence/slot/duplicate/weather/
  anchor checks, before it ever becomes a `ScoredOutfit`. Cloud already
  could not bypass validation; no code change was needed here.
- **`RecommendationExplainer.buildExplanation(...)`** — already builds
  real, deduped, non-generic explanation text from `ScoredCandidate.reasons`
  (max 3 sentences), with an honest fallback when no reasons exist. M19
  only re-presents this text as a bulleted "Why this?" list, never
  generates new copy.
- **`AiStyledBadge`/`RuleBasedBadge`** (M18) — the AI-vs-rule-engine
  distinction and cache-hit disclosure already existed and needed no
  change.
- **`StylingEngineRouter`/`CloudStylingEngine`** — cloud dispatch already
  sends the wardrobe as text (never images), already returns `null` (not
  an empty list) to distinguish "cloud unusable" from "cloud returned
  zero," and `count` was already `.take(count)`-only, meaning a larger
  "Show another" request causes no extra cloud calls.

None of the above was rebuilt or touched beyond what's listed under
"Changed" below.

## What M19 actually added

### 1. `count` — real "Show another," not client-side repetition

`StylingEngineRepository.suggestOutfits` gained
`count: Int = DEFAULT_SUGGESTION_COUNT` (default `3`, matching
`core:data`'s own existing internal constant by construction), threaded
through `StylingEngineRepositoryImpl` and `StylingEngineRouter` into
`OutfitAssembler`'s already-existing `count` parameter. The ViewModel's
`showAnother()` requests `requestedCount + 3` (capped at 9) and diffs the
new results' garment-ID signatures against what's already on screen —
only advancing if a genuinely different outfit appears. When the
wardrobe's own candidate pool is exhausted (`pickNth`'s clamping behavior
means requesting more than the pool holds just repeats the worst-ranked
candidate), the UI says so honestly: *"No other complete outfit matches
this context with your current wardrobe."* — never a silent repeat.

### 2. `SuggestionContext.occasionId` — a dead field made real

The field existed on `SuggestionContext` since M9 but was never read
anywhere. `ContextResolution.resolvePlannedOccasionDressCode` gained an
`explicitOccasionId` parameter, checked before the existing
calendar-derived (`WearEventStatus.PLANNED`) fallback — an explicit
occasion selection always wins over what's on the calendar. This reuses
`Occasion.impliedDressCode()`'s existing keyword mapping and
`plannedOccasionFactor`'s existing scoring bonus; no new scoring logic.
The Recommendations screen's new `TodaysContextHeader` exposes an
occasion chip row (including "Any occasion" → `null`) that calls
`onOccasionSelected`, which updates state and triggers a real recompute
— proven by tests asserting the engine's `SuggestionContext.occasionId`
actually changes, not just the UI chip.

### 3. An honest error state

`RecommendationsUiState` gained `isError`/`errorMessage`. Any failure
inside the recommendation fetch (weather lookup, engine call, cloud
parsing) — previously uncaught, meaning a real failure would either crash
or silently produce a stale/empty screen — now lands on a dedicated error
card ("Something went wrong" + "Try again"), never a fabricated
recommendation. The boundary catch is broad by necessity (an engine call
can fail for reasons this screen can't enumerate in advance) and is
`@Suppress`-documented with the same broad-catch-at-a-boundary precedent
already used in `WeatherRepositoryImpl`/`SyncEngine`/`SyncRepositoryImpl`.

### 4. Try On, wired to the existing flow

The outfit-actions row gained a "Try On" button next to the existing
"Preview this look," passing the same `List<Long>` garment-ID selection
`WardrobeNavHost` already used for `onOpenPreview` into the existing
`TryOnRoute`. No new Try-On flow — this is routing, not a rebuild.

### 5. UI polish that re-presents real data, not new signals

- Weather line on the context header: `WeatherSnapshot.headline(...)`
  (already existed) — the ViewModel fetches weather once and threads the
  same `WeatherSnapshot` into both scoring (`SuggestionContext.weather`)
  and display, so there is no duplicate weather fetch per screen load.
- Suggestion tabs relabeled "Best Match" / "Alternative N" (was "Look N"),
  matching the assembler's own best-first ordering.
- "Why this?" renders `RecommendationExplainer`'s existing explanation
  text as a bulleted list (`String.toReasonBullets()`, a pure
  sentence-split with no fabrication — tested against multi-sentence,
  single-sentence, and empty input).
- A live "Using Cloud AI to style your outfit…" banner, reusing M18's
  `AiActivityBanner` and the same `observeActiveOperations()` signal Home
  already uses, filtered to `AiCapability.OUTFIT_STYLING` — never shown
  for the rule-engine path.
- Empty-wardrobe state copy tightened to "Not enough wardrobe items for a
  complete outfit" + "Add garments," distinct from the new error state.

### 6. Deliberately NOT touched — and why

- **Parts 10–13** (Calendar, Insights, app-wide visual polish, and the
  remainder of the wider "AI Wardrobe Assistant" epic) — out of scope per
  the brief, untouched.
- **`suggestForItem`** — confirmed zero production callers; left without a
  `count` parameter since nothing calls it with one.
- **No new recommendation engine, no new AI activity tracker, no new
  wear-history table** — every "Show another"/context-recompute/AI-status
  capability above reuses an existing mechanism rather than adding a
  parallel one.

## Consequences

- `StylingEngineRepository.suggestOutfits` is a signature change (new
  parameter, defaulted) — every implementation (`StylingEngineRepositoryImpl`,
  `StylingEngineRouter`) and both `FakeStylingEngineRepository` test doubles
  (`feature:outfits`, `feature:closet`) were updated, not weakened.
- `RecommendedOutfitUiModel` gained a required `reasonBullets` field — the
  one other direct-construction test site (`AiStyledBadgeTest`) was updated
  accordingly.
- `RecommendationsViewModel`'s constructor and function count grew past
  detekt's default thresholds from the new context/weather/AI-activity
  dependencies; state-transition and side-effect helper functions were
  extracted to file-scope functions in `RecommendationActions.kt` and
  `RecommendationsViewModelSupport.kt` (mirroring this file's own
  pre-existing `persistSelectedOutfit`/`logOutfitWear` pattern) rather than
  suppressed away, keeping the class itself down to its 10 genuinely
  stateful methods. The constructor's `@Suppress("LongParameterList")`
  matches the same precedent already used for other multi-repository
  ViewModels in this codebase (e.g. M18's `ClosetViewModel`).
- Genuine, disclosed limitation: "Show another" grows the request in fixed
  steps of 3 up to a cap of 9 — a caller who wants a 10th distinct outfit
  when the wardrobe supports it will not get one from this screen.
  Recorded in `TECHNICAL_DEBT.md`.
- No database migration — every new capability reuses existing tables,
  existing scoring, and existing validation.
