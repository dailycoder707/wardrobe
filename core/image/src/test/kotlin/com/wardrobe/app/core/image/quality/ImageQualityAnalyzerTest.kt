package com.wardrobe.app.core.image.quality

import android.graphics.Bitmap
import android.graphics.Color
import com.wardrobe.app.core.model.garment.QualityCheckName
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.QualityVerdict
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the Photo Quality Assistant's heuristics against synthetic
 * bitmaps built pixel-by-pixel — Robolectric's `Bitmap` supports real
 * `getPixel`/`setPixel`, so these are genuine checks, not mocked math. Every
 * threshold in `ImageQualityAnalyzer` was calibrated against these cases, not
 * derived from theory alone.
 */
@RunWith(RobolectricTestRunner::class)
class ImageQualityAnalyzerTest {
    private val analyzer = ImageQualityAnalyzer()

    @Test
    fun `a solid-color image is flagged as blurry`() {
        val report = analyzer.analyze(solid(64, gray(128)))
        assertEquals(QualityVerdict.FAIL, verdictFor(report, QualityCheckName.SHARPNESS))
    }

    @Test
    fun `a checkerboard image is not flagged as blurry`() {
        val report = analyzer.analyze(checkerboard(64))
        assertEquals(QualityVerdict.PASS, verdictFor(report, QualityCheckName.SHARPNESS))
    }

    @Test
    fun `a very dark image fails the brightness check`() {
        val report = analyzer.analyze(solid(64, gray(5)))
        assertEquals(QualityVerdict.FAIL, verdictFor(report, QualityCheckName.BRIGHTNESS))
    }

    @Test
    fun `a well-lit mid-gray image passes brightness`() {
        val report = analyzer.analyze(solid(64, gray(140)))
        assertEquals(QualityVerdict.PASS, verdictFor(report, QualityCheckName.BRIGHTNESS))
    }

    @Test
    fun `a small proportion of blown-out pixels triggers an exposure warning`() {
        val totalPixels = 64 * 64
        val blownOutCount = (totalPixels * 0.15).toInt()
        val bitmap =
            bitmapOf(64) { x, y ->
                val index = y * 64 + x
                if (index < blownOutCount) Color.WHITE else gray(140)
            }

        val report = analyzer.analyze(bitmap)

        assertEquals(QualityVerdict.WARNING, verdictFor(report, QualityCheckName.EXPOSURE))
    }

    @Test
    fun `an image below the minimum resolution fails the resolution check`() {
        val report = analyzer.analyze(solid(100, gray(140)))
        assertEquals(QualityVerdict.FAIL, verdictFor(report, QualityCheckName.RESOLUTION))
    }

    @Test
    fun `a high-resolution image passes the resolution check`() {
        val report = analyzer.analyze(solid(1200, gray(140)))
        assertEquals(QualityVerdict.PASS, verdictFor(report, QualityCheckName.RESOLUTION))
    }

    @Test
    fun `a centered subject distinct from the border passes framing`() {
        val bitmap =
            bitmapOf(64) { x, y ->
                if (x in 16..47 && y in 16..47) gray(20) else gray(220)
            }

        val report = analyzer.analyze(bitmap)

        assertEquals(QualityVerdict.PASS, verdictFor(report, QualityCheckName.FRAMING))
    }

    @Test
    fun `a subject touching the frame edge triggers a framing warning`() {
        val bitmap =
            bitmapOf(64) { x, y ->
                if (x in 0..47 && y in 16..47) gray(20) else gray(220)
            }

        val report = analyzer.analyze(bitmap)

        assertEquals(QualityVerdict.WARNING, verdictFor(report, QualityCheckName.FRAMING))
    }

    @Test
    fun `a near-uniform image with no clear subject triggers a framing warning`() {
        val report = analyzer.analyze(solid(64, gray(140)))
        assertEquals(QualityVerdict.WARNING, verdictFor(report, QualityCheckName.FRAMING))
    }

    @Test
    fun `a uniform background passes the complexity check`() {
        val report = analyzer.analyze(solid(64, gray(220)))
        assertEquals(QualityVerdict.PASS, verdictFor(report, QualityCheckName.BACKGROUND_COMPLEXITY))
    }

    @Test
    fun `a noisy border triggers a background-complexity warning`() {
        val report = analyzer.analyze(checkerboard(64))
        assertEquals(QualityVerdict.WARNING, verdictFor(report, QualityCheckName.BACKGROUND_COMPLEXITY))
    }

    private fun verdictFor(
        report: QualityReport,
        name: QualityCheckName,
    ) = report.checks.first { it.name == name }.verdict

    private fun gray(value: Int): Int = Color.rgb(value, value, value)

    private fun solid(
        size: Int,
        color: Int,
    ): Bitmap = bitmapOf(size) { _, _ -> color }

    private fun checkerboard(size: Int): Bitmap =
        bitmapOf(size) { x, y -> if ((x + y) % 2 == 0) Color.BLACK else Color.WHITE }

    private fun bitmapOf(
        size: Int,
        colorAt: (x: Int, y: Int) -> Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (y in 0 until size) {
            for (x in 0 until size) {
                bitmap.setPixel(x, y, colorAt(x, y))
            }
        }
        return bitmap
    }
}
