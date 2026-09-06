package com.wardrobe.app.core.ai.privacy

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val SIZE = 48

@RunWith(RobolectricTestRunner::class)
class FaceBlurrerTest {
    @Test
    fun `pixelateRegions makes every pixel in a block identical, obscuring the original detail`() {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        // A fine checkerboard inside the face region — real per-pixel detail
        // that pixelation must destroy.
        for (y in 10 until 34) {
            for (x in 10 until 34) {
                bitmap.setPixel(x, y, if ((x + y) % 2 == 0) Color.BLACK else Color.WHITE)
            }
        }

        val result = pixelateRegions(bitmap, listOf(Rect(10, 10, 34, 34)))

        // Every pixel in the top-left 12x12 block (the first pixelation
        // block, given PIXELATION_FACTOR=12) must now be identical — the
        // checkerboard detail must be gone.
        val blockColor = result.getPixel(10, 10)
        var uniform = true
        for (y in 10 until 22) {
            for (x in 10 until 22) {
                if (result.getPixel(x, y) != blockColor) uniform = false
            }
        }
        assertTrue("expected a pixelated block to be a single uniform color", uniform)
    }

    @Test
    fun `pixelateRegions leaves pixels outside the given rects untouched`() {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.RED)

        val result = pixelateRegions(bitmap, listOf(Rect(20, 20, 30, 30)))

        assertEquals(Color.RED, result.getPixel(0, 0))
    }

    @Test
    fun `pixelateRegions clamps a rect that extends past the bitmap bounds instead of crashing`() {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)

        // Should not throw even though this rect reaches far outside the bitmap.
        pixelateRegions(bitmap, listOf(Rect(-10, -10, SIZE + 50, SIZE + 50)))
    }
}
