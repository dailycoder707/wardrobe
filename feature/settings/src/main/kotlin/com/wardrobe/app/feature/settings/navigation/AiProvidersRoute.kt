package com.wardrobe.app.feature.settings.navigation

import kotlinx.serialization.Serializable

/** Add-to-Wardrobe v2's AI Providers settings screen (ADR-012) — reached the
 * same way [WeatherSettingsRoute] is: a top-bar action from Recommendations,
 * since this module still has no fuller Settings hub screen. */
@Serializable
object AiProvidersRoute
