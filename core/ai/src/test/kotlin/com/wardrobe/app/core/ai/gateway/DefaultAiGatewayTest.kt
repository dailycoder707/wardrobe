package com.wardrobe.app.core.ai.gateway

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.wardrobe.app.core.ai.job.AiCapabilityWorker
import com.wardrobe.app.core.ai.job.AiJobManager
import com.wardrobe.app.core.ai.job.AiWorkRegistry
import com.wardrobe.app.core.ai.job.FakeAiJobDao
import com.wardrobe.app.core.ai.job.pumpLooperUntilBothComplete
import com.wardrobe.app.core.ai.job.pumpLooperUntilComplete
import com.wardrobe.app.core.ai.privacy.PrivacyPreprocessor
import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiProviderMode
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.AiVendor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

private const val BITMAP_SIZE = 8

@RunWith(RobolectricTestRunner::class)
class DefaultAiGatewayTest {
    private lateinit var context: Context
    private lateinit var aiResultCacheDao: FakeAiResultCacheDao
    private lateinit var aiMetrics: FakeAiMetrics
    private lateinit var visionAdapter: FakeVisionPromptAdapter
    private lateinit var imageAdapter: FakeImageTaskAdapter
    private lateinit var gateway: DefaultAiGateway

    private val cloudConfig =
        AiProviderConfig(
            capability = AiCapability.GARMENT_METADATA,
            mode = AiProviderMode.CLOUD,
            vendor = AiVendor.OPENAI,
            baseUrl = "https://api.openai.com",
            model = "gpt-vision",
            costRatePerThousandTokens = null,
            consentGrantedAt = Instant.EPOCH,
            consentHost = "https://api.openai.com",
        )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val registry = AiWorkRegistry()
        val config =
            Configuration
                .Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            if (workerClassName == AiCapabilityWorker::class.java.name) {
                                AiCapabilityWorker(appContext, workerParameters, registry)
                            } else {
                                null
                            }
                    },
                ).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
        val jobManager = AiJobManager(WorkManager.getInstance(context), registry, FakeAiJobDao(), clock)

        aiResultCacheDao = FakeAiResultCacheDao()
        aiMetrics = FakeAiMetrics()
        visionAdapter = FakeVisionPromptAdapter()
        imageAdapter = FakeImageTaskAdapter()

        gateway =
            DefaultAiGateway(
                privacyPreprocessor = PassThroughPrivacyPreprocessor(),
                aiResultCacheDao = aiResultCacheDao,
                imageCacheStore = AiResultImageCacheStore(context),
                aiJobManager = jobManager,
                aiMetrics = aiMetrics,
                visionPromptAdapters = mapOf(AiVendor.OPENAI to visionAdapter),
                imageTaskAdapters = mapOf(AiVendor.OPENAI to imageAdapter),
                clock = clock,
            )
    }

    @Test
    fun `runVisionPrompt on success caches the result and records a SUCCESS metric with cloud provenance`() {
        val dispatchCtx = dispatchContext()

        val result =
            pumpLooperUntilComplete(context) {
                gateway.runVisionPrompt(dispatchCtx, "metadata-v1", "system", "user", newBitmap())
            } as VisionPromptResult.Success

        assertEquals("suggested-json", result.rawResponseText)
        assertEquals(AiResultSource.CLOUD, result.provenance.source)
        assertEquals(1, visionAdapter.invocationCount)
        assertEquals(1, aiMetrics.recordedEvents.count { it.outcome == AiCallOutcome.SUCCESS })
    }

    @Test
    fun `runVisionPrompt threads expectJsonResponse through to the adapter request, false by default`() {
        val dispatchCtx = dispatchContext()

        pumpLooperUntilComplete(context) {
            gateway.runVisionPrompt(dispatchCtx, "metadata-v1", "system", "user", newBitmap())
        }

        assertEquals(false, visionAdapter.lastRequest?.expectJsonResponse)
    }

    @Test
    fun `runVisionPrompt with expectJsonResponse true passes it through to the adapter request`() {
        val dispatchCtx = dispatchContext()

        pumpLooperUntilComplete(context) {
            gateway.runVisionPrompt(
                dispatchCtx,
                "metadata-v1",
                "system",
                "user",
                newBitmap(),
                expectJsonResponse = true,
            )
        }

        assertEquals(true, visionAdapter.lastRequest?.expectJsonResponse)
    }

    @Test
    fun `runVisionPrompt never repeats an identical call, serving the second one from cache`() {
        val dispatchCtx = dispatchContext()
        val image = newBitmap()

        pumpLooperUntilComplete(context) {
            gateway.runVisionPrompt(dispatchCtx, "metadata-v1", "system", "user", image)
            gateway.runVisionPrompt(dispatchCtx, "metadata-v1", "system", "user", image)
        }

        assertEquals(1, visionAdapter.invocationCount)
        assertEquals(1, aiMetrics.recordedEvents.count { it.outcome == AiCallOutcome.CACHE_HIT })
    }

    @Test
    fun `runVisionPrompt's cache key varies with promptVersion, never serving a stale prompt's result`() {
        val dispatchCtx = dispatchContext()
        val image = newBitmap()

        pumpLooperUntilComplete(context) {
            gateway.runVisionPrompt(dispatchCtx, "metadata-v1", "system", "user", image)
            gateway.runVisionPrompt(dispatchCtx, "metadata-v2", "system", "user", image)
        }

        assertEquals(2, visionAdapter.invocationCount)
    }

    @Test
    fun `runVisionPrompt's cache key varies with the vendor, never serving another vendor's cached result`() {
        val dispatchCtx = dispatchContext()
        val image = newBitmap()

        val secondResult =
            pumpLooperUntilComplete(context) {
                gateway.runVisionPrompt(dispatchCtx, "metadata-v1", "system", "user", image)
                val otherVendorCtx = dispatchCtx.copy(config = dispatchCtx.config.copy(vendor = AiVendor.AZURE_OPENAI))
                gateway.runVisionPrompt(otherVendorCtx, "metadata-v1", "system", "user", image)
            }

        // No adapter is registered for AZURE_OPENAI in this test's gateway, so
        // the only way this could return Success is a wrongful cache hit
        // serving OpenAI's cached result under a different vendor — a correct
        // cache miss instead falls through to "no adapter for this vendor."
        assertTrue(secondResult is VisionPromptResult.Failure)
        assertEquals(0, aiMetrics.recordedEvents.count { it.outcome == AiCallOutcome.CACHE_HIT })
    }

    @Test
    fun `runVisionPrompt on adapter failure records a FAILURE metric and returns Failure`() {
        visionAdapter.nextResult = VisionPromptAdapterResult.Failure("bad_request")
        val dispatchCtx = dispatchContext()

        val result =
            pumpLooperUntilComplete(context) {
                gateway.runVisionPrompt(dispatchCtx, "metadata-v1", "system", "user", newBitmap())
            }

        assertTrue(result is VisionPromptResult.Failure)
        assertEquals(1, aiMetrics.recordedEvents.count { it.outcome == AiCallOutcome.FAILURE })
    }

    /** M24 Phase 8/11#9 — a real network failure (the adapter's HTTP call
     * itself throwing, not returning a well-formed `Failure` result) must
     * become an honest [VisionPromptResult.Failure] the router can fall
     * back to on-device from, never an uncaught exception reaching the
     * caller or a fabricated success. */
    @Test
    fun `runVisionPrompt on a real network failure (IOException) returns an honest Failure, never crashes`() {
        visionAdapter.exceptionToThrow = IOException("unable to resolve host")
        val dispatchCtx = dispatchContext()

        val result =
            pumpLooperUntilComplete(context) {
                gateway.runVisionPrompt(dispatchCtx, "metadata-v1", "system", "user", newBitmap())
            }

        assertTrue(result is VisionPromptResult.Failure)
        assertEquals(1, aiMetrics.recordedEvents.count { it.outcome == AiCallOutcome.TIMEOUT })
    }

    @Test
    fun `two concurrent runVisionPrompt calls for the identical request record only one SUCCESS metric`() {
        val dispatchCtx = dispatchContext()
        val image = newBitmap()

        pumpLooperUntilBothComplete(context) {
            gateway.runVisionPrompt(dispatchCtx, "metadata-v1", "system", "user", image)
        }

        assertEquals(1, visionAdapter.invocationCount)
        assertEquals(1, aiMetrics.recordedEvents.count { it.outcome == AiCallOutcome.SUCCESS })
        assertEquals(1, aiResultCacheDao.upsertCallCount)
    }

    @Test
    fun `runImageTask never repeats an identical call, serving the second one from a cached file`() {
        val dispatchCtx = dispatchContext().copy(capability = AiCapability.GARMENT_EXTRACTION)
        val image = newBitmap()

        val (first, second) =
            pumpLooperUntilComplete(context) {
                val firstResult = gateway.runImageTask(dispatchCtx, "extraction-v1", "extract", listOf(image))
                val secondResult = gateway.runImageTask(dispatchCtx, "extraction-v1", "extract", listOf(image))
                firstResult as ImageTaskResult.Success to secondResult as ImageTaskResult.Success
            }

        assertEquals(1, imageAdapter.invocationCount)
        assertEquals(second.resultImage.width, first.resultImage.width)
    }

    @Test
    fun `runImageTask with multiple images never repeats an identical multi-image call`() {
        val dispatchCtx = dispatchContext().copy(capability = AiCapability.VIRTUAL_TRY_ON)
        val bodyPhoto = newBitmap()
        val garmentCutout = distinctBitmap()

        pumpLooperUntilComplete(context) {
            gateway.runImageTask(dispatchCtx, "tryon-v1", "try_on_render", listOf(bodyPhoto, garmentCutout))
            gateway.runImageTask(dispatchCtx, "tryon-v1", "try_on_render", listOf(bodyPhoto, garmentCutout))
        }

        assertEquals(1, imageAdapter.invocationCount)
    }

    @Test
    fun `runImageTask's cache key varies with every image, not just the first`() {
        val dispatchCtx = dispatchContext().copy(capability = AiCapability.VIRTUAL_TRY_ON)
        val bodyPhoto = newBitmap()
        val garmentA = distinctBitmap()
        val garmentB = distinctBitmap()

        pumpLooperUntilComplete(context) {
            gateway.runImageTask(dispatchCtx, "tryon-v1", "try_on_render", listOf(bodyPhoto, garmentA))
            gateway.runImageTask(dispatchCtx, "tryon-v1", "try_on_render", listOf(bodyPhoto, garmentB))
        }

        assertEquals(2, imageAdapter.invocationCount)
    }

    private fun dispatchContext(): AiDispatchContext =
        AiDispatchContext(capability = AiCapability.GARMENT_METADATA, config = cloudConfig, apiKey = "test-key")

    private fun newBitmap(): Bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)

    /** A fresh bitmap with real, randomized pixel content — unlike
     * [newBitmap], two calls never hash identically, needed to prove the
     * cache key actually varies with this specific image. */
    private fun distinctBitmap(): Bitmap = newBitmap().apply { setPixel(0, 0, Random.nextInt()) }
}

private class PassThroughPrivacyPreprocessor : PrivacyPreprocessor {
    override suspend fun prepareExtractionPayload(originalPhoto: Bitmap): Bitmap = originalPhoto

    override suspend fun prepareGarmentPayload(cutout: Bitmap): Bitmap = cutout
}

private class FakeVisionPromptAdapter : VisionPromptAdapter {
    var invocationCount = 0
        private set
    var lastRequest: VisionPromptAdapterRequest? = null
        private set
    var nextResult: VisionPromptAdapterResult =
        VisionPromptAdapterResult.Success("suggested-json", estimatedInputTokens = 10, estimatedOutputTokens = 5)

    /** M24 — set to simulate a real network-layer failure (the HTTP call
     * itself throwing) rather than the adapter returning a well-formed
     * [VisionPromptAdapterResult.Failure]; only [java.io.IOException] and
     * [kotlinx.coroutines.TimeoutCancellationException] are realistic here,
     * matching what [AiCapabilityWorker] actually forwards. */
    var exceptionToThrow: Throwable? = null

    override suspend fun run(request: VisionPromptAdapterRequest): VisionPromptAdapterResult {
        invocationCount++
        lastRequest = request
        exceptionToThrow?.let { throw it }
        return nextResult
    }
}

private class FakeImageTaskAdapter : ImageTaskAdapter {
    var invocationCount = 0
        private set

    override suspend fun run(request: ImageTaskAdapterRequest): ImageTaskAdapterResult {
        invocationCount++
        return ImageTaskAdapterResult.Success(request.images.first(), confidence = 0.9f)
    }
}
