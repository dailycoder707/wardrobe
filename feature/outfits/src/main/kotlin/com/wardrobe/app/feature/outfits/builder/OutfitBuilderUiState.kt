package com.wardrobe.app.feature.outfits.builder

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.domain.styling.ColorHarmony
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.common.TagId
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.ui.components.GarmentTileUiModel

@Immutable
data class OutfitBuilderFormState(
    val name: String = "",
    val notes: String = "",
    val mood: String = "",
    val occasionId: OccasionId? = null,
    val seasons: Set<Season> = emptySet(),
    val dressCodes: Set<DressCode> = emptySet(),
    val tagIds: Set<TagId> = emptySet(),
    val isFavorite: Boolean = false,
)

@Immutable
data class OutfitBuilderReferenceData(
    val categories: List<Category> = emptyList(),
    val brands: List<Brand> = emptyList(),
    val occasions: List<Occasion> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

@Immutable
data class OutfitBuilderUiState(
    val isLoading: Boolean = true,
    val slots: Map<OutfitSlot, GarmentTileUiModel> = emptyMap(),
    val form: OutfitBuilderFormState = OutfitBuilderFormState(),
    val closetGarments: List<GarmentTileUiModel> = emptyList(),
    val reference: OutfitBuilderReferenceData = OutfitBuilderReferenceData(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val colorHarmony: ColorHarmony? = null,
    val isSaving: Boolean = false,
    val didSave: Boolean = false,
    val toastMessage: String? = null,
) {
    val isEmpty: Boolean get() = slots.isEmpty()
}
