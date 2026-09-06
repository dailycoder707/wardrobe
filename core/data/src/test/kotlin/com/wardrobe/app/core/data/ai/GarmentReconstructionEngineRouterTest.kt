package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.image.reconstruction.OnDeviceReconstructionEngine
import com.wardrobe.app.core.image.reconstruction.ReconstructionResult
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.AiVendor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val CLOUD_READY_CONFIG =
    AiProviderConfig(
        capability = AiCapability.GARMENT_RECONSTRUCTION,
        mode = AiProviderMode.CLOUD,
        vendor = AiVendor.GENERIC_REST,
        baseUrl = "https://example.test",
        model = null,
        costRatePerThousandTokens = null,
        consentGrantedAt = Instant.EPOCH,
        consentHost = "https://example.test",
    )

class GarmentReconstructionEngineRouterTest {
    @Test
    fun `reconstruct uses the on-device engine when not cloud-ready`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceReconstructionEngine>()
            val onDeviceResult = ReconstructionResult.NotAttempted(0f, "no cloud provider configured")
            coEvery { onDeviceEngine.reconstruct(any(), any()) } returns onDeviceResult
            val preferences = fakePreferences(AiProviderConfig.onDeviceDefault(AiCapability.GARMENT_RECONSTRUCTION))
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns null }
            val router = GarmentReconstructionEngineRouter(onDeviceEngine, mockk(), preferences, apiKeyStore)

            val result = router.reconstruct(mockk(), extractionConfidence = 0.6f)

            assertSame(onDeviceResult, result)
        }

    @Test
    fun `reconstruct accepts a cloud fill only when its confidence is genuinely HIGH`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceReconstructionEngine>()
            val cloudBitmap = mockk<Bitmap>()
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns
                ImageTaskResult.Success(cloudBitmap, confidence = 0.92f, provenance = provenance())
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = GarmentReconstructionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore)

            val result = router.reconstruct(mockk(), extractionConfidence = 0.6f) as ReconstructionResult.Reconstructed

            assertSame(cloudBitmap, result.bitmap)
            assertTrue(result.confidence >= 0.85f)
        }

    @Test
    fun `reconstruct falls back to on-device when the cloud confidence is only MEDIUM`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceReconstructionEngine>()
            val onDeviceResult = ReconstructionResult.NotAttempted(0.3f, "occlusion detected")
            coEvery { onDeviceEngine.reconstruct(any(), any()) } returns onDeviceResult
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns
                ImageTaskResult.Success(mockk(), confidence = 0.6f, provenance = provenance())
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = GarmentReconstructionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore)

            val result = router.reconstruct(mockk(), extractionConfidence = 0.6f)

            assertSame(onDeviceResult, result)
        }

    @Test
    fun `reconstruct falls back to on-device when the cloud dispatch fails`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceReconstructionEngine>()
            val onDeviceResult = ReconstructionResult.NotAttempted(0.3f, "occlusion detected")
            coEvery { onDeviceEngine.reconstruct(any(), any()) } returns onDeviceResult
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns ImageTaskResult.Failure("timeout")
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = GarmentReconstructionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore)

            val result = router.reconstruct(mockk(), extractionConfidence = 0.6f)

            assertSame(onDeviceResult, result)
        }
}

private fun fakePreferences(config: AiProviderConfig): AiProviderPreferencesDataStore =
    mockk { every { observeConfig(any()) } returns flowOf(config) }

private fun provenance(): AiResultProvenance =
    AiResultProvenance(AiResultSource.CLOUD, "generic", null, "reconstruction-v1", Instant.EPOCH)
