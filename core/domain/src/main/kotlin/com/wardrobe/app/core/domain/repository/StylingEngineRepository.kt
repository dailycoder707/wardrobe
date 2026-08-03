package com.wardrobe.app.core.domain.repository

import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.styling.RecommendationRunDiagnostics
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext

/**
 * Phase 6 owns the implementation (rule-based scoring, the hard weather filter, the
 * feedback→rule feedback loop). This interface exists now so `feature:outfits`/
 * `feature:closet` can be built against a stable contract before Phase 6 lands — see
 * ADR-004's note on how an LLM-backed implementation could be added later as a second
 * `@Binds` target without this interface, or any feature module, changing.
 */
interface StylingEngineRepository {
    suspend fun suggestOutfits(context: SuggestionContext): List<ScoredOutfit>

    suspend fun suggestForItem(
        garmentId: GarmentId,
        context: SuggestionContext,
    ): List<ScoredOutfit>

    /** Backs the "Replace Shoes/Bag/Jewelry/Watch/Jacket" quick actions — the
     * best-scoring alternative for one slot of an already-generated outfit,
     * excluding whatever currently fills it. `null` means nothing else in the
     * wardrobe is available for that slot right now. */
    suspend fun suggestReplacementForSlot(
        outfit: Outfit,
        slot: OutfitSlot,
        context: SuggestionContext,
    ): GarmentId?

    /** Phase 9 — [OutfitSlot.ACCESSORIES]/[OutfitSlot.JEWELRY] can now carry
     * several concurrent presentation-only picks (see `ScoredOutfit`'s
     * `accessoryItems`/`jewelryItems`), so [suggestReplacementForSlot]'s
     * single-slot key can no longer disambiguate "replace just the belt"
     * from "replace just the scarf." These two methods are the equivalent
     * for one specific sub-category. */
    suspend fun suggestReplacementForAccessory(
        outfit: Outfit,
        category: AccessoryCategory,
        excludingGarmentId: GarmentId,
        context: SuggestionContext,
    ): GarmentId?

    suspend fun suggestReplacementForJewelry(
        outfit: Outfit,
        category: JewelryCategory,
        excludingGarmentId: GarmentId,
        context: SuggestionContext,
    ): GarmentId?

    /** A snapshot of how the *most recent* `suggestOutfits`/`suggestForItem`
     * call resolved its context (Phase 7) — Developer Panel diagnostics only,
     * never user-facing. Reading before any suggestion call returns the
     * default, all-zero [RecommendationRunDiagnostics]. */
    fun lastRunDiagnostics(): RecommendationRunDiagnostics
}
