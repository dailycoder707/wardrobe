# :core:testing

Test-only fixtures shared across every module's test source set — fakes, not
mocks-by-default, for the `core:domain` repository interfaces, plus an in-memory
Room database builder for `core:database` tests.

## Packages
| Package | Will hold |
|---|---|
| `fake/` | In-memory fake implementations of `core:domain` repository interfaces (e.g. `FakeGarmentRepository`) — used by ViewModel unit tests across `feature:*` modules |
| `fixture/` | Builder functions for domain models with sane defaults (`garmentFixture { }`) so tests don't hand-construct every field |
| `rule/` | JUnit rules — a `MainDispatcherRule` swapping `Dispatchers.Main` for a `TestDispatcher`, an in-memory-Room `TestRule` |

Exposes JUnit4, MockK, Turbine, Room-testing, androidx-test, and Hilt-testing as
`api` dependencies (not `implementation`) precisely so any module's test source
set that depends on `core:testing` gets all of them transitively without
re-declaring each one.

Nothing is implemented yet — this fills in as Phase 5's sub-phases land, so tests
have fakes to depend on from day one instead of retrofitting them in Phase 8.
