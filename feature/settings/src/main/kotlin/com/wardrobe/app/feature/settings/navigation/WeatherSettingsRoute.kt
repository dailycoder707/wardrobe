package com.wardrobe.app.feature.settings.navigation

import kotlinx.serialization.Serializable

/** Weather Settings (Phase 7) — this module's first real screen. There is no
 * Settings hub yet (`feature:settings` was scaffolded but never built before
 * this phase), so this route is reached directly from a top-bar action on
 * Recommendations, not from a fuller Settings home screen. */
@Serializable
object WeatherSettingsRoute
