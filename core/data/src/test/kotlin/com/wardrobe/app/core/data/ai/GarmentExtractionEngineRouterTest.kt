package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.gateway.VisionPromptResult
import com.wardrobe.app.core.ai.security.ApiKeyStore
import com.wardrobe.app.core.datastore.preferences.AiProviderPreferencesDataStore
import com.wardrobe.app.core.image.segmentation.ExtractionResult
import com.wardrobe.app.core.image.segmentation.OnDeviceExtractionEngine
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiFallbackReasons
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.time.Instant

private const val PHOTO_SIZE = 10

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

private val GEMINI_CONFIG = CLOUD_READY_CONFIG.copy(vendor = AiVendor.GEMINI)

@RunWith(RobolectricTestRunner::class)
class GarmentExtractionEngineRouterTest {
    @Test
    fun `extract uses the on-device engine when the capability is not cloud-ready, no fallback reported`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val onDeviceResult = ExtractionResult.Success(mockk(), confidence = 0.5f, provenance = onDeviceProvenance())
            coEvery { onDeviceEngine.extract(any()) } returns onDeviceResult
            val preferences = fakePreferences(ON_DEVICE_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns null }
            val router =
                GarmentExtractionEngineRouter(onDeviceEngine, mockk(), preferences, apiKeyStore, mockk(relaxed = true))

            val result = router.extract(mockk())

            assertSame(onDeviceResult, result)
            assertFalse((result as ExtractionResult.Success).provenance.fallbackUsed)
        }

    @Test
    fun `extract dispatches to the cloud gateway when fully configured and returns its result, no fallback reported`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val resultBitmap = mockk<Bitmap>()
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns
                ImageTaskResult.Success(resultBitmap, 0.9f, cloudProvenance())
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore, mockk(relaxed = true))

            val result = router.extract(mockk()) as ExtractionResult.Success

            assertSame(resultBitmap, result.transparentCutout)
            assertFalse(result.provenance.fallbackUsed)
            coVerify(exactly = 0) { onDeviceEngine.extract(any()) }
        }

    /** M25 real-device finding: a fallback result must carry
     * `requestedSource=CLOUD`/a real `fallbackReason`, never masquerade as
     * a plain on-device run — the cutout pixels/confidence themselves are
     * still exactly what the on-device engine produced. */
    @Test
    fun `extract falls back to on-device when the cloud dispatch fails, tagged as a real fallback`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val cutout = mockk<Bitmap>()
            val onDeviceResult = ExtractionResult.Success(cutout, confidence = 0.4f, provenance = onDeviceProvenance())
            coEvery { onDeviceEngine.extract(any()) } returns onDeviceResult
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns ImageTaskResult.Failure("timeout")
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore, mockk(relaxed = true))

            val result = router.extract(mockk()) as ExtractionResult.Success

            assertSame(cutout, result.transparentCutout)
            assertEquals(0.4f, result.confidence)
            assertTrue(result.provenance.fallbackUsed)
            assertEquals(AiResultSource.CLOUD, result.provenance.requestedSource)
            assertEquals("timeout", result.provenance.fallbackReason)
        }

    /** M25 real-device finding, still true post the Gemini-segmentation
     * follow-up: a `GARMENT_EXTRACTION` row configured for a vendor with
     * neither an `ImageTaskAdapter` binding nor Gemini's own segmentation
     * path (every vendor except `GENERIC_REST` and `GEMINI` — see
     * `AdapterBindsModule`/`AiVendor.supportsCloudGarmentSegmentation`)
     * always dispatch-fails with this exact reason from
     * `DefaultAiGateway.runImageTask`. This is architectural, not
     * transient, and must still resolve to an honestly-tagged fallback. */
    @Test
    fun `a vendor with neither ImageTaskAdapter nor Gemini segmentation falls back tagged adapter-unavailable`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val cutout = mockk<Bitmap>()
            val onDeviceResult = ExtractionResult.Success(cutout, confidence = 0.6f, provenance = onDeviceProvenance())
            coEvery { onDeviceEngine.extract(any()) } returns onDeviceResult
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns
                ImageTaskResult.Failure(AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE)
            val claudeConfig = CLOUD_READY_CONFIG.copy(vendor = AiVendor.CLAUDE)
            val preferences = fakePreferences(claudeConfig)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore, mockk(relaxed = true))

            val result = router.extract(mockk()) as ExtractionResult.Success

            assertTrue(result.provenance.fallbackUsed)
            assertEquals(AiFallbackReasons.IMAGE_TASK_ADAPTER_UNAVAILABLE, result.provenance.fallbackReason)
            coVerify(exactly = 0) { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `extract on-device failure is returned as-is, no fallback tagging possible on a Failure`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val onDeviceFailure = ExtractionResult.Failure("no_subject_detected")
            coEvery { onDeviceEngine.extract(any()) } returns onDeviceFailure
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns ImageTaskResult.Failure("timeout")
            val preferences = fakePreferences(CLOUD_READY_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore, mockk(relaxed = true))

            val result = router.extract(mockk())

            assertSame(onDeviceFailure, result)
        }

    // --- M25 Gemini-segmentation follow-up ---------------------------------

    @Test
    fun `a Gemini config dispatches via runVisionPrompt, never runImageTask`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Failure("timeout")
            coEvery { onDeviceEngine.extract(any()) } returns
                ExtractionResult.Success(mockk(), 0.5f, onDeviceProvenance())
            val preferences = fakePreferences(GEMINI_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore, mockk(relaxed = true))

            router.extract(mockk())

            coVerify(exactly = 0) { gateway.runImageTask(any(), any(), any(), any()) }
            coVerify { gateway.runVisionPrompt(any(), any(), any(), any(), any(), expectJsonResponse = true) }
        }

    @Test
    fun `a valid Gemini segmentation response produces a real cloud cutout, provider reported as GEMINI`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val gateway = mockk<AiGateway>()
            val sourcePhoto = solidBitmap(PHOTO_SIZE, Color.argb(255, 4, 5, 6))
            val mask = base64Png(solidBitmap(PHOTO_SIZE, Color.argb(255, 255, 255, 255)))
            val raw = """{"detections": [{"label": "shirt", "box_2d": [0, 0, 1000, 1000], "mask": "$mask"}]}"""
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success(raw, cloudProvenance(provider = "GEMINI"))
            val preferences = fakePreferences(GEMINI_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore, mockk(relaxed = true))

            val result = router.extract(sourcePhoto) as ExtractionResult.Success

            assertFalse(result.provenance.fallbackUsed)
            assertEquals(AiResultSource.CLOUD, result.provenance.source)
            assertEquals("GEMINI", result.provenance.provider)
            assertEquals(PHOTO_SIZE, result.transparentCutout.width)
            coVerify(exactly = 0) { onDeviceEngine.extract(any()) }
        }

    @Test
    fun `a Gemini response with no usable detection falls back to on-device, tagged with the unusable reason`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val cutout = mockk<Bitmap>()
            coEvery { onDeviceEngine.extract(any()) } returns
                ExtractionResult.Success(cutout, 0.4f, onDeviceProvenance())
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Success("""{"detections": []}""", cloudProvenance(provider = "GEMINI"))
            val preferences = fakePreferences(GEMINI_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore, mockk(relaxed = true))

            val result = router.extract(mockk()) as ExtractionResult.Success

            assertSame(cutout, result.transparentCutout)
            assertTrue(result.provenance.fallbackUsed)
            assertEquals(AiFallbackReasons.GEMINI_SEGMENTATION_UNUSABLE, result.provenance.fallbackReason)
        }

    @Test
    fun `a failed Gemini dispatch falls back to on-device, tagged with the real dispatch failure reason`() =
        runTest {
            val onDeviceEngine = mockk<OnDeviceExtractionEngine>()
            val cutout = mockk<Bitmap>()
            coEvery { onDeviceEngine.extract(any()) } returns
                ExtractionResult.Success(cutout, 0.4f, onDeviceProvenance())
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runVisionPrompt(any(), any(), any(), any(), any(), any()) } returns
                VisionPromptResult.Failure("http_error_429")
            val preferences = fakePreferences(GEMINI_CONFIG)
            val apiKeyStore = mockk<ApiKeyStore> { every { getApiKey(any()) } returns "a-real-key" }
            val router =
                GarmentExtractionEngineRouter(onDeviceEngine, gateway, preferences, apiKeyStore, mockk(relaxed = true))

            val result = router.extract(mockk()) as ExtractionResult.Success

            assertTrue(result.provenance.fallbackUsed)
            assertEquals("http_error_429", result.provenance.fallbackReason)
        }
}

private fun fakePreferences(config: AiProviderConfig): AiProviderPreferencesDataStore =
    mockk { every { observeConfig(any()) } returns flowOf(config) }

private fun onDeviceProvenance(): AiResultProvenance =
    AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH)

private fun cloudProvenance(provider: String = "generic"): AiResultProvenance =
    AiResultProvenance(AiResultSource.CLOUD, provider, null, "extraction-v1", Instant.EPOCH)

private fun solidBitmap(
    size: Int,
    argb: Int,
): Bitmap =
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until size) for (x in 0 until size) setPixel(x, y, argb)
    }

private fun base64Png(bitmap: Bitmap): String {
    val bytes = ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }
    return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
}
