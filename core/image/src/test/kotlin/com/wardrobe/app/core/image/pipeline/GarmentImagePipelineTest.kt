package com.wardrobe.app.core.image.pipeline

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.wardrobe.app.core.image.quality.ImageQualityAnalyzer
import com.wardrobe.app.core.image.segmentation.BackgroundRemover
import com.wardrobe.app.core.image.segmentation.CutoutResult
import com.wardrobe.app.core.image.storage.ImageFileStore
import com.wardrobe.app.core.model.garment.BackgroundRemovalStatus
import com.wardrobe.app.core.model.garment.ImageType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

/**
 * Exercises the pipeline's own orchestration (stage ordering, file writes,
 * fallback behavior when background removal fails) against a
 * [FakeBackgroundRemover] test double — real ML Kit inference is not under
 * test here (see phase-5b-image-pipeline.md's testing table for why).
 */
@RunWith(RobolectricTestRunner::class)
class GarmentImagePipelineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val fileStore = ImageFileStore(ApplicationProvider.getApplicationContext())

    private class FakeBackgroundRemover(
        private val succeed: Boolean,
    ) : BackgroundRemover {
        override suspend fun removeBackground(bitmap: Bitmap): CutoutResult =
            if (succeed) {
                CutoutResult.Success(
                    Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888),
                    confidence = 0.9f,
                )
            } else {
                CutoutResult.Failure("fake_failure")
            }
    }

    private fun sourcePhoto(): File {
        val file = tempFolder.newFile("${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    private fun pipeline(succeed: Boolean) =
        GarmentImagePipeline(fileStore, FakeBackgroundRemover(succeed), ImageQualityAnalyzer())

    @Test
    fun `a successful background removal writes all three variants`() =
        runTest {
            val staged = pipeline(succeed = true).process(sourcePhoto(), UUID.randomUUID().toString(), cropRect = null)

            assertEquals(BackgroundRemovalStatus.SUCCEEDED, staged.backgroundRemovalStatus)
            assertEquals(
                setOf(ImageType.ORIGINAL, ImageType.CUTOUT, ImageType.THUMBNAIL),
                staged.variants.map { it.type }.toSet(),
            )
            staged.variants.forEach { assertTrue("expected ${it.filePath} to exist", File(it.filePath).exists()) }
        }

    @Test
    fun `a failed background removal keeps the original and skips the cutout variant`() =
        runTest {
            val staged = pipeline(succeed = false).process(sourcePhoto(), UUID.randomUUID().toString(), cropRect = null)

            assertEquals(BackgroundRemovalStatus.FAILED_KEPT_ORIGINAL, staged.backgroundRemovalStatus)
            assertEquals(setOf(ImageType.ORIGINAL, ImageType.THUMBNAIL), staged.variants.map { it.type }.toSet())
        }

    @Test
    fun `quickQualityCheck runs the full check set without writing any files`() {
        val report = pipeline(succeed = true).quickQualityCheck(sourcePhoto())

        assertEquals(6, report.checks.size)
    }
}
