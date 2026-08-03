package com.wardrobe.app.feature.trips.navigation

import kotlinx.serialization.Serializable

/** The trip list — this module's own top-level entry point (Phase 9). */
@Serializable
object TripsRoute

@Serializable
data class TripDetailRoute(
    val tripId: Long,
)

@Serializable
data class PackingRoute(
    val tripId: Long,
)
