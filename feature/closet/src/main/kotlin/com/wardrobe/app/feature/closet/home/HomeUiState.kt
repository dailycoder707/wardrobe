package com.wardrobe.app.feature.closet.home

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.ui.components.GarmentTileUiModel

@Immutable
data class WardrobeSummaryUiModel(
    val totalActiveGarments: Int,
    val wornAtLeastOnce: Int,
    val usagePercent: Int,
)

/** Phase 5e's "Home Insights" — a compact highlight reel, not a second copy
 * of the full Insights screen. Each populated field is one tap from a
 * specific garment or from the full Insights screen (`feature:stats`) for
 * anything Home only summarizes (e.g. Least Used lives in Insights, not
 * duplicated here) — see `phase-5e-wardrobe-intelligence.md`'s Home Insights
 * design decision. */
@Immutable
data class HomeInsightsUiModel(
    val mostUsedGarment: GarmentTileUiModel? = null,
    val waitingToBeWornGarment: GarmentTileUiModel? = null,
    val recentlyPurchasedGarment: GarmentTileUiModel? = null,
    val favoriteColorName: String? = null,
    val favoriteBrandName: String? = null,
    val favoriteCategoryName: String? = null,
    val upcomingOutfitLabel: String? = null,
) {
    val isEmpty: Boolean
        get() =
            mostUsedGarment == null &&
                waitingToBeWornGarment == null &&
                recentlyPurchasedGarment == null &&
                favoriteColorName == null &&
                favoriteBrandName == null &&
                favoriteCategoryName == null &&
                upcomingOutfitLabel == null
}

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val greeting: String = "",
    val homeTitle: String = "My Wardrobe",
    val showGreeting: Boolean = true,
    val showWardrobeHealthCard: Boolean = true,
    val currentDateText: String = "",
    val summary: WardrobeSummaryUiModel? = null,
    val recentlyAdded: List<GarmentTileUiModel> = emptyList(),
    val recentlyWorn: List<GarmentTileUiModel> = emptyList(),
    val continueEditing: List<GarmentTileUiModel> = emptyList(),
    val insights: HomeInsightsUiModel = HomeInsightsUiModel(),
    /** A nonzero count means an Add-to-Wardrobe import didn't reach
     * `COMPLETED` — surfaced as a "Resume Import" banner so an import
     * interrupted by an app restart or crash is still discoverable. */
    val incompleteImportCount: Int = 0,
) {
    val isEmpty: Boolean
        get() = !isLoading && recentlyAdded.isEmpty() && summary?.totalActiveGarments == 0
}
