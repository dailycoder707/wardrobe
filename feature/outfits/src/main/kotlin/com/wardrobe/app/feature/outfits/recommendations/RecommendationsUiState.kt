package com.wardrobe.app.feature.outfits.recommendations

import androidx.compose.runtime.Immutable
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.common.OccasionId
import com.wardrobe.app.core.model.outfit.Occasion
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.ui.components.GarmentTileUiModel

/** One slot's tile within a recommended outfit — tapping it opens Garment
 * Detail (Searchable Recommendations, per the master brief). */
@Immutable
data class RecommendedItemUiModel(
    val slot: OutfitSlot,
    val slotLabel: String,
    val tile: GarmentTileUiModel,
)

/** Phase 9 — one accessory/jewelry item the engine recommends wearing
 * *alongside* the outfit, beyond the single item in [RecommendedOutfitUiModel.items]
 * — see `ScoredOutfit.accessoryItems`/`jewelryItems`'s KDoc for why these
 * ride alongside rather than inside the outfit itself. */
@Immutable
data class RecommendedAccessoryUiModel(
    val label: String,
    val garmentName: String,
    val explanation: String,
)

/** [provenance]/[aiConfidence] are `null` for a rule-engine outfit; a cloud
 * `OUTFIT_STYLING` suggestion (M12) carries both, so the screen can show an
 * "AI Styled" badge with a provider/confidence/reasoning info button —
 * see [ScoredOutfit][com.wardrobe.app.core.model.styling.ScoredOutfit]'s KDoc.
 *
 * [reasonBullets] (M19) is the exact same text as [explanation], split into
 * individual sentences for the "Why this?" list — not a second explanation
 * generator, just a different presentation of the one real string the
 * engine already produces (`RecommendationExplainer.buildExplanation`). */
@Immutable
data class RecommendedOutfitUiModel(
    val outfit: Outfit,
    val explanation: String,
    val reasonBullets: List<String>,
    val score: Double,
    val items: List<RecommendedItemUiModel>,
    val accessoryItems: List<RecommendedAccessoryUiModel> = emptyList(),
    val jewelryItems: List<RecommendedAccessoryUiModel> = emptyList(),
    val provenance: AiResultProvenance? = null,
    val aiConfidence: Float? = null,
)

@Immutable
data class RecommendationsUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val suggestions: List<RecommendedOutfitUiModel> = emptyList(),
    val selectedIndex: Int = 0,
    val actionMessage: String? = null,
    /** M19 — the real, live weather this run's scoring actually used
     * (`WeatherSnapshot.headline`, the same repository/formatting Home's
     * assistant card already uses) — `null` only when no weather signal is
     * genuinely available (weather off in settings, no location resolved),
     * never a placeholder. */
    val weatherSummary: String? = null,
    /** M19 — the real `Occasion` reference data (`OccasionRepository`) a
     * user can pick to request a recommendation for a specific occasion —
     * changing this recomputes for real (see [RecommendationsViewModel.onOccasionSelected]). */
    val availableOccasions: List<Occasion> = emptyList(),
    val selectedOccasionId: OccasionId? = null,
    /** M19/M18 — mirrors a real, currently in-flight `OUTFIT_STYLING` job
     * from `AiProviderSettingsRepository.observeActiveOperations()`; never
     * shown for the (default) rule-engine path, since that genuinely isn't
     * an AI operation. */
    val isCloudStylingActive: Boolean = false,
    /** M22 fix — [isEmpty] used to mean two genuinely different things with
     * one message ("Not enough wardrobe items for a complete outfit"): a
     * wardrobe with *zero* garments, and a wardrobe with garments that just
     * aren't enough to complete a full outfit. This distinguishes them so
     * the empty state can say which one is actually true. */
    val hasNoGarments: Boolean = false,
) {
    val selected: RecommendedOutfitUiModel? get() = suggestions.getOrNull(selectedIndex)
    val isEmpty: Boolean get() = !isLoading && !isError && suggestions.isEmpty()
}
