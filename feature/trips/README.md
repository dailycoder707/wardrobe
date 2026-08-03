# :feature:trips

Trip management and packing lists — built from scratch in Phase 9. This
module was registered in `settings.gradle.kts` since Phase 1 as a
placeholder (README only, zero screens); Smart Wardrobe Intelligence &
Daily Assistant gave it its first real UI, per an explicit scope decision
confirmed with the user before implementation (build the screens this
phase, not just the backend generation logic).

Packing suggestions run the same rule-based `StylingEngineRepository` every
other recommendation surface in the app uses — one real suggestion call per
trip day, no travel-specific logic duplicated, no LLM.

## Packages
| Package | Screen(s) |
|---|---|
| `list/` | `TripsScreen`/`TripsViewModel` — trip list (sorted by start date), a `CreateTripDialog` (name, destination, date range via `ISO_LOCAL_DATE` text entry, luggage size) |
| `detail/` | `TripDetailScreen`/`TripDetailViewModel` — trip summary, "Generate Packing List"/"Regenerate" (calls `TripRepository.generatePackingSuggestions` then `savePackingList`), delete |
| `packing/` | `PackingScreen`/`PackingViewModel` — the generated checklist grouped by category (`Day N Outfit`, `Toiletries`, `Reminders`), pack/unpack `Checkbox`es wired to `setPacked` |
| `navigation/` | `TripsRoute` (object), `TripDetailRoute(tripId)`, `PackingRoute(tripId)` |

## Packing generation

`TripRepository.generatePackingSuggestions(tripId)` (`core:data`'s
`TripRepositoryImpl`) calls `StylingEngineRepository.suggestOutfits` once
per trip day, deduplicates the resulting garments across days into
`PackingListItem` rows (`garmentId`-based, tagged `"Day N Outfit"`), and adds
a static, trip-length-scaled toiletries checklist plus a small set of
reminder items (`freeTextName`-based — phone charger, travel adapter, a
carry-on liquid-restriction reminder when `LuggageSize.CARRY_ON`). Pure
generation: `TripDetailViewModel` decides whether to `savePackingList` the
result, preserving the pre-existing "replace the packing list wholesale,
never merge" contract `TripRepository` already had.

Each day's `SuggestionContext` has `weather = null` — there is no forecast
API for a future or distant destination in this app's weather integration,
so trip-day outfits reflect Stylist Preferences/rotation/favorite scoring
only, not weather-appropriateness. See `phase-9-smart-wardrobe-
intelligence.md`'s Known Limitations and `TECHNICAL_DEBT.md` item 15.

## Testing

`TripsViewModelTest`, `TripDetailViewModelTest`, `PackingViewModelTest` (8
tests total) against module-local `FakeTripRepository`/`FakeGarmentRepository`
(`fakes/FakeRepositories.kt`) — trip list sorting and name-fallback, trip
creation/deletion, route-driven trip loading and not-found handling,
packing-list generation-then-save, packing items grouped by category with
garment names resolved, pack/unpack toggling.
