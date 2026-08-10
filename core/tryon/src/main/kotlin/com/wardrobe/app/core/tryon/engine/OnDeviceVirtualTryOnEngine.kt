package com.wardrobe.app.core.tryon.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.wardrobe.app.core.ai.tryon.TryOnRenderResult
import com.wardrobe.app.core.ai.tryon.VirtualTryOnEngine
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.common.BodyProfileId
import com.wardrobe.app.core.model.tryon.BodyMeasurements
import com.wardrobe.app.core.model.tryon.LightingAdjustment
import com.wardrobe.app.core.model.tryon.MeasurementSource
import com.wardrobe.app.core.model.tryon.TryOnAnchorRegion
import com.wardrobe.app.core.tryon.lighting.LightingMatcher
import com.wardrobe.app.core.tryon.placement.DefaultPlacementCalculator
import com.wardrobe.app.core.tryon.pose.BodyAnchorEstimate
import com.wardrobe.app.core.tryon.pose.BodyAnchorEstimator
import com.wardrobe.app.core.tryon.rendering.TRY_ON_LAYER_WIDTH_FRACTION
import com.wardrobe.app.core.tryon.shadow.ShadowRenderer
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val DEFAULT_REGION = TryOnAnchorRegion.SHOULDER_LINE
private const val MAX_CHANNEL = 255
private const val MIN_CHANNEL = 0

/**
 * The on-device implementation of [VirtualTryOnEngine] (M12) — flattens the
 * existing Phase 10 building blocks (unchanged: [BodyAnchorEstimator],
 * [DefaultPlacementCalculator], [LightingMatcher], [ShadowRenderer]) into a
 * single static bitmap via plain `Canvas`/`Matrix` draw calls, the same
 * compositing technique `core:tryon`'s own `TryOnRenderCache` already uses
 * for non-interactive previews. A separate class from that cache because
 * `TryOnRenderCache` operates on saved body-profile/placement-template
 * *files* and a garment's real [OutfitSlot][com.wardrobe.app.core.model.outfit.OutfitSlot],
 * while [VirtualTryOnEngine] is a stateless, file-agnostic Bitmap-in/
 * Bitmap-out contract the [com.wardrobe.app.core.ai.gateway.AiGateway]'s
 * cloud counterpart also has to satisfy — neither existing pipeline is
 * modified, this only calls them.
 *
 * [DEFAULT_REGION]: this interface has no garment-slot parameter (a plain
 * "try this cutout on," not "try this top on"), so every garment anchors at
 * the shoulder line — the region the most common single-item case (a top or
 * dress) actually uses. A disclosed simplification (`TECHNICAL_DEBT.md`),
 * not a bug: the live, interactive `feature:tryon` flow (which does know the
 * garment's slot) never calls this class and is entirely unaffected.
 */
@Singleton
class OnDeviceVirtualTryOnEngine
    @Inject
    constructor(
        private val bodyAnchorEstimator: BodyAnchorEstimator,
    ) : VirtualTryOnEngine {
        override suspend fun render(
            bodyPhoto: Bitmap,
            garmentCutout: Bitmap,
            mask: Bitmap?,
        ): TryOnRenderResult {
            val estimate = bodyAnchorEstimator.estimate(bodyPhoto)
            val measurements = estimate.toBodyMeasurements()
            val placement = DefaultPlacementCalculator.calculate(DEFAULT_REGION, measurements)
            val maskedCutout = applyMask(garmentCutout, mask)
            val litGarment = applyLighting(maskedCutout, LightingMatcher.match(bodyPhoto))
            val shadow = ShadowRenderer.deriveShadowSilhouette(litGarment)
            val rendered = composite(bodyPhoto, shadow, litGarment, placement)
            val confidence = (estimate as? BodyAnchorEstimate.Success)?.confidence
            return TryOnRenderResult.Success(rendered, confidence, AiResultSource.ON_DEVICE)
        }
    }

private fun BodyAnchorEstimate.toBodyMeasurements(): BodyMeasurements? =
    (this as? BodyAnchorEstimate.Success)?.let {
        BodyMeasurements(
            bodyProfileId = BodyProfileId(0),
            shoulderWidthFraction = it.shoulderWidthFraction,
            torsoHeightFraction = it.torsoHeightFraction,
            waistHeightFraction = it.waistHeightFraction,
            hipWidthFraction = it.hipWidthFraction,
            neckPositionYFraction = it.neckPositionYFraction,
            anklePositionYFraction = it.anklePositionYFraction,
            confidence = it.confidence,
            source = MeasurementSource.POSE_DETECTION,
            computedAt = Instant.now(),
        )
    }

/** [GarmentMask][com.wardrobe.app.core.model.tryon.GarmentMask]'s own
 * semantics ("erase/restore over the cutout's own alpha, applied before the
 * placement transform") applied directly to an in-memory bitmap rather than
 * a saved mask file — multiplies the cutout's own alpha by the mask's,
 * nearest-neighbor sampled if the two aren't the same size. `null` mask
 * returns [cutout] unchanged. */
private fun applyMask(
    cutout: Bitmap,
    mask: Bitmap?,
): Bitmap {
    if (mask == null) return cutout
    val result = cutout.copy(Bitmap.Config.ARGB_8888, true)
    for (y in 0 until result.height) {
        for (x in 0 until result.width) {
            val maskX = (x * mask.width) / result.width
            val maskY = (y * mask.height) / result.height
            val maskAlpha = Color.alpha(mask.getPixel(maskX, maskY))
            val pixel = result.getPixel(x, y)
            val newAlpha = (Color.alpha(pixel) * maskAlpha) / MAX_CHANNEL
            result.setPixel(x, y, Color.argb(newAlpha, Color.red(pixel), Color.green(pixel), Color.blue(pixel)))
        }
    }
    return result
}

/** Applies [LightingAdjustment]'s color-grade to a garment cutout's RGB
 * channels, leaving alpha untouched — the consuming side of the
 * documented-but-previously-unconsumed contract on [LightingAdjustment]
 * itself ("applied to garment layers at render time"). */
private fun applyLighting(
    cutout: Bitmap,
    adjustment: LightingAdjustment,
): Bitmap {
    val result = cutout.copy(Bitmap.Config.ARGB_8888, true)
    for (y in 0 until result.height) {
        for (x in 0 until result.width) {
            val pixel = result.getPixel(x, y)
            result.setPixel(
                x,
                y,
                Color.argb(
                    Color.alpha(pixel),
                    adjustChannel(Color.red(pixel), adjustment.colorGainR, adjustment.brightnessDelta),
                    adjustChannel(Color.green(pixel), adjustment.colorGainG, adjustment.brightnessDelta),
                    adjustChannel(Color.blue(pixel), adjustment.colorGainB, adjustment.brightnessDelta),
                ),
            )
        }
    }
    return result
}

private fun adjustChannel(
    channel: Int,
    gain: Float,
    brightnessDelta: Float,
): Int = ((channel * gain) + brightnessDelta).toInt().coerceIn(MIN_CHANNEL, MAX_CHANNEL)

/** Flattens the body photo + shadow + lit garment into one bitmap — the
 * same `Canvas`/`Matrix` compositing `TryOnRenderCache.flatten`/`drawLayer`
 * already use (unchanged), reusing [TRY_ON_LAYER_WIDTH_FRACTION] so this
 * batch render matches the live interactive screen's own sizing
 * convention. */
private fun composite(
    bodyPhoto: Bitmap,
    shadow: Bitmap,
    garment: Bitmap,
    placement: DefaultPlacementCalculator.CalculatedPlacement,
): Bitmap {
    val result = bodyPhoto.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(shadow, layerMatrix(result, shadow, placement), paint)
    canvas.drawBitmap(garment, layerMatrix(result, garment, placement), paint)
    return result
}

private fun layerMatrix(
    background: Bitmap,
    layer: Bitmap,
    placement: DefaultPlacementCalculator.CalculatedPlacement,
): Matrix {
    val targetWidth = background.width * TRY_ON_LAYER_WIDTH_FRACTION
    val baseScale = targetWidth / layer.width
    val effectiveScale = baseScale * placement.scale
    return Matrix().apply {
        postScale(effectiveScale, effectiveScale)
        postRotate(placement.rotationDegrees)
        postTranslate(
            placement.offsetXFraction * background.width - (layer.width * effectiveScale) / 2f,
            placement.offsetYFraction * background.height,
        )
    }
}
