package com.wardrobe.app.core.image.quality

import android.graphics.Bitmap
import com.wardrobe.app.core.model.garment.QualityCheck
import com.wardrobe.app.core.model.garment.QualityCheckName
import com.wardrobe.app.core.model.garment.QualityReport
import com.wardrobe.app.core.model.garment.QualityVerdict
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * The Photo Quality Assistant — six lightweight, model-free heuristics, chosen
 * specifically so this can run *before* the comparatively expensive
 * background-removal stage (see phase-5b-image-pipeline.md). None of this uses
 * ML Kit; it operates on a small downsampled grid so the cost stays roughly
 * constant regardless of the source photo's actual resolution.
 *
 * Every threshold below is a calibrated heuristic, not a derived constant —
 * verified against synthetic test bitmaps in `ImageQualityAnalyzerTest`
 * (solid-color, checkerboard, dim, blown-out, off-center), not asserted from
 * theory alone.
 */
class ImageQualityAnalyzer
    @Inject
    constructor() {
        fun analyze(bitmap: Bitmap): QualityReport {
            val checks = mutableListOf<QualityCheck>()
            checks += resolutionCheck(bitmap.width, bitmap.height)

            val analysis = downsample(bitmap, ANALYSIS_SIZE)
            val luminance = luminanceGrid(analysis)

            checks += sharpnessCheck(luminance, analysis.width, analysis.height)
            checks += brightnessCheck(luminance)
            checks += exposureCheck(luminance)

            val border = BorderSample.from(luminance, analysis.width, analysis.height)
            checks += framingCheck(luminance, analysis.width, analysis.height, border)
            checks += backgroundComplexityCheck(border)

            return QualityReport(checks)
        }

        private fun resolutionCheck(
            width: Int,
            height: Int,
        ): QualityCheck {
            val shortEdge = min(width, height)
            return when {
                shortEdge < MIN_SHORT_EDGE_PX -> {
                    QualityCheck(
                        QualityCheckName.RESOLUTION,
                        QualityVerdict.FAIL,
                        "Image resolution is too low (${width}x$height)",
                    )
                }

                shortEdge < RECOMMENDED_SHORT_EDGE_PX -> {
                    QualityCheck(
                        QualityCheckName.RESOLUTION,
                        QualityVerdict.WARNING,
                        "Image resolution is lower than recommended (${width}x$height)",
                    )
                }

                else -> {
                    QualityCheck(QualityCheckName.RESOLUTION, QualityVerdict.PASS, "Resolution is good")
                }
            }
        }

        private fun sharpnessCheck(
            luminance: IntArray,
            width: Int,
            height: Int,
        ): QualityCheck {
            var sum = 0.0
            var sumSquares = 0.0
            var count = 0
            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val center = luminance[y * width + x]
                    val up = luminance[(y - 1) * width + x]
                    val down = luminance[(y + 1) * width + x]
                    val left = luminance[y * width + x - 1]
                    val right = luminance[y * width + x + 1]
                    val laplacian = (LAPLACIAN_CENTER_WEIGHT * center - up - down - left - right).toDouble()
                    sum += laplacian
                    sumSquares += laplacian * laplacian
                    count++
                }
            }
            val variance = if (count == 0) 0.0 else (sumSquares / count) - (sum / count).let { it * it }
            return when {
                variance < SHARPNESS_FAIL_BELOW -> {
                    QualityCheck(QualityCheckName.SHARPNESS, QualityVerdict.FAIL, "Image appears very blurry")
                }

                variance < SHARPNESS_WARNING_BELOW -> {
                    QualityCheck(QualityCheckName.SHARPNESS, QualityVerdict.WARNING, "Image may be slightly blurry")
                }

                else -> {
                    QualityCheck(QualityCheckName.SHARPNESS, QualityVerdict.PASS, "Image is sharp")
                }
            }
        }

        private fun brightnessCheck(luminance: IntArray): QualityCheck {
            val mean = luminance.average()
            return when {
                mean < BRIGHTNESS_FAIL_BELOW || mean > BRIGHTNESS_FAIL_ABOVE -> {
                    QualityCheck(
                        QualityCheckName.BRIGHTNESS,
                        QualityVerdict.FAIL,
                        if (mean < BRIGHTNESS_FAIL_BELOW) "Image is far too dark" else "Image is far too bright",
                    )
                }

                mean < BRIGHTNESS_WARNING_BELOW || mean > BRIGHTNESS_WARNING_ABOVE -> {
                    QualityCheck(
                        QualityCheckName.BRIGHTNESS,
                        QualityVerdict.WARNING,
                        if (mean < BRIGHTNESS_WARNING_BELOW) "Image is a bit dark" else "Image is a bit bright",
                    )
                }

                else -> {
                    QualityCheck(QualityCheckName.BRIGHTNESS, QualityVerdict.PASS, "Good lighting")
                }
            }
        }

        private fun exposureCheck(luminance: IntArray): QualityCheck {
            val clipped = luminance.count { it <= EXPOSURE_CLIP_LOW || it >= EXPOSURE_CLIP_HIGH }
            val fraction = clipped.toDouble() / luminance.size
            return when {
                fraction >= EXPOSURE_FAIL_FRACTION -> {
                    QualityCheck(
                        QualityCheckName.EXPOSURE,
                        QualityVerdict.FAIL,
                        "Large parts of the image are over/under-exposed",
                    )
                }

                fraction >= EXPOSURE_WARNING_FRACTION -> {
                    QualityCheck(
                        QualityCheckName.EXPOSURE,
                        QualityVerdict.WARNING,
                        "Some parts of the image are over/under-exposed",
                    )
                }

                else -> {
                    QualityCheck(QualityCheckName.EXPOSURE, QualityVerdict.PASS, "Exposure looks good")
                }
            }
        }

        private fun framingCheck(
            luminance: IntArray,
            width: Int,
            height: Int,
            border: BorderSample,
        ): QualityCheck {
            var minX = width
            var maxX = -1
            var minY = height
            var maxY = -1
            var foregroundCount = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val diff = kotlin.math.abs(luminance[y * width + x] - border.meanLuminance)
                    if (diff > FRAMING_DIFF_THRESHOLD) {
                        foregroundCount++
                        minX = min(minX, x)
                        maxX = max(maxX, x)
                        minY = min(minY, y)
                        maxY = max(maxY, y)
                    }
                }
            }
            val totalPixels = width * height
            val foregroundFraction = foregroundCount.toDouble() / totalPixels
            val margin = max(1, width / EDGE_MARGIN_DIVISOR)
            val touchesEdge =
                foregroundCount > 0 &&
                    (minX <= margin || minY <= margin || maxX >= width - 1 - margin || maxY >= height - 1 - margin)

            return when {
                foregroundCount == 0 || foregroundFraction < FRAMING_MIN_FRACTION -> {
                    QualityCheck(QualityCheckName.FRAMING, QualityVerdict.WARNING, "Garment appears small in the frame")
                }

                touchesEdge -> {
                    QualityCheck(QualityCheckName.FRAMING, QualityVerdict.WARNING, "Garment may be partially cropped")
                }

                else -> {
                    QualityCheck(QualityCheckName.FRAMING, QualityVerdict.PASS, "Framing looks good")
                }
            }
        }

        private fun backgroundComplexityCheck(border: BorderSample): QualityCheck =
            when {
                border.variance > BACKGROUND_COMPLEXITY_FAIL_ABOVE -> {
                    QualityCheck(
                        QualityCheckName.BACKGROUND_COMPLEXITY,
                        QualityVerdict.WARNING,
                        "Background appears cluttered",
                    )
                }

                else -> {
                    QualityCheck(QualityCheckName.BACKGROUND_COMPLEXITY, QualityVerdict.PASS, "Background looks clean")
                }
            }

        private fun downsample(
            bitmap: Bitmap,
            targetSize: Int,
        ): Bitmap = Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)

        private fun luminanceGrid(bitmap: Bitmap): IntArray {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            return IntArray(pixels.size) { i ->
                val pixel = pixels[i]
                val r = (pixel shr RED_SHIFT) and BYTE_MASK
                val g = (pixel shr GREEN_SHIFT) and BYTE_MASK
                val b = pixel and BYTE_MASK
                (LUMA_R * r + LUMA_G * g + LUMA_B * b).toInt()
            }
        }

        /** Mean/variance of a thin band around the image's four edges — used as
         * both the assumed background color (for [framingCheck]) and the
         * background-complexity signal, since a cluttered background is exactly
         * what makes the next pipeline stage (background removal) more likely to
         * produce a bad edge. */
        private class BorderSample(
            val meanLuminance: Int,
            val variance: Double,
        ) {
            companion object {
                fun from(
                    luminance: IntArray,
                    width: Int,
                    height: Int,
                ): BorderSample {
                    val band = max(1, width / BORDER_BAND_DIVISOR)
                    val values = mutableListOf<Int>()
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            val onBorder = x < band || y < band || x >= width - band || y >= height - band
                            if (onBorder) values += luminance[y * width + x]
                        }
                    }
                    val mean = values.average()
                    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
                    return BorderSample(mean.toInt(), variance)
                }
            }
        }

        private companion object {
            const val ANALYSIS_SIZE = 64
            const val MIN_SHORT_EDGE_PX = 400
            const val RECOMMENDED_SHORT_EDGE_PX = 900

            const val SHARPNESS_FAIL_BELOW = 4.0
            const val SHARPNESS_WARNING_BELOW = 40.0

            const val BRIGHTNESS_FAIL_BELOW = 25.0
            const val BRIGHTNESS_FAIL_ABOVE = 235.0
            const val BRIGHTNESS_WARNING_BELOW = 60.0
            const val BRIGHTNESS_WARNING_ABOVE = 200.0

            const val EXPOSURE_CLIP_LOW = 5
            const val EXPOSURE_CLIP_HIGH = 250
            const val EXPOSURE_WARNING_FRACTION = 0.10
            const val EXPOSURE_FAIL_FRACTION = 0.30

            const val FRAMING_DIFF_THRESHOLD = 35
            const val FRAMING_MIN_FRACTION = 0.05
            const val EDGE_MARGIN_DIVISOR = 20

            const val BORDER_BAND_DIVISOR = 10
            const val BACKGROUND_COMPLEXITY_FAIL_ABOVE = 900.0

            const val LUMA_R = 0.299
            const val LUMA_G = 0.587
            const val LUMA_B = 0.114

            const val LAPLACIAN_CENTER_WEIGHT = 4
            const val RED_SHIFT = 16
            const val GREEN_SHIFT = 8
            const val BYTE_MASK = 0xFF
        }
    }
