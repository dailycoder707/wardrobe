package com.wardrobe.app.feature.outfits.capsules

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.intelligence.CapsuleType

@Immutable
data class CapsuleItemUiModel(
    val slotLabel: String,
    val garmentNames: List<String>,
)

@Immutable
data class CapsulesUiState(
    val isLoading: Boolean = true,
    val selectedType: CapsuleType = CapsuleType.OFFICE,
    val explanation: String = "",
    val items: List<CapsuleItemUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && items.isEmpty()
}
