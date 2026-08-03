package com.wardrobe.app.core.model.sync

/**
 * Wardrobe Sync settings (Phase 8) — mirrors [com.wardrobe.app.core.model.weather.WeatherPreferences]'s
 * pattern: a small DataStore-backed toggle set, never required for the rest
 * of the app to function (Constitution rule 12 extended to sync: it must
 * refine, never gate, ordinary use of the app).
 */
data class SyncPreferences(
    val autoSyncEnabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
)
