package com.wardrobe.app.core.tryon.rendercache

import java.security.MessageDigest

/**
 * One garment layer's contribution to [TryOnRenderCacheKey] — every field
 * here is one of the four invalidation triggers `phase-10-personal-virtual-
 * tryon.md`'s render-cache section calls for: a re-cut cutout ([cutoutChecksum]),
 * a moved/rescaled/rotated placement ([templateId] + [templateUpdatedAtEpochMillis]),
 * and a re-masked garment ([maskUpdatedAtEpochMillis]) — [TryOnRenderCacheKey]
 * itself adds the fourth (body profile/measurements changes).
 */
data class TryOnRenderLayerInput(
    val cutoutFilePath: String,
    val cutoutChecksum: String?,
    val maskFilePath: String?,
    val maskUpdatedAtEpochMillis: Long?,
    val templateId: Long,
    val templateUpdatedAtEpochMillis: Long,
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val scale: Float,
    val rotationDegrees: Float,
)

/**
 * Everything [TryOnRenderCache] needs to know whether a previously
 * flattened bitmap is still valid: a re-captured body profile
 * ([bodyProfileUpdatedAtEpochMillis]), freshly recomputed measurements
 * ([measurementsComputedAtEpochMillis]), plus each visible garment's own
 * [TryOnRenderLayerInput]. [digest] is a stable SHA-256 over every one of
 * these — two calls with identical inputs (same layer order) always yield
 * the same digest; any single input changing changes it.
 */
data class TryOnRenderCacheKey(
    val bodyProfileUpdatedAtEpochMillis: Long,
    val measurementsComputedAtEpochMillis: Long?,
    val layers: List<TryOnRenderLayerInput>,
) {
    fun digest(): String {
        val text =
            buildString {
                append(bodyProfileUpdatedAtEpochMillis)
                append('|')
                append(measurementsComputedAtEpochMillis)
                layers.forEach { layer ->
                    append('|')
                    append(layer.cutoutChecksum ?: layer.cutoutFilePath)
                    append(',')
                    append(layer.maskUpdatedAtEpochMillis ?: NO_MASK_SENTINEL)
                    append(',')
                    append(layer.templateId)
                    append(',')
                    append(layer.templateUpdatedAtEpochMillis)
                }
            }
        return MessageDigest
            .getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }
}

private const val NO_MASK_SENTINEL = -1L
