# :core:common

Small, boring, shared utilities that need Android (unlike `core:model`/`core:domain`) —
this is why it's an Android library, not a `kotlin("jvm")` module.

## Packages
| Package | Holds |
|---|---|
| `di/` | Coroutine `CoroutineDispatcher` qualifier annotations (`@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`) used across modules so no repository/use case ever hardcodes `Dispatchers.IO` directly |
| `result/` | `AppError` sealed hierarchy (`Recoverable` / `NonRecoverable`) and the `Result<T, AppError>` wrapper repositories return — Phase 1 Section 25 |
| `util/` | Date/unit conversion helpers (metric↔imperial, °C↔°F) — Phase 1 Section 29: these are a Settings preference, never locale-inferred |
| `logging/` | A thin `Logger` interface wrapping Timber, so the underlying logging library is swappable without touching call sites — Phase 1 Section 26 |

Nothing is implemented yet.
