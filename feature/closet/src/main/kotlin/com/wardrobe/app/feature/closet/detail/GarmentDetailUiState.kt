package com.wardrobe.app.feature.closet.detail

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.intelligence.GarmentInsights
import java.time.LocalDate

@Immutable
data class GarmentDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val notFound: Boolean = false,
    val garment: Garment? = null,
    val categoryName: String? = null,
    val brandName: String? = null,
    /** Phase 9 — Last Worn/First Worn/Total Wears/Cost Per Wear/Rotation
     * Score/Season Usage/Availability/Laundry/Packing status, all derived
     * from `WardrobeIntelligenceRepository`, never a second copy of a number
     * this state used to compute itself. */
    val insights: GarmentInsights? = null,
    val wearHistory: List<LocalDate> = emptyList(),
)
