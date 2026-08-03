package com.wardrobe.app.core.domain.repository

/**
 * Re-schedules the periodic weather refresh job at a new interval (Weather
 * Settings' "Refresh interval" control, Phase 7) — a thin abstraction purely
 * so `feature:settings` never needs a direct dependency on `core:data`'s
 * `WorkManager`-based `WeatherRefreshWorker`, the same "feature modules only
 * see `core:domain` interfaces" rule every other repository in this app
 * already follows.
 */
interface WeatherRefreshScheduler {
    fun reschedule(intervalHours: Int)
}
