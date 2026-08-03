package com.wardrobe.app.feature.stats.navigation

import kotlinx.serialization.Serializable

/** Insights is the nav-dock top-level destination (`WardrobeNavHost`); Wardrobe
 * Story and Wardrobe Health are reached from within Insights (or Home), the
 * same way Garment Detail/Edit Garment aren't dock-level despite being real
 * screens. */
@Serializable
object InsightsRoute

@Serializable
object WardrobeStoryRoute

@Serializable
object WardrobeHealthRoute
