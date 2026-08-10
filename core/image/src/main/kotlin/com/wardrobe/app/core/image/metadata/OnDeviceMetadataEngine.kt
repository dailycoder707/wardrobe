package com.wardrobe.app.core.image.metadata

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.wardrobe.app.core.model.ai.AiResultProvenance
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.ai.MetadataField
import com.wardrobe.app.core.model.ai.MetadataSuggestion
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sqrt

private const val SECONDARY_COLOR_MIN_WEIGHT = 0.08f
private const val PATTERN_STDDEV_THRESHOLD = 28.0

/** How far the measured stddev has to sit from [PATTERN_STDDEV_THRESHOLD]
 * to count as a fully confident read — a stddev this far below the
 * threshold (near-zero variance, obviously solid) or above it (very high
 * variance, obviously patterned) yields confidence 1.0; a stddev sitting
 * exactly at the threshold — the genuinely ambiguous case — yields 0.0.
 * Chosen equal to the threshold itself so "twice the threshold" or "zero"
 * both read as maximally confident, a reasonable, stated-as-approximate
 * scale rather than a calibrated statistical model. */
private const val PATTERN_CONFIDENCE_MARGIN = 28.0
private const val MIN_STDDEV_SAMPLE_COUNT = 2
private const val BRAND_CANDIDATE_MIN_LENGTH = 2
private const val BRAND_CANDIDATE_MAX_LENGTH = 24

/**
 * The default/fallback [GarmentMetadataEngine] (Add-to-Wardrobe v2,
 * ADR-012) — real, bounded, on-device-only signals: primary/secondary color
 * via k-means clustering ([kMeansColorClusters]) on cutout pixels,
 * solid-vs-patterned via a luminance-variance heuristic, and brand via ML
 * Kit Text Recognition OCR. Category, subcategory, sleeve length, neckline,
 * garment length, fit, season, dress code, occasion, gender, warmth, and
 * style tags are left unset — no real on-device signal exists for them; a
 * cloud provider (`GarmentMetadataEngineRouter`, `core:data`) is what fills
 * those in.
 */
@Singleton
class OnDeviceMetadataEngine
    @Inject
    constructor() : GarmentMetadataEngine {
        private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

        override suspend fun generateMetadata(cutout: Bitmap): List<MetadataSuggestion> =
            buildList {
                addAll(colorSuggestions(cutout))
                patternSuggestion(cutout)?.let(::add)
                brandSuggestion(cutout)?.let(::add)
            }

        private fun colorSuggestions(cutout: Bitmap): List<MetadataSuggestion> {
            val clusters = kMeansColorClusters(cutout) ?: return emptyList()
            val (primary, secondary) = clusters
            return buildList {
                add(colorSuggestion(MetadataField.PRIMARY_COLOR, primary))
                if (secondary != null && secondary.weight >= SECONDARY_COLOR_MIN_WEIGHT) {
                    add(colorSuggestion(MetadataField.SECONDARY_COLOR, secondary))
                }
            }
        }

        private fun colorSuggestion(
            field: MetadataField,
            centroid: RgbCentroid,
        ): MetadataSuggestion {
            val (name, confidence) = NamedColorPalette.nearest(centroid.r, centroid.g, centroid.b)
            return MetadataSuggestion(
                field = field,
                value = name,
                confidence = confidence,
                provenance = onDeviceProvenance(),
            )
        }

        /** Confidence is a real function of how far the measured stddev sits
         * from [PATTERN_STDDEV_THRESHOLD] — a reading far from the
         * threshold in either direction is genuinely more certain than one
         * sitting right on the solid/patterned boundary; see
         * [PATTERN_CONFIDENCE_MARGIN]'s KDoc. Not a placeholder or a
         * constant — it varies with the actual measurement. */
        private fun patternSuggestion(cutout: Bitmap): MetadataSuggestion? {
            val stddev = luminanceStandardDeviation(cutout) ?: return null
            val value = if (stddev < PATTERN_STDDEV_THRESHOLD) "Solid" else "Patterned"
            return MetadataSuggestion(
                field = MetadataField.PATTERN,
                value = value,
                confidence = patternConfidence(stddev),
                provenance = onDeviceProvenance(),
            )
        }

        /** ML Kit's on-device Latin text recognizer reports no per-result
         * confidence score at all — `confidence = null` here is not an
         * oversight, there is nothing real to report (Constitution rule 4). */
        private suspend fun brandSuggestion(cutout: Bitmap): MetadataSuggestion? {
            val candidate =
                recognizeText(cutout)
                    ?.lines()
                    ?.map(String::trim)
                    ?.firstOrNull(::isPlausibleBrandCandidate)
            return candidate?.let {
                MetadataSuggestion(
                    field = MetadataField.BRAND,
                    value = it,
                    confidence = null,
                    provenance = onDeviceProvenance(),
                )
            }
        }

        private suspend fun recognizeText(bitmap: Bitmap): String? =
            suspendCancellableCoroutine { continuation ->
                val input = InputImage.fromBitmap(bitmap, 0)
                textRecognizer
                    .process(input)
                    .addOnSuccessListener { result -> continuation.resume(result.text.takeIf { it.isNotBlank() }) }
                    .addOnFailureListener { continuation.resume(null) }
            }
    }

/** Top-level (rather than a private class member) so it's directly
 * unit-testable as pure math, with no `Bitmap`/ML Kit/Robolectric machinery
 * needed — same reasoning as `MetadataSuggestionResolver.nameMatches` being
 * `internal` for its own tests. See [PATTERN_CONFIDENCE_MARGIN]'s KDoc for
 * why distance-from-threshold is the confidence signal. */
internal fun patternConfidence(stddev: Double): Float {
    val distanceFromThreshold = abs(stddev - PATTERN_STDDEV_THRESHOLD)
    return (distanceFromThreshold / PATTERN_CONFIDENCE_MARGIN).coerceIn(0.0, 1.0).toFloat()
}

private fun isPlausibleBrandCandidate(line: String): Boolean =
    line.length in BRAND_CANDIDATE_MIN_LENGTH..BRAND_CANDIDATE_MAX_LENGTH

private fun onDeviceProvenance(): AiResultProvenance =
    AiResultProvenance(
        source = AiResultSource.ON_DEVICE,
        provider = null,
        model = null,
        promptVersion = null,
        generatedAt = Instant.now(),
    )

private fun luminanceStandardDeviation(bitmap: Bitmap): Double? {
    val width = bitmap.width
    val height = bitmap.height
    if (width == 0 || height == 0) return null
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val luminances = pixels.filter(::isOpaque).map { luminance(decomposeRgb(it)) }
    return if (luminances.size < MIN_STDDEV_SAMPLE_COUNT) null else standardDeviation(luminances)
}

private fun standardDeviation(values: List<Double>): Double {
    val mean = values.average()
    val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
    return sqrt(variance)
}
