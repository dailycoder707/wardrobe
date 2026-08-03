package com.wardrobe.app.feature.outfits.recommendations

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.ui.components.GarmentTileUiModel

/** One slot's tile within a recommended outfit — tapping it opens Garment
 * Detail (Searchable Recommendations, per the master brief). */
@Immutable
data class RecommendedItemUiModel(
    val slot: OutfitSlot,
    val slotLabel: String,
    val tile: GarmentTileUiModel,
)

/** Phase 9 — one accessory/jewelry item the engine recommends wearing
 * *alongside* the outfit, beyond the single item in [RecommendedOutfitUiModel.items]
 * — see `ScoredOutfit.accessoryItems`/`jewelryItems`'s KDoc for why these
 * ride alongside rather than inside the outfit itself. */
@Immutable
data class RecommendedAccessoryUiModel(
    val label: String,
    val garmentName: String,
    val explanation: String,
)

@Immutable
data class RecommendedOutfitUiModel(
    val outfit: Outfit,
    val explanation: String,
    val score: Double,
    val items: List<RecommendedItemUiModel>,
    val accessoryItems: List<RecommendedAccessoryUiModel> = emptyList(),
    val jewelryItems: List<RecommendedAccessoryUiModel> = emptyList(),
)

@Immutable
data class RecommendationsUiState(
    val isLoading: Boolean = true,
    val suggestions: List<RecommendedOutfitUiModel> = emptyList(),
    val selectedIndex: Int = 0,
    val actionMessage: String? = null,
) {
    val selected: RecommendedOutfitUiModel? get() = suggestions.getOrNull(selectedIndex)
    val isEmpty: Boolean get() = !isLoading && suggestions.isEmpty()
}
