package com.wardrobe.app.feature.outfits.duplicates

import androidx.compose.runtime.Immutable

@Immutable
data class DuplicateGroupUiModel(
    val garmentNames: List<String>,
    val matchedOnBrand: Boolean,
    val similarUsage: Boolean,
)

@Immutable
data class DuplicatesUiState(
    val isLoading: Boolean = true,
    val groups: List<DuplicateGroupUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}
