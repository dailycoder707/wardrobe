package com.wardrobe.app.feature.stats.health

import com.wardrobe.app.feature.stats.common.InsightItemUiModel

/** Every entry is advisory, never a warning — see this module's own
 * philosophy note in `phase-5e-wardrobe-intelligence.md`. [items] is empty
 * for advisories that are a single observation (e.g. "Balanced Colors") and
 * populated for the ones that point at specific garments. */
data class HealthAdvisoryUiModel(
    val title: String,
    val message: String,
    val items: List<InsightItemUiModel> = emptyList(),
)

data class HealthUiState(
    val isLoading: Boolean = true,
    val advisories: List<HealthAdvisoryUiModel> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && advisories.isEmpty()
}
