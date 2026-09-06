# ADR-019: Calendar & Outfit Planning (M20 Part 10)

**Status**: Accepted (implementation milestone, added 2026-08-10, extending
M19's recommendation architecture, M18's AI transparency signal, and Phase
5d/8/9's existing Calendar feature)

## Context

M20's brief read as if a calendar/outfit-planning feature did not yet
exist — asking for a "Calendar/Planner screen," a persisted planning
model, and a full lifecycle of plan/replace/remove actions to be built
from scratch. Before writing any code, this milestone's own instructions
required inspecting 12 named infrastructure areas first. That inspection
found something the brief's framing did not anticipate: **a complete,
already-shipping Calendar feature module** (`feature:calendar`, built in
Phase 5d and extended in Phase 9), already on the bottom navigation dock,
already backed by real persistence, with month/list views, day detail,
log/plan wear, reschedule, duplicate day, recurring weekly scheduling, and
conflict-detection badges. This ADR records what already existed, the four
genuine gaps M20 actually filled, and why the rest of the brief's
20-phase scope was already satisfied.

## What already existed (not rebuilt)

- **The "calendar entry" model** — `WearEvent` (`core/model/.../wear/WearEvent.kt`),
  with `status: WearEventStatus { PLANNED, WORN }` (added Phase 5d).
  "Planning an outfit for a date" has always meant inserting a `WearEvent`
  row with `status = PLANNED`, referencing either a `garmentId` or an
  `outfitId` (XOR-enforced by the model's own `init` block) — never a
  duplicated snapshot of garment data. No new domain model was needed for
  Part 1.
- **Persistence** — `WearEventEntity`/`wear_events` (DB version 9, already
  current; no migration needed for M20 since every field M20 needed —
  `occasionId`, `weatherCacheId` — already existed on the entity, simply
  never written by the Calendar UI itself). `WearEventDao`/`WearEventRepositoryImpl`
  already provide `observeEvents(DateRange)`, `logWear`, `updateWear`,
  `deleteEvent`, `clearDay`, `duplicateDay` — exactly Part 3's required
  operation set.
- **The Calendar screen itself** — `CalendarScreen.kt`, `CalendarViewModel.kt`,
  `MonthGrid.kt` (a real selectable month grid with worn/planned/conflict
  dot indicators, Part 4), `DayDetailPanel.kt`, `LogWearSheet.kt` (pick a
  saved outfit or an individual garment, Part 5), `WearHistoryList.kt`, a
  Material3 `DatePickerDialog` already used for reschedule/duplicate-day.
  Already registered as `CalendarRoute`, already a bottom-nav destination
  (`app/.../navigation/WardrobeNavHost.kt`) — Part 13's navigation
  requirement was already satisfied.
- **Recurring scheduling** — deliberately materializes real `PLANNED` rows
  for 8 weeks rather than a persisted recurrence-rule engine (a Phase 5d
  scope decision, unchanged by M20).
- **Conflict detection** (Phase 9) — `WardrobeIntelligenceRepository.observeCalendarConflicts`
  flags duplicate-planned-outfit / laundry / trip-packing conflicts as a
  subtle dot on the month grid, already wired.
- **Sync** — `WearEvent` was already fully sync-registered
  (`WearEventSyncHandler`, `SyncEntityType.WEAR_EVENT`) before M20, and its
  wire format (`WearEventWire`) already carries `occasionSyncId` — Part 15
  needed zero new sync code; M20's occasion-assignment feature (below)
  syncs across devices automatically because the field was already part
  of the wire format.
- **Home's "upcoming plan" surface** — `HomeInsightsUiModel.upcomingOutfitLabel`
  (M15/M16), built from a real `wearEventRepository.observeEvents(DateRange(today, today+30d))`
  query, already shown as a "Coming up" insight chip. Part 13's optional
  Home card was already satisfied by real data; adding a second,
  redundant "upcoming plan" surface would have been exactly the kind of
  duplication this milestone's own instructions warned against.

None of the above was rebuilt or touched beyond what's listed under
"Changed" below.

## What M20 actually added

### 1. Recommend for this date (Part 6) — reusing the exact M19 engine

`CalendarViewModel` gained `StylingEngineRepository`, `WeatherRepository`,
and `StatsRepository` dependencies (plus `Clock`, see the bug-fix section
below). A new `CalendarRecommendationActions` class (mirroring the
existing `CalendarEventActions` split) calls
`stylingEngineRepository.suggestOutfits(SuggestionContext(date, weather,
occasionId), count)` — the identical M19 entry point `feature:outfits`'
Recommendations screen uses. **No second recommendation engine.** A new
`DayRecommendationSheet.kt` (bottom sheet) previews the result: thumbnail
row, real explanation text, and a provenance label mirroring
`feature:outfits`' exact "AI Styled" / "Styled from your wardrobe
preferences and today's context" wording (Part 9) — duplicated as a few
lines of `Text`, not a shared component, since `feature:calendar` cannot
depend on `feature:outfits` (ADR-010's layering rule); this is a
deliberate, disclosed, presentation-only duplication, not a second
architecture.

### 2. "Show another" and "Plan this outfit" (Part 10) — the same dedup discipline as M19

`onShowAnother()` requests more candidates (`requestedCount` grows by 3,
capped at 9, reset on a fresh request) and only advances the shown
suggestion when its garment-ID signature genuinely differs from what's
already displayed — identical mechanism to M19's `RecommendationsViewModel.showAnother()`,
including the exact same honest-exhaustion message
("No other complete outfit matches this context with your current
wardrobe.") when the wardrobe's candidate pool is exhausted.
`onPlanRecommendedOutfit()` persists via the same `OutfitId(0)`-sentinel
save-if-unsaved pattern `feature:outfits`' `persistSelectedOutfit` uses,
then a real `WearEventRepository.logWear`/`updateWear` call — never a
new persistence path.

### 3. Replace (Part 11)

A new "Replace with a new recommendation" icon on a `PLANNED` outfit's
Day Detail row (`onReplaceEvent`) opens the same recommendation sheet in
"replace" mode; "Plan this outfit" then calls `updateWear` on the
existing row (preserving its id, note, `weatherCacheId`, `createdAt`)
instead of inserting a duplicate. Reschedule and Remove already existed
(Phase 5d) and were left unchanged.

**Deleted-outfit handling was found to already be structurally
impossible**, not merely unhandled: `OutfitRepositoryImpl.deleteOutfit`
throws while wear history references the outfit (a `RESTRICT` foreign
key), and `CalendarViewModel`'s own outfit lookup uses
`OutfitFilter(isSaved = null, isArchived = null)` — archived/unsaved
outfits still resolve to a name and thumbnail. Part 11's "handle a broken
outfit reference" concern doesn't apply; no defensive code was added for
a state the schema already prevents.

### 4. Occasion assignment (Part 7)

`WearEvent.occasionId` existed on the model/entity/sync-wire format since
Phase 5d/8 but was never *written* by the Calendar UI (`CalendarEventActions.logWear`
always passed `occasionId = null`). A new occasion chip row in Day
Detail (`OccasionChipRow`, reusing `WardrobeFilterChip`) lets the user
pick one of the real `Occasion` reference rows (`OccasionRepository.observeAll()`,
already loaded by `CalendarViewModel`) or "No occasion." Selecting one
immediately persists it onto every `WearEvent` already logged that day
(`onOccasionSelected`); a day with nothing logged yet remembers the pick
and attaches it the moment something is planned. This directly feeds the
*already-existing* M19 behavior in `ContextResolution.resolvePlannedOccasionDressCode`
(reads the day's `PLANNED` event's `occasionId` as a fallback when no
explicit `SuggestionContext.occasionId` is given) — unchanged, no new
occasion system, satisfying Part 7's explicit instruction to preserve
that exact M19 precedence.

### 5. Weather context (Part 8) — honest, never fabricated

`WeatherRepository.getForecastForConfiguredLocation(date)` was found (via
inspection, not assumption) to genuinely support forecast-quality data
for **today through +6 days** (Open-Meteo's `forecast_days=7`); beyond
that window it silently falls back to cache — potentially a *different*
day's cached snapshot. Calendar distinguishes exactly the three states
Part 8 asked for, using a fact the snapshot itself discloses rather than
a guessed cutoff:

```kotlin
sealed interface DateWeatherUiState {
    data object Unavailable : DateWeatherUiState                 // nothing resolved at all
    data object ForecastUnavailableForDate : DateWeatherUiState   // snapshot.date != the requested date
    data class Available(val summary: String) : DateWeatherUiState
}
```

A snapshot whose own `date` field doesn't match the requested date is
never shown as if it were real data for that date — this is how a
month-ahead selection is told apart from a within-window one, without
hardcoding the provider's 7-day constant into `feature:calendar`. A new
`WeatherSnapshot.dailyHeadline(unit)` extension (`core:model`) was added
alongside the existing `headline(unit)` — `headline()` only reads
`currentTempC`, which is `null` for every date but today, so a future
date needs its own high/low-based line ("18°C–24°C. Rain expected.").

### 6. Wear-history integration (Part 12)

A "Worn recently" / "Not worn recently" label on `PLANNED` events, built
from `StatsRepository.observeCostPerWear()`'s real `lastWornDate` per
garment (already excludes `PLANNED` rows from its own counting — verified
via `StatsRepositoryImplTest`), checked against a 14-day window. `null`
(not a guessed `false`) when no cost-per-wear entry exists for any
relevant garment — never invented. Only computed for `PLANNED` events; a
`WORN` entry already *is* the wear record.

### 7. Notifications/reminders (Part 14) — deliberately not built

No notification infrastructure (`NotificationManager`/`NotificationChannel`)
exists anywhere in the app; every existing `WorkManager` worker
(`SyncWorker`, `WeatherRefreshWorker`, `BackupExportWorker`,
`OrphanedImageCleanupWorker`, `ImageProcessingWorker`, `AiCapabilityWorker`)
is internal/maintenance, none user-facing. Per this milestone's own
explicit instruction ("if notification behavior does not already exist,
leave it out"), M20 does not add reminders. Recorded as deferred, not
silently dropped — see `TECHNICAL_DEBT.md`.

## Consequences

- **No database migration** — every field M20 needed already existed on
  `WearEventEntity` (`occasionId`, `weatherCacheId`), unwritten by the
  Calendar UI until now. DB stays at version 9.
- **No new sync code** — `WearEvent` was already fully sync-registered
  with `occasionSyncId` already part of its wire format.
- `CalendarViewModel`'s constructor gained 4 dependencies
  (`StylingEngineRepository`, `WeatherRepository`, `StatsRepository`,
  and a genuinely new one — `Clock`, see below); `CalendarViewModelTest`
  and its fakes were updated, not weakened, plus 3 new fakes added
  (`FakeStylingEngineRepository`, `FakeWeatherRepository`,
  `FakeStatsRepository`) mirroring `feature:outfits`' own M19 fakes.
- **Real bug found and fixed**: `CalendarViewModel` computed "today" via
  bare `LocalDate.now()`/`YearMonth.now()` rather than the injected-`Clock`
  convention every other date-sensitive ViewModel in this codebase
  follows (`HomeViewModel`, `RecommendationsViewModel`) — a latent
  test-determinism gap that this milestone's own new date-boundary logic
  (recommend-for-date, weather-for-date) made worth fixing while already
  touching this exact class. Now `Clock`-driven throughout; tests use a
  fixed clock, matching the established test convention.
- **Real bug found and fixed**: `ContextResolution.prependPlannedOutfit`'s
  explanation text was hardcoded `"...for today."` — correct for every
  prior caller (all of which only ever called `suggestOutfits` with
  `date == LocalDate.now(clock)`), but wrong the instant Calendar calls
  the same engine with a genuine future date. Now date-aware: `"...for
  today."` only when the date actually is today, `"...for {date}."`
  otherwise. Covered by a new test in `StylingEngineRepositoryImplTest`.
- **Real bug found and fixed**: replacing a still-`PLANNED` "today" event
  with a new recommendation would have silently forced it to `WORN`
  (a plain `date.isAfter(today)` check, ignoring the row's actual prior
  status) — inconsistent with `CalendarEventActions.onRescheduleEvent`'s
  own established precedent of preserving `event.status` when the target
  date isn't in the future. `persistDayRecommendation` now follows the
  same rule.
- Genuine, disclosed limitation: "Show another" caps at `requestedCount = 9`
  (mirrors M19's identical, already-accepted limitation, documented there
  and here for the calendar path too).
- Parts 1–3's own instruction to "extend instead of duplicate" was
  satisfied literally: zero new tables, zero new DAOs, zero new
  repository interfaces — every M20 capability is new logic wired onto
  the pre-existing `WearEvent`/`StylingEngineRepository`/`WeatherRepository`/
  `StatsRepository`/`OccasionRepository` surfaces.
