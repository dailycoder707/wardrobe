# :core:network

Weather only. This is the **only** module in the entire app that makes an
outbound network call — Open-Meteo, free, keyless (Phase 1 Section 18). Do not add
another API client here without first revisiting the Section 0 budget-posture
decision (zero-cost/offline) in `alta-class-closet-app-master-prompt.md`.

## Packages
| Package | Holds |
|---|---|
| `weather/` | `OpenMeteoService` (Retrofit interface), `OpenMeteoDtos` (`@Serializable` DTOs for the `current`/`daily` blocks), `WeatherProvider` (the interface `core:data` depends on) + `OpenMeteoWeatherProvider` (its implementation, including the WMO weather-code → `WeatherCondition` mapping) |

`core:data`'s `WeatherRepositoryImpl` wraps `WeatherProvider` and is what
actually enforces the "always return a cached value, never a bare error" rule
(Phase 1 Section 18, Phase 7's Constitution rule 12) — this module itself only
knows how to make the HTTP call and map the response; it has no cache, no
fallback logic, and no knowledge of user preferences.

## Weather code mapping

Open-Meteo returns numeric WMO weather codes, not a named condition. Every code
this app cares about is a named constant (`WMO_CLEAR_SKY`, `WMO_MAINLY_CLEAR`,
etc.) grouped into `CLOUDY_CODES`/`FOG_CODES`/`RAIN_CODES`/`SNOW_CODES`/
`STORM_CODES` sets in `OpenMeteoWeatherProvider.kt`, so the mapping to
`WeatherCondition` is auditable at a glance rather than a wall of magic
numbers.

## Request shape

`OpenMeteoService` requests both the `current` block and a 7-day `daily`
block in a single call (`DEFAULT_FORECAST_DAYS = 7`) so that calendar-planned
future outfits can resolve a real forecast for their date, not just today's.

Covered by `OpenMeteoWeatherProviderTest` (WMO-code mapping, current-block-
only-applies-to-today, empty-daily-block handling).
