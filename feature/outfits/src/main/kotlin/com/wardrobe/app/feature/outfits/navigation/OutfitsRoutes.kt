package com.wardrobe.app.feature.outfits.navigation

import kotlinx.serialization.Serializable

/** The Saved Looks grid — this module's top-level nav-dock destination. */
@Serializable
object SavedLooksRoute

/** [outfitId] `0` builds a brand-new outfit; any other id opens that saved
 * outfit for editing ("Restyle") — one route, matching
 * `OutfitRepository.saveOutfit`'s single insert-or-update contract. */
@Serializable
data class OutfitBuilderRoute(
    val outfitId: Long = 0L,
)

@Serializable
data class OutfitDetailRoute(
    val outfitId: Long,
)

/** Recommendations — this module's second top-level nav-dock destination
 * (Phase 6). */
@Serializable
object RecommendationsRoute

@Serializable
object StylistPreferencesRoute

/** [garmentIds] is a comma-joined string of garment ids rather than a
 * `List<Long>` route parameter — keeps this route trivially `@Serializable`
 * without a custom `NavType`. */
@Serializable
data class OutfitPreviewRoute(
    val garmentIds: String,
)

/** Phase 9 — Capsule Suggestions, reached from Recommendations. */
@Serializable
object CapsulesRoute

/** Phase 9 — Duplicate Detection, reached from Recommendations. */
@Serializable
object DuplicateGarmentsRoute
