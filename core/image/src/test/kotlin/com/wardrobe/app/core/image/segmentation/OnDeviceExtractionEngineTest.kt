package com.wardrobe.app.core.image.segmentation

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnDeviceExtractionEngineTest {
    @Test
    fun `extract delegates to the person region masker on a successful cutout, preserving confidence`() =
        runTest {
            val cutoutBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            val maskedBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            val sourcePhoto = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val backgroundRemover = FakeBackgroundRemover(CutoutResult.Success(cutoutBitmap, confidence = 0.77f))
            val personRegionMasker = FakePersonRegionMasker(maskedBitmap)
            val engine = OnDeviceExtractionEngine(backgroundRemover, personRegionMasker)

            val result = engine.extract(sourcePhoto)

            check(result is ExtractionResult.Success)
            assertSame(maskedBitmap, result.transparentCutout)
            assertEquals(0.77f, result.confidence)
            assertSame(cutoutBitmap, personRegionMasker.lastCutoutArgument)
            assertSame(sourcePhoto, personRegionMasker.lastOriginalArgument)
        }

    @Test
    fun `extract propagates a background-removal failure without invoking the masker`() =
        runTest {
            val backgroundRemover = FakeBackgroundRemover(CutoutResult.Failure("mlkit_no_foreground_bitmap"))
            val personRegionMasker = FakePersonRegionMasker(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            val engine = OnDeviceExtractionEngine(backgroundRemover, personRegionMasker)

            val result = engine.extract(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))

            check(result is ExtractionResult.Failure)
            assertEquals("mlkit_no_foreground_bitmap", result.reason)
            assertTrue(!personRegionMasker.wasInvoked)
        }
}

private class FakeBackgroundRemover(
    private val result: CutoutResult,
) : BackgroundRemover {
    override suspend fun removeBackground(bitmap: Bitmap): CutoutResult = result
}

private class FakePersonRegionMasker(
    private val result: Bitmap,
) : PersonRegionMasker {
    var wasInvoked = false
        private set
    var lastCutoutArgument: Bitmap? = null
        private set
    var lastOriginalArgument: Bitmap? = null
        private set

    override suspend fun mask(
        cutout: Bitmap,
        originalPhoto: Bitmap,
    ): Bitmap {
        wasInvoked = true
        lastCutoutArgument = cutout
        lastOriginalArgument = originalPhoto
        return result
    }
}
