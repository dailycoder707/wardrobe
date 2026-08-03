package com.wardrobe.app.core.tryon.shadow

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShadowRendererTest {
    @Test
    fun `a fully opaque pixel becomes a fixed-fraction alpha black pixel`() {
        val cutout = solid(Color.argb(255, 200, 30, 30))

        val shadow = ShadowRenderer.deriveShadowSilhouette(cutout)

        val pixel = shadow.getPixel(0, 0)
        assertEquals(0, Color.red(pixel))
        assertEquals(0, Color.green(pixel))
        assertEquals(0, Color.blue(pixel))
        assertEquals(89, Color.alpha(pixel))
    }

    @Test
    fun `a fully transparent pixel stays fully transparent`() {
        val cutout = solid(Color.argb(0, 200, 30, 30))

        val shadow = ShadowRenderer.deriveShadowSilhouette(cutout)

        assertEquals(0, Color.alpha(shadow.getPixel(0, 0)))
    }

    @Test
    fun `a half-transparent pixel's shadow alpha scales proportionally`() {
        val cutout = solid(Color.argb(128, 0, 0, 0))

        val shadow = ShadowRenderer.deriveShadowSilhouette(cutout)

        assertEquals(44, Color.alpha(shadow.getPixel(0, 0)))
    }

    @Test
    fun `blur is supported at API 31 and above`() {
        assertTrue(ShadowRenderer.supportsBlur(sdkInt = 31))
        assertTrue(ShadowRenderer.supportsBlur(sdkInt = 34))
    }

    @Test
    fun `blur is not supported below API 31, this project's actual minSdk range`() {
        assertFalse(ShadowRenderer.supportsBlur(sdkInt = 26))
        assertFalse(ShadowRenderer.supportsBlur(sdkInt = 30))
    }

    private fun solid(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
}
