package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.image.metadata.GarmentMetadataEngine
import com.wardrobe.app.core.image.presentation.EnhancedPresentation
import com.wardrobe.app.core.image.presentation.GarmentPresentationEnhancer
import com.wardrobe.app.core.image.quality.ImageQualityAnalyzer
import com.wardrobe.app.core.image.reconstruction.GarmentReconstructionEngine
import com.wardrobe.app.core.image.reconstruction.ReconstructionResult
import com.wardrobe.app.core.image.segmentation.ExtractionResult
import com.wardrobe.app.core.image.segmentation.GarmentExtractionEngine
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.ComparisonStageLabel
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ReconstructionOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Exercises the pipeline's own orchestration (stage ordering, file writes,
 * fallback behavior when extraction fails) against fake engine test doubles
 * for all four AI-capability stages — real ML Kit/cloud inference is not
 * under test here (see phase-5b-image-pipeline.md's testing table for why).
 */
@RunWith(RobolectricTestRunner::class)
class GarmentImagePipelineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fileStore = ImageFileStore(ApplicationProvider.getApplicationContext())

    private class FakeExtractionEngine(
        private val succeed: Boolean,
    ) : GarmentExtractionEngine {
        override suspend fun extract(sourcePhoto: Bitmap): ExtractionResult =
            if (succeed) {
                ExtractionResult.Success(
                    Bitmap.createBitmap(sourcePhoto.width, sourcePhoto.height, Bitmap.Config.ARGB_8888),
                    confidence = 0.9f,
                    provenance = AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH),
                )
            } else {
                ExtractionResult.Failure("fake_failure")
            }
    }

    private class FakePresentationEnhancer : GarmentPresentationEnhancer {
        override fun enhance(cutout: Bitmap): EnhancedPresentation =
            EnhancedPresentation(
                enhancedCutout = cutout,
                whiteBackgroundVariant = Bitmap.createBitmap(cutout.width, cutout.height, Bitmap.Config.ARGB_8888),
            )
    }

    private class FakeReconstructionEngine(
        private val occlusionSeverity: Float = 0f,
    ) : GarmentReconstructionEngine {
        override suspend fun reconstruct(
            cutout: Bitmap,
            extractionConfidence: Float?,
        ): ReconstructionResult = ReconstructionResult.NotAttempted(occlusionSeverity, "no_cloud_provider_configured")
    }

    private class FakeMetadataEngine(
        private val suggestions: List<MetadataSuggestion> = emptyList(),
    ) : GarmentMetadataEngine {
        override suspend fun generateMetadata(cutout: Bitmap): List<MetadataSuggestion> = suggestions
    }

    private fun sourcePhoto(): File {
        val file = tempFolder.newFile("${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    private fun pipeline(
        extractionSucceeds: Boolean,
        metadataSuggestions: List<MetadataSuggestion> = emptyList(),
    ) = GarmentImagePipeline(
        fileStore,
        FakeExtractionEngine(extractionSucceeds),
        FakePresentationEnhancer(),
        FakeReconstructionEngine(),
        FakeMetadataEngine(metadataSuggestions),
        ImageQualityAnalyzer(),
    )

    @Test
    fun `a successful extraction writes all four variants`() =
        runTest {
            val staged =
                pipeline(
                    extractionSucceeds = true,
                ).process(sourcePhoto(), UUID.randomUUID().toString(), cropRect = null)

            assertEquals(BackgroundRemovalStatus.SUCCEEDED, staged.backgroundRemovalStatus)
            assertEquals(
                setOf(ImageType.ORIGINAL, ImageType.CUTOUT, ImageType.WHITE_BACKGROUND, ImageType.THUMBNAIL),
                staged.variants.map { it.type }.toSet(),
            )
            staged.variants.forEach { assertTrue("expected ${it.filePath} to exist", File(it.filePath).exists()) }
        }

    @Test
    fun `a successful extraction produces comparison stages for original, extracted, and enhanced`() =
        runTest {
            val staged =
                pipeline(
                    extractionSucceeds = true,
                ).process(sourcePhoto(), UUID.randomUUID().toString(), cropRect = null)

            assertEquals(
                listOf(ComparisonStageLabel.ORIGINAL, ComparisonStageLabel.EXTRACTED, ComparisonStageLabel.ENHANCED),
                staged.comparisonStages.map { it.label },
            )
            staged.comparisonStages.forEach {
                assertTrue("expected ${it.filePath} to exist", File(it.filePath).exists())
            }
        }

    @Test
    fun `a failed extraction keeps the original and skips the cutout and white-background variants`() =
        runTest {
            val staged =
                pipeline(
                    extractionSucceeds = false,
                ).process(sourcePhoto(), UUID.randomUUID().toString(), cropRect = null)

            assertEquals(BackgroundRemovalStatus.FAILED_KEPT_ORIGINAL, staged.backgroundRemovalStatus)
            assertEquals(setOf(ImageType.ORIGINAL, ImageType.THUMBNAIL), staged.variants.map { it.type }.toSet())
            assertEquals(ReconstructionOutcome.NOT_ATTEMPTED, staged.reconstructionOutcome)
            assertTrue("expected no metadata suggestions without a cutout", staged.metadataSuggestions.isEmpty())
            assertEquals(listOf(ComparisonStageLabel.ORIGINAL), staged.comparisonStages.map { it.label })
            assertNull("no AI summary without a cutout to describe", staged.aiProcessingSummary)
        }

    @Test
    fun `a successful extraction carries the on-device metadata engine's suggestions through`() =
        runTest {
            val suggestion =
                MetadataSuggestion(
                    field = MetadataField.PRIMARY_COLOR,
                    value = "Gray",
                    confidence = 0.8f,
                    provenance = AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH),
                )
            val staged =
                pipeline(extractionSucceeds = true, metadataSuggestions = listOf(suggestion))
                    .process(sourcePhoto(), UUID.randomUUID().toString(), cropRect = null)

            assertEquals(listOf(suggestion), staged.metadataSuggestions)
            val summary = staged.aiProcessingSummary
            assertTrue("expected a real AI processing summary", summary != null)
            assertEquals(0.8f, summary!!.averageConfidence)
            assertTrue("expected real non-negative processing time", summary.processingMs >= 0)
        }

    @Test
    fun `quickQualityCheck runs the full check set without writing any files`() {
        val report = pipeline(extractionSucceeds = true).quickQualityCheck(sourcePhoto())

        assertEquals(6, report.checks.size)
    }
}
