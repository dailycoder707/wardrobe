package com.wardrobe.app.core.model.weather

/**
 * A simple, provider-agnostic condition every screen renders directly —
 * Open-Meteo's own WMO weather-code table (Phase 7, `core:network`) is
 * mapped down to these seven values at the network boundary so nothing
 * above `core:network` needs to know what a WMO code is. [UNKNOWN] is the
 * honest fallback for a code this app doesn't recognize, never a guess.
 */
enum class WeatherCondition { SUNNY, CLOUDY, RAIN, STORM, FOG, SNOW, UNKNOWN }
