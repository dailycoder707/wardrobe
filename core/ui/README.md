# :core:ui

Shared composables that carry app-specific *behavior*, not just styling (that's
`core:designsystem`) — loading/error/empty state rendering, the garment tile,
navigation dock, and toast/confirmation host used across feature modules.

## Packages
| Package | Contents |
|---|---|
| `components/` | `GarmentTile` (the core browsing unit — resting/pressed/selected states, favorite toggle), `EmptyState`, `LoadingShimmer`/`ClosetGridSkeleton`, `WardrobeFilterChip`, `NavigationDock`, `ConfirmationToast`, `SectionHeader` |
| `debug/` | `RecompositionTracker` — a `SideEffect`-based per-composable recomposition counter, read by `feature:closet`'s Developer Panel (Phase 5c). `OutfitBuilderDiagnostics` (Phase 5d), `StatsDiagnostics` (Phase 5e), and `RecommendationDiagnostics` (Phase 6 — last generation time, suggestion count, top score, active rule count, active flow subscriptions; extended Phase 7 with weather source, weather cache age, rules-applied count, planned-outfit-used flag, and context notes, all bundled into one `RecommendationRunDiagnostics` parameter rather than growing `recordGeneration`'s own parameter list past detekt's `LongParameterList` threshold) — plain object singletons bridging each feature module's own ViewModel state to the same Developer Panel, without a `feature:outfits`/`feature:stats` → `feature:closet` module dependency |

Coil is configured as an `api` dependency here — every garment thumbnail/cutout in
the app loads through `GarmentTile`'s `AsyncImage`, so caching behavior is
consistent everywhere rather than reconfigured per screen. No `coil-network-*`
artifact: every image this app displays is a local file, never a remote URL.

Compose UI tests run under Robolectric (`testDebugUnitTest`, no device/emulator in
this environment) — requires both `debugImplementation(compose.ui.test.manifest)`
and `testOptions.unitTests.isIncludeAndroidResources = true` together, or
`createComposeRule()` fails to resolve a host `ComponentActivity`. See
`phase-5c-wardrobe-experience.md`'s testing-strategy section.
