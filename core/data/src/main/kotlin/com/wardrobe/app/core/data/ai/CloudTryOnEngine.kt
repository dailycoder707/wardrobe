package com.wardrobe.app.core.data.ai

import android.graphics.Bitmap
import com.wardrobe.app.core.ai.gateway.AiDispatchContext
import com.wardrobe.app.core.ai.gateway.AiGateway
import com.wardrobe.app.core.ai.gateway.ImageTaskResult
import com.wardrobe.app.core.ai.prompt.PromptVersions
import com.wardrobe.app.core.ai.tryon.TryOnRenderResult
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiProviderConfig
import com.wardrobe.app.core.model.ai.AiResultSource
import javax.inject.Inject
import javax.inject.Singleton

private const val TRY_ON_TASK_TYPE = "try_on_render"
private const val MIN_RESULT_DIMENSION_PX = 64

/**
 * M12's cloud path for `VIRTUAL_TRY_ON` — the only class that builds the
 * Try-On [com.wardrobe.app.core.ai.gateway.ImageTaskAdapterRequest]. Unlike
 * Extraction/Reconstruction's single-image request, this one sends every
 * image that actually shapes the render (body, garment, optional mask) —
 * [com.wardrobe.app.core.ai.gateway.DefaultAiGateway]'s cache key already
 * combines all of their hashes, not just the first, so two different
 * garments tried on the same body photo never collide in the cache.
 *
 * [validate] is this capability's own "Cloud Result Validation" gate
 * (M12): a provider's image is never accepted as-is. Corrupt/undecodable
 * bytes are already `null`-filtered by the adapter layer before this class
 * ever sees a [Bitmap]; this adds the two checks that require actually
 * looking at the decoded result — a real minimum resolution, and a real
 * (non-`null`) confidence, since Try-On is the one capability whose spec
 * requires confidence rather than tolerating it as an honestly-missing
 * value.
 */
@Singleton
class CloudTryOnEngine
    @Inject
    constructor(
        private val aiGateway: AiGateway,
    ) {
        suspend fun render(
            bodyPhoto: Bitmap,
            garmentCutout: Bitmap,
            mask: Bitmap?,
            config: AiProviderConfig,
            apiKey: String,
        ): TryOnRenderResult {
            val context = AiDispatchContext(AiCapability.VIRTUAL_TRY_ON, config, apiKey)
            val images = listOfNotNull(bodyPhoto, garmentCutout, mask)
            val result = aiGateway.runImageTask(context, PromptVersions.TRYON_V1, TRY_ON_TASK_TYPE, images)
            return when (result) {
                is ImageTaskResult.Success -> validate(result)
                is ImageTaskResult.Failure -> TryOnRenderResult.Failure(result.reason)
            }
        }

        private fun validate(result: ImageTaskResult.Success): TryOnRenderResult {
            val image = result.resultImage
            val belowMinimumResolution = image.width < MIN_RESULT_DIMENSION_PX || image.height < MIN_RESULT_DIMENSION_PX
            val failureReason =
                when {
                    belowMinimumResolution -> "rendered_image_below_minimum_resolution"
                    result.confidence == null -> "missing_confidence"
                    else -> null
                }
            return failureReason?.let { TryOnRenderResult.Failure(it) }
                ?: TryOnRenderResult.Success(image, requireNotNull(result.confidence), AiResultSource.CLOUD)
        }
    }
