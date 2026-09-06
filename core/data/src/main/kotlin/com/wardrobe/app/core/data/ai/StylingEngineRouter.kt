package com.wardrobe.app.core.data.ai

import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.data.repository.DEFAULT_SUGGESTION_COUNT
import com.wardrobe.app.core.data.repository.StylingEngineRepositoryImpl
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.domain.repository.StylingEngineRepository
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.styling.AccessoryCategory
import com.wardrobe.app.core.model.styling.JewelryCategory
import com.wardrobe.app.core.model.styling.RecommendationRunDiagnostics
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The runtime router for `OUTFIT_STYLING` (M12) — the same on-device-
 * default/automatic-fallback shape as the Extraction/Reconstruction/
 * Metadata routers (ADR-012 §11). [onDeviceEngine] (the unmodified Phase 6
 * rule engine) stays the sole implementation for the single-slot
 * "replace this item" quick actions and diagnostics — those are
 * mechanical lookups, not the creative "suggest a whole outfit" call this
 * milestone adds a cloud path for. A cloud outfit that comes back empty
 * (dispatch failure, malformed response, or every candidate outfit failing
 * [com.wardrobe.app.core.data.repository.styling.validateCloudOutfit]'s
 * validation) falls back to the on-device engine — cloud degrades this
 * capability, it never breaks it.
 */
@Singleton
class StylingEngineRouter
    @Inject
    constructor(
        private val onDeviceEngine: StylingEngineRepositoryImpl,
        private val cloudStylingEngine: CloudStylingEngine,
        private val preferencesDataStore: AiProviderPreferencesDataStore,
        private val apiKeyStore: ApiKeyStore,
    ) : StylingEngineRepository {
        override suspend fun suggestOutfits(
            context: SuggestionContext,
            count: Int,
        ): List<ScoredOutfit> {
            val cloud = cloudSuggestions(context, count, anchorGarment = null)
            return cloud ?: onDeviceEngine.suggestOutfits(context, count)
        }

        override suspend fun suggestForItem(
            garmentId: GarmentId,
            context: SuggestionContext,
        ): List<ScoredOutfit> {
            val cloud = cloudSuggestions(context, DEFAULT_SUGGESTION_COUNT, anchorGarment = garmentId)
            return cloud ?: onDeviceEngine.suggestForItem(garmentId, context)
        }

        /** `null` means the cloud path wasn't usable at all (not configured,
         * dispatch failed) or produced nothing that survived validation —
         * either way the caller falls back to the on-device engine. An empty
         * `List` is never returned from here to mean "cloud succeeded with
         * zero outfits," since that's indistinguishable from "didn't run." */
        private suspend fun cloudSuggestions(
            context: SuggestionContext,
            count: Int,
            anchorGarment: GarmentId?,
        ): List<ScoredOutfit>? {
            val dispatchContext = cloudDispatchContextOrNull() ?: return null
            val input = onDeviceEngine.loadEngineInput(context)
            val anchorIsValid = anchorGarment == null || anchorGarment in input.garmentsById
            val cloudResult =
                if (anchorIsValid) {
                    cloudStylingEngine.suggestOutfits(
                        context = context,
                        input = input,
                        dispatchContext = dispatchContext,
                        inspirationImage = null,
                        count = count,
                        anchorGarmentId = anchorGarment,
                    )
                } else {
                    emptyList()
                }
            return cloudResult.ifEmpty { null }
        }

        private suspend fun cloudDispatchContextOrNull(): AiDispatchContext? {
            val config = preferencesDataStore.observeConfig(AiCapability.OUTFIT_STYLING).first()
            val apiKey = apiKeyStore.getApiKey(AiCapability.OUTFIT_STYLING)
            if (!config.isCloudReady() || apiKey.isNullOrBlank()) return null
            return AiDispatchContext(AiCapability.OUTFIT_STYLING, config, apiKey)
        }

        override suspend fun suggestReplacementForSlot(
            outfit: Outfit,
            slot: OutfitSlot,
            context: SuggestionContext,
        ): GarmentId? = onDeviceEngine.suggestReplacementForSlot(outfit, slot, context)

        override suspend fun suggestReplacementForAccessory(
            outfit: Outfit,
            category: AccessoryCategory,
            excludingGarmentId: GarmentId,
            context: SuggestionContext,
        ): GarmentId? = onDeviceEngine.suggestReplacementForAccessory(outfit, category, excludingGarmentId, context)

        override suspend fun suggestReplacementForJewelry(
            outfit: Outfit,
            category: JewelryCategory,
            excludingGarmentId: GarmentId,
            context: SuggestionContext,
        ): GarmentId? = onDeviceEngine.suggestReplacementForJewelry(outfit, category, excludingGarmentId, context)

        override fun lastRunDiagnostics(): RecommendationRunDiagnostics = onDeviceEngine.lastRunDiagnostics()
    }
