package com.wardrobe.app.feature.trips.detail

import androidx.compose.runtime.Immutable

@Immutable
data class TripDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val name: String = "",
    val destination: String = "",
    val dateRangeLabel: String = "",
    val luggageSizeLabel: String? = null,
    val packingItemCount: Int = 0,
    val packedCount: Int = 0,
    val isGeneratingPackingList: Boolean = false,
)
