# :feature:calendar

Wear logging, outfit scheduling, and the calendar views (Phase 5d). Multiple
outfits or individual garments per day, logged without being forced into an
"outfit" wrapper — one of the documented pain points in the source-app
teardown that this product explicitly fixes (Phase 1 Section 4 domain
notes). Also introduces Outfit Scheduling (`WearEventStatus.PLANNED`) on top
of Phase 3's originally retrospective-only wear log.

## Packages
| Package | Contents |
|---|---|
| `calendar/` | `CalendarScreen`/`CalendarViewModel` — month grid (`MonthGrid`), day detail (`DayDetailPanel`), log-wear sheet (`LogWearSheet`), wear history list view (`WearHistoryList`); write actions live on `CalendarViewModel.actions` (a `CalendarEventActions` instance), not on the ViewModel directly |
| `navigation/` | This module's type-safe route (`CalendarRoute`) — Day Detail and Wear History are in-screen panels/toggles, not separate routes |

See `phase-5d-wardrobe-stylist.md` for Calendar's design (month grid, day
detail, log-wear, the recurring-outfit scope decision), Wear History, and
known limitations. `TECHNICAL_DEBT.md` item 9 tracks this phase's deliberate
simplifications.

## Phase 9 additions

`CalendarViewModel` gained a `wardrobeIntelligenceRepository` dependency and
a `conflictsFlow` observing `observeCalendarConflicts(lookAheadDays = 14)` —
three conflict types (a planned outfit repeated across two dates, a planned
garment currently in the laundry, a planned garment packed for a trip that
doesn't cover that date). `DayCellUiModel` gained `hasConflict`, rendered as
a small, non-blocking `ConflictDot()` on the affected day (`MonthGrid.kt`) —
no popup, no dialog. `DayDetailPanel` gained `conflictMessages`, shown as
plain-language error-colored lines when a selected day has one.
