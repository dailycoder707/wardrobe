package com.wardrobe.app.core.ai.privacy

import android.graphics.Bitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultPrivacyPreprocessorTest {
    @Test
    fun `prepareExtractionPayload runs the photo through face blurring`() =
        runTest {
            val faceBlurrer = FakeFaceBlurrer()
            val preprocessor = DefaultPrivacyPreprocessor(faceBlurrer)

            preprocessor.prepareExtractionPayload(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))

            assertTrue("extraction payloads must be face-blurred first", faceBlurrer.wasInvoked)
        }

    @Test
    fun `prepareGarmentPayload never invokes face blurring, since a cutout has no face`() =
        runTest {
            val faceBlurrer = FakeFaceBlurrer()
            val preprocessor = DefaultPrivacyPreprocessor(faceBlurrer)

            preprocessor.prepareGarmentPayload(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))

            assertFalse(
                "a garment cutout is already faceless — blurring it would be pointless work",
                faceBlurrer.wasInvoked,
            )
        }
}

private class FakeFaceBlurrer : FaceBlurrer {
    var wasInvoked = false
        private set

    override suspend fun blurFaces(bitmap: Bitmap): Bitmap {
        wasInvoked = true
        return bitmap
    }
}
