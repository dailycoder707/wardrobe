package com.wardrobe.app.feature.trips.list

import androidx.compose.runtime.Immutable

@Immutable
data class TripUiModel(
    val id: Long,
    val name: String,
    val destination: String,
    val dateRangeLabel: String,
)

@Immutable
data class TripsUiState(
    val isLoading: Boolean = true,
    val trips: List<TripUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && trips.isEmpty()
}
