package com.wardrobe.app.core.tryon.lighting

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val REFERENCE_GRAY = 158

@RunWith(RobolectricTestRunner::class)
class LightingMatcherTest {
    @Test
    fun `a photo at the reference mid-tone yields no adjustment`() {
        val adjustment = LightingMatcher.match(solid(Color.rgb(REFERENCE_GRAY, REFERENCE_GRAY, REFERENCE_GRAY)))

        assertEquals(0f, adjustment.brightnessDelta, 1f)
        assertEquals(1f, adjustment.colorGainR, 0.02f)
        assertEquals(1f, adjustment.colorGainG, 0.02f)
        assertEquals(1f, adjustment.colorGainB, 0.02f)
    }

    @Test
    fun `a brighter-than-reference photo yields a positive brightness delta and gain above 1`() {
        val adjustment = LightingMatcher.match(solid(Color.rgb(220, 220, 220)))

        assertTrue(adjustment.brightnessDelta > 0f)
        assertTrue(adjustment.colorGainR > 1f)
    }

    @Test
    fun `a darker-than-reference photo yields a negative brightness delta and gain below 1`() {
        val adjustment = LightingMatcher.match(solid(Color.rgb(40, 40, 40)))

        assertTrue(adjustment.brightnessDelta < 0f)
        assertTrue(adjustment.colorGainR < 1f)
    }

    @Test
    fun `a warm color cast yields a higher red gain than blue gain`() {
        val adjustment = LightingMatcher.match(solid(Color.rgb(200, 158, 100)))

        assertTrue(adjustment.colorGainR > adjustment.colorGainB)
    }

    private fun solid(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }
}
