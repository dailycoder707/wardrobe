package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.image.segmentation.ExtractionResult
import com.wardrobe.app.core.image.segmentation.OnDeviceExtractionEngine
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.AiVendor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

private val ON_DEVICE_CONFIG = AiProviderConfig.onDeviceDefault(AiCapability.GARMENT_EXTRACTION)

private val CLOUD_READY_CONFIG =
    AiProviderConfig(
        capability = AiCapability.GARMENT_EXTRACTION,
        mode = AiProviderMode.CLOUD,
        vendor = AiVendor.GENERIC_REST,
        baseUrl = "https://example.test",
        model = null,
        costRatePerThousandTokens = null,
        consentGrantedAt = Instant.EPOCH,
        consentHost = "https://example.test",
    )

class GarmentExtractionEngineRouterTest {
    @Test
    fun `extract uses the on-device engine when the capability is not cloud-ready`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val onDeviceResult = ExtractionResult.Success(mockk(), confidence = 0.5f)
            coEvery { onDeviceEngine.extract(any()) } returns onDeviceResult
            val preferences = fakePreferences(ON_DEVICE_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns null }
            val router = GarmentExtractionEngineRouter(onDeviceEngine, mockk(), preferences, apiKeyStore)

            val result = router.extract(mockk())

            assertSame(onDeviceResult, result)
        }

    @Test
    fun `extract dispatches to the cloud gateway when fully configured and returns its result`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val resultBitmap = mockk<Bitmap>()
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns
                ImageTaskResult.Success(resultBitmap, 0.9f, provenance())
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore)

            val result = router.extract(mockk()) as ExtractionResult.Success

            assertSame(resultBitmap, result.transparentCutout)
            coVerify(exactly = 0) { onDeviceEngine.extract(any()) }
        }

    @Test
    fun `extract falls back to on-device when the cloud dispatch fails`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val onDeviceResult = ExtractionResult.Success(mockk(), confidence = 0.4f)
            coEvery { onDeviceEngine.extract(any()) } returns onDeviceResult
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns ImageTaskResult.Failure("timeout")
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore)

            val result = router.extract(mockk())

            assertSame(onDeviceResult, result)
        }
}

private fun fakePreferences(config: AiProviderConfig): AiProviderPreferencesDataStore =
    mockk { every { observeConfig(any()) } returns flowOf(config) }

private fun provenance(): AiResultProvenance =
    AiResultProvenance(AiResultSource.CLOUD, "generic", null, "extraction-v1", Instant.EPOCH)
