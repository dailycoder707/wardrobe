package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.ai.tryon.TryOnRenderResult
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.AiVendor
import com.wardrobe.app.core.tryon.engine.OnDeviceVirtualTryOnEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

private val ON_DEVICE_CONFIG = AiProviderConfig.onDeviceDefault(AiCapability.VIRTUAL_TRY_ON)
private val CLOUD_READY_CONFIG =
    AiProviderConfig(
        capability = AiCapability.VIRTUAL_TRY_ON,
        mode = AiProviderMode.CLOUD,
        vendor = AiVendor.GENERIC_REST,
        baseUrl = "https://example.test",
        model = null,
        costRatePerThousandTokens = null,
        consentGrantedAt = Instant.EPOCH,
        consentHost = "https://example.test",
    )

class TryOnRouterTest {
    @Test
    fun `render uses the on-device engine when cloud isn't configured`() =
        runTest {
            val onDevice = mockk<OnDeviceVirtualTryOnEngine>()
            val onDeviceResult = TryOnRenderResult.Success(mockk<Bitmap>(), 0.5f, AiResultSource.ON_DEVICE)
            coEvery { onDevice.render(any(), any(), any()) } returns onDeviceResult
            val preferences = fakePreferences(ON_DEVICE_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns null }
            val router = TryOnRouter(onDevice, mockk(), preferences, apiKeyStore)

            val result = router.render(mockk(), mockk())

            assertSame(onDeviceResult, result)
        }

    @Test
    fun `render uses the cloud engine's result when the cloud path succeeds`() =
        runTest {
            val onDevice = mockk<OnDeviceVirtualTryOnEngine>()
            val cloudEngine = mockk<CloudTryOnEngine>()
            val cloudResult = TryOnRenderResult.Success(mockk<Bitmap>(), 0.9f, AiResultSource.CLOUD)
            coEvery { cloudEngine.render(any(), any(), any(), any(), any()) } returns cloudResult
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = TryOnRouter(onDevice, cloudEngine, preferences, apiKeyStore)

            val result = router.render(mockk(), mockk())

            assertSame(cloudResult, result)
            coVerify(exactly = 0) { onDevice.render(any(), any(), any()) }
        }

    @Test
    fun `render falls back to on-device when the cloud path fails`() =
        runTest {
            val onDevice = mockk<OnDeviceVirtualTryOnEngine>()
            val onDeviceResult = TryOnRenderResult.Success(mockk<Bitmap>(), 0.5f, AiResultSource.ON_DEVICE)
            coEvery { onDevice.render(any(), any(), any()) } returns onDeviceResult
            val cloudEngine = mockk<CloudTryOnEngine>()
            coEvery { cloudEngine.render(any(), any(), any(), any(), any()) } returns
                TryOnRenderResult.Failure("missing_confidence")
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router = TryOnRouter(onDevice, cloudEngine, preferences, apiKeyStore)

            val result = router.render(mockk(), mockk())

            assertSame(onDeviceResult, result)
        }
}

private fun fakePreferences(config: AiProviderConfig): AiProviderPreferencesDataStore =
    mockk { every { observeConfig(any()) } returns flowOf(config) }
