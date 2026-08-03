package com.wardrobe.app.feature.tryon.render

import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.tryon.ClothingDepth
import com.wardrobe.app.core.model.tryon.GarmentPlacementTemplate

/**
 * Keeps the full [GarmentPlacementTemplate] (not just its numeric fields) so
 * a gesture update can persist it wholesale via
 * `TryOnPlacementRepository.saveGarmentPlacementTemplate` without first
 * re-fetching `bodyProfileId`/`templateType`/`placementSource`/`lastUsedAt` —
 * the same "keep the whole domain object in UI state, not a flattened
 * projection" choice this screen needs since, unlike a read-only preview, it
 * writes this object straight back.
 */
data class TryOnLayerUiModel(
    val garmentId: GarmentId,
    val bodyProfileId: BodyProfileId,
    val slot: OutfitSlot,
    val depth: ClothingDepth,
    val title: String,
    val imagePath: String?,
    val template: GarmentPlacementTemplate,
) {
    /** "Auto-placed — drag to adjust" — gone forever once the user performs
     * their first manual edit on this template (Constitution rule 7),
     * brought back only by an explicit "Reset to Auto Placement". */
    val showConfidenceChip: Boolean get() = !template.isUserAdjusted
}

data class TryOnUiState(
    val isLoading: Boolean = true,
    val needsBodyProfile: Boolean = false,
    val backgroundPhotoPath: String? = null,
    val layers: List<TryOnLayerUiModel> = emptyList(),
)
