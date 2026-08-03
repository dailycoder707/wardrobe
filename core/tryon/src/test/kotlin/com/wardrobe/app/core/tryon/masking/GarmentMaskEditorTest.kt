package com.wardrobe.app.core.tryon.masking

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GarmentMaskEditorTest {
    @Test
    fun `erase zeroes alpha within the brush radius`() {
        val bitmap = solid(20, 20, Color.argb(255, 10, 20, 30))

        val erased = GarmentMaskEditor.erase(bitmap, x = 10, y = 10, radiusPx = 5f)

        assertEquals(0, Color.alpha(erased.getPixel(10, 10)))
    }

    @Test
    fun `erase leaves pixels outside the brush radius untouched`() {
        val bitmap = solid(20, 20, Color.argb(255, 10, 20, 30))

        val erased = GarmentMaskEditor.erase(bitmap, x = 10, y = 10, radiusPx = 2f)

        assertEquals(255, Color.alpha(erased.getPixel(19, 19)))
    }

    @Test
    fun `restore brings back the original alpha after an erase`() {
        val original = solid(20, 20, Color.argb(200, 10, 20, 30))
        val erased = GarmentMaskEditor.erase(original, x = 10, y = 10, radiusPx = 5f)

        val restored = GarmentMaskEditor.restore(erased, original, x = 10, y = 10, radiusPx = 5f)

        assertEquals(200, Color.alpha(restored.getPixel(10, 10)))
    }

    @Test
    fun `erase preserves the color channels, only alpha changes`() {
        val bitmap = solid(20, 20, Color.argb(255, 10, 20, 30))

        val erased = GarmentMaskEditor.erase(bitmap, x = 10, y = 10, radiusPx = 5f)

        val pixel = erased.getPixel(10, 10)
        assertEquals(10, Color.red(pixel))
        assertEquals(20, Color.green(pixel))
        assertEquals(30, Color.blue(pixel))
    }

    private fun solid(
        width: Int,
        height: Int,
        color: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
}
