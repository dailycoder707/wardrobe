package com.wardrobe.app.feature.outfits.detail

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.intelligence.OutfitInsights
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.ui.components.GarmentTileUiModel
import java.time.LocalDate

@Immutable
data class OutfitSlotUiModel(
    val slot: OutfitSlot,
    val garment: GarmentTileUiModel,
)

@Immutable
data class OutfitDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val error: String? = null,
    val title: String = "",
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val notes: String? = null,
    val mood: String? = null,
    val occasionName: String? = null,
    val seasonNames: List<String> = emptyList(),
    val dressCodeNames: List<String> = emptyList(),
    val tagNames: List<String> = emptyList(),
    val slots: List<OutfitSlotUiModel> = emptyList(),
    val wearCount: Int = 0,
    val lastWornDate: LocalDate? = null,
    val wearHistory: List<LocalDate> = emptyList(),
    val deleteBlockedMessage: String? = null,
    /** Phase 9 — Average Rating (derived from Feedback votes)/Suitable
     * Weather/Estimated Comfort/Estimated Warmth/Rotation Priority, all
     * derived from `WardrobeIntelligenceRepository`. */
    val insights: OutfitInsights? = null,
)
