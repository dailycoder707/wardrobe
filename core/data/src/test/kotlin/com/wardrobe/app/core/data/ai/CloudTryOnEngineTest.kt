package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.tryon.TryOnRenderResult
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.AiVendor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val CLOUD_CONFIG =
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
private const val MIN_VALID_DIMENSION = 128
private const val TOO_SMALL_DIMENSION = 10

class CloudTryOnEngineTest {
    private fun bitmap(
        width: Int,
        height: Int,
    ): Bitmap =
        mockk {
            every { this@mockk.width } returns width
            every { this@mockk.height } returns height
        }

    private fun provenance() = AiResultProvenance(AiResultSource.CLOUD, "generic", null, "tryon-v1", Instant.EPOCH)

    @Test
    fun `a valid high-resolution image with real confidence is accepted`() =
        runTest {
            val gateway = mockk<AiGateway>()
            val resultImage = bitmap(MIN_VALID_DIMENSION, MIN_VALID_DIMENSION)
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns
                ImageTaskResult.Success(resultImage, 0.8f, provenance())
            val engine = CloudTryOnEngine(gateway)

            val result = engine.render(mockk(), mockk(), null, CLOUD_CONFIG, "a-real-key")

            assertTrue(result is TryOnRenderResult.Success)
            val success = result as TryOnRenderResult.Success
            assertEquals(0.8f, success.confidence)
            assertEquals(AiResultSource.CLOUD, success.source)
        }

    @Test
    fun `an image below minimum resolution is rejected, never silently accepted`() =
        runTest {
            val gateway = mockk<AiGateway>()
            val tooSmall = bitmap(TOO_SMALL_DIMENSION, TOO_SMALL_DIMENSION)
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns
                ImageTaskResult.Success(tooSmall, 0.8f, provenance())
            val engine = CloudTryOnEngine(gateway)

            val result = engine.render(mockk(), mockk(), null, CLOUD_CONFIG, "a-real-key")

            assertTrue(result is TryOnRenderResult.Failure)
            assertEquals(
                "rendered_image_below_minimum_resolution",
                (result as TryOnRenderResult.Failure).reason,
            )
        }

    @Test
    fun `a missing confidence is rejected, never treated as an honest null`() =
        runTest {
            val gateway = mockk<AiGateway>()
            val resultImage = bitmap(MIN_VALID_DIMENSION, MIN_VALID_DIMENSION)
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns
                ImageTaskResult.Success(resultImage, null, provenance())
            val engine = CloudTryOnEngine(gateway)

            val result = engine.render(mockk(), mockk(), null, CLOUD_CONFIG, "a-real-key")

            assertTrue(result is TryOnRenderResult.Failure)
            assertEquals("missing_confidence", (result as TryOnRenderResult.Failure).reason)
        }

    @Test
    fun `a provider dispatch failure surfaces its reason rather than throwing`() =
        runTest {
            val gateway = mockk<AiGateway>()
            coEvery { gateway.runImageTask(any(), any(), any(), any()) } returns
                ImageTaskResult.Failure("http_error_500")
            val engine = CloudTryOnEngine(gateway)

            val result = engine.render(mockk(), mockk(), null, CLOUD_CONFIG, "a-real-key")

            assertTrue(result is TryOnRenderResult.Failure)
            assertEquals("http_error_500", (result as TryOnRenderResult.Failure).reason)
        }
}
