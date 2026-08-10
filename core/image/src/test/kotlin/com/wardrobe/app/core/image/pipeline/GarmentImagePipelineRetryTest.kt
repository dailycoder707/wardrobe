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
import com.wardrobe.app.core.model.garment.ComparisonStageLabel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class GarmentImagePipelineRetryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fileStore = ImageFileStore(ApplicationProvider.getApplicationContext())

    private class SequencedExtractionEngine(
        private val results: MutableList<ExtractionResult>,
    ) : GarmentExtractionEngine {
        var callCount = 0
            private set

        override suspend fun extract(sourcePhoto: Bitmap): ExtractionResult {
            callCount++
            return results.removeAt(0)
        }
    }

    private class PassthroughEnhancer : GarmentPresentationEnhancer {
        override fun enhance(cutout: Bitmap): EnhancedPresentation =
            EnhancedPresentation(
                enhancedCutout = cutout,
                whiteBackgroundVariant = Bitmap.createBitmap(cutout.width, cutout.height, Bitmap.Config.ARGB_8888),
            )
    }

    private class NeverReconstructs : GarmentReconstructionEngine {
        override suspend fun reconstruct(
            cutout: Bitmap,
            extractionConfidence: Float?,
        ): ReconstructionResult = ReconstructionResult.NotAttempted(0f, "no_cloud_provider_configured")
    }

    private class SequencedMetadataEngine(
        private val results: MutableList<List<MetadataSuggestion>>,
    ) : GarmentMetadataEngine {
        var callCount = 0
            private set

        override suspend fun generateMetadata(cutout: Bitmap): List<MetadataSuggestion> {
            callCount++
            return results.removeAt(0)
        }
    }

    private fun suggestion(value: String) =
        MetadataSuggestion(
            field = MetadataField.PRIMARY_COLOR,
            value = value,
            confidence = 0.8f,
            provenance = AiResultProvenance(AiResultSource.ON_DEVICE, null, null, null, Instant.EPOCH),
        )

    private fun sourcePhoto(): File {
        val file = tempFolder.newFile("${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    private fun fakeCutout() = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)

    @Test
    fun `retryMetadata re-runs only metadata generation from the existing cutout`() =
        runTest {
            val extractionEngine =
                SequencedExtractionEngine(mutableListOf(ExtractionResult.Success(fakeCutout(), confidence = 0.9f)))
            val metadataEngine =
                SequencedMetadataEngine(mutableListOf(listOf(suggestion("Gray")), listOf(suggestion("Blue"))))
            val pipeline =
                GarmentImagePipeline(
                    fileStore,
                    extractionEngine,
                    PassthroughEnhancer(),
                    NeverReconstructs(),
                    metadataEngine,
                    ImageQualityAnalyzer(),
                )
            val initial = pipeline.process(sourcePhoto(), UUID.randomUUID().toString(), cropRect = null)
            assertEquals(listOf(suggestion("Gray")), initial.metadataSuggestions)

            val retried = pipeline.retryMetadata(initial)

            assertEquals(listOf(suggestion("Blue")), retried.metadataSuggestions)
            assertEquals(2, metadataEngine.callCount)
            assertEquals(1, extractionEngine.callCount)
            assertEquals(initial.variants, retried.variants)
        }

    @Test
    fun `retryEnhancement re-runs enhancement onward without re-calling extraction`() =
        runTest {
            val extractionEngine =
                SequencedExtractionEngine(mutableListOf(ExtractionResult.Success(fakeCutout(), confidence = 0.9f)))
            val metadataEngine =
                SequencedMetadataEngine(mutableListOf(listOf(suggestion("Gray")), listOf(suggestion("Blue"))))
            val pipeline =
                GarmentImagePipeline(
                    fileStore,
                    extractionEngine,
                    PassthroughEnhancer(),
                    NeverReconstructs(),
                    metadataEngine,
                    ImageQualityAnalyzer(),
                )
            val initial = pipeline.process(sourcePhoto(), UUID.randomUUID().toString(), cropRect = null)

            val retried = pipeline.retryEnhancement(initial)

            assertEquals(listOf(suggestion("Blue")), retried.metadataSuggestions)
            assertEquals(1, extractionEngine.callCount)
            assertEquals(
                listOf(ComparisonStageLabel.ORIGINAL, ComparisonStageLabel.EXTRACTED, ComparisonStageLabel.ENHANCED),
                retried.comparisonStages.map { it.label },
            )
        }

    @Test
    fun `retryExtraction re-runs the full chain from the saved original photo`() =
        runTest {
            val extractionEngine =
                SequencedExtractionEngine(
                    mutableListOf(
                        ExtractionResult.Success(fakeCutout(), confidence = 0.5f),
                        ExtractionResult.Success(fakeCutout(), confidence = 0.95f),
                    ),
                )
            val metadataEngine =
                SequencedMetadataEngine(mutableListOf(listOf(suggestion("Gray")), listOf(suggestion("Blue"))))
            val pipeline =
                GarmentImagePipeline(
                    fileStore,
                    extractionEngine,
                    PassthroughEnhancer(),
                    NeverReconstructs(),
                    metadataEngine,
                    ImageQualityAnalyzer(),
                )
            val initial = pipeline.process(sourcePhoto(), UUID.randomUUID().toString(), cropRect = null)
            assertEquals(0.5f, initial.cutoutConfidence)

            val retried = pipeline.retryExtraction(initial)

            assertEquals(0.95f, retried.cutoutConfidence)
            assertEquals(2, extractionEngine.callCount)
            assertEquals(listOf(suggestion("Blue")), retried.metadataSuggestions)
            retried.variants.forEach {
                assertTrue("expected ${it.filePath} to exist", File(it.filePath).exists())
            }
        }
}
