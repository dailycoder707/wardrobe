package com.wardrobe.app.feature.closet.closet

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.garment.Brand
import com.wardrobe.app.core.model.garment.Category
import com.wardrobe.app.core.model.garment.Color
import com.wardrobe.app.core.model.garment.GarmentSort
import com.wardrobe.app.core.model.garment.Material
import com.wardrobe.app.core.model.garment.Tag
import com.wardrobe.app.core.ui.components.GarmentTileUiModel

/** Options available to build a filter/sort UI from — resolved reference data,
 * not user selections (see [ClosetFilterState] for those). */
@Immutable
data class ClosetFilterOptions(
    val categories: List<Category> = emptyList(),
    val colors: List<Color> = emptyList(),
    val brands: List<Brand> = emptyList(),
    val materials: List<Material> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

@Immutable
data class ClosetUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val garments: List<GarmentTileUiModel> = emptyList(),
    val totalUnfilteredCount: Int = 0,
    val searchQuery: String = "",
    val filters: ClosetFilterState = ClosetFilterState.EMPTY,
    val filterOptions: ClosetFilterOptions = ClosetFilterOptions(),
    val sort: GarmentSort = GarmentSort.DEFAULT,
    val gridColumnCount: Int = 3,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val recentSearches: List<String> = emptyList(),
    val toastMessage: String? = null,
) {
    val isEmptyResult: Boolean
        get() = !isLoading && garments.isEmpty()

    val isEmptyCloset: Boolean
        get() = isEmptyResult && totalUnfilteredCount == 0 && searchQuery.isBlank() && filters.activeCount == 0
}
