package com.wardrobe.app.core.domain.repository

/** Thin scheduling abstraction so `feature:settings` never needs a direct
 * `core:data`/WorkManager dependency — mirrors [WeatherRefreshScheduler]'s
 * exact pattern from Phase 7. */
interface SyncScheduler {
    /** (Re)schedules the periodic background sync worker with the given
     * Wi-Fi-only/charging-only preferences. Cheap to call after every
     * preference save — a no-op if nothing actually changed. */
    fun reschedule(
        wifiOnly: Boolean,
        chargingOnly: Boolean,
    )

    /** Enqueues one immediate, one-shot sync — backs the Wardrobe Sync
     * screen's "Manual Sync" button. */
    fun syncNow()
}
