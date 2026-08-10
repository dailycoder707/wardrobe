package com.wardrobe.app.core.data.ai

import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.data.repository.StylingEngineRepositoryImpl
import com.wardrobe.app.core.data.repository.styling.EngineInput
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.AiVendor
import com.wardrobe.app.core.model.common.CategoryId
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.model.garment.Garment
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.outfit.Outfit
import com.wardrobe.app.core.model.outfit.OutfitSlot
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.styling.RecommendationPreferences
import com.wardrobe.app.core.model.styling.ScoredOutfit
import com.wardrobe.app.core.model.styling.SuggestionContext
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val ON_DEVICE_CONFIG = AiProviderConfig.onDeviceDefault(AiCapability.OUTFIT_STYLING)
private val CLOUD_READY_CONFIG =
    AiProviderConfig(
        capability = AiCapability.OUTFIT_STYLING,
        mode = AiProviderMode.CLOUD,
        vendor = AiVendor.GENERIC_REST,
        baseUrl = "https://example.test",
        model = null,
        costRatePerThousandTokens = null,
        consentGrantedAt = Instant.EPOCH,
        consentHost = "https://example.test",
    )
private val CONTEXT = SuggestionContext(LocalDate.of(2026, 6, 15), null, null)

class StylingEngineRouterTest {
    private fun garment(id: Long) =
        Garment(
            id = GarmentId(id),
            name = "Item $id",
            categoryId = CategoryId(1),
            primaryColorId = null,
            palette = emptyList(),
            materials = emptyList(),
            tagIds = emptyList(),
            seasons = emptySet(),
            dressCodes = emptySet(),
            pattern = null,
            fit = null,
            length = null,
            sleeveLength = null,
            warmthRating = null,
            breathabilityRating = null,
            brandId = null,
            size = null,
            price = null,
            purchaseDate = null,
            condition = null,
            careNotes = null,
            status = GarmentStatus.ACTIVE,
            isReviewed = true,
            isFavorite = false,
            images = emptyList(),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun engineInput() =
        EngineInput(
            candidateGarments = listOf(garment(1)),
            garmentsById = mapOf(GarmentId(1) to garment(1)),
            categoriesById = emptyMap(),
            colorsById = emptyMap(),
            activeRules = emptyList(),
            preferences = RecommendationPreferences(),
            costPerWearByGarmentId = emptyMap(),
            favoriteColorIds = emptyList(),
            today = LocalDate.of(2026, 6, 15),
            weather = null,
        )

    private fun scoredOutfit(source: AiResultSource) =
        ScoredOutfit(
            outfit = testOutfit(OutfitId(0), OutfitSource.AI_SUGGESTED),
            score = 1.0,
            explanation = "test",
            passedWeatherFilter = true,
            provenance = AiResultProvenance(source, "generic", null, "styling-v1", Instant.EPOCH),
        )

    private fun testOutfit(
        id: OutfitId,
        source: OutfitSource,
    ) = Outfit(
        id = id,
        name = null,
        garments = emptyList(),
        occasionId = null,
        source = source,
        isSaved = false,
        photoUri = null,
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `suggestOutfits uses the on-device engine when cloud isn't configured`() =
        runTest {
            val onDevice = mockk<StylingEngineRepositoryImpl>()
            val onDeviceResult = listOf(scoredOutfit(AiResultSource.ON_DEVICE))
            coEvery { onDevice.suggestOutfits(CONTEXT) } returns onDeviceResult
            val preferences = fakePreferences(ON_DEVICE_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns null }
            val router = StylingEngineRouter(onDevice, mockk(), preferences, apiKeyStore)

            val result = router.suggestOutfits(CONTEXT)

            assertSame(onDeviceResult, result)
        }

    @Test
    fun `suggestOutfits uses the validated cloud outfits when the cloud path succeeds`() =
        runTest {
            val onDevice = mockk<StylingEngineRepositoryImpl>()
            coEvery { onDevice.loadEngineInput(CONTEXT) } returns engineInput()
            val cloudEngine = mockk<CloudStylingEngine>()
            val cloudResult = listOf(scoredOutfit(AiResultSource.CLOUD))
            coEvery {
                cloudEngine.suggestOutfits(any(), any(), any(), any(), any(), any())
            } returns cloudResult
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = StylingEngineRouter(onDevice, cloudEngine, preferences, apiKeyStore)

            val result = router.suggestOutfits(CONTEXT)

            assertSame(cloudResult, result)
            coVerify(exactly = 0) { onDevice.suggestOutfits(any()) }
        }

    @Test
    fun `suggestOutfits falls back to on-device when the cloud path returns nothing usable`() =
        runTest {
            val onDevice = mockk<StylingEngineRepositoryImpl>()
            coEvery { onDevice.loadEngineInput(CONTEXT) } returns engineInput()
            val onDeviceResult = listOf(scoredOutfit(AiResultSource.ON_DEVICE))
            coEvery { onDevice.suggestOutfits(CONTEXT) } returns onDeviceResult
            val cloudEngine = mockk<CloudStylingEngine>()
            coEvery {
                cloudEngine.suggestOutfits(any(), any(), any(), any(), any(), any())
            } returns emptyList()
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = StylingEngineRouter(onDevice, cloudEngine, preferences, apiKeyStore)

            val result = router.suggestOutfits(CONTEXT)

            assertSame(onDeviceResult, result)
        }

    @Test
    fun `suggestReplacementForSlot always uses the on-device engine, never the cloud path`() =
        runTest {
            val onDevice = mockk<StylingEngineRepositoryImpl>()
            val outfit = testOutfit(OutfitId(1), OutfitSource.USER_CREATED)
            coEvery {
                onDevice.suggestReplacementForSlot(outfit, OutfitSlot.TOP, CONTEXT)
            } returns GarmentId(7)
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = StylingEngineRouter(onDevice, mockk(), preferences, apiKeyStore)

            val result = router.suggestReplacementForSlot(outfit, OutfitSlot.TOP, CONTEXT)

            assertTrue(result == GarmentId(7))
        }
}

private fun fakePreferences(config: AiProviderConfig): AiProviderPreferencesDataStore =
    mockk { every { observeConfig(any()) } returns flowOf(config) }
