package com.wardrobe.app.feature.trips.packing

import androidx.compose.runtime.Immutable

@Immutable
data class PackingItemUiModel(
    val id: Long,
    val garmentId: Long?,
    val name: String,
    val isPacked: Boolean,
    val rationale: String?,
)

@Immutable
data class PackingGroupUiModel(
    val category: String,
    val items: List<PackingItemUiModel>,
)

@Immutable
data class PackingUiState(
    val isLoading: Boolean = true,
    val groups: List<PackingGroupUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && groups.isEmpty()
}
