package com.wardrobe.app.feature.outfits.list

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.OutfitSort

@Immutable
data class SavedLooksFilterState(
    val occasionId: OccasionId? = null,
    val favoriteOnly: Boolean = false,
    val showArchived: Boolean = false,
) {
    val activeCount: Int
        get() = listOf(occasionId != null, favoriteOnly).count { it }

    companion object {
        val EMPTY = SavedLooksFilterState()
    }
}

@Immutable
data class OutfitCardUiModel(
    val id: Long,
    val title: String,
    val thumbnailPaths: List<String>,
    val isFavorite: Boolean,
    val occasionName: String?,
    val wearCount: Int,
)

@Immutable
data class SavedLooksUiState(
    val isLoading: Boolean = true,
    val outfits: List<OutfitCardUiModel> = emptyList(),
    val totalUnfilteredCount: Int = 0,
    val searchQuery: String = "",
    val filters: SavedLooksFilterState = SavedLooksFilterState.EMPTY,
    val occasionOptions: List<Occasion> = emptyList(),
    val sort: OutfitSort = OutfitSort.DEFAULT,
    val toastMessage: String? = null,
) {
    val isEmptyResult: Boolean get() = !isLoading && outfits.isEmpty()
    val isEmptyLibrary: Boolean
        get() = isEmptyResult && totalUnfilteredCount == 0 && searchQuery.isBlank() && filters.activeCount == 0
}
