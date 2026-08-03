package com.wardrobe.app.feature.closet.navigation

import kotlinx.serialization.Serializable

/** Type-safe Nav Compose routes for every screen this phase builds. [HomeRoute]
 * moved here from `:app` (Phase 2 skeleton) since `:app` shouldn't own screen
 * routes for a feature it doesn't implement — see `WardrobeNavHost` for how
 * these get registered. */
@Serializable
object HomeRoute

@Serializable
data class ClosetRoute(
    val favoritesOnly: Boolean = false,
    val focusSearch: Boolean = false,
)

@Serializable
data class GarmentDetailRoute(
    val garmentId: Long,
)

@Serializable
data class EditGarmentRoute(
    val garmentId: Long,
)

/** The Developer Panel (debug builds only) — see `debug/DeveloperPanel.kt`. */
@Serializable
object DeveloperPanelRoute
